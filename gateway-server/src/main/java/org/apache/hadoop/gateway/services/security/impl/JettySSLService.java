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

import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.security.auth.x500.X500Principal;

import org.apache.hadoop.gateway.GatewayMessages;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.hadoop.gateway.services.security.AliasService;
import org.apache.hadoop.gateway.services.security.KeystoreService;
import org.apache.hadoop.gateway.services.security.KeystoreServiceException;
import org.apache.hadoop.gateway.services.security.MasterService;
import org.apache.hadoop.gateway.services.security.SSLService;
import org.apache.hadoop.gateway.util.X500PrincipalParser;
import org.eclipse.jetty.server.ssl.SslConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class JettySSLService implements SSLService {
  private static final String GATEWAY_IDENTITY_PASSPHRASE = "gateway-identity-passphrase";
  private static final String GATEWAY_CREDENTIAL_STORE_NAME = "__gateway";
  private static GatewayMessages log = MessagesFactory.get( GatewayMessages.class );
  
  private MasterService ms;
  private KeystoreService ks;
  private AliasService as;
  private List<String> sslExcludeProtocols = null;

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
    try {
      if (!ks.isCredentialStoreForClusterAvailable(GATEWAY_CREDENTIAL_STORE_NAME)) {
        log.creatingCredentialStoreForGateway();
        ks.createCredentialStoreForCluster(GATEWAY_CREDENTIAL_STORE_NAME);
        as.generateAliasForCluster(GATEWAY_CREDENTIAL_STORE_NAME, GATEWAY_IDENTITY_PASSPHRASE);
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
        char[] passphrase = as.getPasswordFromAliasForCluster(GATEWAY_CREDENTIAL_STORE_NAME, GATEWAY_IDENTITY_PASSPHRASE);
        ks.addSelfSignedCertForGateway("gateway-identity", passphrase);
      }
      else {
        log.keyStoreForGatewayFoundNotCreating();
      }
      logAndValidateCertificate();
    } catch (KeystoreServiceException e) {
      throw new ServiceLifecycleException("Keystore was not loaded properly - the provided (or persisted) master secret may not match the password for the keystore.", e);
    }

    sslExcludeProtocols = config.getExcludedSSLProtocols();
  }

  private void logAndValidateCertificate() throws ServiceLifecycleException {
    // let's log the hostname (CN) and cert expiry from the gateway's public cert to aid in SSL debugging
    Certificate cert = as.getCertificateForGateway("gateway-identity");
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
  
  public Object buildSSlConnector( String keystoreFileName ) {
    SslContextFactory sslContextFactory = new SslContextFactory( true );
    sslContextFactory.setCertAlias( "gateway-identity" );
//    String keystorePath = gatewayHomeDir + File.separatorChar +  "conf" + File.separatorChar +  "security" + File.separatorChar + "keystores" + File.separatorChar + "gateway.jks";
    sslContextFactory.setKeyStoreType("JKS");
    sslContextFactory.setKeyStorePath(keystoreFileName);
    char[] master = ms.getMasterSecret();
    sslContextFactory.setKeyStorePassword(new String(master));
    char[] keypass = as.getPasswordFromAliasForCluster(GATEWAY_CREDENTIAL_STORE_NAME, GATEWAY_IDENTITY_PASSPHRASE);
    if (keypass == null) {
      // there has been no alias created for the key - let's assume it is the same as the keystore password
      keypass = master;
    }
    sslContextFactory.setKeyManagerPassword(new String(keypass));

    // TODO: make specific truststore too?
//    sslContextFactory.setTrustStore(keystorePath);
//    sslContextFactory.setTrustStorePassword(new String(keypass));
    sslContextFactory.setNeedClientAuth( false );
    sslContextFactory.setTrustAll( true );
    if (sslExcludeProtocols != null) {
      sslContextFactory.setExcludeProtocols((String[]) sslExcludeProtocols.toArray());
    }
    SslConnector sslConnector = new SslSelectChannelConnector( sslContextFactory );

    return sslConnector;
  }  
  
  @Override
  public void start() throws ServiceLifecycleException {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void stop() throws ServiceLifecycleException {
    // TODO Auto-generated method stub
    
  }
}
