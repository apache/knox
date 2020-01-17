/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.discovery.advanced;

import static java.util.stream.Collectors.toSet;

import java.util.Locale;
import java.util.Map.Entry;
import java.util.stream.Stream;
import java.util.Properties;
import java.util.Set;

/**
 * Wrapper class providing useful methods on properties coming from
 * <code>$KNOX_CONF_DIR/auto-discovery-advanced-configuration.properties</code>
 */
public class AdvancedServiceDiscoveryConfig {

  public static final String PARAMETER_NAME_PREFIX_ENABLED_SERVICE = "gateway.auto.discovery.enabled.";
  public static final String PARAMETER_NAME_EXPECTED_TOPOLOGIES = "gateway.auto.discovery.expected.topology.names";

  private final Properties properties;

  public AdvancedServiceDiscoveryConfig() {
    this(null);
  }

  public AdvancedServiceDiscoveryConfig(Properties properties) {
    this.properties = properties == null ? new Properties() : properties;
  }

  public boolean isServiceEnabled(String serviceName) {
    return Boolean.valueOf(getPropertyIgnoreCase(PARAMETER_NAME_PREFIX_ENABLED_SERVICE + serviceName, "true"));
  }

  public Set<String> getEnabledServiceNames() {
    return properties.entrySet().stream().filter(keyValuePair -> Boolean.valueOf((String) keyValuePair.getValue()))
        .map(keyValuePair -> ((String) keyValuePair.getKey()).substring(PARAMETER_NAME_PREFIX_ENABLED_SERVICE.length()).toUpperCase(Locale.getDefault())).collect(toSet());
  }

  public Set<String> getExpectedTopologyNames() {
    return Stream.of(properties.getProperty(PARAMETER_NAME_EXPECTED_TOPOLOGIES, "").split(",")).map(expectedToplogyName -> expectedToplogyName.trim()).collect(toSet());
  }

  private String getPropertyIgnoreCase(String propertyName, String defaultValue) {
    final String property = properties.getProperty(propertyName);
    if (property != null) {
      return property;
    } else {
      for (Entry<Object, Object> entry : properties.entrySet()) {
        if (propertyName.equalsIgnoreCase((String) entry.getKey())) {
          return (String) entry.getValue();
        }
      }
      return defaultValue;
    }
  }
}
