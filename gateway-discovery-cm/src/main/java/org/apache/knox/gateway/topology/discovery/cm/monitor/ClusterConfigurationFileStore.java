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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.topology.discovery.cm.ClouderaManagerServiceDiscoveryMessages;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * A file-based ClusterConfigurationStore implementation.
 */
public class ClusterConfigurationFileStore extends AbstractConfigurationStore
                                           implements ClusterConfigurationStore {

  private static final ClouderaManagerServiceDiscoveryMessages log =
                            MessagesFactory.get(ClouderaManagerServiceDiscoveryMessages.class);

  private ObjectMapper mapper;


  ClusterConfigurationFileStore(GatewayConfig gatewayConfig) {
    super(gatewayConfig);
    mapper = new ObjectMapper();
    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
  }

  @Override
  public void store(String address, String cluster, Map<String, ServiceConfigurationModel> configs) {
    Path persistenceDir = getPersistenceDir();
    if (persistenceDir != null && Files.exists(persistenceDir)) {
      File persistenceFile = getPersistenceFile(address, cluster);
      try (OutputStream out = Files.newOutputStream(persistenceFile.toPath())) {
        ServiceConfigurationRecord record = new ServiceConfigurationRecord();
        record.setClusterName(cluster);
        record.setDiscoveryAddress(address);
        record.setConfigs(configs);

        mapper.writeValue(out, record);
      } catch (Exception e) {
        log.failedToPersistClusterMonitorData(getMonitorType(), persistenceFile.getAbsolutePath(), e);
      }
    }
  }

  @Override
  public Set<ServiceConfigurationRecord> getAll() {
    Set<ServiceConfigurationRecord> result = new HashSet<>();

    Path persistenceDir = getPersistenceDir();
    if (persistenceDir != null && Files.exists(persistenceDir)) {
      Collection<File> persistedConfigs = FileUtils.listFiles(persistenceDir.toFile(), new String[]{"ver"}, false);
      for (File persisted : persistedConfigs) {
        result.add(get(persisted));
      }
    }

    return result;
  }

  @Override
  public ServiceConfigurationRecord get(String address, String cluster) {
    return get(getPersistenceFile(address, cluster));
  }

  @Override
  protected File getPersistenceFile(final String address, final String clusterName) {
    return getPersistenceFile(address, clusterName.replaceAll(" ", "_"), "ver");
  }

  private ServiceConfigurationRecord get(final File persisted) {
    ServiceConfigurationRecord result = null;

    if (persisted != null && persisted.exists()) {
      try (InputStream in = Files.newInputStream(persisted.toPath())) {
        result = mapper.readValue(in, ServiceConfigurationRecord.class);
      } catch (Exception e) {
        log.failedToLoadClusterMonitorServiceConfigurations(getMonitorType(), e);
      }
    }

    return result;
  }

}
