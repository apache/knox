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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.security.auth.x500.X500Principal;

import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.services.security.SSLService;
import org.apache.knox.gateway.util.X500PrincipalParser;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class JettySSLService implements SSLService {
  private static final String EPHEMERAL_DH_KEY_SIZE_PROPERTY = "jdk.tls.ephemeralDHKeySize";
  private static final String GATEWAY_TRUSTSTORE_PASSWORD = "gateway-truststore-password";
  private static final String GATEWAY_CREDENTIAL_STORE_NAME = "__gateway";
  private static GatewayMessages log = MessagesFactory.get( GatewayMessages.class );

  private MasterService ms;
  private KeystoreService ks;
  private AliasService as;
  private List<String> sslIncludeCiphers = null;
  private List<String> sslExcludeCiphers = null;
  private List<String> sslExcludeProtocols = null;
  private boolean clientAuthNeeded;
  private boolean trustAllCerts;
  private String truststorePath;
  private String keystoreType;
  private String trustStoreType;
  private boolean clientAuthWanted;

  public void setMasterService(MasterService ms) {
    this.ms = ms;
  }

  public void setAliasService(AliasService as) {
    this.as = as;
  }

  public void setKeystoreService(KeystoreService ks) {
    this.ks = ks;
  }

  
  @Override
  public void init(GatewayConfig config, Map<String, String> options)
      throws ServiceLifecycleException {
    // set any JSSE or security related system properties
    System.setProperty(EPHEMERAL_DH_KEY_SIZE_PROPERTY, config.getEphemeralDHKeySize());
    try {
      if (!ks.isCredentialStoreForClusterAvailable(GATEWAY_CREDENTIAL_STORE_NAME)) {
        log.creatingCredentialStoreForGateway();
        ks.createCredentialStoreForCluster(GATEWAY_CREDENTIAL_STORE_NAME);
        // LET'S NOT GENERATE A DIFFERENT KEY PASSPHRASE BY DEFAULT ANYMORE
        // IF A DEPLOYMENT WANTS TO CHANGE THE KEY PASSPHRASE TO MAKE IT MORE SECURE THEN
        // THEY CAN ADD THE ALIAS EXPLICITLY WITH THE CLI
        // as.generateAliasForCluster(GATEWAY_CREDENTIAL_STORE_NAME, GATEWAY_IDENTITY_PASSPHRASE);
      }
      else {
        log.credentialStoreForGatewayFoundNotCreating();
      }
    } catch (KeystoreServiceException e) {
      throw new ServiceLifecycleException("Keystore was not loaded properly - the provided (or persisted) master secret may not match the password for the keystore.", e);
    }

    try {
      if (!ks.isKeystoreForGatewayAvailable()) {
        log.creatingKeyStoreForGateway();
        ks.createKeystoreForGateway();
        char[] passphrase = null;
        try {
          passphrase = as.getGatewayIdentityPassphrase();
        } catch (AliasServiceException e) {
          throw new ServiceLifecycleException("Error accessing credential store for the gateway.", e);
        }
        if (passphrase == null) {
          passphrase = ms.getMasterSecret();
        }
        ks.addSelfSignedCertForGateway("gateway-identity", passphrase);
      }
      else {
        log.keyStoreForGatewayFoundNotCreating();
      }
      logAndValidateCertificate();
    } catch (KeystoreServiceException e) {
      throw new ServiceLifecycleException("Keystore was not loaded properly - the provided (or persisted) master secret may not match the password for the keystore.", e);
    }

    keystoreType = config.getKeystoreType();
    sslIncludeCiphers = config.getIncludedSSLCiphers();
    sslExcludeCiphers = config.getExcludedSSLCiphers();
    sslExcludeProtocols = config.getExcludedSSLProtocols();
    clientAuthNeeded = config.isClientAuthNeeded();
    clientAuthWanted = config.isClientAuthWanted();
    truststorePath = config.getTruststorePath();
    trustAllCerts = config.getTrustAllCerts();
    trustStoreType = config.getTruststoreType();
  }

  private void logAndValidateCertificate() throws ServiceLifecycleException {
    // let's log the hostname (CN) and cert expiry from the gateway's public cert to aid in SSL debugging
    Certificate cert;
    try {
      cert = as.getCertificateForGateway("gateway-identity");
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
      }
      else {
        throw new ServiceLifecycleException("Public certificate for the gateway cannot be found with the alias gateway-identity. Plase check the identity certificate alias.");
      }
    }
    else {
      throw new ServiceLifecycleException("Public certificate for the gateway is not of the expected type of X509Certificate. Something is wrong with the gateway keystore.");
    }
  }

  public Object buildSslContextFactory( String keystoreFileName ) throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
    SslContextFactory sslContextFactory = new SslContextFactory( true );
    sslContextFactory.setCertAlias( "gateway-identity" );
    sslContextFactory.setKeyStoreType(keystoreType);
    sslContextFactory.setKeyStorePath(keystoreFileName);
    char[] master = ms.getMasterSecret();
    sslContextFactory.setKeyStorePassword(new String(master));
    char[] keypass = null;
    try {
      keypass = as.getGatewayIdentityPassphrase();
    } catch (AliasServiceException e) {
      // nop - default passphrase will be used
    }
    if (keypass == null) {
      // there has been no alias created for the key - let's assume it is the same as the keystore password
      keypass = master;
    }
    sslContextFactory.setKeyManagerPassword(new String(keypass));

    String truststorePassword = null;
    if (clientAuthNeeded || clientAuthWanted) {
      if (truststorePath != null) {
        char[] truststorePwd = null;
        try {
          truststorePwd = as.getPasswordFromAliasForGateway(GATEWAY_TRUSTSTORE_PASSWORD);
        } catch (AliasServiceException e) {
          // nop - master secret will be used
        }
        if (truststorePwd != null) {
          truststorePassword = new String(truststorePwd);
        }
        else {
          truststorePassword = new String(master);
        }
        sslContextFactory.setTrustStore(loadKeyStore(truststorePath, trustStoreType, truststorePassword.toCharArray()));
        sslContextFactory.setTrustStorePassword(truststorePassword);
        sslContextFactory.setTrustStoreType(trustStoreType);
      }
      else {
        // when clientAuthIsNeeded but no truststore provided
        // default to the server's keystore and details
        sslContextFactory.setTrustStore(loadKeyStore(keystoreFileName, keystoreType, master));
        sslContextFactory.setTrustStorePassword(new String(master));
        sslContextFactory.setTrustStoreType(keystoreType);
      }
    }
    if (clientAuthNeeded) {
      sslContextFactory.setNeedClientAuth( clientAuthNeeded );
    }
    else {
      sslContextFactory.setWantClientAuth( clientAuthWanted );
    }
    sslContextFactory.setTrustAll( trustAllCerts );
    if (sslIncludeCiphers != null && !sslIncludeCiphers.isEmpty()) {
      sslContextFactory.setIncludeCipherSuites( sslIncludeCiphers.toArray(new String[sslIncludeCiphers.size()]) );
    }
    if (sslExcludeCiphers != null && !sslExcludeCiphers.isEmpty()) {
      sslContextFactory.setExcludeCipherSuites( sslExcludeCiphers.toArray(new String[sslExcludeCiphers.size()]) );
    }
    if (sslExcludeProtocols != null && !sslExcludeProtocols.isEmpty()) {
      sslContextFactory.setExcludeProtocols( sslExcludeProtocols.toArray(new String[sslExcludeProtocols.size()]) );
    }
    return sslContextFactory;
  }
  
  @Override
  public void start() throws ServiceLifecycleException {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void stop() throws ServiceLifecycleException {
    // TODO Auto-generated method stub
    
  }

  private static KeyStore loadKeyStore( String fileName, String storeType, char[] storePass ) throws CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException {
    KeyStore keystore = KeyStore.getInstance(storeType);
    //Coverity CID 1352655
    InputStream is = new FileInputStream(fileName);
    try {
      keystore.load( is, storePass );
    } finally {
      if( is != null ) {
        is.close();
      }
    }
    return keystore;
  }

}
