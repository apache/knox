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

import org.apache.knox.gateway.filter.security.AbstractIdentityAssertionBase;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.ldap.LDAPRolesLookupService;
import org.apache.knox.gateway.util.GroupUtils;

import javax.security.auth.Subject;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static jakarta.ws.rs.core.Response.ok;
import static jakarta.ws.rs.core.Response.status;

public abstract class AbstractAuthResource {
  public static final String AUTH_ACTOR_ID_HEADER_NAME = "preauth.auth.header.actor.id.name";
  public static final String AUTH_ACTOR_GROUPS_HEADER_NAME = "preauth.auth.header.actor.groups";
  public static final String AUTH_ACTOR_GROUPS_HEADER_PREFIX = "preauth.auth.header.actor.groups.prefix";
  public static final String AUTH_TOKEN_HEADER_NAME = "preauth.auth.header.auth.token.name";
  public static final String AUTH_TOKEN_SIZE_LIMIT = "preauth.auth.header.auth.token.size.limit";
  public static final String GROUP_HEADER_LENGTH_LIMIT = "preauth.auth.header.groups.length.limit";
  public static final String GROUP_HEADER_SIZE_LIMIT = "preauth.auth.header.groups.size.limit";
  private static final String GROUP_FILTER_PATTERN = "preauth.group.filter.pattern";

  static final AuthMessages LOG = MessagesFactory.get(AuthMessages.class);

  static final String DEFAULT_AUTH_ACTOR_ID_HEADER_NAME = "X-Knox-Actor-ID";
  static final String DEFAULT_AUTH_ACTOR_GROUPS_HEADER_PREFIX = "X-Knox-Actor-Groups";

  static final Pattern DEFAULT_GROUP_FILTER_PATTERN = Pattern.compile(".*");

  /*
   * Bounds this one header only it is not a budget for the whole response, whose group headers
   * are unbounded by default (see GROUP_HEADER_SIZE_LIMIT). Jetty caps the response headers at
   * gateway.httpserver.responseHeaderBuffer (8KB by default) and the calling proxy has a limit of
   * its own, so 6KB is a token size that still leaves room for the status line, the standard
   * headers and this service's actor id header.
   */
  private static final String DEFAULT_AUTH_TOKEN_SIZE_LIMIT = "6144";

  /*
   * RFC 9110 section 5.1 field-name = token. Validating the configured name keeps a typo, or a
   * value carrying CR/LF, from ever reaching setHeader.
   */
  private static final Pattern HTTP_FIELD_NAME = Pattern.compile("[!#$%&'*+\\-.^_`|~0-9A-Za-z]+");

  /*
   * Header names that are off limits for this service to set.
   * Response header names this service must not write a configured value into: ones that hand the
   * value to the browser (Set-Cookie), ones that frame the response (Content-Length,
   * Transfer-Encoding, Content-Type, Content-Encoding), the hop-by-hop set that Jetty and the
   * proxy interpret rather than forward, and Cache-Control, which the token branch below sets
   * itself and would otherwise silently clobber one line later.
   */
  private static final Set<String> RESERVED_HEADER_NAMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
      HttpHeaders.SET_COOKIE.toLowerCase(Locale.ROOT), "set-cookie2",
      HttpHeaders.CONTENT_LENGTH.toLowerCase(Locale.ROOT), HttpHeaders.CONTENT_TYPE.toLowerCase(Locale.ROOT),
      HttpHeaders.CONTENT_ENCODING.toLowerCase(Locale.ROOT), HttpHeaders.CACHE_CONTROL.toLowerCase(Locale.ROOT),
      HttpHeaders.LOCATION.toLowerCase(Locale.ROOT), HttpHeaders.WWW_AUTHENTICATE.toLowerCase(Locale.ROOT),
      HttpHeaders.DATE.toLowerCase(Locale.ROOT), "transfer-encoding", "connection", "upgrade", "te", "trailer",
      "keep-alive", "proxy-authenticate", "proxy-authorization")));

  /*
   * RFC 7515 section 3.1 compact serialization: three base64url segments separated by dots, the
   * last of which is empty for an unsecured JWS. Validating the VALUE matters more than validating
   * the name: the name comes from the operator, but the token is attacker-influenced, and
   * parseFromHTTPBasicCredentials hands back an Authorization: Basic "Token:<value>" payload
   * verbatim with no JWT check. A value carrying CR/LF must never reach setHeader -- response
   * splitting in the endpoint an ext_authz gate trusts must not depend on the servlet container
   * happening to sanitize it, and this is a reusable base class with an abstract getResponse().
   */
  private static final Pattern COMPACT_JWS = Pattern.compile("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]*");

  /*
   * initialize() runs on every request -- neither PreAuthResource nor ExtAuthzResource is a
   * @Singleton -- so a config problem warned about inline would be warned about on every request,
   * and the shipped drfa appender has no size cap or retention. Report each distinct problem once
   * per JVM instead.
   */
  private static final Set<String> REPORTED_CONFIG_PROBLEMS = ConcurrentHashMap.newKeySet();

  private static final String DEFAULT_GROUP_HEADER_LENGTH_LIMIT = "1000";
  private static final String DEFAULT_GROUP_HEADER_SIZE_LIMIT = "-1"; // turned off by default, to be backward compatible
  private static final String ACTOR_GROUPS_HEADER_FORMAT = "%s-%d";

  protected String authHeaderActorIDName;
  protected String authHeaderActorGroupsName;
  protected String authHeaderActorGroupsPrefix;
  protected String authHeaderAuthTokenName;
  private int authTokenSizeLimit;
  private int groupHeaderLengthLimit;
  private int groupHeaderSizeLimit;
  protected Pattern groupFilterPattern;
  protected String authHeaderActorRolesName;
  private LDAPRolesLookupService ldapRolesLookupService;

  protected void initialize() {
    authHeaderActorIDName = getInitParameter(AUTH_ACTOR_ID_HEADER_NAME, DEFAULT_AUTH_ACTOR_ID_HEADER_NAME);
    authHeaderActorGroupsName = getInitParameter(AUTH_ACTOR_GROUPS_HEADER_NAME, null);
    authHeaderActorGroupsPrefix = getInitParameter(AUTH_ACTOR_GROUPS_HEADER_PREFIX, DEFAULT_AUTH_ACTOR_GROUPS_HEADER_PREFIX);
    // Opt-in: with no header name configured the caller's token is never emitted
    authHeaderAuthTokenName = getInitParameter(AUTH_TOKEN_HEADER_NAME, null);
    authTokenSizeLimit = parseLimit(AUTH_TOKEN_SIZE_LIMIT, DEFAULT_AUTH_TOKEN_SIZE_LIMIT);
    groupHeaderLengthLimit = parseLimit(GROUP_HEADER_LENGTH_LIMIT, DEFAULT_GROUP_HEADER_LENGTH_LIMIT);
    groupHeaderSizeLimit = parseLimit(GROUP_HEADER_SIZE_LIMIT, DEFAULT_GROUP_HEADER_SIZE_LIMIT);
    final String groupFilterPatternString = getInitParameter(GROUP_FILTER_PATTERN, null);
    groupFilterPattern = groupFilterPatternString == null ? DEFAULT_GROUP_FILTER_PATTERN : Pattern.compile(groupFilterPatternString);

    // Every configurable header name goes through the same gate. Defending only the token
    // header would leave the worse outcome undefended: an actor id header configured as
    // Content-Length writes a user name into the response framing on every successful auth check.
    if (!isUsableHeaderName(authHeaderActorIDName)) {
      if (shouldReport(AUTH_ACTOR_ID_HEADER_NAME, authHeaderActorIDName)) {
        LOG.headerNameNotUsableFallingBack(AUTH_ACTOR_ID_HEADER_NAME, sanitize(authHeaderActorIDName), DEFAULT_AUTH_ACTOR_ID_HEADER_NAME);
      }
      authHeaderActorIDName = DEFAULT_AUTH_ACTOR_ID_HEADER_NAME;
    }
    if (authHeaderActorGroupsName != null && !isUsableHeaderName(authHeaderActorGroupsName)) {
      if (shouldReport(AUTH_ACTOR_GROUPS_HEADER_NAME, authHeaderActorGroupsName)) {
        LOG.headerNameNotUsableDropped(AUTH_ACTOR_GROUPS_HEADER_NAME, sanitize(authHeaderActorGroupsName));
      }
      // dropped rather than defaulted: with no explicit name the indexed prefix form is used
      authHeaderActorGroupsName = null;
    }
    if (!isUsableHeaderName(authHeaderActorGroupsPrefix)) {
      if (shouldReport(AUTH_ACTOR_GROUPS_HEADER_PREFIX, authHeaderActorGroupsPrefix)) {
        LOG.headerNameNotUsableFallingBack(AUTH_ACTOR_GROUPS_HEADER_PREFIX, sanitize(authHeaderActorGroupsPrefix), DEFAULT_AUTH_ACTOR_GROUPS_HEADER_PREFIX);
      }
      authHeaderActorGroupsPrefix = DEFAULT_AUTH_ACTOR_GROUPS_HEADER_PREFIX;
    }
    if (authHeaderAuthTokenName != null && !isUsableHeaderName(authHeaderAuthTokenName)) {
      if (shouldReport(AUTH_TOKEN_HEADER_NAME, authHeaderAuthTokenName)) {
        LOG.authTokenHeaderNameNotUsable(AUTH_TOKEN_HEADER_NAME, sanitize(authHeaderAuthTokenName));
      }
      authHeaderAuthTokenName = null;
    }
    if (authHeaderAuthTokenName != null && collidesWithIdentityHeader(authHeaderAuthTokenName)) {
      // A misconfigured token header name would overwrite the identity this service exists to
      // assert. Drop the token rather than the identity.
      if (shouldReport(AUTH_TOKEN_HEADER_NAME + ".collision", authHeaderAuthTokenName)) {
        LOG.authTokenHeaderNameCollides(AUTH_TOKEN_HEADER_NAME, sanitize(authHeaderAuthTokenName));
      }
      authHeaderAuthTokenName = null;
    }

    final GatewayServices gatewayServices = (GatewayServices) getContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    if (gatewayServices != null) {
      ldapRolesLookupService = gatewayServices.getService(ServiceType.LDAP_ROLES_LOOKUP_SERVICE);
    }

  }

  /* abstract method to get the response instance */
  abstract HttpServletResponse getResponse();

  /* Abstract method that gets context instance */
  abstract ServletContext getContext();

  abstract ServletRequest getRequest();

  String getInitParameter(String paramName, String defaultValue) {
    final String initParam = getContext().getInitParameter(paramName);
    return initParam == null ? defaultValue : initParam;
  }

  public Response doGetImpl() {
    final Subject subject = SubjectUtils.getCurrentSubject();

    final String primaryPrincipalName = subject == null ? null : SubjectUtils.getPrimaryPrincipalName(subject);
    if (primaryPrincipalName == null) {
      LOG.noPrincipalFound();
      return status(HttpServletResponse.SC_UNAUTHORIZED).build();
    }
    getResponse().setHeader(authHeaderActorIDName, primaryPrincipalName);

    // Populate the caller's own token, if one was captured at authentication time and the operator
    // asked for it. The value is the bare serialized JWT: it is up to the caller to add any
    // scheme prefix the downstream service expects.
    if (authHeaderAuthTokenName != null) {
      final String authToken = SubjectUtils.getAuthToken(subject);
      if (authToken != null) {
        if (authTokenSizeLimit > 0 && authToken.length() > authTokenSizeLimit) {
          // Emitting it anyway risks the whole response being truncated or rejected, which would
          // cost the caller the identity headers too. Degrade the way lookupRoles does: warn and go on.
          LOG.authTokenTooLargeToForward(authToken.length(), AUTH_TOKEN_SIZE_LIMIT, authTokenSizeLimit, authHeaderAuthTokenName);
        } else if (!COMPACT_JWS.matcher(authToken).matches()) {
          LOG.authTokenNotWellFormed(authHeaderAuthTokenName);
        } else {
          getResponse().setHeader(authHeaderAuthTokenName, authToken);
          // RFC 6749 section 5.1: a response carrying a token must not be stored by any cache on
          // the way back to the proxy. Only set when a token is actually emitted, so responses
          // without one keep exactly the cache semantics they have today.
          getResponse().setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        }
      }
    }

    // Populate actor groups/roles headers
    final Set<String> matchingGroupNames = subject == null ? Collections.emptySet()
            : SubjectUtils.getGroupPrincipals(subject).stream().filter(group -> groupFilterPattern.matcher(group.getName()).matches()).map(group -> group.getName())
            .collect(Collectors.toSet());
    final Collection<String> roles = lookupRoles(primaryPrincipalName, matchingGroupNames);
    if (!matchingGroupNames.isEmpty() || !roles.isEmpty()) {
      final boolean useRoles = !roles.isEmpty();
      final List<String> groupStrings = GroupUtils.getGroupStrings(useRoles ? roles : matchingGroupNames, groupHeaderLengthLimit, groupHeaderSizeLimit);
      for (int i = 0; i < groupStrings.size(); i++) {
        getResponse().addHeader(createGroupsHeaderName(useRoles, i), groupStrings.get(i));
      }
    }
    return ok().build();
  }

  /**
   * @param paramName the service parameter to read
   * @param defaultValue the default to use when it is unset or unparseable
   * @return the limit as an int, falling back to the default when it cannot be parsed. A typo here
   *         must not throw out of a Jersey resource: doGetImpl is the gate an ext_authz proxy
   *         consults, and a 500 on every request would fail that gate closed.
   */
  private int parseLimit(String paramName, String defaultValue) {
    final String configured = getInitParameter(paramName, defaultValue);
    try {
      return Integer.parseInt(configured.trim());
    } catch (NumberFormatException e) {
      if (shouldReport(paramName, configured)) {
        LOG.limitNotANumber(paramName, sanitize(configured), defaultValue);
      }
      return Integer.parseInt(defaultValue);
    }
  }

  /**
   * @param paramName the offending service parameter
   * @param configuredValue its configured value, used to key the report
   * @return true only the first time this parameter/value pair is seen in this JVM, so each
   *         distinct configuration problem is reported once
   */
  private static boolean shouldReport(String paramName, String configuredValue) {
    return REPORTED_CONFIG_PROBLEMS.add(paramName + '=' + configuredValue);
  }

  /**
   * @param configuredValue a rejected configuration value that is about to be logged
   * @return the value with CR/LF folded to spaces and bounded in length, so that a multi-line XML
   *         {@code <value>} cannot forge additional gateway.log entries
   */
  private static String sanitize(String configuredValue) {
    if (configuredValue == null) {
      return null;
    }
    final String flattened = configuredValue.replaceAll("[\\r\\n]", " ");
    return flattened.length() > 80 ? flattened.substring(0, 80) + "..." : flattened;
  }

  /* test hook: the per-JVM report registry is static, so tests must be able to reset it */
  static void clearReportedConfigProblems() {
    REPORTED_CONFIG_PROBLEMS.clear();
  }

  /* test hook: proves a repeated config problem is reported once, not once per request */
  static int reportedConfigProblemCount() {
    return REPORTED_CONFIG_PROBLEMS.size();
  }

  /**
   * @param headerName the configured auth token header name
   * @return whether that name is a legal HTTP field name that this service may safely write
   */
  private boolean isUsableHeaderName(String headerName) {
    return HTTP_FIELD_NAME.matcher(headerName).matches()
        && !RESERVED_HEADER_NAMES.contains(headerName.toLowerCase(Locale.ROOT));
  }

  /**
   * @param headerName the configured auth token header name
   * @return whether that name is one this service already uses for the caller's identity, in which
   *         case emitting the token there would overwrite the identity header
   */
  private boolean collidesWithIdentityHeader(String headerName) {
    // HTTP header names are case insensitive, so a collision does not need to match exactly
    return headerName.equalsIgnoreCase(authHeaderActorIDName)
        || headerName.equalsIgnoreCase(authHeaderActorGroupsName)
        || headerName.equalsIgnoreCase(authHeaderActorGroupsPrefix)
        || headerName.toLowerCase(Locale.ROOT).startsWith(authHeaderActorGroupsPrefix.toLowerCase(Locale.ROOT) + '-');
  }

  private String createGroupsHeaderName(boolean useRoles, int index) {
    if (authHeaderActorGroupsName != null) {
      // explicit groups header takes precedence over the prefix and is used directly, without an index suffix
      return authHeaderActorGroupsName;
    } else if (useRoles || rolesLookupExecuted()) {
      return authHeaderActorGroupsPrefix;
    } else {
      return String.format(Locale.ROOT, ACTOR_GROUPS_HEADER_FORMAT, authHeaderActorGroupsPrefix, index + 1);
    }
  }

  private Collection<String> lookupRoles(String userName, Collection<String> groups) {
    Collection<String> roles = null;
      try {
        if (!rolesLookupExecuted() && ldapRolesLookupService != null && ldapRolesLookupService.enabled()) {
          roles = ldapRolesLookupService.lookupRoles(userName, groups);
        }
      } catch (Exception e) {
        // Couldn't lookup roles: log and return null so that the API will return the groups
        LOG.ldapRolesLookupFailed(userName, e);
      }
      return roles == null ? Collections.emptySet() : roles;
  }

  private boolean rolesLookupExecuted() {
    final Object rolesLookupExecutedReqAttribute = getRequest() == null ? null : getRequest().getAttribute(AbstractIdentityAssertionBase.ROLES_LOOKUP_EXECUTED);
    return rolesLookupExecutedReqAttribute != null && Boolean.parseBoolean(rolesLookupExecutedReqAttribute.toString());
  }

}
