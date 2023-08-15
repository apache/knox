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
package org.apache.knox.gateway.topology.simple;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SimpleDescriptorImpl implements SimpleDescriptor {

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

    @JsonProperty("read-only")
    private boolean readOnly;

    @JsonProperty("cluster")
    private String cluster;

    @JsonProperty("provision-encrypt-query-string-credential")
    private boolean provisionEncryptQueryStringCredential = true;

    @JsonProperty("services")
    private List<Service> services;

    @JsonProperty("applications")
    private List<Application> applications;

    private String name;

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setDiscoveryType(String discoveryType) {
      this.discoveryType = discoveryType;
    }

    @Override
    public String getDiscoveryType() {
        return discoveryType;
    }

    public void setDiscoveryAddress(String discoveryAddress) {
      this.discoveryAddress = discoveryAddress;
    }

    @Override
    public String getDiscoveryAddress() {
        return discoveryAddress;
    }

    public void setDiscoveryUser(String discoveryUser) {
      this.discoveryUser = discoveryUser;
    }

    @Override
    public String getDiscoveryUser() {
        return discoveryUser;
    }

    public void setDiscoveryPasswordAlias(String discoveryPasswordAlias) {
      this.discoveryPasswordAlias = discoveryPasswordAlias;
    }

    @Override
    public String getDiscoveryPasswordAlias() {
        return discoveryPasswordAlias;
    }

    public void setCluster(String cluster) {
      this.cluster = cluster;
    }

    @Override
    public String getCluster() {
        return cluster;
    }

    public void setProviderConfig(String providerConfig) {
      this.providerConfig = providerConfig;
    }

    @Override
    public String getProviderConfig() {
        return providerConfig;
    }

    @Override
    public boolean isReadOnly() {
      return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
      this.readOnly = readOnly;
    }

    @Override
    public boolean isProvisionEncryptQueryStringCredential() {
      return provisionEncryptQueryStringCredential;
    }

    public void setProvisionEncryptQueryStringCredential(boolean provisionEncryptQueryStringCredential) {
      this.provisionEncryptQueryStringCredential = provisionEncryptQueryStringCredential;
    }

    public void addService(Service service) {
      if (services == null) {
        services = new ArrayList<>();
      }
      services.add(service);
    }

    public void setServices(Collection<Service> services) {
      this.services = new ArrayList<>(services);
    }

    @Override
    public List<Service> getServices() {
        List<Service> result = new ArrayList<>();
        if (services != null) {
            result.addAll(services);
        }
        return result;
    }

    @Override
    public Service getService(String serviceName) {
      return getServices().stream().filter(service -> service.getName().equals(serviceName)).findFirst().orElse(null);
    }

    public void addApplication(Application application) {
      if (applications == null) {
        applications = new ArrayList<>();
      }
      applications.add(application);
    }

    @Override
    public List<Application> getApplications() {
        List<Application> result = new ArrayList<>();
        if (applications != null) {
            result.addAll(applications);
        }
        return result;
    }

    @Override
    public Application getApplication(String applicationName) {
      return getApplications().stream().filter(application -> application.getName().equals(applicationName)).findFirst().orElse(null);
    }

    public static class ServiceImpl implements Service {
        @JsonProperty("name")
        private String name;

        @JsonProperty("version")
        private String version;

        @JsonProperty("params")
        private Map<String, String> params;

        @JsonProperty("urls")
        private List<String> urls;

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public Map<String, String> getParams() {
            return params;
        }

        @Override
        public List<String> getURLs() {
            return urls;
        }

        public void addUrl(String url) {
          if (this.urls == null) {
            this.urls = new ArrayList<>();
          }
          this.urls.add(url);
        }

        public void setName(String name) {
          this.name = name;
        }

        public void setVersion(String version) {
          this.version = version;
        }

        public void addParam(String name, String value) {
          if (this.params == null) {
            this.params = new TreeMap<>();
          }
          this.params.put(name, value);
        }
    }

    public static class ApplicationImpl implements Application {
        @JsonProperty("name")
        private String name;

        @JsonProperty("params")
        private Map<String, String> params;

        @JsonProperty("urls")
        private List<String> urls;

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Map<String, String> getParams() {
            return params;
        }

        @Override
        public List<String> getURLs() {
            return urls;
        }

        public void addUrl(String url) {
          if (this.urls == null) {
            this.urls = new ArrayList<>();
          }
          this.urls.add(url);
        }

        public void setName(String name) {
          this.name = name;
        }

        public void addParam(String name, String value) {
          if (this.params == null) {
            this.params = new TreeMap<>();
          }
          this.params.put(name, value);
        }
    }

}
