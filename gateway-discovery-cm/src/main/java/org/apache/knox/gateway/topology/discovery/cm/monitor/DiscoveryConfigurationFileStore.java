/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.discovery.cm.monitor;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * A file-based DiscoveryConfigurationStore implementation.
 */
public class DiscoveryConfigurationFileStore extends AbstractConfigurationStore
                                             implements DiscoveryConfigurationStore {

  private static final String PERSISTED_FILE_COMMENT = "Generated File. Do Not Edit!";

  private static final String PROP_CLUSTER_PREFIX = "cluster.";
  private static final String PROP_CLUSTER_SOURCE = PROP_CLUSTER_PREFIX + "source";
  private static final String PROP_CLUSTER_NAME = PROP_CLUSTER_PREFIX + "name";
  private static final String PROP_CLUSTER_USER = PROP_CLUSTER_PREFIX + "user";
  private static final String PROP_CLUSTER_ALIAS = PROP_CLUSTER_PREFIX + "pwd.alias";

  DiscoveryConfigurationFileStore(GatewayConfig gatewayConfig) {
    super(gatewayConfig);
  }

  @Override
  public void store(final ServiceDiscoveryConfig config) {
    Path persistenceDir = getPersistenceDir();
    if (persistenceDir != null && Files.exists(persistenceDir)) {

      String address = config.getAddress();
      String cluster = config.getCluster();

      Properties props = new Properties();
      props.setProperty(PROP_CLUSTER_NAME, cluster);
      props.setProperty(PROP_CLUSTER_SOURCE, address);

      String username = config.getUser();
      if (username != null) {
        props.setProperty(PROP_CLUSTER_USER, username);
      }
      String pwdAlias = config.getPasswordAlias();
      if (pwdAlias != null) {
        props.setProperty(PROP_CLUSTER_ALIAS, pwdAlias);
      }

      persist(props, getPersistenceFile(address,cluster));
    }
  }

  @Override
  public Set<ServiceDiscoveryConfig> getAll() {
    Set<ServiceDiscoveryConfig> result = new HashSet<>();

    Path persistenceDir = getPersistenceDir();
    if (persistenceDir != null && Files.exists(persistenceDir)) {
      Collection<File> persistedConfigs = FileUtils.listFiles(persistenceDir.toFile(), new String[]{"conf"}, false);
      for (File persisted : persistedConfigs) {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(persisted.toPath())) {
          props.load(in);

          if (StringUtils.isBlank(props.getProperty(PROP_CLUSTER_SOURCE))) {
            log.missingServiceDiscoveryConfigProperty(PROP_CLUSTER_SOURCE);
          } else if (StringUtils.isBlank(props.getProperty(PROP_CLUSTER_NAME))) {
            log.missingServiceDiscoveryConfigProperty(PROP_CLUSTER_NAME);
          } else {
            result.add(new ServiceDiscoveryConfig() {
              @Override
              public String getAddress() {
                return props.getProperty(PROP_CLUSTER_SOURCE);
              }

              @Override
              public String getCluster() {
                return props.getProperty(PROP_CLUSTER_NAME);
              }

              @Override
              public String getUser() {
                return props.getProperty(PROP_CLUSTER_USER);
              }

              @Override
              public String getPasswordAlias() {
                return props.getProperty(PROP_CLUSTER_ALIAS);
              }
            });
          }

        } catch (IOException e) {
          log.failedToLoadClusterMonitorServiceDiscoveryConfig(getMonitorType(), e);
        }
      }
    }

    return result;
  }

  @Override
  protected File getPersistenceFile(final String address, final String clusterName) {
    return getPersistenceFile(address, clusterName.replaceAll(" ", "_"), "conf");
  }

  private void persist(final Properties props, final File dest) {
    try (OutputStream out = Files.newOutputStream(dest.toPath())) {
      props.store(out, PERSISTED_FILE_COMMENT);
      out.flush();
    } catch (Exception e) {
      log.failedToPersistClusterMonitorData(getMonitorType(), dest.getAbsolutePath(), e);
    }
  }

}
