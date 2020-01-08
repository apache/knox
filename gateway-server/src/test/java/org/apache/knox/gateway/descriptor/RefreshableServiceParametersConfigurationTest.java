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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Paths;

import org.apache.knox.gateway.descriptor.RefreshableServiceParametersConfiguration.TopologyServiceParameters;
import org.junit.Test;

public class RefreshableServiceParametersConfigurationTest {

  @Test
  public void testRefreshableServiceParametersConfiguration() throws Exception {
    final String testConfigPath = this.getClass().getClassLoader().getResource("refreshableServiceParams/refreshable-service-parameters.xml").getPath();
    final RefreshableServiceParametersConfiguration serviceParametersConfiguration = new RefreshableServiceParametersConfiguration(Paths.get(testConfigPath));

    assertEquals(2, serviceParametersConfiguration.getTopologies().size());
    assertTrue(serviceParametersConfiguration.getTopologies().contains("topology1"));
    assertTrue(serviceParametersConfiguration.getTopologies().contains("topology2"));

    TopologyServiceParameters topologyServiceParameters = serviceParametersConfiguration.getServiceParameters("topology1");
    assertEquals(1, topologyServiceParameters.getServiceParameters().size());
    assertServiceParameters(topologyServiceParameters, "NIFI-REGISTRY", "useTwoWaySsl", "true");

    topologyServiceParameters = serviceParametersConfiguration.getServiceParameters("topology2");
    assertEquals(2, topologyServiceParameters.getServiceParameters().size());
    assertServiceParameters(topologyServiceParameters, "HIVE", "httpclient.connectionTimeout", "5m");
    assertServiceParameters(topologyServiceParameters, "HIVE", "httpclient.socketTimeout", "200m");
    assertServiceParameters(topologyServiceParameters, "HUE", "httpclient.connectionTimeout", "5m");
  }

  private void assertServiceParameters(TopologyServiceParameters topologyServiceParameters, String expectedServiceName, String expectedParameterName,
      String expectedParameterValue) {
    assertTrue(topologyServiceParameters.getServiceParameters().containsKey(expectedServiceName));
    assertTrue(topologyServiceParameters.getServiceParameters().get(expectedServiceName).containsKey(expectedParameterName));
    assertTrue(topologyServiceParameters.getServiceParameters().get(expectedServiceName).get(expectedParameterName).equals(expectedParameterValue));
  }
}
