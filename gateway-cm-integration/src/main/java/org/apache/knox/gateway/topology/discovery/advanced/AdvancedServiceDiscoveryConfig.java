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
import java.util.Properties;
import java.util.Set;

/**
 * Wrapper class providing useful methods on properties coming from
 * <code>$KNOX_CONF_DIR/auto-discovery-advanced-configuration.properties</code>
 */
public class AdvancedServiceDiscoveryConfig {

  public static final String PARAMETER_NAME_PREFIX_ENABLED_SERVICE = "gateway.auto.discovery.";
  public static final String PARAMETER_NAME_POSTFIX_ENABLED_SERVICE = ".enabled.";
  public static final String PARAMETER_NAME_TOPOLOGY_NAME = "gateway.auto.discovery.topology.name";
  public static final String PARAMETER_NAME_DISCOVERY_ADDRESS = "gateway.auto.discovery.address";
  public static final String PARAMETER_NAME_DISCOVERY_CLUSTER = "gateway.auto.discovery.cluster";

  private final Properties properties;

  public AdvancedServiceDiscoveryConfig() {
    this(null);
  }

  public AdvancedServiceDiscoveryConfig(Properties properties) {
    this.properties = properties == null ? new Properties() : properties;
  }

  public boolean isServiceEnabled(String serviceName) {
    final String propertyName = PARAMETER_NAME_PREFIX_ENABLED_SERVICE + getTopologyName() + PARAMETER_NAME_POSTFIX_ENABLED_SERVICE + serviceName;
    return Boolean.valueOf(getPropertyIgnoreCase(propertyName, "true"));
  }

  public Set<String> getEnabledServiceNames() {
    return properties.entrySet().stream().filter(keyValuePair -> Boolean.valueOf((String) keyValuePair.getValue()))
        .map(keyValuePair -> ((String) keyValuePair.getKey()).substring(((String) keyValuePair.getKey()).lastIndexOf('.') + 1).toUpperCase(Locale.getDefault())).collect(toSet());
  }

  public String getTopologyName() {
    return getPropertyIgnoreCase(PARAMETER_NAME_TOPOLOGY_NAME, "");
  }

  public String getDiscoveryAddress() {
    return getPropertyIgnoreCase(PARAMETER_NAME_DISCOVERY_ADDRESS, "");
  }

  public String getDiscoveryCluster() {
    return getPropertyIgnoreCase(PARAMETER_NAME_DISCOVERY_CLUSTER, "");
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

  @Override
  public String toString() {
    return this.properties.toString();
  }
}
