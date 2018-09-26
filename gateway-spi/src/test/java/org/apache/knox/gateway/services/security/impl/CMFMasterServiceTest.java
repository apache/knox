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
package org.apache.knox.gateway.services.security.impl;

import java.io.File;

import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.test.category.FastTests;
import org.apache.knox.test.category.UnitTests;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
*
*/
@Category( { UnitTests.class, FastTests.class } )
public class CMFMasterServiceTest {
  TestCMFMasterService ms = null;

  @Before
  public void setup() {
    ms = new TestCMFMasterService("ambari");
  }
  
  @Test
  public void testMasterService() {
    try {
      ms.setupMasterSecret(".", true);
      //System.out.println("MASTER: " + new String(ms.getMasterSecret()));
      assertTrue(new String(ms.getMasterSecret()).equals("testmastersecret"));
      File file = new File("ambari-master");
      assertTrue(file.exists());
      file.delete();
    } catch (ServiceLifecycleException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      fail();
    }
  }
}