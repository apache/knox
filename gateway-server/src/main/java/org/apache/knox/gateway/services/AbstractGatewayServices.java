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
package org.apache.knox.gateway.services;

import static org.apache.knox.gateway.services.ServiceType.PREFERRED_START_ORDER;

import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * AbstractGatewayServices is an abstract implementation of {@link GatewayServices} that manages the
 * contained/registered services. See {@link ServiceType} for the different types of services that may
 * be registered.
 * <p>
 * This implementation ensures the proper ordering of registered services when starting and stopping
 * them.
 * <p>
 * {@link GatewayServices} implementations should extend this class and register relevant services
 * during the initialization process ({@link GatewayServices#init(GatewayConfig, Map)} using
 * {@link #addService(ServiceType, Service)}.
 * <p>
 */
public abstract class AbstractGatewayServices implements GatewayServices {

  private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);

  private final Map<ServiceType, Service> services = new EnumMap<>(ServiceType.class);
  private final String role;
  private final String name;

  AbstractGatewayServices(String role, String name) {
    this.role = role;
    this.name = name;
  }

  @Override
  public void start() throws ServiceLifecycleException {
    for (ServiceType serviceType : PREFERRED_START_ORDER) {
      Service service = services.get(serviceType);
      // If the the service has not been registered, skip it.
      if (service != null) {
        LOG.startingService(serviceType.getServiceTypeName());
        service.start();
      }
    }
  }

  @Override
  public void stop() throws ServiceLifecycleException {
    // Reverse the preferred startup order
    ArrayList<ServiceType> copy = new ArrayList<>(Arrays.asList(PREFERRED_START_ORDER));
    Collections.reverse(copy);

    for (ServiceType serviceType : copy) {
      Service service = services.get(serviceType);
      // If the the service has not been registered, skip it.
      if (service != null) {
        LOG.stoppingService(serviceType.getServiceTypeName());
        service.stop();
      }
    }
  }

  @Override
  public String getRole() {
    return role;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Set<ServiceType> getServiceTypes() {
    return services.keySet();
  }

  @Override
  public <T> T getService(ServiceType serviceType) {
    return (T) services.get(serviceType);
  }

  /**
   * Add/register a service with this container.
   * <p>
   * An implementation for a service type may be added/registered only once.  Adding a service
   * implementation for a service type that was previously added overwrites the previously added
   * implementation.
   *
   * @param serviceType the {@link ServiceType} type to add
   * @param service     the {@link Service} implementation
   */
  protected void addService(ServiceType serviceType, Service service) {
    services.put(serviceType, service);
  }
}
