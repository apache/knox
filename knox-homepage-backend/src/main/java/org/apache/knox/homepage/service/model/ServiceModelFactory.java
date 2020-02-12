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
package org.apache.knox.homepage.service.model;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import javax.servlet.http.HttpServletRequest;

import org.apache.knox.gateway.topology.Service;

public class ServiceModelFactory {

  private static final Map<String, ServiceModel> serviceModels = new HashMap<>();

  static {
    for (ServiceModel serviceModel : ServiceLoader.load(ServiceModel.class)) {
      serviceModels.put(serviceModel.getServiceName(), serviceModel);
    }
  }

  public static ServiceModel getServiceModel(HttpServletRequest request, String gatewayPath, String topologyName, Service service) {
    ServiceModel serviceModel = serviceModels == null ? null : serviceModels.get(service.getRole());
    serviceModel = serviceModel == null ? new ServiceModel() : serviceModel;
    serviceModel.setRequest(request);
    serviceModel.setGatewayPath(gatewayPath);
    serviceModel.setTopologyName(topologyName);
    serviceModel.setService(service);
    return serviceModel;
  }

}
