/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.util;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.authorize.AccessControlList;
import org.apache.hadoop.security.authorize.AuthorizationException;
import org.apache.hadoop.security.authorize.DefaultImpersonationProvider;
import org.apache.hadoop.util.MachineList;
import org.apache.knox.gateway.i18n.GatewaySpiMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import java.net.InetAddress;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An extension of Hadoop's DefaultImpersonationProvider that adds support for group-based impersonation.
 * This provider allows users who belong to specific groups to impersonate other users.
 */
public class GroupBasedImpersonationProvider extends DefaultImpersonationProvider {
    private static final GatewaySpiMessages LOG = MessagesFactory.get(GatewaySpiMessages.class);
    private static final String CONF_HOSTS = ".hosts";
    private static final String CONF_USERS = ".users";
    private static final String CONF_GROUPS = ".groups";
    private final Map<String, AccessControlList> proxyGroupsAcls = new HashMap<>();
    EnumSet<AuthFilterUtils.ImpersonationFlags> impersonationFlags = EnumSet.noneOf(AuthFilterUtils.ImpersonationFlags.class);
    private Map<String, MachineList> groupProxyHosts = new HashMap<>();
    private String groupConfigPrefix;

    public GroupBasedImpersonationProvider(EnumSet<AuthFilterUtils.ImpersonationFlags> impersonationFlags) {
        super();
        this.impersonationFlags = impersonationFlags;
    }

    @Override
    public Configuration getConf() {
        return super.getConf();
    }

    @Override
    public void setConf(Configuration conf) {
        super.setConf(conf);
    }

    @Override
    public void init(String proxyGroupPrefix) {
        initGroupBasedProvider(proxyGroupPrefix);
    }

    private void initGroupBasedProvider(String proxyGroupPrefix) {
        groupConfigPrefix = proxyGroupPrefix +
                (proxyGroupPrefix.endsWith(".") ? "" : ".");

        // constructing regex to match the following patterns:
        //   $configPrefix.[ANY].users
        //   $configPrefix.[ANY].groups
        //   $configPrefix.[ANY].hosts
        //
        String prefixRegEx = groupConfigPrefix.replace(".", "\\.");
        String usersGroupsRegEx = prefixRegEx + "[\\S]*(" +
                Pattern.quote(CONF_USERS) + "|" + Pattern.quote(CONF_GROUPS) + ")";
        String hostsRegEx = prefixRegEx + "[\\S]*" + Pattern.quote(CONF_HOSTS);

        // get list of users and groups per proxygroup
        // Map of <hadoop.proxygroup.[VIRTUAL_GROUP].users|groups, group1,group2>
        Map<String, String> allMatchKeys =
                getConf().getValByRegex(usersGroupsRegEx);

        for (Map.Entry<String, String> entry : allMatchKeys.entrySet()) {
            //aclKey = hadoop.proxygroup.[VIRTUAL_GROUP]
            String aclKey = getAclKey(entry.getKey());

            if (!proxyGroupsAcls.containsKey(aclKey)) {
                proxyGroupsAcls.put(aclKey, new AccessControlList(
                        allMatchKeys.get(aclKey + CONF_USERS),
                        allMatchKeys.get(aclKey + CONF_GROUPS)));
            }
        }

        // get hosts per proxygroup
        allMatchKeys = getConf().getValByRegex(hostsRegEx);
        for (Map.Entry<String, String> entry : allMatchKeys.entrySet()) {
            groupProxyHosts.put(entry.getKey(),
                    new MachineList(entry.getValue()));
        }
    }

    private String getAclKey(String key) {
        int endIndex = key.lastIndexOf('.');
        if (endIndex != -1) {
            return key.substring(0, endIndex);
        }
        return key;
    }

    /**
     * Authorization based on groups that are already in Subject
     *
     * @param user the user information attempting the operation, which includes the real
     *             user and the effective impersonated user.
     * @param remoteAddress the remote address from which the user is connecting.
     * @throws AuthorizationException if the user is not authorized based on the
     *                                configured impersonation and group policies.
     */
    @Override
    public void authorize(UserGroupInformation user, InetAddress remoteAddress) throws AuthorizationException {
        // If both authorization methods are disabled, allow the operation to proceed
        if (impersonationFlags.isEmpty() || !impersonationFlags.contains(AuthFilterUtils.ImpersonationFlags.GROUP_IMPERSONATION)) {
            LOG.successfulImpersonation(user.getRealUser().getUserName(), user.getUserName());
            return;
        }
        checkProxyGroupAuthorization(user, remoteAddress, Collections.emptyList());
    }

    /**
     * Authorization based on groups that are provided as a function agument
     *
     * @param user the user information attempting the operation, which includes the real
     *             user and the effective impersonated user.
     * @param groups the list of groups to check for authorization.
     * @param remoteAddress the remote address from which the user is connecting.
     * @throws AuthorizationException if the user is not authorized based on the
     *                                configured impersonation and group policies.
     */
    public void authorize(UserGroupInformation user, InetAddress remoteAddress, List<String> groups) throws AuthorizationException {
        // If both authorization methods are disabled, allow the operation to proceed
        if (impersonationFlags.isEmpty() || !impersonationFlags.contains(AuthFilterUtils.ImpersonationFlags.GROUP_IMPERSONATION)) {
            LOG.successfulImpersonation(user.getRealUser().getUserName(), user.getUserName());
            return;
        }
        checkProxyGroupAuthorization(user, remoteAddress, groups);
    }

    /**
     * Helper method to check if the group a given user belongs to is authorized to impersonate
     * Returns true if the user is authorized, false otherwise.
     *
     * @param user
     * @param remoteAddress
     * @return
     */
    private void checkProxyGroupAuthorization(final UserGroupInformation user, final InetAddress remoteAddress, List<String> groups) throws AuthorizationException {
        if (user == null) {
            throw new IllegalArgumentException("user is null.");
        }

        final UserGroupInformation realUser = user.getRealUser();
        if (realUser == null) {
            return;
        }

        // Get the real user's groups (both real and virtual)
        Set<String> realUserGroups = new HashSet<>();
        /* Add provided groups */
        if(groups != null && !groups.isEmpty()) {
            realUserGroups.addAll(groups);
        }
        /* Add groups from subject */
        if (user.getRealUser().getGroupNames() != null) {
            Collections.addAll(realUserGroups, user.getRealUser().getGroupNames());
        }

        boolean proxyGroupFound = false;
        // Check if any of the real user's groups have permission to impersonate the proxy user
        for (String group : realUserGroups) {
            final AccessControlList acl = proxyGroupsAcls.get(groupConfigPrefix +
                    group);

            if (acl == null || !acl.isUserAllowed(user)) {
                continue;
            } else {
                proxyGroupFound = true;
                break;
            }
        }

        if (!proxyGroupFound) {
            LOG.failedToImpersonateGroups(realUser.getUserName(), realUserGroups.toString(), user.getUserName());
            throw new AuthorizationException("User: " + realUser.getUserName()
                    + " with groups " + realUserGroups.toString()
                    + " is not allowed to impersonate " + user.getUserName());
        }

        boolean proxyGroupHostFound = false;
        for (final String group : realUserGroups) {
            final MachineList machineList = groupProxyHosts.get(groupConfigPrefix + group + CONF_HOSTS);

            if (machineList == null || !machineList.includes(remoteAddress)) {
                continue;
            } else {
                proxyGroupHostFound = true;
                break;
            }
        }

        if (!proxyGroupHostFound) {
            LOG.failedToImpersonateGroupsFromAddress(realUser.getUserName(), realUserGroups.toString(), user.getUserName(), remoteAddress.toString());
            throw new AuthorizationException("User: " + realUser.getUserName()
                    + "with groups " + realUserGroups.toString()
                    + " from address " + remoteAddress.toString()
                    + " is not allowed to impersonate " + user.getUserName());
        }

        /* all checks pass */
    }
}
