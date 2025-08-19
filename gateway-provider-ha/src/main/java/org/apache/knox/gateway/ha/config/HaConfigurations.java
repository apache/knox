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

import java.util.List;

public interface HaConfigurations {

    HaProvider getHaProvider();

    void setLoadBalancingEnabled(boolean enabled);

    void setHaProvider(HaProvider haProvider);

    boolean isStickySessionEnabled();

    void setStickySessionsEnabled(boolean enabled);

    String getStickySessionCookieName();

    void setStickySessionCookieName(String stickySessionCookieName);

    boolean isLoadBalancingEnabled();

    List<String> getDisableLoadBalancingForUserAgents();

    void setDisableLoadBalancingForUserAgents(List<String> disableLoadBalancingForUserAgents);

    int getMaxFailoverAttempts();

    void setMaxFailoverAttempts(int maxFailoverAttempts);

    int getFailoverSleep();

    void setFailoverSleep(int failoverSleep);

    void setFailoverNonIdempotentRequestEnabled(boolean enabled);

    boolean isFailoverNonIdempotentRequestEnabled();

    void setNoFallbackEnabled(boolean enabled);

    boolean isNoFallbackEnabled();
}
