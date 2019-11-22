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
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.knox.test.TestUtils.LOG_ENTER;
import static org.apache.knox.test.TestUtils.LOG_EXIT;

/**
 * Test that the Gateway Topology Port Mapping feature is disabled properly.
 *
 */
@Category(ReleaseTest.class)
public class GatewayPortMappingDisableFeatureTest extends PortMappingHelper {

  @Rule
  public ExpectedException exception = ExpectedException.none();

  public GatewayPortMappingDisableFeatureTest() {
    super();
  }

  @Before
  public void setup() throws Exception {
    eeriePort = getAvailablePort(1240, 49151);
    ConcurrentHashMap<String, Integer> topologyPortMapping = new ConcurrentHashMap<>();
    topologyPortMapping.put("eerie", eeriePort);
    /* define port mappings but feature disabled */
    init(null, topologyPortMapping, false);
  }

  @After
  public void cleanup() throws Exception {
    LOG_ENTER();
    driver.cleanup();
    driver.reset();
    masterServer.reset();
    LOG_EXIT();
  }

  /*
   * Test the standard case
   */
  @Test(timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testBasicListOperation() throws IOException {
    test(driver.getUrl("WEBHDFS") );
  }

  /*
   * Test the multi port fail scenario when the feature is disabled.
   */
  @Test(timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testMultiPortFailOperation() throws IOException {
    exception.expect(ConnectException.class);
    exception.expectMessage("Connection refused");
    test("http://localhost:" + eeriePort + "/webhdfs" );
  }
}
