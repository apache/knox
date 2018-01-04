/**
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
package org.apache.hadoop.gateway.topology.discovery.ambari;

import org.apache.hadoop.gateway.topology.discovery.ServiceDiscovery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class AmbariCluster implements ServiceDiscovery.Cluster {

    private String name = null;

    private ServiceURLFactory urlFactory;

    private Map<String, Map<String, ServiceConfiguration>> serviceConfigurations = new HashMap<>();

    private Map<String, AmbariComponent> components = null;


    AmbariCluster(String name) {
        this.name = name;
        components = new HashMap<>();
        urlFactory = ServiceURLFactory.newInstance(this);
    }

    void addServiceConfiguration(String serviceName, String configurationType, ServiceConfiguration serviceConfig) {
        if (!serviceConfigurations.keySet().contains(serviceName)) {
            serviceConfigurations.put(serviceName, new HashMap<>());
        }
        serviceConfigurations.get(serviceName).put(configurationType, serviceConfig);
    }


    void addComponent(AmbariComponent component) {
        components.put(component.getName(), component);
    }


    ServiceConfiguration getServiceConfiguration(String serviceName, String configurationType) {
        ServiceConfiguration sc = null;
        Map<String, ServiceConfiguration> configs = serviceConfigurations.get(serviceName);
        if (configs != null) {
            sc = configs.get(configurationType);
        }
        return sc;
    }


    Map<String, Map<String, ServiceConfiguration>> getServiceConfigurations() {
        return serviceConfigurations;
    }


    Map<String, AmbariComponent> getComponents() {
        return components;
    }


    AmbariComponent getComponent(String name) {
        return components.get(name);
    }


    @Override
    public String getName() {
        return name;
    }


    @Override
    public List<String> getServiceURLs(String serviceName) {
        List<String> urls = new ArrayList<>();
        urls.addAll(urlFactory.create(serviceName));
        return urls;
    }


    static class ServiceConfiguration {

        private String type;
        private String version;
        private Map<String, String> props;

        ServiceConfiguration(String type, String version, Map<String, String> properties) {
            this.type = type;
            this.version = version;
            this.props = properties;
        }

        public String getVersion() {
            return version;
        }

        public String getType() {
            return type;
        }

        public Map<String, String> getProperties() {
            return props;
        }
    }

}
