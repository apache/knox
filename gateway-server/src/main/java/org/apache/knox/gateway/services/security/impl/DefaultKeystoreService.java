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

import static org.apache.knox.gateway.services.security.AliasService.NO_CLUSTER_NAME;

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
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.util.X509CertificateUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

import javax.crypto.spec.SecretKeySpec;

public class DefaultKeystoreService implements KeystoreService, Service {

  private static final String DN_TEMPLATE = "CN={0},OU=Test,O=Hadoop,L=Test,ST=Test,C=US";
  private static final String CREDENTIALS_SUFFIX = "-credentials.jceks";
  private static final String CREDENTIALS_STORE_TYPE = "JCEKS";
  private static final String CERT_GEN_MODE = "hadoop.gateway.cert.gen.mode";
  private static final String CERT_GEN_MODE_LOCALHOST = "localhost";
  private static final String CERT_GEN_MODE_HOSTNAME = "hostname";
  private static GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);
  private static GatewayResources RES = ResourcesFactory.get(GatewayResources.class);

  private GatewayConfig config;
  private Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();
  private Lock readLock;
  private Lock writeLock;

  private MasterService masterService;
  private String keyStoreDir;

  public void setMasterService(MasterService ms) {
    this.masterService = ms;
  }

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
      createKeyStore(Paths.get(getKeystorePath()), config.getIdentityKeystoreType(), getKeyStorePassword(config.getIdentityKeystorePasswordAlias()));
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public KeyStore getKeystoreForGateway() throws KeystoreServiceException {
    return getKeystore(Paths.get(config.getIdentityKeystorePath()), config.getIdentityKeystoreType(), config.getIdentityKeystorePasswordAlias(), true);
  }

  @Override
  public KeyStore getTruststoreForHttpClient() throws KeystoreServiceException {
    String trustStorePath = config.getHttpClientTruststorePath();
    if (trustStorePath == null) {
      // If the trustStorePath is null, fallback to behavior before KNOX-1812
      return getKeystoreForGateway();
    } else {
      return getKeystore(Paths.get(trustStorePath), config.getHttpClientTruststoreType(), config.getHttpClientTruststorePasswordAlias(), true);
    }
  }

  @Override
  public KeyStore getSigningKeystore() throws KeystoreServiceException {
    return getSigningKeystore(null);
  }

  @Override
  public KeyStore getSigningKeystore(String keystoreName) throws KeystoreServiceException {
    Path keyStoreFile;
    String keyStoreType;
    String passwordAlias;
    if(keystoreName != null) {
      keyStoreFile = Paths.get(keyStoreDir, keystoreName + ".jks");
      keyStoreType = "jks";
      passwordAlias = null;
    } else {
      keyStoreFile = Paths.get(config.getSigningKeystorePath());
      keyStoreType = config.getSigningKeystoreType();
      passwordAlias = config.getSigningKeystorePasswordAlias();
    }
    return getKeystore(keyStoreFile, keyStoreType, passwordAlias, true);
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

        writeKeyStoreToFile(privateKS, Paths.get(config.getIdentityKeystorePath()), getKeyStorePassword(config.getIdentityKeystorePasswordAlias()));
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
    MessageFormat headerFormatter = new MessageFormat(DN_TEMPLATE, Locale.ROOT);
    String[] paramArray = new String[1];
    paramArray[0] = hostname;
    return headerFormatter.format(paramArray);
  }

  @Override
  public void createCredentialStoreForCluster(String clusterName) throws KeystoreServiceException {
    Path keystoreFilePath = Paths.get(keyStoreDir, clusterName + CREDENTIALS_SUFFIX);
    writeLock.lock();
    try {
      createKeyStore(keystoreFilePath, CREDENTIALS_STORE_TYPE, masterService.getMasterSecret());
    }
    finally {
      writeLock.unlock();
    }
  }

  @Override
  public boolean isCredentialStoreForClusterAvailable(String clusterName) throws KeystoreServiceException {
    boolean rc;
    final Path keyStoreFilePath = Paths.get(keyStoreDir, clusterName + CREDENTIALS_SUFFIX);
    readLock.lock();
    try {
      try {
        rc = isKeyStoreAvailable(keyStoreFilePath, CREDENTIALS_STORE_TYPE, masterService.getMasterSecret());
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
    final Path keyStoreFilePath = Paths.get(config.getIdentityKeystorePath());
    readLock.lock();
    try {
      try {
        return isKeyStoreAvailable(keyStoreFilePath, config.getIdentityKeystoreType(), getKeyStorePassword(config.getIdentityKeystorePasswordAlias()));
      } catch (KeyStoreException | IOException e) {
        throw new KeystoreServiceException(e);
      }
    } finally {
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
    // Do not fail getting the credential store if the keystore file does not exist.  The returned
    // KeyStore will be empty.  This seems like a potential bug, but is the behavior before KNOX-1812
    return getKeystore(Paths.get(keyStoreDir, clusterName + CREDENTIALS_SUFFIX), CREDENTIALS_STORE_TYPE, null, false);
  }

  @Override
  public void addCredentialForCluster(String clusterName, String alias, String value)
      throws KeystoreServiceException {
    writeLock.lock();
    try {
      removeFromCache(clusterName, alias);
      KeyStore ks = getCredentialStoreForCluster(clusterName);
      addCredential(alias, value, ks);
      final Path keyStoreFilePath = Paths.get(keyStoreDir, clusterName + CREDENTIALS_SUFFIX);
      try {
        writeKeyStoreToFile(ks, keyStoreFilePath, masterService.getMasterSecret());
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
    final Path keyStoreFilePath = Paths.get(keyStoreDir, clusterName + CREDENTIALS_SUFFIX);
    writeLock.lock();
    try {
      removeFromCache(clusterName, alias);
      KeyStore ks = getCredentialStoreForCluster(clusterName);
      removeCredential(alias, ks);
      try {
        writeKeyStoreToFile(ks, keyStoreFilePath, masterService.getMasterSecret());
      } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException e) {
        LOG.failedToRemoveCredentialForCluster(clusterName, e);
      }
    } finally {
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

  /**
   * Loads a keystore file.
   * <p>
   * if <code>failIfNotAccessible</code> is <code>true</code>, then the path to the keystore file
   * (keystorePath) is validated such that it exists, is a file and can be read by the process. If
   * any of these checks fail, a {@link KeystoreServiceException} is thrown in dictating the exact
   * reason.
   * <p>
   * Before the keystore file is loaded, the service's read lock is locked to prevent concurrent
   * reads on the file.
   *
   * @param keystorePath        the path to the keystore file
   * @param keystoreType        the type of keystore file
   * @param alias               the alias for the password to the keystore file (see {@link #getKeyStorePassword(String)})
   * @param failIfNotAccessible <code>true</code> to ensure the keystore file exists and is readable; <code>false</code> to not check
   * @return a {@link KeyStore}, or <code>null</code> if the requested keystore cannot be created
   * @throws KeystoreServiceException if an error occurs loading the keystore file
   */
  private KeyStore getKeystore(Path keystorePath, String keystoreType, String alias, boolean failIfNotAccessible) throws KeystoreServiceException {

    if (failIfNotAccessible) {
      File keystoreFile = keystorePath.toFile();

      if (!keystoreFile.exists()) {
        LOG.keystoreFileDoesNotExist(keystorePath.toString());
        throw new KeystoreServiceException("The keystore file does not exist: " + keystoreFile.getAbsolutePath());
      } else if (!keystoreFile.isFile()) {
        LOG.keystoreFileIsNotAFile(keystorePath.toString());
        throw new KeystoreServiceException("The keystore file is not a file: " + keystoreFile.getAbsolutePath());
      } else if (!keystoreFile.canRead()) {
        LOG.keystoreFileIsNotAccessible(keystorePath.toString());
        throw new KeystoreServiceException("The keystore file cannot be read: " + keystoreFile.getAbsolutePath());
      }
    }

    readLock.lock();
    try {
      return loadKeyStore(keystorePath, keystoreType, getKeyStorePassword(alias));
    } finally {
      readLock.unlock();
    }
  }

  private boolean isKeyStoreAvailable(final Path keyStoreFilePath, String storeType, char[] password) throws KeyStoreException, IOException {
    if (keyStoreFilePath.toFile().exists()) {
      try (InputStream input = Files.newInputStream(keyStoreFilePath)) {
        final KeyStore keyStore = KeyStore.getInstance(storeType);
        keyStore.load(input, password);
        return true;
      } catch (NoSuchAlgorithmException | CertificateException e) {
        LOG.failedToLoadKeystore(keyStoreFilePath.toString(), storeType, e);
      } catch (IOException | KeyStoreException e) {
        LOG.failedToLoadKeystore(keyStoreFilePath.toString(), storeType, e);
        throw e;
      }
    }
    return false;
  }

  // Package private for unit test access
  KeyStore createKeyStore(Path keystoreFilePath, String keystoreType, char[] password) throws KeystoreServiceException {
    try (OutputStream out = createKeyStoreFile(keystoreFilePath)) {
      KeyStore ks = KeyStore.getInstance(keystoreType);
      ks.load(null, null);
      ks.store(out, password);
      return ks;
    } catch (NoSuchAlgorithmException | CertificateException | KeyStoreException | IOException e) {
      LOG.failedToCreateKeystore(keystoreFilePath.toString(), keystoreType, e);
      throw new KeystoreServiceException(e);
    }
  }

  private static OutputStream createKeyStoreFile(Path keystoreFilePath) throws IOException {
    File file = keystoreFilePath.toFile();
    if (file.exists()) {
      if (file.isDirectory()) {
        throw new IOException(file.getAbsolutePath());
      } else if (!file.canWrite()) {
        throw new IOException(file.getAbsolutePath());
      }
    } else {
      File dir = file.getParentFile();
      if (!dir.exists()) {
        if (!dir.mkdirs()) {
          throw new IOException(file.getAbsolutePath());
        }
      }
    }
    return Files.newOutputStream(file.toPath());
  }

  private void addCredential(String alias, String value, KeyStore ks) {
    if (ks != null) {
      try {
        final Key key = new SecretKeySpec(value.getBytes(StandardCharsets.UTF_8), "AES");
        ks.setKeyEntry(alias, key, masterService.getMasterSecret(), null);
      } catch (KeyStoreException e) {
        LOG.failedToAddCredential(e);
      }
    }
  }

  private void removeCredential(String alias, KeyStore ks) {
    if (ks != null) {
      try {
        if (ks.containsAlias(alias)) {
          ks.deleteEntry(alias);
        }
      } catch (KeyStoreException e) {
        LOG.failedToRemoveCredential(e);
      }
    }
  }

  // Package private for unit test access
  KeyStore loadKeyStore(final Path keyStoreFilePath, final String storeType, final char[] password) throws KeystoreServiceException {
    try {
      final KeyStore keyStore = KeyStore.getInstance(storeType);

      // If the file does not exist, create an empty keystore
      if (keyStoreFilePath.toFile().exists()) {
        try (InputStream input = Files.newInputStream(keyStoreFilePath)) {
          keyStore.load(input, password);
        }
      } else {
        keyStore.load(null, password);
      }
      return keyStore;
    } catch (CertificateException | NoSuchAlgorithmException | KeyStoreException | IOException e) {
      LOG.failedToLoadKeystore(keyStoreFilePath.toString(), storeType, e);
      throw new KeystoreServiceException(e);
    }
  }

  // Package private for unit test access
  void writeKeyStoreToFile(final KeyStore keyStore, final Path path, char[] password)
      throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
    // TODO: backup the keystore on disk before attempting a write and restore on failure
    try (OutputStream out = Files.newOutputStream(path)) {
      keyStore.store(out, password);
    }
  }

  private char[] getKeyStorePassword(String alias) throws KeystoreServiceException {
    char[] password = null;
    if (StringUtils.isNotEmpty(alias)) {
      password = getCredentialForCluster(NO_CLUSTER_NAME, alias);
    }
    return (password == null) ? masterService.getMasterSecret() : password;
  }
}
