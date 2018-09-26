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
package org.apache.knox.gateway.services.metrics.impl;

import com.codahale.metrics.MetricRegistry;
import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.metrics.InstrumentationProvider;
import org.apache.knox.gateway.services.metrics.InstrumentationProviderDescriptor;
import org.apache.knox.gateway.services.metrics.MetricsContext;
import org.apache.knox.gateway.services.metrics.MetricsReporter;
import org.apache.knox.gateway.services.metrics.MetricsReporterException;
import org.apache.knox.gateway.services.metrics.MetricsService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

public class DefaultMetricsService implements MetricsService {
  private static GatewayMessages LOG = MessagesFactory.get( GatewayMessages.class );

  private static final MetricRegistry metrics = new MetricRegistry();

  public static final String METRICS_REGISTRY = "metrics-registry";

  private Map<Class<?>, InstrumentationProvider> instrumentationProviders;

  private ArrayList<MetricsReporter> metricsReporters;

  private MetricsContext context;

  private GatewayConfig config;

  public static MetricRegistry getMetricRegistry() {
    return metrics;
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws
      ServiceLifecycleException {
    this.config = config;
    context = new DefaultMetricsContext(this);
    context.setProperty(METRICS_REGISTRY, getMetricRegistry());
    instrumentationProviders = new HashMap<>();
    metricsReporters = new ArrayList<>();
    if (config.isMetricsEnabled()) {
      loadInstrumentationProviders();
      loadAndInitReporters(config);
    }
  }

  private void loadInstrumentationProviders() {
    ServiceLoader<InstrumentationProviderDescriptor> descriptors = ServiceLoader.load(InstrumentationProviderDescriptor.class);
    Iterator<InstrumentationProviderDescriptor> descriptorIterator = descriptors.iterator();
    while ( descriptorIterator.hasNext() ) {
      instrumentationProviders.putAll(descriptorIterator.next().providesInstrumentation());
    }
  }

  private void loadAndInitReporters(GatewayConfig config) {
    ServiceLoader<MetricsReporter> reporters = ServiceLoader.load(MetricsReporter.class);
    Iterator<MetricsReporter> reportersIterator = reporters.iterator();
    while ( reportersIterator.hasNext() ) {
      MetricsReporter metricsReporter = reportersIterator.next();
      try {
        metricsReporter.init(config);
        metricsReporters.add(metricsReporter);
      } catch ( MetricsReporterException e ) {
        LOG.failedToInitializeReporter(metricsReporter.getName(), e);
      }
    }
  }

  @Override
  public void start() throws ServiceLifecycleException {
    if (config.isMetricsEnabled()) {
      for (MetricsReporter reporter : metricsReporters) {
        if ( reporter.isEnabled() ) {
          try {
            reporter.start(context);
          } catch ( MetricsReporterException e ) {
            LOG.failedToStartReporter(reporter.getName(), e);
          }
        }
      }
    }
  }

  @Override
  public void stop() throws ServiceLifecycleException {
    if (config.isMetricsEnabled()) {
      for (MetricsReporter reporter : metricsReporters) {
        if (reporter.isEnabled()) {
          try {
            reporter.stop();
          } catch ( MetricsReporterException e ) {
            LOG.failedToStopReporter(reporter.getName(), e);
          }
        }
      }
    }
  }

  @Override
  public <T> T getInstrumented(T instanceClass) {
    InstrumentationProvider<T> instrumentationProvider = instrumentationProviders.get(instanceClass.getClass());
    if (instrumentationProvider == null) {
      return null;
    }
    return instrumentationProvider.getInstrumented(instanceClass, context);
  }

  @Override
  public <T> T getInstrumented(Class<T> clazz) {
    InstrumentationProvider<T> instrumentationProvider = instrumentationProviders.get(clazz);
    if (instrumentationProvider == null) {
      return null;
    }
    return instrumentationProvider.getInstrumented(context);
  }

  public Map<Class<?>, InstrumentationProvider> getInstrumentationProviders() {
    return instrumentationProviders;
  }

  public ArrayList<MetricsReporter> getMetricsReporters() {
    return metricsReporters;
  }

  public MetricsContext getContext() {
    return context;
  }

}
