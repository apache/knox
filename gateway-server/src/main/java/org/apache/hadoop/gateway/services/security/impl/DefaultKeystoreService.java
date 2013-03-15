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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.hadoop.gateway.services.security.KeystoreService;
import org.apache.hadoop.gateway.services.security.KeystoreServiceException;
import org.apache.hadoop.gateway.services.security.MasterService;

import sun.security.x509.AlgorithmId;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.CertificateIssuerName;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateSubjectName;
import sun.security.x509.CertificateValidity;
import sun.security.x509.CertificateVersion;
import sun.security.x509.CertificateX509Key;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;

public class DefaultKeystoreService implements KeystoreService {

  private static final String TEST_CERT_DN = "CN=hadoop.gateway,OU=Test,O=Hadoop,L=Test,ST=Test,C=US";
  private static final String CREDENTIALS_SUFFIX = "-credentials.jceks";
  private static final String GATEWAY_KEYSTORE = "gateway.jks";
  
  private MasterService masterService;
  private String keyStoreDir;

  @Override
  public void init(GatewayConfig config, Map<String, String> options)
      throws ServiceLifecycleException {
    this.keyStoreDir = config.getGatewayHomeDir() + File.separator + "conf" + File.separator + "security" + File.separator + "keystores" + File.separator;
    File ksd = new File(this.keyStoreDir);
    if (!ksd.exists()) {
      ksd.mkdirs();
    }
  }

  @Override
  public void start() throws ServiceLifecycleException {
    // TODO Auto-generated method stub

  }

  @Override
  public void stop() throws ServiceLifecycleException {
    // TODO Auto-generated method stub

  }

  @Override
  public void createKeystoreForGateway() {
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
    KeyPairGenerator keyPairGenerator;
    try {
      keyPairGenerator = KeyPairGenerator.getInstance("RSA");
      keyPairGenerator.initialize(1024);  
      KeyPair KPair = keyPairGenerator.generateKeyPair();
      X509Certificate cert = generateCertificate(TEST_CERT_DN, KPair, 365, "SHA1withRSA");

      KeyStore privateKS = getKeystoreForGateway();
      privateKS.setKeyEntry(alias, KPair.getPrivate(),  
          passphrase,  
          new java.security.cert.Certificate[]{cert});  
      
      writeKeystoreToFile(privateKS, new File( keyStoreDir + GATEWAY_KEYSTORE  ));
    } catch (NoSuchAlgorithmException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (GeneralSecurityException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }  
  }
  
  /** 
   * Create a self-signed X.509 Certificate
   * @param dn the X.509 Distinguished Name, eg "CN=Test, L=London, C=GB"
   * @param pair the KeyPair
   * @param days how many days from now the Certificate is valid for
   * @param algorithm the signing algorithm, eg "SHA1withRSA"
   */ 
  private X509Certificate generateCertificate(String dn, KeyPair pair, int days, String algorithm)
    throws GeneralSecurityException, IOException
  {
    PrivateKey privkey = pair.getPrivate();
    X509CertInfo info = new X509CertInfo();
    Date from = new Date();
    Date to = new Date(from.getTime() + days * 86400000l);
    CertificateValidity interval = new CertificateValidity(from, to);
    BigInteger sn = new BigInteger(64, new SecureRandom());
    X500Name owner = new X500Name(dn);
   
    info.set(X509CertInfo.VALIDITY, interval);
    info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(sn));
    info.set(X509CertInfo.SUBJECT, new CertificateSubjectName(owner));
    info.set(X509CertInfo.ISSUER, new CertificateIssuerName(owner));
    info.set(X509CertInfo.KEY, new CertificateX509Key(pair.getPublic()));
    info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
    AlgorithmId algo = new AlgorithmId(AlgorithmId.md5WithRSAEncryption_oid);
    info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(algo));
   
    // Sign the cert to identify the algorithm that's used.
    X509CertImpl cert = new X509CertImpl(info);
    cert.sign(privkey, algorithm);
   
    // Update the algorith, and resign.
    algo = (AlgorithmId)cert.get(X509CertImpl.SIG_ALG);
    info.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, algo);
    cert = new X509CertImpl(info);
    cert.sign(privkey, algorithm);
    return cert;
  }   

  @Override
  public void createCredentialStoreForCluster(String clusterName) {
    String filename = keyStoreDir + clusterName + CREDENTIALS_SUFFIX;
    createKeystore(filename, "JCEKS");
  }

  private void createKeystore(String filename, String keystoreType) {
    try {
      FileOutputStream out = new FileOutputStream( filename );
      KeyStore ks = KeyStore.getInstance(keystoreType);  
      ks.load( null, null );  
      ks.store( out, masterService.getMasterSecret() );
    } catch (KeyStoreException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (NoSuchAlgorithmException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (CertificateException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  
  @Override
  public boolean isCredentialStoreForClusterAvailable(String clusterName) throws KeystoreServiceException {
    final File  keyStoreFile = new File( keyStoreDir + clusterName + CREDENTIALS_SUFFIX  );
    try {
      return isKeystoreAvailable(keyStoreFile, "JCEKS");
    } catch (KeyStoreException e) {
      e.printStackTrace();
      throw new KeystoreServiceException(e);
    } catch (IOException e) {
      // TODO Auto-generated catch block
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

  private boolean isKeystoreAvailable(final File keyStoreFile, String storeType) throws KeyStoreException, IOException {
    if ( keyStoreFile.exists() )
    {
      FileInputStream input = null;
      try {
        final KeyStore  keyStore = KeyStore.getInstance(storeType);
        input   = new FileInputStream( keyStoreFile );
        keyStore.load( input, masterService.getMasterSecret() );
        return true;
      } catch (NoSuchAlgorithmException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (CertificateException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
        throw e;
      } catch (KeyStoreException e) {
        e.printStackTrace();
        throw e;
      }
      finally {
          try {
            input.close();
          } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
      }
    }
    return false;
  }

  @Override
  public Key getKeyForGateway(String alias) throws KeystoreServiceException {
    Key key = null;
    KeyStore ks = getKeystoreForGateway();
    if (ks != null) {
      try {
        key = ks.getKey(alias, masterService.getMasterSecret());
      } catch (UnrecoverableKeyException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (KeyStoreException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (NoSuchAlgorithmException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return key;
  }  
  
  public KeyStore getCredentialStoreForCluster(String clusterName) {
    final File  keyStoreFile = new File( keyStoreDir + clusterName + CREDENTIALS_SUFFIX  );
    return getKeystore(keyStoreFile, "JCEKS");
  }

  private KeyStore getKeystore(final File keyStoreFile, String storeType) {
    KeyStore credStore = null;
    try {
      credStore = loadKeyStore( keyStoreFile, masterService.getMasterSecret(), storeType);
    } catch (CertificateException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (KeyStoreException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (NoSuchAlgorithmException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return credStore;
  }
  
  private static KeyStore loadKeyStore( final File keyStoreFile, final char[] masterPassword, String storeType )
       throws CertificateException, IOException,
       KeyStoreException, NoSuchAlgorithmException {     

   final KeyStore  keyStore = KeyStore.getInstance(storeType);
   if ( keyStoreFile.exists() )
   {
       final FileInputStream   input   = new FileInputStream( keyStoreFile );
       try {
           keyStore.load( input, masterPassword );
       }
       finally {
           input.close();
       }
   }
   else
   {
       keyStore.load( null, masterPassword );
   }

   return keyStore;       
  }

  public void writeKeystoreToFile(final KeyStore keyStore, final File file)
         throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
    // TODO: does this really need to be part of the interface?
    // TODO: backup the keystore on disk before attempting a write and restore on failure
      final FileOutputStream  out = new FileOutputStream(file);
      try
      {
          keyStore.store( out, masterService.getMasterSecret());
      }
      finally
      {
          out.close();
      }
  }

  public void setMasterService(MasterService ms) {
    this.masterService = ms;
  }
  
  public void addCredentialForCluster(String clusterName, String alias, String value) {
    KeyStore ks = getCredentialStoreForCluster(clusterName);
    if (ks != null) {
      try {
        final Key key = new SecretKeySpec(value.getBytes("UTF8"), "AES");
        ks.setKeyEntry( alias, key, masterService.getMasterSecret(), null);
        final File  keyStoreFile = new File( keyStoreDir + clusterName + CREDENTIALS_SUFFIX  );
        writeKeystoreToFile(ks, keyStoreFile);
      } catch (KeyStoreException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (NoSuchAlgorithmException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (CertificateException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  @Override
  public char[] getCredentialForCluster(String clusterName, String alias) {
    char[] credential = null;
    KeyStore ks = getCredentialStoreForCluster(clusterName);
    if (ks != null) {
      try {
        credential = new String(ks.getKey(alias, masterService.getMasterSecret()).getEncoded()).toCharArray();
      } catch (UnrecoverableKeyException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (KeyStoreException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (NoSuchAlgorithmException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return credential;
  }

  @Override
  public byte[] getKeyForCluster(String clusterName, String alias) {
    byte[] key = null;
    KeyStore ks = getCredentialStoreForCluster(clusterName);
    if (ks != null) {
      try {
        System.out.println("ALIAS: " + alias);
        System.out.println("MASTER SERVICE == NULL: " + (masterService == null));
        key = ks.getKey(alias, masterService.getMasterSecret()).getEncoded();
      } catch (UnrecoverableKeyException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (KeyStoreException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (NoSuchAlgorithmException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return key;
  }

  @Override
  public void addKeyForCluster(String clusterName, String alias, byte[] value) {
    KeyStore ks = getCredentialStoreForCluster(clusterName);
    if (ks != null) {
      final Key key = new SecretKeySpec(value, "AES");
      try {
        ks.setKeyEntry( alias, key, masterService.getMasterSecret(), null);
        final File  keyStoreFile = new File( keyStoreDir + clusterName + CREDENTIALS_SUFFIX  );
        writeKeystoreToFile(ks, keyStoreFile);
      } catch (KeyStoreException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (NoSuchAlgorithmException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (CertificateException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

}
