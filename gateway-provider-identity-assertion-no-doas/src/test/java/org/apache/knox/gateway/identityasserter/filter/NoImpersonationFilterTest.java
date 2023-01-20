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

import org.apache.knox.gateway.context.ContextAttributes;
import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.services.GatewayServices;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.security.auth.Subject;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;

import static org.apache.knox.gateway.identityasserter.common.filter.AbstractIdentityAsserterDeploymentContributor.IMPERSONATION_PARAMS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NoImpersonationFilterTest {

  @Test
  public void testInitParameters() throws Exception {
    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE)).andReturn("topology1").anyTimes();
    context.setAttribute(ContextAttributes.IMPERSONATION_ENABLED_ATTRIBUTE, Boolean.FALSE);
    EasyMock.expectLastCall();
    EasyMock.expect(config.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(context.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    EasyMock.replay( config );
    EasyMock.replay( context );

    NoImpersonationFilter filter = new NoImpersonationFilter();
    Subject subject = new Subject();

    subject.getPrincipals().add(new PrimaryPrincipal("lmccay"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admin"));

    filter.init(config);
    String username = filter.mapUserPrincipal(((Principal) subject.getPrincipals(PrimaryPrincipal.class).toArray()[0]).getName());
    String[] groups = filter.mapGroupPrincipals(username, subject);
    assertEquals("lmccay", username);
    assertNull(groups); // means for the caller to use the existing subject groups

    config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter("principal.mapping") ).andReturn( "lmccay,kminder=hdfs;newuser=mapred" ).anyTimes();
    EasyMock.expect(config.getInitParameter("group.principal.mapping") ).andReturn( "kminder=group1;lmccay=mrgroup,mrducks" ).anyTimes();
    EasyMock.expect(config.getInitParameter(IMPERSONATION_PARAMS) ).andReturn("doAs").anyTimes();
    context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE)).andReturn("topology1").anyTimes();
    context.setAttribute(ContextAttributes.IMPERSONATION_ENABLED_ATTRIBUTE, Boolean.FALSE);
    EasyMock.expectLastCall();
    EasyMock.expect(config.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.replay( context, config );
    filter.init(config);
    username = filter.mapUserPrincipal(((Principal) subject.getPrincipals(PrimaryPrincipal.class).toArray()[0]).getName());
    String[] mappedGroups = filter.mapGroupPrincipals(((Principal) subject.getPrincipals(PrimaryPrincipal.class).toArray()[0]).getName(), subject);
    assertEquals("hdfs", username);
    assertTrue("mrgroup not found in groups: " + Arrays.toString(mappedGroups), groupFoundIn("mrgroup", mappedGroups));
    assertTrue("mrducks not found in groups: " + Arrays.toString(mappedGroups), groupFoundIn("mrducks", mappedGroups));
    assertFalse("group1 WAS found in groups: " + Arrays.toString(mappedGroups), groupFoundIn("group1", mappedGroups));

    subject = new Subject();

    subject.getPrincipals().add(new PrimaryPrincipal("kminder"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admin"));

    config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter("principal.mapping") ).andReturn( "lmccay,kminder=hdfs;newuser=mapred" ).anyTimes();
    EasyMock.expect(config.getInitParameter("group.principal.mapping") ).andReturn( "kminder=group1;lmccay=mrgroup,mrducks" ).anyTimes();
    EasyMock.expect(config.getInitParameter(IMPERSONATION_PARAMS) ).andReturn("doAs").anyTimes();
    context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE)).andReturn("topology1").anyTimes();
    context.setAttribute(ContextAttributes.IMPERSONATION_ENABLED_ATTRIBUTE, Boolean.FALSE);
    EasyMock.expectLastCall();
    EasyMock.expect(config.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.replay(context, config );
    filter.init(config);
    username = filter.mapUserPrincipal(((Principal) subject.getPrincipals(PrimaryPrincipal.class).toArray()[0]).getName());
    mappedGroups = filter.mapGroupPrincipals(((Principal) subject.getPrincipals(PrimaryPrincipal.class).toArray()[0]).getName(), subject);
    assertEquals("hdfs", username);
    assertTrue("group1 not found in groups: " + Arrays.toString(mappedGroups), groupFoundIn("group1", mappedGroups));
  }

  private boolean groupFoundIn(String expected, String[] mappedGroups) {
    if (mappedGroups == null) {
      return false;
    }
    for (String mappedGroup : mappedGroups) {
      if (mappedGroup.equals(expected)) {
        return true;
      }
    }
    return false;
  }

  @Test
  public void testContextParameters() throws Exception {
    // for backward compatibility of old deployment contributor's method
    // of adding init params to the servlet context instead of to the filter.
    // There is the possibility that previously deployed topologies will have
    // init params in web.xml at the context level instead of the filter level.
    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(config.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.replay( config );
    EasyMock.replay( context );

    NoImpersonationFilter filter = new NoImpersonationFilter();
    Subject subject = new Subject();

    subject.getPrincipals().add(new PrimaryPrincipal("lmccay"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admin"));

    filter.init(config);
    String username = filter.mapUserPrincipal(((Principal) subject.getPrincipals(PrimaryPrincipal.class).toArray()[0]).getName());
    String[] groups = filter.mapGroupPrincipals(((Principal) subject.getPrincipals(PrimaryPrincipal.class).toArray()[0]).getName(), subject);

    assertEquals("lmccay", username);
    assertNull(groups); // means for the caller to use the existing subject groups

    config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(config.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(context.getInitParameter("principal.mapping") ).andReturn( "lmccay,kminder=hdfs;newuser=mapred" ).anyTimes();
    EasyMock.expect(context.getInitParameter("group.principal.mapping") ).andReturn( "kminder=group1;lmccay=mrgroup,mrducks" ).anyTimes();
    EasyMock.replay( config );
    EasyMock.replay( context );
    filter.init(config);
    username = filter.mapUserPrincipal(((Principal) subject.getPrincipals(PrimaryPrincipal.class).toArray()[0]).getName());
    groups = filter.mapGroupPrincipals(((Principal) subject.getPrincipals(PrimaryPrincipal.class).toArray()[0]).getName(), subject);
    assertEquals("hdfs", username);
    assertTrue("mrgroup not found in groups: " + Arrays.toString(groups), groupFoundIn("mrgroup", groups));
    assertTrue("mrducks not found in groups: " + Arrays.toString(groups), groupFoundIn("mrducks", groups));
    assertFalse("group1 WAS found in groups: " + Arrays.toString(groups), groupFoundIn("group1", groups));

    subject = new Subject();

    subject.getPrincipals().add(new PrimaryPrincipal("kminder"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admin"));

    config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(config.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(context.getInitParameter("principal.mapping") ).andReturn( "lmccay,kminder=hdfs;newuser=mapred" ).anyTimes();
    EasyMock.expect(context.getInitParameter("group.principal.mapping") ).andReturn( "kminder=group1;lmccay=mrgroup,mrducks" ).anyTimes();
    EasyMock.replay( config );
    EasyMock.replay( context );
    filter.init(config);
    username = filter.mapUserPrincipal(((Principal) subject.getPrincipals(PrimaryPrincipal.class).toArray()[0]).getName());
    assertEquals("hdfs", username);
  }
}
