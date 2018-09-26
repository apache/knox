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
import org.apache.knox.gateway.GatewayFilter;
import org.apache.knox.gateway.services.metrics.InstrumentationProvider;
import org.apache.knox.gateway.services.metrics.MetricsContext;
import org.apache.knox.gateway.services.metrics.impl.DefaultMetricsService;

public class InstrGatewayFilterProvider implements
    InstrumentationProvider<GatewayFilter> {

  @Override
  public GatewayFilter getInstrumented(MetricsContext metricsContext) {
    throw new UnsupportedOperationException();
  }

  @Override
  public GatewayFilter getInstrumented(GatewayFilter gatewayFilter, MetricsContext metricsContext) {
    return new InstrumentedGatewayFilter(gatewayFilter, (MetricRegistry) metricsContext.getProperty(DefaultMetricsService.METRICS_REGISTRY));
  }
}
