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
package org.apache.knox.gateway.topology.discovery;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.knox.gateway.services.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates instances of ServiceDiscovery implementations.
 *
 * This factory uses the ServiceLoader mechanism to load ServiceDiscovery
 * implementations as extensions.
 *
 */
public abstract class ServiceDiscoveryFactory {

  private static final Logger LOG = LoggerFactory.getLogger(ServiceDiscoveryFactory.class);

  private static final Service[] NO_GATEWAY_SERVICS = new Service[] {};

  public static ServiceDiscovery get(String type) {
    return get(type, NO_GATEWAY_SERVICS);
  }

  public static Set<ServiceDiscovery> getAllServiceDiscoveries() {
    final Set<ServiceDiscovery> serviceDiscoveries = new HashSet<>();
    ServiceLoader.load(ServiceDiscoveryType.class).forEach((serviceDiscoveryType) -> {
      serviceDiscoveries.add(serviceDiscoveryType.newInstance());
    });
    return serviceDiscoveries;
  }

  public static ServiceDiscovery get(String type, Service... gatewayServices) {
    final ServiceDiscovery sd = getAllServiceDiscoveries().stream().filter(serviceDiscovery -> serviceDiscovery.getType().equalsIgnoreCase(type)).findFirst().orElse(null);
    // Inject any gateway services that were specified, and which are referenced in the impl
    injectGatewayServices(sd, gatewayServices);
    return sd;
  }

  private static void injectGatewayServices(final ServiceDiscovery serviceDiscovery, Service... gatewayServices) {
    if (ArrayUtils.isNotEmpty(gatewayServices)) {
      try {
        for (Field field : serviceDiscovery.getClass().getDeclaredFields()) {
          if (field.getDeclaredAnnotation(GatewayService.class) != null) {
            for (Service gatewayService : gatewayServices) {
              if (gatewayService != null) {
                if (field.getType().isAssignableFrom(gatewayService.getClass())) {
                  field.setAccessible(true);
                  field.set(serviceDiscovery, gatewayService);
                }
              }
            }
          }
        }
      } catch (Exception e) {
        LOG.error("Error while injecting Gateway Services in service discoveries", e);
      }
    }
  }
}
