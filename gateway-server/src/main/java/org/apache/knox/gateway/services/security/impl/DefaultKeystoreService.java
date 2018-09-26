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

import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.GatewayResources;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.i18n.resources.ResourcesFactory;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
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
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DefaultKeystoreService extends BaseKeystoreService implements
    KeystoreService, Service {

  private static final String dnTemplate = "CN={0},OU=Test,O=Hadoop,L=Test,ST=Test,C=US";
  private static final String CREDENTIALS_SUFFIX = "-credentials.jceks";
  public static final String GATEWAY_KEYSTORE = "gateway.jks";
  private static final String CERT_GEN_MODE = "hadoop.gateway.cert.gen.mode";
  private static final String CERT_GEN_MODE_LOCALHOST = "localhost";
  private static final String CERT_GEN_MODE_HOSTNAME = "hostname";
  private static GatewayMessages LOG = MessagesFactory.get( GatewayMessages.class );
  private static GatewayResources RES = ResourcesFactory.get( GatewayResources.class );

  private String signingKeystoreName = null;
  private String signingKeyAlias = null;
  private Map<String, Map<String, String>> cache = new ConcurrentHashMap<String,Map<String, String>>();
  private Lock readLock = null;
  private Lock writeLock = null;

  @Override
  public void init(GatewayConfig config, Map<String, String> options)
      throws ServiceLifecycleException {
    ReadWriteLock lock = new ReentrantReadWriteLock(true);
    readLock = lock.readLock();
    writeLock = lock.writeLock();

    this.keyStoreDir = config.getGatewaySecurityDir() + File.separator + "keystores" + File.separator;
    File ksd = new File(this.keyStoreDir);
    if (!ksd.exists()) {
      if( !ksd.mkdirs() ) {
        throw new ServiceLifecycleException( RES.failedToCreateKeyStoreDirectory( ksd.getAbsolutePath() ) );
      }
    }

    signingKeystoreName = config.getSigningKeystoreName();
    // ensure that the keystore actually exists and fail to start if not
    if (signingKeystoreName != null) {
      File sks = new File(this.keyStoreDir, signingKeystoreName);
      if (!sks.exists()) {
        throw new ServiceLifecycleException("Configured signing keystore does not exist.");
      }
      signingKeyAlias = config.getSigningKeyAlias();
      if (signingKeyAlias != null) {
        // ensure that the signing key alias exists in the configured keystore
        KeyStore ks;
        try {
          ks = getSigningKeystore();
          if (ks != null) {
            if (!ks.containsAlias(signingKeyAlias)) {
              throw new ServiceLifecycleException("Configured signing key alias does not exist.");
            }
          }
        } catch (KeystoreServiceException e) {
          throw new ServiceLifecycleException("Unable to get the configured signing keystore.", e);
        } catch (KeyStoreException e) {
          throw new ServiceLifecycleException("Signing keystore has not been loaded.", e);
        }
      }
    }
  }

  @Override
  public void start() throws ServiceLifecycleException {
  }

  @Override
  public void stop() throws ServiceLifecycleException {
  }

  @Override
  public void createKeystoreForGateway() throws KeystoreServiceException {
    writeLock.lock();
    try {
      String filename = getKeystorePath();
      createKeystore(filename, "JKS");
    } 
    finally {
      writeLock.unlock();
    }
  }

  @Override
  public KeyStore getKeystoreForGateway() throws KeystoreServiceException {
    final File  keyStoreFile = new File( keyStoreDir + GATEWAY_KEYSTORE  );
    readLock.lock();
    try {
      return getKeystore(keyStoreFile, "JKS");
    }
    finally {
      readLock.unlock();
    }
  }

  @Override
  public KeyStore getSigningKeystore() throws KeystoreServiceException {
    File  keyStoreFile = null;
    if (signingKeystoreName == null) {
      keyStoreFile = new File(keyStoreDir + GATEWAY_KEYSTORE);
    }
    else {
      keyStoreFile = new File(keyStoreDir + signingKeystoreName);
      // make sure the keystore exists
      if (!keyStoreFile.exists()) {
        throw new KeystoreServiceException("Configured signing keystore does not exist.");
      }
    }
    readLock.lock();
    try {
      return getKeystore(keyStoreFile, "JKS");
    }
    finally {
      readLock.unlock();
    }
  }

  @Override
  public void addSelfSignedCertForGateway(String alias, char[] passphrase) throws KeystoreServiceException {
    writeLock.lock();
    try {
      addSelfSignedCertForGateway(alias, passphrase, null);
    }
    finally {
        writeLock.unlock();
    }
  }

  @Override
  public void addSelfSignedCertForGateway(String alias, char[] passphrase, String hostname) 
      throws KeystoreServiceException {
    writeLock.lock();
    try {
      KeyPairGenerator keyPairGenerator;
      try {
        keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);  
        KeyPair KPair = keyPairGenerator.generateKeyPair();
        if (hostname == null) {
          hostname = System.getProperty(CERT_GEN_MODE, CERT_GEN_MODE_LOCALHOST);
        }
        X509Certificate cert = null;
        if(hostname.equals(CERT_GEN_MODE_HOSTNAME)) {
          String dn = buildDistinguishedName(InetAddress.getLocalHost().getHostName());
          cert = X509CertificateUtil.generateCertificate(dn, KPair, 365, "SHA1withRSA");
        }
        else {
          String dn = buildDistinguishedName(hostname);
          cert = X509CertificateUtil.generateCertificate(dn, KPair, 365, "SHA1withRSA");
        }
  
        KeyStore privateKS = getKeystoreForGateway();
        privateKS.setKeyEntry(alias, KPair.getPrivate(),  
            passphrase,  
            new java.security.cert.Certificate[]{cert});  
        
        writeKeystoreToFile(privateKS, new File( keyStoreDir + GATEWAY_KEYSTORE  ));
        //writeCertificateToFile( cert, new File( keyStoreDir + alias + ".pem" ) );
      } catch (NoSuchAlgorithmException e) {
        LOG.failedToAddSeflSignedCertForGateway( alias, e );
        throw new KeystoreServiceException(e);
      } catch (GeneralSecurityException e) {
        LOG.failedToAddSeflSignedCertForGateway( alias, e );
        throw new KeystoreServiceException(e);
      } catch (IOException e) {
        LOG.failedToAddSeflSignedCertForGateway( alias, e );
        throw new KeystoreServiceException(e);
      }
    }
    finally {
      writeLock.unlock();
    }
  }

  private String buildDistinguishedName(String hostname) {
    MessageFormat headerFormatter = new MessageFormat(dnTemplate, Locale.ROOT);
    String[] paramArray = new String[1];
    paramArray[0] = hostname;
    String dn = headerFormatter.format(paramArray);
    return dn;
  }
  
  @Override
  public void createCredentialStoreForCluster(String clusterName) throws KeystoreServiceException {
    String filename = keyStoreDir + clusterName + CREDENTIALS_SUFFIX;
    writeLock.lock();
    try {
      createKeystore(filename, "JCEKS");
    }
    finally {
      writeLock.unlock();
    }
  }

  @Override
  public boolean isCredentialStoreForClusterAvailable(String clusterName) throws KeystoreServiceException {
    boolean rc = false;
    final File  keyStoreFile = new File( keyStoreDir + clusterName + CREDENTIALS_SUFFIX  );
    readLock.lock();
    try {
      try {
        rc = isKeystoreAvailable(keyStoreFile, "JCEKS");
      } catch (KeyStoreException e) {
        throw new KeystoreServiceException(e);
      } catch (IOException e) {
        throw new KeystoreServiceException(e);
      }
      return rc;
    }
    finally {
      readLock.unlock();
    }
  }

  @Override
  public boolean isKeystoreForGatewayAvailable() throws KeystoreServiceException {
    boolean rc = false;
    final File  keyStoreFile = new File( keyStoreDir + GATEWAY_KEYSTORE  );
    readLock.lock();
    try {
      try {
        rc = isKeystoreAvailable(keyStoreFile, "JKS");
      } catch (KeyStoreException e) {
        throw new KeystoreServiceException(e);
      } catch (IOException e) {
        throw new KeystoreServiceException(e);
      }
      return rc;
    }
    finally {
      readLock.unlock();
    }
  }

  @Override
  public Key getKeyForGateway(String alias, char[] passphrase) throws KeystoreServiceException {
    Key key = null;
    readLock.lock();
    try {
      KeyStore ks = getKeystoreForGateway();
      if (passphrase == null) {
        passphrase = masterService.getMasterSecret();
        LOG.assumingKeyPassphraseIsMaster();
      }
      if (ks != null) {
        try {
          key = ks.getKey(alias, passphrase);
        } catch (UnrecoverableKeyException e) {
          LOG.failedToGetKeyForGateway( alias, e );
        } catch (KeyStoreException e) {
          LOG.failedToGetKeyForGateway( alias, e );
        } catch (NoSuchAlgorithmException e) {
          LOG.failedToGetKeyForGateway( alias, e );
        }
      }
      return key;
    }
    finally {
      readLock.unlock();
    }
  }  

  @Override
  public Key getSigningKey(String alias, char[] passphrase) throws KeystoreServiceException {
    Key key = null;
    readLock.lock();
    try {
      KeyStore ks = getSigningKeystore();
      if (passphrase == null) {
        passphrase = masterService.getMasterSecret();
        LOG.assumingKeyPassphraseIsMaster();
      }
      if (ks != null) {
        try {
          key = ks.getKey(alias, passphrase);
        } catch (UnrecoverableKeyException e) {
          LOG.failedToGetKeyForGateway( alias, e );
        } catch (KeyStoreException e) {
          LOG.failedToGetKeyForGateway( alias, e );
        } catch (NoSuchAlgorithmException e) {
          LOG.failedToGetKeyForGateway( alias, e );
        }
      }
      return key;
    }
    finally {
      readLock.unlock();
    }
  }

  public KeyStore getCredentialStoreForCluster(String clusterName) 
      throws KeystoreServiceException {
    final File  keyStoreFile = new File( keyStoreDir + clusterName + CREDENTIALS_SUFFIX  );
    readLock.lock();
    try {
      return getKeystore(keyStoreFile, "JCEKS");
    }
    finally {
      readLock.unlock();
    }
  }

  public void addCredentialForCluster(String clusterName, String alias, String value) 
      throws KeystoreServiceException {
    writeLock.lock();
    try {
      removeFromCache(clusterName, alias);
      KeyStore ks = getCredentialStoreForCluster(clusterName);
      addCredential(alias, value, ks);
      final File  keyStoreFile = new File( keyStoreDir + clusterName + CREDENTIALS_SUFFIX  );
      try {
        writeKeystoreToFile(ks, keyStoreFile);
      } catch (KeyStoreException e) {
        LOG.failedToAddCredentialForCluster( clusterName, e );
      } catch (NoSuchAlgorithmException e) {
        LOG.failedToAddCredentialForCluster( clusterName, e );
      } catch (CertificateException e) {
        LOG.failedToAddCredentialForCluster( clusterName, e );
      } catch (IOException e) {
        LOG.failedToAddCredentialForCluster( clusterName, e );
      }
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public char[] getCredentialForCluster(String clusterName, String alias) 
      throws KeystoreServiceException {
    char[] credential = null;
    readLock.lock();
    try {
      credential = checkCache(clusterName, alias);
      if (credential == null) {
        KeyStore ks = getCredentialStoreForCluster(clusterName);
        if (ks != null) {
          try {
            char[] masterSecret = masterService.getMasterSecret();
            Key credentialKey = ks.getKey( alias, masterSecret );
            if (credentialKey != null) {
              byte[] credentialBytes = credentialKey.getEncoded();
              String credentialString = new String( credentialBytes, "UTF-8" );
              credential = credentialString.toCharArray();
              addToCache(clusterName, alias, credentialString);
            }
          } catch (UnrecoverableKeyException e) {
            LOG.failedToGetCredentialForCluster( clusterName, e );
          } catch (KeyStoreException e) {
            LOG.failedToGetCredentialForCluster( clusterName, e );
          } catch (NoSuchAlgorithmException e) {
            LOG.failedToGetCredentialForCluster( clusterName, e );
          } catch (UnsupportedEncodingException e) {
            LOG.failedToGetCredentialForCluster( clusterName, e );
           }
  
        }
      }
      return credential;
    }
    finally {
      readLock.unlock();
    }
  }

  @Override
  public void removeCredentialForCluster(String clusterName, String alias) throws KeystoreServiceException {
    final File  keyStoreFile = new File( keyStoreDir + clusterName + CREDENTIALS_SUFFIX  );
    writeLock.lock();
    try {
      removeFromCache(clusterName, alias);
      KeyStore ks = getCredentialStoreForCluster(clusterName);
      removeCredential(alias, ks);
      try {
        writeKeystoreToFile(ks, keyStoreFile);
      } catch (KeyStoreException e) {
        LOG.failedToRemoveCredentialForCluster(clusterName, e);
      } catch (NoSuchAlgorithmException e) {
        LOG.failedToRemoveCredentialForCluster(clusterName, e);
      } catch (CertificateException e) {
        LOG.failedToRemoveCredentialForCluster(clusterName, e);
      } catch (IOException e) {
        LOG.failedToRemoveCredentialForCluster(clusterName, e);
      }
    }
    finally {
      writeLock.unlock();
    }
  }

  /**
   * Called only from within critical sections of other methods above.
   * @param clusterName
   * @param alias
   * @return
   */
  private char[] checkCache(String clusterName, String alias) {
    char[] c = null;
    String cred = null;
      HashMap<String, String> clusterCache = (HashMap<String, String>) cache.get(clusterName);
      if (clusterCache == null) {
        return null;
      }
      cred = clusterCache.get(alias);
      if (cred != null) {
        c = cred.toCharArray();
      }
      return c;
  }

  /**
   * Called only from within critical sections of other methods above.
   * @param clusterName
   * @param alias
   * @param credentialString
   */
  private void addToCache(String clusterName, String alias, String credentialString) {
      HashMap<String, String> clusterCache = (HashMap<String, String>) cache.get(clusterName);
      if (clusterCache == null) {
        clusterCache = new HashMap<String, String>();
      }
      clusterCache.put(alias, credentialString);
  }

  /**
   * Called only from within critical sections of other methods above.
   * @param clusterName
   * @param alias
   */
  private void removeFromCache(String clusterName, String alias) {
      HashMap<String, String> clusterCache = (HashMap<String, String>) cache.get(clusterName);
      if (clusterCache == null) {
        return;
      }
      clusterCache.remove(alias);
  }

  @Override
  public String getKeystorePath() {
    return keyStoreDir + GATEWAY_KEYSTORE;
  }
}
