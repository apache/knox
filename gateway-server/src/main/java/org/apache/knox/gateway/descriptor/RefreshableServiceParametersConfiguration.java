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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
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
  // where BLOCK_* is built up as $TOPOLOGY.$SERVICE.$PARMAPETER_NAME=$PARAMETER_VALUE
  // For instance: test-topology.HDFS.test.pramameter=test.value
  private void parseConfig(final Configuration configuration) {
    final String parametersText = configuration.get(CONFIGURATION_NAME);
    if (parametersText != null && !parametersText.isEmpty()) {
      for (String topologyServiceParameterConfig : parametersText.split(";")) {
        String[] parameterPairParts = topologyServiceParameterConfig.trim().split("=", 2);
        String[] topologyAndServiceAndParameterNames = parameterPairParts[0].trim().split("\\.");
        String topology = topologyAndServiceAndParameterNames[0].trim();
        if (getServiceParameters(topology) == null) {
          topologyServiceParameters.add(new TopologyServiceParameters(topology));
        }

        String serviceName = topologyAndServiceAndParameterNames[1].trim();
        String parameterName = parameterPairParts[0].substring(topology.length() + serviceName.length() + 2).trim();
        String parameterValue = parameterPairParts[1].trim();
        getServiceParameters(topology).addServiceParameter(serviceName, parameterName, parameterValue);
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
    private Map<String, Map<String, String>> serviceParameters;

    TopologyServiceParameters(String topology) {
      this.topology = topology;
      this.serviceParameters = new TreeMap<>();
    }

    public String getTopology() {
      return topology;
    }

    void addServiceParameter(String serviceName, String parameterName, String parameterValue) {
      serviceParameters.computeIfAbsent(serviceName, k -> new TreeMap<>()).put(parameterName, parameterValue);
    }

    public Map<String, Map<String, String>> getServiceParameters() {
      return serviceParameters;
    }

    @Override
    public boolean equals(Object obj) {
      return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
      return HashCodeBuilder.reflectionHashCode(this);
    }
  }

}