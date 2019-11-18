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
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClient;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.EncryptionResult;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.util.PasswordUtils;
import org.apache.zookeeper.ZooDefs;

import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An {@link AliasService} implementation based on zookeeper remote service registry.
 */
public class ZookeeperRemoteAliasService implements AliasService {
  public static final String TYPE = "zookeeper";

  public static final String PATH_KNOX = "/knox";
  public static final String PATH_KNOX_SECURITY = PATH_KNOX + "/security";
  public static final String PATH_KNOX_ALIAS_STORE_TOPOLOGY =
      PATH_KNOX_SECURITY + "/topology";
  public static final String PATH_SEPARATOR = "/";

  private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);
  // N.B. This is ZooKeeper-specific, and should be abstracted when another registry is supported
  private static final RemoteConfigurationRegistryClient.EntryACL AUTHENTICATED_USERS_ALL =
      new RemoteConfigurationRegistryClient.EntryACL() {
        @Override
        public String getId() {
          return "";
        }

        @Override
        public String getType() {
          return "auth";
        }

        @Override
        public Object getPermissions() {
          return ZooDefs.Perms.ALL;
        }

        @Override
        public boolean canRead() {
          return true;
        }

        @Override
        public boolean canWrite() {
          return true;
        }
      };

  private final AliasService localAliasService;
  private final MasterService ms;
  private final RemoteConfigurationRegistryClientService remoteConfigurationRegistryClientService;

  private RemoteConfigurationRegistryClient remoteClient;
  private ConfigurableEncryptor encryptor;
  private GatewayConfig config;

  ZookeeperRemoteAliasService(AliasService localAliasService, MasterService ms,
      RemoteConfigurationRegistryClientService remoteConfigurationRegistryClientService) {
    this.localAliasService = localAliasService;
    this.ms = ms;
    this.remoteConfigurationRegistryClientService = remoteConfigurationRegistryClientService;
  }

  /**
   * Build an entry path for the given cluster and alias
   */
  private static String buildAliasEntryName(final String clusterName,
      final String alias) {
    // Convert all alias names to lower case (JDK-4891485)
    return buildClusterEntryName(clusterName) + PATH_SEPARATOR + alias.toLowerCase(Locale.ROOT);
  }

  /**
   * Build an entry path for the given cluster
   */
  private static String buildClusterEntryName(final String clusterName) {
    return PATH_KNOX_ALIAS_STORE_TOPOLOGY + PATH_SEPARATOR + clusterName;
  }

  /**
   * Ensure that the given entry path exists.
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
   */
  private static void checkPathsExist(
      final RemoteConfigurationRegistryClient remoteClient) {
    ensureEntry(PATH_KNOX, remoteClient);
    ensureEntry(PATH_KNOX_SECURITY, remoteClient);
    ensureEntry(PATH_KNOX_ALIAS_STORE_TOPOLOGY, remoteClient);
    ensureEntry(
        PATH_KNOX_ALIAS_STORE_TOPOLOGY + PATH_SEPARATOR + NO_CLUSTER_NAME,
        remoteClient);
  }

  /**
   * Get a list of all aliases for a given cluster.
   * Remote aliases are preferred over local.
   *
   * @param clusterName cluster name
   * @return List of all the aliases
   */
  @Override
  public List<String> getAliasesForCluster(final String clusterName) throws AliasServiceException {
    List<String> remoteAliases = new ArrayList<>();

    /* If we have remote registry configured, query it */
    if (remoteClient != null) {
      remoteAliases = remoteClient
          .listChildEntries(buildClusterEntryName(clusterName));
    }

    return remoteAliases;
  }

  @Override
  public void addAliasForCluster(final String clusterName,
      final String alias, final String value)
      throws AliasServiceException {
    if (remoteClient != null) {
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
  public void removeAliasForCluster(final String clusterName, final String alias)
      throws AliasServiceException {
    /* If we have remote registry configured, query it */
    if (remoteClient != null) {
      final String aliasEntryPath = buildAliasEntryName(clusterName, alias);

      if (remoteClient.entryExists(aliasEntryPath)) {
        remoteClient.deleteEntry(aliasEntryPath);

        if (remoteClient.entryExists(aliasEntryPath)) {
          throw new IllegalStateException(String.format(Locale.ROOT,
              "Failed to delete alias %s for cluster %s in remote registry",
              alias, clusterName));
        }
      }
    }
  }

  @Override
  public char[] getPasswordFromAliasForCluster(String clusterName, String alias)
      throws AliasServiceException {
    return getPasswordFromAliasForCluster(clusterName, alias, false);
  }

  @Override
  public char[] getPasswordFromAliasForCluster(String clusterName,
      String alias, boolean generate) throws AliasServiceException {

    char[] password = null;

    /* try to get it from remote registry */
    if (remoteClient != null) {
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

    /* found nothing */
    return password;
  }

  @Override
  public void generateAliasForCluster(final String clusterName, final String alias)
      throws AliasServiceException {
    /* auto-generated password */
    final String passwordString = PasswordUtils.generatePassword(16);
    addAliasForCluster(clusterName, alias, passwordString);
  }

  @Override
  public char[] getPasswordFromAliasForGateway(String alias)
      throws AliasServiceException {
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
  public void generateAliasForGateway(final String alias) throws AliasServiceException {
    generateAliasForCluster(NO_CLUSTER_NAME, alias);
  }

  @Override
  public Certificate getCertificateForGateway(final String alias)
      throws AliasServiceException {
    throw new AliasServiceException(new UnsupportedOperationException());
  }

  @Override
  public void init(final GatewayConfig config, final Map<String, String> options)
      throws ServiceLifecycleException {
    this.config = config;

    /* If we have remote registry configured, query it */
    final String clientName = config.getRemoteConfigurationMonitorClientName();
    if (clientName != null && remoteConfigurationRegistryClientService != null) {
      remoteClient = remoteConfigurationRegistryClientService.get(clientName);

      /* ensure that nodes are properly setup */
      ensureEntries(remoteClient);

      /* Confirm access to the remote aliases directory */
      final List<String> aliases = remoteClient.listChildEntries(PATH_KNOX_ALIAS_STORE_TOPOLOGY);
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

      encryptor = new ConfigurableEncryptor(new String(ms.getMasterSecret()));
      encryptor.init(config);
    } else {
      LOG.missingClientConfigurationForRemoteMonitoring();
    }
  }

  @Override
  public void start() throws ServiceLifecycleException {

  }

  @Override
  public void stop() throws ServiceLifecycleException {
    if(remoteClient != null) {
      try {
        remoteClient.removeEntryListener(PATH_KNOX_ALIAS_STORE_TOPOLOGY);
      } catch (final Exception e) {
        LOG.errorRemovingRemoteListener(PATH_KNOX_ALIAS_STORE_TOPOLOGY, e.toString());
      }
    }
  }

  /**
   * Encrypt the clear text with master password.
   * @param clear clear text to be encrypted
   * @return encrypted and base 64 encoded result.
   * @throws Exception exception on failure
   */
  String encrypt(final String clear) throws Exception {
    final EncryptionResult result = encryptor.encrypt(clear);

    return Base64.encodeBase64String(
        (Base64.encodeBase64String(result.salt) + "::" +
             Base64.encodeBase64String(result.iv) + "::" +
             Base64.encodeBase64String(result.cipher)).getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Function to decrypt the encrypted text using master secret.
   *
   * @param encoded encoded and encrypted string.
   * @return decrypted password.
   * @throws Exception exception on failure
   */
  String decrypt(final String encoded) throws Exception {
    final String line = new String(Base64.decodeBase64(encoded), StandardCharsets.UTF_8);
    final String[] parts = line.split("::");
    if(parts.length != 3) {
      throw new IllegalArgumentException("Data should have 3 parts split by ::");
    }
    return new String(encryptor.decrypt(
        Base64.decodeBase64(parts[0]),
        Base64.decodeBase64(parts[1]),
        Base64.decodeBase64(parts[2])), StandardCharsets.UTF_8);
  }

  /**
   * Ensure that the nodes are properly set up.
   */
  private void ensureEntries(
      final RemoteConfigurationRegistryClient remoteClient) {
    ensureEntry(PATH_KNOX, remoteClient);
    ensureEntry(PATH_KNOX_SECURITY, remoteClient);
    ensureEntry(PATH_KNOX_ALIAS_STORE_TOPOLOGY, remoteClient);
    ensureEntry(
        PATH_KNOX_ALIAS_STORE_TOPOLOGY + PATH_SEPARATOR + NO_CLUSTER_NAME,
        remoteClient);
  }

  /**
   * A listener that listens for changes to the child nodes.
   */
  private class RemoteAliasChildListener
      implements RemoteConfigurationRegistryClient.ChildEntryListener {

    final ZookeeperRemoteAliasService remoteAliasService;

    RemoteAliasChildListener (final ZookeeperRemoteAliasService remoteAliasService ) {
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
          if (paths.length > 1) {
            localAliasService.removeAliasForCluster(paths[0], paths[1]);
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
                new RemoteAliasEntryListener(paths[0], paths[1],
                    remoteAliasService, localAliasService));
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
  private class RemoteAliasEntryListener
      implements RemoteConfigurationRegistryClient.EntryListener {
    final String cluster;
    final String alias;
    final AliasService remoteAliasService;
    final AliasService localAliasService;

    RemoteAliasEntryListener(final String cluster, final String alias,
                             final AliasService remoteAliasService,
                             final AliasService localAliasService) {
      this.cluster = cluster;
      this.alias = alias;
      this.remoteAliasService = remoteAliasService;
      this.localAliasService = localAliasService;
    }

    @Override
    public void entryChanged(final RemoteConfigurationRegistryClient client,
        final String path, final byte[] data) {
      try {
        localAliasService.addAliasForCluster(cluster, alias,
            decrypt(new String(data, StandardCharsets.UTF_8)));
      } catch (final Exception e) {
        /* log and move on */
        LOG.errorAddingAliasLocally(cluster, alias, e.toString());
      }
    }
  }
}
