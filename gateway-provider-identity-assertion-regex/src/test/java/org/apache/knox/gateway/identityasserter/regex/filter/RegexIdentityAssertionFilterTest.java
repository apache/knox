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
package org.apache.knox.gateway.identityasserter.regex.filter;

import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.security.auth.Subject;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;

import java.security.Principal;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

public class RegexIdentityAssertionFilterTest {

  @Test
  public void testExtractUsernameFromEmail() throws Exception {
    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect(context.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    EasyMock.replay( config );
    EasyMock.replay( context );

    RegexIdentityAssertionFilter filter = new RegexIdentityAssertionFilter();

    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal( "member@us.apache.org" ) );
    subject.getPrincipals().add(new GroupPrincipal( "user" ) );
    subject.getPrincipals().add( new GroupPrincipal( "admin" ) );

    // First test is with no config.  Since the output template is the empty string that should be the result.
    filter.init(config);
    String actual = filter.mapUserPrincipal(((Principal) subject.getPrincipals(PrimaryPrincipal.class).toArray()[0]).getName());
    String[] groups = filter.mapGroupPrincipals(actual, subject);
    assertThat( actual, is( "" ) );
    assertThat( groups, is( nullValue() ) ); // means for the caller to use the existing subject groups

    // Test what is effectively a static mapping
    config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(config.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(context.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    EasyMock.expect(config.getInitParameter( "output" ) ).andReturn( "test-output" ).anyTimes();
    EasyMock.replay( config );
    EasyMock.replay( context );
    filter.init( config );
    actual = filter.mapUserPrincipal(((Principal) subject.getPrincipals(PrimaryPrincipal.class).toArray()[0]).getName());
    assertEquals( actual, "test-output" );

    // Test username extraction.
    config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(config.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(context.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    EasyMock.expect(config.getInitParameter( "input" ) ).andReturn( "(.*)@.*" ).anyTimes();
    EasyMock.expect(config.getInitParameter( "output" ) ).andReturn( "prefix_{1}_suffix" ).anyTimes();
    EasyMock.replay( config );
    EasyMock.replay( context );
    filter.init( config );
    actual = filter.mapUserPrincipal( "member@us.apache.org" );
    assertEquals( actual, "prefix_member_suffix" );

  }

  @Test
  public void testMapDomain() throws Exception {
    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(config.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(context.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    EasyMock.replay( config );
    EasyMock.replay( context );

    RegexIdentityAssertionFilter filter = new RegexIdentityAssertionFilter();

    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal( "member@us.apache.org" ) );
    subject.getPrincipals().add(new GroupPrincipal( "user" ) );
    subject.getPrincipals().add( new GroupPrincipal( "admin" ) );

    String actual;

    // Test dictionary lookup.
    config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(config.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(context.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    EasyMock.expect(config.getInitParameter( "input" ) ).andReturn( "(.*)@(.*?)\\..*" ).anyTimes();
    EasyMock.expect(config.getInitParameter( "output" ) ).andReturn( "prefix_{1}_suffix:{[2]}" ).anyTimes();
    EasyMock.expect(config.getInitParameter( "lookup" ) ).andReturn( "us=USA;ca=CANADA" ).anyTimes();
    EasyMock.replay( config );
    EasyMock.replay( context );
    filter.init( config );
    actual = filter.mapUserPrincipal( "member1@us.apache.org" );
    assertThat( actual, is( "prefix_member1_suffix:USA" ) );
    actual = filter.mapUserPrincipal( "member2@ca.apache.org" );
    assertThat( actual, is( "prefix_member2_suffix:CANADA" ) );
    actual = filter.mapUserPrincipal( "member3@nj.apache.org" );
    assertThat( actual, is( "prefix_member3_suffix:" ) );
  }

  @Test
  public void testOrRegexInputForEmailAndSimple() throws Exception {
    FilterConfig config;
    ServletContext context;
    String actual;
    RegexIdentityAssertionFilter filter = new RegexIdentityAssertionFilter();

    // Test non-match of principal.
    config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(config.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(context.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    EasyMock.expect(config.getInitParameter( "input" ) ).andReturn( "([^@]*)(@.*)?" ).anyTimes();
    EasyMock.expect(config.getInitParameter( "output" ) ).andReturn( "prefix_{1}_suffix" ).anyTimes();
    EasyMock.replay( config );
    EasyMock.replay( context );
    filter.init( config );
    actual = filter.mapUserPrincipal( "test-simple-name" );
    assertThat( actual, is("prefix_test-simple-name_suffix" ) );

    actual = filter.mapUserPrincipal( "test-simple-name@test-email-domain" );
    assertThat( actual, is("prefix_test-simple-name_suffix" ) );

  }

}
