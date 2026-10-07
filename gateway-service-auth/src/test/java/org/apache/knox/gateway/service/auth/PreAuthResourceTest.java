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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.security.auth.Subject;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;

import org.apache.knox.gateway.filter.security.AbstractIdentityAssertionBase;
import org.apache.knox.gateway.security.AuthTokenCredential;
import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.ldap.LDAPRolesLookupService;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

public class PreAuthResourceTest {

  private static final String USER_NAME = "test-username";
  private static final String AUTH_TOKEN_HEADER = "X-Knox-Auth-Token";
  private static final String GROUPS_HEADER = "X-Knox-Actor-Groups";
  private static final String TOKEN = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0LXVzZXJuYW1lIn0.c2lnbmF0dXJl";
  private ServletContext context;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private final Subject subject = new Subject();

  @Before
  public void setUp() {
    subject.getPrincipals().add(new PrimaryPrincipal(USER_NAME));
  }

  private void configureCommonExpectations(String actorIdHeaderName) throws Exception {
    configureCommonExpectations(actorIdHeaderName, null, Collections.emptySet());
  }

  private void configureCommonExpectations(String actorIdHeaderName, String groupsHeaderPrefix, Collection<String> groups) throws Exception {
    configureCommonExpectations(actorIdHeaderName, groupsHeaderPrefix, groups, null, false);
  }

  private void configureCommonExpectations(String actorIdHeaderName, String groupsHeaderPrefix, Collection<String> groups, GatewayServices gatewayServices, boolean rolesLookupExecuted) throws Exception {
    context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameter(PreAuthResource.AUTH_ACTOR_ID_HEADER_NAME)).andReturn(actorIdHeaderName).anyTimes();
    EasyMock.expect(context.getInitParameter(PreAuthResource.AUTH_ACTOR_GROUPS_HEADER_PREFIX)).andReturn(groupsHeaderPrefix).anyTimes();
    request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getAttribute(AbstractIdentityAssertionBase.ROLES_LOOKUP_EXECUTED)).andReturn(rolesLookupExecuted).anyTimes();
    response = EasyMock.createNiceMock(HttpServletResponse.class);

    if (SubjectUtils.getPrimaryPrincipalName(subject) != null) {
      final String expectedActorIdHeader = actorIdHeaderName == null ? PreAuthResource.DEFAULT_AUTH_ACTOR_ID_HEADER_NAME : actorIdHeaderName;
      response.setHeader(expectedActorIdHeader, USER_NAME);
      EasyMock.expectLastCall();
    }

    if (gatewayServices != null) {
      EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(gatewayServices);
      final LDAPRolesLookupService rolesLookupService = gatewayServices.getService(ServiceType.LDAP_ROLES_LOOKUP_SERVICE);
      if (rolesLookupService != null && rolesLookupService.enabled()) {
          final String expectedGroupsHeaderPrefix = (groupsHeaderPrefix == null ? PreAuthResource.DEFAULT_AUTH_ACTOR_GROUPS_HEADER_PREFIX : groupsHeaderPrefix);
          response.addHeader(EasyMock.eq(expectedGroupsHeaderPrefix), EasyMock.anyString());
          EasyMock.expectLastCall().times(1);
      }
      if (rolesLookupExecuted) {
        groups.forEach(group -> subject.getPrincipals().add(new GroupPrincipal(group)));
      }
    } else if (!groups.isEmpty()) {
      groups.forEach(group -> subject.getPrincipals().add(new GroupPrincipal(group)));
      final int groupStringSize = calculateGroupStringSize(groups);
      final int expectedGroupHeaderCount = groupStringSize / 1000 + 1;
      final String expectedGroupsHeaderPrefix = (groupsHeaderPrefix == null ? PreAuthResource.DEFAULT_AUTH_ACTOR_GROUPS_HEADER_PREFIX : groupsHeaderPrefix)
              + "-";
      for (int i = 1; i <= expectedGroupHeaderCount; i++) {
        response.addHeader(EasyMock.eq(expectedGroupsHeaderPrefix + i), EasyMock.anyString());
        EasyMock.expectLastCall();
      }
    }

    EasyMock.replay(context, request, response);
  }

  private int calculateGroupStringSize(Collection<String> groups) {
    final AtomicInteger size = new AtomicInteger(0);
    groups.forEach(group -> size.addAndGet(group.length()));
    size.addAndGet(groups.size() - 1); // commas
    return size.get();
  }

  @Test
  public void testSubjectWithoutPrimaryPrincipalReturnsUnauthorized() throws Exception {
    subject.getPrincipals().clear();
    configureCommonExpectations(null);
    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    final Response response = executeResourceWithSubject(preAuthResource);
    assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
  }

  @Test
  public void testPopulatingDefaultActorIdHeaderNoGroups() throws Exception {
    configureCommonExpectations(null);
    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    executeResourceWithSubject(preAuthResource);
    EasyMock.verify(response);
  }

  @Test
  public void testPopulatingCustomActorIdHeaderNoGroups() throws Exception {
    configureCommonExpectations("customActorId");
    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    executeResourceWithSubject(preAuthResource);
    EasyMock.verify(response);
  }

  @Test
  public void testPopulatingDefaultGroupsHeader() throws Exception {
    configureCommonExpectations(null, null, Collections.singleton("group1"));
    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    executeResourceWithSubject(preAuthResource);
    EasyMock.verify(response);
  }

  @Test
  public void testPopulatingCustomGroupsHeader() throws Exception {
    configureCommonExpectations(null, "customGroupHeader", Collections.singleton("group1"));
    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    executeResourceWithSubject(preAuthResource);
    EasyMock.verify(response);
  }

  @Test
  public void testExplicitGroupsHeaderTakesPrecedenceOverPrefix() throws Exception {
    final String explicitGroupsHeader = "X-Knox-Actor-Groups";
    subject.getPrincipals().add(new GroupPrincipal("group1"));

    context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameter(PreAuthResource.AUTH_ACTOR_GROUPS_HEADER_NAME)).andReturn(explicitGroupsHeader).anyTimes();
    // a prefix is configured as well, to prove the explicit header wins and no index suffix is appended
    EasyMock.expect(context.getInitParameter(PreAuthResource.AUTH_ACTOR_GROUPS_HEADER_PREFIX)).andReturn("X-Knox-Prefixed-Groups").anyTimes();
    request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getAttribute(AbstractIdentityAssertionBase.ROLES_LOOKUP_EXECUTED)).andReturn(false).anyTimes();
    response = EasyMock.createNiceMock(HttpServletResponse.class);
    response.setHeader(PreAuthResource.DEFAULT_AUTH_ACTOR_ID_HEADER_NAME, USER_NAME);
    EasyMock.expectLastCall();
    // the explicit header name is used directly, without an index suffix
    response.addHeader(EasyMock.eq(explicitGroupsHeader), EasyMock.anyString());
    EasyMock.expectLastCall().times(1);
    EasyMock.replay(context, request, response);

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    executeResourceWithSubject(preAuthResource);
    EasyMock.verify(response);
  }

  @Test
  public void testPopulatingGroupsWithRoles() throws Exception {
    final String rolesHeader = "X-Knox-Roles";
    final GatewayServices gatewayServices = configureLdapRolesLookupExpectations(false);
    configureCommonExpectations(PreAuthResource.DEFAULT_AUTH_ACTOR_ID_HEADER_NAME, rolesHeader, Collections.singleton("engineering"), gatewayServices, false);
    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    Response preAuthResponse = executeResourceWithSubject(preAuthResource);
    assertEquals(HttpServletResponse.SC_OK, preAuthResponse.getStatus());
    EasyMock.verify(response);
  }

  @Test
  public void testRolesAreNotPopulatedTwice() throws Exception {
    final String rolesHeader = "X-Knox-Roles";
    final GatewayServices gatewayServices = configureLdapRolesLookupExpectations(true);
    configureCommonExpectations(PreAuthResource.DEFAULT_AUTH_ACTOR_ID_HEADER_NAME, rolesHeader, Collections.singleton("engineering"), gatewayServices, true);
    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    Response preAuthResponse = executeResourceWithSubject(preAuthResource);
    assertEquals(HttpServletResponse.SC_OK, preAuthResponse.getStatus());
    EasyMock.verify(response);
  }

  private GatewayServices configureLdapRolesLookupExpectations(boolean roleLookupExecuted) throws Exception {
    final String role1 = "platform:admin";
    final String role2 = "ml-workspace:viewer";
    final LDAPRolesLookupService rolesLookupService = EasyMock.createMock(LDAPRolesLookupService.class);
    EasyMock.expect(rolesLookupService.enabled()).andReturn(true).anyTimes();
    if (!roleLookupExecuted) {
      EasyMock.expect(rolesLookupService.lookupRoles(EasyMock.eq(USER_NAME), EasyMock.anyObject())).andReturn(Arrays.asList(role1, role2)).anyTimes();
    }

    final GatewayServices gatewayServices = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(gatewayServices.getService(ServiceType.LDAP_ROLES_LOOKUP_SERVICE)).andReturn(rolesLookupService).anyTimes();

    EasyMock.replay(rolesLookupService, gatewayServices);
    return gatewayServices;
  }

  @Test
  public void testPopulatingMultipleGroupsHeaderWithLargeGroupNames() throws Exception {
    doTestPopulatingMultipleGroupsHeaderWithLargeGroupNames(100);
    doTestPopulatingMultipleGroupsHeaderWithLargeGroupNames(500);
  }

  private void doTestPopulatingMultipleGroupsHeaderWithLargeGroupNames(int numberOfGroupsToAdd) throws Exception {
    final Set<String> groups = new HashSet<>();
    for (int i = 1; i <= numberOfGroupsToAdd; i++) {
      groups.add("longGroupName" + i);
    }
    configureCommonExpectations(null, null, groups);
    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    executeResourceWithSubject(preAuthResource);
    EasyMock.verify(response);
  }

  @Test
  public void testPopulatingAuthTokenHeader() throws Exception {
    subject.getPrivateCredentials().add(new AuthTokenCredential(TOKEN));
    final Map<String, String> headers = configureAuthTokenExpectations(AUTH_TOKEN_HEADER);

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    assertEquals(HttpServletResponse.SC_OK, executeResourceWithSubject(preAuthResource).getStatus());
    assertEquals("The bare serialized token should be emitted, with no Bearer prefix",
        TOKEN, headers.get(AUTH_TOKEN_HEADER));
  }

  @Test
  public void testAuthTokenHeaderIsNotPopulatedWhenParamIsUnset() throws Exception {
    subject.getPrivateCredentials().add(new AuthTokenCredential(TOKEN));
    final Map<String, String> headers = configureAuthTokenExpectations(null);

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    assertEquals(HttpServletResponse.SC_OK, executeResourceWithSubject(preAuthResource).getStatus());
    assertEquals("Only the actor id header is expected when the token header is not configured",
        Collections.singleton(PreAuthResource.DEFAULT_AUTH_ACTOR_ID_HEADER_NAME), headers.keySet());
  }

  @Test
  public void testAuthTokenHeaderIsNotPopulatedWithoutAToken() throws Exception {
    final Map<String, String> headers = configureAuthTokenExpectations(AUTH_TOKEN_HEADER);

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    assertEquals("A subject with no captured token is still authenticated",
        HttpServletResponse.SC_OK, executeResourceWithSubject(preAuthResource).getStatus());
    assertNull("No token to emit", headers.get(AUTH_TOKEN_HEADER));
  }

  @Test
  public void testAuthTokenHeaderIsOmittedWhenTokenExceedsTheSizeLimit() throws Exception {
    final String oversizedToken = buildToken(200);
    subject.getPrivateCredentials().add(new AuthTokenCredential(oversizedToken));
    final Map<String, String> headers = configureAuthTokenExpectations(AUTH_TOKEN_HEADER,
        Collections.singletonMap(AbstractAuthResource.AUTH_TOKEN_SIZE_LIMIT, "100"));

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    assertEquals("An oversized token must not fail the request",
        HttpServletResponse.SC_OK, executeResourceWithSubject(preAuthResource).getStatus());
    assertNull("A token larger than the configured limit must be omitted rather than truncated",
        headers.get(AUTH_TOKEN_HEADER));
    assertEquals("The actor id header must still be emitted",
        USER_NAME, headers.get(PreAuthResource.DEFAULT_AUTH_ACTOR_ID_HEADER_NAME));
  }

  @Test
  public void testAuthTokenSizeLimitCanBeDisabled() throws Exception {
    final String oversizedToken = buildToken(200);
    subject.getPrivateCredentials().add(new AuthTokenCredential(oversizedToken));
    final Map<String, String> headers = configureAuthTokenExpectations(AUTH_TOKEN_HEADER,
        Collections.singletonMap(AbstractAuthResource.AUTH_TOKEN_SIZE_LIMIT, "-1"));

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    assertEquals(HttpServletResponse.SC_OK, executeResourceWithSubject(preAuthResource).getStatus());
    assertEquals("A negative limit turns the size check off", oversizedToken, headers.get(AUTH_TOKEN_HEADER));
  }

  @Test
  public void testTokenIsWithinTheDefaultSizeLimit() throws Exception {
    // 6 KB is the default budget; a typical JWT is well under it
    subject.getPrivateCredentials().add(new AuthTokenCredential(TOKEN));
    final Map<String, String> headers = configureAuthTokenExpectations(AUTH_TOKEN_HEADER);

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    assertEquals(HttpServletResponse.SC_OK, executeResourceWithSubject(preAuthResource).getStatus());
    assertEquals(TOKEN, headers.get(AUTH_TOKEN_HEADER));
  }

  @Test
  public void testAuthTokenHeaderIsOmittedWhenItWouldClobberTheActorIdHeader() throws Exception {
    subject.getPrivateCredentials().add(new AuthTokenCredential(TOKEN));
    // deliberately misconfigured: the token header name is the actor id header name
    final Map<String, String> headers = configureAuthTokenExpectations(PreAuthResource.DEFAULT_AUTH_ACTOR_ID_HEADER_NAME);

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    assertEquals(HttpServletResponse.SC_OK, executeResourceWithSubject(preAuthResource).getStatus());
    assertEquals("The identity header must win over a colliding token header name",
        USER_NAME, headers.get(PreAuthResource.DEFAULT_AUTH_ACTOR_ID_HEADER_NAME));
  }

  @Test
  public void testAuthTokenHeaderIsOmittedWhenItWouldClobberTheGroupsHeader() throws Exception {
    subject.getPrivateCredentials().add(new AuthTokenCredential(TOKEN));
    subject.getPrincipals().add(new GroupPrincipal("group1"));
    final Map<String, String> groupHeaders = new LinkedHashMap<>();
    final Map<String, String> headers = configureAuthTokenExpectations(GROUPS_HEADER,
        Collections.singletonMap(AbstractAuthResource.AUTH_ACTOR_GROUPS_HEADER_NAME, GROUPS_HEADER), groupHeaders);

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    assertEquals(HttpServletResponse.SC_OK, executeResourceWithSubject(preAuthResource).getStatus());
    assertNull("A token header name colliding with the groups header must be dropped",
        headers.get(GROUPS_HEADER));
    assertEquals("The groups header must still be emitted", "group1", groupHeaders.get(GROUPS_HEADER));
  }

  @Test
  public void testAMalformedSizeLimitFallsBackToTheDefaultInsteadOfFailingTheRequest() throws Exception {
    subject.getPrivateCredentials().add(new AuthTokenCredential(TOKEN));
    final Map<String, String> headers = configureAuthTokenExpectations(AUTH_TOKEN_HEADER,
        Collections.singletonMap(AbstractAuthResource.AUTH_TOKEN_SIZE_LIMIT, "6kb"));

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    assertEquals("A malformed size limit must not take the endpoint down",
        HttpServletResponse.SC_OK, executeResourceWithSubject(preAuthResource).getStatus());
    assertEquals("The default limit applies when the configured one cannot be parsed",
        TOKEN, headers.get(AUTH_TOKEN_HEADER));
  }

  @Test
  public void testAZeroSizeLimitTurnsTheCheckOffRatherThanSuppressingEveryToken() throws Exception {
    subject.getPrivateCredentials().add(new AuthTokenCredential(TOKEN));
    final Map<String, String> headers = configureAuthTokenExpectations(AUTH_TOKEN_HEADER,
        Collections.singletonMap(AbstractAuthResource.AUTH_TOKEN_SIZE_LIMIT, "0"));

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    assertEquals(HttpServletResponse.SC_OK, executeResourceWithSubject(preAuthResource).getStatus());
    assertEquals("Any non-positive limit disables the check; it must not suppress every token and warn per request",
        TOKEN, headers.get(AUTH_TOKEN_HEADER));
  }

  @Test
  public void testATokenExactlyAtTheLimitIsStillEmitted() throws Exception {
    final String token = buildToken(100);
    subject.getPrivateCredentials().add(new AuthTokenCredential(token));
    final Map<String, String> headers = configureAuthTokenExpectations(AUTH_TOKEN_HEADER,
        Collections.singletonMap(AbstractAuthResource.AUTH_TOKEN_SIZE_LIMIT, "100"));

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    assertEquals(HttpServletResponse.SC_OK, executeResourceWithSubject(preAuthResource).getStatus());
    assertEquals("The limit is inclusive", token, headers.get(AUTH_TOKEN_HEADER));
  }

  @Test
  public void testTokenBearingResponseIsNotCacheable() throws Exception {
    subject.getPrivateCredentials().add(new AuthTokenCredential(TOKEN));
    final Map<String, String> headers = configureAuthTokenExpectations(AUTH_TOKEN_HEADER);

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    assertEquals(HttpServletResponse.SC_OK, executeResourceWithSubject(preAuthResource).getStatus());
    assertEquals("RFC 6749 section 5.1: a response carrying a token must not be cached",
        "no-store", headers.get("Cache-Control"));
  }

  @Test
  public void testResponseWithoutATokenKeepsItsExistingCacheSemantics() throws Exception {
    final Map<String, String> headers = configureAuthTokenExpectations(AUTH_TOKEN_HEADER);

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    assertEquals(HttpServletResponse.SC_OK, executeResourceWithSubject(preAuthResource).getStatus());
    assertNull("No token was emitted, so nothing about caching changes", headers.get("Cache-Control"));
  }

  @Test
  public void testAuthTokenHeaderIsOmittedWhenTheNameIsNotALegalHeaderName() throws Exception {
    // a name pasted from YAML with a stray space, and one carrying a CRLF injection attempt
    for (String illegalName : Arrays.asList("X-Knox Auth Token", "X-Knox-Auth-Token\r\nInjected: 1", "X-Knox-Auth-Token:")) {
      final Subject freshSubject = new Subject();
      freshSubject.getPrincipals().add(new PrimaryPrincipal(USER_NAME));
      freshSubject.getPrivateCredentials().add(new AuthTokenCredential(TOKEN));
      final Map<String, String> headers = configureAuthTokenExpectations(illegalName);

      final PreAuthResource preAuthResource = new PreAuthResource();
      preAuthResource.context = context;
      preAuthResource.response = response;
      preAuthResource.request = request;
      Subject.doAs(freshSubject, (PrivilegedExceptionAction<Object>) () -> {
        preAuthResource.init();
        return preAuthResource.doGet();
      });
      assertNull("An illegal header name must be rejected, not passed to setHeader: " + illegalName,
          headers.get(illegalName));
    }
  }

  @Test
  public void testAuthTokenHeaderIsOmittedWhenTheNameIsAProtocolHeader() throws Exception {
    subject.getPrivateCredentials().add(new AuthTokenCredential(TOKEN));
    final Map<String, String> headers = configureAuthTokenExpectations("Set-Cookie");

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    assertEquals(HttpServletResponse.SC_OK, executeResourceWithSubject(preAuthResource).getStatus());
    assertNull("Writing the token into Set-Cookie would hand it to the browser with no cookie attributes",
        headers.get("Set-Cookie"));
  }

  @Test
  public void testCollisionWithACustomGroupsHeaderThatDoesNotShareTheDefaultPrefix() throws Exception {
    subject.getPrivateCredentials().add(new AuthTokenCredential(TOKEN));
    final Map<String, String> initParams = new LinkedHashMap<>();
    initParams.put(AbstractAuthResource.AUTH_ACTOR_GROUPS_HEADER_NAME, "X-My-Groups");
    initParams.put(AbstractAuthResource.AUTH_ACTOR_GROUPS_HEADER_PREFIX, "X-Knox-Prefixed-Groups");
    final Map<String, String> headers = configureAuthTokenExpectations("X-My-Groups", initParams);

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    assertEquals(HttpServletResponse.SC_OK, executeResourceWithSubject(preAuthResource).getStatus());
    assertNull("The explicit groups header name must be protected on its own, not via the prefix",
        headers.get("X-My-Groups"));
  }

  @Test
  public void testCollisionWithAnIndexedGroupsHeaderName() throws Exception {
    subject.getPrivateCredentials().add(new AuthTokenCredential(TOKEN));
    // X-Knox-Actor-Groups-1 is the form createGroupsHeaderName emits in the multi-header case
    final Map<String, String> headers = configureAuthTokenExpectations("X-Knox-Actor-Groups-1");

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    assertEquals(HttpServletResponse.SC_OK, executeResourceWithSubject(preAuthResource).getStatus());
    assertNull("An indexed groups header name must be caught by the prefix clause",
        headers.get("X-Knox-Actor-Groups-1"));
  }

  @Test
  public void testCollisionDetectionIsCaseInsensitive() throws Exception {
    subject.getPrivateCredentials().add(new AuthTokenCredential(TOKEN));
    // HTTP field names are case insensitive, so this is the same header as X-Knox-Actor-ID
    final Map<String, String> headers = configureAuthTokenExpectations("x-knox-actor-id");

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    assertEquals(HttpServletResponse.SC_OK, executeResourceWithSubject(preAuthResource).getStatus());
    assertNull("A differently-cased collision is still a collision", headers.get("x-knox-actor-id"));
    assertEquals("The identity header must survive", USER_NAME,
        headers.get(PreAuthResource.DEFAULT_AUTH_ACTOR_ID_HEADER_NAME));
  }

  @Test
  public void testATokenCarryingCrlfIsNeverWrittenToAResponseHeader() throws Exception {
    // parseFromHTTPBasicCredentials returns Basic "Token:<value>" verbatim with no isJWT() check,
    // so a value with CR/LF spliced into it can reach here stamped as the caller's credential.
    // Response splitting in the endpoint an ext_authz proxy trusts must not depend on the
    // container happening to sanitize it.
    for (String hostile : Arrays.asList(TOKEN + "\r\nX-Injected: 1", TOKEN + "\nX-Injected: 1",
        TOKEN + "\r\n", "eyJ\u0000hdr.body.sig", TOKEN + " trailing")) {
      final Subject freshSubject = new Subject();
      freshSubject.getPrincipals().add(new PrimaryPrincipal(USER_NAME));
      freshSubject.getPrivateCredentials().add(new AuthTokenCredential(hostile));
      final Map<String, String> headers = configureAuthTokenExpectations(AUTH_TOKEN_HEADER);

      final PreAuthResource preAuthResource = new PreAuthResource();
      preAuthResource.context = context;
      preAuthResource.response = response;
      preAuthResource.request = request;
      Subject.doAs(freshSubject, (PrivilegedExceptionAction<Object>) () -> {
        preAuthResource.init();
        return preAuthResource.doGet();
      });
      assertNull("A token that is not a well-formed JWS compact serialization must not be emitted: "
          + hostile.replace("\r", "\\r").replace("\n", "\\n"), headers.get(AUTH_TOKEN_HEADER));
      assertEquals("The identity header must still be asserted", USER_NAME,
          headers.get(PreAuthResource.DEFAULT_AUTH_ACTOR_ID_HEADER_NAME));
    }
  }

  @Test
  public void testAWellFormedCompactJwsIsStillEmitted() throws Exception {
    // guards the validation above against over-tightening: all three JWS segment alphabets,
    // including base64url '-' and '_' and an unsigned token's empty signature
    for (String legal : Arrays.asList(TOKEN, "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhIn0.",
        "ab-cd_ef.gh-ij_kl.mn-op_qr")) {
      final Subject freshSubject = new Subject();
      freshSubject.getPrincipals().add(new PrimaryPrincipal(USER_NAME));
      freshSubject.getPrivateCredentials().add(new AuthTokenCredential(legal));
      final Map<String, String> headers = configureAuthTokenExpectations(AUTH_TOKEN_HEADER);

      final PreAuthResource preAuthResource = new PreAuthResource();
      preAuthResource.context = context;
      preAuthResource.response = response;
      preAuthResource.request = request;
      Subject.doAs(freshSubject, (PrivilegedExceptionAction<Object>) () -> {
        preAuthResource.init();
        return preAuthResource.doGet();
      });
      assertEquals("A well-formed compact JWS must still be forwarded: " + legal,
          legal, headers.get(AUTH_TOKEN_HEADER));
    }
  }

  @Test
  public void testAuthTokenHeaderIsOmittedWhenTheNameIsOneThisServiceOverwritesItself() throws Exception {
    // Cache-Control is set by the token branch itself, so configuring the token into it would be
    // silently clobbered one line later; Connection and Content-Type corrupt the response instead.
    for (String name : Arrays.asList("Cache-Control", "Connection", "Upgrade", "Content-Type", "Content-Encoding")) {
      subject.getPrivateCredentials().clear();
      subject.getPrivateCredentials().add(new AuthTokenCredential(TOKEN));
      final Map<String, String> headers = configureAuthTokenExpectations(name);

      final PreAuthResource preAuthResource = new PreAuthResource();
      preAuthResource.context = context;
      preAuthResource.response = response;
      preAuthResource.request = request;
      assertEquals(HttpServletResponse.SC_OK, executeResourceWithSubject(preAuthResource).getStatus());
      assertNull("The token must not be written into " + name, headers.get(name));
    }
  }

  @Test
  public void testAMalformedGroupHeaderLimitDoesNotFailEveryRequest() throws Exception {
    // the sibling of the auth-token size limit: initialize() runs per request, so an unguarded
    // parseInt here 500s every proxied request and fails an ext_authz gate closed
    for (String param : Arrays.asList(AbstractAuthResource.GROUP_HEADER_LENGTH_LIMIT,
        AbstractAuthResource.GROUP_HEADER_SIZE_LIMIT)) {
      final Map<String, String> headers = configureAuthTokenExpectations(AUTH_TOKEN_HEADER,
          Collections.singletonMap(param, "1,000"));

      final PreAuthResource preAuthResource = new PreAuthResource();
      preAuthResource.context = context;
      preAuthResource.response = response;
      preAuthResource.request = request;
      assertEquals("A malformed " + param + " must not take the endpoint down",
          HttpServletResponse.SC_OK, executeResourceWithSubject(preAuthResource).getStatus());
      assertEquals(USER_NAME, headers.get(PreAuthResource.DEFAULT_AUTH_ACTOR_ID_HEADER_NAME));
    }
  }

  @Test
  public void testAReservedActorIdHeaderNameFallsBackToTheDefault() throws Exception {
    // Content-Length is reserved for the token header precisely because it is dangerous; the same
    // name on the actor id header would write a user name into the response framing
    final Map<String, String> headers = configureAuthTokenExpectations(AUTH_TOKEN_HEADER,
        Collections.singletonMap(AbstractAuthResource.AUTH_ACTOR_ID_HEADER_NAME, "Content-Length"));

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    assertEquals(HttpServletResponse.SC_OK, executeResourceWithSubject(preAuthResource).getStatus());
    assertNull("A user name must never be written into Content-Length", headers.get("Content-Length"));
    assertEquals("The actor id falls back to the default name rather than being lost", USER_NAME,
        headers.get(PreAuthResource.DEFAULT_AUTH_ACTOR_ID_HEADER_NAME));
  }

  @Test
  public void testAnIllegalGroupsHeaderNameIsDropped() throws Exception {
    final Map<String, String> initParams = new LinkedHashMap<>();
    initParams.put(AbstractAuthResource.AUTH_ACTOR_GROUPS_HEADER_NAME, "X-My Groups");
    final Map<String, String> headers = configureAuthTokenExpectations(AUTH_TOKEN_HEADER, initParams);
    subject.getPrincipals().add(new GroupPrincipal("analysts"));

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    preAuthResource.request = request;
    assertEquals(HttpServletResponse.SC_OK, executeResourceWithSubject(preAuthResource).getStatus());
    assertNull("An illegal groups header name must not reach addHeader", headers.get("X-My Groups"));
  }

  @Test
  public void testAConfigProblemIsWarnedAboutOnceRatherThanOnEveryRequest() throws Exception {
    // initialize() runs per request (neither resource is @Singleton), so warning inline would turn
    // one typo into an unbounded log flood that fills the gateway's disk
    AbstractAuthResource.clearReportedConfigProblems();
    for (int i = 0; i < 5; i++) {
      configureAuthTokenExpectations("X Knox Token");
      final PreAuthResource preAuthResource = new PreAuthResource();
      preAuthResource.context = context;
      preAuthResource.response = response;
      preAuthResource.request = request;
      executeResourceWithSubject(preAuthResource);
    }
    assertEquals("The same config problem must be reported once, not once per request",
        1, AbstractAuthResource.reportedConfigProblemCount());
  }

  /**
   * @param length the exact character length the token should have
   * @return a well-formed JWS compact serialization of that length. The size-limit tests are about
   *         length, not shape, but the resource validates the shape too, so the padding has to sit
   *         inside a real three-segment token rather than being a bare run of characters.
   */
  private static String buildToken(int length) {
    final StringBuilder token = new StringBuilder(length);
    for (int i = 0; i < length - 2; i++) {
      token.append('t');
    }
    return token.append("..").toString();   // header.<empty payload>.<empty signature>
  }

  /**
   * @param authTokenHeaderName the configured auth token header name, or null to leave it unset
   * @return the live map of headers set on the response
   */
  private Map<String, String> configureAuthTokenExpectations(String authTokenHeaderName) {
    return configureAuthTokenExpectations(authTokenHeaderName, Collections.emptyMap());
  }

  /**
   * @param authTokenHeaderName the configured auth token header name, or null to leave it unset
   * @param extraInitParams any further service params to configure
   * @return the live map of headers set on the response
   */
  private Map<String, String> configureAuthTokenExpectations(String authTokenHeaderName, Map<String, String> extraInitParams) {
    return configureAuthTokenExpectations(authTokenHeaderName, extraInitParams, null);
  }

  /**
   * @param authTokenHeaderName the configured auth token header name, or null to leave it unset
   * @param extraInitParams any further service params to configure
   * @param addedHeaders when non-null, collects the headers set via {@code addHeader} (groups/roles)
   * @return the live map of headers set via {@code setHeader}
   */
  private Map<String, String> configureAuthTokenExpectations(String authTokenHeaderName, Map<String, String> extraInitParams,
      Map<String, String> addedHeaders) {
    final Map<String, String> headers = new LinkedHashMap<>();
    final Map<String, String> initParams = new LinkedHashMap<>(extraInitParams);
    if (authTokenHeaderName != null) {
      initParams.put(AbstractAuthResource.AUTH_TOKEN_HEADER_NAME, authTokenHeaderName);
    }
    context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameter(EasyMock.anyString()))
        .andAnswer(() -> initParams.get(EasyMock.getCurrentArguments()[0])).anyTimes();
    request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getAttribute(AbstractIdentityAssertionBase.ROLES_LOOKUP_EXECUTED)).andReturn(false).anyTimes();
    response = EasyMock.createNiceMock(HttpServletResponse.class);
    response.setHeader(EasyMock.anyString(), EasyMock.anyString());
    EasyMock.expectLastCall().andAnswer(() -> {
      headers.put((String) EasyMock.getCurrentArguments()[0], (String) EasyMock.getCurrentArguments()[1]);
      return null;
    }).anyTimes();
    if (addedHeaders != null) {
      response.addHeader(EasyMock.anyString(), EasyMock.anyString());
      EasyMock.expectLastCall().andAnswer(() -> {
        addedHeaders.put((String) EasyMock.getCurrentArguments()[0], (String) EasyMock.getCurrentArguments()[1]);
        return null;
      }).anyTimes();
    }
    EasyMock.replay(context, request, response);
    return headers;
  }

  private Response executeResourceWithSubject(final PreAuthResource preAuthResource) throws PrivilegedActionException {
    return (Response) Subject.doAs(subject, new PrivilegedExceptionAction<Object>() {

      @Override
      public Object run() throws Exception {
        preAuthResource.init();
        return preAuthResource.doGet();
      }

    });
  }

}
