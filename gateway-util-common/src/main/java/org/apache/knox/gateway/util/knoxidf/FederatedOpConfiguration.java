/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.util.knoxidf;

import javax.servlet.ServletContext;

public class FederatedOpConfiguration implements Comparable<FederatedOpConfiguration> {
    private final boolean enabled;
    private final String name;
    private final String clientId;
    private final String clientSecret;
    private final String tokenEndpoint;
    private final String authorizeEndpoint;
    private final String userInfoEndpoint;
    private final String discoveryEndpoint;
    private final String authorizeCallback;

    public FederatedOpConfiguration(final ServletContext servletContext, final String opName) {
        this.name = opName;
        final String prefix = KnoxIDFConstants.FEDERATED_OP_CONFIG_PREFIX + (opName != null ? opName + "." : "");
        this.enabled = Boolean.parseBoolean(servletContext.getInitParameter(prefix + "enabled"));
        this.clientId = servletContext.getInitParameter(prefix + "clientId");
        this.clientSecret = servletContext.getInitParameter(prefix + "clientSecret");
        this.tokenEndpoint = servletContext.getInitParameter(prefix + "token.endpoint");
        this.authorizeEndpoint = servletContext.getInitParameter(prefix + "authorize.endpoint");
        this.authorizeCallback = servletContext.getInitParameter(prefix + "authorize.callback");
        this.userInfoEndpoint = servletContext.getInitParameter(prefix + "userinfo.endpoint");
        this.discoveryEndpoint = servletContext.getInitParameter(prefix + "discovery.endpoint");
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    String getAuthorizeEndpoint() {
        return authorizeEndpoint;
    }

    public String getAuthorizeCallback() {
        return authorizeCallback;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public String getUserInfoEndpoint() {
        return userInfoEndpoint;
    }

    public String getDiscoveryEndpoint() {
        return discoveryEndpoint;
    }

    @Override
    public int compareTo(FederatedOpConfiguration other) {
        return this.name.compareTo(other.name);
    }
}
