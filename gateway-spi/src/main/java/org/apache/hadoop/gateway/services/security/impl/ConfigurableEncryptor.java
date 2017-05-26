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

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.i18n.GatewaySpiMessages;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.services.security.EncryptionResult;

public class ConfigurableEncryptor {
  private static final GatewaySpiMessages LOG = MessagesFactory.get( GatewaySpiMessages.class );
  
  private static final int ITERATION_COUNT = 65536;
  private static final int KEY_LENGTH = 128;
  
  private Cipher ecipher;
  private Cipher dcipher;
  private SecretKey secret;
  private byte[] salt = null;
  private char[] passPhrase = null;
  private String alg = "AES";
  private String pbeAlg = "PBKDF2WithHmacSHA1";
  private String transformation = "AES/CBC/PKCS5Padding";
  private int saltSize = 8;
  private int iterationCount = ITERATION_COUNT;
  private int keyLength = KEY_LENGTH;
 
  public ConfigurableEncryptor(String passPhrase) {
      try {
        this.passPhrase = passPhrase.toCharArray();
        salt = new byte[saltSize];
        SecureRandom rnd = new SecureRandom();
        rnd.nextBytes(salt);
        
        SecretKey tmp = getKeyFromPassword(passPhrase);
        secret = new SecretKeySpec (tmp.getEncoded(), alg);
 
        ecipher = Cipher.getInstance(transformation);
        ecipher.init(Cipher.ENCRYPT_MODE, secret);
       
        dcipher = Cipher.getInstance(transformation);
        byte[] iv = ecipher.getParameters().getParameterSpec(IvParameterSpec.class).getIV();
        dcipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
      } catch (NoSuchAlgorithmException e) {
        LOG.failedToEncryptPassphrase( e );
      } catch (NoSuchPaddingException e) {
        LOG.failedToEncryptPassphrase( e );
      } catch (InvalidKeyException e) {
        LOG.failedToEncryptPassphrase( e );
      } catch (InvalidParameterSpecException e) {
        LOG.failedToEncryptPassphrase( e );
      } catch (InvalidAlgorithmParameterException e) {
        LOG.failedToEncryptPassphrase( e );
      }
  }
  
  ConfigurableEncryptor(SecretKey secret) {
    try {
      this.secret = new SecretKeySpec (secret.getEncoded(), alg);

      ecipher = Cipher.getInstance(transformation);
      ecipher.init(Cipher.ENCRYPT_MODE, secret);
     
      dcipher = Cipher.getInstance(transformation);
      byte[] iv = ecipher.getParameters().getParameterSpec(IvParameterSpec.class).getIV();
      dcipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
    } catch (NoSuchAlgorithmException e) {
      LOG.failedToEncryptPassphrase( e );
    } catch (NoSuchPaddingException e) {
      LOG.failedToEncryptPassphrase( e );
    } catch (InvalidKeyException e) {
      LOG.failedToEncryptPassphrase( e );
    } catch (InvalidParameterSpecException e) {
      LOG.failedToEncryptPassphrase( e );
    } catch (InvalidAlgorithmParameterException e) {
      LOG.failedToEncryptPassphrase( e );
    }
  }

  public void init(GatewayConfig config) {
    if (config != null) {
	    String alg = config.getAlgorithm();
	    if (alg != null) {
		  this.alg = alg;
	    }
	    String pbeAlg = config.getPBEAlgorithm();
	    if (pbeAlg != null) {
		  this.pbeAlg = pbeAlg;
	    }
	    String transformation = config.getTransformation();
	    if (transformation != null) {
		  this.transformation = transformation;
	    }
	    String saltSize = config.getSaltSize();
	    if (saltSize != null) {
		  this.saltSize = Integer.parseInt(saltSize);
	    }
	    String iterationCount = config.getIterationCount();
	    if (iterationCount != null) {
		  this.iterationCount = Integer.parseInt(iterationCount);
	    }
	    String keyLength = config.getKeyLength();
	    if (keyLength != null) {
		  this.keyLength = Integer.parseInt(keyLength);
	    }
    }
  }

  public SecretKey getKeyFromPassword(String passPhrase) {
    return getKeyFromPassword(passPhrase, salt);
  }
  
  public SecretKey getKeyFromPassword(String passPhrase, byte[] salt) {
    SecretKeyFactory factory;
    SecretKey key = null;
    try {
      factory = SecretKeyFactory.getInstance(pbeAlg);
      KeySpec spec = new PBEKeySpec(passPhrase.toCharArray(), salt, iterationCount, keyLength);
      key = factory.generateSecret(spec);
    } catch (NoSuchAlgorithmException e) {
      LOG.failedToGenerateKeyFromPassword( e );
    } catch (InvalidKeySpecException e) {
      LOG.failedToGenerateKeyFromPassword( e );
    }
    
    return key;
  }

  public EncryptionResult encrypt(String encrypt) throws Exception {
      byte[] bytes = encrypt.getBytes("UTF8");
      EncryptionResult atom = encrypt(bytes);
      return atom;
  }

  public EncryptionResult encrypt(byte[] plain) throws Exception {
    EncryptionResult atom = new EncryptionResult(salt, ecipher.getParameters().getParameterSpec(IvParameterSpec.class).getIV(), ecipher.doFinal(plain));
    return atom;
  }

  public String decrypt(String salt, String iv, String cipher) throws Exception {
    byte[] decrypted = decrypt(salt.getBytes("UTF8"), iv.getBytes("UTF8"), cipher.getBytes("UTF8"));
    return new String(decrypted, "UTF8");
  }

  public byte[] decrypt(byte[] salt, byte[] iv, byte[] encrypt) throws Exception {
    SecretKey tmp = getKeyFromPassword(new String(passPhrase), salt);
    secret = new SecretKeySpec(tmp.getEncoded(), alg);
    
    dcipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
    return dcipher.doFinal(encrypt);
  }
  
  public byte[] decrypt(byte[] encrypt) throws Exception {
    dcipher.init(Cipher.DECRYPT_MODE, secret);
    return dcipher.doFinal(encrypt);
  }
}
