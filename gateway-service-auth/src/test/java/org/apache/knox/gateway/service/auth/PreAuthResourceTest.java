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
import static org.junit.Assert.assertTrue;

import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.security.auth.Subject;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.ldap.LDAPRolesLookupService;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

public class PreAuthResourceTest {

  private static final String USER_NAME = "test-username";
  private ServletContext context;
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
    configureCommonExpectations(actorIdHeaderName, groupsHeaderPrefix, groups, null);
  }

  private void configureCommonExpectations(String actorIdHeaderName, String groupsHeaderPrefix, Collection<String> groups, GatewayServices gatewayServices) throws Exception {
    context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameter(PreAuthResource.AUTH_ACTOR_ID_HEADER_NAME)).andReturn(actorIdHeaderName).anyTimes();
    EasyMock.expect(context.getInitParameter(PreAuthResource.AUTH_ACTOR_GROUPS_HEADER_PREFIX)).andReturn(groupsHeaderPrefix).anyTimes();
    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    response = EasyMock.createNiceMock(HttpServletResponse.class);

    if (SubjectUtils.getPrimaryPrincipalName(subject) != null) {
      final String expectedActorIdHeader = actorIdHeaderName == null ? PreAuthResource.DEFAULT_AUTH_ACTOR_ID_HEADER_NAME : actorIdHeaderName;
      response.setHeader(expectedActorIdHeader, USER_NAME);
      EasyMock.expectLastCall();
    }

    if (gatewayServices != null) {
      EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(gatewayServices);
      groups.forEach(group -> subject.getPrincipals().add(new GroupPrincipal(group)));
      if (!groups.isEmpty()) {
        final String expectedGroupsHeaderPrefix = (groupsHeaderPrefix == null ? PreAuthResource.DEFAULT_AUTH_ACTOR_GROUPS_HEADER_PREFIX : groupsHeaderPrefix);
        response.addHeader(EasyMock.eq(expectedGroupsHeaderPrefix), EasyMock.anyString());
        EasyMock.expectLastCall();
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
    final Response response = executeResourceWithSubject(preAuthResource);
    assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
  }

  @Test
  public void testPopulatingDefaultActorIdHeaderNoGroups() throws Exception {
    configureCommonExpectations(null);
    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    executeResourceWithSubject(preAuthResource);
    EasyMock.verify(response);
  }

  @Test
  public void testPopulatingCustomActorIdHeaderNoGroups() throws Exception {
    configureCommonExpectations("customActorId");
    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    executeResourceWithSubject(preAuthResource);
    EasyMock.verify(response);
  }

  @Test
  public void testPopulatingDefaultGroupsHeader() throws Exception {
    configureCommonExpectations(null, null, Collections.singleton("group1"));
    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    executeResourceWithSubject(preAuthResource);
    EasyMock.verify(response);
  }

  @Test
  public void testPopulatingCustomGroupsHeader() throws Exception {
    configureCommonExpectations(null, "customGroupHeader", Collections.singleton("group1"));
    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    executeResourceWithSubject(preAuthResource);
    EasyMock.verify(response);
  }

  @Test
  public void testPopulatingGroupsWithRoles() throws Exception {
    final GatewayServices gatewayServices = configureLdapRolesLookupExpectations();
    configureCommonExpectations(PreAuthResource.DEFAULT_AUTH_ACTOR_ID_HEADER_NAME, null, Collections.singleton("engineering"), gatewayServices);
    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    Response preAuthResponse = executeResourceWithSubject(preAuthResource);
    assertEquals(HttpServletResponse.SC_OK, preAuthResponse.getStatus());
    EasyMock.verify(response);
  }

  @Test
  public void testInheritedRolesFromSubjectAllAppearInSingleRolesHeader() throws Exception {
    final String directRole = "platform:admin";
    final String inheritedRole = "ml-workspace:viewer";
    subject.getPrincipals().add(new GroupPrincipal(directRole));
    subject.getPrincipals().add(new GroupPrincipal(inheritedRole));

    final LDAPRolesLookupService rolesLookupService = EasyMock.createMock(LDAPRolesLookupService.class);
    EasyMock.expect(rolesLookupService.enabled()).andReturn(true).anyTimes();

    final GatewayServices gatewayServices = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(gatewayServices.getService(ServiceType.LDAP_ROLES_LOOKUP_SERVICE)).andReturn(rolesLookupService).anyTimes();

    context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(gatewayServices).anyTimes();
    EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(mockConfigWithRolesLookupInterceptor()).anyTimes();

    response = EasyMock.createMock(HttpServletResponse.class);
    response.setHeader(PreAuthResource.DEFAULT_AUTH_ACTOR_ID_HEADER_NAME, USER_NAME);
    EasyMock.expectLastCall();
    final Capture<String> headerValue = EasyMock.newCapture(CaptureType.ALL);
    response.addHeader(EasyMock.eq(PreAuthResource.DEFAULT_AUTH_ACTOR_GROUPS_HEADER_PREFIX), EasyMock.capture(headerValue));
    EasyMock.expectLastCall();

    EasyMock.replay(rolesLookupService, gatewayServices, context, response);

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    executeResourceWithSubject(preAuthResource);

    final List<String> allHeaders = headerValue.getValues();
    final String joined = String.join(",", allHeaders);
    assertTrue("directRole missing from actor-groups header: " + joined, joined.contains(directRole));
    assertTrue("inheritedRole missing from actor-groups header (KNOX-3374): " + joined, joined.contains(inheritedRole));
    EasyMock.verify(response, rolesLookupService);
  }

  private static GatewayConfig mockConfigWithRolesLookupInterceptor() {
    final String name = "rolesLookupInterceptor";
    final GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.getLDAPInterceptorNames()).andReturn(Collections.singletonList(name)).anyTimes();
    EasyMock.expect(config.getLDAPInterceptorConfig(name)).andReturn(Collections.singletonMap("interceptorType", "rolesLookup")).anyTimes();
    EasyMock.replay(config);
    return config;
  }

  @Test
  public void testFallsBackToDirectRolesLookupWhenSubjectHasNoGroups() throws Exception {
    final String role1 = "platform:admin";
    final String role2 = "ml-workspace:viewer";

    final LDAPRolesLookupService rolesLookupService = EasyMock.createMock(LDAPRolesLookupService.class);
    EasyMock.expect(rolesLookupService.enabled()).andReturn(true).anyTimes();
    EasyMock.expect(rolesLookupService.lookupRoles(EasyMock.eq(USER_NAME), EasyMock.eq(Collections.emptySet())))
        .andReturn(Arrays.asList(role1, role2));

    final GatewayServices gatewayServices = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(gatewayServices.getService(ServiceType.LDAP_ROLES_LOOKUP_SERVICE)).andReturn(rolesLookupService).anyTimes();

    context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(gatewayServices).anyTimes();

    response = EasyMock.createMock(HttpServletResponse.class);
    response.setHeader(PreAuthResource.DEFAULT_AUTH_ACTOR_ID_HEADER_NAME, USER_NAME);
    EasyMock.expectLastCall();
    final Capture<String> headerValue = EasyMock.newCapture(CaptureType.ALL);
    response.addHeader(EasyMock.eq(PreAuthResource.DEFAULT_AUTH_ACTOR_GROUPS_HEADER_PREFIX), EasyMock.capture(headerValue));
    EasyMock.expectLastCall();

    EasyMock.replay(rolesLookupService, gatewayServices, context, response);

    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    executeResourceWithSubject(preAuthResource);

    final String joined = String.join(",", headerValue.getValues());
    assertTrue("role1 missing from fallback lookup: " + joined, joined.contains(role1));
    assertTrue("role2 missing from fallback lookup: " + joined, joined.contains(role2));
    EasyMock.verify(response, rolesLookupService);
  }

  private GatewayServices configureLdapRolesLookupExpectations() throws Exception {
    final LDAPRolesLookupService rolesLookupService = EasyMock.createNiceMock(LDAPRolesLookupService.class);
    EasyMock.expect(rolesLookupService.enabled()).andReturn(true).anyTimes();
    EasyMock.expect(rolesLookupService.lookupRoles(EasyMock.eq(USER_NAME), EasyMock.<Collection<String>>anyObject()))
        .andReturn(Arrays.asList("platform:admin", "ml-workspace:viewer")).anyTimes();

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
    executeResourceWithSubject(preAuthResource);
    EasyMock.verify(response);
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
