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
package org.apache.knox.gateway.service.restcatalog;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.security.CommonTokenConstants;
import org.apache.knox.gateway.service.restcatalog.i18n.TokenMetadataHandlerMessages;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;

public class TokenMetadataHeaderHandler {
    static final String TOKEN_METADATA_PARAM = "token-metadata-headers";
    static final String METADATA_HEADER_PREFIX_PARAM = "metadata-header-prefix";

    static final String DEFAULT_HEADER_PREFIX = "X-Knox-Meta-";

    private static final TokenMetadataHandlerMessages LOG = MessagesFactory.get(TokenMetadataHandlerMessages.class);

    private static final String INVALID_CLIENT_SECRET = "Error while parsing the received client secret";

    private final TokenStateService tss;

    private final String headerPrefix;

    private final Set<String> metadataHeaderConfig = new HashSet<>();

    private final Cache<String, TokenMetadata> metadataCache = Caffeine.newBuilder().maximumSize(100).build();

    TokenMetadataHeaderHandler(final FilterConfig filterConfig) {
        ServletContext sc = filterConfig.getServletContext();
        GatewayServices gatewayServices = (GatewayServices) sc.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
        tss = gatewayServices.getService(ServiceType.TOKEN_STATE_SERVICE);

        metadataHeaderConfig.addAll(getMetadataHeaderConfig(filterConfig));

        headerPrefix = getMetadataHeaderPrefixConfig(filterConfig);
    }

    void applyHeadersToRequest(final HttpServletRequest inboundRequest,
                                      final HttpUriRequest outboundRequest) {
        final String clientId = getClientID(inboundRequest);
        if (clientId != null) {
            Map<String, String> metadata = getMetadata(tss, clientId);
            for (String key : metadataHeaderConfig) {
                if (metadata.containsKey(key)) {
                    outboundRequest.setHeader(headerPrefix + key, metadata.get(key));
                }
            }
        }
    }

    private Set<String> getMetadataHeaderConfig(final FilterConfig filterConfig) {
        Set<String> metadataForHeaders = new HashSet<>();

        // Add the default metadata element to be included in the outbound request headers
        metadataForHeaders.add("userName");

        // Parse the configured token metadata elements which should be included as outbound request headers
        String tokenMetadataHeadersConfig = filterConfig.getInitParameter(TOKEN_METADATA_PARAM);
        String[] tokenMetadataHeaderNames = tokenMetadataHeadersConfig.split(",");
        for (String metadataName : tokenMetadataHeaderNames) {
            metadataForHeaders.add(metadataName.trim());
        }
        return metadataForHeaders;
    }

    private String getMetadataHeaderPrefixConfig(final FilterConfig filterConfig) {
        String value = DEFAULT_HEADER_PREFIX;
        String configured = filterConfig.getInitParameter(METADATA_HEADER_PREFIX_PARAM);
        if (configured != null) {
            configured = configured.trim();
            if (!configured.isEmpty()) {
                value = configured;
            }
        }
        return value;
    }

    /**
     * Get the clientId from the client credentials in the request body content.
     *
     * @param request The ServletRequest.
     *
     * @return The clientId from the request.
     */
    String getClientID(ServletRequest request) {
        String clientId = null;

        String clientSecret = getClientSecretFromRequestBody(request);
        if (clientSecret != null) {
            try {
                final String[] decodedSecret = decodeBase64(clientSecret).split("::");
                clientId = decodeBase64(decodedSecret[0]);
            } catch (Exception e) {
                LOG.invalidClientSecret(e);
                throw new SecurityException(INVALID_CLIENT_SECRET, e);
            }
        }

        return clientId;
    }

    /**
     * Get the client secret from the request body.
     *
     * @param request The ServletRequest with the body content.
     *
     * @return The client secret.
     */
    private String getClientSecretFromRequestBody(ServletRequest request) {
        final String grantType = request.getParameter(CommonTokenConstants.GRANT_TYPE);
        String clientSecret = null;
        if (CommonTokenConstants.CLIENT_CREDENTIALS.equals(grantType)) {
            clientSecret = request.getParameter(CommonTokenConstants.CLIENT_SECRET);
        }
        return clientSecret;
    }

    private String decodeBase64(final String encoded) {
        return new String(Base64.getDecoder().decode(encoded.getBytes(UTF_8)), UTF_8);
    }

    /**
     * Get the token metadata associated with the specified clientId.
     *
     * @param clientId The clientId for which the token metadata is requested.
     *
     * @return A map of the associated token metadata.
     */
    private Map<String, String> getMetadata(final TokenStateService tss, final String clientId) {
        Map<String, String> metadata = Collections.emptyMap();

        // Get the associated token metadata
        TokenMetadata tm = metadataCache.get(clientId, k -> {
            try {
                return tss.getTokenMetadata(clientId);
            } catch (UnknownTokenException e) {
                LOG.invalidClientId(clientId, e);
                return null;
            }
        });
        if (tm != null) {
            metadata = tm.getMetadataMap();
        } else {
            LOG.noMetadataForClientId(clientId);
        }

        return metadata;
    }

}
