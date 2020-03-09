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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.knox.gateway.service.definition.Metadata;
import org.apache.knox.gateway.topology.Service;

@XmlRootElement(name = "service")
@XmlAccessorType(XmlAccessType.NONE)
public class ServiceModel {

  public enum Type {
    API, UI, UNKNOWN
  };

  protected HttpServletRequest request;
  protected String topologyName;
  protected String gatewayPath;
  protected Service service;
  protected Metadata serviceMetadata;

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
  public String getServiceUrl() throws MalformedURLException {
    String context = getContext();
    if ("HIVE".equals(getServiceName())) {
      return String.format(Locale.ROOT, "jdbc:hive2://%s:%d/;?hive.server2.transport.mode=http;hive.server2.thrift.http.path=/%s/%s%s", request.getServerName(),
          request.getServerPort(), gatewayPath, topologyName, context);
    } else {
      final String backendUrlString = getBackendServiceUrl();
      if (context.indexOf("{{BACKEND_HOST}}") > -1) {
        context = context.replace("{{BACKEND_HOST}}", backendUrlString);
      }
      if (context.indexOf("{{SCHEME}}") > -1 || context.indexOf("{{HOST}}") > -1 || context.indexOf("{{PORT}}") > -1) {
        final URL backendUrl = new URL(backendUrlString);
          context = context.replace("{{SCHEME}}", backendUrl.getProtocol());
          context = context.replace("{{HOST}}", backendUrl.getHost());
          context = context.replace("{{PORT}}", String.valueOf(backendUrl.getPort()));
      }
      return String.format(Locale.ROOT, "%s://%s:%s/%s/%s%s", request.getScheme(), request.getServerName(), request.getServerPort(), gatewayPath, topologyName, context);
    }
  }

  protected String getBackendServiceUrl() {
    final String serviceUrl = service == null ? "" : service.getUrl();
    return serviceUrl == null ? "" : serviceUrl;
  }
}
