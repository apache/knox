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
package org.apache.knox.gateway.ha.provider.impl;

import org.apache.knox.gateway.ha.provider.URLManager;
import org.apache.knox.gateway.ha.provider.URLManagerLoader;
import org.junit.Assert;
import org.junit.Test;

public class URLManagerLoaderTest {

  @Test
  public void testURLManagerLoader() {
    DefaultHaServiceConfig serviceConfig = new DefaultHaServiceConfig("mock-test");
    URLManager manager = URLManagerLoader.loadURLManager(serviceConfig);
    Assert.assertNotNull(manager);
    Assert.assertTrue(manager instanceof MockURLManager);
    Assert.assertNotNull(((MockURLManager) manager).getConfig());
    Assert.assertEquals("mock-test", ((MockURLManager) manager).getConfig().getServiceName());
  }

  @Test
  public void testDefaultURLManager() {
    DefaultHaServiceConfig serviceConfig = new DefaultHaServiceConfig("nothing like this exists");
    URLManager manager = URLManagerLoader.loadURLManager(serviceConfig);
    Assert.assertNotNull(manager);
    Assert.assertTrue(manager instanceof DefaultURLManager);
    manager = URLManagerLoader.loadURLManager(null);
    Assert.assertNotNull(manager);
    Assert.assertTrue(manager instanceof DefaultURLManager);
  }

}
