/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.discovery.ambari;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class YarnUIURLCreatorTest extends RMURLCreatorTestBase {

  @Override
  String getTargetService() {
    return "YARNUI";
  }


  @Override
  ServiceURLCreator getServiceURLCreator(AmbariCluster cluster) {
    ServiceURLCreator creator = new YarnUIURLCreator();
    creator.init(cluster);
    return creator;
  }


  @Test
  public void testCreateHttpURLs() throws Exception {
    final String expectedAddress = "test.host.unsecure:8088";
    String url = doTestCreateSingleURL("HTTP_ONLY", expectedAddress, "test.host.secure:8088");
    assertEquals(ResourceManagerURLCreatorBase.SCHEME_HTTP + "://" + expectedAddress, url);
  }


  @Test
  public void testCreateHAHttpURLs() throws Exception {
    final String activeHttpAddress = "test.host.unsecure.active:8088";
    final String stdbyHttpAddress  = "test.host.unsecure.stdby:8088";
    List<String> urls = doTestCreateHAURLs("HTTP_ONLY",
                                           activeHttpAddress,
                                           stdbyHttpAddress,
                                           "test.host.secure.active:8088",
                                           "test.host.secure.stdby:8088");
    assertTrue(urls.contains(ResourceManagerURLCreatorBase.SCHEME_HTTP + "://" + activeHttpAddress));
    assertTrue(urls.contains(ResourceManagerURLCreatorBase.SCHEME_HTTP + "://" + stdbyHttpAddress));
  }


  @Test
  public void testCreateHttpsURLs() throws Exception {
    final String expectedAddress = "test.host.secure:8088";
    String url = doTestCreateSingleURL("HTTPS_ONLY", "test.host.unsecure:8088", expectedAddress);
    assertEquals(ResourceManagerURLCreatorBase.SCHEME_HTTPS + "://" + expectedAddress, url);
  }


  @Test
  public void testCreateHAHttpsURLs() throws Exception {
    final String activeHttpsAddress = "test.host.secure.active:8088";
    final String stdbyHttpsAddress  = "test.host.secure.stdby:8088";
    List<String> urls = doTestCreateHAURLs("HTTPS_ONLY",
                                           "test.host.unsecure.active:8088",
                                           "test.host.unsecure.stdby:8088",
                                           stdbyHttpsAddress,
                                           activeHttpsAddress);
    assertTrue(urls.contains(ResourceManagerURLCreatorBase.SCHEME_HTTPS + "://" + activeHttpsAddress));
    assertTrue(urls.contains(ResourceManagerURLCreatorBase.SCHEME_HTTPS + "://" + stdbyHttpsAddress));
  }


}
