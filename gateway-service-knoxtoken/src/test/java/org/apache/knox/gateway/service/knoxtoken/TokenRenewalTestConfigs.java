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
package org.apache.knox.gateway.service.knoxtoken;

import javax.security.auth.Subject;

class TokenRenewalTestConfigs {
    //true, if server-side token state management should be enabled in the service; Otherwise, false or null.
    private final Boolean serviceLevelConfig;

    //true, if server-side token state management should be enabled in the gateway-site.xml; Otherwise, false or null.
    private final Boolean gatewayLevelConfig;

    //A comma-delimited list of permitted renewer usernames
    private final String renewers;

    //A comma-delimited list of permitted renewer group names
    private final String groupRenewers;

    //The maximum duration (milliseconds) for a token's lifetime
    private final Long maxTokenLifetime;

    //The security subject who's making the request
    private final Subject caller;

    private TokenRenewalTestConfigs(Builder builder) {
        this.serviceLevelConfig = builder.serviceLevelConfig;
        this.gatewayLevelConfig = builder.gatewayLevelConfig;
        this.renewers = builder.renewers;
        this.groupRenewers = builder.groupRenewers;
        this.maxTokenLifetime = builder.maxTokenLifetime;
        this.caller = builder.caller;
    }

    static Builder builder() {
        return new Builder();
    }

    Boolean getServiceLevelConfig() {
        return serviceLevelConfig;
    }

    Boolean getGatewayLevelConfig() {
        return gatewayLevelConfig;
    }

    boolean isTokenStateServerManaged() {
        return Boolean.TRUE.equals(serviceLevelConfig) || Boolean.TRUE.equals(gatewayLevelConfig);
    }

    String getRenewers() {
        return renewers;
    }

    String getGroupRenewers() {
        return groupRenewers;
    }

    Long getMaxTokenLifetime() {
        return maxTokenLifetime;
    }

    Subject getCaller() {
        return caller;
    }

    static class Builder {
        private Boolean serviceLevelConfig;
        private Boolean gatewayLevelConfig;
        private String renewers;
        private String groupRenewers;
        private Long maxTokenLifetime;
        private Subject caller;

        Builder serviceLevelConfig(boolean serviceLevelConfig) {
            this.serviceLevelConfig = serviceLevelConfig;
            return this;
        }

        Builder gatewayLevelConfig(boolean gatewayLevelConfig) {
            this.gatewayLevelConfig = gatewayLevelConfig;
            return this;
        }

        Builder renewers(String renewers) {
            this.renewers = renewers;
            return this;
        }

        Builder groupRenewers(String groupRenewers) {
            this.groupRenewers = groupRenewers;
            return this;
        }

        Builder maxTokenLifetime(Long maxTokenLifetime) {
            this.maxTokenLifetime = maxTokenLifetime;
            return this;
        }

        Builder caller(Subject caller) {
            this.caller = caller;
            return this;
        }

        @SuppressWarnings("PMD.AccessorClassGeneration")
        TokenRenewalTestConfigs build() {
            return new TokenRenewalTestConfigs(this);
        }
    }
}
