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
package org.apache.knox.gateway.services.metrics.impl.instr;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.httpclient.HttpClientMetricNameStrategy;
import com.codahale.metrics.httpclient.InstrumentedHttpRequestExecutor;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.RequestLine;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.knox.gateway.services.metrics.InstrumentationProvider;
import org.apache.knox.gateway.services.metrics.MetricsContext;
import org.apache.knox.gateway.services.metrics.impl.DefaultMetricsService;

import java.net.URISyntaxException;
import java.util.Locale;

public class InstrHttpClientBuilderProvider implements
    InstrumentationProvider<HttpClientBuilder> {

  @Override
  public HttpClientBuilder getInstrumented(MetricsContext metricsContext) {
    MetricRegistry registry = (MetricRegistry) metricsContext.getProperty(DefaultMetricsService.METRICS_REGISTRY);
    return  HttpClientBuilder.create().setRequestExecutor(new InstrumentedHttpRequestExecutor(registry, TOPOLOGY_URL_AND_METHOD));
  }

  @Override
  public HttpClientBuilder getInstrumented(HttpClientBuilder instanceClass, MetricsContext metricsContext) {
    throw new UnsupportedOperationException();
  }

  private static final HttpClientMetricNameStrategy TOPOLOGY_URL_AND_METHOD = new HttpClientMetricNameStrategy() {
    @Override
    public String getNameFor(String name, HttpRequest request) {
      try {
        String context = "";
        Header header = request.getFirstHeader("X-Forwarded-Context");
        if (header != null) {
          context = header.getValue();
        }
        RequestLine requestLine = request.getRequestLine();
        URIBuilder uriBuilder = new URIBuilder(requestLine.getUri());
        String resourcePath = InstrUtils.getResourcePath(uriBuilder.removeQuery().build().toString());
        return MetricRegistry.name("service", name, context + resourcePath, methodNameString(request));
      } catch (URISyntaxException e) {
        throw new IllegalArgumentException(e);
      }
    }

    private String methodNameString(HttpRequest request) {
      return request.getRequestLine().getMethod().toLowerCase(Locale.ROOT) + "-requests";
    }
  };
}
