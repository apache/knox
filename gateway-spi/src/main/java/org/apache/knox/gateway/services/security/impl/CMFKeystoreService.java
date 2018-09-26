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
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.apache.knox.gateway.i18n.GatewaySpiMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.KeystoreServiceException;

public class CMFKeystoreService extends BaseKeystoreService {
  private static GatewaySpiMessages LOG = MessagesFactory.get( GatewaySpiMessages.class );

  private static final String TEST_CERT_DN = "CN=hadoop,OU=Test,O=Hadoop,L=Test,ST=Test,C=US";
  private static final String CREDENTIALS_SUFFIX = "-credentials.jceks";

  private String serviceName = null;
  
  public CMFKeystoreService(String keystoreDir, String serviceName)
      throws ServiceLifecycleException {
    this.serviceName = serviceName;
    this.keyStoreDir = keystoreDir + File.separator;
    File ksd = new File(this.keyStoreDir);
    if (!ksd.exists() && !ksd.mkdirs()) {
      throw new ServiceLifecycleException("Cannot create the keystore directory");
    }
  }

  public void createKeystore() throws KeystoreServiceException {
    String filename = keyStoreDir + serviceName + ".jks";
    createKeystore(filename, "JKS");
  }

  public KeyStore getKeystore() throws KeystoreServiceException {
    final File  keyStoreFile = new File( keyStoreDir + serviceName  );
    return getKeystore(keyStoreFile, "JKS");
  }
  
  public void addSelfSignedCert(String alias, char[] passphrase)
      throws KeystoreServiceException {
    KeyPairGenerator keyPairGenerator;
    try {
      keyPairGenerator = KeyPairGenerator.getInstance("RSA");
      keyPairGenerator.initialize(1024);  
      KeyPair KPair = keyPairGenerator.generateKeyPair();
      X509Certificate cert = X509CertificateUtil.generateCertificate(TEST_CERT_DN, KPair, 365, "SHA1withRSA");

      KeyStore privateKS = getKeystore();
      if (privateKS != null) {
        privateKS.setKeyEntry(alias, KPair.getPrivate(),  
          passphrase,  
          new java.security.cert.Certificate[]{cert});  
        writeKeystoreToFile(privateKS, new File( keyStoreDir + serviceName  ));
      } else {
        throw new IOException("Unable to open gateway keystore.");
      }
    } catch (NoSuchAlgorithmException e) {
      LOG.failedToAddSeflSignedCertForGateway(alias, e);
    } catch (GeneralSecurityException e) {
      LOG.failedToAddSeflSignedCertForGateway(alias, e);
    } catch (IOException e) {
      LOG.failedToAddSeflSignedCertForGateway(alias, e);
    }  
  }
  
  public void createCredentialStore() throws KeystoreServiceException {
    String filename = keyStoreDir + serviceName + CREDENTIALS_SUFFIX;
    createKeystore(filename, "JCEKS");
  }

  public boolean isCredentialStoreAvailable() throws KeystoreServiceException {
    final File  keyStoreFile = new File( keyStoreDir + serviceName + CREDENTIALS_SUFFIX  );
    try {
      return isKeystoreAvailable(keyStoreFile, "JCEKS");
    } catch (KeyStoreException e) {
      throw new KeystoreServiceException(e);
    } catch (IOException e) {
      throw new KeystoreServiceException(e);
    }
  }

  public boolean isKeystoreAvailable() throws KeystoreServiceException {
    final File  keyStoreFile = new File( keyStoreDir + serviceName + ".jks" );
    try {
      return isKeystoreAvailable(keyStoreFile, "JKS");
    } catch (KeyStoreException e) {
      throw new KeystoreServiceException(e);
    } catch (IOException e) {
      throw new KeystoreServiceException(e);
    }
  }

  public Key getKey(String alias, char[] passphrase) throws KeystoreServiceException {
    Key key = null;
    KeyStore ks = getKeystore();
    if (ks != null) {
      try {
        key = ks.getKey(alias, passphrase);
      } catch (UnrecoverableKeyException e) {
        // TODO Auto-generated catch block
        LOG.failedToGetKey(alias, e);
      } catch (KeyStoreException e) {
        LOG.failedToGetKey(alias, e);
      } catch (NoSuchAlgorithmException e) {
        LOG.failedToGetKey(alias, e);
      }
    }
    return key;
  }  
  
  public KeyStore getCredentialStore() throws KeystoreServiceException {
    final File  keyStoreFile = new File( keyStoreDir + serviceName + CREDENTIALS_SUFFIX  );
    return getKeystore(keyStoreFile, "JCEKS");
  }

  public void addCredential(String alias, String value) throws KeystoreServiceException {
    KeyStore ks = getCredentialStore();
    addCredential(alias, value, ks);
    final File  keyStoreFile = new File( keyStoreDir + serviceName + CREDENTIALS_SUFFIX  );
    try {
      writeKeystoreToFile(ks, keyStoreFile);
    } catch (KeyStoreException e) {
      LOG.failedToAddCredential(e);
    } catch (NoSuchAlgorithmException e) {
      LOG.failedToAddCredential(e);
    } catch (CertificateException e) {
      LOG.failedToAddCredential(e);
    } catch (IOException e) {
      LOG.failedToAddCredential(e);
    }
  }

  public char[] getCredential(String alias) throws KeystoreServiceException {
    char[] credential = null;
    KeyStore ks = getCredentialStore();
    credential = getCredential(alias, credential, ks);
    return credential;
  }

}
