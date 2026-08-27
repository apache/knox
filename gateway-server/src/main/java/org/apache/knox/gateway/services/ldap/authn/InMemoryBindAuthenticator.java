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
package org.apache.knox.gateway.services.ldap.authn;

import org.apache.directory.api.ldap.model.constants.AuthenticationLevel;
import org.apache.directory.api.ldap.model.exception.LdapAuthenticationException;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.server.core.api.LdapPrincipal;
import org.apache.directory.server.core.api.interceptor.context.BindOperationContext;
import org.apache.directory.server.core.authn.AbstractAuthenticator;

import java.nio.charset.StandardCharsets;

/**
 * Authenticator for an in-memory user.
 */
public class InMemoryBindAuthenticator extends AbstractAuthenticator {

    private final Dn bindDn;
    private final String bindPassword;

    public InMemoryBindAuthenticator(Dn bindDn, String bindPassword)
        throws LdapException {
        super(AuthenticationLevel.SIMPLE, bindDn);
        this.bindDn = bindDn;
        this.bindPassword = bindPassword;
    }

    @Override
    public LdapPrincipal authenticate(BindOperationContext bindContext) throws LdapException {
        Dn dn = bindContext.getDn();

        // Check if authenticating as the configured in-memory user
        if (bindDn.equals(dn)) {
            byte[] passwordBytes = bindContext.getCredentials();
            String password = new String(passwordBytes, StandardCharsets.UTF_8);
            if (bindPassword.equals(password)) {
                return new LdapPrincipal(getDirectoryService().getSchemaManager(), dn, AuthenticationLevel.SIMPLE);
            } else {
                throw new LdapAuthenticationException("Failed to authenticate: " + dn.getName());
            }
        }

        // Pass-through to default authenticators if it's a normal directory user
        return null;
    }
}
