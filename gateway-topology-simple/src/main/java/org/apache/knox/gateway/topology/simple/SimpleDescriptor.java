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
package org.apache.knox.gateway.topology.simple;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;


public interface SimpleDescriptor {

    String DISCOVERY_PARAM_PREFIX = "discovery-";

    String getName();

    String getDiscoveryType();

    String getDiscoveryAddress();

    String getDiscoveryUser();

    String getDiscoveryPasswordAlias();

    String getCluster();

    String getProviderConfig();

    boolean isReadOnly();

    boolean isProvisionEncryptQueryStringCredential();

    List<Service> getServices();

    Service getService(String serviceName);

    List<Application> getApplications();

    Application getApplication(String applicationName);

    @JsonDeserialize(as = SimpleDescriptorImpl.ServiceImpl.class)
    interface Service {
        String getName();

        String getVersion();

        Map<String, String> getParams();

        List<String> getURLs();
    }

    @JsonDeserialize(as = SimpleDescriptorImpl.ApplicationImpl.class)
    interface Application {
        String getName();

        Map<String, String> getParams();

        List<String> getURLs();
    }
}
