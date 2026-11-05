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
package org.apache.knox.gateway.service.knoxidf;

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants;

import jakarta.servlet.ServletContext;

/**
 * Resolves the endpoint URLs advertised by the discovery documents ({@link DiscoveryResource} and
 * the RFC 8414 {@link OAuthServerMetadataResource}). Each endpoint is advertised on the topology
 * serving the request unless a KNOXIDF parameter moves it to another one:
 * <ul>
 *   <li>{@code token.exchange.topology.name}: token and userinfo endpoints</li>
 *   <li>{@code authorization.topology.name}: authorization endpoint</li>
 *   <li>{@code client.registration.topology.name}: registration endpoint</li>
 * </ul>
 * An unset or blank parameter keeps the endpoint on the current topology.
 */
final class DiscoveryEndpointResolver {
    static final DiscoveryEndpointResolver NONE = new DiscoveryEndpointResolver(null, null, null, null);

    private final String currentTopology;
    private final String tokenExchangeTopology;
    private final String authorizationTopology;
    private final String clientRegistrationTopology;

    DiscoveryEndpointResolver(String currentTopology, String tokenExchangeTopology,
                              String authorizationTopology, String clientRegistrationTopology) {
        this.currentTopology = currentTopology;
        this.tokenExchangeTopology = StringUtils.trimToNull(tokenExchangeTopology);
        this.authorizationTopology = StringUtils.trimToNull(authorizationTopology);
        this.clientRegistrationTopology = StringUtils.trimToNull(clientRegistrationTopology);
    }

    static DiscoveryEndpointResolver from(ServletContext servletContext) {
        return new DiscoveryEndpointResolver(
            (String) servletContext.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE),
            servletContext.getInitParameter(KnoxIDFConstants.TOKEN_EXCHANGE_TOPOLOGY_NAME),
            servletContext.getInitParameter(KnoxIDFConstants.AUTHORIZATION_TOPOLOGY_NAME),
            servletContext.getInitParameter(KnoxIDFConstants.CLIENT_REGISTRATION_TOPOLOGY_NAME));
    }

    String authorizationEndpoint(String baseUrl) {
        return onTopology(baseUrl, authorizationTopology) + AuthorizeResource.RESOURCE_PATH;
    }

    String tokenEndpoint(String baseUrl) {
        return onTopology(baseUrl, tokenExchangeTopology) + TokenResource.RESOURCE_PATH;
    }

    // userinfo is served next to the token endpoint, so token.exchange.topology.name moves both.
    String userInfoEndpoint(String baseUrl) {
        return onTopology(baseUrl, tokenExchangeTopology) + UserInfoResource.RESOURCE_PATH;
    }

    String registrationEndpoint(String baseUrl) {
        return onTopology(baseUrl, clientRegistrationTopology) + RegistrationResource.RESOURCE_PATH + "/register";
    }

    /**
     * Returns {@code baseUrl} with the current topology's path segment replaced by {@code target},
     * or {@code baseUrl} unchanged when {@code target} is {@code null}. Only the base URL's
     * {@code /<topology>/} segment is replaced, so a topology name that also occurs in the host or
     * in a resource path (e.g. a topology named {@code knoxidf}) is not rewritten there.
     */
    private String onTopology(String baseUrl, String target) {
        if (target == null || currentTopology == null) {
            return baseUrl;
        }
        final String segment = "/" + currentTopology + "/";
        final int at = baseUrl.lastIndexOf(segment);
        if (at < 0) {
            return baseUrl;
        }
        return baseUrl.substring(0, at) + "/" + target + "/" + baseUrl.substring(at + segment.length());
    }
}
