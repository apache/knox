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
package org.apache.knox.gateway.services.token.impl;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * An {@link SSLSocketFactory} decorator that stamps a fixed host onto every socket it creates.
 * <p>
 * BouncyCastle's JSSE takes the hostname for HTTPS endpoint identification from the socket itself
 * rather than from the URL. {@link java.net.HttpURLConnection}, which Nimbus uses to fetch a JWKS,
 * creates the socket without a host and connects it by address, so under the BouncyCastle FIPS
 * provider the hostname arrives as {@code null} and endpoint identification fails with
 * "No hostname specified for HTTPS endpoint ID check" before the certificate is ever examined.
 * That happens even when the certificate carries a matching iPAddress SAN.
 * <p>
 * Setting the host restores the check: BouncyCastle matches an IP literal against the
 * certificate's iPAddress SANs and a name against its dNSName SANs. Verification is preserved,
 * not bypassed.
 * <p>
 * The host is applied through {@code setHost(String)}, declared by
 * {@code org.bouncycastle.jsse.BCSSLSocket}. That type is reached reflectively so Knox does not
 * take a compile dependency on the BouncyCastle TLS provider, which is present only in FIPS
 * deployments. On sockets that do not expose the method this decorator is a no-op, which is
 * correct for the stock JSSE: it identifies IP endpoints from the URL already.
 */
class HostSettingSSLSocketFactory extends SSLSocketFactory {

  private static final TokenAuthorityServiceMessages LOG = MessagesFactory.get(TokenAuthorityServiceMessages.class);

  private static final String SET_HOST = "setHost";

  private final SSLSocketFactory delegate;
  private final String host;

  HostSettingSSLSocketFactory(SSLSocketFactory delegate, String host) {
    this.delegate = delegate;
    this.host = host;
  }

  /**
   * Wraps {@code base} so sockets created for {@code url} carry that URL's host.
   *
   * @return the wrapped factory, or {@code base} unchanged when the URL has no usable host
   */
  static SSLSocketFactory forUrl(URL url, SSLSocketFactory base) {
    final String host = url == null ? null : url.getHost();
    if (host == null || host.isEmpty()) {
      return base;
    }
    return new HostSettingSSLSocketFactory(base, host);
  }

  /**
   * Applies {@code host} to {@code socket} via a public {@code setHost(String)} method.
   * <p>
   * The method is looked up on each of the socket's declaring types in turn, because
   * {@link Method#invoke} checks access against the type the method was <em>looked up on</em>,
   * not against the socket's runtime class, while the call itself dispatches virtually. The same
   * method body can therefore be reachable through one declaring type and not another. In
   * ordinary code this is the difference between {@code ((ProvSSLSocketDirect) s).setHost(h)},
   * which will not compile outside BouncyCastle's package, and
   * {@code ((BCSSLSocket) s).setHost(h)}, which will - both ending up in the same implementation.
   * <p>
   * That is why the two failures are treated differently:
   * <ul>
   *   <li>{@link IllegalAccessException} describes the declaring type, not the implementation: the
   *       method never ran, and a different declaring type - the public interface - may well be
   *       invocable. The walk continues.</li>
   *   <li>An exception thrown by {@code setHost} itself means the call got through and failed.
   *       Every remaining candidate type resolves to that same implementation, so retrying would
   *       repeat an identical call and any side effects it already had. The walk stops.</li>
   * </ul>
   * Either way the host is simply left unset, which is the behaviour from before it was applied.
   *
   * @return {@code true} when the host was applied, {@code false} when the socket does not
   *         support it or the call failed
   */
  static boolean setHost(Socket socket, String host) {
    if (socket == null || host == null || host.isEmpty()) {
      return false;
    }
    for (Class<?> type : declaringTypes(socket.getClass())) {
      final Method method;
      try {
        method = type.getMethod(SET_HOST, String.class);
      } catch (NoSuchMethodException e) {
        continue;
      }
      try {
        method.invoke(socket, host);
        return true;
      } catch (IllegalAccessException e) {
        /* This declaring type is not accessible from here; a public interface still may be. */
        continue;
      } catch (InvocationTargetException | RuntimeException e) {
        final Throwable cause = e.getCause() == null ? e : e.getCause();
        LOG.failedToSetSocketHost(host, cause.toString(), e);
        return false;
      }
    }
    return false;
  }

  /*
   * Ordered so the accessible declaration is tried first: BouncyCastle's socket implementations
   * are package private (and their package need not be exported by the module), so setHost is only
   * invocable through the public BCSSLSocket interface that also declares it. Looking at the
   * concrete class first would merely raise IllegalAccessException and have to be recovered from.
   * Interfaces are collected transitively, then the class hierarchy is appended as a fallback for
   * sockets shaped differently from BouncyCastle's.
   */
  private static Collection<Class<?>> declaringTypes(Class<?> socketType) {
    final Set<Class<?>> interfaces = new LinkedHashSet<>();
    final List<Class<?>> classes = new ArrayList<>();
    for (Class<?> type = socketType; type != null && type != Object.class; type = type.getSuperclass()) {
      classes.add(type);
      addInterfaces(type, interfaces);
    }
    final List<Class<?>> types = new ArrayList<>(interfaces);
    types.addAll(classes);
    return types;
  }

  private static void addInterfaces(Class<?> type, Set<Class<?>> collected) {
    for (Class<?> implemented : type.getInterfaces()) {
      if (collected.add(implemented)) {
        addInterfaces(implemented, collected);
      }
    }
  }

  private Socket configure(Socket socket) {
    setHost(socket, host);
    return socket;
  }

  @Override
  public String[] getDefaultCipherSuites() {
    return delegate.getDefaultCipherSuites();
  }

  @Override
  public String[] getSupportedCipherSuites() {
    return delegate.getSupportedCipherSuites();
  }

  @Override
  public Socket createSocket() throws IOException {
    return configure(delegate.createSocket());
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException {
    return configure(delegate.createSocket(host, port));
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
    return configure(delegate.createSocket(host, port, localHost, localPort));
  }

  @Override
  public Socket createSocket(InetAddress host, int port) throws IOException {
    return configure(delegate.createSocket(host, port));
  }

  @Override
  public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
    return configure(delegate.createSocket(address, port, localAddress, localPort));
  }

  @Override
  public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
    return configure(delegate.createSocket(s, host, port, autoClose));
  }
}
