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
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Map;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.test.category.FastTests;
import org.apache.knox.test.category.UnitTests;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertTrue;

/**
*
*/
@Category( { UnitTests.class, FastTests.class } )
public class CMFKeystoreServiceTest {
  CMFKeystoreService ks;
  String aliasName = "TestAliasName";
  String secretValue = "AliasSecretValue";
  char[] password = { 'P', 'A', 'S', 'S' };
  File credentialsStoreFile = new File("ambari-credentials.jceks");
  File keyStoreFile = new File("ambari.jks");
  File certificateFile = new File("ambari");

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
  public void testCreationOfStoreForCredential() throws KeystoreServiceException {
    try {
      ks.createCredentialStore();
      assertTrue("Credential Store file is not created", ks.isCredentialStoreAvailable()
          && credentialsStoreFile.exists());
      KeyStore credentialStore = ks.getCredentialStore();
      assertTrue("Credential Store file is not created with proper file type",
        ("JCEKS").equalsIgnoreCase(credentialStore.getType()));
    } finally {
      credentialsStoreFile.deleteOnExit();
    }
  }

  @Test
  public void testCreationOfKeyStore() throws KeystoreServiceException {
    try {
      ks.createKeystore();
      assertTrue("Key Store file is not created", ks.isKeystoreAvailable() && keyStoreFile.exists());
      KeyStore keystore = ks.getKeystore();
      assertTrue("Key Store file is not created with proper file type",
        ("JKS").equalsIgnoreCase(keystore.getType()));
      ks.createCredentialStore();
      ks.addCredential(aliasName, "secretValue");
    } finally {
      keyStoreFile.deleteOnExit();
      credentialsStoreFile.deleteOnExit();
    }
  }

  @Test
  public void testAdditionOfCredentialsToKeyStore() throws KeystoreServiceException {
    try {
      ks.createKeystore();
      ks.createCredentialStore();
      ks.addCredential(aliasName, "secretValue");
      char[] secret = ks.getCredential(aliasName);
      assertTrue("Addition of Credentials failed", new String(secret).equals("secretValue"));
    } finally {
      credentialsStoreFile.deleteOnExit();
      keyStoreFile.deleteOnExit();
    }
  }

  @Test
  public void testAdditionOfAliasWithSelfSignedCertificate() throws KeystoreServiceException,
      KeyStoreException {
    try {
      ks.createKeystore();
      ks.createCredentialStore();
      ks.addCredential(aliasName, "secretValue");
      ks.addSelfSignedCert(aliasName, password);
      KeyStore keystore = ks.getKeystore();
      assertTrue("Addition of Alias with Self Signed Certificate failed",
        !keystore.getCertificate(aliasName).toString().isEmpty() && certificateFile.exists());
    } finally {
      credentialsStoreFile.deleteOnExit();
      keyStoreFile.deleteOnExit();
      certificateFile.deleteOnExit();
    }
  }

  @Test
  public void testFetchOfAliasKey() throws KeystoreServiceException {
    try {
      ks.createKeystore();
      ks.createCredentialStore();
      ks.addCredential(aliasName, "secretValue");
      ks.addSelfSignedCert(aliasName, password);
      assertTrue("Fetch of AliasKey failed", !ks.getKey(aliasName, password).toString().isEmpty()
          && certificateFile.exists());
    } finally {
      credentialsStoreFile.deleteOnExit();
      keyStoreFile.deleteOnExit();
      certificateFile.deleteOnExit();
    }
  }
}
