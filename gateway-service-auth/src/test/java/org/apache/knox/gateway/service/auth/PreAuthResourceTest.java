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

import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.security.auth.Subject;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;

import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.security.SubjectUtils;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

public class PreAuthResourceTest {

  private static final String USER_NAME = "test-username";
  private ServletContext context;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private final Subject subject = new Subject();

  @Before
  public void setUp() {
    subject.getPrincipals().add(new PrimaryPrincipal(USER_NAME));
  }

  private void configureCommonExpectations(String actorIdHeaderName, String groupsHeaderPrefix) {
    configureCommonExpectations(actorIdHeaderName, groupsHeaderPrefix, Collections.emptySet());
  }

  private void configureCommonExpectations(String actorIdHeaderName, String groupsHeaderPrefix, Collection<String> groups) {
    context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameter(PreAuthResource.AUTH_ACTOR_ID_HEADER_NAME)).andReturn(actorIdHeaderName).anyTimes();
    EasyMock.expect(context.getInitParameter(PreAuthResource.AUTH_ACTOR_GROUPS_HEADER_PREFIX)).andReturn(groupsHeaderPrefix).anyTimes();
    request = EasyMock.createNiceMock(HttpServletRequest.class);
    response = EasyMock.createNiceMock(HttpServletResponse.class);

    if (SubjectUtils.getPrimaryPrincipalName(subject) != null) {
      final String expectedActorIdHeader = actorIdHeaderName == null ? PreAuthResource.DEFAULT_AUTH_ACTOR_ID_HEADER_NAME : actorIdHeaderName;
      response.setHeader(expectedActorIdHeader, USER_NAME);
      EasyMock.expectLastCall();
    }

    if (!groups.isEmpty()) {
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
    configureCommonExpectations(null, null);
    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    final Response response = executeResourceWithSubject(preAuthResource);
    assertEquals(response.getStatus(), HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  public void testPopulatingDefaultActorIdHeaderNoGroups() throws Exception {
    configureCommonExpectations(null, null);
    final PreAuthResource preAuthResource = new PreAuthResource();
    preAuthResource.context = context;
    preAuthResource.response = response;
    executeResourceWithSubject(preAuthResource);
    EasyMock.verify(response);
  }

  @Test
  public void testPopulatingCustomActorIdHeaderNoGroups() throws Exception {
    configureCommonExpectations("customActorId", null);
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
