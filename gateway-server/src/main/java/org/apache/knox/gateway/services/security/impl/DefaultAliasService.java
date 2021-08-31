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

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AbstractAliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.util.PasswordUtils;

public class DefaultAliasService extends AbstractAliasService {
  private static final GatewayMessages LOG = MessagesFactory.get( GatewayMessages.class );

  private KeystoreService keystoreService;
  private MasterService masterService;
  private GatewayConfig config;

  @Override
  public void init(GatewayConfig config, Map<String, String> options)
      throws ServiceLifecycleException {
    this.config = config;
  }

  @Override
  public void start() throws ServiceLifecycleException {
  }

  @Override
  public void stop() throws ServiceLifecycleException {
  }

  @Override
  public char[] getGatewayIdentityPassphrase() throws AliasServiceException {
    char[] passphrase = getPasswordFromAliasForGateway(config.getIdentityKeyPassphraseAlias());
    if (passphrase == null) {
      // Fall back to the keystore password if a key-specific password was not explicitly set.
      passphrase = getGatewayIdentityKeystorePassword();
    }
    if (passphrase == null) {
      // Use the master password if not password was found
      passphrase = masterService.getMasterSecret();
    }
    return passphrase;
  }

  @Override
  public char[] getGatewayIdentityKeystorePassword() throws AliasServiceException {
    char[] passphrase = getPasswordFromAliasForGateway(config.getIdentityKeystorePasswordAlias());
    if (passphrase == null) {
      // Use the master password if not password was found
      passphrase = masterService.getMasterSecret();
    }
    return passphrase;
  }

  @Override
  public char[] getSigningKeyPassphrase() throws AliasServiceException {
    char[] passphrase = getPasswordFromAliasForGateway(config.getSigningKeyPassphraseAlias());
    if (passphrase == null) {
      // Fall back to the keystore password if a key-specific password was not explicitly set.
      passphrase = getSigningKeystorePassword();
    }
    if (passphrase == null) {
      // Use the master password if not password was found
      passphrase = masterService.getMasterSecret();
    }
    return passphrase;
  }

  @Override
  public char[] getSigningKeystorePassword() throws AliasServiceException {
    char[] passphrase = getPasswordFromAliasForGateway(config.getSigningKeystorePasswordAlias());
    if (passphrase == null) {
      // Use the master password if not password was found
      passphrase = masterService.getMasterSecret();
    }
    return passphrase;
  }

  @Override
  public char[] getPasswordFromAliasForCluster(String clusterName, String alias)
      throws AliasServiceException {
    return getPasswordFromAliasForCluster(clusterName, alias, false);
  }

  @Override
  public char[] getPasswordFromAliasForCluster(String clusterName, String alias, boolean generate)
      throws AliasServiceException {
    char[] credential;
    try {
      credential = keystoreService.getCredentialForCluster(clusterName, alias);
      if (credential == null && generate) {
        generateAliasForCluster(clusterName, alias);
        credential = keystoreService.getCredentialForCluster(clusterName, alias);
      }
    } catch (KeystoreServiceException e) {
      LOG.failedToGetCredentialForCluster(clusterName, e);
      throw new AliasServiceException(e);
    }
    return credential;
  }

  public void setKeystoreService(KeystoreService ks) {
    this.keystoreService = ks;
  }

  public void setMasterService(MasterService ms) {
    this.masterService = ms;

  }

  @Override
  public void generateAliasForCluster(String clusterName, String alias)
      throws AliasServiceException {
    try {
      keystoreService.getCredentialStoreForCluster(clusterName);
    } catch (KeystoreServiceException e) {
      LOG.failedToGenerateAliasForCluster(clusterName, e);
      throw new AliasServiceException(e);
    }
    String passwordString = PasswordUtils.generatePassword(16);
    addAliasForCluster(clusterName, alias, passwordString);
  }

  @Override
  public void addAliasForCluster(String clusterName, String alias, String value) {
    try {
      keystoreService.addCredentialForCluster(clusterName, alias, value);
    } catch (KeystoreServiceException e) {
      LOG.failedToAddCredentialForCluster(clusterName, e);
    }
  }

  @Override
  public void addAliasesForCluster(String clusterName, Map<String, String> aliases) throws AliasServiceException {
    try {
      keystoreService.addCredentialsForCluster(clusterName, aliases);
    } catch (KeystoreServiceException e) {
      LOG.failedToAddCredentialsForCluster(clusterName, e);
    }
  }

  @Override
  public void removeAliasForCluster(String clusterName, String alias)
      throws AliasServiceException {
    try {
      keystoreService.removeCredentialForCluster(clusterName, alias);
    } catch (KeystoreServiceException e) {
      throw new AliasServiceException(e);
    }
  }

  @Override
  public void removeAliasesForCluster(String clusterName, Set<String> aliases) throws AliasServiceException {
    try {
      keystoreService.removeCredentialsForCluster(clusterName, aliases);
    } catch (KeystoreServiceException e) {
      throw new AliasServiceException(e);
    }
  }

  @Override
  public char[] getPasswordFromAliasForGateway(String alias)
      throws AliasServiceException {
    return getPasswordFromAliasForCluster(NO_CLUSTER_NAME, alias);
  }

  //Overriding the default behavior as we want to avoid loading the keystore N-times from the file system
  @Override
  public Map<String, char[]> getPasswordsForGateway() throws AliasServiceException {
    final Map<String, char[]> passwordAliasMap = new HashMap<>();
    try {
      final KeyStore gatewayCredentialStore = keystoreService.getCredentialStoreForCluster(NO_CLUSTER_NAME);
      final Enumeration<String> aliases = gatewayCredentialStore.aliases();
      String alias;
      while (aliases.hasMoreElements()) {
        alias = aliases.nextElement();
        passwordAliasMap.put(alias, keystoreService.getCredentialForCluster(NO_CLUSTER_NAME, alias, gatewayCredentialStore));
      }
    } catch (KeystoreServiceException | KeyStoreException e) {
      e.printStackTrace();
    }
    return passwordAliasMap;
  }

  @Override
  public void generateAliasForGateway(String alias)
      throws AliasServiceException {
    generateAliasForCluster(NO_CLUSTER_NAME, alias);
  }

  @Override
  public Certificate getCertificateForGateway(String alias) {
    Certificate cert = null;
    try {
      cert = this.keystoreService.getKeystoreForGateway().getCertificate(alias);
    } catch (KeyStoreException | KeystoreServiceException e) {
      LOG.unableToRetrieveCertificateForGateway(e);
      // should we throw an exception?
    }
    return cert;
  }

  @Override
  public List<String> getAliasesForCluster(String clusterName) {
    ArrayList<String> list = new ArrayList<>();
    KeyStore keyStore;
    try {
      keyStore = keystoreService.getCredentialStoreForCluster(clusterName);
      if (keyStore != null) {
        String alias;
        try {
          Enumeration<String> e = keyStore.aliases();
          while (e.hasMoreElements()) {
             alias = e.nextElement();
             // only include the metadata key names in the list of names
             if (!alias.contains("@")) {
                 list.add(alias);
             }
          }
        } catch (KeyStoreException e) {
          LOG.failedToGetCredentialForCluster(clusterName, e);
        }
      }
    } catch (KeystoreServiceException kse) {
      LOG.failedToGetCredentialForCluster(clusterName, kse);
    }
    return list;
  }
}
