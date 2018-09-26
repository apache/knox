/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.services.security.impl;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.GatewayServer;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClient;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.EncryptionResult;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.zookeeper.ZooDefs;

import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An {@link AliasService} implementation based on
 * remote service registry.
 * <p>
 * This class encapsulates the default AliasService implementation which uses
 * local keystore to store the aliases. The order in which Aliases are stored are
 * <ul>
 * <li>Local Keystore</li>
 * <li>Remote Registry</li>
 * </ul>
 *
 * @since 1.1.0
 */
public class RemoteAliasService implements AliasService {

  public static final String PATH_KNOX = "/knox";
  public static final String PATH_KNOX_SECURITY = PATH_KNOX + "/security";
  public static final String PATH_KNOX_ALIAS_STORE_TOPOLOGY =
      PATH_KNOX_SECURITY + "/topology";
  public static final String PATH_SEPARATOR = "/";
  public static final String DEFAULT_CLUSTER_NAME = "__gateway";
  public static final String GATEWAY_IDENTITY_PASSPHRASE = "gateway-identity-passphrase";

  private static final GatewayMessages LOG = MessagesFactory
      .get(GatewayMessages.class);
  // N.B. This is ZooKeeper-specific, and should be abstracted when another registry is supported
  private static final RemoteConfigurationRegistryClient.EntryACL AUTHENTICATED_USERS_ALL;

  static {
    AUTHENTICATED_USERS_ALL = new RemoteConfigurationRegistryClient.EntryACL() {
      public String getId() {
        return "";
      }

      public String getType() {
        return "auth";
      }

      public Object getPermissions() {
        return ZooDefs.Perms.ALL;
      }

      public boolean canRead() {
        return true;
      }

      public boolean canWrite() {
        return true;
      }
    };
  }

  private RemoteConfigurationRegistryClient remoteClient;
  private ConfigurableEncryptor encryptor;
  /**
   * Default alias service
   */
  private AliasService localAliasService;
  private RemoteConfigurationRegistryClientService registryClientService;
  private MasterService ms;
  private GatewayConfig config;
  private Map<String, String> options;

  /* create an instance */
  public RemoteAliasService() {
    super();
  }

  /**
   * Build an entry path for the given cluster and alias
   *
   * @param clusterName
   * @param alias
   * @return
   */
  private static String buildAliasEntryName(final String clusterName,
      final String alias) {
    return buildClusterEntryName(clusterName) + PATH_SEPARATOR + alias;
  }

  /**
   * Build an entry path for the given cluster
   *
   * @param clusterName
   * @return
   */
  private static String buildClusterEntryName(final String clusterName) {
    return PATH_KNOX_ALIAS_STORE_TOPOLOGY + PATH_SEPARATOR + clusterName;
  }

  /**
   * Ensure that the given entry path exists.
   *
   * @param path
   * @param remoteClient
   */
  private static void ensureEntry(final String path,
      final RemoteConfigurationRegistryClient remoteClient) {
    if (!remoteClient.entryExists(path)) {
      remoteClient.createEntry(path);
    } else {
      // Validate the ACL
      List<RemoteConfigurationRegistryClient.EntryACL> entryACLs = remoteClient
          .getACL(path);
      for (RemoteConfigurationRegistryClient.EntryACL entryACL : entryACLs) {
        // N.B. This is ZooKeeper-specific, and should be abstracted when another registry is supported
        // For now, check for world:anyone with ANY permissions (even read-only)
        if (entryACL.getType().equals("world") && entryACL.getId()
            .equals("anyone")) {
          LOG.suspectWritableRemoteConfigurationEntry(path);

          // If the client is authenticated, but "anyone" can write the content, then the content may not
          // be trustworthy.
          if (remoteClient.isAuthenticationConfigured()) {
            LOG.correctingSuspectWritableRemoteConfigurationEntry(path);

            // Replace the existing ACL with one that permits only authenticated users
            remoteClient.setACL(path,
                Collections.singletonList(AUTHENTICATED_USERS_ALL));
          }
        }
      }
    }
  }

  /**
   * Check to make sure all the required entries are properly set up
   *
   * @param remoteClient
   */
  private static void checkPathsExist(
      final RemoteConfigurationRegistryClient remoteClient) {
    ensureEntry(PATH_KNOX, remoteClient);
    ensureEntry(PATH_KNOX_SECURITY, remoteClient);
    ensureEntry(PATH_KNOX_ALIAS_STORE_TOPOLOGY, remoteClient);
    ensureEntry(
        PATH_KNOX_ALIAS_STORE_TOPOLOGY + PATH_SEPARATOR + DEFAULT_CLUSTER_NAME,
        remoteClient);

  }

  /**
   * Returns an empty list if the given list is null,
   * else returns the given list.
   *
   * @param given
   * @return
   */
  private static List<String> safe(final List given) {
    return given == null ? Collections.EMPTY_LIST : given;
  }

  /**
   * Set a {@link RemoteConfigurationRegistryClientService} instance
   * used to talk to remote remote service registry.
   *
   * @param registryClientService
   */
  public void setRegistryClientService(
      final RemoteConfigurationRegistryClientService registryClientService) {
    this.registryClientService = registryClientService;
  }

  /**
   * Set a {@link MasterService} instance.
   *
   * @param ms
   */
  public void setMasterService(final MasterService ms) {
    this.ms = ms;
  }

  /**
   * Set local alias service
   *
   * @param localAliasService
   */
  public void setLocalAliasService(AliasService localAliasService) {
    this.localAliasService = localAliasService;
  }

  /**
   * Get a list of all aliases for a given cluster.
   * Remote aliases are preferred over local.
   *
   * @param clusterName cluster name
   * @return List of all the aliases
   * @throws AliasServiceException
   */
  @Override
  public List<String> getAliasesForCluster(final String clusterName)
      throws AliasServiceException {

    List<String> remoteAliases = new ArrayList<>();

    /* If we have remote registry configured, query it */
    if (remoteClient != null && config.isRemoteAliasServiceEnabled()) {
      remoteAliases = remoteClient
          .listChildEntries(buildClusterEntryName(clusterName));
    }

    List<String> localAliases = localAliasService
        .getAliasesForCluster(clusterName);

    /* merge */
    for (final String alias : safe(localAliases)) {
      if (!remoteAliases.contains(alias.toLowerCase(Locale.ROOT))) {
        remoteAliases.add(alias);
      }
    }

    return remoteAliases;
  }

  @Override
  public void addAliasForCluster(final String clusterName,
      final String givenAlias, final String value)
      throws AliasServiceException {

    /* convert all alias names to lower case since JDK expects the same behaviour */
    final String alias = givenAlias.toLowerCase(Locale.ROOT);

    /* first add the alias to the local keystore */
    localAliasService.addAliasForCluster(clusterName, alias, value);

    if (remoteClient != null && config.isRemoteAliasServiceEnabled()) {

      final String aliasEntryPath = buildAliasEntryName(clusterName, alias);

      /* Ensure the entries are properly set up */
      checkPathsExist(remoteClient);
      ensureEntry(buildClusterEntryName(clusterName), remoteClient);
      try {

        remoteClient.createEntry(aliasEntryPath, encrypt(value));

      } catch (Exception e) {
        throw new AliasServiceException(e);
      }

      if (remoteClient.getEntryData(aliasEntryPath) == null) {
        throw new IllegalStateException(String.format(Locale.ROOT, 
            "Failed to store alias %s for cluster %s in remote registry", alias,
            clusterName));
      }

    }
  }

  @Override
  public void removeAliasForCluster(final String clusterName,
      final String givenAlias) throws AliasServiceException {

    /* convert all alias names to lower case since JDK expects the same behaviour */
    final String alias = givenAlias.toLowerCase(Locale.ROOT);

    /* first remove it from the local keystore */
    localAliasService.removeAliasForCluster(clusterName, alias);

    /* If we have remote registry configured, query it */
    if (remoteClient != null && config.isRemoteAliasServiceEnabled()) {

      final String aliasEntryPath = buildAliasEntryName(clusterName, alias);

      if (remoteClient.entryExists(aliasEntryPath)) {
        remoteClient.deleteEntry(aliasEntryPath);

        if (remoteClient.entryExists(aliasEntryPath)) {
          throw new IllegalStateException(String.format(Locale.ROOT, 
              "Failed to delete alias %s for cluster %s in remote registry",
              alias, clusterName));
        }

      }

    } else {
      LOG.missingClientConfigurationForRemoteMonitoring();
    }

  }

  @Override
  public char[] getPasswordFromAliasForCluster(String clusterName, String alias)
      throws AliasServiceException {
    return getPasswordFromAliasForCluster(clusterName, alias, false);
  }

  @Override
  public char[] getPasswordFromAliasForCluster(String clusterName,
      String givenAlias, boolean generate) throws AliasServiceException {

    /* convert all alias names to lower case since JDK expects the same behaviour */
    final String alias = givenAlias.toLowerCase(Locale.ROOT);

    char[] password = null;

    /* try to get it from remote registry */
    if (remoteClient != null && config.isRemoteAliasServiceEnabled()) {

      checkPathsExist(remoteClient);
      String encrypted = null;

      if(remoteClient.entryExists(buildAliasEntryName(clusterName, alias))) {
        encrypted = remoteClient
            .getEntryData(buildAliasEntryName(clusterName, alias));
      }

      /* Generate a new password */
      if (encrypted == null) {

        /* Generate a new password  */
        if (generate) {
          generateAliasForCluster(clusterName, alias);
          password = getPasswordFromAliasForCluster(clusterName, alias);
        }

      } else {
        try {
          password = decrypt(encrypted).toCharArray();
        } catch (final Exception e) {
          throw new AliasServiceException(e);
        }
      }

    }

    /*
     * If
     * 1. Remote registry not configured or
     * 2. Password not found for given alias in remote registry,
     * Then try local keystore
     */
    if(password == null) {
      /* try to get it from the local keystore, ignore generate flag. */
      password = localAliasService
          .getPasswordFromAliasForCluster(clusterName, alias, generate);
    }

    /* found nothing */
    return password;
  }

  @Override
  public void generateAliasForCluster(final String clusterName,
      final String givenAlias) throws AliasServiceException {

    /* convert all alias names to lower case since JDK expects the same behaviour */
    final String alias = givenAlias.toLowerCase(Locale.ROOT);
    /* auto-generated password */
    final String passwordString = DefaultAliasService.generatePassword(16);
    addAliasForCluster(clusterName, alias, passwordString);
  }

  @Override
  public char[] getPasswordFromAliasForGateway(String alias)
      throws AliasServiceException {
    return getPasswordFromAliasForCluster(DEFAULT_CLUSTER_NAME, alias);
  }

  @Override
  public char[] getGatewayIdentityPassphrase() throws AliasServiceException {
    char[] passphrase = getPasswordFromAliasForGateway(
        GATEWAY_IDENTITY_PASSPHRASE);
    if (passphrase == null) {
      passphrase = ms.getMasterSecret();
    }
    return passphrase;
  }

  @Override
  public void generateAliasForGateway(final String alias)
      throws AliasServiceException {
    generateAliasForCluster(DEFAULT_CLUSTER_NAME, alias);
  }

  @Override
  public Certificate getCertificateForGateway(final String alias)
      throws AliasServiceException {
    /* We don't store certs in remote registry so we just delegate certs to keystore (DefaultAliasService.getCertificateForGateway) */
    return localAliasService.getCertificateForGateway(alias);
  }

  @Override
  public void init(final GatewayConfig config,
      final Map<String, String> options) throws ServiceLifecycleException {
    this.config = config;
    this.options = options;

    /* setup and initialize encryptor for encryption and decryption of passwords */
    encryptor = new ConfigurableEncryptor(new String(ms.getMasterSecret()));
    encryptor.init(config);

    /* If we have remote registry configured, query it */
    final String clientName = config.getRemoteConfigurationMonitorClientName();
    if (clientName != null) {

      if (registryClientService != null) {

        remoteClient = registryClientService.get(clientName);

      } else {
        throw new ServiceLifecycleException(
            "Remote configuration registry not initialized");
      }

    } else {
      LOG.missingClientConfigurationForRemoteMonitoring();
    }

  }

  @Override
  public void start() throws ServiceLifecycleException {

    if (remoteClient != null && config.isRemoteAliasServiceEnabled()) {

      /* ensure that nodes are properly setup */
      ensureEntries(remoteClient);

      /* Confirm access to the remote aliases directory */
      final List<String> aliases = remoteClient
          .listChildEntries(PATH_KNOX_ALIAS_STORE_TOPOLOGY);
      if (aliases == null) {
        // Either the entry does not exist, or there is an authentication problem
        throw new IllegalStateException(
            "Unable to access remote path: " + PATH_KNOX_ALIAS_STORE_TOPOLOGY);
      }

      /* Register a listener for aliases entry additions/removals */
      try {
        remoteClient.addChildEntryListener(PATH_KNOX_ALIAS_STORE_TOPOLOGY,
            new RemoteAliasChildListener(this));
      } catch (final Exception e) {
        throw new IllegalStateException(
            "Unable to add listener for path " + PATH_KNOX_ALIAS_STORE_TOPOLOGY,
            e);
      }

    }

    if(!config.isRemoteAliasServiceEnabled()) {
      LOG.remoteAliasServiceDisabled();
    } else {
      LOG.remoteAliasServiceEnabled();
    }

  }

  @Override
  public void stop() throws ServiceLifecycleException {
    if(remoteClient != null && config.isRemoteAliasServiceEnabled()) {
      try {
        remoteClient.removeEntryListener(PATH_KNOX_ALIAS_STORE_TOPOLOGY);
      } catch (final Exception e) {
        LOG.errorRemovingRemoteListener(PATH_KNOX_ALIAS_STORE_TOPOLOGY, e.toString());
      }
    }
  }

  /**
   * Add the alias to the local keystore.
   * Most likely this will be called by remote registry watch listener.
   *
   * @param clusterName Name of the cluster
   * @param alias       Alias name to be added
   * @param value       alias value to be added
   * @throws AliasServiceException
   */
  public void addAliasForClusterLocally(final String clusterName,
      final String alias, final String value) throws AliasServiceException {
    localAliasService.addAliasForCluster(clusterName, alias, value);
  }

  /**
   * Remove the given alias from local keystore.
   * Most likely this will be called by remote registry watch listener.
   *
   * @param clusterName Name of the cluster
   * @param alias       Alias name to be removed
   * @throws AliasServiceException
   */
  public void removeAliasForClusterLocally(final String clusterName,
      final String alias) throws AliasServiceException {
    LOG.removeAliasLocally(clusterName, alias);
    localAliasService.removeAliasForCluster(clusterName, alias);
  }

  /**
   * Ensure that the nodes are properly set up.
   *
   * @param remoteClient
   */
  private void ensureEntries(
      final RemoteConfigurationRegistryClient remoteClient) {
    ensureEntry(PATH_KNOX, remoteClient);
    ensureEntry(PATH_KNOX_SECURITY, remoteClient);
    ensureEntry(PATH_KNOX_ALIAS_STORE_TOPOLOGY, remoteClient);
    ensureEntry(
        PATH_KNOX_ALIAS_STORE_TOPOLOGY + PATH_SEPARATOR + DEFAULT_CLUSTER_NAME,
        remoteClient);
  }

  /**
   * Encrypt the clear text with master password.
   * @param clear clear text to be encrypted
   * @return encrypted and base 64 encoded result.
   * @throws Exception
   */
  public String encrypt(final String clear) throws Exception {

    final EncryptionResult result = encryptor.encrypt(clear);

    return Base64.encodeBase64String(
        (Base64.encodeBase64String(result.salt) + "::" + Base64
            .encodeBase64String(result.iv) + "::" + Base64
            .encodeBase64String(result.cipher)).getBytes("UTF8"));

  }

  /**
   * Function to decrypt the encrypted text using master secret.
   *
   * @param encoded encoded and encrypted string.
   * @return decrypted password.
   */
  public String decrypt(final String encoded) throws Exception {

    final String line = new String(Base64.decodeBase64(encoded), StandardCharsets.UTF_8);
    final String[] parts = line.split("::");

    return new String(encryptor
        .decrypt(Base64.decodeBase64(parts[0]), Base64.decodeBase64(parts[1]),
            Base64.decodeBase64(parts[2])), StandardCharsets.UTF_8);
  }

  /**
   * A listener that listens for changes to the child nodes.
   */
  private class RemoteAliasChildListener
      implements RemoteConfigurationRegistryClient.ChildEntryListener {

    final RemoteAliasService remoteAliasService;

    public RemoteAliasChildListener (final RemoteAliasService remoteAliasService ) {
      this.remoteAliasService = remoteAliasService;
    }

    @Override
    public void childEvent(final RemoteConfigurationRegistryClient client,
        final Type type, final String path) {

      final String subPath = StringUtils.substringAfter(path,
          PATH_KNOX_ALIAS_STORE_TOPOLOGY + PATH_SEPARATOR);
      final String[] paths = StringUtils.split(subPath, '/');

      switch (type) {
      case REMOVED:
        try {
          /* remove listener */
          client.removeEntryListener(path);

          if (GatewayServer.getGatewayServices() != null) {
            /* remove the alias from local keystore */
            final AliasService aliasService = GatewayServer.getGatewayServices()
                .getService(GatewayServices.ALIAS_SERVICE);
            if (aliasService != null && paths.length > 1
                && aliasService instanceof RemoteAliasService) {
              ((RemoteAliasService) aliasService)
                  .removeAliasForClusterLocally(paths[0], paths[1]);
            }
          }

        } catch (final Exception e) {
          LOG.errorRemovingAliasLocally(paths[0], paths[1], e.toString());
        }
        break;

      case ADDED:
        /* do not set listeners on cluster name but on respective aliases */
        if (paths.length > 1) {
          LOG.addAliasLocally(paths[0], paths[1]);
          try {
            client.addEntryListener(path,
                new RemoteAliasEntryListener(paths[0], paths[1], remoteAliasService));
          } catch (final Exception e) {
            LOG.errorRemovingAliasLocally(paths[0], paths[1], e.toString());
          }

        } else if (subPath != null) {
          /* Add a child listener for the cluster */
          LOG.addRemoteListener(path);
          try {
            client.addChildEntryListener(path, new RemoteAliasChildListener(remoteAliasService));
          } catch (Exception e) {
            LOG.errorAddingRemoteListener(path, e.toString());
          }

        }

        break;
      }

    }
  }

  /**
   * A listener that listens for changes to node value.
   */
  private static class RemoteAliasEntryListener
      implements RemoteConfigurationRegistryClient.EntryListener {

    final String cluster;
    final String alias;
    final RemoteAliasService remoteAliasService;

    /**
     * Create an instance
     *
     * @param cluster
     * @param alias
     */
    public RemoteAliasEntryListener(final String cluster, final String alias, final RemoteAliasService remoteAliasService) {
      super();
      this.cluster = cluster;
      this.alias = alias;
      this.remoteAliasService = remoteAliasService;
    }

    @Override
    public void entryChanged(final RemoteConfigurationRegistryClient client,
        final String path, final byte[] data) {

      if (GatewayServer.getGatewayServices() != null) {
        final AliasService aliasService = GatewayServer.getGatewayServices()
            .getService(GatewayServices.ALIAS_SERVICE);

        if (aliasService != null
            && aliasService instanceof RemoteAliasService) {
          try {
            ((RemoteAliasService) aliasService)
                .addAliasForClusterLocally(cluster, alias, remoteAliasService.decrypt(new String(data, StandardCharsets.UTF_8)));
          } catch (final Exception e) {
            /* log and move on */
            LOG.errorAddingAliasLocally(cluster, alias, e.toString());
          }

        }

      }
    }

  }

}
