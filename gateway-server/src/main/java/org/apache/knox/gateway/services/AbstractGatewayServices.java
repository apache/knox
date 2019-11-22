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

import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AbstractGatewayServices is an abstract implementation of {@link GatewayServices} that manages the
 * contained/registered services. See {@link ServiceType} for the different types of services that may
 * be registered.
 *
 * This implementation ensures the proper ordering of registered services when starting and stopping
 * them.
 *
 * {@link GatewayServices} implementations should extend this class and register relevant services
 * during the initialization process ({@link GatewayServices#init(GatewayConfig, Map)} using
 * {@link #addService(ServiceType, Service)}.
 */
public abstract class AbstractGatewayServices implements GatewayServices {

  private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);

  private final Map<ServiceType, Service> services = new EnumMap<>(ServiceType.class);
  private final List<Service> orderedServices = new ArrayList<>();
  private final String role;
  private final String name;

  AbstractGatewayServices(String role, String name) {
    this.role = role;
    this.name = name;
  }

  @Override
  public void start() throws ServiceLifecycleException {
    for (Service service : orderedServices) {
      LOG.startingService(service.getClass().getName());
      service.start();
    }
  }

  @Override
  public void stop() throws ServiceLifecycleException {
    // Reverse the preferred startup order
    ArrayList<Service> copy = new ArrayList<>(orderedServices);
    Collections.reverse(copy);

    for (Service service : copy) {
      LOG.stoppingService(service.getClass().getName());
      service.stop();
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
   *
   * An implementation for a service type may be added/registered only once.  Adding a service
   * implementation for a service type that was previously added overwrites the previously added
   * implementation.
   *
   * @param serviceType the {@link ServiceType} type to add
   * @param service     the {@link Service} implementation
   */
  protected void addService(ServiceType serviceType, Service service) {

    // Ensure the service is not null
    if (service == null) {
      throw new NullPointerException("A null service may not be added.");
    }

    orderedServices.add(service);
    services.put(serviceType, service);
  }
}
