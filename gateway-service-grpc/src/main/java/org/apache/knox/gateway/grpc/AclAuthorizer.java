/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.grpc;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.knox.gateway.filter.AclParser;
import org.apache.knox.gateway.filter.InvalidACLException;

/**
 * Evaluates a topology's {@code AclsAuthz} ACLs for a gRPC call.
 * <p>
 * Knox's authorization responsibility on this path is deliberately one question:
 * may this user use this service in this topology at all? Fine-grained
 * authorization — databases, tables, columns, row filters, masking — belongs to
 * policy evaluated inside the backend against the identity Knox asserts, and is
 * not something a gateway can usefully duplicate.
 * <p>
 * The syntax and semantics are the servlet provider's, down to sharing its
 * {@link AclParser}: {@code users;groups;ipaddresses}, an {@code AND}/{@code OR}
 * processing mode, {@code *} wildcards, and the {@code KNOX_ADMIN_USERS} /
 * {@code KNOX_ADMIN_GROUPS} placeholders. Operators should not have to learn a
 * second ACL dialect because the transport changed.
 */
public class AclAuthorizer {

  private static final String ACL_SUFFIX = ".acl";
  private static final String ACL_MODE_SUFFIX = ".acl.mode";
  private static final String DEFAULT_ACL_MODE = "AND";
  private static final String KNOX_ADMIN_USERS_PLACEHOLDER = "KNOX_ADMIN_USERS";
  private static final String KNOX_ADMIN_GROUPS_PLACEHOLDER = "KNOX_ADMIN_GROUPS";

  private final AclParser parser = new AclParser();
  private final String aclProcessingMode;
  private final Set<String> adminUsers;
  private final Set<String> adminGroups;
  private final boolean unrestricted;

  /**
   * Builds an authorizer for one resource role from a topology's provider
   * parameters.
   *
   * @param resourceRole the service role the ACLs apply to, e.g. {@code SPARKCONNECT}
   * @param providerParams the {@code AclsAuthz} provider parameters, or null if the
   *        topology declares no such provider
   * @param knoxAdminUsers comma-separated admin users from gateway configuration
   * @param knoxAdminGroups comma-separated admin groups from gateway configuration
   * @throws InvalidACLException if a configured ACL is malformed
   */
  public AclAuthorizer(String resourceRole,
                       Map<String, String> providerParams,
                       String knoxAdminUsers,
                       String knoxAdminGroups) throws InvalidACLException {
    // Provider params become filter params lowercased on the servlet path, and
    // the filter looks them up that way; match it so the same topology XML works.
    final Map<String, String> params = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    if (providerParams != null) {
      params.putAll(providerParams);
    }

    String mode = params.get(resourceRole + ACL_MODE_SUFFIX);
    if (mode == null) {
      mode = params.get("acl.mode");
    }
    this.aclProcessingMode = mode == null ? DEFAULT_ACL_MODE : mode.toUpperCase(Locale.ROOT);

    final String acls = params.get(resourceRole + ACL_SUFFIX);
    parser.parseAcls(resourceRole, acls);

    this.adminUsers = split(knoxAdminUsers);
    this.adminGroups = split(knoxAdminGroups);

    // No ACLs configured at all means no restrictions, matching the servlet
    // provider: a topology that never mentions this role does not silently deny.
    this.unrestricted = parser.users.isEmpty() && parser.groups.isEmpty()
        && parser.ipv.getIPAddresses().isEmpty();
  }

  private static Set<String> split(String csv) {
    if (csv == null || csv.trim().isEmpty()) {
      return Collections.emptySet();
    }
    return new HashSet<>(Arrays.asList(csv.trim().split("\\s*,\\s*")));
  }

  /**
   * Decides whether a call is permitted.
   *
   * @param user the authenticated principal
   * @param groups the principal's groups, possibly empty
   * @param remoteAddress the client's IP address, or null if unavailable
   * @return true if the call may proceed
   */
  public boolean isPermitted(String user, Set<String> groups, String remoteAddress) {
    if (unrestricted) {
      return true;
    }

    boolean userAccess = checkUser(user);
    boolean groupAccess = checkGroups(groups);
    boolean ipAccess = remoteAddress != null && parser.ipv.validateIpAddress(remoteAddress);

    if ("OR".equals(aclProcessingMode)) {
      // Under OR, a wildcard has to read as "not a reason to grant" — otherwise a
      // single '*' in any position would admit everyone.
      if (parser.anyUser) {
        userAccess = false;
      }
      if (parser.anyGroup) {
        groupAccess = false;
      }
      if (parser.ipv.allowsAnyIP()) {
        ipAccess = false;
      }
      return userAccess || groupAccess || ipAccess;
    }
    if ("AND".equals(aclProcessingMode)) {
      return userAccess && groupAccess && ipAccess;
    }
    return false;
  }

  private boolean checkUser(String user) {
    if (user == null) {
      return false;
    }
    if (parser.anyUser) {
      return true;
    }
    if (parser.users.contains(user)) {
      return true;
    }
    return parser.users.contains(KNOX_ADMIN_USERS_PLACEHOLDER) && adminUsers.contains(user);
  }

  private boolean checkGroups(Set<String> groups) {
    if (groups == null || groups.isEmpty()) {
      // A subject with no groups can still satisfy an AND policy whose group
      // position is a wildcard, e.g. '*;*;127.0.0.*'.
      return parser.anyGroup && "AND".equals(aclProcessingMode);
    }
    if (parser.anyGroup) {
      return true;
    }
    for (String group : groups) {
      if (parser.groups.contains(group)) {
        return true;
      }
      if (parser.groups.contains(KNOX_ADMIN_GROUPS_PLACEHOLDER) && adminGroups.contains(group)) {
        return true;
      }
    }
    return false;
  }
}
