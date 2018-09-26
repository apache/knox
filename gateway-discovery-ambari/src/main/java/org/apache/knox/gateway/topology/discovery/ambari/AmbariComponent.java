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
package org.apache.knox.gateway.topology.discovery.ambari;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class AmbariComponent {

    private String clusterName = null;
    private String serviceName = null;
    private String name        = null;
    private String version     = null;

    private List<String> hostNames = new ArrayList<>();

    private Map<String, String> properties = null;

    AmbariComponent(String              name,
                    String              version,
                    String              cluster,
                    String              service,
                    List<String>        hostNames,
                    Map<String, String> properties) {
        this.name = name;
        this.serviceName = service;
        this.clusterName = cluster;
        this.version = version;
        this.properties = properties;

        if (hostNames != null) {
            // Add the hostnames individually to prevent adding any null values
            for (String hostName : hostNames) {
                if (hostName != null) {
                    this.hostNames.add(hostName);
                }
            }
        }
    }

    String getVersion() {
        return version;
    }

    String getName() {
        return name;
    }

    String getServiceName() {
        return serviceName;
    }

    String getClusterName() {
        return clusterName;
    }

    List<String> getHostNames() {
        return hostNames;
    }

    Map<String, String> getConfigProperties() {
        return properties;
    }

    String getConfigProperty(String propertyName) {
        return properties.get(propertyName);
    }

}
