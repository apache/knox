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

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.knox.gateway.SpiGatewayMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import javax.net.ssl.SSLContext;

public class FipsConnectionManagerFactory {

    private static final SpiGatewayMessages LOG = MessagesFactory.get(SpiGatewayMessages.class);

    public static PoolingHttpClientConnectionManager createConnectionManager(SSLContext sslContext, int maxConnections) {
        LOG.configureInterceptingSocket();
        BCInterceptingConnectionSocketFactory BCInterceptingConnectionSocketFactory = sslContext != null ?
                new BCInterceptingConnectionSocketFactory(new SSLConnectionSocketFactory(sslContext))
                : new BCInterceptingConnectionSocketFactory(SSLConnectionSocketFactory.getSocketFactory());

        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", new BCInterceptingConnectionSocketFactory(PlainConnectionSocketFactory.getSocketFactory()))
                .register("https", BCInterceptingConnectionSocketFactory)
                .build();

        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        connManager.setMaxTotal(maxConnections);
        connManager.setDefaultMaxPerRoute(maxConnections);
        return connManager;
    }
}
