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
import org.apache.knox.gateway.ha.provider.HaProvider;
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

    boolean isStickySessionEnabled();

    void setStickySessionsEnabled(boolean enabled);

    String getStickySessionCookieName();

    void setStickySessionCookieName(String stickySessionCookieName);

    HaProvider getHaProvider();

    String getServiceRole();

    void setLoadBalancingEnabled(boolean enabled);

    boolean isLoadBalancingEnabled();

    List<String> getDisableLoadBalancingForUserAgents();

    void setDisableLoadBalancingForUserAgents(List<String> disableLoadBalancingForUserAgents);

    AtomicReference<String> getActiveURL();

    void setActiveURL(String url);

    int getMaxFailoverAttempts();

    void setMaxFailoverAttempts(int maxFailoverAttempts);

    int getFailoverSleep();

    void setFailoverSleep(int failoverSleep);

    void setFailoverNonIdempotentRequestEnabled(boolean enabled);

    boolean isFailoverNonIdempotentRequestEnabled();

    void setNoFallbackEnabled(boolean enabled);

    boolean isNoFallbackEnabled();

    URI getDispatchUrl(HttpServletRequest request);

    default void initializeCommonHaDispatch(HaServiceConfig serviceConfig) {
        setLoadBalancingEnabled(serviceConfig.isLoadBalancingEnabled());
        setStickySessionsEnabled(isLoadBalancingEnabled() && serviceConfig.isStickySessionEnabled());

        if (isStickySessionEnabled()) {
            setStickySessionCookieName(serviceConfig.getStickySessionCookieName());
        }

        if (StringUtils.isNotBlank(serviceConfig.getStickySessionDisabledUserAgents())) {
            setDisableLoadBalancingForUserAgents(Arrays.asList(serviceConfig.getStickySessionDisabledUserAgents()
                    .trim()
                    .split("\\s*,\\s*")));
        }
        setupUrlHashLookup();

        /* setup the active URL for non-LB case */
        setActiveURL(getHaProvider().getActiveURL(getServiceRole()));

        // Suffix the cookie name by the service to make it unique
        // The cookie path is NOT unique since Knox is stripping the service name.
        setStickySessionCookieName(getStickySessionCookieName() + '-' + getServiceRole());

        // Set the failover parameters
        setMaxFailoverAttempts(serviceConfig.getMaxFailoverAttempts());
        setFailoverSleep(serviceConfig.getFailoverSleep());
        setFailoverNonIdempotentRequestEnabled(serviceConfig.isFailoverNonIdempotentRequestEnabled());
        setNoFallbackEnabled(isStickySessionEnabled() && serviceConfig.isNoFallbackEnabled());
    }

    default void setKnoxHaCookie(final HttpUriRequest outboundRequest, final HttpServletRequest inboundRequest,
                                 final HttpServletResponse outboundResponse, boolean sslEnabled) {
        if (isStickySessionEnabled()) {
            List<Cookie> serviceHaCookies = Collections.emptyList();
            if (inboundRequest.getCookies() != null) {
                serviceHaCookies = Arrays
                        .stream(inboundRequest.getCookies())
                        .filter(cookie -> getStickySessionCookieName().equals(cookie.getName()))
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
                final List<String> urls = getHaProvider().getURLs(getServiceRole())
                        .stream()
                        .filter(u -> u.contains(outboundRequest.getURI().getHost()))
                        .collect(Collectors.toList());
                final String cookieValue = urlToHashLookup.get(urls.get(0));
                Cookie stickySessionCookie = new Cookie(getStickySessionCookieName(), cookieValue);
                stickySessionCookie.setPath(inboundRequest.getContextPath());
                stickySessionCookie.setMaxAge(-1);
                stickySessionCookie.setHttpOnly(true);
                stickySessionCookie.setSecure(sslEnabled);
                outboundResponse.addCookie(stickySessionCookie);
            }
        }
    }

    default Optional<URI> getBackendFromHaCookie(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest) {
        if (isLoadBalancingEnabled() && isStickySessionEnabled() && inboundRequest.getCookies() != null) {
            for (Cookie cookie : inboundRequest.getCookies()) {
                if (getStickySessionCookieName().equals(cookie.getName())) {
                    String backendURLHash = cookie.getValue();
                    String backendURL = hashToUrlLookup.get(backendURLHash);
                    // Make sure that the url provided is actually a valid backend url
                    if (getHaProvider().getURLs(getServiceRole()).contains(backendURL)) {
                        try {
                            return Optional.of(updateHostURL(outboundRequest.getURI(), backendURL));
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

    default void setupUrlHashLookup() {
        for (String url : getHaProvider().getURLs(getServiceRole())) {
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
        if (getDisableLoadBalancingForUserAgents().stream().anyMatch(userAgentFromBrowser::contains)) {
            userAgentDisabled = true;
            LOG.disableHALoadbalancinguserAgent(userAgentFromBrowser, getDisableLoadBalancingForUserAgents().toString());
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

        /**
         * case where loadbalancing is enabled
         * and we have a HTTP request configured not to use LB
         * use the activeURL
         */
        if (isLoadBalancingEnabled() && userAgentDisabled) {
            try {
                ((HttpRequestBase) outboundRequest).setURI(updateHostURL(outboundRequest.getURI(), getActiveURL().get()));
            } catch (final URISyntaxException e) {
                LOG.errorSettingActiveUrl();
            }
        }

        return backendURI;
    }

    default void shiftActiveURL(boolean userAgentDisabled, Optional<URI> backendURI) {
        /**
         * 1. Load balance when loadbalancing is enabled and there are no overrides (disableLB)
         * 2. Loadbalance only when sticky session is enabled but cookie not detected
         *    i.e. when loadbalancing is enabled every request that does not have BACKEND cookie
         *    needs to be loadbalanced. If a request has BACKEND coookie and Loadbalance=on then
         *    there should be no loadbalancing.
         */
        if (isLoadBalancingEnabled() && !userAgentDisabled) {
            /* check sticky session enabled */
            if (isStickySessionEnabled()) {
                /* loadbalance only when sticky session enabled and no backend url cookie */
                if (!backendURI.isPresent()) {
                    getHaProvider().makeNextActiveURLAvailable(getServiceRole());
                } else {
                    /* sticky session enabled and backend url cookie is valid no need to loadbalance */
                    /* do nothing */
                }
            } else {
                getHaProvider().makeNextActiveURLAvailable(getServiceRole());
            }
        }
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
            getHaProvider().markFailedURL(getServiceRole(), outboundRequest.getURI().toString());
            AtomicInteger counter = (AtomicInteger) inboundRequest.getAttribute(FAILOVER_COUNTER_ATTRIBUTE);
            if (counter == null) {
                counter = new AtomicInteger(0);
            }

            if (counter.incrementAndGet() <= getMaxFailoverAttempts()) {
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
        inboundRequest = new StickySessionCookieRemovedRequest(getStickySessionCookieName(), inboundRequest);
        URI uri = getDispatchUrl(inboundRequest);
        ((HttpRequestBase) outboundRequest).setURI(uri);
        if (getFailoverSleep() > 0) {
            try {
                Thread.sleep(getFailoverSleep());
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
                            .filter(cookie -> getStickySessionCookieName().equals(cookie.getName()))
                            .findFirst();
        }

        // Check for a case where no fallback is configured
        if (isStickySessionEnabled() && isNoFallbackEnabled() && sessionCookie.isPresent()) {
            LOG.noFallbackError();
            outboundResponse.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Service connection error, HA failover disabled");
            return true;
        }
        return false;
    }

    default boolean isNonIdempotentAndNonIdempotentFailoverDisabled(HttpUriRequest outboundRequest) {
        return !isFailoverNonIdempotentRequestEnabled() && nonIdempotentRequests.stream().anyMatch(outboundRequest.getMethod()::equalsIgnoreCase);
    }
}
