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
package org.apache.knox.gateway.topology.discovery.cm.collector;

import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;
import org.apache.knox.gateway.topology.discovery.cm.model.hue.HueLBServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.hue.HueServiceModelGenerator;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HueURLCollectorTest extends AbstractURLCollectorTest {

  @Test
  public void testNoServiceModels() {
    HueURLCollector collector = new HueURLCollector();
    List<String> urls = collector.collect(Collections.emptyMap());
    assertNotNull(urls);
    assertEquals(0, urls.size());
  }

  @Test
  public void testSingleServiceModel() {
    Map<String, List<ServiceModel>> testRoleModels = new HashMap<>();

    testRoleModels.put(HueServiceModelGenerator.SERVICE,
                       Collections.singletonList(createMockServiceModel(HueServiceModelGenerator.SERVICE,
                                                                        HueServiceModelGenerator.SERVICE_TYPE,
                                                                        HueServiceModelGenerator.ROLE_TYPE,
                                                                        "http://hostx:1234/test1")));

    HueURLCollector collector = new HueURLCollector();
    List<String> urls = collector.collect(testRoleModels);
    assertEquals("Expected only the single URL.", 1, urls.size());
    assertTrue(urls.contains("http://hostx:1234/test1"));
  }

  @Test
  public void testMultipleServiceModels() {
    Map<String, List<ServiceModel>> testRoleModels = new HashMap<>();

    testRoleModels.put(HueServiceModelGenerator.SERVICE,
                       Arrays.asList(createMockServiceModel(HueServiceModelGenerator.SERVICE,
                                                            HueServiceModelGenerator.SERVICE_TYPE,
                                                            HueServiceModelGenerator.ROLE_TYPE,
                                                            "http://hostx:1234/test1"),
                                     createMockServiceModel(HueServiceModelGenerator.SERVICE,
                                                            HueServiceModelGenerator.SERVICE_TYPE,
                                                            HueServiceModelGenerator.ROLE_TYPE,
                                                            "http://hostx:1234/test2"),
                                     createMockServiceModel(HueServiceModelGenerator.SERVICE,
                                                            HueServiceModelGenerator.SERVICE_TYPE,
                                                            HueServiceModelGenerator.ROLE_TYPE,
                                                            "http://hostx:1234/test3")));

    HueURLCollector collector = new HueURLCollector();
    List<String> urls = collector.collect(testRoleModels);
    assertEquals("Expected all the URLs.", 3, urls.size());
    assertTrue(urls.contains("http://hostx:1234/test1"));
    assertTrue(urls.contains("http://hostx:1234/test2"));
    assertTrue(urls.contains("http://hostx:1234/test3"));
  }

  @Test
  public void testMultipleServiceModelsWithHueLB() {

    Map<String, List<ServiceModel>> testRoleModels = new HashMap<>();

    testRoleModels.put(HueServiceModelGenerator.ROLE_TYPE,
                       Arrays.asList(createMockServiceModel(HueServiceModelGenerator.SERVICE,
                                                            HueServiceModelGenerator.SERVICE_TYPE,
                                                            HueServiceModelGenerator.ROLE_TYPE,
                                                            "http://hostx:1234/test1"),
                                     createMockServiceModel(HueServiceModelGenerator.SERVICE,
                                                            HueServiceModelGenerator.SERVICE_TYPE,
                                                            HueServiceModelGenerator.ROLE_TYPE,
                                                            "http://hostx:1234/test2")));


    testRoleModels.put(HueLBServiceModelGenerator.ROLE_TYPE,
                       Collections.singletonList(createMockServiceModel(HueLBServiceModelGenerator.SERVICE,
                                                                        HueLBServiceModelGenerator.SERVICE_TYPE,
                                                                        HueLBServiceModelGenerator.ROLE_TYPE,
                                                                        "http://hostx:1234/lb")));

    HueURLCollector collector = new HueURLCollector();
    List<String> urls = collector.collect(testRoleModels);
    assertEquals("Expected only the load-balancer URL.", 1, urls.size());
    assertEquals("Expected the load-balancer URL.", "http://hostx:1234/lb", urls.get(0));
  }

}
