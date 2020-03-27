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

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonPropertyOrder({ "providers", "readOnly" })
public class JSONProviderConfiguration implements ProviderConfiguration {

  @JsonProperty("providers")
  @JsonSerialize(contentAs = JSONProvider.class)
  @JsonDeserialize(contentAs = JSONProvider.class)
  private Set<Provider> providers;

  @JsonProperty("readOnly")
  private boolean readOnly;

  @Override
  public Set<Provider> getProviders() {
    return providers == null ? Collections.emptySet() : Collections.unmodifiableSet(new TreeSet<>(providers));
  }

  @Override
  public void saveOrUpdateProviders(Set<Provider> providersToReplace) {
    if (this.providers == null) {
      this.providers = new TreeSet<>();
      this.providers.addAll(providersToReplace);
    } else {
      providersToReplace.forEach(providerToAdd -> {
        final Optional<Provider> toBeRemoved = this.providers.stream().filter(provider -> provider.getRole().equals(providerToAdd.getRole())).findFirst();
        if (toBeRemoved.isPresent()) {
          this.providers.remove(toBeRemoved.get());
        }
        this.providers.add(providerToAdd);
      });
    }
  }

  @Override
  public boolean isReadOnly() {
    return readOnly;
  }

  @Override
  public void setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
  }

  @Override
  public int hashCode() {
    return HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(Object obj) {
    return EqualsBuilder.reflectionEquals(this, obj);
  }

  @JsonPropertyOrder({ "role", "name", "enabled", "params" })
  public static class JSONProvider implements ProviderConfiguration.Provider, Comparable<JSONProvider> {

    @JsonProperty("role")
    private String role;

    @JsonProperty("name")
    private String name;

    @JsonProperty("enabled")
    private boolean enabled;

    @JsonProperty("params")
    private Map<String, String> params;

    @Override
    public String getRole() {
      return role;
    }

    public void setRole(String role) {
      this.role = role;
    }

    @Override
    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    @Override
    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    @Override
    public Map<String, String> getParams() {
      Map<String, String> result = new TreeMap<>();
      if (params != null) {
        result.putAll(params);
      }
      return result;
    }

    public void addParam(String key, String value) {
      if (params == null) {
        params = new TreeMap<>();
      }
      params.put(key, value);
    }

    public void removeParam(String key) {
      if (params != null) {
        params.remove(key);
      }
    }

    @Override
    public int hashCode() {
      return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
      return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int compareTo(JSONProvider other) {
      return Integer.compare(ProviderOrder.getOrdinalForRole(role), ProviderOrder.getOrdinalForRole(other.role));
    }
  }

}
