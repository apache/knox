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
package org.apache.knox.gateway.service.auth;

import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.security.auth.Subject;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.security.SubjectUtils;

@Path(PreAuthResource.RESOURCE_PATH)
public class PreAuthResource {
  static final String RESOURCE_PATH = "auth/api/v1/pre";
  private static final AuthMessages LOG = MessagesFactory.get(AuthMessages.class);
  static final String AUTH_ACTOR_ID_HEADER_NAME = "preauth.auth.header.actor.id.name";
  static final String AUTH_ACTOR_GROUPS_HEADER_PREFIX = "preauth.auth.header.actor.groups.prefix";
  static final String GROUP_FILTER_PATTERN = "preauth.group.filter.pattern";

  static final String DEFAULT_AUTH_ACTOR_ID_HEADER_NAME = "X-Knox-Actor-ID";
  static final String DEFAULT_AUTH_ACTOR_GROUPS_HEADER_PREFIX = "X-Knox-Actor-Groups";
  private static final Pattern DEFAULT_GROUP_FILTER_PATTERN = Pattern.compile(".*");

  private static final int MAX_HEADER_LENGTH = 1000;
  private static final String ACTOR_GROUPS_HEADER_FORMAT = "%s-%d";

  @Context
  HttpServletResponse response;

  @Context
  ServletContext context;

  private String authHeaderActorIDName;
  private String authHeaderActorGroupsPrefix;
  private Pattern groupFilterPattern;

  @PostConstruct
  public void init() {
    authHeaderActorIDName = getInitParameter(AUTH_ACTOR_ID_HEADER_NAME, DEFAULT_AUTH_ACTOR_ID_HEADER_NAME);
    authHeaderActorGroupsPrefix = getInitParameter(AUTH_ACTOR_GROUPS_HEADER_PREFIX, DEFAULT_AUTH_ACTOR_GROUPS_HEADER_PREFIX);
    final String groupFilterPatternString = context.getInitParameter(GROUP_FILTER_PATTERN);
    groupFilterPattern = groupFilterPatternString == null ? DEFAULT_GROUP_FILTER_PATTERN : Pattern.compile(groupFilterPatternString);
  }

  private String getInitParameter(String paramName, String defaultValue) {
    final String initParam = context.getInitParameter(paramName);
    return initParam == null ? defaultValue : initParam;
  }

  @GET
  public Response doGet() {
    final Subject subject = SubjectUtils.getCurrentSubject();

    final String primaryPrincipalName = subject == null ? null : SubjectUtils.getPrimaryPrincipalName(subject);
    if (primaryPrincipalName == null) {
      LOG.noPrincipalFound();
      return status(HttpServletResponse.SC_UNAUTHORIZED).build();
    }
    response.setHeader(authHeaderActorIDName, primaryPrincipalName);

    // Populate actor groups headers
    final Set<String> matchingGroupNames = subject == null ? Collections.emptySet()
        : SubjectUtils.getGroupPrincipals(subject).stream().filter(group -> groupFilterPattern.matcher(group.getName()).matches()).map(group -> group.getName())
            .collect(Collectors.toSet());
    if (!matchingGroupNames.isEmpty()) {
      final List<String> groupStrings = getGroupStrings(matchingGroupNames);
      for (int i = 0; i < groupStrings.size(); i++) {
        response.addHeader(String.format(Locale.ROOT, ACTOR_GROUPS_HEADER_FORMAT, authHeaderActorGroupsPrefix, i + 1), groupStrings.get(i));
      }
    }
    return ok().build();
  }

  private List<String> getGroupStrings(Collection<String> groupNames) {
    if (groupNames.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> groupStrings = new ArrayList<>();
    StringBuilder sb = new StringBuilder();
    for (String groupName : groupNames) {
      if (sb.length() + groupName.length() > MAX_HEADER_LENGTH) {
        groupStrings.add(sb.toString());
        sb = new StringBuilder();
      }
      if (sb.length() > 0) {
        sb.append(',');
      }
      sb.append(groupName);
    }
    if (sb.length() > 0) {
      groupStrings.add(sb.toString());
    }
    return groupStrings;
  }

}