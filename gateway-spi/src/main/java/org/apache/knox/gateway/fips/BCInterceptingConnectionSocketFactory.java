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
package org.apache.knox.gateway.fips;

import org.apache.http.HttpHost;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class BCInterceptingConnectionSocketFactory implements ConnectionSocketFactory {
    private final ConnectionSocketFactory delegate;

    public BCInterceptingConnectionSocketFactory(ConnectionSocketFactory delegate) {
        this.delegate = delegate;
    }

    @Override
    public Socket createSocket(HttpContext context) throws IOException {
        return new BCInterceptingSocket(delegate.createSocket(context));
    }

    @Override
    public Socket connectSocket(int connectTimeout, Socket socket, HttpHost host,
                                InetSocketAddress remoteAddress, InetSocketAddress localAddress,
                                HttpContext context) throws IOException {
        Socket connected = delegate.connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, context);
        return connected instanceof BCInterceptingSocket ? connected : new BCInterceptingSocket(connected);
    }
}
