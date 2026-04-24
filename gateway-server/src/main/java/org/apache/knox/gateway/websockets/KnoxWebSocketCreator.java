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
package org.apache.knox.gateway.websockets;

import jakarta.websocket.ClientEndpointConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.registry.ServiceDefEntry;
import org.apache.knox.gateway.services.registry.ServiceDefinitionRegistry;
import org.apache.knox.gateway.services.registry.ServiceRegistry;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.webshell.WebshellWebSocketAdapter;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.server.ServerUpgradeRequest;
import org.eclipse.jetty.websocket.server.ServerUpgradeResponse;
import org.eclipse.jetty.websocket.server.WebSocketCreator;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class KnoxWebSocketCreator implements WebSocketCreator {
    private static final WebsocketLogMessages LOG = MessagesFactory
    .get(WebsocketLogMessages.class);

    public static final String WEBSOCKET_PROTOCOL_STRING = "ws://";

    public static final String SECURE_WEBSOCKET_PROTOCOL_STRING = "wss://";

    static final String REGEX_SPLIT_CONTEXT = "^((?:[^/]*/){2}[^/]*)";

    static final String REGEX_SPLIT_SERVICE_PATH = "^((?:[^/]*/){3}[^/]*)";

    static final String REGEX_WEBSHELL_REQUEST_PATH =
    "^(" + SECURE_WEBSOCKET_PROTOCOL_STRING+"|"+WEBSOCKET_PROTOCOL_STRING + ")[^/]+/[^/]+/webshell$";

    private static final int POOL_SIZE = 10;
    private final AtomicInteger concurrentWebshells;

    /**
     * Manage the threads that are spawned
     * @since 0.13
     */
    private final ExecutorService pool;

    final GatewayConfig config;
    final GatewayServices services;

    public KnoxWebSocketCreator(GatewayConfig config, GatewayServices services) {
        this.config = config;
        this.services = services;
        this.pool = Executors.newFixedThreadPool(POOL_SIZE);
        this.concurrentWebshells = new AtomicInteger(0);
    }

    @Override
    public Object createWebSocket(ServerUpgradeRequest req, ServerUpgradeResponse resp, Callback callback) {
        try {
            // 1. Get the raw HTTP URI from the Jetty 12 Request
            HttpURI httpURI = req.getHttpURI();

            // 2. Translate the scheme to match Jetty 9's behavior (http -> ws, https -> wss)
            String wsScheme = "https".equalsIgnoreCase(httpURI.getScheme()) ? "wss" : "ws";

            // 3. Reconstruct the java.net.URI for Knox's internal routing methods
            final URI requestURI = HttpURI.build(httpURI).scheme(wsScheme).toURI();

            // Now Knox's regex will work
            if (isWebshellRequest(requestURI)) {
                return handleWebshellRequest(req); // Note: Update handleWebshellRequest to accept ServerUpgradeRequest
            }

            final String backendURL = getMatchedBackendURL(requestURI);
            LOG.debugLog("Generated backend URL for websocket connection: " + backendURL);

            final ClientEndpointConfig clientConfig = getClientEndpointConfig(req, backendURL);
            clientConfig.getUserProperties().put("org.apache.knox.gateway.websockets.truststore", getTruststore());

            return new ProxyWebSocketAdapter(URI.create(backendURL), pool, clientConfig, config);

        } catch (final Exception e) {
            LOG.failedCreatingWebSocket(e);
            // In Jetty 12, completing the callback with failure tells the server to reject the upgrade
            callback.failed(e);
            return null;
        }
    }

    private boolean isWebshellRequest(URI requestURI){
        return requestURI.toString().matches(REGEX_WEBSHELL_REQUEST_PATH);
    }

    private WebshellWebSocketAdapter handleWebshellRequest(ServerUpgradeRequest req){
        if (config.isWebShellEnabled()){
            if (concurrentWebshells.get() >= config.getMaximumConcurrentWebshells()){
                throw new RuntimeException("Number of allowed concurrent Web Shell sessions exceeded");
            }
            JWTValidator jwtValidator = JWTValidatorFactory.create(req, services, config);
            if (jwtValidator.validate()) {
                return new WebshellWebSocketAdapter(pool, config, jwtValidator, concurrentWebshells);
            }
            throw new RuntimeException("No valid token found for Web Shell connection");
        }
        throw new RuntimeException("Web Shell not enabled");
    }

    private KeyStore getTruststore() throws KeystoreServiceException {
        final KeystoreService ks = this.services
        .getService(ServiceType.KEYSTORE_SERVICE);
        KeyStore trustKeystore = null;
        trustKeystore = ks.getTruststoreForHttpClient();
        if (trustKeystore == null) {
            trustKeystore = ks.getKeystoreForGateway();
        }
        return trustKeystore;
    }

    /**
     * Returns a {@link ClientEndpointConfig} config that contains the headers
     * to be passed to the backend.
     * @since 0.14.0
     */
    private ClientEndpointConfig getClientEndpointConfig(final ServerUpgradeRequest req, final String backendURL) {

        return ClientEndpointConfig.Builder.create()
        .configurator(new ClientEndpointConfig.Configurator() {

            @Override
            public void beforeRequest(final Map<String, List<String>> headers) {

                // 1. Safely iterate over Jetty 12 HttpFields and copy them to the Jakarta map
                for (HttpField field : req.getHeaders()) {
                    headers.computeIfAbsent(field.getName(), k -> new ArrayList<>())
                    .add(field.getValue());
                }

                // 2. Properly construct and override the Host header
                try {
                    final URI backendURI = new URI(backendURL);

                    // Handle implicit ports (where getPort() returns -1) to prevent "Host: example.com:-1"
                    int port = backendURI.getPort();
                    String hostValue = backendURI.getHost() + (port != -1 ? ":" + port : "");

                    headers.put("Host", Collections.singletonList(hostValue));

                } catch (final URISyntaxException e) {
                    LOG.onError(String.format(Locale.ROOT,
                    "Error getting backend url, this could cause 'Host does not match SNI' exception. Cause: %s",
                    e.toString()));
                }
            }
        }).build();
    }

    /**
     * This method looks at the context path and returns the backend websocket
     * url. If websocket url is found it is used as is, or we default to
     * ws://{host}:{port} which might or might not be right.
     * @param requestURI url to match
     * @return Websocket backend url
     */
    protected synchronized String getMatchedBackendURL(final URI requestURI) {
        final String path = requestURI.getRawPath();
        final String query = requestURI.getRawQuery();

        final ServiceRegistry serviceRegistryService = services
        .getService(ServiceType.SERVICE_REGISTRY_SERVICE);

        final ServiceDefinitionRegistry serviceDefinitionService = services
        .getService(ServiceType.SERVICE_DEFINITION_REGISTRY);

        /* Filter out the /cluster/topology to get the context we want */
        String[] pathInfo = path.split(REGEX_SPLIT_CONTEXT);

        final ServiceDefEntry entry = serviceDefinitionService
        .getMatchingService(pathInfo[1]);

        if (entry == null) {
            throw new RuntimeException(
            String.format(Locale.ROOT, "Cannot find service for the given path: %s", path));
        }

        /* Filter out /cluster/topology/service to get endpoint */
        String[] pathService = path.split(REGEX_SPLIT_SERVICE_PATH);

        /* URL used to connect to websocket backend */
        String backendURL = urlFromServiceDefinition(serviceRegistryService, entry, path);
        LOG.debugLog("Url obtained from services definition: " + backendURL);

        StringBuilder backend = new StringBuilder();
        try {
            if (StringUtils.containsAny(backendURL, WEBSOCKET_PROTOCOL_STRING, SECURE_WEBSOCKET_PROTOCOL_STRING)) {
                LOG.debugLog("ws or wss protocol found in service url");
                URI serviceUri = new URI(backendURL);
                backend.append(serviceUri);
                String pathSuffix = generateUrlSuffix(backend.toString(), pathService);
                backend.append(pathSuffix);
            } else if (StringUtils.containsAny(requestURI.toString(), WEBSOCKET_PROTOCOL_STRING, SECURE_WEBSOCKET_PROTOCOL_STRING)) {
                LOG.debugLog("ws or wss protocol found in request url");
                URL serviceUrl = new URL(backendURL);
                final String protocol = (serviceUrl.getProtocol().equals("https")) ? "wss" : "ws";
                backend.append(protocol).append("://");
                backend.append(serviceUrl.getHost()).append(':');
                backend.append(serviceUrl.getPort()).append('/');
                backend.append(serviceUrl.getPath());
                String pathSuffix = generateUrlSuffix(backend.toString(), pathService);
                backend.append(pathSuffix);
            } else {
                LOG.debugLog("ws or wss protocol not found in service url or request url");
                URL serviceUrl = new URL(backendURL);

                /* Use http host:port if ws url not configured */
                final String protocol = (serviceUrl.getProtocol().equals("ws")
                || serviceUrl.getProtocol().equals("wss")) ? serviceUrl.getProtocol()
                : "ws";
                backend.append(protocol).append("://");
                backend.append(serviceUrl.getHost()).append(':');
                backend.append(serviceUrl.getPort()).append('/');
                backend.append(serviceUrl.getPath());
            }
            /* in case we have query params */
            if(!StringUtils.isBlank(query)) {
                backend.append('?').append(query);
            }
            backendURL = backend.toString();

        } catch (MalformedURLException e){
            LOG.badUrlError(e);
            throw new RuntimeException(e.toString());
        } catch (Exception  e1) {
            LOG.failedCreatingWebSocket(e1);
            throw new RuntimeException(e1.toString());
        }

        return backendURL;
    }

    private static String urlFromServiceDefinition(
    final ServiceRegistry serviceRegistry, final ServiceDefEntry entry,
    final String path) {

        final String[] contexts = path.split("/");

        /*
         * we have a match, if ws:// is present it is returned else http:// is
         * returned
         */
        return serviceRegistry.lookupServiceURL(contexts[2],
        entry.getName().toUpperCase(Locale.ROOT));
    }

    private String generateUrlSuffix(String backendPart, String[] pathService) {
        /* Avoid Zeppelin Regression - as this would require ambari changes and break current knox websocket use case*/
        if (!StringUtils.endsWith(backendPart, "/ws") && pathService.length > 0
        &&  pathService[1] != null) {
            String newPathSuffix = pathService[1];
            if ((backendPart.endsWith("/")) && (pathService[1].startsWith("/"))) {
                newPathSuffix = pathService[1].substring(1);
            }
            return newPathSuffix;
        }
        return "";
    }
}
