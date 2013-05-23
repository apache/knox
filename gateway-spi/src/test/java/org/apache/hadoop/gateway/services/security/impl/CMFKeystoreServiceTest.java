/**
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
package org.apache.hadoop.gateway.services.security.impl;

import java.io.File;
import java.util.Map;

import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.hadoop.gateway.services.security.KeystoreServiceException;
import org.apache.hadoop.gateway.services.security.MasterService;
import org.apache.hadoop.test.category.FastTests;
import org.apache.hadoop.test.category.UnitTests;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
*
*/
@Category( { UnitTests.class, FastTests.class } )
public class CMFKeystoreServiceTest {
  CMFKeystoreService ks;

  @Before
  public void setup() {
    try {
      ks = new CMFKeystoreService(".", "ambari");
      ks.setMasterService(new MasterService() {

        public void init(GatewayConfig config, Map<String, String> options)
            throws ServiceLifecycleException {
          // TODO Auto-generated method stub
          
        }

        public void start() throws ServiceLifecycleException {
          // TODO Auto-generated method stub
          
        }

        public void stop() throws ServiceLifecycleException {
          // TODO Auto-generated method stub
          
        }

        public char[] getMasterSecret() {
          // TODO Auto-generated method stub
          return "testmaster".toCharArray();
        }
        
      });
    } catch (ServiceLifecycleException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  
  @Test
  public void testCredentialStore() {
    try {
      ks.createKeystore();
      assertTrue(ks.isKeystoreAvailable());
      ks.createCredentialStore();
      assertTrue(ks.isCredentialStoreAvailable());
      ks.addCredential("aliasName", "secretValue");
      char[] secret = ks.getCredential("aliasName");
      assertTrue(new String(secret).equals("secretValue"));
      ks.addCredential("encrypt_url", "sdkgfksdgfjkhsdjkfhb");
      File file = new File("ambari-credentials.jceks");
      assertTrue(file.exists());
      file.delete();
      file = new File("ambari.jks");
      assertTrue(file.exists());
      file.delete();
    } catch (KeystoreServiceException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      fail();
    }
  }
}