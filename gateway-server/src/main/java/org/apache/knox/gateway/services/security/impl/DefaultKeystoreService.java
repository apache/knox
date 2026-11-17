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

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.PrefixFileFilter;
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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.crypto.spec.SecretKeySpec;

public class DefaultKeystoreService implements KeystoreService {
  private static final String DN_TEMPLATE = "CN={0},OU=Test,O=Hadoop,L=Test,ST=Test,C=US";
  public static final String CREDENTIALS_SUFFIX = "-credentials.";
  private static final String CERT_GEN_MODE = "hadoop.gateway.cert.gen.mode";
  private static final String CERT_GEN_MODE_LOCALHOST = "localhost";
  private static final String CERT_GEN_MODE_HOSTNAME = "hostname";
  private static GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);
  private static GatewayResources RES = ResourcesFactory.get(GatewayResources.class);

  Cache<CacheKey, String> cache;

  /*
   * Writers hold the write lock; readers share the read lock, so loads of different files run in
   * parallel. Loads of the same file are serialized on its entry in keyStoreLoadLocks, because two
   * file locks on one file within a JVM fail with an OverlappingFileLockException.
   */
  private ReadWriteLock keyStoreLock;
  private Map<Path, Object> keyStoreLoadLocks;

  /* Signing keystores and credential stores as loaded from disk, keyed by their normalized file path */
  private Map<Path, CachedKeyStore> keyStoreCache;
  private boolean keyStoreFileCacheEnabled;

  private GatewayConfig config;

  private MasterService masterService;
  private Path keyStoreDirPath;

  private String credentialStoreAlgorithm;
  private String credentialStoreType;
  private String credentialsSuffix;
  private String selfSigningCertificateAlgorithm;

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

    if (this.cache == null) {
      this.cache = Caffeine.newBuilder().expireAfterAccess(config.getKeystoreCacheEntryTimeToLiveInMinutes(), TimeUnit.MINUTES).maximumSize(config.getKeystoreCacheSizeLimit()).build();
    }

    // Not initialized with the fields: partial mocks are created without running the constructor
    if (this.keyStoreLock == null) {
      this.keyStoreLock = new ReentrantReadWriteLock();
      this.keyStoreLoadLocks = new ConcurrentHashMap<>();
      this.keyStoreCache = new ConcurrentHashMap<>();
    }
    this.keyStoreCache.clear();
    this.keyStoreFileCacheEnabled = config.isKeystoreFileCacheEnabled();

    this.credentialStoreAlgorithm = config.getCredentialStoreAlgorithm();
    this.credentialStoreType = config.getCredentialStoreType();
    this.credentialsSuffix = CREDENTIALS_SUFFIX + this.credentialStoreType.toLowerCase(Locale.ROOT);
    this.selfSigningCertificateAlgorithm = config.getSelfSigningCertificateAlgorithm();
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
    return getKeystore(Paths.get(config.getIdentityKeystorePath()), config.getIdentityKeystoreType(), config.getIdentityKeystorePasswordAlias(), true, false);
  }

  @Override
  public KeyStore getTruststoreForHttpClient() throws KeystoreServiceException {
    String trustStorePath = config.getHttpClientTruststorePath();
    if (trustStorePath == null) {
      return null;
    } else {
      return getKeystore(Paths.get(trustStorePath), config.getHttpClientTruststoreType(), config.getHttpClientTruststorePasswordAlias(), true, false);
    }
  }

  @Override
  public KeyStore getKeystoreForHttpClient() throws KeystoreServiceException {
    String keystorePath = config.getHttpClientKeystorePath();
    if (keystorePath == null) {
      // Single-EKU with no dedicated client keystore: the clientAuth identity is
      // co-located in the gateway keystore.
      if (config.isSingleEkuEnabled()) {
        return getKeystoreForGateway();
      }
      return null;
    }
    return getKeystore(Paths.get(keystorePath), config.getHttpClientKeystoreType(),
        config.getHttpClientKeystorePasswordAlias(), true, false);
  }

  @Override
  public KeyStore getSigningKeystore() throws KeystoreServiceException {
    return getSigningKeystore(null);
  }

  @Override
  public KeyStore getSigningKeystore(String keystoreName) throws KeystoreServiceException {
    return getSigningKeystore(keystoreName, true);
  }

  private KeyStore getSigningKeystore(String keystoreName, boolean cacheable) throws KeystoreServiceException {
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
    return getKeystore(keyStoreFile, keyStoreType, passwordAlias, true, cacheable);
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

  @Override
  public void addSelfSignedCertForGateway(String alias, char[] passphrase, String hostname, String... ekuOids)
      throws KeystoreServiceException {
    addCertForGateway(alias, passphrase, hostname, ekuOids);
  }

  private void addCertForGateway(String alias, char[] passphrase, String hostname, String... ekuOids)
      throws KeystoreServiceException {
    KeyPairGenerator keyPairGenerator;
    keyStoreLock.writeLock().lock();
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
        cert = X509CertificateUtil.generateCertificate(dn, KPair, 365, this.selfSigningCertificateAlgorithm, ekuOids);
      }
      else {
        String dn = buildDistinguishedName(hostname);
        cert = X509CertificateUtil.generateCertificate(dn, KPair, 365, this.selfSigningCertificateAlgorithm, ekuOids);
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
    } finally {
      keyStoreLock.writeLock().unlock();
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
    checkExistingCredentialStore(clusterName);
    createKeyStore(credentialStorePath(clusterName), this.credentialStoreType, masterService.getMasterSecret());
  }

  private void checkExistingCredentialStore(String clusterName) {
    final File[] existingClusterCredentialStoreFiles = keyStoreDirPath.toFile().listFiles((FileFilter) new PrefixFileFilter(clusterName + CREDENTIALS_SUFFIX));
    if (existingClusterCredentialStoreFiles != null) {
      for (File existingClusterCredentialStoreFile : existingClusterCredentialStoreFiles) {
        String existingCredentialStoreType = FilenameUtils.getExtension(existingClusterCredentialStoreFile.getName());
        if (!this.credentialStoreType.equals(existingCredentialStoreType)) {
          LOG.credentialStoreForClusterFoundWithDifferentType(clusterName, existingCredentialStoreType);
        }
      }
    }
  }

  @Override
  public boolean isCredentialStoreForClusterAvailable(String clusterName) throws KeystoreServiceException {
    final Path keyStoreFilePath = credentialStorePath(clusterName);
    try {
      return isKeyStoreAvailable(keyStoreFilePath, this.credentialStoreType, masterService.getMasterSecret());
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
    // Read the key from a private copy: once a BCFKS store has handed out a private key, it returns
    // that key for any passphrase, so a shared instance would skip the passphrase check
    return getKeyFromKeystore(getSigningKeystore(keystoreName, false), alias, passphrase);
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
  public KeyStore getCredentialStoreForCluster(String clusterName) throws KeystoreServiceException {
    // Do not fail getting the credential store if the keystore file does not exist.  The returned
    // KeyStore will be empty.  This seems like a potential bug, but is the behavior before KNOX-1812
    return getKeystore(credentialStorePath(clusterName), this.credentialStoreType, null, false, true);
  }

  private Path credentialStorePath(String clusterName) {
    return keyStoreDirPath.resolve(clusterName + this.credentialsSuffix);
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
    keyStoreLock.writeLock().lock();
    try {
      removeFromCache(clusterName, credentials.keySet());
      // Change a private copy; the cached instance is shared with readers
      final Path keyStoreFilePath = credentialStorePath(clusterName);
      KeyStore ks = loadKeyStore(keyStoreFilePath, this.credentialStoreType, masterService.getMasterSecret());
      try {
        // Add all the credential keys to the keystore
        for (Map.Entry<String, String> credential : credentials.entrySet()) {
          final Key key = new SecretKeySpec(credential.getValue().getBytes(StandardCharsets.UTF_8), this.credentialStoreAlgorithm);
          ks.setKeyEntry(credential.getKey(), key, masterService.getMasterSecret(), null);
        }

        // Write all the changes once
        writeKeyStoreToFile(ks, keyStoreFilePath, masterService.getMasterSecret());
        addToCache(clusterName, credentials);
      } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException e) {
        LOG.failedToAddCredentialForCluster(clusterName, e);
      }
    } finally {
      keyStoreLock.writeLock().unlock();
    }
  }

  @Override
  public char[] getCredentialForCluster(String clusterName, String alias)
      throws KeystoreServiceException {
    char[] credential = checkCache(clusterName, alias);
    if (credential != null) {
      return credential;
    }

    // Holding the read lock keeps writers from changing the alias between reading it from the store
    // and putting it into the cache
    keyStoreLock.readLock().lock();
    try {
      credential = checkCache(clusterName, alias);
      if (credential == null) {
        KeyStore ks = getCredentialStoreForCluster(clusterName);
        if (ks != null) {
          try {
            credential = getCredentialForCluster(clusterName, alias, ks);
          } catch (KeystoreServiceException e) {
            LOG.failedToGetCredentialForCluster(clusterName, e);
          }
        }
      }
    } finally {
      keyStoreLock.readLock().unlock();
    }

    return credential;
  }

  @Override
  public char[] getCredentialForCluster(String clusterName, String alias, KeyStore ks) throws KeystoreServiceException {
    try {
      char[] credential = null;
      Key credentialKey = ks.getKey(alias, masterService.getMasterSecret());
      if (credentialKey == null) {
        credentialKey = ks.getKey(alias.toLowerCase(Locale.ROOT), masterService.getMasterSecret());
      }
      if (credentialKey != null) {
        final String credentialString = new String(credentialKey.getEncoded(), StandardCharsets.UTF_8);
        credential = credentialString.toCharArray();
        addToCache(clusterName, alias, credentialString);
      }
      return credential;
    } catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e) {
      throw new KeystoreServiceException(e);
    }
  }

  @Override
  public void removeCredentialForCluster(String clusterName, String alias) throws KeystoreServiceException {
    removeCredentialsForCluster(clusterName, Collections.singleton(alias));
  }

  @Override
  public void removeCredentialsForCluster(String clusterName, Set<String> aliases) throws KeystoreServiceException {
    // Needed to prevent read then write synchronization issue where alias is not removed
    keyStoreLock.writeLock().lock();
    try {
      // Change a private copy; the cached instance is shared with readers
      final Path keyStoreFilePath = credentialStorePath(clusterName);
      KeyStore ks = loadKeyStore(keyStoreFilePath, this.credentialStoreType, masterService.getMasterSecret());
      try {
        // Delete all the entries
        for (String alias : aliases) {
          if (ks.containsAlias(alias)) {
            ks.deleteEntry(alias);
          }
        }
        removeFromCache(clusterName, aliases);

        // Update the keystore file once to reflect all the alias deletions
        writeKeyStoreToFile(ks, keyStoreFilePath, masterService.getMasterSecret());
      } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException e) {
        LOG.failedToRemoveCredentialForCluster(clusterName, e);
      }
    } finally {
      keyStoreLock.writeLock().unlock();
    }
  }

  /**
   * Safe to call without holding the keystore lock.
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
   * The keystore file is loaded under the service's read lock, so it is not loaded while a writer
   * changes a keystore file.
   * <p>
   * If <code>cacheable</code> is <code>true</code> and the keystore file cache is enabled, the
   * returned {@link KeyStore} is shared with other callers and must not be changed. It is loaded again
   * only when the file changes (see {@link #getCachedKeyStore(Path, String, char[])}).
   *
   * @param keystorePath        the path to the keystore file
   * @param keystoreType        the type of keystore file
   * @param alias               the alias for the password to the keystore file (see {@link #getKeyStorePassword(String)})
   * @param failIfNotAccessible <code>true</code> to ensure the keystore file exists and is readable; <code>false</code> to not check
   * @param cacheable           <code>true</code> if the caller only reads entries other than private keys from the keystore
   * @return a {@link KeyStore}, or <code>null</code> if the requested keystore cannot be created
   * @throws KeystoreServiceException if an error occurs loading the keystore file
   */
  private KeyStore getKeystore(Path keystorePath, String keystoreType, String alias,
                               boolean failIfNotAccessible, boolean cacheable)
      throws KeystoreServiceException {
    final boolean useCache = cacheable && keyStoreFileCacheEnabled;
    if (useCache) {
      // An unchanged file passed the checks below when it was loaded, so a single stat is enough
      final CachedKeyStore cached = keyStoreCache.get(keyStoreCacheKey(keystorePath));
      if (cached != null
          && cached.isCurrent(keystoreType, digest(getKeyStorePassword(alias)), KeyStoreFileStamp.read(keystorePath))) {
        return cached.getKeyStore();
      }
    }

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

    final char[] password = getKeyStorePassword(alias);
    keyStoreLock.readLock().lock();
    try {
      return useCache
          ? getCachedKeyStore(keystorePath, keystoreType, password)
          : loadKeyStoreExclusively(keystorePath, keystoreType, password);
    } finally {
      keyStoreLock.readLock().unlock();
    }
  }

  /**
   * Returns the cached {@link KeyStore} for the file, loading it first if it is not cached yet, its file
   * has changed since it was loaded, or it was loaded with a different type or password.
   * <p>
   * A file is considered unchanged as long as its modification time, size and file key stay the same.
   * Lookups of a current entry do not get here (see {@link #getKeystore(Path, String, String, boolean, boolean)}),
   * so they never lock and never wait for an unrelated load.
   * <p>
   * Called with the read lock held.
   */
  private KeyStore getCachedKeyStore(Path keyStoreFilePath, String storeType, char[] password)
      throws KeystoreServiceException {
    final Path cacheKey = keyStoreCacheKey(keyStoreFilePath);
    final byte[] passwordDigest = digest(password);

    synchronized (keyStoreLoadLock(keyStoreFilePath)) {
      // Taken before loading, so a change made during the load leaves a stamp that does not match the file
      final long loadStartedAt = System.currentTimeMillis();
      final KeyStoreFileStamp stamp = KeyStoreFileStamp.read(keyStoreFilePath);

      // Another thread may have loaded the file while this one waited
      final CachedKeyStore cached = keyStoreCache.get(cacheKey);
      if (cached != null && cached.isCurrent(storeType, passwordDigest, stamp)) {
        return cached.getKeyStore();
      }

      final KeyStore keyStore = loadKeyStore(keyStoreFilePath, storeType, password);
      keyStoreCache.put(cacheKey, new CachedKeyStore(storeType, passwordDigest, stamp, keyStore, loadStartedAt));
      return keyStore;
    }
  }

  /**
   * Loads a private copy of the keystore file, which the caller is free to change.
   * <p>
   * Called with the read lock held.
   */
  private KeyStore loadKeyStoreExclusively(Path keyStoreFilePath, String storeType, char[] password)
      throws KeystoreServiceException {
    synchronized (keyStoreLoadLock(keyStoreFilePath)) {
      return loadKeyStore(keyStoreFilePath, storeType, password);
    }
  }

  private Object keyStoreLoadLock(Path keyStoreFilePath) {
    return keyStoreLoadLocks.computeIfAbsent(keyStoreCacheKey(keyStoreFilePath), path -> new Object());
  }

  private static Path keyStoreCacheKey(Path keyStoreFilePath) {
    return keyStoreFilePath.toAbsolutePath().normalize();
  }

  private static byte[] digest(char[] password) {
    if (password == null) {
      return new byte[0];
    }
    final ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
    try {
      final MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
      messageDigest.update(encoded.duplicate());
      return messageDigest.digest();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    } finally {
      if (encoded.hasArray()) {
        Arrays.fill(encoded.array(), (byte) 0);
      }
    }
  }

  private boolean isKeyStoreAvailable(final Path keyStoreFilePath, String storeType,
                                      char[] password)
      throws KeyStoreException, IOException {
    keyStoreLock.readLock().lock();
    try {
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
    } finally {
      keyStoreLock.readLock().unlock();
    }
  }

  // Package private for unit test access
  KeyStore createKeyStore(Path keystoreFilePath, String keystoreType, char[] password) throws KeystoreServiceException {
    keyStoreLock.writeLock().lock();
    try {
      return createKeyStoreLocked(keystoreFilePath, keystoreType, password);
    } finally {
      keyStoreLock.writeLock().unlock();
    }
  }

  private KeyStore createKeyStoreLocked(Path keystoreFilePath, String keystoreType, char[] password) throws KeystoreServiceException {
    // Ensure the parent directory exists...
    // This is symlink safe.
    Path parentPath = keystoreFilePath.getParent();
    if (parentPath != null && !Files.isDirectory(parentPath)) {
      try{
        // This will attempt to create all missing directories.  No failures will occur if the
        // directories already exist.
        Files.createDirectories(parentPath);
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
  // Callers hold either the write lock, or the read lock and the file's load lock
  KeyStore loadKeyStore(final Path keyStoreFilePath, final String storeType,
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

  @Override
  public KeyStore loadKeyStore(String path, String keystoreType, String password)
          throws KeystoreServiceException {
      try {
          return createKeyStore(FileSystems.getDefault().getPath(path), keystoreType, password.toCharArray());
      } catch (Exception e) {
          throw new KeystoreServiceException(e);
      }
  }

  @Override
  public KeyStore loadTruststore(String path,  String keystoreType, String password)
          throws KeystoreServiceException {
      keyStoreLock.readLock().lock();
      try {
          return loadKeyStoreExclusively(FileSystems.getDefault().getPath(path), keystoreType, password.toCharArray());
      } catch (Exception e) {
          throw new KeystoreServiceException(e);
      } finally {
          keyStoreLock.readLock().unlock();
      }
  }

  // Package private for unit test access
  void writeKeyStoreToFile(final KeyStore keyStore, final Path path, char[] password)
      throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
    keyStoreLock.writeLock().lock();
    try {
      // TODO: backup the keystore on disk before attempting a write and restore on failure
      try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.WRITE,
          StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
        fileChannel.lock();
        try (OutputStream out = Channels.newOutputStream(fileChannel)) {
          keyStore.store(out, password);
        }
      } finally {
        // Also when the write failed, as the file may be partially written
        keyStoreCache.remove(keyStoreCacheKey(path));
      }
    } finally {
      keyStoreLock.writeLock().unlock();
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
