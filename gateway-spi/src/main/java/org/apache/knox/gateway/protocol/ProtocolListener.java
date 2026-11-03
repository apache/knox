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
package org.apache.knox.gateway.protocol;

import java.util.Collections;
import java.util.List;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;

/**
 * A network listener for a protocol the servlet pipeline cannot carry, running
 * alongside Jetty on its own port and managed by the {@code GatewayServer}
 * lifecycle.
 * <p>
 * Implementations are discovered with {@link java.util.ServiceLoader}, so the
 * gateway server does not depend on them at compile time and their transport
 * libraries stay off the gateway classpath unless the providing module is
 * deployed. This is what lets the Spark Connect listener bring gRPC and a shaded
 * Netty along without either reaching the servlet stack.
 * <p>
 * The WebSocket handler solves the same class of problem, but a WebSocket
 * upgrade can share Jetty's HTTP/1.1 connector; protocols that cannot (gRPC
 * needs HTTP/2 with ALPN, which Knox's connectors do not offer) need their own
 * socket, and therefore their own lifecycle hook.
 *
 * @since 3.0.0
 */
public interface ProtocolListener {

  /**
   * A short name for this listener, used in startup logging and error messages.
   *
   * @return the listener name; never null
   */
  String getName();

  /**
   * Whether the deployment has switched this listener on. Called before
   * {@link #start}; a listener that returns false is never started and must not
   * bind a port or allocate threads.
   *
   * @param config the gateway configuration
   * @return true if this listener should run
   */
  boolean isEnabled(GatewayConfig config);

  /**
   * Bind and begin serving. Called after the gateway services have started and
   * topologies have been deployed, so implementations may resolve backends from
   * the service registry during startup.
   *
   * @param config the gateway configuration
   * @param services the started gateway services
   * @throws Exception if the listener cannot start; the gateway will fail to start
   */
  void start(GatewayConfig config, GatewayServices services) throws Exception;

  /**
   * Stop accepting new work and drain in-flight requests before forcing
   * termination. Called before Jetty stops.
   * <p>
   * How long to drain for is the implementation's own configuration to read —
   * what counts as a reasonable wait depends entirely on the protocol, and a
   * listener carrying multi-hour streams has different needs from one serving
   * short requests.
   * <p>
   * Implementations must return rather than throw when the drain deadline passes
   * with work still in flight; failing to stop cleanly should not prevent the
   * rest of the gateway from shutting down.
   *
   * @throws Exception if the listener cannot be stopped
   */
  void stop() throws Exception;

  /**
   * The port this listener is bound to, for startup logging.
   *
   * @return the bound port, or -1 if the listener is not running
   */
  int getPort();

  /**
   * Every port this listener bound, for implementations that run more than one
   * server. Reported at startup so an operator can see what came up.
   *
   * @return the bound ports; by default the single {@link #getPort()}
   */
  default List<Integer> getPorts() {
    return Collections.singletonList(getPort());
  }

  /**
   * Notifies the listener that topologies have been redeployed, so anything it
   * derived from topology configuration must be recomputed.
   * <p>
   * Knox reloads topologies from disk while running, and listeners on this path
   * do not go through the webapp redeployment that refreshes the servlet filter
   * chains. A listener that caches anything from a topology — authorization
   * rules, provider parameters — will therefore keep serving stale configuration
   * until it is told otherwise, which for security configuration means an
   * administrator's change silently not taking effect.
   * <p>
   * Called for every topology event, so implementations should be cheap:
   * invalidate and recompute lazily rather than rebuilding here.
   */
  default void reload() {
  }
}
