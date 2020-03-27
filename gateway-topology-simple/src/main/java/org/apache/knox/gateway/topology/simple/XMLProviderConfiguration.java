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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

@XmlRootElement(name = "gateway")
@XmlAccessorType(XmlAccessType.FIELD)
class XMLProviderConfiguration implements ProviderConfiguration {

  @XmlElement(name = "provider", type=XMLProvider.class)
  private Set<Provider> providers;

  @XmlElement(name = "readOnly")
  private boolean readOnly;

  @Override
  public Set<Provider> getProviders() {
    return Collections.unmodifiableSet(providers);
  }

  @Override
  public void saveOrUpdateProviders(Set<Provider> providersToReplace) {
    if (providers == null) {
      providers = new TreeSet<>();
      this.providers.addAll(providersToReplace);
    } else {
      providersToReplace.forEach(providerToAdd -> {
        final Optional<Provider> toBeRemoved = providers.stream().filter(provider -> provider.getRole().equals(providerToAdd.getRole())).findFirst();
        if (toBeRemoved.isPresent()) {
          providers.remove(toBeRemoved.get());
        }
        providers.add(providerToAdd);
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

  @XmlAccessorType(XmlAccessType.NONE)
  @XmlType(propOrder = { "role", "name", "enabled", "params" })
  private static class XMLProvider implements ProviderConfiguration.Provider, Comparable<XMLProvider> {
    @XmlElement
    private String role;

    @XmlElement
    private String name;

    @XmlElement
    private boolean enabled;

    @XmlElement(name = "param")
    private List<XMLParam> params;

    @Override
    public String getRole() {
      return role;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public boolean isEnabled() {
      return enabled;
    }

    @Override
    public Map<String, String> getParams() {
      Map<String, String> result = new TreeMap<>();
      if (params != null) {
        for (XMLParam p : params) {
          result.put(p.name, p.value);
        }
      }
      return result;
    }

    @Override
    public int hashCode() {
      return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
      return EqualsBuilder.reflectionEquals(this, obj);
    }

    @XmlAccessorType(XmlAccessType.NONE)
    private static class XMLParam {
      @XmlElement
      private String name;

      @XmlElement
      private String value;
    }

    @Override
    public int compareTo(XMLProvider other) {
      return Integer.compare(ProviderOrder.getOrdinalForRole(role), ProviderOrder.getOrdinalForRole(other.role));
    }
  }
}
