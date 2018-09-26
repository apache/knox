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
package org.apache.knox.gateway.shell;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.MapConfiguration;
import org.apache.commons.configuration.SubsetConfiguration;

import java.util.HashMap;

public class ClientContext {

  private final Configuration configuration;
  private final PoolContext poolContext;
  private final SocketContext socketContext;
  private final ConnectionContext connectionContext;

  private ClientContext() {
    configuration = new MapConfiguration(new HashMap<>());
    poolContext = new PoolContext(this);
    socketContext = new SocketContext(this);
    connectionContext = new ConnectionContext(this);
  }

  private static class Context {
    private final ClientContext clientContext;
    protected final Configuration configuration;

    private Context(ClientContext clientContext, String prefix) {
      this.clientContext = clientContext;
      this.configuration = new SubsetConfiguration(clientContext.configuration, prefix);
    }

    public ClientContext end() {
      return clientContext;
    }
  }

  public static class PoolContext extends Context {

    private PoolContext(ClientContext clientContext) {
      super(clientContext, "pool");
    }

    public int maxTotal() {
      return configuration.getInt("total", 20);
    }

    public PoolContext maxTotal(final int timeout) {
      configuration.addProperty("total", timeout);
      return this;
    }

    public int defaultMaxPerRoute() {
      return configuration.getInt("max-per-route", 20);
    }

    public PoolContext defaultMaxPerRoute(final int reuseaddr) {
      configuration.addProperty("max-per-route", reuseaddr);
      return this;
    }
  }

  public static class SocketContext extends Context {

    private SocketContext(ClientContext clientContext) {
      super(clientContext, "socket");
    }

    public int timeout() {
      return configuration.getInt("timeout", 0);
    }

    public SocketContext timeout(final int timeout) {
      configuration.addProperty("timeout", timeout);
      return this;
    }

    public boolean reuseAddress() {
      return configuration.getBoolean("reuse-address", false);
    }

    public SocketContext reuseAddress(final boolean reuseAddress) {
      configuration.addProperty("reuse-address", reuseAddress);
      return this;
    }

    public int linger() {
      return configuration.getInt("linger", -1);
    }

    public SocketContext linger(final int value) {
      configuration.addProperty("linger", value);
      return this;
    }

    public boolean keepalive() {
      return configuration.getBoolean("keepalive", false);
    }

    public SocketContext keepalive(final boolean enableKeepalive) {
      configuration.addProperty("keepalive", enableKeepalive);
      return this;
    }

    public boolean tcpNoDelay() {
      return configuration.getBoolean("nodelay", true);
    }

    public SocketContext tcpNoDelay(final boolean value) {
      configuration.addProperty("nodelay", value);
      return this;
    }
  }

  public static class ConnectionContext extends Context {

    private ConnectionContext(ClientContext clientContext) {
      super(clientContext, "connection");
    }

    public int timeout() {
      return configuration.getInt("timeout", 0);
    }

    public ConnectionContext timeout(final int timeout) {
      configuration.addProperty("timeout", timeout);
      return this;
    }

    public boolean staleChecking() {
      return configuration.getBoolean("stalecheck", true);
    }

    public ConnectionContext staleChecking(final boolean value) {
      configuration.addProperty("stalecheck", value);
      return this;
    }

    public boolean secure() {
      return configuration.getBoolean("secure", true);
    }

    public ConnectionContext secure(final boolean value) {
      configuration.addProperty("secure", value);
      return this;
    }

    public int bufferSize() {
      return configuration.getInt("buffer-size", -1);
    }

    public ConnectionContext bufferSize(final int size) {
      configuration.addProperty("buffer-size", size);
      return this;
    }
  }

  public PoolContext pool() {
    return poolContext;
  }

  public SocketContext socket() {
    return socketContext;
  }

  public ConnectionContext connection() {
    return connectionContext;
  }

  public static ClientContext with(final String username, final String password, final String url) {
    ClientContext context = new ClientContext();
    context.configuration.addProperty("username", username);
    context.configuration.addProperty("password", password);
    context.configuration.addProperty("url", url);
    return context;
  }

  public static ClientContext with(final String url) {
    ClientContext context = new ClientContext();
    context.configuration.addProperty("url", url);
    return context;
  }

  public String username() {
    return configuration.getString("username");
  }

  public String password() {
    return configuration.getString("password");
  }

  public String url() {
    return configuration.getString("url");
  }
}
