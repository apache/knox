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
package org.apache.knox.gateway;

import org.apache.knox.test.TestUtils;
import org.apache.knox.test.category.ReleaseTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.knox.test.TestUtils.LOG_ENTER;
import static org.apache.knox.test.TestUtils.LOG_EXIT;

/**
 * Test the Gateway Topology Port Mapping functionality
 *
 */
@Category(ReleaseTest.class)
public class GatewayPortMappingFuncTest extends PortMappingHelper {

  public GatewayPortMappingFuncTest() {
    super();
  }

  @BeforeClass
  public static void setup() throws Exception {
    eeriePort = getAvailablePort(1240, 49151);
    ConcurrentHashMap<String, Integer> topologyPortMapping = new ConcurrentHashMap<>();
    topologyPortMapping.put("eerie", eeriePort);
    init(null, topologyPortMapping);
  }

  @AfterClass
  public static void cleanup() throws Exception {
    LOG_ENTER();
    driver.cleanup();
    driver.reset();
    masterServer.reset();
    LOG_EXIT();
  }

  /*
   * Test the standard case:
   * http://localhost:{topologyPort}/gateway/eerie/webhdfs/v1
   */
  @Test(timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testBasicListOperation() throws IOException {
    LOG_ENTER();
    test("http://localhost:" + eeriePort + "/gateway/eerie" + "/webhdfs" );
    LOG_EXIT();
  }

  /*
   * Test the multi port scenario.
   *
   * http://localhost:{eeriePort}/webhdfs/v1
   */
  @Test(timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testMultiPortOperation() throws IOException {
    LOG_ENTER();
    test("http://localhost:" + eeriePort + "/webhdfs" );
    LOG_EXIT();
  }

  /*
   * Test the multi port scenario when gateway path is included.
   *
   * http://localhost:{eeriePort}/gateway/eerie/webhdfs/v1
   */
  @Test(timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testMultiPortWithGatewayPath() throws IOException {
    LOG_ENTER();
    test("http://localhost:" + eeriePort + "/gateway/eerie" + "/webhdfs" );
    LOG_EXIT();
  }

}
