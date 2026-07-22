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

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.preauth.filter.PreAuthValidationException;
import org.apache.knox.gateway.preauth.filter.PreAuthValidator;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;

public class ServiceAccountValidator implements PreAuthValidator {
    private static final K8sPreAuthMessages LOG = MessagesFactory.get(K8sPreAuthMessages.class);

    public static final String VALIDATION_METHOD_VALUE = "preauth.k8s.service.account.validation";
    private static final String PARAM_PREFIX = "preauth.k8s.sa.";
    public static final String SPIFFE_HEADER_PARAM = PARAM_PREFIX + "spiffe.header";
    public static final String SPIFFE_HEADER_DEFAULT = "x-spiffe-id";
    public static final String USER_HEADER_PARAM = PARAM_PREFIX + "custom.header";
    public static final String USER_HEADER_DEFAULT = "x-knoxidf-obo.username";
    public static final String USER_ANNOTATION_PARAM = PARAM_PREFIX + "user.annotation";
    public static final String USER_ANNOTATION_DEFAULT = "knox.apache.org/owner-username";
    public static final String CACHE_TTL_SECONDS_PARAM = PARAM_PREFIX + "cache.ttl.seconds";
    public static final long CACHE_TTL_SECONDS_DEFAULT = 60L;
    public static final String CACHE_MAX_SIZE_PARAM = PARAM_PREFIX + "cache.max.size";
    public static final long CACHE_MAX_SIZE_DEFAULT = 1000L;

    private K8sServiceAccountResolver resolver;

    public ServiceAccountValidator() {
    }

    @Override
    public void init(FilterConfig filterConfig) throws Exception {
        final long ttlSeconds = getAndVerifyParam(filterConfig, CACHE_TTL_SECONDS_PARAM, CACHE_TTL_SECONDS_DEFAULT);
        final long maxSize =  getAndVerifyParam(filterConfig, CACHE_MAX_SIZE_PARAM, CACHE_MAX_SIZE_DEFAULT);

        if (resolver == null) {
            resolver = createResolver(Duration.ofSeconds(ttlSeconds), maxSize);
        }
    }

    protected K8sServiceAccountResolver createResolver(Duration duration, long maxSize) {
        return new K8sServiceAccountResolver(duration, maxSize);
    }

    private long getAndVerifyParam(final FilterConfig filterConfig, final String paramName, final long defaultValue) throws Exception {
        final long paramValue = longParam(filterConfig, paramName, defaultValue);
        if (paramValue <= 0) {
            throw new ServletException(paramName + " must be > 0 (got " + paramValue + ")");
        }
        return paramValue;
    }

    @Override
    public void destroy() {
        if (resolver != null) {
            resolver.close();
            resolver = null;
        }
    }

    @Override
    public boolean validate(HttpServletRequest httpRequest, FilterConfig filterConfig)
            throws PreAuthValidationException {
        final String spiffeHeader = paramOrDefault(filterConfig, SPIFFE_HEADER_PARAM, SPIFFE_HEADER_DEFAULT);
        final String userHeader = paramOrDefault(filterConfig, USER_HEADER_PARAM, USER_HEADER_DEFAULT);
        final String annotationKey = paramOrDefault(filterConfig, USER_ANNOTATION_PARAM, USER_ANNOTATION_DEFAULT);

        final String spiffeRaw = httpRequest.getHeader(spiffeHeader);
        final String assertedUser = httpRequest.getHeader(userHeader);

        if (spiffeRaw == null || spiffeRaw.isEmpty()) {
            LOG.missingSpiffeHeader(spiffeHeader);
            return false;
        }
        if (assertedUser == null || assertedUser.isEmpty()) {
            LOG.missingUserHeader(userHeader);
            return false;
        }

        final Optional<SpiffeId> parsed = SpiffeId.parse(spiffeRaw);
        if (parsed.isEmpty()) {
            LOG.unparseableSpiffeId(spiffeRaw, assertedUser);
            return false;
        }
        final SpiffeId spiffe = parsed.get();

        final Optional<String> ownerFromSa = resolver.getAnnotation(spiffe.namespace(), spiffe.serviceAccount(), annotationKey);
        if (ownerFromSa.isEmpty()) {
            LOG.missingServiceAccountAnnotation(spiffe.namespace(), spiffe.serviceAccount(),
                    annotationKey, assertedUser, spiffeRaw);
            return false;
        }

        final boolean match = ownerFromSa.get().equals(assertedUser);
        if (!match) {
            LOG.assertedUserDoesNotMatchAnnotation(assertedUser, spiffe.namespace(),
                    spiffe.serviceAccount(), annotationKey, spiffeRaw);
        }
        return match;
    }

    @Override
    public String getName() {
        return VALIDATION_METHOD_VALUE;
    }

    private static String paramOrDefault(FilterConfig cfg, String name, String defaultValue) {
        final String v = cfg.getInitParameter(name);
        return (v == null || v.isEmpty()) ? defaultValue : v;
    }

    private long longParam(FilterConfig cfg, String name, long defaultValue) {
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
