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

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.service.definition.ServiceDefinition;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.registry.ServiceDefEntry;
import org.apache.knox.gateway.services.registry.ServiceDefinitionRegistry;
import org.apache.knox.gateway.services.registry.ServiceRegistry;
import org.apache.knox.gateway.util.ServiceDefinitionsLoader;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import javax.websocket.ClientEndpointConfig;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Websocket handler that will handle websocket connection request. This class
 * is responsible for creating a proxy socket for inbound and outbound
 * connections. This is also where the http to websocket handoff happens.
 *
 * @since 0.10
 */
public class GatewayWebsocketHandler extends WebSocketHandler
    implements WebSocketCreator {

  private static final WebsocketLogMessages LOG = MessagesFactory
      .get(WebsocketLogMessages.class);

  public static final String WEBSOCKET_PROTOCOL_STRING = "ws://";

  public static final String SECURE_WEBSOCKET_PROTOCOL_STRING = "wss://";

  static final String REGEX_SPLIT_CONTEXT = "^((?:[^/]*/){2}[^/]*)";

  final static String REGEX_SPLIT_SERVICE_PATH = "^((?:[^/]*/){3}[^/]*)";

  private static final int POOL_SIZE = 10;

  /**
   * Manage the threads that are spawned
   * @since 0.13
   */
  private final ExecutorService pool;

  final GatewayConfig config;
  final GatewayServices services;

  /**
   * Create an instance
   *
   * @param config
   * @param services
   */
  public GatewayWebsocketHandler(final GatewayConfig config,
      final GatewayServices services) {
    super();

    this.config = config;
    this.services = services;
    pool = Executors.newFixedThreadPool(POOL_SIZE);

  }

  /*
   * (non-Javadoc)
   *
   * @see
   * org.eclipse.jetty.websocket.server.WebSocketHandler#configure(org.eclipse.
   * jetty.websocket.servlet.WebSocketServletFactory)
   */
  @Override
  public void configure(final WebSocketServletFactory factory) {
    factory.setCreator(this);
    factory.getPolicy()
        .setMaxTextMessageSize(config.getWebsocketMaxTextMessageSize());
    factory.getPolicy()
        .setMaxBinaryMessageSize(config.getWebsocketMaxBinaryMessageSize());

    factory.getPolicy().setMaxBinaryMessageBufferSize(
        config.getWebsocketMaxBinaryMessageBufferSize());
    factory.getPolicy().setMaxTextMessageBufferSize(
        config.getWebsocketMaxTextMessageBufferSize());

    factory.getPolicy()
        .setInputBufferSize(config.getWebsocketInputBufferSize());

    factory.getPolicy()
        .setAsyncWriteTimeout(config.getWebsocketAsyncWriteTimeout());
    factory.getPolicy().setIdleTimeout(config.getWebsocketIdleTimeout());

  }

  /*
   * (non-Javadoc)
   *
   * @see
   * org.eclipse.jetty.websocket.servlet.WebSocketCreator#createWebSocket(org.
   * eclipse.jetty.websocket.servlet.ServletUpgradeRequest,
   * org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse)
   */
  @Override
  public Object createWebSocket(ServletUpgradeRequest req,
      ServletUpgradeResponse resp) {

    try {
      final URI requestURI = req.getRequestURI();
      final String path = requestURI.getPath();

      /* URL used to connect to websocket backend */
      final String backendURL = getMatchedBackendURL(path);

      /* Upgrade happens here */
      return new ProxyWebSocketAdapter(URI.create(backendURL), pool, getClientEndpointConfig(req));
    } catch (final Exception e) {
      LOG.failedCreatingWebSocket(e);
      throw e;
    }
  }

  /**
   * Returns a {@link ClientEndpointConfig} config that contains the headers
   * to be passed to the backend.
   * @since 0.14.0
   * @param req
   * @return
   */
  private ClientEndpointConfig getClientEndpointConfig(final ServletUpgradeRequest req) {

    return ClientEndpointConfig.Builder.create().configurator( new ClientEndpointConfig.Configurator() {

       @Override
       public void beforeRequest(final Map<String, List<String>> headers) {

         /* Add request headers */
         req.getHeaders().forEach(headers::putIfAbsent);

       }
    }).build();
  }

  /**
   * This method looks at the context path and returns the backend websocket
   * url. If websocket url is found it is used as is, or we default to
   * ws://{host}:{port} which might or might not be right.
   *
   * @param
   * @return Websocket backend url
   */
  private synchronized String getMatchedBackendURL(final String path) {

    final ServiceRegistry serviceRegistryService = services
        .getService(GatewayServices.SERVICE_REGISTRY_SERVICE);

    final ServiceDefinitionRegistry serviceDefinitionService = services
        .getService(GatewayServices.SERVICE_DEFINITION_REGISTRY);

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

    final File servicesDir = new File(config.getGatewayServicesDir());

    final Set<ServiceDefinition> serviceDefs = ServiceDefinitionsLoader
        .getServiceDefinitions(servicesDir);

    /* URL used to connect to websocket backend */
    String backendURL = urlFromServiceDefinition(serviceDefs,
        serviceRegistryService, entry, path);

    StringBuffer backend = new StringBuffer();
    try {

      /* if we do not find websocket URL we default to HTTP */
      if (!StringUtils.containsAny(backendURL, WEBSOCKET_PROTOCOL_STRING, SECURE_WEBSOCKET_PROTOCOL_STRING)) {
        URL serviceUrl = new URL(backendURL);

        /* Use http host:port if ws url not configured */
        final String protocol = (serviceUrl.getProtocol().equals("ws")
                || serviceUrl.getProtocol().equals("wss")) ? serviceUrl.getProtocol()
                : "ws";
        backend.append(protocol).append("://");
        backend.append(serviceUrl.getHost()).append(":");
        backend.append(serviceUrl.getPort()).append("/");
        backend.append(serviceUrl.getPath());
      }
      else {
        URI serviceUri = new URI(backendURL);
        backend.append(serviceUri);
        /* Avoid Zeppelin Regression - as this would require ambari changes and break current knox websocket use case*/
        if (!StringUtils.endsWith(backend.toString(), "/ws") && pathService.length > 0 && pathService[1] != null) {
          backend.append(pathService[1]);
        }
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
      final Set<ServiceDefinition> serviceDefs,
      final ServiceRegistry serviceRegistry, final ServiceDefEntry entry,
      final String path) {

    final String[] contexts = path.split("/");

    final String serviceURL = serviceRegistry.lookupServiceURL(contexts[2],
        entry.getName().toUpperCase(Locale.ROOT));

    /*
     * we have a match, if ws:// is present it is returned else http:// is
     * returned
     */
    return serviceURL;

  }

}
