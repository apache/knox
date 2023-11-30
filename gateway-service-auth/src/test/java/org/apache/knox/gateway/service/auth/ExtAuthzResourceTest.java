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

import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.security.SubjectUtils;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.Subject;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ExtAuthzResourceTest {

  private static final String USER_NAME = "test-username";
  private ServletContext context;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private final Subject subject = new Subject();

  @Before
  public void setUp() {
    subject.getPrincipals().add(new PrimaryPrincipal(USER_NAME));
  }

  @Test
  public void testIgnoreAdditionalPaths() throws Exception {
    configureCommonExpectations(null, null, Collections.singleton("group1"));
    final ExtAuthzResource extAuthzResource = new ExtAuthzResource();
    extAuthzResource.context = context;
    extAuthzResource.response = response;
    executeResourceWithAdditionalPath(extAuthzResource);
    EasyMock.verify(response);
  }


  private void configureCommonExpectations(String actorIdHeaderName, String groupsHeaderPrefix, Collection<String> groups) {
    context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameter(ExtAuthzResource.AUTH_ACTOR_ID_HEADER_NAME)).andReturn(actorIdHeaderName).anyTimes();
    EasyMock.expect(context.getInitParameter(ExtAuthzResource.AUTH_ACTOR_GROUPS_HEADER_PREFIX)).andReturn(groupsHeaderPrefix).anyTimes();
    EasyMock.expect(context.getInitParameter(ExtAuthzResource.IGNORE_ADDITIONAL_PATH)).andReturn("true").anyTimes();
    request = EasyMock.createNiceMock(HttpServletRequest.class);
    response = EasyMock.createNiceMock(HttpServletResponse.class);

    if (SubjectUtils.getPrimaryPrincipalName(subject) != null) {
      final String expectedActorIdHeader = actorIdHeaderName == null ? "X-Knox-Actor-ID" : actorIdHeaderName;
      response.setHeader(expectedActorIdHeader, USER_NAME);
      EasyMock.expectLastCall();
    }

    if (!groups.isEmpty()) {
      groups.forEach(group -> subject.getPrincipals().add(new GroupPrincipal(group)));
      final int groupStringSize = calculateGroupStringSize(groups);
      final int expectedGroupHeaderCount = groupStringSize / 1000 + 1;
      final String expectedGroupsHeaderPrefix = (groupsHeaderPrefix == null ? "X-Knox-Actor-Groups" : groupsHeaderPrefix)
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


  private Response executeResourceWithAdditionalPath(final ExtAuthzResource extAuthzResource) throws PrivilegedActionException {
    return (Response) Subject.doAs(subject, new PrivilegedExceptionAction<Object>() {

      @Override
      public Object run() throws Exception {
        extAuthzResource.init();
        return extAuthzResource.doGetWithPath(new UriInfo() {
          @Override
          public String getPath() {
            return "/gateway/sandbox/auth/api/v1/extauthz/does-not-exist";
          }

          @Override
          public String getPath(boolean decode) {
            return "/gateway/sandbox/auth/api/v1/extauthz/does-not-exist";
          }

          @Override
          public List<PathSegment> getPathSegments() {
            return null;
          }

          @Override
          public List<PathSegment> getPathSegments(boolean decode) {
            return null;
          }

          @Override
          public URI getRequestUri() {
            return null;
          }

          @Override
          public UriBuilder getRequestUriBuilder() {
            return null;
          }

          @Override
          public URI getAbsolutePath() {
            return null;
          }

          @Override
          public UriBuilder getAbsolutePathBuilder() {
            return null;
          }

          @Override
          public URI getBaseUri() {
            return null;
          }

          @Override
          public UriBuilder getBaseUriBuilder() {
            return null;
          }

          @Override
          public MultivaluedMap<String, String> getPathParameters() {
            return null;
          }

          @Override
          public MultivaluedMap<String, String> getPathParameters(
              boolean decode) {
            return null;
          }

          @Override
          public MultivaluedMap<String, String> getQueryParameters() {
            return null;
          }

          @Override
          public MultivaluedMap<String, String> getQueryParameters(
              boolean decode) {
            return null;
          }

          @Override
          public List<String> getMatchedURIs() {
            return null;
          }

          @Override
          public List<String> getMatchedURIs(boolean decode) {
            return null;
          }

          @Override
          public List<Object> getMatchedResources() {
            return null;
          }

          @Override
          public URI resolve(URI uri) {
            return null;
          }

          @Override
          public URI relativize(URI uri) {
            return null;
          }
        });
      }

    });
  }


}
