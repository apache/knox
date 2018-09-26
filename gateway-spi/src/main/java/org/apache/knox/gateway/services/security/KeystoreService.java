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
package org.apache.knox.gateway.services.security;

import java.security.Key;
import java.security.KeyStore;

public interface KeystoreService {

  public void createKeystoreForGateway() throws KeystoreServiceException;

  public void addSelfSignedCertForGateway(String alias, char[] passphrase) throws KeystoreServiceException;
  
  void addSelfSignedCertForGateway(String alias, char[] passphrase, String hostname) throws KeystoreServiceException;

  public KeyStore getKeystoreForGateway() throws KeystoreServiceException;

  public KeyStore getSigningKeystore() throws KeystoreServiceException;

  public Key getKeyForGateway(String alias, char[] passphrase) throws KeystoreServiceException;

  public Key getSigningKey(String alias, char[] passphrase) throws KeystoreServiceException;

  public void createCredentialStoreForCluster(String clusterName) throws KeystoreServiceException;
  
  public boolean isCredentialStoreForClusterAvailable(String clusterName) throws KeystoreServiceException;

  public boolean isKeystoreForGatewayAvailable() throws KeystoreServiceException;
  
  public KeyStore getCredentialStoreForCluster(String clusterName) throws KeystoreServiceException;

  public void addCredentialForCluster(String clusterName, String alias, String key) throws KeystoreServiceException;

  public void removeCredentialForCluster(String clusterName, String alias) throws KeystoreServiceException;

  public char[] getCredentialForCluster(String clusterName, String alias) throws KeystoreServiceException;

  public String getKeystorePath();
}
