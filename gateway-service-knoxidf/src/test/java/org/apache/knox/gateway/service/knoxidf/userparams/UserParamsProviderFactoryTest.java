/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.service.knoxidf.userparams;

import org.easymock.EasyMock;
import org.junit.Test;

import jakarta.servlet.ServletContext;

import static org.junit.Assert.assertTrue;
import static org.apache.knox.gateway.service.knoxidf.userparams.UserParamsProviderFactory.TOKEN_USER_PARAMS_ENABLED;

/**
 * Verifies which {@link UserParamsProvider} {@link UserParamsProviderFactory} selects: the LDAP provider when
 * {@code user.params.provider.ldap.url} is set, unless {@code knoxidf.token.user.params.enabled} is {@code false},
 * which applies to the token endpoint only, not to /userinfo.
 */
public class UserParamsProviderFactoryTest {

  @Test
  public void testNoLdapUrlSelectsEmptyProvider() {
    final UserParamsProvider provider = UserParamsProviderFactory.getUserParamsProvider(servletContext(null, null));
    assertTrue(provider instanceof EmptyUserParamsProvider);
  }

  @Test
  public void testUserParamsLookupIsEnabledByDefault() {
    final UserParamsProvider provider = UserParamsProviderFactory.getUserParamsProvider(servletContext("ldap://localhost:33389", null));
    assertTrue("Without the switch the configured LDAP provider must be used.", provider instanceof LdapUserParamsProvider);
  }

  @Test
  public void testUserParamsLookupCanBeExplicitlyEnabled() {
    final UserParamsProvider provider = UserParamsProviderFactory.getUserParamsProvider(servletContext("ldap://localhost:33389", "true"));
    assertTrue(provider instanceof LdapUserParamsProvider);
  }

  @Test
  public void testUserParamsLookupCanBeDisabled() {
    final UserParamsProvider provider = UserParamsProviderFactory.getUserParamsProvider(servletContext("ldap://localhost:33389", "false"));
    assertTrue("A disabled lookup must never contact LDAP.", provider instanceof EmptyUserParamsProvider);
  }

  @Test
  public void testUserParamsLookupSwitchIsCaseInsensitive() {
    final UserParamsProvider provider = UserParamsProviderFactory.getUserParamsProvider(servletContext("ldap://localhost:33389", "FALSE"));
    assertTrue(provider instanceof EmptyUserParamsProvider);
  }

  @Test
  public void testExplicitlyEnabledProviderIgnoresTokenSwitch() {
    final UserParamsProvider provider = UserParamsProviderFactory.getUserParamsProvider(servletContext("ldap://localhost:33389", "false"), true);
    assertTrue("/userinfo must keep using the configured LDAP provider.", provider instanceof LdapUserParamsProvider);
  }

  @Test
  public void testExplicitlyEnabledProviderWithoutLdapUrlIsEmpty() {
    final UserParamsProvider provider = UserParamsProviderFactory.getUserParamsProvider(servletContext(null, null), true);
    assertTrue(provider instanceof EmptyUserParamsProvider);
  }

  @Test
  public void testExplicitlyDisabledProviderIsEmpty() {
    final UserParamsProvider provider = UserParamsProviderFactory.getUserParamsProvider(servletContext("ldap://localhost:33389", "true"), false);
    assertTrue(provider instanceof EmptyUserParamsProvider);
  }

  private static ServletContext servletContext(String ldapUrl, String userParamsEnabled) {
    final ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameter(LdapUserParamsProvider.LDAP_URL)).andReturn(ldapUrl).anyTimes();
    EasyMock.expect(context.getInitParameter(TOKEN_USER_PARAMS_ENABLED)).andReturn(userParamsEnabled).anyTimes();
    EasyMock.replay(context);
    return context;
  }
}
