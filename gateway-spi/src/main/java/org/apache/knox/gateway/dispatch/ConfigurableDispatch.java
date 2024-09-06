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
package org.apache.knox.gateway.dispatch;

import org.apache.commons.collections.MapUtils;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.config.Configure;
import org.apache.knox.gateway.config.Default;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.util.StringUtils;

import javax.security.auth.Subject;
import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Optional;
import java.util.List;
import java.util.Collection;
import java.util.Locale;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extends DefaultDispatch to:
 *   make request/response exclude headers configurable
 *   make url encoding configurable
 */
public class ConfigurableDispatch extends DefaultDispatch {
  private Set<String> requestExcludeHeaders = super.getOutboundRequestExcludeHeaders();
  private Set<String> responseExcludeHeaders = super.getOutboundResponseExcludeHeaders();
  private Map<String, String> requestAppendHeaders = Collections.emptyMap();
  private Set<String> responseExcludeSetCookieHeaderDirectives = super.getOutboundResponseExcludedSetCookieHeaderDirectives();
  private Boolean removeUrlEncoding = false;

  private boolean shouldIncludePrincipalAndGroups;
  private String actorIdHeaderName = DEFAULT_AUTH_ACTOR_ID_HEADER_NAME;
  private String actorGroupsHeaderPrefix = DEFAULT_AUTH_ACTOR_GROUPS_HEADER_PREFIX;
  private String groupFilterPattern = DEFAULT_GROUP_FILTER_PATTERN;

  static final String DEFAULT_AUTH_ACTOR_ID_HEADER_NAME = "X-Knox-Actor-ID";
  static final String DEFAULT_AUTH_ACTOR_GROUPS_HEADER_PREFIX = "X-Knox-Actor-Groups";
  static final String DEFAULT_GROUP_FILTER_PATTERN = ".*";
  static final String DEFAULT_ARE_USERS_GROUPS_HEADER_INCLUDED = "false";

  protected static final int MAX_HEADER_LENGTH = 1000;
  protected static final String ACTOR_GROUPS_HEADER_FORMAT = "%s-%d";
  protected Pattern groupPattern = Pattern.compile(DEFAULT_GROUP_FILTER_PATTERN);


  private Set<String> convertCommaDelimitedHeadersToSet(String headers) {
    return headers == null ?  Collections.emptySet(): new HashSet<>(Arrays.asList(headers.split("\\s*,\\s*")));
  }

  /**
   * This function converts header string to a Map(String, String)
   * Header String format: a:b,c:d --> this will translate to Map({a=b,c=d})
   */
  private Map<String, String> convertCommaDelimitedHeadersToMap(String headers) {
    if(null == headers){
      return Collections.emptyMap();
    }
    try {
      Map<String, String> extraHeaders = new HashMap<>();
      Arrays.stream(headers.split("\\s*;\\s*"))
              .forEach(keyValuePair -> extraHeaders.putIfAbsent(keyValuePair.split("\\s*:\\s*")[0], keyValuePair.split("\\s*:\\s*")[1]));
      return extraHeaders;
    } catch (Exception e) {
      auditor.audit("deserialize headers", headers, "header",
              ActionOutcome.WARNING, "Extra Headers are skipped because of "+e.getMessage());
      return Collections.emptyMap();
    }
  }

  @Configure
  protected void setRequestExcludeHeaders(@Default(" ") String headers) {
    if(!" ".equals(headers)) {
      this.requestExcludeHeaders = convertCommaDelimitedHeadersToSet(headers);
    }
  }

  @Configure
  protected void setResponseExcludeHeaders(@Default(" ") String headers) {
    if (!" ".equals(headers)) {
      final Set<String> headerSet = convertCommaDelimitedHeadersToSet(headers);
      populateSetCookieHeaderDirectiveExlusions(headerSet);
      populateHttpHeaderExlusionsOtherThanSetCookie(headerSet);
    }
  }

  @Configure
  protected void setRequestAppendHeaders(@Default(" ") String extraHeaders) {
    if(!" ".equals(extraHeaders)) {
      this.requestAppendHeaders = convertCommaDelimitedHeadersToMap(extraHeaders);
    }
  }

  private void populateSetCookieHeaderDirectiveExlusions(final Set<String> headerSet) {
    final Optional<String> setCookieHeader = headerSet.stream().filter(s -> StringUtils.startsWithIgnoreCase(s, SET_COOKIE)).findFirst();
    if (setCookieHeader.isPresent()) {
      final String[] setCookieHeaderParts = setCookieHeader.get().split(":");
      responseExcludeSetCookieHeaderDirectives = setCookieHeaderParts.length > 1
          ? new HashSet<>(Arrays.asList(setCookieHeaderParts[1].split(";"))).stream().map(e -> e.trim()).collect(Collectors.toSet())
          : EXCLUDE_SET_COOKIES_DEFAULT;
    } else {
      /* Exclude headers list is defined but we don't have set-cookie in the list,
      by default prevent these cookies from leaking */
      responseExcludeSetCookieHeaderDirectives = EXCLUDE_SET_COOKIES_DEFAULT;
    }
  }

  private void populateHttpHeaderExlusionsOtherThanSetCookie(final Set<String> headerSet) {
    final Set<String> excludedHeadersOthenThanSetCookie = headerSet.stream().filter(s -> !StringUtils.startsWithIgnoreCase(s, SET_COOKIE)).collect(Collectors.toSet());
    if (!excludedHeadersOthenThanSetCookie.isEmpty()) {
      this.responseExcludeHeaders = excludedHeadersOthenThanSetCookie;
    }
  }

  @Configure
  protected void setRemoveUrlEncoding(@Default("false") String removeUrlEncoding) {
    this.removeUrlEncoding = Boolean.parseBoolean(removeUrlEncoding);
  }

  @Configure
  public void setShouldIncludePrincipalAndGroups(@Default(DEFAULT_ARE_USERS_GROUPS_HEADER_INCLUDED) boolean shouldIncludePrincipalAndGroups) {
    this.shouldIncludePrincipalAndGroups = shouldIncludePrincipalAndGroups;
  }

  @Configure
  public void setActorIdHeaderName(@Default(DEFAULT_AUTH_ACTOR_ID_HEADER_NAME) String actorIdHeaderName) {
    this.actorIdHeaderName = actorIdHeaderName;
  }

  @Configure
  public void setActorGroupsHeaderPrefix(@Default(DEFAULT_AUTH_ACTOR_GROUPS_HEADER_PREFIX) String actorGroupsHeaderPrefix) {
    this.actorGroupsHeaderPrefix = actorGroupsHeaderPrefix;
  }

  @Configure
  public void setGroupFilterPattern(@Default(DEFAULT_GROUP_FILTER_PATTERN) String groupFilterPattern) {
    this.groupFilterPattern = groupFilterPattern;
    groupPattern = Pattern.compile(this.groupFilterPattern);
  }

  public void copyRequestHeaderFields(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, boolean shouldAddExpect100Header) {
    super.copyRequestHeaderFields(outboundRequest, inboundRequest, shouldAddExpect100Header);
    addPrincipalAndGroups(outboundRequest);
  }

  @Override
  public void copyRequestHeaderFields(HttpUriRequest outboundRequest,
                                      HttpServletRequest inboundRequest) {
    super.copyRequestHeaderFields(outboundRequest, inboundRequest);
    addPrincipalAndGroups(outboundRequest);
  }

  private void addPrincipalAndGroups(final HttpUriRequest outboundRequest) {
    //If there are some headers to be appended, append them
    Map<String, String> extraHeaders = getOutboundRequestAppendHeaders();
    if(MapUtils.isNotEmpty(extraHeaders)){
      extraHeaders.forEach(outboundRequest::addHeader);
    }
    /* If we need to add user and groups to outbound request */
    if(shouldIncludePrincipalAndGroups) {
      Map<String, String> groups = addPrincipalAndGroups();
      if(MapUtils.isNotEmpty(groups)){
        groups.forEach(outboundRequest::addHeader);
      }
    }
  }

  private Map<String, String> addPrincipalAndGroups() {
    final Map<String, String> headers = new ConcurrentHashMap();
    final Subject subject = SubjectUtils.getCurrentSubject();

    final String primaryPrincipalName = subject == null ? null : SubjectUtils.getPrimaryPrincipalName(subject);
    if (primaryPrincipalName == null) {
      LOG.noPrincipalFound();
      headers.put(actorIdHeaderName, "");
    } else {
      headers.put(actorIdHeaderName, primaryPrincipalName);
    }

    // Populate actor groups headers
    final Set<String> matchingGroupNames = subject == null ? Collections.emptySet()
            : SubjectUtils.getGroupPrincipals(subject).stream().filter(group -> groupPattern.matcher(group.getName()).matches()).map(group -> group.getName())
            .collect(Collectors.toSet());
    if (!matchingGroupNames.isEmpty()) {
      final List<String> groupStrings = getGroupStrings(matchingGroupNames);
      for (int i = 0; i < groupStrings.size(); i++) {
        headers.put(String.format(Locale.ROOT, ACTOR_GROUPS_HEADER_FORMAT, actorGroupsHeaderPrefix, i + 1), groupStrings.get(i));
      }
    }
    return headers;
  }

  private List<String> getGroupStrings(final Collection<String> groupNames) {
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

  @Override
  public Set<String> getOutboundResponseExcludeHeaders() {
    return responseExcludeHeaders == null ? Collections.emptySet() : responseExcludeHeaders;
  }

  @Override
  public Set<String> getOutboundResponseExcludedSetCookieHeaderDirectives() {
    return responseExcludeSetCookieHeaderDirectives == null ? Collections.emptySet() : responseExcludeSetCookieHeaderDirectives;
  }

  @Override
  public Set<String> getOutboundRequestExcludeHeaders() {
    return requestExcludeHeaders == null ? Collections.emptySet() : requestExcludeHeaders;
  }

  public Map<String, String> getOutboundRequestAppendHeaders() {
    return requestAppendHeaders == null ? Collections.emptyMap() : requestAppendHeaders;
  }

  public boolean getRemoveUrlEncoding() {
    return removeUrlEncoding;
  }

  @Override
  public URI getDispatchUrl(HttpServletRequest request) {
    if (getRemoveUrlEncoding()) {
      String base = request.getRequestURI();
      StringBuffer str = new StringBuffer();
      str.append(base);
      String query = request.getQueryString();
      if (query != null) {
        try {
          query = URLDecoder.decode(query, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
          // log
        }
        str.append('?');
        str.append(query);
      }
      encodeUnwiseCharacters(str);
      return URI.create(str.toString());
    }

    return super.getDispatchUrl(request);
  }

}
