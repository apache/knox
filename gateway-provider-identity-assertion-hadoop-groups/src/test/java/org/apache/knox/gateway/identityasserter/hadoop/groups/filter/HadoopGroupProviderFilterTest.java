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
package org.apache.knox.gateway.identityasserter.hadoop.groups.filter;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import java.security.Principal;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.apache.knox.gateway.identityasserter.common.filter.CommonIdentityAssertionFilter;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.easymock.EasyMock;
import org.junit.Test;

/**
 * Test for {@link HadoopGroupProviderFilter}
 *
 * @since 0.11.0
 */
public class HadoopGroupProviderFilterTest {
  private static final String USER_NAME = "knox";
  /**
   * System username
   */
  private static final String failUsername = "highly_unlikely_username_to_have";

  /**
   * System username
   */
  private static final String username = System.getProperty("user.name");

  /**
   * Hadoop Groups implementation.
   */
  public HadoopGroupProviderFilterTest() {
    super();
  }

  /*
   * Test that valid groups are retrieved for a legitimate user.
   */
  @Test
  public void testGroups() throws ServletException {

    final FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(config.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(context.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    EasyMock.replay( config );
    EasyMock.replay( context );

    final HadoopGroupProviderFilter filter = new HadoopGroupProviderFilter();

    final Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal(username));

    filter.init(config);
    final String principal = filter.mapUserPrincipal(
        ((Principal) subject.getPrincipals(PrimaryPrincipal.class).toArray()[0])
            .getName());
    final String[] groups = filter.mapGroupPrincipals(principal, subject);

    assertThat(principal, is(username));
    assertThat(
        "No groups assosciated with the user, most likely this is a failure, it is only OK when 'bash -c groups' command returns 0 groups. ",
        groups.length > 0);

  }

  /*
   * Test that no groups are retrieved for a dummy user.
   */
  @Test
  public void testUnknownUser() throws ServletException {

    final FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(config.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(context.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    EasyMock.replay( config );
    EasyMock.replay( context );

    final HadoopGroupProviderFilter filter = new HadoopGroupProviderFilter();

    final Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal(failUsername));

    filter.init(config);
    final String principal = filter.mapUserPrincipal(
        ((Principal) subject.getPrincipals(PrimaryPrincipal.class).toArray()[0])
            .getName());
    final String[] groups = filter.mapGroupPrincipals(principal, subject);

    assertThat(principal, is(failUsername));
    assertThat(
        "Somehow groups were found for this user, how is it possible ! check 'bash -c groups' command ",
        groups.length == 0);

  }

  /*
   * Test for a bad config (nonexistent). This test proves, we are not falling
   * back on {@link ShellBasedUnixGroupsMapping} because we explicitly use
   * {@link LdapGroupsMapping} and in case of bad config we get empty groups
   * (Hadoop way).
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  @Test
  public void badConfigTest() throws ServletException {

    final List<String> keysList = Arrays.asList("hadoop.security.group.mapping",
        "hadoop.security.group.mapping.ldap.bind.user",
        "hadoop.security.group.mapping.ldap.bind.password",
        "hadoop.security.group.mapping.ldap.url",
        "hadoop.security.group.mapping.ldap.search.filter.group",
        "hadoop.security.group.mapping.ldap.search.attr.member",
        "hadoop.security.group.mapping.ldap.search.filter.user");

    final FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(config.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect(context.getInitParameter("principal.mapping") ).andReturn( "" ).anyTimes();

    EasyMock.expect(config.getInitParameter("hadoop.security.group.mapping"))
        .andReturn("org.apache.hadoop.security.LdapGroupsMapping").anyTimes();
    EasyMock
        .expect(config
            .getInitParameter("hadoop.security.group.mapping.ldap.bind.user"))
        .andReturn("uid=dummy,ou=people,dc=hadoop,dc=apache,dc=org").anyTimes();
    EasyMock
        .expect(config.getInitParameter(
            "hadoop.security.group.mapping.ldap.bind.password"))
        .andReturn("unbind-me-please").anyTimes();
    EasyMock
        .expect(
            config.getInitParameter("hadoop.security.group.mapping.ldap.url"))
        .andReturn("ldap://nomansland:33389").anyTimes();
    EasyMock
        .expect(config.getInitParameter(
            "hadoop.security.group.mapping.ldap.search.filter.group"))
        .andReturn("(objectclass=groupOfNames)").anyTimes();
    EasyMock
        .expect(config.getInitParameter(
            "hadoop.security.group.mapping.ldap.search.attr.member"))
        .andReturn("member").anyTimes();
    EasyMock
        .expect(config.getInitParameter(
            "hadoop.security.group.mapping.ldap.search.filter.user"))
        .andReturn(
            "(&amp;(|(objectclass=person)(objectclass=applicationProcess))(cn={0}))")
        .anyTimes();
    EasyMock.expect(config.getInitParameterNames())
            .andStubAnswer(() -> Collections.enumeration((keysList)));

    EasyMock.replay( config );
    EasyMock.replay( context );

    final HadoopGroupProviderFilter filter = new HadoopGroupProviderFilter();

    final Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal(username));

    filter.init(config);
    final String principal = filter.mapUserPrincipal(
        ((Principal) subject.getPrincipals(PrimaryPrincipal.class).toArray()[0])
            .getName());
    final String[] groups = filter.mapGroupPrincipals(principal, subject);

    assertThat(principal, is(username));

    /*
     * Unfortunately, Hadoop does not let us know what went wrong all we get is
     * empty groups
     */
    assertThat(groups.length, is(0));

  }

  @Test
  public void testGroupsWithVirtualGroup() throws Exception {
    Set<String> calculatedGroups = new HashSet<>();
    FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getServletContext()).andReturn(context).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).
            andReturn(Collections.enumeration(Arrays.asList(
                    CommonIdentityAssertionFilter.VIRTUAL_GROUP_MAPPING_PREFIX + "test-virtual-group")))
            .anyTimes();
    EasyMock.expect(config.getInitParameter(CommonIdentityAssertionFilter.VIRTUAL_GROUP_MAPPING_PREFIX + "test-virtual-group")).
            andReturn("(and (username 'knox') (member 'hadoop-group'))").anyTimes();

    EasyMock.replay(config);
    EasyMock.replay(context);

    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.replay(request);

    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay(response);

    FilterChain chain = (req, resp) -> {};

    HadoopGroupProviderFilter filter = new HadoopGroupProviderFilter() {
      @Override
      protected void continueChainAsPrincipal(HttpServletRequestWrapper request, ServletResponse response, FilterChain chain, String mappedPrincipalName, String[] groups) {
        calculatedGroups.addAll(Arrays.asList(groups));
      }

      @Override
      protected List<String> hadoopGroups(String mappedPrincipalName) {
        return Collections.singletonList("hadoop-group");
      }
    };

    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal(USER_NAME));
    Subject.doAs(
            subject,
            (PrivilegedExceptionAction<Object>) () -> {
              filter.init(config);
              filter.doFilter(request, response, chain);
              return null;
            });

    assertEquals(
            new HashSet<>(Arrays.asList("hadoop-group", "test-virtual-group")), calculatedGroups);
  }
}
