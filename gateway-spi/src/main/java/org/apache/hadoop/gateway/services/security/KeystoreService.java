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
package org.apache.hadoop.gateway.services.security;

import java.io.File;
import java.io.IOException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import org.apache.hadoop.gateway.services.Service;

public interface KeystoreService extends Service {

  public void createKeystoreForGateway();

  public void addSelfSignedCertForGateway(String alias, char[] passphrase);
  
  public KeyStore getKeystoreForGateway();
  
  public Key getKeyForGateway(String alias, char[] passphrase) throws KeystoreServiceException;

  public void createCredentialStoreForCluster(String clusterName);
  
  public boolean isCredentialStoreForClusterAvailable(String clusterName) throws KeystoreServiceException;

  public boolean isKeystoreForGatewayAvailable() throws KeystoreServiceException;
  
  public KeyStore getCredentialStoreForCluster(String clusterName);

  public void addCredentialForCluster(String clusterName, String alias, String key);

  public char[] getCredentialForCluster(String clusterName, String alias);

  public void writeKeystoreToFile(final KeyStore keyStore, final File file)
      throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException;

  public byte[] getKeyForCluster(String clusterName, String alias);

  public void addKeyForCluster(String clusterName, String alias, byte[] key);
}
