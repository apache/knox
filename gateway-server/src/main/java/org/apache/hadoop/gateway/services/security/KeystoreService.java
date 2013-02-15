package org.apache.hadoop.gateway.services.security;

import org.apache.hadoop.gateway.services.Service;

public interface KeystoreService extends Service {

  public void setMasterService(MasterService ms);
  
  public void createKeystoreForGateway();

  public void addSelfSignedCertForGateway(String clusterName, String alias);
  
  public void createCredentialStoreForCluster(String clusterName);

  public void generateAliasForCluster(String clusterName, String alias);

  public void addAliasForCluster(String clusterName, String alias, String key);

  public char[] getAliasForCluster(String clusterName, String alias);

  public char[] getAliasForCluster(String clusterName, String alias, boolean create);
}
