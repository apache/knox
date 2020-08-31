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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.GatewayResources;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.i18n.resources.ResourcesFactory;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.util.X509CertificateUtil;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.crypto.spec.SecretKeySpec;

public class DefaultKeystoreService implements KeystoreService {
  private static final String DN_TEMPLATE = "CN={0},OU=Test,O=Hadoop,L=Test,ST=Test,C=US";
  public static final String CREDENTIALS_SUFFIX = "-credentials.jceks";
  private static final String CREDENTIALS_STORE_TYPE = "JCEKS";
  private static final String CERT_GEN_MODE = "hadoop.gateway.cert.gen.mode";
  private static final String CERT_GEN_MODE_LOCALHOST = "localhost";
  private static final String CERT_GEN_MODE_HOSTNAME = "hostname";
  private static GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);
  private static GatewayResources RES = ResourcesFactory.get(GatewayResources.class);

  // Let's configure the cache with hard-coded attributes now; we can introduce new gateway configuration later on if
  // needed visible for testing
  final Cache<CacheKey, String> cache = Caffeine.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES).maximumSize(1000).build();

  private GatewayConfig config;

  private MasterService masterService;
  private Path keyStoreDirPath;

  public void setMasterService(MasterService ms) {
    this.masterService = ms;
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options)
      throws ServiceLifecycleException {
    this.config = config;

    this.keyStoreDirPath = Paths.get(config.getGatewayKeystoreDir());
    if (Files.notExists(keyStoreDirPath)) {
      try {
        // This will attempt to create all missing directories.  No failures will occur if the
        // directories already exist.
        Files.createDirectories(keyStoreDirPath);
      } catch (IOException e) {
        throw new ServiceLifecycleException(RES.failedToCreateKeyStoreDirectory(keyStoreDirPath.toString()));
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
    createKeyStore(Paths.get(config.getIdentityKeystorePath()), config.getIdentityKeystoreType(),
        getKeyStorePassword(config.getIdentityKeystorePasswordAlias()));
  }

  @Override
  public KeyStore getKeystoreForGateway() throws KeystoreServiceException {
    return getKeystore(Paths.get(config.getIdentityKeystorePath()), config.getIdentityKeystoreType(), config.getIdentityKeystorePasswordAlias(), true);
  }

  @Override
  public KeyStore getTruststoreForHttpClient() throws KeystoreServiceException {
    String trustStorePath = config.getHttpClientTruststorePath();
    if (trustStorePath == null) {
      return null;
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
      keyStoreFile = keyStoreDirPath.resolve(keystoreName + ".jks");
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
    addSelfSignedCertForGateway(alias, passphrase, null);
  }

  @Override
  public void addSelfSignedCertForGateway(String alias, char[] passphrase, String hostname)
      throws KeystoreServiceException {
    addCertForGateway(alias, passphrase, hostname);
  }

  private synchronized void addCertForGateway(String alias, char[] passphrase, String hostname)
      throws KeystoreServiceException {
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

      writeKeyStoreToFile(privateKS, Paths.get(config.getIdentityKeystorePath()),
          getKeyStorePassword(config.getIdentityKeystorePasswordAlias()));
    } catch (GeneralSecurityException | IOException e) {
      LOG.failedToAddSeflSignedCertForGateway( alias, e );
      throw new KeystoreServiceException(e);
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
    createKeyStore(keyStoreDirPath.resolve(clusterName + CREDENTIALS_SUFFIX),
        CREDENTIALS_STORE_TYPE, masterService.getMasterSecret());
  }

  @Override
  public boolean isCredentialStoreForClusterAvailable(String clusterName) throws KeystoreServiceException {
    final Path keyStoreFilePath = keyStoreDirPath.resolve(clusterName + CREDENTIALS_SUFFIX);
    try {
      return isKeyStoreAvailable(keyStoreFilePath, CREDENTIALS_STORE_TYPE, masterService.getMasterSecret());
    } catch (KeyStoreException | IOException e) {
      throw new KeystoreServiceException(e);
    }
  }

  @Override
  public boolean isKeystoreForGatewayAvailable() throws KeystoreServiceException {
    final Path keyStoreFilePath = Paths.get(config.getIdentityKeystorePath());
    try {
      return isKeyStoreAvailable(keyStoreFilePath, config.getIdentityKeystoreType(),
          getKeyStorePassword(config.getIdentityKeystorePasswordAlias()));
    } catch (KeyStoreException | IOException e) {
      throw new KeystoreServiceException(e);
    }
  }

  @Override
  public Key getKeyForGateway(char[] passphrase) throws KeystoreServiceException {
    return getKeyForGateway(config.getIdentityKeyAlias(), passphrase);
  }

  @Override
  public Key getKeyForGateway(String alias, char[] passphrase) throws KeystoreServiceException {
    return getKeyFromKeystore(getKeystoreForGateway(), alias, passphrase);
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
    return getKeyFromKeystore(getSigningKeystore(keystoreName), alias, passphrase);
  }

  private Key getKeyFromKeystore(KeyStore ks, String alias, char[] passphrase) {
    Key key = null;
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

  @Override
  public KeyStore getCredentialStoreForCluster(String clusterName)
      throws KeystoreServiceException {
    // Do not fail getting the credential store if the keystore file does not exist.  The returned
    // KeyStore will be empty.  This seems like a potential bug, but is the behavior before KNOX-1812
    return getKeystore(keyStoreDirPath.resolve(clusterName + CREDENTIALS_SUFFIX),
        CREDENTIALS_STORE_TYPE, null, false);
  }

  @Override
  public void addCredentialForCluster(String clusterName, String alias, String value)
      throws KeystoreServiceException {
    addCredentialsForCluster(clusterName, Collections.singletonMap(alias, value));
  }

  @Override
  public void addCredentialsForCluster(String clusterName, Map<String, String> credentials)
      throws KeystoreServiceException {
    // Needed to prevent read then write synchronization issue where alias is not added
    synchronized (this) {
      removeFromCache(clusterName, credentials.keySet());
      KeyStore ks = getCredentialStoreForCluster(clusterName);
      if (ks != null) {
        try {
          // Add all the credential keys to the keystore
          for (Map.Entry<String, String> credential : credentials.entrySet()) {
            final Key key = new SecretKeySpec(credential.getValue().getBytes(StandardCharsets.UTF_8), "AES");
            ks.setKeyEntry(credential.getKey(), key, masterService.getMasterSecret(), null);
          }

          // Write all the changes once
          final Path keyStoreFilePath = keyStoreDirPath.resolve(clusterName + CREDENTIALS_SUFFIX);
          writeKeyStoreToFile(ks, keyStoreFilePath, masterService.getMasterSecret());
          addToCache(clusterName, credentials);
        } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException e) {
          LOG.failedToAddCredentialForCluster(clusterName, e);
        }
      }
    }
  }

  @Override
  public char[] getCredentialForCluster(String clusterName, String alias)
      throws KeystoreServiceException {
    char[] credential;

    synchronized (this) {
      credential = checkCache(clusterName, alias);
      if (credential == null) {
        KeyStore ks = getCredentialStoreForCluster(clusterName);
        if (ks != null) {
          try {
            char[] masterSecret = masterService.getMasterSecret();
            Key credentialKey = ks.getKey(alias, masterSecret);
            if (credentialKey != null) {
              byte[] credentialBytes = credentialKey.getEncoded();
              String credentialString = new String(credentialBytes, StandardCharsets.UTF_8);
              credential = credentialString.toCharArray();
              addToCache(clusterName, alias, credentialString);
            }
          } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException e) {
            LOG.failedToGetCredentialForCluster(clusterName, e);
          }

        }
      }
    }

    return credential;
  }

  @Override
  public void removeCredentialForCluster(String clusterName, String alias) throws KeystoreServiceException {
    removeCredentialsForCluster(clusterName, Collections.singleton(alias));
  }

  @Override
  public void removeCredentialsForCluster(String clusterName, Set<String> aliases) throws KeystoreServiceException {
    // Needed to prevent read then write synchronization issue where alias is not removed
    synchronized (this) {
      KeyStore ks = getCredentialStoreForCluster(clusterName);
      if (ks != null) {
        try {
          // Delete all the entries
          for (String alias : aliases) {
            if (ks.containsAlias(alias)) {
              ks.deleteEntry(alias);
            }
          }
          removeFromCache(clusterName, aliases);

          // Update the keystore file once to reflect all the alias deletions
          final Path keyStoreFilePath = keyStoreDirPath.resolve(clusterName + CREDENTIALS_SUFFIX);
          writeKeyStoreToFile(ks, keyStoreFilePath, masterService.getMasterSecret());
        } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException e) {
          LOG.failedToRemoveCredentialForCluster(clusterName, e);
        }
      }
    }
  }

  /**
   * Called only from within critical sections of other methods above.
   */
  private char[] checkCache(String clusterName, String alias) {
    final String cachedCredential = cache.getIfPresent(CacheKey.of(clusterName, alias));
    return cachedCredential == null ? null : cachedCredential.toCharArray();
  }

  /**
   * Called only from within critical sections of other methods above.
   */
  private void addToCache(String clusterName, String alias, String credentialString) {
    cache.put(CacheKey.of(clusterName, alias), credentialString);
  }

  /**
   * Called only from within critical sections of other methods above.
   */
  private void addToCache(String clusterName, Map<String, String> credentials) {
    for (String alias : credentials.keySet()) {
      cache.put(CacheKey.of(clusterName, alias), credentials.get(alias));
    }
  }

  /**
   * Called only from within critical sections of other methods above.
   */
  private void removeFromCache(String clusterName, String alias) {
    cache.invalidate(CacheKey.of(clusterName, alias));
  }

  /**
   * Called only from within critical sections of other methods above.
   */
  private void removeFromCache(String clusterName, Set<String> aliases) {
    Set<CacheKey> keys = new HashSet<>();
    for (String alias : aliases) {
      keys.add(CacheKey.of(clusterName, alias));
    }
    cache.invalidateAll(keys);
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
  private synchronized KeyStore getKeystore(Path keystorePath, String keystoreType, String alias,
                                            boolean failIfNotAccessible)
      throws KeystoreServiceException {
    if (failIfNotAccessible) {
      if (Files.notExists(keystorePath)) {
        LOG.keystoreFileDoesNotExist(keystorePath.toString());
        throw new KeystoreServiceException("The keystore file does not exist: " + keystorePath.toString());
      } else if (!Files.isRegularFile(keystorePath)) {
        LOG.keystoreFileIsNotAFile(keystorePath.toString());
        throw new KeystoreServiceException("The keystore file is not a file: " + keystorePath.toString());
      } else if (!Files.isReadable(keystorePath)) {
        LOG.keystoreFileIsNotAccessible(keystorePath.toString());
        throw new KeystoreServiceException("The keystore file cannot be read: " + keystorePath.toString());
      }
    }

    return loadKeyStore(keystorePath, keystoreType, getKeyStorePassword(alias));
  }

  private synchronized boolean isKeyStoreAvailable(final Path keyStoreFilePath, String storeType,
                                                   char[] password)
      throws KeyStoreException, IOException {
    if (Files.exists(keyStoreFilePath) &&
            Files.isRegularFile(keyStoreFilePath) &&
            Files.isReadable(keyStoreFilePath)) {
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
  // We need this to be synchronized to prevent multiple threads from using at once
  synchronized KeyStore createKeyStore(Path keystoreFilePath, String keystoreType, char[] password)
      throws KeystoreServiceException {
    if (Files.notExists(keystoreFilePath)) {
      // Ensure the parent directory exists...
      try {
        // This will attempt to create all missing directories.  No failures will occur if the
        // directories already exist.
        Files.createDirectories(keystoreFilePath.getParent());
      } catch (IOException e) {
        LOG.failedToCreateKeystore(keystoreFilePath.toString(), keystoreType, e);
        throw new KeystoreServiceException(e);
      }
    }

    try {
      KeyStore ks = KeyStore.getInstance(keystoreType);
      ks.load(null, null);
      writeKeyStoreToFile(ks, keystoreFilePath, password);
      return ks;
    } catch (NoSuchAlgorithmException | CertificateException | KeyStoreException | IOException e) {
      LOG.failedToCreateKeystore(keystoreFilePath.toString(), keystoreType, e);
      throw new KeystoreServiceException(e);
    }
  }

  // Package private for unit test access
  synchronized KeyStore loadKeyStore(final Path keyStoreFilePath, final String storeType,
                                     final char[] password) throws KeystoreServiceException {
    try {
      final KeyStore keyStore = KeyStore.getInstance(storeType);

      // If the file does not exist, create an empty keystore
      if (Files.exists(keyStoreFilePath)) {
        try (FileChannel fileChannel = FileChannel.open(keyStoreFilePath, StandardOpenOption.READ)) {
          fileChannel.lock(0L, Long.MAX_VALUE, true);
          try (InputStream input = Channels.newInputStream(fileChannel)) {
            keyStore.load(input, password);
          }
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
  synchronized void writeKeyStoreToFile(final KeyStore keyStore, final Path path, char[] password)
      throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
    // TODO: backup the keystore on disk before attempting a write and restore on failure
    try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.WRITE,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
      fileChannel.lock();
      try (OutputStream out = Channels.newOutputStream(fileChannel)) {
        keyStore.store(out, password);
      }
    }
  }

  private char[] getKeyStorePassword(String alias) throws KeystoreServiceException {
    char[] password = null;
    if (alias != null && !alias.isEmpty()) {
      password = getCredentialForCluster(NO_CLUSTER_NAME, alias);
    }
    return (password == null) ? masterService.getMasterSecret() : password;
  }

  private static class CacheKey {
    private final String clusterName;
    private final String alias;

    private CacheKey(String clusterName, String alias) {
      this.clusterName = clusterName;
      this.alias = alias;
    }

    private static CacheKey of(String clusterName, String alias) {
      return new CacheKey(clusterName, alias);
    }

    @Override
    public int hashCode() {
      return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
      return EqualsBuilder.reflectionEquals(this, obj);
    }
  }
}
