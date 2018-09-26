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

import org.apache.knox.gateway.services.Service;

import java.lang.reflect.Field;
import java.util.ServiceLoader;

/**
 * Creates instances of ServiceDiscovery implementations.
 *
 * This factory uses the ServiceLoader mechanism to load ServiceDiscovery implementations as extensions.
 *
 */
public abstract class ServiceDiscoveryFactory {

    private static final Service[] NO_GATEWAY_SERVICS = new Service[]{};


    public static ServiceDiscovery get(String type) {
        return get(type, NO_GATEWAY_SERVICS);
    }


    public static ServiceDiscovery get(String type, Service...gatewayServices) {
        ServiceDiscovery sd  = null;

        // Look up the available ServiceDiscovery types
        ServiceLoader<ServiceDiscoveryType> loader = ServiceLoader.load(ServiceDiscoveryType.class);
        for (ServiceDiscoveryType sdt : loader) {
            if (sdt.getType().equalsIgnoreCase(type)) {
                try {
                    ServiceDiscovery instance = sdt.newInstance();
                    // Make sure the type reported by the instance matches the type declared by the factory
                    // (is this necessary?)
                    if (instance.getType().equalsIgnoreCase(type)) {
                        sd = instance;

                        // Inject any gateway services that were specified, and which are referenced in the impl
                        if (gatewayServices != null && gatewayServices.length > 0) {
                            for (Field field : sd.getClass().getDeclaredFields()) {
                                if (field.getDeclaredAnnotation(GatewayService.class) != null) {
                                    for (Service s : gatewayServices) {
                                        if (s != null) {
                                            if (field.getType().isAssignableFrom(s.getClass())) {
                                                field.setAccessible(true);
                                                field.set(sd, s);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return sd;
    }


}
