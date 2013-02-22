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
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.hadoop.gateway.services.security.KeystoreService;
import org.apache.hadoop.gateway.services.security.MasterService;

public class DefaultKeystoreService implements KeystoreService {

  private static final String CREDENTIALS_SUFFIX = "-credentials.jceks";
  
  private MasterService masterService;
  private String keyStoreDir;

  @Override
  public void init(GatewayConfig config, Map<String, String> options)
      throws ServiceLifecycleException {
    this.keyStoreDir = config.getGatewayHomeDir() + File.separator + "conf" + File.separator + "security" + File.separator + "keystores" + File.separator;
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
  }

  @Override
  public void addSelfSignedCertForGateway(String clusterName, String alias) {
  }

  @Override
  public void createCredentialStoreForCluster(String clusterName) {
    String filename = keyStoreDir + clusterName + CREDENTIALS_SUFFIX;
    try {
      FileOutputStream out = new FileOutputStream( filename );
      KeyStore ks = KeyStore.getInstance("JCEKS");  
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
  public boolean isCredentialStoreForClusterAvailable(String clusterName) {
    final File  keyStoreFile = new File( keyStoreDir + clusterName + CREDENTIALS_SUFFIX  );
    if ( keyStoreFile.exists() )
    {
      FileInputStream input = null;
      try {
        final KeyStore  keyStore = KeyStore.getInstance("JCEKS");
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
      } catch (KeyStoreException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
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

  public KeyStore getCredentialStoreForCluster(String clusterName) {
    final File  keyStoreFile = new File( keyStoreDir + clusterName + CREDENTIALS_SUFFIX  );
    KeyStore credStore = null;
    try {
      credStore = loadKeyStore( keyStoreFile, masterService.getMasterSecret());
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
  
  private static KeyStore loadKeyStore( final File keyStoreFile, final char[] masterPassword )
       throws CertificateException, IOException,
       KeyStoreException, NoSuchAlgorithmException {     

   final KeyStore  keyStore = KeyStore.getInstance("JCEKS");
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

  public void writeKeyStoreToFile(final KeyStore keyStore, final File file)
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
  
  public void addCredentialForCluster(String clusterName, String alias, String value) {
    KeyStore ks = getCredentialStoreForCluster(clusterName);
    if (ks != null) {
      try {
        final Key key = new SecretKeySpec(value.getBytes("UTF8"), "AES");
        ks.setKeyEntry( alias, key, masterService.getMasterSecret(), null);
        final File  keyStoreFile = new File( keyStoreDir + clusterName + CREDENTIALS_SUFFIX  );
        writeKeyStoreToFile(ks, keyStoreFile);
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
        writeKeyStoreToFile(ks, keyStoreFile);
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
