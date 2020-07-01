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

import java.util.Map;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.impl.CLIMasterService;
import org.apache.knox.gateway.services.security.impl.DefaultMasterService;

public class MasterServiceFactory extends AbstractServiceFactory {
  private static final String DEFAULT_IMPLEMENTATION_NAME = "default";
  private static final String CLI_IMPLEMENTATION_NAME = "cli";

  @Override
  public Service create(GatewayServices gatewayServices, ServiceType serviceType, GatewayConfig gatewayConfig, Map<String, String> options, String implementation)
      throws ServiceLifecycleException {
    Service service = null;
    if (getServiceType() == serviceType) {
      switch (implementation) {
      case DEFAULT_IMPLEMENTATION_NAME:
      case "":
        service = new DefaultMasterService();
        break;
      case CLI_IMPLEMENTATION_NAME:
        service = new CLIMasterService();
        break;
      default:
        throw new IllegalArgumentException("Invalid Master Service implementation provided: " + implementation);
      }
    }

    logServiceUsage(implementation, serviceType);
    return service;
  }

  @Override
  protected ServiceType getServiceType() {
    return ServiceType.MASTER_SERVICE;
  }

}
