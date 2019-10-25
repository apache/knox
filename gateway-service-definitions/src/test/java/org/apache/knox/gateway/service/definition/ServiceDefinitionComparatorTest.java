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
package org.apache.knox.gateway.service.definition;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ServiceDefinitionComparatorTest {

  private final ServiceDefinitionComparator serviceDefinitionComparator = new ServiceDefinitionComparator();

  @Test
  public void shouldCompareByName() throws Exception {
    assertEquals(-1, serviceDefinitionComparator.compare(buildServiceDefinition("service1", "role1", "1.0"), buildServiceDefinition("service2", "role1", "1.0")));
  }

  @Test
  public void shouldCompareByRole() throws Exception {
    assertEquals(-1, serviceDefinitionComparator.compare(buildServiceDefinition("service1", "role1", "1.0"), buildServiceDefinition("service1", "role2", "1.0")));
  }

  @Test
  public void shouldCompareByVersion() throws Exception {
    assertEquals(-1, serviceDefinitionComparator.compare(buildServiceDefinition("service1", "role1", "1.0"), buildServiceDefinition("service1", "role1", "2.0")));
  }

  @Test
  public void shouldReturnZeroIfAllEquals() throws Exception {
    assertEquals(0, serviceDefinitionComparator.compare(buildServiceDefinition("service1", "role1", "1.0"), buildServiceDefinition("service1", "role1", "1.0")));
  }

  private ServiceDefinition buildServiceDefinition(String name, String role, String version) {
    final ServiceDefinition serviceDefinition = new ServiceDefinition();
    serviceDefinition.setName(name);
    serviceDefinition.setRole(role);
    serviceDefinition.setVersion(version);
    return serviceDefinition;
  }

}
