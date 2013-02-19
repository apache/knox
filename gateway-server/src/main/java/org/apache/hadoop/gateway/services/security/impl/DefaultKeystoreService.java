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
  
  private KeyStore getCredentialStoreForCluster(String clusterName) {
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
  
  @Override
  public void generateAliasForCluster(String clusterName, String alias) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void addAliasForCluster(String clusterName, String alias, String value) {
    KeyStore ks = getCredentialStoreForCluster(clusterName);
    if (ks != null) {
      final Key key = new SecretKeySpec(value.getBytes(), "AES");
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

  private void writeKeyStoreToFile(
      final KeyStore  keyStore,
      final File      file)
         throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException
  {
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
  
  @Override
  public char[] getAliasForCluster(String clusterName, String alias) {
    return getAliasForCluster(clusterName, alias, false);
  }

  @Override
  public char[] getAliasForCluster(String clusterName, String alias, boolean generate) {
    String passwordString = null;
    KeyStore ks = getCredentialStoreForCluster(clusterName);
    if (ks != null) {
      Key key;
      try {
        key = ks.getKey(alias, masterService.getMasterSecret());
        if (key != null) {
          passwordString  = new String(key.getEncoded());
        }
        else {
          if (generate) {
            passwordString = generatePasscode();
          }
          addAliasForCluster(clusterName, alias, passwordString);
        }
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
    return passwordString.toCharArray();
  }

  private String generatePasscode() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void setMasterService(MasterService ms) {
    // TODO Auto-generated method stub
    
  }

}
