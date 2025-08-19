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

public class IcebergRestServiceModelGeneratorTest extends AbstractServiceModelGeneratorTest {

  @Test
  @Override
  public void testHandles() {
    final Map<String, String> serviceConfig = new HashMap<>();
    serviceConfig.put(IcebergRestServiceModelGenerator.REST_CATALOG_ENABLED, "true");

    assertTrue(doTestHandles(newGenerator(), getServiceType(), serviceConfig, getRoleType(), Collections.emptyMap()));
  }

  @Test
  public void testHandlesWhenEnabledIsFalse() {
    final Map<String, String> serviceConfig = new HashMap<>();
    serviceConfig.put(IcebergRestServiceModelGenerator.REST_CATALOG_ENABLED, "false");

    assertFalse(doTestHandles(newGenerator(), getServiceType(), serviceConfig, getRoleType(), Collections.emptyMap()));
  }


  @Test
  public void testServiceModel() {
    final Map<String, String> serviceConfig = new HashMap<>();
    serviceConfig.put("hive_metastore_catalog_servlet_port", "8090");
    serviceConfig.put("hive_metastore_catalog_servlet_path", "icecli");
    serviceConfig.put("hive_rest_catalog_enabled", "true");

    final Map<String, String> roleConfig = Collections.emptyMap();

    validateServiceModel(createServiceModel(serviceConfig, roleConfig), serviceConfig, roleConfig);
  }

  @Override
  protected String getServiceType() {
    return IcebergRestServiceModelGenerator.SERVICE_TYPE;
  }

  @Override
  protected String getRoleType() {
    return IcebergRestServiceModelGenerator.ROLE_TYPE;
  }

  @Override
  protected ServiceModelGenerator newGenerator() {
    return new IcebergRestServiceModelGenerator();
  }

}
