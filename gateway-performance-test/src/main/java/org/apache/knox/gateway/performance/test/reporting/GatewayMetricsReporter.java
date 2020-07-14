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
package org.apache.knox.gateway.performance.test.reporting;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.performance.test.ExecutorServiceUtils;
import org.apache.knox.gateway.performance.test.PerformanceTestConfiguration;
import org.apache.knox.gateway.performance.test.PerformanceTestLifeCyleListener;
import org.apache.knox.gateway.performance.test.PerformanceTestMessages;
import org.apache.knox.gateway.performance.test.ResponseTimeCache;

public class GatewayMetricsReporter implements PerformanceTestLifeCyleListener {
  private static final PerformanceTestMessages LOG = MessagesFactory.get(PerformanceTestMessages.class);
  private static final String TOKEN_STATE_STATISTICS_OBJECT_NAME = "metrics:type=Statistics,name=TokenStateService";
  private static final String TIMERS_OBJECT_NAME = "metrics:type=timers,name=client*";
  private static final String HEAP_GAUGES_OBJECT_NAME = "metrics:type=gauges,name=heap*";
  private final PerformanceTestConfiguration configuration;
  private final ResponseTimeCache responseTimeCache;
  private final JMXConnector jmxConnector;
  private final ScheduledExecutorService executor;
  private final Map<String, String> objectNamesToQuery;
  private final List<ReportEngine> reportEngines;

  public GatewayMetricsReporter(PerformanceTestConfiguration configuration, ResponseTimeCache responseTimeCache) throws IOException {
    this.configuration = configuration;
    this.responseTimeCache = responseTimeCache;
    final JMXServiceURL serviceUrl = new JMXServiceURL(configuration.getGatewayJmxUrl());
    jmxConnector = JMXConnectorFactory.connect(serviceUrl, null);
    final ThreadFactory threadFactory = new BasicThreadFactory.Builder().namingPattern("GatewayMetricsReporter-%d").build();
    this.executor = Executors.newSingleThreadScheduledExecutor(threadFactory);
    this.objectNamesToQuery = new HashMap<>();
    objectNamesToQuery.put("tokenStateStatistics", TOKEN_STATE_STATISTICS_OBJECT_NAME);
    objectNamesToQuery.put("timers", TIMERS_OBJECT_NAME);
    objectNamesToQuery.put("heapGauges", HEAP_GAUGES_OBJECT_NAME);
    this.reportEngines = Arrays.asList(new JsonReportEngine(configuration), new YamlReportEngine(configuration));
  }

  public void start() throws IOException {
    final MBeanServerConnection mbeanServerConnection = jmxConnector.getMBeanServerConnection();
    executor.scheduleAtFixedRate(() -> generateReports(mbeanServerConnection), configuration.getReportGenerationPeriod(), configuration.getReportGenerationPeriod(),
        TimeUnit.SECONDS);
  }

  @Override
  public void onFinish() {
    ExecutorServiceUtils.shutdownAndAwaitTermination(executor, 10, TimeUnit.SECONDS);
    try {
      jmxConnector.close();
    } catch (IOException e) {
      // NOP
    }
    LOG.shutDownMetricsReporter();
  }

  private void generateReports(MBeanServerConnection mbeanConn) {
    printResponseTimes();
    printMetrics(mbeanConn);
  }

  private void printResponseTimes() {
    final Map<String, Object> responseTimesMap = new HashMap<>();
    setReponseTimesMetrics(responseTimesMap, responseTimeCache.listAcquireResponseTimes(), "acquireResponseTimes");
    setReponseTimesMetrics(responseTimesMap, responseTimeCache.listRenewResponseTimes(), "renewResponseTimes");
    for (ReportEngine reportEngine : reportEngines) {
      reportEngine.generateReport("responseTimes", responseTimesMap);
    }
  }

  private void setReponseTimesMetrics(Map<String, Object> responseTimesMap, List<Long> responseTimes, String metricsName) {
    final Map<String, Object> statistics = new HashMap<>();
    statistics.put("_data", responseTimes);
    final double[] doubleArrayResponseTimes = responseTimes.stream().mapToDouble(responseTime -> responseTime).toArray();
    statistics.put("max", StatUtils.max(doubleArrayResponseTimes));
    statistics.put("min", StatUtils.min(doubleArrayResponseTimes));
    statistics.put("mean", StatUtils.mean(doubleArrayResponseTimes));
    statistics.put("mode", StatUtils.mode(doubleArrayResponseTimes));
    statistics.put("geometricMean", StatUtils.geometricMean(doubleArrayResponseTimes));
    responseTimesMap.put(metricsName, statistics);
  }

  private void printMetrics(MBeanServerConnection mbeanConn) {
    try {
      for (Map.Entry<String, String> objectNameEntry : objectNamesToQuery.entrySet()) {
        Map<String, Object> metrics = new HashMap<>();
        for (ObjectName bean : mbeanConn.queryNames(ObjectName.getInstance(objectNameEntry.getValue()), null)) {
          metrics.put(bean.getCanonicalName(), getAttributes(mbeanConn, bean, mbeanConn.getMBeanInfo(bean)));
        }
        for (ReportEngine reportEngine : reportEngines) {
          reportEngine.generateReport(objectNameEntry.getKey(), metrics);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private Map<String, Object> getAttributes(MBeanServerConnection mbeanConn, ObjectName bean, MBeanInfo mbeanInfo) throws Exception {
    final Map<String, Object> attributeMap = new HashMap<>();
    for (MBeanAttributeInfo mbeanAttribute : mbeanInfo.getAttributes()) {
      String attributeName = mbeanAttribute.getName();
      Object attributeValue = mbeanConn.getAttribute(bean, attributeName);
      attributeMap.put(attributeName, attributeValue);
    }
    return attributeMap;
  }

}
