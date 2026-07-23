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
package org.apache.knox.gateway.ha.dispatch;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.ha.dispatch.i18n.HaDispatchMessages;
import org.apache.knox.gateway.ha.config.HaConfigurations;
import org.apache.knox.gateway.ha.provider.HaServiceConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public interface CommonHaDispatch {

    HaDispatchMessages LOG = MessagesFactory.get(HaDispatchMessages.class);
    Map<String, String> urlToHashLookup = new HashMap<>();
    Map<String, String> hashToUrlLookup = new HashMap<>();
    String FAILOVER_COUNTER_ATTRIBUTE = "dispatch.ha.failover.counter";
    List<String> nonIdempotentRequests = Arrays.asList("POST", "PATCH", "CONNECT");

    String getServiceRole();

    URI getDispatchUrl(HttpServletRequest request);

    HaConfigurations getHaConfigurations();

    AtomicReference<String> getActiveURL();

    void setActiveURL(String url);

    default void initializeCommonHaDispatch(HaServiceConfig serviceConfig) {
        getHaConfigurations().setLoadBalancingEnabled(serviceConfig.isLoadBalancingEnabled());
        getHaConfigurations().setStickySessionsEnabled(getHaConfigurations().isLoadBalancingEnabled() && serviceConfig.isStickySessionEnabled());

        if (getHaConfigurations().isStickySessionEnabled()) {
            getHaConfigurations().setStickySessionCookieName(serviceConfig.getStickySessionCookieName());
        }

        if (StringUtils.isNotBlank(serviceConfig.getStickySessionDisabledUserAgents())) {
            getHaConfigurations().setDisableLoadBalancingForUserAgents(Arrays.asList(serviceConfig.getStickySessionDisabledUserAgents()
                    .trim()
                    .split("\\s*,\\s*")));
        }
        setupUrlHashLookup();

        /* setup the active URL for non-LB case */
        setActiveURL(getHaConfigurations().getHaProvider().getActiveURL(getServiceRole()));

        // Suffix the cookie name by the service to make it unique
        // The cookie path is NOT unique since Knox is stripping the service name.
        getHaConfigurations().setStickySessionCookieName(getHaConfigurations().getStickySessionCookieName() + '-' + getServiceRole());

        // Set the failover parameters
        getHaConfigurations().setMaxFailoverAttempts(serviceConfig.getMaxFailoverAttempts());
        getHaConfigurations().setFailoverSleep(serviceConfig.getFailoverSleep());
        getHaConfigurations().setFailoverNonIdempotentRequestEnabled(serviceConfig.isFailoverNonIdempotentRequestEnabled());
        getHaConfigurations().setNoFallbackEnabled(getHaConfigurations().isStickySessionEnabled() && serviceConfig.isNoFallbackEnabled());
    }

    default void setKnoxHaCookie(final HttpUriRequest outboundRequest, final HttpServletRequest inboundRequest,
                                 final HttpServletResponse outboundResponse, boolean sslEnabled) {
        if (getHaConfigurations().isStickySessionEnabled()) {
            List<Cookie> serviceHaCookies = Collections.emptyList();
            if (inboundRequest.getCookies() != null) {
                serviceHaCookies = Arrays
                        .stream(inboundRequest.getCookies())
                        .filter(cookie -> getHaConfigurations().getStickySessionCookieName().equals(cookie.getName()))
                        .collect(Collectors.toList());
            }

            /* if the inbound request has a valid hash then no need to set a different hash */
            if (serviceHaCookies.isEmpty() || !hashToUrlLookup.containsKey(serviceHaCookies.get(0).getValue())) {
                /**
                 * Due to concurrency issues haProvider.getActiveURL() will not return the accurate list
                 * This will cause issues where original request goes to host-1 and cookie is set for host-2 - because
                 * haProvider.getActiveURL() returned host-2. To prevent this from happening we need to make sure
                 * we set cookie for the endpoint that was served and not rely on haProvider.getActiveURL().
                 * let LBing logic take care of rotating urls.
                 **/
                final List<String> urls = getHaConfigurations().getHaProvider().getURLs(getServiceRole())
                        .stream()
                        .filter(u -> u.contains(outboundRequest.getURI().getHost()))
                        .collect(Collectors.toList());
                final String cookieValue = urlToHashLookup.get(urls.get(0));
                Cookie stickySessionCookie = new Cookie(getHaConfigurations().getStickySessionCookieName(), cookieValue);
                stickySessionCookie.setPath(inboundRequest.getContextPath());
                stickySessionCookie.setMaxAge(-1);
                stickySessionCookie.setHttpOnly(true);
                stickySessionCookie.setSecure(sslEnabled);
                outboundResponse.addCookie(stickySessionCookie);
            }
        }
    }

    default Optional<URI> getBackendFromHaCookie(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest) {
        if (getHaConfigurations().isLoadBalancingEnabled() && getHaConfigurations().isStickySessionEnabled() && inboundRequest.getCookies() != null) {
            for (Cookie cookie : inboundRequest.getCookies()) {
                if (getHaConfigurations().getStickySessionCookieName().equals(cookie.getName())) {
                    String backendURLHash = cookie.getValue();
                    String backendURL = hashToUrlLookup.get(backendURLHash);
                    // Make sure that the url provided is actually a valid backend url
                    if (getHaConfigurations().getHaProvider().getURLs(getServiceRole()).contains(backendURL)) {
                        try {
                            return Optional.of(updateBackendURL(outboundRequest.getURI(), backendURL));
                        } catch (URISyntaxException ignore) {
                            // The cookie was invalid so we just don't set it. Knox will pick a backend automatically
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }


    default String hash(String url) {
        return DigestUtils.sha256Hex(url);
    }

    /**
     * A helper function that updates the schema, host and port
     * of the URI with the provided string URL and returnes a new
     * URI object
     *
     * @param source
     * @param host
     * @return
     */
    default URI updateHostURL(final URI source, final String host) throws URISyntaxException {
        final URI newUri = new URI(host);
        final URIBuilder uriBuilder = new URIBuilder(source);
        uriBuilder.setScheme(newUri.getScheme());
        uriBuilder.setHost(newUri.getHost());
        uriBuilder.setPort(newUri.getPort());
        return uriBuilder.build();
    }

    /**
     * Re-targets a rewritten URI at a different backend, carrying over the backend's base
     * path in addition to scheme, host and port.
     */
    default URI updateBackendURL(final URI source, final String newBackend) throws URISyntaxException {
        final URI newUri = new URI(newBackend);
        final URIBuilder uriBuilder = new URIBuilder(source);
        uriBuilder.setScheme(newUri.getScheme());
        uriBuilder.setHost(newUri.getHost());
        uriBuilder.setPort(newUri.getPort());
        final String newBasePath = normalizeBasePath(newUri.getPath());
        final String oldBasePath = matchConfiguredBasePath(source);
        if (oldBasePath != null && !oldBasePath.equals(newBasePath)) {
            final String sourcePath = source.getPath() == null ? "" : source.getPath();
            uriBuilder.setPath(newBasePath + sourcePath.substring(oldBasePath.length()));
        }
        return uriBuilder.build();
    }

    default String matchConfiguredBasePath(final URI source) {
        if (source.getHost() == null) {
            return null;
        }
        final String sourcePath = source.getPath() == null ? "" : source.getPath();
        String best = null;
        for (String url : getHaConfigurations().getHaProvider().getURLs(getServiceRole())) {
            try {
                final URI poolUri = new URI(url);
                if (!source.getHost().equals(poolUri.getHost()) || effectivePort(source) != effectivePort(poolUri)) {
                    continue;
                }
                final String basePath = normalizeBasePath(poolUri.getPath());
                if (isPathPrefix(sourcePath, basePath) && (best == null || basePath.length() > best.length())) {
                    best = basePath;
                }
            } catch (URISyntaxException ignore) {
                // a malformed pool entry cannot be the URL the rewriter used
            }
        }
        return best;
    }

    static int effectivePort(final URI uri) {
        final int port = uri.getPort();
        if (port != -1) {
            return port;
        }
        final String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        return -1;
    }

    static String normalizeBasePath(final String path) {
        if (path == null) {
            return "";
        }
        String normalized = path;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    static boolean isPathPrefix(final String path, final String prefix) {
        return path.startsWith(prefix)
                && (path.length() == prefix.length() || path.charAt(prefix.length()) == '/');
    }

    default void setupUrlHashLookup() {
        for (String url : getHaConfigurations().getHaProvider().getURLs(getServiceRole())) {
            String urlHash = hash(url);
            urlToHashLookup.put(url, urlHash);
            hashToUrlLookup.put(urlHash, url);
        }
    }

    default boolean isUserAgentDisabled(HttpServletRequest inboundRequest) {
        final String userAgentFromBrowser = StringUtils.isBlank(inboundRequest.getHeader("User-Agent")) ? "" : inboundRequest.getHeader("User-Agent");
        /* disable loadblancing override */
        boolean userAgentDisabled = false;

        /* disable loadbalancing in case a configured user agent is detected to disable LB */
        if (getHaConfigurations().getDisableLoadBalancingForUserAgents().stream().anyMatch(userAgentFromBrowser::contains)) {
            userAgentDisabled = true;
            LOG.disableHALoadbalancinguserAgent(userAgentFromBrowser, getHaConfigurations().getDisableLoadBalancingForUserAgents().toString());
        }

        return userAgentDisabled;
    }

    default Optional<URI> setBackendUri(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, boolean userAgentDisabled) {
        /* if disable LB is set don't bother setting backend from cookie */
        Optional<URI> backendURI = Optional.empty();
        if (!userAgentDisabled) {
            backendURI = getBackendFromHaCookie(outboundRequest, inboundRequest);
            backendURI.ifPresent(uri -> ((HttpRequestBase) outboundRequest).setURI(uri));
        }

        if (getHaConfigurations().isLoadBalancingEnabled()) {
            if (userAgentDisabled) {
                /**
                 * case where loadbalancing is enabled
                 * and we have a HTTP request configured not to use LB
                 * use the activeURL
                 */
                try {
                    ((HttpRequestBase) outboundRequest).setURI(updateHostURL(outboundRequest.getURI(), getActiveURL().get()));
                } catch (final URISyntaxException e) {
                    LOG.errorSettingActiveUrl();
                }
            } else if (!backendURI.isPresent()) {
                String nextURL = getHaConfigurations().getHaProvider().getActiveURLAndAdvance(getServiceRole());
                if (nextURL != null) {
                    try {
                        ((HttpRequestBase) outboundRequest).setURI(updateBackendURL(outboundRequest.getURI(), nextURL));
                    } catch (final URISyntaxException e) {
                        LOG.errorSettingActiveUrl();
                    }
                }
            }
        }

        return backendURI;
    }

    /**
     * A helper method that marks an endpoint failed.
     * Changes HA Provider state.
     * Changes ActiveUrl state.
     * Changes for inbound urls should be handled by calling functions.
     *
     * @param outboundRequest
     * @param inboundRequest
     * @return current failover counter
     */
    default AtomicInteger markEndpointFailed(final HttpUriRequest outboundRequest, final HttpServletRequest inboundRequest) {
        synchronized (this) {
            getHaConfigurations().getHaProvider().markFailedURL(getServiceRole(), outboundRequest.getURI().toString());
            AtomicInteger counter = (AtomicInteger) inboundRequest.getAttribute(FAILOVER_COUNTER_ATTRIBUTE);
            if (counter == null) {
                counter = new AtomicInteger(0);
            }

            if (counter.incrementAndGet() <= getHaConfigurations().getMaxFailoverAttempts()) {
                setupUrlHashLookup(); // refresh the url hash after failing a url
                /* in case of failover update the activeURL variable */
                getActiveURL().set(outboundRequest.getURI().toString());
            }
            return counter;
        }
    }

    default HttpServletRequest prepareForFailover(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest) {
        //null out target url so that rewriters run again
        inboundRequest.setAttribute(AbstractGatewayFilter.TARGET_REQUEST_URL_ATTRIBUTE_NAME, null);
        // Make sure to remove the ha cookie from the request
        inboundRequest = new StickySessionCookieRemovedRequest(getHaConfigurations().getStickySessionCookieName(), inboundRequest);
        ((HttpRequestBase) outboundRequest).setURI(getDispatchUrl(inboundRequest));

        if (getHaConfigurations().isLoadBalancingEnabled() && !isUserAgentDisabled(inboundRequest)) {
            final String nextURL = getHaConfigurations().getHaProvider().getActiveURLAndAdvance(getServiceRole());
            if (nextURL != null) {
                try {
                    ((HttpRequestBase) outboundRequest).setURI(updateBackendURL(outboundRequest.getURI(), nextURL));
                } catch (final URISyntaxException e) {
                    LOG.errorSettingActiveUrl();
                }
            }
        }

        if (getHaConfigurations().getFailoverSleep() > 0) {
            try {
                Thread.sleep(getHaConfigurations().getFailoverSleep());
            } catch (InterruptedException e) {
                LOG.failoverSleepFailed(getServiceRole(), e);
                Thread.currentThread().interrupt();
            }
        }
        LOG.failingOverRequest(outboundRequest.getURI().toString());
        return inboundRequest;
    }

    default boolean disabledFailoverHandled(HttpServletRequest inboundRequest, HttpServletResponse outboundResponse) throws IOException {
        // Check whether the session cookie is present
        Optional<Cookie> sessionCookie = Optional.empty();
        if (inboundRequest.getCookies() != null) {
            sessionCookie =
                    Arrays.stream(inboundRequest.getCookies())
                            .filter(cookie -> getHaConfigurations().getStickySessionCookieName().equals(cookie.getName()))
                            .findFirst();
        }

        // Check for a case where no fallback is configured
        if (getHaConfigurations().isStickySessionEnabled() && getHaConfigurations().isNoFallbackEnabled() && sessionCookie.isPresent()) {
            LOG.noFallbackError();
            outboundResponse.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Service connection error, HA failover disabled");
            return true;
        }
        return false;
    }

    default boolean isNonIdempotentAndNonIdempotentFailoverDisabled(HttpUriRequest outboundRequest) {
        return !getHaConfigurations().isFailoverNonIdempotentRequestEnabled() && nonIdempotentRequests.stream().anyMatch(outboundRequest.getMethod()::equalsIgnoreCase);
    }
}
