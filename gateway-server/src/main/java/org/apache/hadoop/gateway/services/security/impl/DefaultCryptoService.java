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

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.services.security.AliasService;
import org.apache.hadoop.gateway.services.security.CryptoService;
import org.apache.hadoop.gateway.services.security.EncryptionResult;
import org.apache.hadoop.gateway.services.security.KeystoreService;
import org.apache.hadoop.gateway.services.security.KeystoreServiceException;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;

public class DefaultCryptoService implements CryptoService {
  
  private AliasService as = null;
  private KeystoreService ks = null;

  public void setKeystoreService(KeystoreService ks) {
    this.ks = ks;
  }

  public CryptoService setAliasService(AliasService as) {
    this.as = as;
    return this;
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options)
      throws ServiceLifecycleException {
    if (as == null) {
      throw new ServiceLifecycleException("Alias service is not set");
    }
  }

  @Override
  public void start() throws ServiceLifecycleException {
    // TODO Auto-generated method stub

  }

  @Override
  public void stop() throws ServiceLifecycleException {
    // TODO Auto-generated method stub

  }

  @Override
  public void createAndStoreEncryptionKeyForCluster(String clusterName, String alias) {
    as.generateAliasForCluster(clusterName, alias);
  }

  @Override
  public EncryptionResult encryptForCluster(String clusterName, String alias, byte[] clear) {
    char[] password = as.getPasswordFromAliasForCluster(clusterName, alias);
    if (password != null) {
      AESEncryptor aes = null;
      try {
        aes = new AESEncryptor(new String(password));
        return aes.encrypt(clear);
      } catch (NoSuchAlgorithmException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      } catch (InvalidKeyException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return null;
  }

  @Override
  public byte[] decryptForCluster(String clusterName, String alias, String cipherText) {
    try {
      return decryptForCluster(clusterName, alias, cipherText.getBytes("UTF8"), null, null);
    } catch (UnsupportedEncodingException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public byte[] decryptForCluster(String clusterName, String alias, byte[] cipherText, byte[] iv, byte[] salt) {
  char[] password = as.getPasswordFromAliasForCluster(clusterName, alias);
  if (password != null) {
      AESEncryptor aes = new AESEncryptor(new String(password));
      try {
        return aes.decrypt(salt, iv, cipherText);
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return null;
  }

  @Override
  public boolean verify(String algorithm, String alias, String signed, byte[] signature) {
    boolean verified = false;
    try {
      Signature sig=Signature.getInstance(algorithm);
      sig.initVerify(ks.getKeystoreForGateway().getCertificate(alias).getPublicKey());
      sig.update(signed.getBytes("UTF-8"));
      verified = sig.verify(signature);
    } catch (SignatureException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (NoSuchAlgorithmException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (InvalidKeyException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (KeyStoreException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (UnsupportedEncodingException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    System.out.println("Signature verified: " + verified);  
    return verified;
  }

  @Override
  public byte[] sign(String algorithm, String alias, String payloadToSign) {
    try {
      PrivateKey privateKey = (PrivateKey) ks.getKeyForGateway(alias);
      Signature signature = Signature.getInstance(algorithm);
      signature.initSign(privateKey);
      signature.update(payloadToSign.getBytes("UTF-8"));
      return signature.sign();
    } catch (NoSuchAlgorithmException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (InvalidKeyException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (SignatureException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (UnsupportedEncodingException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (KeystoreServiceException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }
}
