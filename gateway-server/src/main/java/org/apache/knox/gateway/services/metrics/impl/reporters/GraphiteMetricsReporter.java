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
package org.apache.knox.gateway.services.metrics.impl.reporters;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.metrics.MetricsContext;
import org.apache.knox.gateway.services.metrics.MetricsReporterException;
import org.apache.knox.gateway.services.metrics.impl.DefaultMetricsService;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class GraphiteMetricsReporter extends AbstractMetricsReporter {

  public static final String NAME = "graphite-reporter";

  private Graphite graphite;
  private GraphiteReporter reporter;
  private int reportingFrequency = 1;

  @Override
  public void init(GatewayConfig config) throws MetricsReporterException {
    if (config.isMetricsEnabled() && config.isGraphiteMetricsReportingEnabled()) {
      graphite = new Graphite(new InetSocketAddress(config.getGraphiteHost(), config.getGraphitePort()));
      reportingFrequency = config.getGraphiteReportingFrequency();
      setEnabled(true);
    }
  }

  @Override
  public void start(MetricsContext metricsContext) throws MetricsReporterException {
    MetricRegistry registry = (MetricRegistry) metricsContext.getProperty(
        DefaultMetricsService.METRICS_REGISTRY);
    reporter = GraphiteReporter.forRegistry(registry)
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.MILLISECONDS)
        .filter(MetricFilter.ALL)
        .build(graphite);
    reporter.start(reportingFrequency, TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws MetricsReporterException {
    reporter.stop();
  }

  @Override
  public String getName() {
    return NAME;
  }
}
