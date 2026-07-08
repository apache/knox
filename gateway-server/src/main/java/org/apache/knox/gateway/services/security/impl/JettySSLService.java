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

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.security.SSLService;
import org.apache.knox.gateway.util.X500PrincipalParser;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class JettySSLService implements SSLService {
  private static final String EPHEMERAL_DH_KEY_SIZE_PROPERTY = "jdk.tls.ephemeralDHKeySize";
  private static final String GATEWAY_CREDENTIAL_STORE_NAME = "__gateway";
  // Extended Key Usage OIDs (RFC 5280) used to enforce single-purpose certificates in single-EKU mode.
  private static final String EKU_SERVER_AUTH = "1.3.6.1.5.5.7.3.1";
  private static final String EKU_CLIENT_AUTH = "1.3.6.1.5.5.7.3.2";
  private static GatewayMessages log = MessagesFactory.get( GatewayMessages.class );

  private KeystoreService keystoreService;
  private AliasService aliasService;

  public void setAliasService(AliasService as) {
    this.aliasService = as;
  }

  public void setKeystoreService(KeystoreService ks) {
    this.keystoreService = ks;
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options)
      throws ServiceLifecycleException {
    // set any JSSE or security related system properties
    System.setProperty(EPHEMERAL_DH_KEY_SIZE_PROPERTY, config.getEphemeralDHKeySize());
    try {
      if (!keystoreService.isCredentialStoreForClusterAvailable(GATEWAY_CREDENTIAL_STORE_NAME)) {
        log.creatingCredentialStoreForGateway();
        keystoreService.createCredentialStoreForCluster(GATEWAY_CREDENTIAL_STORE_NAME);
        // LET'S NOT GENERATE A DIFFERENT KEY PASSPHRASE BY DEFAULT ANYMORE
        // IF A DEPLOYMENT WANTS TO CHANGE THE KEY PASSPHRASE TO MAKE IT MORE SECURE THEN
        // THEY CAN ADD THE ALIAS EXPLICITLY WITH THE CLI
        // as.generateAliasForCluster(GATEWAY_CREDENTIAL_STORE_NAME, GATEWAY_IDENTITY_PASSPHRASE);
      }
      else {
        log.credentialStoreForGatewayFoundNotCreating();
      }
    } catch (KeystoreServiceException e) {
      throw new ServiceLifecycleException("Keystore was not loaded properly - the provided password may not match the password for the keystore.", e);
    }

    try {
      if (!keystoreService.isKeystoreForGatewayAvailable()) {
        log.creatingKeyStoreForGateway();
        keystoreService.createKeystoreForGateway();
        char[] passphrase;
        try {
          passphrase = aliasService.getGatewayIdentityPassphrase();
        } catch (AliasServiceException e) {
          throw new ServiceLifecycleException("Error accessing credential store for the gateway.", e);
        }
        if (config.isSingleEkuEnabled()) {
          // Self-bootstrap single-purpose identities, in-line with dual-EKU self-signing.
          log.generatingSingleEkuIdentity("serverAuth", config.getIdentityKeyAlias());
          keystoreService.addSelfSignedCertForGateway(config.getIdentityKeyAlias(), passphrase, null, EKU_SERVER_AUTH);
          // Co-locate a clientAuth identity only when outbound two-way SSL will use it.
          if (config.isHttpClientTwoWaySslEnabled()) {
            char[] httpClientPassphrase;
            try {
              httpClientPassphrase = aliasService.getHttpClientKeyPassphrase();
            } catch (AliasServiceException e) {
              throw new ServiceLifecycleException("Error accessing credential store for the gateway.", e);
            }
            log.generatingSingleEkuIdentity("clientAuth", config.getHttpClientKeyAlias());
            keystoreService.addSelfSignedCertForGateway(config.getHttpClientKeyAlias(), httpClientPassphrase, null, EKU_CLIENT_AUTH);
          }
        } else {
          keystoreService.addSelfSignedCertForGateway(config.getIdentityKeyAlias(), passphrase);
        }
      }
      else {
        log.keyStoreForGatewayFoundNotCreating();
      }
      logAndValidateCertificate(config);
      if (config.isSingleEkuEnabled()) {
        validateSingleEkuConfig(config);
      }
    } catch (KeystoreServiceException e) {
      throw new ServiceLifecycleException("The identity keystore was not loaded properly - the provided password may not match the password for the keystore.", e);
    }
  }

  private void logAndValidateCertificate(GatewayConfig config) throws ServiceLifecycleException {
    // let's log the hostname (CN) and cert expiry from the gateway's public cert to aid in SSL debugging
    Certificate cert;
    final String identityKeyAlias = config.getIdentityKeyAlias();
    try {
      cert = aliasService.getCertificateForGateway(identityKeyAlias);
    } catch (AliasServiceException e) {
      throw new ServiceLifecycleException("Cannot Retreive Gateway SSL Certificate. Server will not start.", e);
    }
    if (cert != null) {
      if (cert instanceof X509Certificate) {
        X500Principal x500Principal = ((X509Certificate)cert).getSubjectX500Principal();
        X500PrincipalParser parser = new X500PrincipalParser(x500Principal);
        log.certificateHostNameForGateway(parser.getCN());
        Date notBefore = ((X509Certificate) cert).getNotBefore();
        Date notAfter = ((X509Certificate) cert).getNotAfter();
        log.certificateValidityPeriod(notBefore, notAfter);

        // let's not even start if the current date is not within the validity period for the SSL cert
        try {
          ((X509Certificate)cert).checkValidity();
        } catch (CertificateExpiredException e) {
          throw new ServiceLifecycleException("Gateway SSL Certificate is Expired. Server will not start.", e);
        } catch (CertificateNotYetValidException e) {
          throw new ServiceLifecycleException("Gateway SSL Certificate is not yet valid. Server will not start.", e);
        }
      } else {
        throw new ServiceLifecycleException("Public certificate for the gateway is not of the expected type of  . Something is wrong with the gateway keystore.");
      }
    } else {
      throw new ServiceLifecycleException("Public certificate for the gateway cannot be found with the alias " + identityKeyAlias + ". Please check the identity certificate alias.");
    }
  }

  // Package private for unit test access.
  // Single-EKU mode guarantees every certificate Knox uses is single-purpose. It is INDEPENDENT of
  // whether mTLS is enabled in either direction — validate only what is actually used:
  //   - ALWAYS: the server identity certificate must be serverAuth-only (Knox always presents it).
  //   - INBOUND mTLS (client.auth.needed/wanted): the server truststore must be configured.
  //   - OUTBOUND mTLS (httpclient.twoWaySsl.enabled): the client-identity keystore must hold a usable
  //     clientAuth-only key entry, and the outbound HTTP client truststore must be configured.
  // Fail closed: reject a cross-wired / dual-purpose / EKU-less certificate at startup rather than
  // deferring the discovery to a runtime handshake failure.
  void validateSingleEkuConfig(GatewayConfig config) throws ServiceLifecycleException {
    // ALWAYS: the server identity certificate is presented on every inbound TLS connection.
    String identityKeyAlias = config.getIdentityKeyAlias();
    Certificate serverCert;
    try {
      KeyStore serverKeystore = keystoreService.getKeystoreForGateway();
      serverCert = serverKeystore == null ? null : serverKeystore.getCertificate(identityKeyAlias);
    } catch (KeystoreServiceException | KeyStoreException e) {
      throw new ServiceLifecycleException("Single-EKU mode is enabled but the server identity certificate for "
          + "alias '" + identityKeyAlias + "' could not be read. Server will not start.", e);
    }
    validateSinglePurposeEku(serverCert, identityKeyAlias, "server identity keystore",
        EKU_SERVER_AUTH, "serverAuth", EKU_CLIENT_AUTH, "clientAuth");

    // INBOUND mTLS: only when client authentication is enabled does Knox need a truststore to
    // validate incoming client certificates.
    if ((config.isClientAuthNeeded() || config.isClientAuthWanted()) && config.getTruststorePath() == null) {
      throw new ServiceLifecycleException("Single-EKU mode is enabled with inbound client authentication but "
          + "the server truststore (" + GatewayConfig.GATEWAY_TRUSTSTORE_PATH + ") is not configured. "
          + "Server will not start.");
    }

    // OUTBOUND mTLS: only when two-way SSL is enabled is the client identity presented, so only then
    // must it exist and be clientAuth-only, and only then is an outbound truststore required.
    if (config.isHttpClientTwoWaySslEnabled()) {
      String clientKeyAlias = config.getHttpClientKeyAlias();
      KeyStore clientKeystore;
      try {
        clientKeystore = keystoreService.getKeystoreForHttpClient();
        if (clientKeystore == null || !clientKeystore.isKeyEntry(clientKeyAlias)) {
          log.singleEkuClientIdentityNotAvailable(clientKeyAlias);
          throw new ServiceLifecycleException("Single-EKU mode is enabled with outbound two-way SSL but no "
              + "HTTP client key entry for alias '" + clientKeyAlias + "' ("
              + GatewayConfig.HTTP_CLIENT_KEY_ALIAS + ") is available (configure "
              + GatewayConfig.HTTP_CLIENT_KEYSTORE_PATH + " or let single-EKU generate one). "
              + "Server will not start.");
        }
      } catch (KeystoreServiceException | KeyStoreException e) {
        throw new ServiceLifecycleException("Single-EKU mode is enabled with outbound two-way SSL but the "
            + "HTTP client keystore could not be loaded. Server will not start.", e);
      }

      Certificate clientCert;
      try {
        clientCert = clientKeystore.getCertificate(clientKeyAlias);
      } catch (KeyStoreException e) {
        throw new ServiceLifecycleException("Single-EKU mode is enabled but the certificate for alias '"
            + clientKeyAlias + "' in the HTTP client keystore could not be read. Server will not start.", e);
      }
      validateSinglePurposeEku(clientCert, clientKeyAlias, "HTTP client keystore",
          EKU_CLIENT_AUTH, "clientAuth", EKU_SERVER_AUTH, "serverAuth");

      String httpClientTruststorePath = config.getHttpClientTruststorePath();
      if (httpClientTruststorePath == null || httpClientTruststorePath.isEmpty()) {
        log.singleEkuHttpClientTruststoreNotConfigured(GatewayConfig.HTTP_CLIENT_TRUSTSTORE_PATH);
        throw new ServiceLifecycleException("Single-EKU mode is enabled with outbound two-way SSL but "
            + GatewayConfig.HTTP_CLIENT_TRUSTSTORE_PATH + " is not configured. Knox cannot verify TLS "
            + "certificates of upstream services. Server will not start.");
      }
    }
  }

  // Fail closed unless the given certificate declares exactly the required single Extended Key Usage:
  // it must carry requiredEku and must NOT carry forbiddenEku. A certificate with no EKU extension is
  // rejected because single-EKU mode is an explicit opt-in to purpose-restricted certificates, and an
  // unrestricted (EKU-less) cert is usable for any purpose — defeating the guarantee.
  private void validateSinglePurposeEku(Certificate cert, String alias, String storeDesc,
      String requiredEku, String requiredEkuName, String forbiddenEku, String forbiddenEkuName)
      throws ServiceLifecycleException {
    if (!(cert instanceof X509Certificate)) {
      throw new ServiceLifecycleException("Single-EKU mode is enabled but the certificate for alias '"
          + alias + "' in the " + storeDesc + " is missing or is not an X.509 certificate. Server will not start.");
    }
    List<String> ekus;
    try {
      ekus = ((X509Certificate) cert).getExtendedKeyUsage();
    } catch (CertificateParsingException e) {
      throw new ServiceLifecycleException("Single-EKU mode is enabled but the Extended Key Usage of the "
          + "certificate for alias '" + alias + "' in the " + storeDesc + " could not be parsed. "
          + "Server will not start.", e);
    }
    if (ekus == null || !ekus.contains(requiredEku)) {
      throw new ServiceLifecycleException("Single-EKU mode is enabled but the certificate for alias '"
          + alias + "' in the " + storeDesc + " does not declare the required " + requiredEkuName
          + " Extended Key Usage. Server will not start.");
    }
    if (ekus.contains(forbiddenEku)) {
      throw new ServiceLifecycleException("Single-EKU mode is enabled but the certificate for alias '"
          + alias + "' in the " + storeDesc + " also declares the " + forbiddenEkuName
          + " Extended Key Usage; single-EKU mode requires a single-purpose certificate. Server will not start.");
    }
  }

  @Override
  public Object buildSslContextFactory(GatewayConfig config) throws AliasServiceException {
    String identityKeystorePath = config.getIdentityKeystorePath();
    String identityKeystoreType = config.getIdentityKeystoreType();
    String identityKeyAlias = config.getIdentityKeyAlias();

    SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
    sslContextFactory.setCertAlias( identityKeyAlias );
    sslContextFactory.setKeyStoreType(identityKeystoreType);
    sslContextFactory.setKeyStorePath(identityKeystorePath );

    char[] keystorePasswordChars;
    try {
      keystorePasswordChars = aliasService.getGatewayIdentityKeystorePassword();
    } catch (AliasServiceException e) {
      log.failedToGetPasswordForGatewayIdentityKeystore(e);
      throw e;
    }
    if(keystorePasswordChars != null) {
      sslContextFactory.setKeyStorePassword(new String(keystorePasswordChars));
    }

    char[] keypass;
    try {
      keypass = aliasService.getGatewayIdentityPassphrase();
    } catch (AliasServiceException e) {
      log.failedToGetPassphraseForGatewayIdentityKey(e);
      throw e;
    }
    if(keypass != null) {
      sslContextFactory.setKeyManagerPassword(new String(keypass));
    }

    boolean clientAuthNeeded = config.isClientAuthNeeded();
    boolean clientAuthWanted = config.isClientAuthWanted();
    if (clientAuthNeeded || clientAuthWanted) {
      String truststorePath = config.getTruststorePath();
      String trustStoreType;
      char[] truststorePassword;

      if (truststorePath != null) {
        String trustStorePasswordAlias = config.getTruststorePasswordAlias();
        trustStoreType = config.getTruststoreType();

        try {
          truststorePassword = aliasService.getPasswordFromAliasForGateway(trustStorePasswordAlias);
        } catch (AliasServiceException e) {
          log.failedToGetPasswordForGatewayTruststore(trustStorePasswordAlias, e);
          throw e;
        }
      }
      else {
        // when clientAuthIsNeeded but no truststore provided
        // default to the server's keystore and details
        truststorePath = identityKeystorePath;
        trustStoreType = identityKeystoreType;

        try {
          truststorePassword = aliasService.getGatewayIdentityKeystorePassword();
        } catch (AliasServiceException e) {
          log.failedToGetPasswordForGatewayTruststore(config.getIdentityKeystorePasswordAlias(), e);
          throw e;
        }
      }

      sslContextFactory.setTrustStorePath(truststorePath);
      if(truststorePassword != null) {
        sslContextFactory.setTrustStorePassword(new String(truststorePassword));
      }
      sslContextFactory.setTrustStoreType(trustStoreType);
    }
    if (clientAuthNeeded) {
      sslContextFactory.setNeedClientAuth( clientAuthNeeded );
    }
    else {
      sslContextFactory.setWantClientAuth( clientAuthWanted );
    }

    sslContextFactory.setTrustAll( config.getTrustAllCerts() );

    List<String> sslIncludeCiphers = config.getIncludedSSLCiphers();
    if (sslIncludeCiphers != null && !sslIncludeCiphers.isEmpty()) {
      sslContextFactory.setIncludeCipherSuites( sslIncludeCiphers.toArray(new String[0]) );
    }

    List<String> sslExcludeCiphers = config.getExcludedSSLCiphers();
    if (sslExcludeCiphers != null && !sslExcludeCiphers.isEmpty()) {
      sslContextFactory.setExcludeCipherSuites( sslExcludeCiphers.toArray(new String[0]) );
    }

    List<String> sslExcludeProtocols = config.getExcludedSSLProtocols();
    if (sslExcludeProtocols != null && !sslExcludeProtocols.isEmpty()) {
      sslContextFactory.setExcludeProtocols( sslExcludeProtocols.toArray(new String[0]) );
    }

    final Set<String> sslIncludeProtocols = config.getIncludedSSLProtocols();
    if (sslIncludeProtocols != null && !sslIncludeProtocols.isEmpty()) {
      sslContextFactory.setIncludeProtocols(sslIncludeProtocols.toArray(new String[0]));
    }

    sslContextFactory.setRenegotiationAllowed(config.isSSLRenegotiationAllowed());
    return sslContextFactory;
  }

  @Override
  public void excludeTopologyFromClientAuth(SslContextFactory sslContextFactory, GatewayConfig config, String topologyName) {
    if(config.isClientAuthNeeded() && config.isTopologyExcludedFromClientAuth(topologyName)) {
      SslContextFactory.Server sslContextFactoryServer = (SslContextFactory.Server) sslContextFactory;
      sslContextFactoryServer.setNeedClientAuth(false);
      sslContextFactoryServer.setWantClientAuth(true);
      log.topologyExcludedFromClientAuth(topologyName);
    }
  }

  @Override
  public void start() throws ServiceLifecycleException {
  }

  @Override
  public void stop() throws ServiceLifecycleException {
  }
}
