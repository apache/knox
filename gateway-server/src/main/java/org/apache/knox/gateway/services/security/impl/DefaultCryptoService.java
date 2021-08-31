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

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.Map;

import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.CryptoService;
import org.apache.knox.gateway.services.security.EncryptionResult;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.ServiceLifecycleException;

public class DefaultCryptoService implements CryptoService {
  private static final GatewayMessages LOG = MessagesFactory.get( GatewayMessages.class );

  private static final Map<String,ConfigurableEncryptor> ENCRYPTOR_CACHE = new HashMap<>();

  private AliasService aliasService;
  private KeystoreService keystoreService;
  private GatewayConfig config;

  public void setKeystoreService(KeystoreService ks) {
    this.keystoreService = ks;
  }

  public void setAliasService(AliasService as) {
    this.aliasService = as;
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options)
      throws ServiceLifecycleException {
    this.config = config;
  if (aliasService == null) {
      throw new ServiceLifecycleException("Alias service is not set");
    }
  }

  @Override
  public void start() throws ServiceLifecycleException {

  }

  @Override
  public void stop() throws ServiceLifecycleException {

  }

  @Override
  public void createAndStoreEncryptionKeyForCluster(String clusterName, String alias) {
    try {
      aliasService.generateAliasForCluster(clusterName, alias);
    } catch (AliasServiceException e) {
      e.printStackTrace();
    }
  }

  @Override
  public EncryptionResult encryptForCluster(String clusterName, String alias, byte[] clear) {
    char[] password = null;
    try {
      password = aliasService.getPasswordFromAliasForCluster(clusterName, alias);
    } catch (AliasServiceException e2) {
      e2.printStackTrace();
    }
    if (password != null) {
      try {
        return getEncryptor(clusterName,password).encrypt( clear );
      } catch (Exception e) {
        LOG.failedToEncryptPasswordForCluster( clusterName, e );
      }
    }
    return null;
  }

  @Override
  public byte[] decryptForCluster(String clusterName, String alias, String cipherText) {
    return decryptForCluster(clusterName, alias, cipherText.getBytes(StandardCharsets.UTF_8), null, null);
  }

  @Override
  public byte[] decryptForCluster(String clusterName, String alias, byte[] cipherText, byte[] iv, byte[] salt) {
    try {
      char[] password;
      ConfigurableEncryptor encryptor;
        password = aliasService.getPasswordFromAliasForCluster(clusterName, alias);
        if (password != null) {
          encryptor = getEncryptor(clusterName,password );
          try {
            return encryptor.decrypt( salt, iv, cipherText);
          } catch (Exception e) {
            LOG.failedToDecryptPasswordForCluster( clusterName, e );
          }
      }
      else {
        LOG.failedToDecryptCipherForClusterNullPassword( clusterName );
      }
    } catch (AliasServiceException e1) {
      LOG.failedToDecryptCipherForClusterNullPassword( clusterName );
    }
    return null;
  }

  @Override
  public boolean verify(String algorithm, String signed, byte[] signature) {
    boolean verified = false;
    try {
      Signature sig=Signature.getInstance(algorithm);
      sig.initVerify(keystoreService.getCertificateForGateway().getPublicKey());
      sig.update(signed.getBytes(StandardCharsets.UTF_8));
      verified = sig.verify(signature);
    } catch (SignatureException | KeystoreServiceException | InvalidKeyException | NoSuchAlgorithmException | KeyStoreException e) {
      LOG.failedToVerifySignature( e );
    }
    LOG.signatureVerified( verified );
    return verified;
  }

  @Override
  public byte[] sign(String algorithm, String payloadToSign) {
    try {
      char[] passphrase;
      passphrase = aliasService.getGatewayIdentityPassphrase();
      PrivateKey privateKey = (PrivateKey) keystoreService.getKeyForGateway(passphrase);
      Signature signature = Signature.getInstance(algorithm);
      signature.initSign(privateKey);
      signature.update(payloadToSign.getBytes(StandardCharsets.UTF_8));
      return signature.sign();
    } catch (NoSuchAlgorithmException | AliasServiceException | KeystoreServiceException | SignatureException | InvalidKeyException e) {
      LOG.failedToSignData( e );
    }
    return null;
  }

  // The assumption here is that lock contention will be less of a performance issue than the cost of object creation.
  // We have seen via profiling that AESEncryptor instantiation is very expensive.
  private ConfigurableEncryptor getEncryptor( final String clusterName, final char[] password ) {
    synchronized(ENCRYPTOR_CACHE) {
      ConfigurableEncryptor encryptor = ENCRYPTOR_CACHE.get( clusterName );
      if( encryptor == null ) {
        encryptor = new ConfigurableEncryptor( String.valueOf( password ) );
        encryptor.init(config);
        ENCRYPTOR_CACHE.put( clusterName, encryptor );
      }
      return encryptor;
    }
  }

}
