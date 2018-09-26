/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.  The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.hadoop.gateway.topology;

import org.apache.knox.gateway.topology.Application;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Version;

import java.net.URI;
import java.util.Collection;

@Deprecated
public class Topology extends org.apache.knox.gateway.topology.Topology {

  public Topology() {
    super();
  }

  @Override
  public URI getUri() {
    return super.getUri();
  }

  @Override
  public void setUri(URI uri) {
    super.setUri(uri);
  }

  @Override
  public String getName() {
    return super.getName();
  }

  @Override
  public void setName(String name) {
    super.setName(name);
  }

  @Override
  public long getTimestamp() {
    return super.getTimestamp();
  }

  @Override
  public void setTimestamp(long timestamp) {
    super.setTimestamp(timestamp);
  }

  @Override
  public String getDefaultServicePath() {
    return super.getDefaultServicePath();
  }

  @Override
  public void setDefaultServicePath(String servicePath) {
    super.setDefaultServicePath(servicePath);
  }

  @Override
  public void setGenerated(boolean isGenerated) {
    super.setGenerated(isGenerated);
  }

  @Override
  public boolean isGenerated() {
    return super.isGenerated();
  }

  @Override
  public Collection<Service> getServices() {
    return super.getServices();
  }

  @Override
  public Service getService(String role, String name, Version version) {
    return super.getService(role, name, version);
  }

  @Override
  public void addService(Service service) {
    super.addService(service);
  }

  @Override
  public Collection<Application> getApplications() {
    return super.getApplications();
  }

  @Override
  public Application getApplication(String url) {
    return super.getApplication(url);
  }

  @Override
  public void addApplication(Application application) {
    super.addApplication(application);
  }

  @Override
  public Collection<Provider> getProviders() {
    return super.getProviders();
  }

  @Override
  public Provider getProvider(String role, String name) {
    return super.getProvider(role, name);
  }

  @Override
  public void addProvider(Provider provider) {
    super.addProvider(provider);
  }
}
