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
package org.apache.knox.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@XmlRootElement(name = "topology")
@XmlAccessorType(XmlAccessType.FIELD)
public class Topology {

  @XmlElement(name = "provider")
  @XmlElementWrapper(name = "gateway")
  public List<Provider> providers;
  @XmlElement(name = "service")
  public List<Service> services;
  @XmlElement
  private URI uri;
  @XmlElement
  private String name;
  @XmlElement
  private String path;
  @XmlElement
  private long timestamp;
  @XmlElement(name = "generated")
  private boolean isGenerated;
  @XmlElement(name = "application")
  private List<Application> applications;

  public Topology() {
  }

  public URI getUri() {
    return uri;
  }

  public void setUri(URI uri) {
    this.uri = uri;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String defaultServicePath) {
    this.path = defaultServicePath;
  }

  public boolean isGenerated() {
    return isGenerated;
  }

  public void setGenerated(boolean isGenerated) {
    this.isGenerated = isGenerated;
  }

  public List<Service> getServices() {
    if (services == null) {
      services = new ArrayList<>();
    }
    return services;
  }

  public void setServices(List<Service> services) {
    this.services = services;
  }

  public List<Application> getApplications() {
    if (applications == null) {
      applications = new ArrayList<>();
    }
    return applications;
  }

  public void setApplications(List<Application> applications) {
    this.applications = applications;
  }

  public List<Provider> getProviders() {
    if (providers == null) {
      providers = new ArrayList<>();
    }
    return providers;
  }

  public void setProviders(List<Provider> providers) {
    this.providers = providers;
  }

  @XmlAccessorType(XmlAccessType.NONE)
  public static class Service {
    @XmlElement
    private String role;

    @XmlElement
    private String name;

    @JsonProperty("version")
    @XmlElement
    private String version;

    @XmlElement(name = "param")
    @JsonDeserialize(using = ParamDeserializer.class)
    private List<Param> params;

    @JsonProperty("urls")
    @XmlElement(name = "url")
    private List<String> urls;

    public Service() {
    }

    @JsonProperty("name")
    public String getRole() {
      return role;
    }

    public void setRole(String role) {
      this.role = role;
    }

    @JsonIgnore
    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getVersion() {
      return version;
    }

    public void setVersion(String version) {
      this.version = version;
    }

    public List<String> getUrls() {
      if (urls == null) {
        urls = new ArrayList<>();
      }
      return urls;
    }

    public void setUrls(List<String> urls) {
      this.urls = urls;
    }

    public List<Param> getParams() {
      if (params == null) {
        params = new ArrayList<>();
      }
      return params;
    }

    public void setParams(List<Param> params) {
      this.params = params;
    }

    @JsonProperty("params")
    public Map<String, String> getJsonParams() {
      Map<String, String> result = new LinkedHashMap<>();
      this.getParams().forEach(p -> result.put(p.getName(), p.getValue()));
      return result;
    }
  }

  @XmlAccessorType(XmlAccessType.NONE)
  @JsonPropertyOrder({ "role", "name", "enabled", "param" })
  public static class Provider {

    private static Map<String, Integer> SHIRO_PROVIDER_PARAM_ORDER = new HashMap<>();

    static {
      SHIRO_PROVIDER_PARAM_ORDER.put("sessionTimeout", 0);
      SHIRO_PROVIDER_PARAM_ORDER.put("main.ldapRealm", 1);
      SHIRO_PROVIDER_PARAM_ORDER.put("main.ldapContextFactory", 2);
      SHIRO_PROVIDER_PARAM_ORDER.put("main.ldapGroupContextFactory", 3);
      SHIRO_PROVIDER_PARAM_ORDER.put("main.ldapRealm.contextFactory", 4);
      SHIRO_PROVIDER_PARAM_ORDER.put("main.ldapRealm.userDnTemplate", 5);
      SHIRO_PROVIDER_PARAM_ORDER.put("main.ldapRealm.contextFactory.url", 6);
      SHIRO_PROVIDER_PARAM_ORDER
          .put("main.ldapRealm.contextFactory.authenticationMechanism", 7);
      SHIRO_PROVIDER_PARAM_ORDER.put("urls./**", 8);
    }

    @JsonProperty("role")
    @XmlElement
    private String role;
    @JsonProperty("name")
    @XmlElement
    private String name;
    @XmlElement
    private boolean enabled;
    @XmlElement(name = "param")
    @JsonDeserialize(using = ParamDeserializer.class)
    private List<Param> params;

    public Provider() {
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    @JsonProperty("enabled")
    public String isEnabled() {
      return Boolean.toString(enabled);
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getRole() {
      return role;
    }

    public void setRole(String role) {
      this.role = role;
    }

    public List<Param> getParams() {
      /* for Shiro provider order is important */
      if ("ShiroProvider".equalsIgnoreCase(name) && params != null && !params.isEmpty()) {
        params.sort(Comparator.comparing((Param p) -> SHIRO_PROVIDER_PARAM_ORDER
            .getOrDefault(p.getName(), Integer.MAX_VALUE)).thenComparing(
            (Param p) -> SHIRO_PROVIDER_PARAM_ORDER.getOrDefault(p.getName(), Integer.MAX_VALUE)));
      }
      if (params == null) {
        params = new ArrayList<>();
      }
      return params;
    }

    public void setParams(List<Param> params) {
      this.params = params;
    }

    @JsonProperty("params")
    public Map<String, String> getJsonParams() {
      Map<String, String> result = new LinkedHashMap<>();
      this.getParams().forEach(p -> result.put(p.getName(), p.getValue()));
      return result;
    }
  }

  public static class Descriptor {
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
    private List<Service> services;

    @JsonProperty("applications")
    private List<Application> applications;

    private String name;

    public Descriptor() {
      super();
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getDiscoveryType() {
      return discoveryType;
    }

    public void setDiscoveryType(String discoveryType) {
      this.discoveryType = discoveryType;
    }

    public String getDiscoveryAddress() {
      return discoveryAddress;
    }

    public void setDiscoveryAddress(String discoveryAddress) {
      this.discoveryAddress = discoveryAddress;
    }

    public String getDiscoveryUser() {
      return discoveryUser;
    }

    public void setDiscoveryUser(String discoveryUser) {
      this.discoveryUser = discoveryUser;
    }

    public String getDiscoveryPasswordAlias() {
      return discoveryPasswordAlias;
    }

    public void setDiscoveryPasswordAlias(String discoveryPasswordAlias) {
      this.discoveryPasswordAlias = discoveryPasswordAlias;
    }

    public String getProviderConfig() {
      return providerConfig;
    }

    public void setProviderConfig(String providerConfig) {
      this.providerConfig = providerConfig;
    }

    public String getCluster() {
      return cluster;
    }

    public void setCluster(String cluster) {
      this.cluster = cluster;
    }

    public List<Service> getServices() {
      return services;
    }

    public void setServices(List<Service> services) {
      this.services = services;
    }

    public List<Application> getApplications() {
      return applications;
    }

    public void setApplications(List<Application> applications) {
      this.applications = applications;
    }
  }

  @XmlAccessorType(XmlAccessType.NONE)
  public static class Application extends Service {

    @Override
    public String getRole() {
      return getName();
    }

    @Override
    public void setRole(String role) {
      setName(role);
    }

  }

  @XmlAccessorType(XmlAccessType.NONE)
  public static class Param {

    @XmlElement
    private String name;
    @XmlElement
    private String value;

    public Param() {
    }

    public Param(String name, String value) {
      this.name = name;
      this.value = value;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }
  }

  /* custom deserializer for params field */
  public static class ParamDeserializer extends JsonDeserializer<List> {
    final ObjectMapper mapper = new ObjectMapper();
    @Override
    public List<Topology.Param> deserialize(final JsonParser jsonParser,
        final DeserializationContext deserializationContext)
        throws IOException {

      final JsonNode node = jsonParser.getCodec().readTree(jsonParser);
      final Map<String, String> result = mapper
          .convertValue(node, new TypeReference<Map<String, String>>() {
          });
      final List<Topology.Param> params = new ArrayList();
      result.forEach((k,v) -> params.add(new Param(k, v)));
      return params;
    }
  }

}

