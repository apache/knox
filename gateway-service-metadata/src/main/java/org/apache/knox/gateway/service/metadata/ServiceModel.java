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
package org.apache.knox.gateway.service.metadata;

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.knox.gateway.service.definition.Metadata;
import org.apache.knox.gateway.topology.Service;

@XmlRootElement(name = "service")
@XmlAccessorType(XmlAccessType.NONE)
public class ServiceModel implements Comparable<ServiceModel> {

  static final String SERVICE_URL_TEMPLATE = "%s://%s:%s/%s/%s%s";
  static final String HIVE_SERVICE_NAME = "HIVE";
  static final String HIVE_SERVICE_URL_TEMPLATE = "jdbc:hive2://%s:%d/;ssl=true;transportMode=http;httpPath=%s/%s/hive";

  public enum Type {
    API, UI, API_AND_UI, UNKNOWN
  };

  private HttpServletRequest request;
  private String topologyName;
  private String gatewayPath;
  private Service service;
  private Metadata serviceMetadata;
  private String serviceUrl;

  public void setRequest(HttpServletRequest request) {
    this.request = request;
  }

  public void setTopologyName(String topologyName) {
    this.topologyName = topologyName;
  }

  public void setGatewayPath(String gatewayPath) {
    this.gatewayPath = gatewayPath;
  }

  public void setService(Service service) {
    this.service = service;
  }

  public void setServiceMetadata(Metadata serviceMetadata) {
    this.serviceMetadata = serviceMetadata;
  }

  public void setServiceUrl(String serviceUrl) {
    this.serviceUrl = serviceUrl;
  }

  @XmlElement
  public String getServiceName() {
    return this.service == null ? "" : service.getRole();
  }

  @XmlElement
  public String getVersion() {
    return this.service == null ? "" : this.service.getVersion() == null ? "" : this.service.getVersion().toString();
  }

  @XmlElement(name = "shortDesc")
  public String getShortDescription() {
    if (serviceMetadata == null) {
      return getServiceName().substring(0, 1).toUpperCase(Locale.ROOT) + getServiceName().substring(1).toLowerCase(Locale.ROOT);
    } else {
      return serviceMetadata.getShortDesc();
    }
  }

  @XmlElement
  public String getDescription() {
    if (serviceMetadata == null) {
      return getShortDescription() + (Type.API == getType() ? " REST API" : Type.UI == getType() ? " Web User Interface" : "");
    } else {
      return serviceMetadata.getDescription();
    }
  }

  @XmlElement
  public Type getType() {
    return serviceMetadata == null ? Type.UNKNOWN : Type.valueOf(serviceMetadata.getType());
  }

  @XmlElement
  public String getContext() {
    return (serviceMetadata == null ? "/" + getServiceName().toLowerCase(Locale.ROOT) : serviceMetadata.getContext()) + "/";
  }

  @XmlElement
  public String getServiceUrl() {
    String context = getContext();
    if (HIVE_SERVICE_NAME.equals(getServiceName())) {
      return String.format(Locale.ROOT, HIVE_SERVICE_URL_TEMPLATE, request.getServerName(), request.getServerPort(), gatewayPath, topologyName);
    } else {
      final String backendUrlString = getBackendServiceUrl();
      if (context.indexOf("{{BACKEND_HOST}}") > -1) {
        context = context.replace("{{BACKEND_HOST}}", backendUrlString);
      }
      if (context.indexOf("{{SCHEME}}") > -1 || context.indexOf("{{HOST}}") > -1 || context.indexOf("{{PORT}}") > -1) {
        try {
          final URL backendUrl = new URL(backendUrlString);
          context = context.replace("{{SCHEME}}", backendUrl.getProtocol());
          context = context.replace("{{HOST}}", backendUrl.getHost());
          context = context.replace("{{PORT}}", String.valueOf(backendUrl.getPort()));
        } catch (MalformedURLException e) {
          throw new UncheckedIOException("Error while converting " + backendUrlString + " to a URL", e);
        }
      }
      return String.format(Locale.ROOT, SERVICE_URL_TEMPLATE, request.getScheme(), request.getServerName(), request.getServerPort(), gatewayPath, topologyName, context);
    }
  }

  String getBackendServiceUrl() {
    final String backendServiceUrl = serviceUrl == null ? (service == null ? "" : service.getUrl()) : serviceUrl;
    return backendServiceUrl == null ? "" : backendServiceUrl;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || (obj.getClass() != getClass())) {
      return false;
    }
    final ServiceModel serviceModel = (ServiceModel) obj;
    return new EqualsBuilder().append(topologyName, serviceModel.topologyName).append(gatewayPath, serviceModel.gatewayPath).append(getServiceName(), serviceModel.getServiceName())
        .append(getVersion(), serviceModel.getVersion()).append(serviceMetadata, serviceModel.serviceMetadata).append(getServiceUrl(), serviceModel.getServiceUrl()).isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(topologyName).append(gatewayPath).append(getServiceName()).append(getVersion()).append(serviceMetadata).append(getServiceUrl())
        .toHashCode();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).append(topologyName).append(gatewayPath).append(getServiceName()).append(getVersion())
        .append(serviceMetadata).append(getServiceUrl()).toString();
  }

  @Override
  public int compareTo(ServiceModel other) {
    final int byServiceName = getServiceName().compareTo(other.getServiceName());
    if (byServiceName == 0) {
      final int byVersion = getVersion().compareTo(getVersion());
      return byVersion == 0 ? getBackendServiceUrl().compareTo(other.getBackendServiceUrl()) : byVersion;
    }
    return byServiceName;
  }

}
