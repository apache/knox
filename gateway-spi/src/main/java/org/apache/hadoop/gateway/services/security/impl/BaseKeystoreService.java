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
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.crypto.spec.SecretKeySpec;

import org.apache.hadoop.gateway.i18n.GatewaySpiMessages;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
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

public class BaseKeystoreService {
  private static GatewaySpiMessages LOG = MessagesFactory.get( GatewaySpiMessages.class );

  protected MasterService masterService;
  protected String keyStoreDir;

  private static KeyStore loadKeyStore(final File keyStoreFile, final char[] masterPassword, String storeType)
      throws CertificateException, IOException, KeyStoreException,
      NoSuchAlgorithmException {     
      
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

  /** 
   * Create a self-signed X.509 Certificate
   * @param dn the X.509 Distinguished Name, eg "CN=Test, L=London, C=GB"
   * @param pair the KeyPair
   * @param days how many days from now the Certificate is valid for
   * @param algorithm the signing algorithm, eg "SHA1withRSA"
   */
  protected X509Certificate generateCertificate(String dn, KeyPair pair, int days,
      String algorithm) throws GeneralSecurityException, IOException {
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

  protected void createKeystore(String filename, String keystoreType) {
    try {
      FileOutputStream out = new FileOutputStream( filename );
      KeyStore ks = KeyStore.getInstance(keystoreType);  
      ks.load( null, null );  
      ks.store( out, masterService.getMasterSecret() );
    } catch (KeyStoreException e) {
      LOG.failedToCreateKeystore( filename, keystoreType, e );
    } catch (NoSuchAlgorithmException e) {
      LOG.failedToCreateKeystore( filename, keystoreType, e );
    } catch (CertificateException e) {
      LOG.failedToCreateKeystore( filename, keystoreType, e );
    } catch (FileNotFoundException e) {
      LOG.failedToCreateKeystore( filename, keystoreType, e );
    } catch (IOException e) {
      LOG.failedToCreateKeystore( filename, keystoreType, e );
    }
  }

  protected boolean isKeystoreAvailable(final File keyStoreFile, String storeType) throws KeyStoreException, IOException {
    if ( keyStoreFile.exists() )
    {
      FileInputStream input = null;
      try {
        final KeyStore  keyStore = KeyStore.getInstance(storeType);
        input   = new FileInputStream( keyStoreFile );
        keyStore.load( input, masterService.getMasterSecret() );
        return true;
      } catch (NoSuchAlgorithmException e) {
        LOG.failedToLoadKeystore( keyStoreFile.getName(), storeType, e );
      } catch (CertificateException e) {
        LOG.failedToLoadKeystore( keyStoreFile.getName(), storeType, e );
      } catch (IOException e) {
        LOG.failedToLoadKeystore( keyStoreFile.getName(), storeType, e );
        throw e;
      } catch (KeyStoreException e) {
        LOG.failedToLoadKeystore( keyStoreFile.getName(), storeType, e );
        throw e;
      }
      finally {
          try {
            input.close();
          } catch (IOException e) {
            LOG.failedToLoadKeystore( keyStoreFile.getName(), storeType, e );
          }
      }
    }
    return false;
  }

  protected KeyStore getKeystore(final File keyStoreFile, String storeType) {
    KeyStore credStore = null;
    try {
      credStore = loadKeyStore( keyStoreFile, masterService.getMasterSecret(), storeType);
    } catch (CertificateException e) {
      LOG.failedToLoadKeystore( keyStoreFile.getName(), storeType, e );
    } catch (KeyStoreException e) {
      LOG.failedToLoadKeystore( keyStoreFile.getName(), storeType, e );
    } catch (NoSuchAlgorithmException e) {
      LOG.failedToLoadKeystore( keyStoreFile.getName(), storeType, e );
    } catch (IOException e) {
      LOG.failedToLoadKeystore( keyStoreFile.getName(), storeType, e );
    }
    return credStore;
  }

  public BaseKeystoreService() {
    super();
  }

  protected void addCredential(String alias, String value, KeyStore ks) {
    if (ks != null) {
      try {
        final Key key = new SecretKeySpec(value.getBytes("UTF8"), "AES");
        ks.setKeyEntry( alias, key, masterService.getMasterSecret(), null);
      } catch (KeyStoreException e) {
        LOG.failedToAddCredential(e);
      } catch (IOException e) {
        LOG.failedToAddCredential(e);
      }
    }
  }

  protected char[] getCredential(String alias, char[] credential, KeyStore ks) {
    if (ks != null) {
      try {
        credential = new String(ks.getKey(alias, masterService.getMasterSecret()).getEncoded()).toCharArray();
      } catch (UnrecoverableKeyException e) {
        LOG.failedToGetCredential(e);
      } catch (KeyStoreException e) {
        LOG.failedToGetCredential(e);
      } catch (NoSuchAlgorithmException e) {
        LOG.failedToGetCredential(e);
      }
    }
    return credential;
  }

  protected void writeKeystoreToFile(final KeyStore keyStore, final File file)
      throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
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
}