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

import org.apache.knox.gateway.service.knoxidf.OIDCScope;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.servlet.ServletContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class LdapUserParamsProvider implements UserParamsProvider {
    private static final String PREFIX = "user.params.provider.ldap.";
    static final String LDAP_URL = PREFIX + "url";
    private static final String LDAP_BASE_DN = PREFIX + "baseDn";
    private static final String LDAP_USER_DN_TEMPLATE = PREFIX + "userDnTemplate";
    private static final String LDAP_SYSTEM_USER = PREFIX + "systemUser";
    private static final String LDAP_SYSTEM_PASSWORD_ALIAS = PREFIX + "systemPasswordAlias";

    // === Defaults point to Knox's demo LDAP ===
    private static final String DEFAULT_BASE_DN = "dc=hadoop,dc=apache,dc=org";
    private static final String DEFAULT_USER_DN_TEMPLATE = "uid=%s,ou=people," + DEFAULT_BASE_DN;
    private static final String DEFAULT_SYSTEM_USER = "uid=admin,ou=people," + DEFAULT_BASE_DN;
    private static final String DEFAULT_SYSTEM_PASSWORD = "admin-password";

    private static final String[] ATTRIBUTES = {"cn", "sn", "givenName", "mail"};

    private final String ldapUrl;
    private final String ldapBaseDn;
    private final String ldapUserDnTemplate;
    private final String ldapSystemUser;
    private final String ldapSystemPassword;

    LdapUserParamsProvider(ServletContext servletContext) {
        this.ldapUrl = servletContext.getInitParameter(LDAP_URL);
        this.ldapBaseDn = getInitParamOrDefault(servletContext, LDAP_BASE_DN, DEFAULT_BASE_DN);
        this.ldapUserDnTemplate = getInitParamOrDefault(servletContext, LDAP_USER_DN_TEMPLATE, DEFAULT_USER_DN_TEMPLATE);
        this.ldapSystemUser = getInitParamOrDefault(servletContext, LDAP_SYSTEM_USER, DEFAULT_SYSTEM_USER);
        this.ldapSystemPassword = getSystemPassword(servletContext);
    }

    private String getInitParamOrDefault(ServletContext servletContext, String key, String defaultValue) {
        final String value = servletContext.getInitParameter(key);
        return value == null ? defaultValue : value;
    }

    private String getSystemPassword(ServletContext servletContext) {
        final GatewayServices services = (GatewayServices) servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
        final AliasService aliasService = services.getService(ServiceType.ALIAS_SERVICE);
        try {
            final char[] systemPassword = aliasService.getPasswordFromAliasForGateway(LDAP_SYSTEM_PASSWORD_ALIAS);
            return systemPassword == null ? DEFAULT_SYSTEM_PASSWORD : new String(systemPassword);
        } catch (AliasServiceException e) {
            return DEFAULT_SYSTEM_PASSWORD;
        }
    }

    @Override
    public Map<String, Object> getParamsFor(String subjectName, String scope) {
        Map<String, Object> userParams = new HashMap<>();
        if ("anonymous".equalsIgnoreCase(subjectName)) {
            return userParams;
        }

        Set<String> requestedClaims = OIDCScope.claimsForScopes(scope);

        LdapContext ctx = null;
        try {
            ctx = createSystemContext();

            String userDn = String.format(Locale.US, ldapUserDnTemplate, subjectName);

            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.OBJECT_SCOPE);
            controls.setReturningAttributes(ATTRIBUTES);

            NamingEnumeration<SearchResult> results = ctx.search(userDn, "(objectClass=*)", controls);
            if (results.hasMore()) {
                SearchResult sr = results.next();
                Attributes attrs = sr.getAttributes();

                // --- OIDC standard claims ---
                if (requestedClaims.contains("sub")) {
                    userParams.put("sub", subjectName);
                }
                if (requestedClaims.contains("name")) {
                    userParams.put("name", getAttr(attrs, "cn"));
                }
                if (requestedClaims.contains("family_name")) {
                    userParams.put("family_name", getAttr(attrs, "sn"));
                }
                if (requestedClaims.contains("given_name")) {
                    userParams.put("given_name", getAttr(attrs, "givenName"));
                }
                if (requestedClaims.contains("email")) {
                    userParams.put("email", getAttr(attrs, "mail"));
                }
                if (requestedClaims.contains("email_verified")) {
                    userParams.put("email_verified", Boolean.TRUE);
                }

                // --- Custom: roles ---
                if (requestedClaims.contains("roles")) {
                    List<String> roles = fetchRoles(ctx, userDn);
                    userParams.put("roles", roles);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch user parameters for " + subjectName, e);
        } finally {
            closeContext(ctx);
        }

        return userParams;
    }

    private List<String> fetchRoles(LdapContext ctx, String userDn) throws Exception {
        List<String> roles = new ArrayList<>();

        SearchControls groupControls = new SearchControls();
        groupControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
        groupControls.setReturningAttributes(new String[]{"cn", "member"});

        String groupsBase = "ou=groups," + ldapBaseDn;
        NamingEnumeration<SearchResult> groupResults =
                ctx.search(groupsBase, "(objectClass=groupOfNames)", groupControls);

        while (groupResults.hasMore()) {
            SearchResult group = groupResults.next();
            Attributes groupAttrs = group.getAttributes();
            Attribute members = groupAttrs.get("member");
            if (members != null) {
                NamingEnumeration<?> e = members.getAll();
                while (e.hasMore()) {
                    String memberDn = (String) e.next();
                    if (memberDn.equalsIgnoreCase(userDn)) {
                        roles.add(getAttr(groupAttrs, "cn"));
                        break;
                    }
                }
            }
        }
        return roles;
    }

    private LdapContext createSystemContext() throws Exception {
        Hashtable<String, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapUrl);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, ldapSystemUser);
        env.put(Context.SECURITY_CREDENTIALS, ldapSystemPassword);
        return new InitialLdapContext(env, null);
    }

    private String getAttr(Attributes attrs, String attrName) throws Exception {
        Attribute attr = attrs.get(attrName);
        return attr != null ? (String) attr.get() : null;
    }

    private void closeContext(LdapContext ctx) {
        if (ctx != null) {
            try {
                ctx.close();
            } catch (Exception ignored) {
            }
        }
    }
}

