/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.hadoop.gateway.shirorealm;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.apache.shiro.subject.PrincipalCollection;

import javax.naming.NamingException;
import javax.naming.ldap.LdapContext;

/**
 * An adapter class that delegate calls to {@link org.apache.knox.gateway.shirorealm.KnoxLdapRealm}
 * for backwards compatibility with package structure.
 *
 * @since 0.14.0
 * @deprecated Use {@link org.apache.knox.gateway.shirorealm.KnoxLdapRealm}
 */
@Deprecated
public class KnoxLdapRealm extends org.apache.knox.gateway.shirorealm.KnoxLdapRealm {
  public KnoxLdapRealm() {
    super();
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(
      AuthenticationToken token) throws AuthenticationException {
    return super.doGetAuthenticationInfo(token);
  }

  /**
   * Get groups from LDAP.
   *
   * @param principals         the principals of the Subject whose
   *                           AuthenticationInfo should be queried from the
   *                           LDAP server.
   * @param ldapContextFactory factory used to retrieve LDAP connections.
   * @return an {@link AuthorizationInfo} instance containing information
   * retrieved from the LDAP server.
   * @throws NamingException if any LDAP errors occur during the search.
   */
  @Override
  protected AuthorizationInfo queryForAuthorizationInfo(
      PrincipalCollection principals, LdapContextFactory ldapContextFactory)
      throws NamingException {
    return super.queryForAuthorizationInfo(principals, ldapContextFactory);
  }

  /**
   * Returns the LDAP User Distinguished Name (DN) to use when acquiring an
   * {@link LdapContext LdapContext} from the {@link LdapContextFactory}.
   *
   * If the the {@link #getUserDnTemplate() userDnTemplate} property has been
   * set, this implementation will construct the User DN by substituting the
   * specified {@code principal} into the configured template.  If the {@link
   * #getUserDnTemplate() userDnTemplate} has not been set, the method argument
   * will be returned directly (indicating that the submitted authentication
   * token principal <em>is</em> the User DN).
   *
   * @param principal the principal to substitute into the configured {@link
   *                  #getUserDnTemplate() userDnTemplate}.
   * @return the constructed User DN to use at runtime when acquiring an {@link
   * LdapContext}.
   * @throws IllegalArgumentException if the method argument is null or empty
   * @throws IllegalStateException    if the {@link #getUserDnTemplate
   *                                  userDnTemplate} has not been set.
   * @see LdapContextFactory#getLdapContext(Object, Object)
   */
  @Override
  protected String getUserDn(String principal)
      throws IllegalArgumentException, IllegalStateException {
    return super.getUserDn(principal);
  }

  @Override
  protected AuthenticationInfo createAuthenticationInfo(
      AuthenticationToken token, Object ldapPrincipal, Object ldapCredentials,
      LdapContext ldapContext) throws NamingException {
    return super.createAuthenticationInfo(token, ldapPrincipal, ldapCredentials,
        ldapContext);
  }
}
