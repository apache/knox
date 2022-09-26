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
package org.apache.knox.gateway.topology.discovery.cm.model.hive;

import org.apache.knox.gateway.topology.discovery.cm.ServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.AbstractServiceModelGeneratorTest;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HiveServiceModelGeneratorTest extends AbstractServiceModelGeneratorTest {

  @Test
  @Override
  public void testHandles() {
    Map<String, String> roleConfig = new HashMap<>();
    roleConfig.put(HiveServiceModelGenerator.SAFETY_VALVE,
                   getSafetyValveConfig(HiveServiceModelGenerator.TRANSPORT_MODE_HTTP));
    assertTrue(doTestHandles(newGenerator(), getServiceType(), Collections.emptyMap(), getRoleType(), roleConfig));
  }

  @Test
  public void testHandlesTransportModeAll() {
    Map<String, String> roleConfig = new HashMap<>();
    roleConfig.put(HiveServiceModelGenerator.SAFETY_VALVE,
                   getSafetyValveConfig(HiveServiceModelGenerator.TRANSPORT_MODE_ALL));
    assertTrue(doTestHandles(newGenerator(), getServiceType(), Collections.emptyMap(), getRoleType(), roleConfig));
  }

  @Test
  public void testHandlesWhenTransportModeIsBinary() {
    Map<String, String> roleConfig = new HashMap<>();
    roleConfig.put(HiveServiceModelGenerator.SAFETY_VALVE, getSafetyValveConfig("binary"));
    assertFalse(doTestHandles(newGenerator(), getServiceType(), Collections.emptyMap(), getRoleType(), roleConfig));
  }

  @Test
  public void testServiceModelMetadata() {
    final Map<String, String> serviceConfig = new HashMap<>();
    serviceConfig.put(HiveServiceModelGenerator.SSL_ENABLED, "false");

    final Map<String, String> roleConfig = new HashMap<>();
    roleConfig.put(HiveServiceModelGenerator.SAFETY_VALVE,
                   getSafetyValveConfig(HiveServiceModelGenerator.TRANSPORT_MODE_HTTP));

    validateServiceModel(createServiceModel(serviceConfig, roleConfig), serviceConfig, roleConfig);
  }

  @Test
  public void testServiceModelMetadataTransportModeAll() {
    final Map<String, String> serviceConfig = new HashMap<>();
    serviceConfig.put(HiveServiceModelGenerator.SSL_ENABLED, "false");

    final Map<String, String> roleConfig = new HashMap<>();
    roleConfig.put(HiveServiceModelGenerator.SAFETY_VALVE,
                   getSafetyValveConfig(HiveServiceModelGenerator.TRANSPORT_MODE_ALL));

    validateServiceModel(createServiceModel(serviceConfig, roleConfig), serviceConfig, roleConfig);
  }

  @Override
  protected String getServiceType() {
    return HiveServiceModelGenerator.SERVICE_TYPE;
  }

  @Override
  protected String getRoleType() {
    return HiveServiceModelGenerator.ROLE_TYPE;
  }

  @Override
  protected ServiceModelGenerator newGenerator() {
    return new HiveServiceModelGenerator();
  }

  private String getSafetyValveConfig(final String transportMode){
    return "<property><name>" + HiveServiceModelGenerator.HTTP_PORT + "</name><value>12345</value></property>" +
           "<property><name>" + HiveServiceModelGenerator.HTTP_PATH + "</name><value>cliService</value></property>" +
           "<property><name>" + HiveServiceModelGenerator.TRANSPORT_MODE + "</name><value>" + transportMode + "</value></property>";
  }
}
