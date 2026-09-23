/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.ha.dispatch;

import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.knox.gateway.ha.provider.HaProvider;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Livy HA dispatch with per-instance backend routing.
 *
 * <p>Extends {@link ConfigurableHADispatch} to add per-instance backend
 * selection for Livy for Spark 3. Routes each request to the specific
 * backend identified by:
 * <ol>
 *   <li>{@code ?host=} query parameter (from Knox Homepage tile click)</li>
 *   <li>{@code Referer} header (AJAX calls from Livy UI)</li>
 *   <li>{@code KNOX_LIVY_BACKEND} sticky cookie (subsequent requests)</li>
 *   <li>Default HA active URL (fallback — round-robin)</li>
 * </ol>
 */
public class LivyHaDispatch extends ConfigurableHADispatch {

    private static final Logger LOG = Logger.getLogger(LivyHaDispatch.class.getName());

    /** Cookie name for sticky backend affinity. */
    public static final String BACKEND_COOKIE_NAME = "KNOX_LIVY_BACKEND";

    /** Query parameter for per-instance routing. */
    public static final String HOST_PARAM = "host";

    /** Cookie max-age in seconds (1 hour). */
    private static final int COOKIE_MAX_AGE = 3600;

    /** Default service role if not set by framework. */
    private static final String DEFAULT_SERVICE_ROLE = "LIVY_FOR_SPARK3";

    /**
     * Our own reference to the HA provider.
     * The parent's haProvider field is private with no getter,
     * so we capture it when the framework calls setHaProvider().
     */
    private HaProvider livyHaProvider;

    // =========================================================================
    // Initialization — Framework calls setHaProvider() BEFORE init()
    // =========================================================================

    /**
     * Called by Knox framework to inject the HA provider.
     * We override to capture our own reference since parent has no getter.
     */
    @Override
    public void setHaProvider(HaProvider haProvider) {
        super.setHaProvider(haProvider);
        this.livyHaProvider = haProvider;
    }

    /**
     * Called by Knox framework after setHaProvider() and setServiceRole().
     * NO arguments — Knox dispatch init() is NOT the same as Filter.init(FilterConfig).
     */
    @Override
    public void init() {
        super.init();
        LOG.log(Level.INFO, "LivyHaDispatch initialized for role: {0}, haProvider: {1}",
                new Object[]{getServiceRoleSafe(), (livyHaProvider != null ? "present" : "null")});
    }

    // =========================================================================
    // Core override — per-instance routing
    // =========================================================================

    @Override
    public void executeRequest(HttpUriRequest outboundRequest,
                               HttpServletRequest inboundRequest,
                               HttpServletResponse outboundResponse)
            throws IOException {

        String targetBackend = resolveTargetBackend(inboundRequest);

        if (targetBackend != null) {
            LOG.log(Level.FINE, "LivyHaDispatch: routing to backend: {0}", targetBackend);
            try {
                URI originalUri = outboundRequest.getURI();
                URI rewrittenUri = rewriteUri(originalUri, targetBackend);
                ((HttpRequestBase) outboundRequest).setURI(rewrittenUri);
                setBackendCookie(outboundResponse, targetBackend);
            } catch (URISyntaxException e) {
                LOG.log(Level.WARNING,
                        "LivyHaDispatch: URI rewrite failed, falling back to HA default", e);
            }
        }

        // Delegate to parent — preserves failover, retry, HA logic
        super.executeRequest(outboundRequest, inboundRequest, outboundResponse);
    }

    // =========================================================================
    // Backend resolution (priority order)
    // =========================================================================

    /**
     * Resolves the target backend URL.
     * Priority: ?host= > Referer > cookie > null (HA default).
     *
     * <p>Package-private for unit testing.
     */
    String resolveTargetBackend(HttpServletRequest request) {
        // Priority 1: ?host= query parameter
        String hostParam = request.getParameter(HOST_PARAM);
        if (hostParam != null && !hostParam.isEmpty()) {
            String matched = findMatchingBackend(hostParam);
            if (matched != null) {
                return matched;
            }
            LOG.log(Level.FINE,
                    "LivyHaDispatch: ?host= did not match any backend: {0}", hostParam);
        }

        // Priority 2: Referer header
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isEmpty()) {
            String hostFromReferer = extractHostParam(referer);
            if (hostFromReferer != null) {
                String matched = findMatchingBackend(hostFromReferer);
                if (matched != null) {
                    return matched;
                }
            }
        }

        // Priority 3: sticky cookie
        String cookieValue = getBackendCookieValue(request);
        if (cookieValue != null) {
            String matched = findMatchingBackend(cookieValue);
            if (matched != null) {
                return matched;
            }
        }

        // No match — parent uses default HA active URL
        return null;
    }

    /**
     * Finds a configured backend URL matching the given host identifier.
     * Matching is hostname-based, case-insensitive, with short hostname fallback.
     *
     * <p>Package-private for unit testing.
     */
    String findMatchingBackend(String hostIdentifier) {
        if (hostIdentifier == null || hostIdentifier.isEmpty()) {
            return null;
        }

        List<String> backends = getBackendUrls();
        if (backends == null || backends.isEmpty()) {
            return null;
        }

        String targetHostname = extractHostname(hostIdentifier);
        if (targetHostname == null || targetHostname.isEmpty()) {
            return null;
        }

        // Exact FQDN match
        for (String backend : backends) {
            String backendHostname = extractHostname(backend);
            if (backendHostname != null && backendHostname.equalsIgnoreCase(targetHostname)) {
                return backend;
            }
        }

        // Short hostname fallback (e.g. "host-4" matches "host-4.example.com")
        String targetShort = targetHostname.split("\\.")[0].toLowerCase(Locale.ROOT);
        for (String backend : backends) {
            String backendHostname = extractHostname(backend);
            if (backendHostname != null) {
                String backendShort = backendHostname.split("\\.")[0].toLowerCase(Locale.ROOT);
                if (backendShort.equals(targetShort)) {
                    return backend;
                }
            }
        }

        return null;
    }

    // =========================================================================
    // Helper methods — protected for testability
    // =========================================================================

    /**
     * Returns configured backend URLs from HA provider.
     * Protected so unit tests can override without needing real HA provider.
     */
    protected List<String> getBackendUrls() {
        if (livyHaProvider != null) {
            try {
                return livyHaProvider.getURLs(getServiceRoleSafe());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to get backend URLs", e);
            }
        }
        return null;
    }

    /**
     * Gets service role safely — tries parent's getServiceRole(),
     * falls back to default if null.
     */
    private String getServiceRoleSafe() {
        try {
            String role = getServiceRole();
            if (role != null && !role.isEmpty()) {
                return role;
            }
        } catch (Exception e) {
            // getServiceRole() might not exist in some Knox versions
        }
        return DEFAULT_SERVICE_ROLE;
    }

    /**
     * Extracts hostname from a URL or hostname:port string.
     */
    protected static String extractHostname(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        try {
            if (url.contains("://")) {
                URI uri = new URI(url);
                return uri.getHost();
            }
            int colonIdx = url.indexOf(':');
            return colonIdx > 0 ? url.substring(0, colonIdx) : url;
        } catch (URISyntaxException e) {
            String work = url;
            int schemeEnd = work.indexOf("://");
            if (schemeEnd >= 0) {
                work = work.substring(schemeEnd + 3);
            }
            int colonIdx = work.indexOf(':');
            int slashIdx = work.indexOf('/');
            int end = work.length();
            if (colonIdx > 0) {
                end = Math.min(end, colonIdx);
            }
            if (slashIdx > 0) {
                end = Math.min(end, slashIdx);
            }
            return work.substring(0, end);
        }
    }

    /**
     * Extracts the "host=" parameter value from a URL or query string.
     */
    protected static String extractHostParam(String urlOrQueryString) {
        if (urlOrQueryString == null || urlOrQueryString.isEmpty()) {
            return null;
        }
        int idx = urlOrQueryString.indexOf("host=");
        if (idx < 0) {
            return null;
        }
        String value = urlOrQueryString.substring(idx + 5);
        int ampIdx = value.indexOf('&');
        if (ampIdx > 0) {
            value = value.substring(0, ampIdx);
        }
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    /**
     * Removes the "host=" parameter from a query string.
     */
    protected static String removeHostParam(String queryString) {
        if (queryString == null) {
            return null;
        }
        String cleaned = queryString
                .replaceAll("(^|&)host=[^&]*", "")
                .replaceAll("^&+", "")
                .replaceAll("&+$", "")
                .replaceAll("&&+", "&");
        return cleaned;
    }

    /**
     * Rewrites URI to target the given backend.
     */
    private URI rewriteUri(URI original, String targetBackend) throws URISyntaxException {
        URI backend = new URI(targetBackend);
        String cleanedQuery = removeHostParam(original.getQuery());
        return new URI(
                backend.getScheme(),
                null,
                backend.getHost(),
                backend.getPort(),
                original.getPath(),
                (cleanedQuery != null && !cleanedQuery.isEmpty()) ? cleanedQuery : null,
                original.getFragment()
        );
    }

    /**
     * Sets KNOX_LIVY_BACKEND sticky cookie.
     */
    private void setBackendCookie(HttpServletResponse response, String backendUrl) {
        Cookie cookie = new Cookie(BACKEND_COOKIE_NAME, backendUrl);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setMaxAge(COOKIE_MAX_AGE);
        response.addCookie(cookie);
    }

    /**
     * Reads KNOX_LIVY_BACKEND cookie value from request.
     */
    private String getBackendCookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (BACKEND_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}