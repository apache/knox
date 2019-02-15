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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;

abstract class BaseKeystoreService {
  private static GatewaySpiMessages LOG = MessagesFactory.get( GatewaySpiMessages.class );

  private MasterService masterService;

  private static KeyStore loadKeyStore(final File keyStoreFile, final char[] storePassword, String storeType)
      throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException {
       final KeyStore  keyStore = KeyStore.getInstance(storeType);
       if ( keyStoreFile.exists() ) {
           try (InputStream input = Files.newInputStream(keyStoreFile.toPath())) {
               keyStore.load( input, storePassword );
           }
       } else {
           keyStore.load( null, storePassword );
       }

       return keyStore;
      }

  private static OutputStream createKeyStoreFile(File file) throws IOException {
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
    return Files.newOutputStream( file.toPath() );
  }

  protected void createKeystore(File file, String keystoreType, char[] storePassword) throws KeystoreServiceException {
    try (OutputStream out = createKeyStoreFile( file )) {
      KeyStore ks = KeyStore.getInstance(keystoreType);
      ks.load( null, null );
      ks.store( out, storePassword );
    } catch (NoSuchAlgorithmException | CertificateException | KeyStoreException | IOException e) {
      LOG.failedToCreateKeystore( file.getAbsolutePath(), keystoreType, e );
      throw new KeystoreServiceException(e);
    }
  }

  protected boolean isKeystoreAvailable(final File keyStoreFile, String storeType, char[] storePassword) throws KeyStoreException, IOException {
    if ( keyStoreFile.exists() ) {
      try (InputStream input = Files.newInputStream(keyStoreFile.toPath())){
        final KeyStore keyStore = KeyStore.getInstance(storeType);
        keyStore.load( input, storePassword );
        return true;
      } catch (NoSuchAlgorithmException | CertificateException e) {
        LOG.failedToLoadKeystore( keyStoreFile.getName(), storeType, e );
      } catch (IOException | KeyStoreException e) {
        LOG.failedToLoadKeystore( keyStoreFile.getName(), storeType, e );
        throw e;
      }
    }
    return false;
  }

  protected KeyStore getKeystore(final File keyStoreFile, String storeType, char[] password) throws KeystoreServiceException {
    KeyStore credStore;
    try {
      credStore = loadKeyStore( keyStoreFile, password, storeType);
    } catch (CertificateException | IOException | NoSuchAlgorithmException | KeyStoreException e) {
      LOG.failedToLoadKeystore( keyStoreFile.getName(), storeType, e );
      throw new KeystoreServiceException(e);
    }
    return credStore;
  }

  protected BaseKeystoreService() {
    super();
  }

  protected void addCredential(String alias, String value, KeyStore ks, char[] keyPassword) {
    if (ks != null) {
      try {
        final Key key = new SecretKeySpec(value.getBytes(StandardCharsets.UTF_8), "AES");
        ks.setKeyEntry( alias, key, keyPassword, null);
      } catch (KeyStoreException e) {
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

  /**
   * Given a keystore and an alias name, attempts to retrieve the requested credential;
   *
   * @param alias      an alias name
   * @param keystore   a {@link KeyStore}
   * @param passphrase that passphrase to use for decryption
   * @return an array of <code>char</code>s; or <code>null</code> if not found
   */
  protected char[] getCredential(String alias, KeyStore keystore, char[] passphrase)
      throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
    Key key = getKey(alias, keystore, passphrase);
    if (key != null) {
      return new String(key.getEncoded(), StandardCharsets.UTF_8).toCharArray();
    }

    return null;
  }

  /**
   * Given a keystore and an alias name, attempts to retrieve the requested {@link Certificate}
   *
   * @param alias    an alias name
   * @param keystore a {@link KeyStore}
   * @return a {@link Certificate}; or <code>null</code> if not found
   * @throws KeyStoreException if an error occurs while trying to retrieve the requested certificate
   */
  protected Certificate getCertificate(String alias, KeyStore keystore) throws KeyStoreException {
    if ((keystore != null) && (alias != null)) {
      return keystore.getCertificate(alias);
    }

    return null;
  }

  /**
   * Given a keystore and an alias name, attempts to retrieve the requested {@link Key}
   *
   * @param alias    an alias name
   * @param keystore a {@link KeyStore}
   * @param passphrase that passphrase to use for decryption
   * @return a {@link Key}; or <code>null</code> if not found
   */
  protected Key getKey(String alias, KeyStore keystore, char[] passphrase)
      throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
    if ((keystore != null) && (alias != null)) {
      return keystore.getKey(alias, passphrase);
    }

    return null;
  }

  protected void writeCertificateToFile( Certificate cert, final File file ) throws CertificateEncodingException, IOException {
    byte[] bytes = cert.getEncoded();
    Base64 encoder = new Base64( 76, "\n".getBytes( StandardCharsets.US_ASCII ) );
    try( OutputStream out = Files.newOutputStream( file.toPath() ) ) {
      out.write( "-----BEGIN CERTIFICATE-----\n".getBytes( StandardCharsets.US_ASCII ) );
      out.write( encoder.encodeToString( bytes ).getBytes( StandardCharsets.US_ASCII ) );
      out.write( "-----END CERTIFICATE-----\n".getBytes( StandardCharsets.US_ASCII ) );
    }
  }

  protected void writeKeystoreToFile(final KeyStore keyStore, char[] storePassword, final File file)
      throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
    // Ensure container directory exists... if not create it....
    if(!file.getParentFile().exists()) {
      file.getParentFile().mkdirs();
    }
    else {
      // TODO: backup the keystore on disk before attempting a write and restore on failure
    }
    try (OutputStream out = Files.newOutputStream(file.toPath())) {
      keyStore.store(out, storePassword);
    }
  }

  public void setMasterService(MasterService ms) {
    this.masterService = ms;
  }

  protected char[] getMasterSecret() {
    return masterService.getMasterSecret();
  }
}
