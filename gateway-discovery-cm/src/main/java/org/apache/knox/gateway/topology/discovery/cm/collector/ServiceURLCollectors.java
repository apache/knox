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
package org.apache.knox.gateway.topology.discovery.cm.collector;

import org.apache.knox.gateway.topology.discovery.cm.ServiceURLCollector;
import org.apache.knox.gateway.topology.discovery.cm.model.hue.HueServiceModelGenerator;

import java.util.HashMap;
import java.util.Map;

/**
 * Mapping of service names to ServiceURLCollector instances.
 */
public class ServiceURLCollectors {

  private static final String DEFAULT = "DEFAULT";

  private static final Map<String, ServiceURLCollector> collectors = new HashMap<>();
  static {
    collectors.put(DEFAULT, new DefaultURLCollector());
    collectors.put(HueServiceModelGenerator.SERVICE, new HueURLCollector());
  }

  /**
   * Get the ServiceURLCollector for the specified service name.
   *
   * @param serviceName The name of the service
   *
   * @return The ServiceURLCollector instance associated with the service name.
   */
  public static ServiceURLCollector getCollector(final String serviceName) {
     ServiceURLCollector collector = collectors.get(serviceName);
     if (collector == null) {
        collector = collectors.get(DEFAULT);
     }
     return collector;
  }

}
