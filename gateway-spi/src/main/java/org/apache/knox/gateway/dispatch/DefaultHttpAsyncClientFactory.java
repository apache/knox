/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.dispatch;

import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.nio.client.HttpAsyncClient;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.metrics.MetricsService;

import javax.net.ssl.SSLContext;
import javax.servlet.FilterConfig;

public class DefaultHttpAsyncClientFactory extends DefaultHttpClientFactory implements HttpAsyncClientFactory {

    @Override
    public HttpAsyncClient createAsyncHttpClient(FilterConfig filterConfig) {
        final String serviceRole = filterConfig.getInitParameter(PARAMETER_SERVICE_ROLE);
        HttpAsyncClientBuilder builder;
        GatewayConfig gatewayConfig = (GatewayConfig) filterConfig.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
        GatewayServices services = (GatewayServices) filterConfig.getServletContext()
                .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
        if (gatewayConfig != null && gatewayConfig.isMetricsEnabled()) {
            MetricsService metricsService = services.getService(ServiceType.METRICS_SERVICE);
            builder = metricsService.getInstrumented(HttpAsyncClientBuilder.class);
        } else {
            builder = HttpAsyncClients.custom();
        }

        // Conditionally set a custom SSLContext
        SSLContext sslContext = createSSLContext(services, filterConfig, serviceRole);
        if (sslContext != null) {
            builder.setSSLContext(sslContext);
        }

        if (Boolean.parseBoolean(System.getProperty(GatewayConfig.HADOOP_KERBEROS_SECURED))) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new DefaultHttpAsyncClientFactory.UseJaasCredentials());

            Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
                    .register(AuthSchemes.SPNEGO, new KnoxSpnegoAuthSchemeFactory(true))
                    .build();

            builder.setDefaultAuthSchemeRegistry(authSchemeRegistry)
                    .setDefaultCookieStore(new HadoopAuthCookieStore(gatewayConfig))
                    .setDefaultCredentialsProvider(credentialsProvider);
        } else {
            builder.setDefaultCookieStore(new DefaultHttpAsyncClientFactory.NoCookieStore());
        }

        builder.setKeepAliveStrategy(DefaultConnectionKeepAliveStrategy.INSTANCE);
        builder.setConnectionReuseStrategy(DefaultConnectionReuseStrategy.INSTANCE);
        builder.setRedirectStrategy(new DefaultHttpAsyncClientFactory.NeverRedirectStrategy());
        int maxConnections = getMaxConnections(filterConfig);
        builder.setMaxConnTotal(maxConnections);
        builder.setMaxConnPerRoute(maxConnections);
        builder.setDefaultRequestConfig(getRequestConfig(filterConfig, serviceRole));

        return builder.build();
    }
}
