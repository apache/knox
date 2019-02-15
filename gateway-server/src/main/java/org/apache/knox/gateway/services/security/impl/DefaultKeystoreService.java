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
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
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
  private static final String CREDENTIAL_STORE_SUFFIX = "-credentials.jceks";
  private static final String CREDENTIAL_STORE_TYPE = "JCEKS";
  private static final String GATEWAY_KEYSTORE_FILE_NAME = "gateway.jks";
  private static final String GATEWAY_KEYSTORE_FILE_EXTENSION = ".jks";
  private static final String GATEWAY_KEYSTORE_TYPE = "JKS";
  private static final String NO_CLUSTER_NAME = "__gateway";
  private static final String CERT_GEN_MODE = "hadoop.gateway.cert.gen.mode";
  private static final String CERT_GEN_MODE_LOCALHOST = "localhost";
  private static final String CERT_GEN_MODE_HOSTNAME = "hostname";
  private static GatewayMessages LOG = MessagesFactory.get( GatewayMessages.class );

  private Map<String, Map<String, char[]>> cache = new ConcurrentHashMap<>();

  private File defaultKeystoreDirectory;

  private File signingKeystoreFile;
  private String signingKeystorePasswordAlias;
  private String signingKeystoreType;
  private String signingKeyAlias;

  private File identityKeystoreFile;
  private String identityKeystorePasswordAlias;
  private String identityKeystoreType;
  private String identityKeyAlias;

  private Lock readLock;
  private Lock writeLock;

  @Override
  public void init(GatewayConfig config, Map<String, String> options)
      throws ServiceLifecycleException {

    ReadWriteLock lock = new ReentrantReadWriteLock(true);
    readLock = lock.readLock();
    writeLock = lock.writeLock();

    defaultKeystoreDirectory = new File(config.getGatewayKeystoreDir());

    initIdentityKeystore(config);
    initSigningKeystore(config);
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
      createKeystore(identityKeystoreFile, identityKeystoreType, getIdentityKeystorePassword());
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public KeyStore getKeystoreForGateway() throws KeystoreServiceException {
    readLock.lock();
    try {
      return getKeystore(identityKeystoreFile, identityKeystoreType, getIdentityKeystorePassword());
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
    File keystoreFile;
    char[] password;
    String keystoreType;

    if (keystoreName != null) {
      keystoreFile = new File(defaultKeystoreDirectory, keystoreName + GATEWAY_KEYSTORE_FILE_EXTENSION);
      password = getMasterSecret();
      keystoreType = GATEWAY_KEYSTORE_TYPE;
    } else {
      keystoreFile = signingKeystoreFile;
      password = getSigningKeystorePassword();
      keystoreType = signingKeystoreType;
    }

    // make sure the keystore exists
    if (!keystoreFile.exists()) {
      throw new KeystoreServiceException("Configured signing keystore does not exist.");
    }
    readLock.lock();
    try {
      return getKeystore(keystoreFile, keystoreType, password);
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

        writeKeystoreToFile(privateKS, getIdentityKeystorePassword(), identityKeystoreFile);
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
    File credentialStoreFile = new File(defaultKeystoreDirectory, clusterName + CREDENTIAL_STORE_SUFFIX);
    writeLock.lock();
    try {
      createKeystore(credentialStoreFile, CREDENTIAL_STORE_TYPE, getMasterSecret());
    }
    finally {
      writeLock.unlock();
    }
  }

  @Override
  public boolean isCredentialStoreForClusterAvailable(String clusterName) throws KeystoreServiceException {
    boolean rc;
    final File keyStoreFile = new File(defaultKeystoreDirectory, clusterName + CREDENTIAL_STORE_SUFFIX);
    readLock.lock();
    try {
      try {
        rc = isKeystoreAvailable(keyStoreFile, CREDENTIAL_STORE_TYPE, getMasterSecret());
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
    readLock.lock();
    try {
      try {
        return isKeystoreAvailable(identityKeystoreFile, identityKeystoreType, getIdentityKeystorePassword());
      } catch (KeyStoreException | IOException e) {
        throw new KeystoreServiceException(e);
      }
    }
    finally {
      readLock.unlock();
    }
  }

  @Override
  public Key getKeyForGateway(String alias, char[] passphrase) throws KeystoreServiceException {
    readLock.lock();
    try {
      try {
        return getKey(alias, getKeystoreForGateway(), ensurePassphrase(passphrase));
      } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException e) {
        LOG.failedToGetKeyForGateway( alias, e );
      }
      return null;
    }
    finally {
      readLock.unlock();
    }
  }

  @Override
  public Key getSigningKey(String alias, char[] passphrase) throws KeystoreServiceException {
    return getSigningKey(null, alias, passphrase);
  }

  @Override
  public Key getSigningKey(String keystoreName, String alias, char[] passphrase) throws KeystoreServiceException {
    readLock.lock();
    try {
      try {
        return getKey(alias, getSigningKeystore(keystoreName), ensurePassphrase(passphrase));
      } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException e) {
        LOG.failedToGetKeyForGateway(alias, e);
      }

      return null;
    }
    finally {
      readLock.unlock();
    }
  }

  @Override
  public KeyStore getCredentialStoreForCluster(String clusterName)
      throws KeystoreServiceException {
    final File keyStoreFile = new File(defaultKeystoreDirectory, clusterName + CREDENTIAL_STORE_SUFFIX);
    readLock.lock();
    try {
      return getKeystore(keyStoreFile, CREDENTIAL_STORE_TYPE, getMasterSecret());
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
      addCredential(alias, value, ks, getMasterSecret());
      final File keyStoreFile = new File(defaultKeystoreDirectory, clusterName + CREDENTIAL_STORE_SUFFIX);
      try {
        writeKeystoreToFile(ks, getMasterSecret(), keyStoreFile);
      } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException e) {
        LOG.failedToAddCredentialForCluster(clusterName, e);
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
            credential = getCredential(alias, ks, getMasterSecret());
            if (credential != null) {
              addToCache(clusterName, alias, credential);
            }
          } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException e) {
            LOG.failedToGetCredentialForCluster(clusterName, e);
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
    final File keyStoreFile = new File(defaultKeystoreDirectory, clusterName + CREDENTIAL_STORE_SUFFIX);
    writeLock.lock();
    try {
      removeFromCache(clusterName, alias);
      KeyStore ks = getCredentialStoreForCluster(clusterName);
      removeCredential(alias, ks);
      try {
        writeKeystoreToFile(ks, getMasterSecret(), keyStoreFile);
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
    Map<String, char[]> clusterCache = cache.get(clusterName);
    if (clusterCache == null) {
      return null;
    }
    return clusterCache.get(alias);
  }

  /**
   * Called only from within critical sections of other methods above.
   */
  private void addToCache(String clusterName, String alias, char[] credentialString) {
    Map<String, char[]> clusterCache = cache.computeIfAbsent(clusterName, k -> new HashMap<>());
    clusterCache.put(alias, credentialString);
  }

  /**
   * Called only from within critical sections of other methods above.
   */
  private void removeFromCache(String clusterName, String alias) {
    Map<String, char[]> clusterCache = cache.get(clusterName);
    if (clusterCache == null) {
      return;
    }
    clusterCache.remove(alias);
  }

  @Override
  public String getKeystorePath() {
    return identityKeystoreFile.getAbsolutePath();
  }

  @Override
  public Certificate getGatewayIdentityCertificate() throws KeystoreServiceException {
    try {
      Certificate cert = getCertificate(identityKeyAlias, getKeystoreForGateway());

      if (cert == null) {
        LOG.gatewayIdentityCertificateNotFound();
        throw new KeystoreServiceException("The identity certificate for the Gateway was not found.");
      }

      return cert;
    } catch (KeyStoreException e) {
      LOG.unableToRetrieveGatewayIdentityCertificate(e);
      throw new KeystoreServiceException(e);
    }
  }

  @Override
  public Key getGatewayIdentityKey(char[] passphrase) throws KeystoreServiceException {
    return getKeyForGateway(identityKeyAlias, passphrase);
  }

  /**
   * Initialize the values related to the identity keystore by processing values in the Gateway
   * configuration.
   * <p>
   * This keystore may or may not be the default keystore (.../data/security/keystores/gateway.jks)
   * depending on the value of <code>gateway.tls.keystore.path</code>.
   *
   * @param gatewayConfig the configuration details
   */
  private void initIdentityKeystore(GatewayConfig gatewayConfig) {
    // determine the location of the identity keystore
    String keystorePath = gatewayConfig.getIdentityKeystorePath();
    if (StringUtils.isEmpty(keystorePath)) {
      // Use a calculated default path...
      identityKeystoreFile = new File(defaultKeystoreDirectory, GATEWAY_KEYSTORE_FILE_NAME);
      identityKeystorePasswordAlias = GatewayConfig.DEFAULT_IDENTITY_KEYSTORE_PASSWORD_ALIAS;
      identityKeystoreType = GATEWAY_KEYSTORE_TYPE;
      identityKeyAlias = GatewayConfig.DEFAULT_IDENTITY_KEY_ALIAS;
    } else {
      identityKeystoreFile = new File(keystorePath);
      identityKeystorePasswordAlias = gatewayConfig.getIdentityKeystorePasswordAlias();
      identityKeystoreType = gatewayConfig.getIdentityKeystoreType();
      if(StringUtils.isEmpty(identityKeystoreType)) {
        identityKeystoreType = GATEWAY_KEYSTORE_TYPE;
      }
      identityKeyAlias = gatewayConfig.getIdentityKeyAlias();
    }
  }

  /**
   * Initialize the values related to the signing keystore by processing values in the Gateway
   * configuration.
   * <p>
   * This keystore may or may not be the default keystore (.../data/security/keystores/gateway.jks)
   * depending on the value of <code>gateway.signing.keystore.name</code>.  If a custom signing
   * keystore is specified but cannot be found, then a {@link ServiceLifecycleException} is thrown.
   *
   * @param gatewayConfig the configuration details
   * @throws ServiceLifecycleException if an error occurs validating the custom keystore for the
   *                                   signing key
   */
  private void initSigningKeystore(GatewayConfig gatewayConfig) throws ServiceLifecycleException {
    String signingKeystoreName = gatewayConfig.getSigningKeystoreName();
    // ensure that the keystore actually exists and fail to start if not
    if (signingKeystoreName != null) {
      signingKeystoreFile = new File(defaultKeystoreDirectory, signingKeystoreName);
      signingKeystorePasswordAlias = GatewayConfig.DEFAULT_SIGNING_KEYSTORE_PASSWORD_ALIAS;
      signingKeystoreType = gatewayConfig.getSigningKeystoreType();
      signingKeyAlias = gatewayConfig.getSigningKeyAlias();

      if (!signingKeystoreFile.exists()) {
        throw new ServiceLifecycleException("Configured signing keystore does not exist.");
      }

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
    } else {
      // The signing keystore is the same as the default identity keystore...
      // TODO: BROKEN IF IDENTITY STORE IS CUSTOMIZED DUE TO PASSWORD ALIASES
      signingKeystoreFile = new File(defaultKeystoreDirectory, GATEWAY_KEYSTORE_FILE_NAME);
      signingKeystorePasswordAlias = GatewayConfig.DEFAULT_IDENTITY_KEYSTORE_PASSWORD_ALIAS;
      signingKeystoreType = GatewayConfig.DEFAULT_IDENTITY_KEYSTORE_TYPE;
      signingKeyAlias = GatewayConfig.DEFAULT_IDENTITY_KEY_ALIAS;
    }

    // TODO: Possibly use the configured identity keystore
  }

  /**
   * Determines the password for the configured identity keystore.
   * <p>
   * If an alias is specified in the configuration (gateway.tls.keystore.password.alias), the use that
   * to look it up in the Gateway's credential store, else use the master secret.
   * <p>
   * If an alias is configured, and does not exist in the credential store, the master password will
   * be assumed for backwards compatibility.
   *
   * @return a password
   * @throws KeystoreServiceException if a failure occurs while attempting to obtain the password
   *                                  from the credential store
   * @see DefaultAliasService#getGatewayIdentityKeystorePassword()
   */
  private char[] getIdentityKeystorePassword() throws KeystoreServiceException {
    char[] password = null;
    if (StringUtils.isNotEmpty(identityKeystorePasswordAlias)) {
      password = getCredentialForCluster(NO_CLUSTER_NAME, identityKeystorePasswordAlias);
    }
    return (password == null) ? getMasterSecret() : password;
  }

  private char[] getSigningKeystorePassword() throws KeystoreServiceException {
    char[] password = null;
    if (StringUtils.isNotEmpty(signingKeystorePasswordAlias)) {
      password = getCredentialForCluster(NO_CLUSTER_NAME, signingKeystorePasswordAlias);
    }
    return (password == null) ? getMasterSecret() : password;
  }

  /**
   * Ensures a passphrase is provided.
   * <p>
   * If the supplied passphrase is <code>null</code>, returns the master secret; else returns the
   * supplied passphrase.
   *
   * @param passphrase the passphrase, or <code>null</code>
   * @return a passphrase
   */
  private char[] ensurePassphrase(char[] passphrase) {
    if (passphrase == null) {
      passphrase = getMasterSecret();
      LOG.assumingKeyPassphraseIsMaster();
    }
    return passphrase;
  }
}
