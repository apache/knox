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

import org.apache.commons.lang.StringUtils;
import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.GatewayResources;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.i18n.resources.ResourcesFactory;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.util.X509CertificateUtil;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
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
  private static final String NO_CLUSTER_NAME = "__gateway";
  private static final String CERT_GEN_MODE = "hadoop.gateway.cert.gen.mode";
  private static final String CERT_GEN_MODE_LOCALHOST = "localhost";
  private static final String CERT_GEN_MODE_HOSTNAME = "hostname";
  private static GatewayMessages LOG = MessagesFactory.get( GatewayMessages.class );
  private static GatewayResources RES = ResourcesFactory.get( GatewayResources.class );

  private GatewayConfig config;
  private Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();
  private Lock readLock;
  private Lock writeLock;

  @Override
  public void init(GatewayConfig config, Map<String, String> options)
      throws ServiceLifecycleException {
    ReadWriteLock lock = new ReentrantReadWriteLock(true);
    readLock = lock.readLock();
    writeLock = lock.writeLock();

    this.config = config;

    this.keyStoreDir = config.getGatewayKeystoreDir();
    File ksd = new File(this.keyStoreDir);
    if (!ksd.exists()) {
      if( !ksd.mkdirs() ) {
        throw new ServiceLifecycleException( RES.failedToCreateKeyStoreDirectory( ksd.getAbsolutePath() ) );
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
      createKeystore(filename, config.getIdentityKeystoreType(), getKeystorePassword(config.getIdentityKeystorePasswordAlias()));
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public KeyStore getKeystoreForGateway() throws KeystoreServiceException {
    final File  keyStoreFile = new File( config.getIdentityKeystorePath() );
    readLock.lock();
    try {
      return getKeystore(keyStoreFile, config.getIdentityKeystoreType(), getKeystorePassword(config.getIdentityKeystorePasswordAlias()));
    }
    finally {
      readLock.unlock();
    }
  }

  @Override
  public KeyStore getSigningKeystore() throws KeystoreServiceException {
    return getSigningKeystore(null);
  }

  @Override
  public KeyStore getSigningKeystore(String keystoreName) throws KeystoreServiceException {
    File keyStoreFile;
    String keyStoreType;
    char[] password;
    if(keystoreName != null) {
      keyStoreFile = new File(keyStoreDir, keystoreName + ".jks");
      keyStoreType = "jks";
      password = masterService.getMasterSecret();
    } else {
      keyStoreFile = new File(config.getSigningKeystorePath());
      keyStoreType = config.getSigningKeystoreType();
      password = getKeystorePassword(config.getSigningKeystorePasswordAlias());
    }

    // make sure the keystore exists
    if (!keyStoreFile.exists()) {
      throw new KeystoreServiceException("Configured signing keystore does not exist.");
    }
    readLock.lock();
    try {
      return getKeystore(keyStoreFile, keyStoreType, password);
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
        keyPairGenerator.initialize(2048);
        KeyPair KPair = keyPairGenerator.generateKeyPair();
        if (hostname == null) {
          hostname = System.getProperty(CERT_GEN_MODE, CERT_GEN_MODE_LOCALHOST);
        }
        X509Certificate cert;
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

        writeKeystoreToFile(privateKS, new File( config.getIdentityKeystorePath() ), getKeystorePassword(config.getIdentityKeystorePasswordAlias()));
        //writeCertificateToFile( cert, new File( keyStoreDir + alias + ".pem" ) );
      } catch (GeneralSecurityException | IOException e) {
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
    return headerFormatter.format(paramArray);
  }

  @Override
  public void createCredentialStoreForCluster(String clusterName) throws KeystoreServiceException {
    String filename = Paths.get(keyStoreDir, clusterName + CREDENTIALS_SUFFIX).toString();
    writeLock.lock();
    try {
      createKeystore(filename, "JCEKS", masterService.getMasterSecret());
    }
    finally {
      writeLock.unlock();
    }
  }

  @Override
  public boolean isCredentialStoreForClusterAvailable(String clusterName) throws KeystoreServiceException {
    boolean rc;
    final File  keyStoreFile = new File( keyStoreDir, clusterName + CREDENTIALS_SUFFIX  );
    readLock.lock();
    try {
      try {
        rc = isKeystoreAvailable(keyStoreFile, "JCEKS", masterService.getMasterSecret());
      } catch (KeyStoreException | IOException e) {
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
    boolean rc;
    final File  keyStoreFile = new File( config.getIdentityKeystorePath() );
    readLock.lock();
    try {
      try {
        rc = isKeystoreAvailable(keyStoreFile, config.getIdentityKeystoreType(), getKeystorePassword(config.getIdentityKeystorePasswordAlias()));
      } catch (KeyStoreException | IOException e) {
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
        } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException e) {
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
  public Key getKeyForGateway(char[] passphrase) throws KeystoreServiceException {
    return getKeyForGateway(config.getIdentityKeyAlias(), passphrase);
  }

  @Override
  public Certificate getCertificateForGateway() throws KeystoreServiceException, KeyStoreException {
    KeyStore ks = getKeystoreForGateway();
    return (ks == null) ? null : ks.getCertificate(config.getIdentityKeyAlias());
  }

  @Override
  public Key getSigningKey(String alias, char[] passphrase) throws KeystoreServiceException {
    return getSigningKey(null, alias, passphrase);
  }

  @Override
  public Key getSigningKey(String keystoreName, String alias, char[] passphrase) throws KeystoreServiceException {
    Key key = null;
    readLock.lock();
    try {
      KeyStore ks = getSigningKeystore(keystoreName);
      if (passphrase == null) {
        passphrase = masterService.getMasterSecret();
        LOG.assumingKeyPassphraseIsMaster();
      }
      if (ks != null) {
        try {
          key = ks.getKey(alias, passphrase);
        } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException e) {
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
  public KeyStore getCredentialStoreForCluster(String clusterName)
      throws KeystoreServiceException {
    final File  keyStoreFile = new File( keyStoreDir, clusterName + CREDENTIALS_SUFFIX  );
    readLock.lock();
    try {
      return getKeystore(keyStoreFile, "JCEKS", masterService.getMasterSecret());
    }
    finally {
      readLock.unlock();
    }
  }

  @Override
  public void addCredentialForCluster(String clusterName, String alias, String value)
      throws KeystoreServiceException {
    writeLock.lock();
    try {
      removeFromCache(clusterName, alias);
      KeyStore ks = getCredentialStoreForCluster(clusterName);
      addCredential(alias, value, ks);
      final File  keyStoreFile = new File( keyStoreDir, clusterName + CREDENTIALS_SUFFIX  );
      try {
        writeKeystoreToFile(ks, keyStoreFile, masterService.getMasterSecret());
      } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException e) {
        LOG.failedToAddCredentialForCluster( clusterName, e );
      }
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public char[] getCredentialForCluster(String clusterName, String alias)
      throws KeystoreServiceException {
    char[] credential;
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
              String credentialString = new String( credentialBytes, StandardCharsets.UTF_8 );
              credential = credentialString.toCharArray();
              addToCache(clusterName, alias, credentialString);
            }
          } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException e) {
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
    final File  keyStoreFile = new File( keyStoreDir, clusterName + CREDENTIALS_SUFFIX  );
    writeLock.lock();
    try {
      removeFromCache(clusterName, alias);
      KeyStore ks = getCredentialStoreForCluster(clusterName);
      removeCredential(alias, ks);
      try {
        writeKeystoreToFile(ks, keyStoreFile, masterService.getMasterSecret());
      } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException e) {
        LOG.failedToRemoveCredentialForCluster(clusterName, e);
      }
    }
    finally {
      writeLock.unlock();
    }
  }

  /**
   * Called only from within critical sections of other methods above.
   */
  private char[] checkCache(String clusterName, String alias) {
    char[] c = null;
    String cred;
    Map<String, String> clusterCache = cache.get(clusterName);
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
   */
  private void addToCache(String clusterName, String alias, String credentialString) {
    Map<String, String> clusterCache = cache.computeIfAbsent(clusterName, k -> new HashMap<>());
    clusterCache.put(alias, credentialString);
  }

  /**
   * Called only from within critical sections of other methods above.
   */
  private void removeFromCache(String clusterName, String alias) {
    Map<String, String> clusterCache = cache.get(clusterName);
    if (clusterCache == null) {
      return;
    }
    clusterCache.remove(alias);
  }

  @Override
  public String getKeystorePath() {
    return config.getIdentityKeystorePath();
  }

  private char[] getKeystorePassword(String alias) throws KeystoreServiceException {
    char[] password = null;
    if (StringUtils.isNotEmpty(alias)) {
      password = getCredentialForCluster(NO_CLUSTER_NAME, alias);
    }
    return (password == null) ? masterService.getMasterSecret() : password;
  }
}
