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

import static java.lang.String.format;
import static java.util.Locale.ROOT;

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.http.HttpServletRequest;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.knox.gateway.service.definition.Metadata;
import org.apache.knox.gateway.service.definition.Sample;
import org.apache.knox.gateway.topology.Service;

@XmlRootElement(name = "service")
@XmlAccessorType(XmlAccessType.NONE)
public class ServiceModel implements Comparable<ServiceModel> {

  static final String SERVICE_URL_TEMPLATE = "%s://%s:%s/%s/%s%s";
  static final String CURL_SAMPLE_TEMPLATE = "curl -iv -X %s \"%s%s\"";
  static final String HIVE_SERVICE_NAME = "HIVE";
  static final String HIVE_SERVICE_URL_TEMPLATE = "jdbc:hive2://%s:%d/;ssl=true;transportMode=http;httpPath=%s/%s/hive";
  static final String IMPALA_SERVICE_NAME = "IMPALA";
  static final String IMPALA_SERVICE_URL_TEMPLATE = "jdbc:impala://%s:%d/;ssl=1;transportMode=http;httpPath=%s/%s/impala;AuthMech=3";

  public enum Type {
    API, UI, API_AND_UI, UNKNOWN
  };

  private HttpServletRequest request;
  private String topologyName;
  private String gatewayPath;
  private Service service;
  private Metadata serviceMetadata;

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
    return serviceMetadata == null ? ("/" + getServiceName().toLowerCase(Locale.ROOT)) : serviceMetadata.getContext();
  }

  @XmlElement(name = "serviceUrls")
  public List<String> getServiceUrls() {
    final Set<String> resolvedServiceUrls = new TreeSet<>();
    final String context = getContext();

    if (HIVE_SERVICE_NAME.equals(getServiceName())) {
      resolvedServiceUrls.add(format(ROOT, HIVE_SERVICE_URL_TEMPLATE, request.getServerName(), request.getServerPort(), gatewayPath, topologyName));
    } else if (IMPALA_SERVICE_NAME.equals(getServiceName())) {
      resolvedServiceUrls.add(format(ROOT, IMPALA_SERVICE_URL_TEMPLATE, request.getServerName(), request.getServerPort(), gatewayPath, topologyName));
    } else {
      if (service != null && service.getUrls() != null && !service.getUrls().isEmpty()) {
        this.service.getUrls().forEach(serviceUrl -> {
          resolvedServiceUrls.add(getServiceUrl(context, serviceUrl));
        });
      } else {
        // fall back to the service URL fetched from the 'service' instance, if any
        resolvedServiceUrls.add(getServiceUrl(context, null));
      }
    }
    return Arrays.asList(resolvedServiceUrls.toArray(new String[0]));
  }

  private String getServiceUrl(String context, String serviceUrl) {
    final String resolvedContext = resolvePlaceholdersFromBackendUrl(context, serviceUrl);
    return String.format(Locale.ROOT, SERVICE_URL_TEMPLATE, request.getScheme(), request.getServerName(), request.getServerPort(), gatewayPath, topologyName, resolvedContext);
  }

  private String resolvePlaceholdersFromBackendUrl(String resolveable, String serviceUrl) {
    String toBeResolved = resolveable;
    if (toBeResolved != null) {
      final String backendUrlString = getBackendServiceUrl(serviceUrl);

      if (StringUtils.isNotBlank(backendUrlString)) {
        if (toBeResolved.indexOf("{{BACKEND_HOST}}") > -1) {
          toBeResolved = toBeResolved.replace("{{BACKEND_HOST}}", backendUrlString);
        }

        if (toBeResolved.indexOf("{{SCHEME}}") > -1 || toBeResolved.indexOf("{{HOST}}") > -1 || toBeResolved.indexOf("{{PORT}}") > -1) {
          try {
            final URL backendUrl = new URL(backendUrlString);
            toBeResolved = toBeResolved.replace("{{SCHEME}}", backendUrl.getProtocol());
            toBeResolved = toBeResolved.replace("{{HOST}}", backendUrl.getHost());
            toBeResolved = toBeResolved.replace("{{PORT}}", String.valueOf(backendUrl.getPort()));
          } catch (MalformedURLException e) {
            throw new UncheckedIOException("Error while converting '" + backendUrlString + "' to a URL", e);
          }
        }
      }
    }

    return toBeResolved;
  }

  String getBackendServiceUrl(String serviceUrl) {
    final String backendServiceUrl = serviceUrl == null ? (service == null ? "" : service.getUrl()) : serviceUrl;
    return backendServiceUrl == null ? "" : backendServiceUrl;
  }

  @XmlElement(name = "sample")
  @XmlElementWrapper(name = "samples")
  public List<Sample> getSamples() {
    final List<Sample> samples = new ArrayList<>();
    if (serviceMetadata != null && serviceMetadata.getSamples() != null) {
      serviceMetadata.getSamples().forEach(sample -> {
        final Sample resolvedSample = new Sample();
        resolvedSample.setDescription(sample.getDescription());
        if (StringUtils.isNotBlank(sample.getValue())) {
          resolvedSample.setValue(sample.getValue());
        } else {
          final String method = StringUtils.isBlank(sample.getMethod()) ? "GET" : sample.getMethod();
          final String path = sample.getPath().startsWith("/") ? sample.getPath() : ("/" + sample.getPath());
          final String serviceUrl = getServiceUrls().isEmpty() ? (service == null ? "$SERVICE_URL" : service.getUrl())
              : getServiceUrls().stream().findFirst().get();
          resolvedSample.setValue(String.format(Locale.ROOT, CURL_SAMPLE_TEMPLATE, method, serviceUrl, path));
        }
        samples.add(resolvedSample);
      });
    }
    return samples;
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
        .append(getVersion(), serviceModel.getVersion()).append(serviceMetadata, serviceModel.serviceMetadata).append(getServiceUrls(), serviceModel.getServiceUrls()).isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(topologyName).append(gatewayPath).append(getServiceName()).append(getVersion()).append(serviceMetadata).append(getServiceUrls())
        .toHashCode();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).append(topologyName).append(gatewayPath).append(getServiceName()).append(getVersion())
        .append(serviceMetadata).append(getServiceUrls()).toString();
  }

  @Override
  public int compareTo(ServiceModel other) {
    final int byServiceName = getServiceName().compareTo(other.getServiceName());
    if (byServiceName == 0) {
      final int byVersion = getVersion().compareTo(getVersion());
      return byVersion == 0 ? Integer.compare(getServiceUrls().size(), other.getServiceUrls().size()) : byVersion;
    }
    return byServiceName;
  }

}
