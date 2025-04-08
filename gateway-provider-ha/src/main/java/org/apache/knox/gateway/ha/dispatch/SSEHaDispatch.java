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

import org.apache.http.client.methods.HttpUriRequest;
import org.apache.knox.gateway.config.Configure;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.ha.dispatch.i18n.HaDispatchMessages;
import org.apache.knox.gateway.ha.provider.HaProvider;
import org.apache.knox.gateway.ha.provider.HaServiceConfig;
import org.apache.knox.gateway.ha.provider.impl.HaServiceConfigConstants;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.sse.SSEDispatch;

import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class SSEHaDispatch extends SSEDispatch implements LBHaDispatch {

    protected static final HaDispatchMessages LOG = MessagesFactory.get(HaDispatchMessages.class);
    protected HaProvider haProvider;
    private boolean loadBalancingEnabled = HaServiceConfigConstants.DEFAULT_LOAD_BALANCING_ENABLED;
    private boolean stickySessionsEnabled = HaServiceConfigConstants.DEFAULT_STICKY_SESSIONS_ENABLED;
    private String stickySessionCookieName = HaServiceConfigConstants.DEFAULT_STICKY_SESSION_COOKIE_NAME;
    private List<String> disableLoadBalancingForUserAgents = Collections.singletonList(HaServiceConfigConstants.DEFAULT_DISABLE_LB_USER_AGENTS);
    private final boolean sslEnabled;

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
        sslEnabled = gatewayConfig.isSSLEnabled();
    }

    @Override
    public void init() {
        super.init();
        LOG.initializingForResourceRole(getServiceRole());
        if (haProvider != null) {
            HaServiceConfig serviceConfig = haProvider.getHaDescriptor().getServiceConfig(getServiceRole());
            this.initializeLBHaDispatch(serviceConfig);
        }
    }

    @Override
    public HaProvider getHaProvider() {
        return haProvider;
    }

    @Override
    public void setLoadBalancingEnabled(boolean enabled) {
        this.loadBalancingEnabled = enabled;
    }

    @Configure
    public void setHaProvider(HaProvider haProvider) {
        this.haProvider = haProvider;
    }

    @Override
    public boolean isStickySessionEnabled() {
        return stickySessionsEnabled;
    }

    @Override
    public void setStickySessionsEnabled(boolean enabled) {
        this.stickySessionsEnabled = enabled;
    }

    @Override
    public String getStickySessionCookieName() {
        return stickySessionCookieName;
    }

    @Override
    public void setStickySessionCookieName(String stickySessionCookieName) {
        this.stickySessionCookieName = stickySessionCookieName;
    }

    @Override
    public boolean isLoadBalancingEnabled() {
        return loadBalancingEnabled;
    }

    @Override
    public List<String> getDisableLoadBalancingForUserAgents() {
        return disableLoadBalancingForUserAgents;
    }

    @Override
    public void setDisableLoadBalancingForUserAgents(List<String> disableLoadBalancingForUserAgents) {
        this.disableLoadBalancingForUserAgents = disableLoadBalancingForUserAgents;
    }

    @Override
    public AtomicReference<String> getActiveURL() {
        return activeURL;
    }

    @Override
    public void setActiveURL(String url) {
        activeURL.set(url);
    }


    @Override
    protected void executeRequestWrapper(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse) {
        boolean userAgentDisabled = isUserAgentDisabled(inboundRequest);
        Optional<URI> backendURI = setBackendUri(outboundRequest, inboundRequest, userAgentDisabled);
        executeRequest(outboundRequest, inboundRequest, outboundResponse);
        shiftActiveURL(userAgentDisabled, backendURI);
    }

    @Override
    protected void outboundResponseWrapper(final HttpUriRequest outboundRequest, final HttpServletRequest inboundRequest, final HttpServletResponse outboundResponse) {
        setKnoxHaCookie(outboundRequest, inboundRequest, outboundResponse, sslEnabled);
    }
}
