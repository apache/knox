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

import org.apache.knox.gateway.identityasserter.common.filter.CommonIdentityAssertionFilter;
import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.Subject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CommonIdentityAssertionFilterTest {
  private String username;
  private Filter filter;

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
        String[] combined = super.combineGroupMappings(mappedGroups, groups);
        assertEquals("LARRY", username);
        assertTrue("Should be greater than 2", combined.length > 2);
        assertTrue(combined[0], combined[0].equalsIgnoreCase("EVERYONE"));
        assertTrue(combined[1].equalsIgnoreCase("USERS") || combined[1].equalsIgnoreCase("ADMIN"));
        assertTrue(combined[2], combined[2].equalsIgnoreCase("USERS") || combined[2].equalsIgnoreCase("ADMIN"));
        return combined;
      }
    };
  }

  @Test
  public void testSimpleFilter() throws ServletException, IOException {
    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter(CommonIdentityAssertionFilter.GROUP_PRINCIPAL_MAPPING)).
        andReturn("*=everyone;").once();
    EasyMock.expect(config.getInitParameter(CommonIdentityAssertionFilter.PRINCIPAL_MAPPING)).
        andReturn("ljm=lmccay;").once();
    EasyMock.replay( config );

    final HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.replay( request );

    final HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( response );

    final FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response)
          throws IOException, ServletException {
      }
    };

    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal("larry"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admin"));
    try {
      Subject.doAs(
        subject,
        new PrivilegedExceptionAction<Object>() {
          @Override
          public Object run() throws Exception {
            filter.init(config);
            filter.doFilter(request, response, chain);
            return null;
          }
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
  }
}
