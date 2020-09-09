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
package org.apache.knox.gateway.backend.hashicorp.vault;

import org.apache.knox.gateway.backend.hashicorp.vault.authentication.HashicorpVaultClientAuthenticationProvider;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AbstractAliasService;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.util.PasswordUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.support.Versioned;

import java.net.URI;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

public class HashicorpVaultAliasService extends AbstractAliasService {
  public static final String TYPE = "hashicorp.vault";
  public static final String VAULT_CONFIG_PREFIX = "hashicorp.vault.";
  public static final String VAULT_ADDRESS_KEY = VAULT_CONFIG_PREFIX + "address";

  private static final String KEY = "data";

  static final String VAULT_SEPARATOR = "/";
  static final String VAULT_SECRETS_ENGINE_KEY = VAULT_CONFIG_PREFIX + "secrets.engine";
  static final String VAULT_PATH_PREFIX_KEY = VAULT_CONFIG_PREFIX + "path.prefix";

  private final AliasService localAliasService;

  private VaultVersionedKeyValueOperations vault;
  private String vaultPathPrefix;
  private GatewayConfig config;

  public HashicorpVaultAliasService(AliasService localAliasService) {
    this.localAliasService = localAliasService;
  }

  private String getPath(String clusterName) {
    return vaultPathPrefix + clusterName;
  }

  private String getPath(String clusterName, String alias) {
    return getPath(clusterName) + VAULT_SEPARATOR + alias;
  }

  @Override
  public List<String> getAliasesForCluster(String clusterName) throws AliasServiceException {
    try {
      List<String> aliases = vault.list(getPath(clusterName));
      if(aliases == null) {
        return Collections.emptyList();
      }

      // Required to check if list entries are valid since versioned KV does a soft delete
      // Keys are still listed but do not have a value
      for (Iterator<String> iterator = aliases.iterator(); iterator.hasNext();) {
        String alias = iterator.next();
        if (getPasswordFromAliasForCluster(clusterName, alias) == null) {
          // Remove the current element from the iterator and the list.
          iterator.remove();
        }
      }

      return aliases;
    } catch (VaultException e) {
      throw new AliasServiceException(e);
    }
  }

  @Override
  public void addAliasForCluster(String clusterName, String alias, String value) throws AliasServiceException {
    try {
      vault.put(getPath(clusterName, alias), Collections.singletonMap(KEY, value));
    } catch (VaultException e) {
      throw new AliasServiceException(e);
    }
  }

  @Override
  public void addAliasesForCluster(String clusterName, Map<String, String> credentials) throws AliasServiceException {
    for (Map.Entry<String, String> credential : credentials.entrySet()) {
      addAliasForCluster(clusterName, credential.getKey(), credential.getValue());
    }
  }

  @Override
  public void removeAliasForCluster(String clusterName, String alias) throws AliasServiceException {
    // Delete is by default a soft delete with versioned KV in Vault
    // https://learn.hashicorp.com/vault/secrets-management/sm-versioned-kv#step-6-permanently-delete-data
    // Below is an example of how to programmatically delete all versions
    /*
    vaultTemplate.doWithSession(restOperations -> {
      restOperations.delete(VAULT_SEPARATOR + vaultSecretsEngine + "/metadata/" + clusterName + VAULT_SEPARATOR + alias);
      return null;
    });
     */
    try {
      vault.delete(getPath(clusterName, alias));
    } catch (VaultException e) {
      throw new AliasServiceException(e);
    }
  }

  @Override
  public void removeAliasesForCluster(String clusterName, Set<String> aliases) throws AliasServiceException {
    for (String alias : aliases) {
      removeAliasForCluster(clusterName, alias);
    }
  }

  @Override
  public char[] getPasswordFromAliasForCluster(String clusterName, String alias) throws AliasServiceException {
    try {
      Versioned<Map<String, Object>> mapVersioned = vault.get(getPath(clusterName, alias));
      if(mapVersioned != null && mapVersioned.hasData()) {
        Map<String, Object> data = mapVersioned.getData();
        if(data != null && data.containsKey(KEY)) {
          return String.valueOf(data.get(KEY)).toCharArray();
        }
      }
      return null;
    } catch (VaultException e) {
      throw new AliasServiceException(e);
    }
  }

  @Override
  public char[] getPasswordFromAliasForCluster(String clusterName, String alias, boolean generate) throws AliasServiceException {
    if(generate) {
      getPasswordFromAliasForCluster(clusterName, alias);
    }
    return getPasswordFromAliasForCluster(clusterName, alias);
  }

  @Override
  public void generateAliasForCluster(String clusterName, String alias) throws AliasServiceException {
    addAliasForCluster(clusterName, alias, PasswordUtils.generatePassword(16));
  }

  @Override
  public char[] getPasswordFromAliasForGateway(String alias) throws AliasServiceException {
    return getPasswordFromAliasForCluster(NO_CLUSTER_NAME, alias);
  }

  @Override
  public char[] getGatewayIdentityPassphrase() throws AliasServiceException {
    return getPasswordFromAliasForGateway(config.getIdentityKeyPassphraseAlias());
  }

  @Override
  public char[] getGatewayIdentityKeystorePassword() throws AliasServiceException {
    return getPasswordFromAliasForGateway(config.getIdentityKeystorePasswordAlias());
  }

  @Override
  public char[] getSigningKeyPassphrase() throws AliasServiceException {
    return getPasswordFromAliasForGateway(config.getSigningKeyPassphraseAlias());
  }

  @Override
  public char[] getSigningKeystorePassword() throws AliasServiceException {
    return getPasswordFromAliasForGateway(config.getSigningKeystorePasswordAlias());
  }

  @Override
  public void generateAliasForGateway(String alias) throws AliasServiceException {
    generateAliasForCluster(NO_CLUSTER_NAME, alias);
  }

  @Override
  public Certificate getCertificateForGateway(String alias) throws AliasServiceException {
    throw new AliasServiceException(new UnsupportedOperationException());
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
    this.config = config;
    Map<String, String> remoteAliasServiceConfiguration = config.getRemoteAliasServiceConfiguration();
    Map<String, String> vaultConfiguration = new HashMap<>();
    for(Map.Entry<String, String> entry : remoteAliasServiceConfiguration.entrySet()) {
      if(entry.getKey().startsWith(VAULT_CONFIG_PREFIX)) {
        vaultConfiguration.put(entry.getKey(),
            entry.getValue());
      }
    }

    String vaultAddress = vaultConfiguration.get(VAULT_ADDRESS_KEY);
    String vaultSecretsEngine = vaultConfiguration.get(VAULT_SECRETS_ENGINE_KEY);
    vaultPathPrefix = getVaultPathPrefix(vaultConfiguration);

    VaultEndpoint vaultEndpoint;
    try {
      vaultEndpoint = VaultEndpoint.from(new URI(vaultAddress));
      ClientAuthentication vaultAuthentication = getClientAuthentication(vaultConfiguration);
      VaultTemplate vaultTemplate = new VaultTemplate(vaultEndpoint, vaultAuthentication);
      vault = vaultTemplate.opsForVersionedKeyValue(vaultSecretsEngine);
    } catch (Exception e) {
      throw new ServiceLifecycleException("Failed to init", e);
    }
  }

  private String getVaultPathPrefix(Map<String, String> properties) {
    String vaultPathPrefix = properties.get(VAULT_PATH_PREFIX_KEY);
    if(vaultPathPrefix == null) {
      return "";
    }
    if(vaultPathPrefix.startsWith(VAULT_SEPARATOR)) {
      vaultPathPrefix = vaultPathPrefix.replaceFirst(VAULT_SEPARATOR, "");
    }
    if(vaultPathPrefix.endsWith(VAULT_SEPARATOR)) {
      return vaultPathPrefix;
    }
    return vaultPathPrefix + VAULT_SEPARATOR;
  }

  private ClientAuthentication getClientAuthentication(Map<String, String> properties)
      throws Exception {
    String authenticationType = properties.get(
        HashicorpVaultClientAuthenticationProvider.AUTHENTICATION_TYPE_KEY);

    ServiceLoader<HashicorpVaultClientAuthenticationProvider> providers =
        ServiceLoader.load(HashicorpVaultClientAuthenticationProvider.class);
    for (HashicorpVaultClientAuthenticationProvider provider : providers) {
      if(authenticationType.equals(provider.getType())) {
        return provider.newInstance(localAliasService, properties);
      }
    }

    throw new IllegalStateException("Not able to find client authentication provider");
  }

  @Override
  public void start() throws ServiceLifecycleException {
  }

  @Override
  public void stop() throws ServiceLifecycleException {
  }
}
