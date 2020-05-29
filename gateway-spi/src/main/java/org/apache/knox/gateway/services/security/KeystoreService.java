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
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.util.Map;
import java.util.Set;

public interface KeystoreService {

  void createKeystoreForGateway() throws KeystoreServiceException;

  void addSelfSignedCertForGateway(String alias, char[] passphrase) throws KeystoreServiceException;

  void addSelfSignedCertForGateway(String alias, char[] passphrase, String hostname) throws KeystoreServiceException;

  KeyStore getKeystoreForGateway() throws KeystoreServiceException;

  /**
   * Gets the configured keystore instance that contains trust data.
   *
   * @throws KeystoreServiceException Exception when unable to get truststore
   * @return a {@link KeyStore}; or <code>null</code> if not configured
   */
  KeyStore getTruststoreForHttpClient() throws KeystoreServiceException;

  KeyStore getSigningKeystore() throws KeystoreServiceException;

  KeyStore getSigningKeystore(String keystoreName) throws KeystoreServiceException;

  Key getKeyForGateway(String alias, char[] passphrase) throws KeystoreServiceException;

  Key getKeyForGateway(char[] passphrase) throws KeystoreServiceException;

  Certificate getCertificateForGateway() throws KeystoreServiceException, KeyStoreException;

  Key getSigningKey(String alias, char[] passphrase) throws KeystoreServiceException;

  Key getSigningKey(String keystoreName, String alias, char[] passphrase) throws KeystoreServiceException;

  void createCredentialStoreForCluster(String clusterName) throws KeystoreServiceException;

  boolean isCredentialStoreForClusterAvailable(String clusterName) throws KeystoreServiceException;

  boolean isKeystoreForGatewayAvailable() throws KeystoreServiceException;

  KeyStore getCredentialStoreForCluster(String clusterName) throws KeystoreServiceException;

  void addCredentialForCluster(String clusterName, String alias, String key) throws KeystoreServiceException;

  void addCredentialsForCluster(String clusterName, Map<String, String> credentials) throws KeystoreServiceException;

  void removeCredentialForCluster(String clusterName, String alias) throws KeystoreServiceException;

  void removeCredentialsForCluster(String clusterName, Set<String> aliases) throws KeystoreServiceException;

  char[] getCredentialForCluster(String clusterName, String alias) throws KeystoreServiceException;

  String getKeystorePath();
}
