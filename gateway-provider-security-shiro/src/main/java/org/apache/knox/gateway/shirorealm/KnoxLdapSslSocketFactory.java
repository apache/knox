/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.knox.gateway.shirorealm;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

public class KnoxLdapSslSocketFactory extends SSLSocketFactory {

    private static final AtomicReference<SSLSocketFactory> CONFIGURED_DELEGATE = new AtomicReference<>();

    private final SSLSocketFactory delegate;

    KnoxLdapSslSocketFactory(SSLSocketFactory delegate) {
        this.delegate = delegate;
    }

    public static SocketFactory getDefault() {
        SSLSocketFactory factory = CONFIGURED_DELEGATE.get();
        if (factory == null) {
            factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        }
        return new KnoxLdapSslSocketFactory(factory);
    }

    static void setDelegate(SSLSocketFactory factory) {
        CONFIGURED_DELEGATE.set(factory);
    }

    static SSLSocketFactory getDelegate() {
        return CONFIGURED_DELEGATE.get();
    }

    SSLSocketFactory getWrappedFactory() {
        return delegate;
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
        return delegate.createSocket();
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        return delegate.createSocket(socket, host, port, autoClose);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return delegate.createSocket(host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return delegate.createSocket(host, port, localHost, localPort);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return delegate.createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return delegate.createSocket(address, port, localAddress, localPort);
    }
}
