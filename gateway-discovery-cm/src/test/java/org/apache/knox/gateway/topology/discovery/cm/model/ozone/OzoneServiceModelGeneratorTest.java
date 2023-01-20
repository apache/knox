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
package org.apache.knox.gateway.topology.discovery.cm.model.ozone;

import org.apache.knox.gateway.topology.discovery.cm.ServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.AbstractServiceModelGeneratorTest;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OzoneServiceModelGeneratorTest extends AbstractServiceModelGeneratorTest {

  @Test
  public void testServiceModelMetadata() {
    final Map<String, String> serviceConfig = Collections.emptyMap();
    final Map<String, String> roleConfig = new HashMap<>();
    roleConfig.put(OzoneServiceModelGenerator.SSL_ENABLED, "true");
    roleConfig.put(OzoneServiceModelGenerator.HTTP_PORT, "9874");
    roleConfig.put(OzoneServiceModelGenerator.HTTPS_PORT, "9875");
    validateServiceModel(createServiceModel(serviceConfig, roleConfig), serviceConfig, roleConfig);
  }

  @Override
  protected String getServiceType() {
    return OzoneServiceModelGenerator.SERVICE_TYPE;
  }

  @Override
  protected String getRoleType() {
    return OzoneServiceModelGenerator.ROLE_TYPE;
  }

  @Override
  protected ServiceModelGenerator newGenerator() {
    return new OzoneServiceModelGenerator();
  }

}
