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
package org.apache.knox.gateway.performance.test;


import static org.apache.knox.gateway.performance.test.knoxtoken.KnoxTokenUseCaseRunner.USE_CASE_NAME;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;

public class PerformanceTestConfiguration {

  private static final String URL_PATH_SEPARATOR = "/";
  private static final String PERF_TEST_PREFIX = "perf.test.";
  private static final String GATEWAY_PREFIX = PERF_TEST_PREFIX + "gateway.";
  private static final String PARAM_GATEWAY_URL_PROTOROL = GATEWAY_PREFIX + "url.protocol";
  private static final String DEFAULT_GATEWAY_URL_PROTOCOL = "https";
  private static final String PARAM_GATEWAY_URL_HOST = GATEWAY_PREFIX + "url.host";
  private static final String DEFAULT_GATEWAY_URL_HOST = "localhost";
  private static final String PARAM_GATEWAY_URL_PORT = GATEWAY_PREFIX + "url.port";
  private static final String DEFAULT_GATEWAY_URL_PORT = "8443";
  private static final String GATEWAY_URL_TEMPLATE = "%s://%s:%d";
  private static final String PARAM_GATEWAY_JMX_PORT = GATEWAY_PREFIX + "jmx.port";
  private static final String DEFAULT_GATEWAY_JMX_PORT = "8888";
  private static final String GATEWAY_JMX_URL_TEMPLATE = "service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi";
  private static final String PARAM_GATEWAY_PATH = GATEWAY_PREFIX + "path";
  private static final String DEFAULT_GATEWAY_PATH = "gateway";
  private static final String PARAM_GATEWAY_USER = GATEWAY_PREFIX + "user";
  private static final String DEFAULT_GATEWAY_USER = "guest";
  private static final String PARAM_GATEWAY_PW = GATEWAY_PREFIX + "pw";
  private static final String DEFAULT_GATEWAY_PW = "guest-password";

  private static final String REPORT_GENERATION_PREFIX = PERF_TEST_PREFIX + "report.generation.";
  private static final String PARAM_REPORT_GENERATION_PERIOD = REPORT_GENERATION_PREFIX + "periodInSecs";
  private static final String DEFAULT_REPORT_GENERATION_PERIOD = "60";
  private static final String PARAM_REPORT_GENERATION_TARGET_FOLDER = REPORT_GENERATION_PREFIX + "target.folder";
  private static final String PARAM_ENABLED_POSTFIX = ".enabled";
  private static final String USE_CASE_PREFIX = PERF_TEST_PREFIX + "usecase.";
  private static final String PARAM_USE_CASE_TOPOLOGY_POSTFIX = ".topology";

  private final Properties configuration = new Properties();
  private final Map<String, Map<String, String>> defaultUseCaseMap;

  PerformanceTestConfiguration(String configFileLocation) throws IOException {
    final Path configFilePath = Paths.get(configFileLocation);
    try (Reader configFileReader = Files.newBufferedReader(configFilePath, StandardCharsets.UTF_8)) {
      configuration.load(configFileReader);
    }
    final Map<String, String> knoxTokenDefaultTopologies = new HashMap<>();
    knoxTokenDefaultTopologies.put("gateway", "sandbox");
    knoxTokenDefaultTopologies.put("tokenbased", "tokenbased");
    defaultUseCaseMap = new HashMap<>();
    defaultUseCaseMap.put(USE_CASE_NAME, knoxTokenDefaultTopologies);
  }

  /* Gateway connection */

  public String getGatewayUrl() {
    final String protocol = configuration.getProperty(PARAM_GATEWAY_URL_PROTOROL, DEFAULT_GATEWAY_URL_PROTOCOL);
    final String host = configuration.getProperty(PARAM_GATEWAY_URL_HOST, DEFAULT_GATEWAY_URL_HOST);
    final int port = Integer.parseInt(configuration.getProperty(PARAM_GATEWAY_URL_PORT, DEFAULT_GATEWAY_URL_PORT));
    return String.format(Locale.ROOT, GATEWAY_URL_TEMPLATE, protocol, host, port);
  }

  public String getGatewayJmxUrl() {
    final String host = configuration.getProperty(PARAM_GATEWAY_URL_HOST, DEFAULT_GATEWAY_URL_HOST);
    final int port = Integer.parseInt(configuration.getProperty(PARAM_GATEWAY_JMX_PORT, DEFAULT_GATEWAY_JMX_PORT));
    return String.format(Locale.ROOT, GATEWAY_JMX_URL_TEMPLATE, host, port);
  }

  public String getGatewayPath() {
    return configuration.getProperty(PARAM_GATEWAY_PATH, DEFAULT_GATEWAY_PATH);
  }

  public String getGatewayUser() {
    return configuration.getProperty(PARAM_GATEWAY_USER, DEFAULT_GATEWAY_USER);
  }

  public String getGatewayPassword() {
    return configuration.getProperty(PARAM_GATEWAY_PW, DEFAULT_GATEWAY_PW);
  }

  /* Reporting */

  public long getReportGenerationPeriod() {
    return Long.parseLong(configuration.getProperty(PARAM_REPORT_GENERATION_PERIOD, DEFAULT_REPORT_GENERATION_PERIOD));
  }

  public String getReportingTargetFolder() {
    final String targetFolder = System.getProperty(PARAM_REPORT_GENERATION_TARGET_FOLDER);
    return StringUtils.isBlank(targetFolder) ? configuration.getProperty(PARAM_REPORT_GENERATION_TARGET_FOLDER) : targetFolder;
  }

  public boolean isReportingEngineEnabled(String engineType) {
    return Boolean.parseBoolean(configuration.getProperty(REPORT_GENERATION_PREFIX + engineType + PARAM_ENABLED_POSTFIX, "false"));
  }

  /* Use case */

  public boolean isUseCaseEnabled(String useCase) {
    return Boolean.parseBoolean(configuration.getProperty(USE_CASE_PREFIX + useCase + PARAM_ENABLED_POSTFIX, "false"));
  }

  public String getUseCaseTopology(String useCase, String topologyType) {
    return configuration.getProperty(USE_CASE_PREFIX + useCase + PARAM_USE_CASE_TOPOLOGY_POSTFIX + topologyType, defaultUseCaseMap.get(useCase).get(topologyType));
  }

  public String getUseCaseUrl(String useCase, String topologyType) {
    return getGatewayUrl() + URL_PATH_SEPARATOR + getGatewayPath() + URL_PATH_SEPARATOR + getUseCaseTopology(useCase, topologyType);
  }

  public String getUseCaseParam(String useCase, String param) {
    return configuration.getProperty(USE_CASE_PREFIX + useCase + "." + param);
  }

}
