/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.ha.provider.impl;

import org.apache.knox.gateway.ha.provider.HaDescriptor;
import org.apache.knox.gateway.ha.provider.HaServiceConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultHaDescriptor implements HaDescriptor {

   private ConcurrentHashMap<String, HaServiceConfig> serviceConfigs;

   public DefaultHaDescriptor() {
      serviceConfigs = new ConcurrentHashMap<>();
   }

   @Override
   public void addServiceConfig(HaServiceConfig serviceConfig) {
      if (serviceConfig == null) {
         throw new IllegalArgumentException("Service config must not be null");
      }
      serviceConfigs.put(serviceConfig.getServiceName(), serviceConfig);
   }

   @Override
   public HaServiceConfig getServiceConfig(String serviceName) {
      return serviceConfigs.get(serviceName);
   }

   @Override
   public List<HaServiceConfig> getServiceConfigs() {
      return new ArrayList<>(serviceConfigs.values());
   }

   @Override
   public List<String> getServiceNames() {
      return new ArrayList<>(serviceConfigs.keySet());
   }

   @Override
   public List<String> getEnabledServiceNames() {
      ArrayList<String> services = new ArrayList<>(serviceConfigs.size());
      Collection<HaServiceConfig> configs = serviceConfigs.values();
      for (HaServiceConfig config : configs) {
         if (config.isEnabled()) {
            services.add(config.getServiceName());
         }
      }
      return services;
   }
}
