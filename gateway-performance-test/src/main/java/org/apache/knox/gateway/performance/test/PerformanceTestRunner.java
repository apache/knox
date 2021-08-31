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
package org.apache.knox.gateway.performance.test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import org.apache.knox.gateway.performance.test.reporting.GatewayMetricsReporter;

public class PerformanceTestRunner {

  public static void main(String[] args) throws Exception {
    final PerformanceTestConfiguration configuration = new PerformanceTestConfiguration(args[0]);
    final ResponseTimeCache responseTimeCache = new ResponseTimeCache();
    final GatewayMetricsReporter metricsReporter = new GatewayMetricsReporter(configuration, responseTimeCache);
    metricsReporter.start();
    final List<PerformanceTestLifeCyleListener> lifeCyleListeners = Arrays.asList(metricsReporter);
    for (UseCaseRunner useCaseRunner : getUseCaseRunners()) {
      if (configuration.isUseCaseEnabled(useCaseRunner.getUseCaseName())) {
        useCaseRunner.setResponseTimeCache(responseTimeCache);
        useCaseRunner.execute(configuration, lifeCyleListeners);
      }
    }
  }

  private static Set<UseCaseRunner> getUseCaseRunners() {
    final Set<UseCaseRunner> useCaseRunners = new HashSet<>();
    ServiceLoader.load(UseCaseRunner.class).forEach((useCaseRunner) -> useCaseRunners.add(useCaseRunner));
    return useCaseRunners;
  }
}
