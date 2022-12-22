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
package org.apache.knox.gateway.identityasserter.concat.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.security.Principal;
import java.util.Collections;

import javax.security.auth.Subject;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;

import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.easymock.EasyMock;
import org.junit.Test;

public class ConcatIdentityAssertionFilterTest {

  @Test
  public void testPrefixAndSuffix() throws Exception {
    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(context.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    EasyMock.replay( config );
    EasyMock.replay( context );

    ConcatIdentityAssertionFilter filter = new ConcatIdentityAssertionFilter();
    Subject subject = new Subject();

    subject.getPrincipals().add(new PrimaryPrincipal("larry"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admin"));

    filter.init(config);
    String username = filter.mapUserPrincipal(((Principal) subject.getPrincipals(PrimaryPrincipal.class).toArray()[0]).getName());
    String[] groups = filter.mapGroupPrincipals(username, subject);
    assertEquals(username, "larry");
    assertNull(groups); // means for the caller to use the existing subject groups

    config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(context.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    EasyMock.expect(config.getInitParameter("concat.prefix") ).andReturn( "sir-" ).anyTimes();
    EasyMock.replay( config );
    EasyMock.replay( context );
    filter.init(config);
    username = filter.mapUserPrincipal(((Principal) subject.getPrincipals(PrimaryPrincipal.class).toArray()[0]).getName());
    assertEquals(username, "sir-larry");

    config = EasyMock.createNiceMock( FilterConfig.class );
    context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(context.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    EasyMock.expect(config.getInitParameter("concat.suffix") ).andReturn( "-tenant-1" ).anyTimes();
    EasyMock.replay( config );
    EasyMock.replay( context );
    filter.init(config);
    username = filter.mapUserPrincipal(((Principal) subject.getPrincipals(PrimaryPrincipal.class).toArray()[0]).getName());
    assertEquals(username, "larry-tenant-1");

    config = EasyMock.createNiceMock( FilterConfig.class );
    context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(context.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    EasyMock.expect(config.getInitParameter("concat.prefix") ).andReturn( "sir-" ).anyTimes();
    EasyMock.expect(config.getInitParameter("concat.suffix") ).andReturn( "-tenant-1" ).anyTimes();
    EasyMock.replay( config );
    EasyMock.replay( context );
    filter.init(config);
    username = filter.mapUserPrincipal(((Principal) subject.getPrincipals(PrimaryPrincipal.class).toArray()[0]).getName());
    assertEquals(username, "sir-larry-tenant-1");
  }
}
