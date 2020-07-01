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

import java.util.Map;
import java.util.ServiceLoader;

import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

public class GatewayServiceFactory implements ServiceFactory {
  private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);
  private ServiceLoader<ServiceFactory> serviceFactories;

  @Override
  public Service create(GatewayServices gatewayServices, ServiceType serviceType, GatewayConfig gatewayConfig, Map<String, String> options) throws ServiceLifecycleException {
    return create(gatewayServices, serviceType, gatewayConfig, options, null);
  }

  @Override
  public Service create(GatewayServices gatewayServices, ServiceType serviceType, GatewayConfig gatewayConfig, Map<String, String> options, String implementation)
      throws ServiceLifecycleException {
    Service service = null;
    for (ServiceFactory serviceFactory : getServiceFactories()) {
      service = implementation == null ? serviceFactory.create(gatewayServices, serviceType, gatewayConfig, options)
          : serviceFactory.create(gatewayServices, serviceType, gatewayConfig, options, implementation);
      if (service != null) {
        break;
      }
    }
    if (service != null) {
      service.init(gatewayConfig, options);
    } else {
      LOG.noServiceFound(serviceType.getServiceTypeName());
    }
    return service;
  }

  private ServiceLoader<ServiceFactory> getServiceFactories() {
    if (serviceFactories == null) {
      serviceFactories = ServiceLoader.load(ServiceFactory.class);
    }
    return serviceFactories;
  }
}
