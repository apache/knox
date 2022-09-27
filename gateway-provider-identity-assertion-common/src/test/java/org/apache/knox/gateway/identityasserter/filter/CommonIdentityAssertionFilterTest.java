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
package org.apache.knox.gateway.identityasserter.filter;

import static org.apache.knox.gateway.audit.log4j.audit.Log4jAuditService.MDC_AUDIT_CONTEXT_KEY;
import static org.apache.knox.gateway.identityasserter.common.filter.AbstractIdentityAsserterDeploymentContributor.IMPERSONATION_PARAMS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.security.auth.Subject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.apache.knox.gateway.identityasserter.common.filter.CommonIdentityAssertionFilter;
import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.logging.log4j.ThreadContext;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

public class CommonIdentityAssertionFilterTest {
  private String username;
  private Filter filter;
  private Set<String> calculatedGroups = new HashSet<>();

  @Before
  public void setUp() {
    filter = new CommonIdentityAssertionFilter() {
      @Override
      public String mapUserPrincipal(String principalName) {
        username = principalName.toUpperCase(Locale.ROOT);
        return principalName;
      }

      @Override
      public String[] mapGroupPrincipals(String principalName, Subject subject) {
        String[] groups = new String[4];
        int i = 0;
        for(GroupPrincipal p : subject.getPrincipals(GroupPrincipal.class)) {
          groups[i] = p.getName().toUpperCase(Locale.ROOT);
          i++;
        }
        return groups;
      }

      @Override
      protected String[] combineGroupMappings(String[] mappedGroups, String[] groups) {
        calculatedGroups.addAll(Arrays.asList(super.combineGroupMappings(mappedGroups, groups)));
        return super.combineGroupMappings(mappedGroups, groups);
      }

      @Override
      protected void continueChainAsPrincipal(HttpServletRequestWrapper request, ServletResponse response, FilterChain chain, String mappedPrincipalName, String[] groups) throws IOException, ServletException {
        assertEquals("Groups should not have duplicates: " + Arrays.toString(groups),
                new HashSet<>(Arrays.asList(groups)).size(),
                groups.length);
        super.continueChainAsPrincipal(request, response, chain, mappedPrincipalName, groups);
      }
    };
    ThreadContext.put(MDC_AUDIT_CONTEXT_KEY, "dummy");
  }

  @Test
  public void testSimpleFilter() throws ServletException, IOException {
    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter(CommonIdentityAssertionFilter.GROUP_PRINCIPAL_MAPPING)).
        andReturn("*=everyone;lmccay=test-virtual-group").once();
    EasyMock.expect(config.getInitParameter(CommonIdentityAssertionFilter.PRINCIPAL_MAPPING)).
        andReturn("ljm=lmccay;").once();
    EasyMock.expect(config.getInitParameterNames()).
            andReturn(Collections.enumeration(Arrays.asList(
                    CommonIdentityAssertionFilter.GROUP_PRINCIPAL_MAPPING,
                    CommonIdentityAssertionFilter.PRINCIPAL_MAPPING,
                    CommonIdentityAssertionFilter.VIRTUAL_GROUP_MAPPING_PREFIX + "test-virtual-group",
                    CommonIdentityAssertionFilter.VIRTUAL_GROUP_MAPPING_PREFIX))) // invalid group with no name
            .anyTimes();
    EasyMock.expect(config.getInitParameter(IMPERSONATION_PARAMS)).
        andReturn("doAs").anyTimes();
    EasyMock.expect(config.getInitParameter(CommonIdentityAssertionFilter.VIRTUAL_GROUP_MAPPING_PREFIX + "test-virtual-group")).
            andReturn("(and (username 'lmccay') (and (member 'users') (member 'admin')))").anyTimes();
    EasyMock.expect(config.getInitParameter(CommonIdentityAssertionFilter.VIRTUAL_GROUP_MAPPING_PREFIX)).
            andReturn("true").anyTimes();
    EasyMock.replay( config );

    final HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.replay( request );

    final HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( response );

    final FilterChain chain = (req, resp) -> {};

    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal("ljm"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admin"));
    try {
      Subject.doAs(
        subject,
              (PrivilegedExceptionAction<Object>) () -> {
                filter.init(config);
                filter.doFilter(request, response, chain);
                return null;
              });
    }
    catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      }
      else if (t instanceof ServletException) {
        throw (ServletException) t;
      }
      else {
        throw new ServletException(t);
      }
    }

    assertEquals("LMCCAY", username);
    assertTrue("Should be greater than 2", calculatedGroups.size() > 2);
    assertTrue(calculatedGroups.containsAll(Arrays.asList("everyone", "USERS", "ADMIN", "test-virtual-group")));
    assertFalse(calculatedGroups.contains(""));
  }
}
