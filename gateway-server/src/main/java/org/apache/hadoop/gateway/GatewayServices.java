package org.apache.hadoop.gateway;

import java.util.HashMap;
import java.util.Map;

import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.services.Service;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.hadoop.gateway.services.security.impl.DefaultKeystoreService;
import org.apache.hadoop.gateway.services.security.impl.DefaultMasterService;

public class GatewayServices {
  private static String MASTER_SERVICE = "org.apache.hadoop.gateway.services.security.MasterService";
  public static String CRYPTO_SERVICE = "org.apache.hadoop.gateway.services.security.CryptoService";
  public static String KEYSTORE_SERVICE = "org.apache.hadoop.gateway.services.security.KeystoreService";
  public static String ALIAS_SERVICE = "org.apache.hadoop.gateway.services.security.AliasService";

  private Map<String,Service> services = new HashMap<String, Service>();

  public GatewayServices() {
    super();
  }

  public void init(GatewayConfig config, Map<String,String> options) throws ServiceLifecycleException {
    DefaultMasterService ms = new DefaultMasterService();
    ms.init(config, options);
    services.put(MASTER_SERVICE, ms);

    DefaultKeystoreService ks = new DefaultKeystoreService();
    ks.setMasterService(ms);
    ks.init(config, options);
    services.put(KEYSTORE_SERVICE, ks);
    
//    ks.createCredentialStoreForCluster("test");
//    ks.addAliasForCluster("test", "url_encrypt", "encryptwiththis");

  }
  
  public void start() throws ServiceLifecycleException {
    DefaultMasterService ms = (DefaultMasterService) services.get(MASTER_SERVICE);
    ms.start();

    DefaultKeystoreService ks = (DefaultKeystoreService) services.get(KEYSTORE_SERVICE);
    ks.start();
  }

  public void stop() throws ServiceLifecycleException {
    DefaultMasterService ms = (DefaultMasterService) services.get(MASTER_SERVICE);
    ms.stop();

    DefaultKeystoreService ks = (DefaultKeystoreService) services.get(KEYSTORE_SERVICE);
    ks.stop();
  }

}
