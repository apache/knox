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
  private final KerberosContext kerberos;

  public ClientContext() {
    configuration = new MapConfiguration(new HashMap<>());
    poolContext = new PoolContext(this);
    socketContext = new SocketContext(this);
    connectionContext = new ConnectionContext(this);
    kerberos = new KerberosContext(this);
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

    PoolContext(ClientContext clientContext) {
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

    SocketContext(ClientContext clientContext) {
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

    ConnectionContext(ClientContext clientContext) {
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

    public ConnectionContext withTruststore(final String truststoreLocation,
        final String truststorePass) {
      configuration.addProperty("truststoreLocation", truststoreLocation);
      configuration.addProperty("truststorePass", truststorePass);
      return this;
    }

    public ConnectionContext withPublicCertPem(final String endpointPublicCertPem) {
          configuration.addProperty("endpointPublicCertPem", endpointPublicCertPem);
          return this;
    }

    public String truststoreLocation() {
      return configuration.getString("truststoreLocation");
    }

    public String truststorePass() {
      return configuration.getString("truststorePass");
    }

    public String endpointPublicCertPem() {
      return configuration.getString("endpointPublicCertPem");
    }
  }

  /**
   * Context for Kerberos properties
   * @since 1.3.0
   */
  public static class KerberosContext extends Context {

    KerberosContext(ClientContext clientContext) {
      super(clientContext, "kerberos");
    }

    public KerberosContext jaasConf(final String path) {
      configuration.addProperty("jaasConf", path);
      return this;
    }

    public String jaasConf() {
      return configuration.getString("jaasConf");
    }

    public KerberosContext krb5Conf(final String path) {
      configuration.addProperty("krb5Conf", path);
      return this;
    }

    public String krb5Conf() {
      return configuration.getString("krb5Conf");
    }

    public KerberosContext enable(final boolean enable) {
      configuration.addProperty("enable", enable);
      return this;
    }

    public boolean enable() {
      return configuration.getBoolean("enable", false);
    }

    public KerberosContext debug(final boolean debug) {
      configuration.addProperty("debug", debug);
      return this;
    }

    public boolean debug() {
      return configuration.getBoolean("debug", false);
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

  public KerberosContext kerberos() {
    return kerberos;
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
