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
import javax.naming.InvalidNameException;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import jakarta.servlet.ServletContext;
import java.util.ArrayList;
import java.util.Collections;
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
    static final String LDAP_SYSTEM_PASSWORD_ALIAS = PREFIX + "systemPasswordAlias";
    static final String LDAP_USER_SEARCH_BASE = PREFIX + "userSearchBase";
    static final String LDAP_USER_SEARCH_FILTER = PREFIX + "userSearchFilter";

    // === Defaults point to Knox's demo LDAP ===
    private static final String DEFAULT_BASE_DN = "dc=hadoop,dc=apache,dc=org";
    private static final String DEFAULT_USER_DN_TEMPLATE = "uid=%s,ou=people," + DEFAULT_BASE_DN;
    private static final String DEFAULT_SYSTEM_USER = "uid=admin,ou=people," + DEFAULT_BASE_DN;
    // {0} is replaced with the username; JNDI escapes filter arguments, so no manual escaping is needed.
    static final String DEFAULT_USER_SEARCH_FILTER = "(uid={0})";

    private static final String MEMBER_OF = "memberOf";
    private static final String[] ATTRIBUTES = {"cn", "sn", "givenName", "mail", MEMBER_OF};

    private final String ldapUrl;
    private final String ldapBaseDn;
    private final String ldapUserDnTemplate;
    private final String ldapUserSearchBase;
    private final String ldapUserSearchFilter;
    private final String ldapSystemUser;
    private final String ldapSystemPasswordAlias;
    private final String ldapSystemPassword;

    LdapUserParamsProvider(ServletContext servletContext) {
        this.ldapUrl = servletContext.getInitParameter(LDAP_URL);
        this.ldapBaseDn = getInitParamOrDefault(servletContext, LDAP_BASE_DN, DEFAULT_BASE_DN);
        this.ldapUserDnTemplate = getInitParamOrDefault(servletContext, LDAP_USER_DN_TEMPLATE, DEFAULT_USER_DN_TEMPLATE);
        this.ldapUserSearchBase = servletContext.getInitParameter(LDAP_USER_SEARCH_BASE);
        this.ldapUserSearchFilter = getInitParamOrDefault(servletContext, LDAP_USER_SEARCH_FILTER, DEFAULT_USER_SEARCH_FILTER);
        this.ldapSystemUser = getInitParamOrDefault(servletContext, LDAP_SYSTEM_USER, DEFAULT_SYSTEM_USER);
        // Required: there is no default alias (and no default password).
        this.ldapSystemPasswordAlias = servletContext.getInitParameter(LDAP_SYSTEM_PASSWORD_ALIAS);
        this.ldapSystemPassword = getSystemPassword(servletContext);
    }

    private String getInitParamOrDefault(ServletContext servletContext, String key, String defaultValue) {
        final String value = servletContext.getInitParameter(key);
        return value == null ? defaultValue : value;
    }

    private String getSystemPassword(ServletContext servletContext) {
        if (ldapSystemPasswordAlias == null) {
            return null;
        }
        final GatewayServices services = (GatewayServices) servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
        final AliasService aliasService = services.getService(ServiceType.ALIAS_SERVICE);
        try {
            final char[] systemPassword = aliasService.getPasswordFromAliasForGateway(ldapSystemPasswordAlias);
            // No hardcoded fallback: if the alias is absent/unresolvable, return null so the LDAP
            // bind fails fast rather than silently binding with a well-known demo password.
            return systemPassword == null ? null : new String(systemPassword);
        } catch (AliasServiceException e) {
            return null;
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

            // Always close the enumeration: LdapContext.close() alone does not release the
            // per-search cursor, so leaking these under load can exhaust server-side resources
            // and start throwing NamingException. NamingEnumeration is not AutoCloseable, hence
            // the explicit try/finally rather than try-with-resources.
            NamingEnumeration<SearchResult> results = searchUser(ctx, subjectName);
            try {
                if (results.hasMore()) {
                    SearchResult sr = results.next();
                    Attributes attrs = sr.getAttributes();
                    String userDn = sr.getNameInNamespace();

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
                        // Prefer the user's memberOf (e.g. the Knox embedded LDAP server, whose roles-lookup
                        // interceptor publishes roles only there); fall back to scanning the group entries.
                        Attribute memberOf = attrs.get(MEMBER_OF);
                        List<String> roles = memberOf != null ? rolesFromMemberOf(memberOf) : Collections.emptyList();
                        if (roles.isEmpty()) {
                            roles = fetchRoles(ctx, userDn);
                        }

                        userParams.put("roles", roles);
                    }
                }
            } finally {
                closeEnumeration(results);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch user parameters for " + subjectName, e);
        } finally {
            closeContext(ctx);
        }

        return userParams;
    }

    /**
     * Find the user entry. With {@code userSearchBase} configured the user is searched in that subtree
     * (required for directories that cannot read a user entry directly by DN, such as the Knox embedded
     * LDAP server proxying a backend); otherwise the entry is read directly from the {@code userDnTemplate} DN.
     */
    private NamingEnumeration<SearchResult> searchUser(LdapContext ctx, String subjectName) throws Exception {
        SearchControls controls = new SearchControls();
        controls.setReturningAttributes(ATTRIBUTES);
        if (ldapUserSearchBase != null) {
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            return ctx.search(ldapUserSearchBase, ldapUserSearchFilter, new Object[]{subjectName}, controls);
        }

        // Escape the subject before interpolating it into the DN template. Without escaping a
        // crafted subject (e.g. "x,ou=admins") could inject additional DN components (LDAP
        // injection). Rdn.escapeValue escapes the RDN value per RFC 2253.
        String userDn = String.format(Locale.US, ldapUserDnTemplate, Rdn.escapeValue(subjectName));
        controls.setSearchScope(SearchControls.OBJECT_SCOPE);
        return ctx.search(userDn, "(objectClass=*)", controls);
    }

    /**
     * Role names are the cn of the memberOf groups that sit directly under the groups base,
     * i.e. the same groups {@link #fetchRoles} would consider.
     */
    private List<String> rolesFromMemberOf(Attribute memberOf) throws Exception {
        List<String> roles = new ArrayList<>();
        LdapName groupsBase = new LdapName("ou=groups," + ldapBaseDn);
        NamingEnumeration<?> e = memberOf.getAll();
        try {
            while (e.hasMore()) {
                LdapName groupDn;
                try {
                    groupDn = new LdapName((String) e.next());
                } catch (InvalidNameException ignored) {
                    continue;
                }
                if (groupDn.size() == groupsBase.size() + 1 && groupDn.startsWith(groupsBase)) {
                    Rdn rdn = groupDn.getRdn(groupDn.size() - 1);
                    if ("cn".equalsIgnoreCase(rdn.getType())) {
                        roles.add(String.valueOf(rdn.getValue()));
                    }
                }
            }
        } finally {
            closeEnumeration(e);
        }
        return roles;
    }

    private List<String> fetchRoles(LdapContext ctx, String userDn) throws Exception {
        List<String> roles = new ArrayList<>();

        SearchControls groupControls = new SearchControls();
        groupControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
        groupControls.setReturningAttributes(new String[]{"cn", "member"});

        String groupsBase = "ou=groups," + ldapBaseDn;
        // Close both the group search cursor and each member enumeration; see getParamsFor.
        NamingEnumeration<SearchResult> groupResults =
                ctx.search(groupsBase, "(objectClass=groupOfNames)", groupControls);
        try {
            while (groupResults.hasMore()) {
                SearchResult group = groupResults.next();
                Attributes groupAttrs = group.getAttributes();
                Attribute members = groupAttrs.get("member");
                if (members != null) {
                    NamingEnumeration<?> e = members.getAll();
                    try {
                        while (e.hasMore()) {
                            String memberDn = (String) e.next();
                            if (memberDn.equalsIgnoreCase(userDn)) {
                                roles.add(getAttr(groupAttrs, "cn"));
                                break;
                            }
                        }
                    } finally {
                        closeEnumeration(e);
                    }
                }
            }
        } finally {
            closeEnumeration(groupResults);
        }
        return roles;
    }

    // Package-private so tests can supply a mock context.
    LdapContext createSystemContext() throws Exception {
        if (ldapSystemPasswordAlias == null) {
            throw new IllegalStateException("No LDAP system password alias configured: set '" + LDAP_SYSTEM_PASSWORD_ALIAS
                    + "' to the name of the alias holding the password of " + ldapSystemUser);
        }
        if (ldapSystemPassword == null) {
            throw new IllegalStateException("LDAP system password alias '" + ldapSystemPasswordAlias
                    + "' (set via '" + LDAP_SYSTEM_PASSWORD_ALIAS + "') is missing or cannot be resolved");
        }
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

    private void closeEnumeration(NamingEnumeration<?> enumeration) {
        if (enumeration != null) {
            try {
                enumeration.close();
            } catch (Exception ignored) {
            }
        }
    }
}

