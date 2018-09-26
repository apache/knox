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
package org.apache.knox.gateway.util;

import org.apache.knox.gateway.deploy.ServiceDeploymentContributor;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class ServiceDefinitionsLoaderTest {

  @Test
  public void testServiceDefinitionLoading() {
    URL url = ClassLoader.getSystemResource("services");
    Set<ServiceDeploymentContributor> contributors = ServiceDefinitionsLoader.loadServiceDefinitions(new File(url.getFile()));
    assertNotNull(contributors);
    assertEquals(2, contributors.size());
    for (ServiceDeploymentContributor contributor : contributors) {
      if (contributor.getName().equals("foo")) {
        Assert.assertEquals("1.0.0", contributor.getVersion().toString());
        assertEquals("FOO", contributor.getRole());
      } else if (contributor.getName().equals("bar")) {
        Assert.assertEquals("2.0.0", contributor.getVersion().toString());
        assertEquals("BAR", contributor.getRole());
      } else {
        fail("the loaded services don't match the test input");
      }
    }
  }
}
