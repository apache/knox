/**
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
package org.apache.knox.gateway.topology.simple;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

class SimpleDescriptorImpl implements SimpleDescriptor {

    @JsonProperty("discovery-type")
    private String discoveryType;

    @JsonProperty("discovery-address")
    private String discoveryAddress;

    @JsonProperty("discovery-user")
    private String discoveryUser;

    @JsonProperty("discovery-pwd-alias")
    private String discoveryPasswordAlias;

    @JsonProperty("provider-config-ref")
    private String providerConfig;

    @JsonProperty("cluster")
    private String cluster;

    @JsonProperty("services")
    private List<ServiceImpl> services;

    private String name = null;

    void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDiscoveryType() {
        return discoveryType;
    }

    @Override
    public String getDiscoveryAddress() {
        return discoveryAddress;
    }

    @Override
    public String getDiscoveryUser() {
        return discoveryUser;
    }

    @Override
    public String getDiscoveryPasswordAlias() {
        return discoveryPasswordAlias;
    }

    @Override
    public String getClusterName() {
        return cluster;
    }

    @Override
    public String getProviderConfig() {
        return providerConfig;
    }

    @Override
    public List<Service> getServices() {
        List<Service> result = new ArrayList<>();
        result.addAll(services);
        return result;
    }

    public static class ServiceImpl implements Service {
        private String name;
        private List<String> urls;

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<String> getURLs() {
            return urls;
        }
    }

}
