/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.service.admin.beans;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name="topology")
public class Topology {

  @XmlElement
  private URI uri;

  @XmlElement
  private String name;

  @XmlElement
  private String path;

  @XmlElement
  private long timestamp;

  @XmlElement(name="generated")
  private boolean isGenerated;

  @XmlElement(name="redeployTime")
  private long redeployTime;

  @XmlElement(name="provider")
  @XmlElementWrapper(name="gateway")
  public List<Provider> providers;

  @XmlElement(name="service")
  public List<Service> services;

  @XmlElement(name="application")
  private List<Application> applications;

  public Topology() {
  }

  public URI getUri() {
    return uri;
  }

  public void setUri( URI uri ) {
    this.uri = uri;
  }

  public String getName() {
    return name;
  }

  public void setName( String name ) {
    this.name = name;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setPath( String defaultServicePath ) {
    this.path = defaultServicePath;
  }

  public String getPath() {
    return path;
  }

  public void setTimestamp( long timestamp ) {
    this.timestamp = timestamp;
  }

  public boolean isGenerated() {
    return isGenerated;
  }

  public void setGenerated(boolean isGenerated) {
    this.isGenerated = isGenerated;
  }

  public long getRedeployTime() {
    return redeployTime;
  }

  public void setRedeployTime(long redeployTime) {
    this.redeployTime = redeployTime;
  }

  public List<Service> getServices() {
    if (services == null) {
      services = new ArrayList<>();
    }
    return services;
  }

  public List<Application> getApplications() {
    if (applications == null) {
      applications = new ArrayList<>();
    }
    return applications;
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

  public void setServices(List<Service> services) {
    this.services = services;
  }

  public void setApplications(List<Application> applications) {
    this.applications = applications;
  }
}
