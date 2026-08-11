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
package org.apache.knox.gateway.services.factory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceFactory;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Topology;

public abstract class AbstractServiceFactory implements ServiceFactory {

  private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);
  private static final String IMPLEMENTATION_PARAM_NAME = "impl";
  private static final String EMPTY_DEFAULT_IMPLEMENTATION = "";

  /** Topology service roles that enable the KnoxIDF-backed service implementations. */
  private static final String KNOXIDF_ROLE = "KNOXIDF";
  private static final String KNOXIDF_ADMIN_ROLE = "KNOXIDF_ADMIN";

  @Override
  public Service create(GatewayServices gatewayServices, ServiceType serviceType, GatewayConfig gatewayConfig, Map<String, String> options) throws ServiceLifecycleException {
    return create(gatewayServices, serviceType, gatewayConfig, options, getImplementation(gatewayConfig));
  }

  @Override
  public Service create(GatewayServices gatewayServices, ServiceType serviceType, GatewayConfig gatewayConfig, Map<String, String> options, String implementation)
      throws ServiceLifecycleException {
    Service service = null;
    if (getServiceType() == serviceType) {
      service = createService(gatewayServices, serviceType, gatewayConfig, options, implementation);
      if (service == null && StringUtils.isNotBlank(implementation)) {
        // no known service implementation created, try to create the custom one
        try {
          service = (Service) Class.forName(implementation).getDeclaredConstructor().newInstance();
          logServiceUsage(implementation, serviceType);
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | NoSuchMethodException |
                 InvocationTargetException e) {
          throw new ServiceLifecycleException("Error while instantiating " + serviceType.getShortName() + " service implementation " + implementation, e);
        }
      }
    }
    return service;
  }

  protected String getImplementation(GatewayConfig gatewayConfig) {
    return gatewayConfig.getServiceParameter(getServiceType().getShortName(), IMPLEMENTATION_PARAM_NAME);
  }

  protected boolean matchesImplementation(String implementation, Class<? extends Object> clazz) {
    return matchesImplementation(implementation, clazz, false);
  }

  protected boolean matchesImplementation(String implementation, Class<? extends Object> clazz, boolean acceptEmptyImplementation) {
    boolean match = clazz.getName().equals(implementation);
    if (!match && acceptEmptyImplementation) {
      match = isEmptyDefaultImplementation(implementation);
    }
    return match;
  }

  protected boolean isEmptyDefaultImplementation(String implementation) {
    return EMPTY_DEFAULT_IMPLEMENTATION.equals(implementation);
  }

  protected boolean shouldCreateService(String implementation) {
    return implementation == null || isEmptyDefaultImplementation(implementation) || getKnownImplementations().contains(implementation);
  }

  protected MasterService getMasterService(GatewayServices gatewayServices) {
    return gatewayServices.getService(ServiceType.MASTER_SERVICE);
  }

  protected KeystoreService getKeystoreService(GatewayServices gatewayServices) {
    return gatewayServices.getService(ServiceType.KEYSTORE_SERVICE);
  }

  protected AliasService getAliasService(GatewayServices gatewayServices) {
    return gatewayServices.getService(ServiceType.ALIAS_SERVICE);
  }

  protected void logServiceUsage(String implementation, ServiceType serviceType) {
    LOG.usingServiceImplementation(isEmptyDefaultImplementation(implementation) ? "default" : implementation, serviceType.getServiceTypeName());
  }

  /**
   * Returns {@code true} if any topology enables KnoxIDF (a service with role {@code KNOXIDF} or
   * {@code KNOXIDF_ADMIN}).
   * <p>
   * Service factories run during {@code DefaultGatewayServices.init}, before the topology monitor
   * has loaded any topologies, so {@link TopologyService#getTopologies()} is typically empty at
   * this point. To detect KnoxIDF anyway we fall back to scanning the on-disk topology directory
   * for the enabling role. The in-memory check is kept first so a caller that runs after topologies
   * are loaded still works. Known limitation: descriptor-generated {@code .topology} files that are
   * not yet materialised on disk at init time are not seen (still strictly better than relying on
   * the empty in-memory map alone).
   */
  protected boolean isKnoxIdfEnabledInAnyTopology(GatewayServices gatewayServices, GatewayConfig gatewayConfig) {
    final TopologyService topologyService = gatewayServices.getService(ServiceType.TOPOLOGY_SERVICE);
    if (topologyService != null) {
      for (Topology topology : topologyService.getTopologies()) {
        if (topology.getServices().stream().anyMatch(service -> isKnoxIdfRole(service.getRole()))) {
          return true;
        }
      }
    }
    return isKnoxIdfEnabledOnDisk(gatewayConfig);
  }

  private static boolean isKnoxIdfRole(String role) {
    return KNOXIDF_ROLE.equals(role) || KNOXIDF_ADMIN_ROLE.equals(role);
  }

  private static boolean isKnoxIdfEnabledOnDisk(GatewayConfig gatewayConfig) {
    if (gatewayConfig == null) {
      return false;
    }
    final String topologyDir = gatewayConfig.getGatewayTopologyDir();
    if (StringUtils.isBlank(topologyDir)) {
      return false;
    }
    final Path dir = Paths.get(topologyDir);
    if (!Files.isDirectory(dir)) {
      return false;
    }
    try (Stream<Path> files = Files.list(dir)) {
      return files.filter(AbstractServiceFactory::isTopologyFile).anyMatch(AbstractServiceFactory::topologyFileEnablesKnoxIdf);
    } catch (IOException e) {
      LOG.failedToListTopologyDirForKnoxIdfDetection(topologyDir, e.getMessage(), e);
      return false;
    }
  }

  private static boolean isTopologyFile(Path path) {
    if (!Files.isRegularFile(path)) {
      return false;
    }
    final String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".xml") || name.endsWith(".topology");
  }

  private static boolean topologyFileEnablesKnoxIdf(Path path) {
    try {
      final String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
      return content.contains("<role>" + KNOXIDF_ROLE + "</role>") || content.contains("<role>" + KNOXIDF_ADMIN_ROLE + "</role>");
    } catch (IOException e) {
      LOG.failedToReadTopologyFileForKnoxIdfDetection(path.toString(), e.getMessage(), e);
      return false;
    }
  }

  // abstract methods

  protected abstract Service createService(GatewayServices gatewayServices, ServiceType serviceType, GatewayConfig gatewayConfig, Map<String, String> options,
      String implementation) throws ServiceLifecycleException;

  protected abstract ServiceType getServiceType();

  protected abstract Collection<String> getKnownImplementations();
}
