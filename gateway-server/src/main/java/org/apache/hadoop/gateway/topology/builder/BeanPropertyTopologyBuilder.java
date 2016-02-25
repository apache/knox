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
package org.apache.hadoop.gateway.topology.builder;

import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.gateway.topology.Application;
import org.apache.hadoop.gateway.topology.Provider;
import org.apache.hadoop.gateway.topology.Service;
import org.apache.hadoop.gateway.topology.Topology;

public class BeanPropertyTopologyBuilder implements TopologyBuilder {

    private String name;
    private List<Provider> providers;
    private List<Service> services;
    private List<Application> applications;

    public BeanPropertyTopologyBuilder() {
        providers = new ArrayList<Provider>();
        services = new ArrayList<Service>();
        applications = new ArrayList<Application>();
    }

    public BeanPropertyTopologyBuilder name(String name) {
        this.name = name;
        return this;
    }

    public String name() {
        return name;
    }

    public BeanPropertyTopologyBuilder addProvider(Provider provider) {
        providers.add(provider);
        return this;
    }

    public List<Provider> providers() {
        return providers;
    }

    public BeanPropertyTopologyBuilder addService(Service service) {
        services.add(service);
        return this;
    }

    public List<Service> services() {
        return services;
    }

    public BeanPropertyTopologyBuilder addApplication( Application application ) {
        applications.add(application);
        return this;
    }

    public List<Application> applications() {
        return applications;
    }

    public Topology build() {
        Topology topology = new Topology();
        topology.setName(name);

          for (Provider provider : providers) {
            topology.addProvider(provider);
        }

        for (Service service : services) {
            topology.addService(service);
        }

        for (Application application : applications) {
            topology.addApplication(application);
        }

        return topology;
    }
}
