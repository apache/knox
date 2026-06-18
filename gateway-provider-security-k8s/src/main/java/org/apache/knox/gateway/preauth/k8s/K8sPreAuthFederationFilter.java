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
package org.apache.knox.gateway.preauth.k8s;

import org.apache.knox.gateway.preauth.filter.AbstractPreAuthFederationFilter;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.util.Set;

public class K8sPreAuthFederationFilter extends AbstractPreAuthFederationFilter {
    private String userHeader = ServiceAccountValidator.USER_HEADER_DEFAULT;
    private K8sServiceAccountResolver resolver;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        super.init(filterConfig);
        String configured = filterConfig.getInitParameter(ServiceAccountValidator.USER_HEADER_PARAM);
        if (configured != null && !configured.isEmpty()) {
            userHeader = configured;
        }

        long ttlSeconds = longParam(filterConfig,
                ServiceAccountValidator.CACHE_TTL_SECONDS_PARAM,
                ServiceAccountValidator.CACHE_TTL_SECONDS_DEFAULT);
        long maxSize = longParam(filterConfig,
                ServiceAccountValidator.CACHE_MAX_SIZE_PARAM,
                ServiceAccountValidator.CACHE_MAX_SIZE_DEFAULT);
        if (ttlSeconds <= 0) {
            throw new ServletException(ServiceAccountValidator.CACHE_TTL_SECONDS_PARAM
                    + " must be > 0 (got " + ttlSeconds + ")");
        }
        if (maxSize <= 0) {
            throw new ServletException(ServiceAccountValidator.CACHE_MAX_SIZE_PARAM
                    + " must be > 0 (got " + maxSize + ")");
        }

        if (resolver == null) {
            resolver = createResolver(Duration.ofSeconds(ttlSeconds), maxSize);
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setAttribute(ServiceAccountValidator.RESOLVER_REQUEST_ATTR, resolver);
        try {
            super.doFilter(request, response, chain);
        } finally {
            request.removeAttribute(ServiceAccountValidator.RESOLVER_REQUEST_ATTR);
        }
    }

    @Override
    public void destroy() {
        if (resolver != null) {
            resolver.close();
            resolver = null;
        }
        super.destroy();
    }

    @Override
    protected String getPrimaryPrincipal(HttpServletRequest httpRequest) {
        return httpRequest.getHeader(userHeader);
    }

    @Override
    protected void addGroupPrincipals(HttpServletRequest request, Set<Principal> principals) {
    }

    @Override
    protected String getValidationFailureMessage() {
        return "Kubernetes pre-authentication failed: SPIFFE/ServiceAccount validation rejected the request.";
    }

    @Override
    protected String getMissingPrincipalMessage() {
        return "Missing required user header for Kubernetes pre-authentication.";
    }

    protected K8sServiceAccountResolver createResolver(Duration ttl, long maxSize) {
        return new K8sServiceAccountResolver(ttl, maxSize);
    }

    private static long longParam(FilterConfig cfg, String name, long defaultValue) {
        final String v = cfg.getInitParameter(name);
        if (v == null || v.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
