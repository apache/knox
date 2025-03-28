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
import org.apache.knox.gateway.config.Configure;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.ha.dispatch.i18n.HaDispatchMessages;
import org.apache.knox.gateway.ha.provider.HaProvider;
import org.apache.knox.gateway.ha.provider.HaServiceConfig;
import org.apache.knox.gateway.ha.provider.impl.HaServiceConfigConstants;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.sse.SSEDispatch;

import javax.servlet.FilterConfig;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class SSEHaDispatch extends SSEDispatch {

    protected static final HaDispatchMessages LOG = MessagesFactory.get(HaDispatchMessages.class);
    protected HaProvider haProvider;
    private static final Map<String, String> urlToHashLookup = new HashMap<>();
    private static final Map<String, String> hashToUrlLookup = new HashMap<>();
    private boolean loadBalancingEnabled = HaServiceConfigConstants.DEFAULT_LOAD_BALANCING_ENABLED;
    private boolean stickySessionsEnabled = HaServiceConfigConstants.DEFAULT_STICKY_SESSIONS_ENABLED;
    private String stickySessionCookieName = HaServiceConfigConstants.DEFAULT_STICKY_SESSION_COOKIE_NAME;
    private List<String> disableLoadBalancingForUserAgents = Collections.singletonList(HaServiceConfigConstants.DEFAULT_DISABLE_LB_USER_AGENTS);
    private final boolean isSSLEnabled;

    /**
     * This activeURL is used to track urls when LB is turned off for some clients.
     * The problem we have with selectively turning off LB is that other clients
     * that use LB can change the state from under the current session where LB is
     * turned off.
     * e.g.
     * ODBC Connection established where LB is off. JDBC connection is established
     * next where LB is enabled. This changes the active URL under the existing ODBC
     * connection which will be an issue.
     * This variable keeps track of non-LB'ed url and updated upon failover.
     */
    private final AtomicReference<String> activeURL = new AtomicReference<>();

    public SSEHaDispatch(FilterConfig filterConfig) {
        super(filterConfig);

        GatewayConfig gatewayConfig = (GatewayConfig) filterConfig.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
        isSSLEnabled = gatewayConfig.isSSLEnabled();
    }

    @Override
    public void init() {
        super.init();
        LOG.initializingForResourceRole(getServiceRole());
        if (haProvider != null) {
            HaServiceConfig serviceConfig = haProvider.getHaDescriptor().getServiceConfig(getServiceRole());
            loadBalancingEnabled = serviceConfig.isLoadBalancingEnabled();

            /* enforce dependency */
            stickySessionsEnabled = loadBalancingEnabled && serviceConfig.isStickySessionEnabled();
            if (stickySessionsEnabled) {
                stickySessionCookieName = serviceConfig.getStickySessionCookieName();
            }

            if (StringUtils.isNotBlank(serviceConfig.getStickySessionDisabledUserAgents())) {
                disableLoadBalancingForUserAgents = Arrays.asList(serviceConfig.getStickySessionDisabledUserAgents()
                        .trim()
                        .split("\\s*,\\s*"));
            }
            setupUrlHashLookup();
        }

        /* setup the active URL for non-LB case */
        activeURL.set(haProvider.getActiveURL(getServiceRole()));

        // Suffix the cookie name by the service to make it unique
        // The cookie path is NOT unique since Knox is stripping the service name.
        stickySessionCookieName = stickySessionCookieName + '-' + getServiceRole();
    }

    private void setupUrlHashLookup() {
        for (String url : haProvider.getURLs(getServiceRole())) {
            String urlHash = hash(url);
            urlToHashLookup.put(url, urlHash);
            hashToUrlLookup.put(urlHash, url);
        }
    }

    public HaProvider getHaProvider() {
        return haProvider;
    }

    @Configure
    public void setHaProvider(HaProvider haProvider) {
        this.haProvider = haProvider;
    }

    @Override
    protected void executeRequestWrapper(HttpUriRequest outboundRequest,
                                         HttpServletRequest inboundRequest, HttpServletResponse outboundResponse) {
        final String userAgentFromBrowser = StringUtils.isBlank(inboundRequest.getHeader("User-Agent")) ? "" : inboundRequest.getHeader("User-Agent");
        /* disable loadblancing override */
        boolean userAgentDisabled = false;

        /* disable loadbalancing in case a configured user agent is detected to disable LB */
        if (disableLoadBalancingForUserAgents.stream().anyMatch(userAgentFromBrowser::contains)) {
            userAgentDisabled = true;
            LOG.disableHALoadbalancinguserAgent(userAgentFromBrowser, disableLoadBalancingForUserAgents.toString());
        }

        /* if disable LB is set don't bother setting backend from cookie */
        Optional<URI> backendURI = Optional.empty();
        if (!userAgentDisabled) {
            backendURI = setBackendFromHaCookie(outboundRequest, inboundRequest);
            backendURI.ifPresent(uri -> ((HttpRequestBase) outboundRequest).setURI(uri));
        }

        /**
         * case where loadbalancing is enabled
         * and we have a HTTP request configured not to use LB
         * use the activeURL
         */
        if (loadBalancingEnabled && userAgentDisabled) {
            try {
                ((HttpRequestBase) outboundRequest).setURI(updateHostURL(outboundRequest.getURI(), activeURL.get()));
            } catch (final URISyntaxException e) {
                LOG.errorSettingActiveUrl();
            }
        }

        executeRequest(outboundRequest, inboundRequest, outboundResponse);
        /**
         * 1. Load balance when loadbalancing is enabled and there are no overrides (disableLB)
         * 2. Loadbalance only when sticky session is enabled but cookie not detected
         *    i.e. when loadbalancing is enabled every request that does not have BACKEND cookie
         *    needs to be loadbalanced. If a request has BACKEND coookie and Loadbalance=on then
         *    there should be no loadbalancing.
         */
        if (loadBalancingEnabled && !userAgentDisabled) {
            /* check sticky session enabled */
            if (stickySessionsEnabled) {
                /* loadbalance only when sticky session enabled and no backend url cookie */
                if (!backendURI.isPresent()) {
                    haProvider.makeNextActiveURLAvailable(getServiceRole());
                } else {
                    /* sticky session enabled and backend url cookie is valid no need to loadbalance */
                    /* do nothing */
                }
            } else {
                haProvider.makeNextActiveURLAvailable(getServiceRole());
            }
        }
    }

    @Override
    protected void outboundResponseWrapper(final HttpUriRequest outboundRequest, final HttpServletRequest inboundRequest, final HttpServletResponse outboundResponse) {
        setKnoxHaCookie(outboundRequest, inboundRequest, outboundResponse);
    }

    private Optional<URI> setBackendFromHaCookie(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest) {
        if (loadBalancingEnabled && stickySessionsEnabled && inboundRequest.getCookies() != null) {
            for (Cookie cookie : inboundRequest.getCookies()) {
                if (stickySessionCookieName.equals(cookie.getName())) {
                    String backendURLHash = cookie.getValue();
                    String backendURL = hashToUrlLookup.get(backendURLHash);
                    // Make sure that the url provided is actually a valid backend url
                    if (haProvider.getURLs(getServiceRole()).contains(backendURL)) {
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

    private void setKnoxHaCookie(final HttpUriRequest outboundRequest, final HttpServletRequest inboundRequest,
                                 final HttpServletResponse outboundResponse) {
        if (stickySessionsEnabled) {
            List<Cookie> serviceHaCookies = Collections.emptyList();
            if (inboundRequest.getCookies() != null) {
                serviceHaCookies = Arrays
                        .stream(inboundRequest.getCookies())
                        .filter(cookie -> stickySessionCookieName.equals(cookie.getName()))
                        .collect(Collectors.toList());
            }
            /* if the inbound request has a valid hash then no need to set a different hash */
            if (!serviceHaCookies.isEmpty() && hashToUrlLookup.containsKey(serviceHaCookies.get(0).getValue())) {
                return;
            } else {
                /**
                 * Due to concurrency issues haProvider.getActiveURL() will not return the accurate list
                 * This will cause issues where original request goes to host-1 and cookie is set for host-2 - because
                 * haProvider.getActiveURL() returned host-2. To prevent this from happening we need to make sure
                 * we set cookie for the endpoint that was served and not rely on haProvider.getActiveURL().
                 * let LBing logic take care of rotating urls.
                 **/
                final List<String> urls = haProvider.getURLs(getServiceRole())
                        .stream()
                        .filter(u -> u.contains(outboundRequest.getURI().getHost()))
                        .collect(Collectors.toList());

                final String cookieValue = urlToHashLookup.get(urls.get(0));
                Cookie stickySessionCookie = new Cookie(stickySessionCookieName, cookieValue);
                stickySessionCookie.setPath(inboundRequest.getContextPath());
                stickySessionCookie.setMaxAge(-1);
                stickySessionCookie.setHttpOnly(true);
                stickySessionCookie.setSecure(isSSLEnabled);
                outboundResponse.addCookie(stickySessionCookie);
            }
        }
    }

    private String hash(String url) {
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
    private URI updateHostURL(final URI source, final String host) throws URISyntaxException {
        final URI newUri = new URI(host);
        final URIBuilder uriBuilder = new URIBuilder(source);
        uriBuilder.setScheme(newUri.getScheme());
        uriBuilder.setHost(newUri.getHost());
        uriBuilder.setPort(newUri.getPort());
        return uriBuilder.build();
    }
}
