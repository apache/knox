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
package org.apache.knox.gateway.topology.discovery.cm;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model of a deployed service configuration and metadata for the configuration properties used to create the model
 * object.
 */
public class ServiceModel {

  public enum Type {API, UI}

  private static final String NULL_VALUE = "null";

  private final Type type;
  private final String service;
  private final String serviceType;
  private final String roleType;
  private final String serviceUrl;

  // Metadata for the model object, which is not directly from the service or role configuration
  private final Map<String, String> qualifyingServiceParams = new ConcurrentHashMap<>();

  // The service configuration properties used to created the model
  private final Map<String, String> serviceConfigProperties = new ConcurrentHashMap<>();

  // The role configuration properties used to created the model
  private final Map<String, Map<String, String>> roleConfigProperties = new ConcurrentHashMap<>();

  /**
   * @param type        The model type
   * @param service     The service name
   * @param serviceType The service type
   * @param roleType    The service role type
   * @param serviceUrl  The service URL
   */
  public ServiceModel(final Type   type,
                      final String service,
                      final String serviceType,
                      final String roleType,
                      final String serviceUrl) {
    this.type        = type;
    this.service     = service;
    this.serviceType = serviceType;
    this.roleType    = roleType;
    this.serviceUrl  = serviceUrl;
  }

  public void addQualifyingServiceParam(final String name, final String value) {
    qualifyingServiceParams.put(name, (value != null ? value : NULL_VALUE));
  }

  public void addServiceProperty(final String name, final String value) {
    serviceConfigProperties.put(name, (value != null ? value : NULL_VALUE));
  }

  public void addRoleProperty(final String role, final String name, final String value) {
    roleConfigProperties.computeIfAbsent(role, m -> new HashMap<>()).put(name, (value != null ? value : NULL_VALUE));
  }

  /**
   * @return The metadata properties associated with the model, which can be used to qualify service discovery.
   */
  public Map<String, String> getQualifyingServiceParams() {
    return qualifyingServiceParams;
  }

  /**
   * @return The value of the metadata property associated with the model, which can be used to qualify service discovery.
   */
  public String getQualifyingServiceParam(final String name) {
    return qualifyingServiceParams.get(name);
  }

  /**
   * @return The service configuration properties employed by the model.
   */
  public Map<String, String> getServiceProperties() {
    return serviceConfigProperties;
  }

  /**
   * @return The role configuration properties employed by the model.
   */
  public Map<String, Map<String, String>> getRoleProperties() {
    return roleConfigProperties;
  }

  /**
   * @return The model type
   */
  public Type getType() {
    return type;
  }

  /**
   * @return The name of the modeled service
   */
  public String getService() {
    return service;
  }

  /**
   * @return The type of the modeled service
   */
  public String getServiceType() {
    return serviceType;
  }

  /**
   * @return The role type of the modeled service
   */
  public String getRoleType() {
    return roleType;
  }

  /**
   * @return The URL of the modeled service
   */
  public String getServiceUrl() {
    return serviceUrl;
  }

  @Override
  public String toString() {
    return getService() + '-' + getServiceType() + '-' + getRoleType() + '-' + getServiceUrl();
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, service, serviceType, roleType, serviceUrl);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ServiceModel other = (ServiceModel) obj;
    return getType().equals(other.getType()) &&
           getService().equals(other.getService()) &&
           getServiceType().equals(other.getServiceType()) &&
           getRoleType().equals(other.getRoleType()) &&
           getServiceUrl().equals(other.getServiceUrl());
  }

}
