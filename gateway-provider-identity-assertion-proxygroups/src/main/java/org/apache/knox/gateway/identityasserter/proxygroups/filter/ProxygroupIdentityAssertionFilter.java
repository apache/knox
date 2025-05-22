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
package org.apache.knox.gateway.identityasserter.proxygroups.filter;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.identityasserter.hadoop.groups.filter.HadoopGroupProviderFilter;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.util.AuthFilterUtils;
import org.apache.knox.gateway.util.AuthorizationException;
import org.apache.knox.gateway.util.HttpExceptionUtils;

import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.apache.knox.gateway.util.AuthFilterUtils.DEFAULT_IMPERSONATION_MODE;
import static org.apache.knox.gateway.util.AuthFilterUtils.IMPERSONATION_MODE;
import static org.apache.knox.gateway.util.AuthFilterUtils.ImpersonationFlags.GROUP_IMPERSONATION;

public class ProxygroupIdentityAssertionFilter extends HadoopGroupProviderFilter {
    private static final ProxygroupProviderMessages LOG = MessagesFactory.get(ProxygroupProviderMessages.class);
    private String impersonationMode = DEFAULT_IMPERSONATION_MODE;
    private EnumSet<AuthFilterUtils.ImpersonationFlags> impersonationFlags;
    private String topologyName;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        super.init(filterConfig);
        topologyName = (String) filterConfig.getServletContext().getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE);
        impersonationFlags = AuthFilterUtils.getImpersonationEnabledFlags(filterConfig);
        impersonationMode = filterConfig.getInitParameter(IMPERSONATION_MODE) != null ? filterConfig.getInitParameter(IMPERSONATION_MODE) : DEFAULT_IMPERSONATION_MODE;

        /* Add group impersonation provider */
        if (!impersonationFlags.isEmpty() && impersonationFlags.contains(GROUP_IMPERSONATION)) {
            final List<String> initParameterNames = AuthFilterUtils.getInitParameterNamesAsList(filterConfig);
            String topologyName = (String) filterConfig.getServletContext().getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE);
            AuthFilterUtils.refreshProxyGroupsConfiguration(filterConfig, initParameterNames, topologyName, ProxygroupsIdentityAsserterDeploymentContributor.NAME);
        }

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        final Subject subject = getSubject();
        /*
         * in case of user auth failure, mapped principal is same as real principal, this is so that
         * group lookup works needed for group proxy auth check
         */
        String mappedPrincipalName = SubjectUtils.getEffectivePrincipalName(subject);

        boolean isProxyUserAuthorized = false;
        boolean isProxyGroupAuthorized = false;

        /* Save exceptions if needed */
        AuthorizationException userAuthorizationException = null;
        AuthorizationException groupsAuthorizationException = null;

        if (impersonationFlags.contains(AuthFilterUtils.ImpersonationFlags.USER_IMPERSONATION)) {
            try {
                mappedPrincipalName = handleProxyUserImpersonation(request, subject);
                isProxyUserAuthorized = true;
            } catch (AuthorizationException e) {
                LOG.hadoopAuthProxyUserFailed(e);
                userAuthorizationException = e;
            }
        }

        mappedPrincipalName = getMappedPrincipalName(request, mappedPrincipalName, subject);
        String[] groups = getMappedGroups(request, mappedPrincipalName, subject);

        if (impersonationFlags.contains(AuthFilterUtils.ImpersonationFlags.GROUP_IMPERSONATION)) {
            try {
                mappedPrincipalName = handleProxyGroupImpersonation(request, subject, groups);
                isProxyGroupAuthorized = true;
            } catch (AuthorizationException e) {
                LOG.hadoopAuthProxyGroupFailed(e);
                groupsAuthorizationException = e;
            }
        }

        // Determine if authorization succeeded based on the impersonation mode
        /*
         * Impersonation mode can only come into play when both proxy user and proxy group are enabled.
         * For cases where only one of them is enabled, we set impersonation mode to OR and rely on either
         * of the modes to succeed.
         *
         * This is the case where one of the impersonations is disabled but hadoop.impersonation.mode value is
         * configured.
         */
        if (impersonationFlags.isEmpty() || (!impersonationFlags.contains(AuthFilterUtils.ImpersonationFlags.USER_IMPERSONATION) || !impersonationFlags.contains(AuthFilterUtils.ImpersonationFlags.GROUP_IMPERSONATION))) {
            impersonationMode = DEFAULT_IMPERSONATION_MODE;
        }

        boolean isAuthorized = "AND".equalsIgnoreCase(impersonationMode)
                ? (isProxyUserAuthorized && isProxyGroupAuthorized)
                : (isProxyUserAuthorized || isProxyGroupAuthorized);

        if (isAuthorized) {
            LOG.hadoopAuthProxyAccessSuccess(SubjectUtils.getEffectivePrincipalName(subject), request.getParameter(AuthFilterUtils.QUERY_PARAMETER_DOAS), impersonationMode);
        } else {
            // Handle authorization failures
            if (groupsAuthorizationException != null) {
                LOG.hadoopAuthProxyGroupFailed(groupsAuthorizationException);
                HttpExceptionUtils.createServletExceptionResponse((HttpServletResponse) response, HttpServletResponse.SC_FORBIDDEN, groupsAuthorizationException);
                return;
            } else if (userAuthorizationException != null) {
                LOG.hadoopAuthProxyGroupFailed(groupsAuthorizationException);
                HttpExceptionUtils.createServletExceptionResponse((HttpServletResponse) response, HttpServletResponse.SC_FORBIDDEN, userAuthorizationException);
                return;
            } else {
                AuthorizationException e = new AuthorizationException("User: " + SubjectUtils.getEffectivePrincipalName(subject) + " is not allowed to impersonate." + "Proxyuser auth result: " + isProxyUserAuthorized + " Proxygroup auth result: " + isProxyGroupAuthorized);
                LOG.hadoopAuthProxyGroupFailed(e);
                HttpExceptionUtils.createServletExceptionResponse((HttpServletResponse) response, HttpServletResponse.SC_FORBIDDEN, e);
                return;
            }
        }

        HttpServletRequestWrapper wrapper = wrapHttpServletRequest(request, mappedPrincipalName);
        continueChainAsPrincipal(wrapper, response, chain, mappedPrincipalName, unique(groups));
    }

    private String handleProxyGroupImpersonation(final ServletRequest request, final Subject subject, String[] groups) throws AuthorizationException {
        String principalName = SubjectUtils.getEffectivePrincipalName(subject);
        if (!impersonationFlags.isEmpty() && impersonationFlags.contains(GROUP_IMPERSONATION)) {
            final String doAsUser = request.getParameter(AuthFilterUtils.QUERY_PARAMETER_DOAS);
            if (doAsUser != null && !doAsUser.equals(principalName)) {
                LOG.hadoopAuthDoAsUser(doAsUser, principalName, request.getRemoteAddr());
                if (principalName != null) {
                    AuthFilterUtils.authorizeGroupImpersonationRequest((HttpServletRequest) request, principalName, doAsUser, topologyName, ProxygroupsIdentityAsserterDeploymentContributor.NAME, Arrays.asList(groups));
                    LOG.hadoopAuthProxyGroupSuccess(principalName, doAsUser);
                    principalName = doAsUser;
                }
            }
        }
        return principalName;
    }


}
