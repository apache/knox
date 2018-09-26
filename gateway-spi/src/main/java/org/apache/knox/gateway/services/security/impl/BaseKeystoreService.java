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

import org.apache.commons.codec.binary.Base64;
import org.apache.knox.gateway.i18n.GatewaySpiMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.security.MasterService;

import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;

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
           final FileInputStream   input = new FileInputStream( keyStoreFile );
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

  private static FileOutputStream createKeyStoreFile( String fileName ) throws IOException {
    File file = new File( fileName );
    if( file.exists() ) {
      if( file.isDirectory() ) {
        throw new IOException( file.getAbsolutePath() );
      } else if( !file.canWrite() ) {
        throw new IOException( file.getAbsolutePath() );
      }
    } else {
      File dir = file.getParentFile();
      if( !dir.exists() ) {
        if( !dir.mkdirs() ) {
          throw new IOException( file.getAbsolutePath() );
        }
      }
    }
    FileOutputStream stream = new FileOutputStream( file );
    return stream;
  }

  protected void createKeystore(String filename, String keystoreType) throws KeystoreServiceException {
    try {
      FileOutputStream out = createKeyStoreFile( filename );
      KeyStore ks = KeyStore.getInstance(keystoreType);  
      ks.load( null, null );  
      ks.store( out, masterService.getMasterSecret() );
      out.close();
    } catch (KeyStoreException e) {
      LOG.failedToCreateKeystore( filename, keystoreType, e );
      throw new KeystoreServiceException(e);
    } catch (NoSuchAlgorithmException e) {
      LOG.failedToCreateKeystore( filename, keystoreType, e );
      throw new KeystoreServiceException(e);
    } catch (CertificateException e) {
      LOG.failedToCreateKeystore( filename, keystoreType, e );
      throw new KeystoreServiceException(e);
    } catch (FileNotFoundException e) {
      LOG.failedToCreateKeystore( filename, keystoreType, e );
      throw new KeystoreServiceException(e);
    } catch (IOException e) {
      LOG.failedToCreateKeystore( filename, keystoreType, e );
      throw new KeystoreServiceException(e);
    }
  }

  protected boolean isKeystoreAvailable(final File keyStoreFile, String storeType) throws KeyStoreException, IOException {
    if ( keyStoreFile.exists() )
    {
      FileInputStream input = null;
      try {
        final KeyStore  keyStore = KeyStore.getInstance(storeType);
        input = new FileInputStream( keyStoreFile );
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
            if ( input != null ) {
              input.close();
            }
          } catch (IOException e) {
            LOG.failedToLoadKeystore( keyStoreFile.getName(), storeType, e );
          }
      }
    }
    return false;
  }

  protected KeyStore getKeystore(final File keyStoreFile, String storeType) throws KeystoreServiceException {
    KeyStore credStore = null;
    try {
      credStore = loadKeyStore( keyStoreFile, masterService.getMasterSecret(), storeType);
    } catch (CertificateException e) {
      LOG.failedToLoadKeystore( keyStoreFile.getName(), storeType, e );
      throw new KeystoreServiceException(e);
    } catch (KeyStoreException e) {
      LOG.failedToLoadKeystore( keyStoreFile.getName(), storeType, e );
      throw new KeystoreServiceException(e);
    } catch (NoSuchAlgorithmException e) {
      LOG.failedToLoadKeystore( keyStoreFile.getName(), storeType, e );
      throw new KeystoreServiceException(e);
    } catch (IOException e) {
      LOG.failedToLoadKeystore( keyStoreFile.getName(), storeType, e );
      throw new KeystoreServiceException(e);
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

  public void removeCredential(String alias, KeyStore ks) {
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

  protected char[] getCredential(String alias, char[] credential, KeyStore ks) {
    if (ks != null) {
      try {
        credential = new String(ks.getKey(alias, masterService.getMasterSecret()).getEncoded(), StandardCharsets.UTF_8).toCharArray();
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

  protected void writeCertificateToFile( Certificate cert, final File file ) throws CertificateEncodingException, IOException {
    byte[] bytes = cert.getEncoded();
    Base64 encoder = new Base64( 76, "\n".getBytes( "ASCII" ) );
    try( final FileOutputStream out = new FileOutputStream( file ) ) {
      out.write( "-----BEGIN CERTIFICATE-----\n".getBytes( "ASCII" ) );
      out.write( encoder.encodeToString( bytes ).getBytes( "ASCII" ) );
      out.write( "-----END CERTIFICATE-----\n".getBytes( "ASCII" ) );
    }
  }

  protected void writeKeystoreToFile(final KeyStore keyStore, final File file)
      throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
     // TODO: backup the keystore on disk before attempting a write and restore on failure
     try( final FileOutputStream out = new FileOutputStream(file) ) {
         keyStore.store( out, masterService.getMasterSecret() );
     }
  }

  public void setMasterService(MasterService ms) {
    this.masterService = ms;
  }
}
