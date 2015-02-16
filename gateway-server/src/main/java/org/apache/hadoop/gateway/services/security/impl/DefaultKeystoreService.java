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

import org.apache.hadoop.gateway.GatewayMessages;
import org.apache.hadoop.gateway.GatewayResources;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.i18n.resources.ResourcesFactory;
import org.apache.hadoop.gateway.services.Service;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.hadoop.gateway.services.security.KeystoreService;
import org.apache.hadoop.gateway.services.security.KeystoreServiceException;
import org.apache.hadoop.gateway.services.security.impl.X509CertificateUtil;

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
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.Map;

public class DefaultKeystoreService extends BaseKeystoreService implements KeystoreService, Service {

  private static final String dnTemplate = "CN={0},OU=Test,O=Hadoop,L=Test,ST=Test,C=US";
  private static final String CREDENTIALS_SUFFIX = "-credentials.jceks";
  private static final String GATEWAY_KEYSTORE = "gateway.jks";
  private static final String CERT_GEN_MODE = "hadoop.gateway.cert.gen.mode";
  private static final String CERT_GEN_MODE_LOCALHOST = "localhost";
  private static final String CERT_GEN_MODE_HOSTNAME = "hostname";
  private static GatewayMessages LOG = MessagesFactory.get( GatewayMessages.class );
  private static GatewayResources RES = ResourcesFactory.get( GatewayResources.class );

  @Override
  public void init(GatewayConfig config, Map<String, String> options)
      throws ServiceLifecycleException {
    this.keyStoreDir = config.getGatewaySecurityDir() + File.separator + "keystores" + File.separator;
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
    String filename = keyStoreDir + GATEWAY_KEYSTORE;
    createKeystore(filename, "JKS");
  }

  @Override
  public KeyStore getKeystoreForGateway() {
    final File  keyStoreFile = new File( keyStoreDir + GATEWAY_KEYSTORE  );
    return getKeystore(keyStoreFile, "JKS");
  }
  
  @Override
  public void addSelfSignedCertForGateway(String alias, char[] passphrase) {
    addSelfSignedCertForGateway(alias, passphrase, null);
  }

  @Override
  public void addSelfSignedCertForGateway(String alias, char[] passphrase, String hostname) {

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
    } catch (GeneralSecurityException e) {
      LOG.failedToAddSeflSignedCertForGateway( alias, e );
    } catch (IOException e) {
      LOG.failedToAddSeflSignedCertForGateway( alias, e );
    }  
  }

  private String buildDistinguishedName(String hostname) {
    MessageFormat headerFormatter = new MessageFormat(dnTemplate);
    String[] paramArray = new String[1];
    paramArray[0] = hostname;
    String dn = headerFormatter.format(paramArray);
    return dn;
  }
  
  @Override
  public void createCredentialStoreForCluster(String clusterName) throws KeystoreServiceException {
    String filename = keyStoreDir + clusterName + CREDENTIALS_SUFFIX;
    createKeystore(filename, "JCEKS");
  }

  @Override
  public boolean isCredentialStoreForClusterAvailable(String clusterName) throws KeystoreServiceException {
    final File  keyStoreFile = new File( keyStoreDir + clusterName + CREDENTIALS_SUFFIX  );
    try {
      return isKeystoreAvailable(keyStoreFile, "JCEKS");
    } catch (KeyStoreException e) {
      throw new KeystoreServiceException(e);
    } catch (IOException e) {
      throw new KeystoreServiceException(e);
    }
  }

  @Override
  public boolean isKeystoreForGatewayAvailable() throws KeystoreServiceException {
    final File  keyStoreFile = new File( keyStoreDir + GATEWAY_KEYSTORE  );
    try {
      return isKeystoreAvailable(keyStoreFile, "JKS");
    } catch (KeyStoreException e) {
      throw new KeystoreServiceException(e);
    } catch (IOException e) {
      throw new KeystoreServiceException(e);
    }
  }

  @Override
  public Key getKeyForGateway(String alias, char[] passphrase) throws KeystoreServiceException {
    Key key = null;
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
  
  public KeyStore getCredentialStoreForCluster(String clusterName) {
    final File  keyStoreFile = new File( keyStoreDir + clusterName + CREDENTIALS_SUFFIX  );
    return getKeystore(keyStoreFile, "JCEKS");
  }

  public void addCredentialForCluster(String clusterName, String alias, String value) {
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
  }
  
  @Override
  public char[] getCredentialForCluster(String clusterName, String alias) {
    char[] credential = null;
    KeyStore ks = getCredentialStoreForCluster(clusterName);
    if (ks != null) {
      try {
        char[] masterSecret = masterService.getMasterSecret();
        Key credentialKey = ks.getKey( alias, masterSecret );
        if (credentialKey != null) {
          byte[] credentialBytes = credentialKey.getEncoded();
          String credentialString = new String( credentialBytes );
          credential = credentialString.toCharArray();
        }
      } catch (UnrecoverableKeyException e) {
        LOG.failedToGetCredentialForCluster( clusterName, e );
      } catch (KeyStoreException e) {
        LOG.failedToGetCredentialForCluster( clusterName, e );
      } catch (NoSuchAlgorithmException e) {
        LOG.failedToGetCredentialForCluster( clusterName, e );
      }
    }
    return credential;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.services.security.KeystoreService#removeCredentialForCluster(java.lang.String, java.lang.String, java.security.KeyStore)
   */
  @Override
  public void removeCredentialForCluster(String clusterName, String alias) {
    KeyStore ks = getCredentialStoreForCluster(clusterName);
    removeCredential(alias, ks);
    final File  keyStoreFile = new File( keyStoreDir + clusterName + CREDENTIALS_SUFFIX  );
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
}
