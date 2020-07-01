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

public abstract class AbstractServiceFactory implements ServiceFactory {

  private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);

  @Override
  public Service create(GatewayServices gatewayServices, ServiceType serviceType, GatewayConfig gatewayConfig, Map<String, String> options) throws ServiceLifecycleException {
    return create(gatewayServices, serviceType, gatewayConfig, options, getImplementation(gatewayConfig));
  }

  protected String getImplementation(GatewayConfig gatewayConfig) {
    return gatewayConfig.getServiceImplementation(getServiceType().getShortName());
  }

  protected abstract ServiceType getServiceType();

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
    LOG.usingServiceImplementation("".equals(implementation) ? "default" : implementation, serviceType.getServiceTypeName());
  }
}
