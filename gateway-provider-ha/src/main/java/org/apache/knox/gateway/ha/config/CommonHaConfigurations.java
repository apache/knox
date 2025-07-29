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
package org.apache.knox.gateway.ha.config;

import org.apache.knox.gateway.ha.provider.HaProvider;
import org.apache.knox.gateway.ha.provider.impl.HaServiceConfigConstants;

import java.util.Collections;
import java.util.List;

public class CommonHaConfigurations implements HaConfigurations {

    private HaProvider haProvider;
    private boolean loadBalancingEnabled = HaServiceConfigConstants.DEFAULT_LOAD_BALANCING_ENABLED;
    private boolean stickySessionsEnabled = HaServiceConfigConstants.DEFAULT_STICKY_SESSIONS_ENABLED;
    private String stickySessionCookieName = HaServiceConfigConstants.DEFAULT_STICKY_SESSION_COOKIE_NAME;
    private List<String> disableLoadBalancingForUserAgents = Collections.singletonList(HaServiceConfigConstants.DEFAULT_DISABLE_LB_USER_AGENTS);
    private int maxFailoverAttempts = HaServiceConfigConstants.DEFAULT_MAX_FAILOVER_ATTEMPTS;
    private int failoverSleep = HaServiceConfigConstants.DEFAULT_FAILOVER_SLEEP;
    private boolean failoverNonIdempotentRequestEnabled = HaServiceConfigConstants.DEFAULT_FAILOVER_NON_IDEMPOTENT;
    private boolean noFallbackEnabled = HaServiceConfigConstants.DEFAULT_NO_FALLBACK_ENABLED;

    @Override
    public HaProvider getHaProvider() {
        return haProvider;
    }

    @Override
    public void setLoadBalancingEnabled(boolean enabled) {
        this.loadBalancingEnabled = enabled;
    }

    @Override
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
    public int getMaxFailoverAttempts() {
        return maxFailoverAttempts;
    }

    @Override
    public void setMaxFailoverAttempts(int maxFailoverAttempts) {
        this.maxFailoverAttempts = maxFailoverAttempts;
    }

    @Override
    public int getFailoverSleep() {
        return failoverSleep;
    }

    @Override
    public void setFailoverSleep(int failoverSleep) {
        this.failoverSleep = failoverSleep;
    }

    @Override
    public void setFailoverNonIdempotentRequestEnabled(boolean enabled) {
        this.failoverNonIdempotentRequestEnabled = enabled;
    }

    @Override
    public boolean isFailoverNonIdempotentRequestEnabled() {
        return failoverNonIdempotentRequestEnabled;
    }

    @Override
    public void setNoFallbackEnabled(boolean enabled) {
        this.noFallbackEnabled = enabled;
    }

    @Override
    public boolean isNoFallbackEnabled() {
        return noFallbackEnabled;
    }
}
