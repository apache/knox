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
package org.apache.knox.gateway.descriptor;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.hadoop.conf.Configuration;
import org.apache.knox.gateway.config.GatewayConfig;

/**
 * Parses <code>refreshable-service-parameters.xml</code> within the pre-configured
 * {@link GatewayConfig#getRefreshableServiceParametersFolder} (if any) and provides useful methods to get topologies and service parameters
 */
public class RefreshableServiceParametersConfiguration {
  private static final String CONFIGURATION_NAME = "refreshable.service.parameters";
  private final Set<TopologyServiceParameters> topologyServiceParameters = new HashSet<>();

  public RefreshableServiceParametersConfiguration(Path resourcePath) throws MalformedURLException {
    final Configuration configuration = new Configuration();
    configuration.addResource(resourcePath.toUri().toURL());
    configuration.reloadConfiguration();
    parseConfig(configuration);
  }

  // the configuration value equals to BLOCK_1;BLOCK_2;...BLOCK_N,
  // where BLOCK_* is built up as $TOPOLOGY:$SERVICE:$VERSION:$PARMAPETER_NAME=$PARAMETER_VALUE
  // For instance: test-topology.HDFS.test.pramameter=test.value
  private void parseConfig(final Configuration configuration) {
    final String parametersText = configuration.get(CONFIGURATION_NAME);
    if (parametersText != null && !parametersText.isEmpty()) {
      for (String topologyServiceParameterConfig : parametersText.split(";")) {
        String[] parameterPairParts = topologyServiceParameterConfig.trim().split("=", 2);
        String[] serviceParamIdentifier = parameterPairParts[0].trim().split(":");
        String topology = serviceParamIdentifier[0].trim();
        if (getServiceParameters(topology) == null) {
          topologyServiceParameters.add(new TopologyServiceParameters(topology));
        }

        String serviceName = serviceParamIdentifier[1].trim();
        String version = serviceParamIdentifier[2].trim();
        String parameterName = serviceParamIdentifier[3].trim();
        String parameterValue = parameterPairParts[1].trim();
        getServiceParameters(topology).addServiceParameter(serviceName, version, parameterName, parameterValue);
      }
    }
  }

  public Set<String> getTopologies() {
    return topologyServiceParameters.stream().map(topologyServiceParameter -> topologyServiceParameter.getTopology()).collect(Collectors.toSet());
  }

  public TopologyServiceParameters getServiceParameters(String topologyName) {
    return topologyServiceParameters.stream().filter(topologyServiceParameters -> topologyServiceParameters.getTopology().equals(topologyName)).findFirst().orElse(null);
  }

  public class TopologyServiceParameters {
    private String topology;
    private Map<ServiceParametersKey, Map<String, String>> serviceParameters;

    TopologyServiceParameters(String topology) {
      this.topology = topology;
      this.serviceParameters = new TreeMap<>();
    }

    public String getTopology() {
      return topology;
    }

    void addServiceParameter(String serviceName, String version, String parameterName, String parameterValue) {
      serviceParameters.computeIfAbsent(new ServiceParametersKey(serviceName, version), k -> new TreeMap<>()).put(parameterName, parameterValue);
    }

    public Map<ServiceParametersKey, Map<String, String>> getServiceParameters() {
      return serviceParameters;
    }

    public Map<String, String> getServiceParameters(String serviceName, String version) {
      final Map<String, String> serviceParametersByServiceAndVersion = new TreeMap<>();
      getServiceParameters().forEach((key, value) -> {
        //descriptors created on Admin UI comes with no version defined; in this case only the service name should matter
        if (key.getName().equalsIgnoreCase(serviceName) && (StringUtils.isBlank(version) || key.getVersion().equals(version))) {
          serviceParametersByServiceAndVersion.putAll(value);
        }
      });
      return serviceParametersByServiceAndVersion;
    }

    @Override
    public boolean equals(Object obj) {
      return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
      return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
      return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
  }

  public class ServiceParametersKey implements Comparable<ServiceParametersKey> {
    private final String name;
    private final String version;

    ServiceParametersKey(String name, String version) {
      this.name = name;
      this.version = version;
    }

    public String getName() {
      return name;
    }

    public String getVersion() {
      return version;
    }

    @Override
    public boolean equals(Object obj) {
      return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
      return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
      return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

    @Override
    public int compareTo(ServiceParametersKey other) {
      final int byName = other.getName().compareTo(name);
      return byName == 0 ? other.getVersion().compareTo(version) : byName;
    }
  }

}