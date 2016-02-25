/**
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
package org.apache.hadoop.gateway.topology;

import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.collections.map.MultiKeyMap;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Topology {

  private URI uri;
  private String name;
  private long timestamp;
  public List<Provider> providerList = new ArrayList<Provider>();
  private Map<String,Map<String,Provider>> providerMap = new HashMap<String,Map<String,Provider>>();
  public List<Service> services = new ArrayList<Service>();
  private MultiKeyMap serviceMap;
  private List<Application> applications = new ArrayList<Application>();
  private Map<String,Application> applicationMap = new HashMap<String,Application>();

  public Topology() {
    serviceMap = MultiKeyMap.decorate(new HashedMap());
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

  public void setTimestamp( long timestamp ) {
    this.timestamp = timestamp;
  }

  public Collection<Service> getServices() {
    return services;
  }

  public Service getService( String role, String name, Version version) {
    return (Service)serviceMap.get(role, name, version);
  }

  public void addService( Service service ) {
    services.add( service );
    serviceMap.put(service.getRole(), service.getName(), service.getVersion(), service);
  }

  public Collection<Application> getApplications() {
    return applications;
  }

  private static String fixApplicationUrl( String url ) {
    if( url == null ) {
      url = "/";
    }
    if( !url.startsWith( "/" ) ) {
      url = "/" + url;
    }
    return url;
  }

  public Application getApplication(String url) {
    return applicationMap.get( fixApplicationUrl( url ) );
  }

  public void addApplication( Application application ) {
    applications.add( application );
    List<String> urls = application.getUrls();
    if( urls == null || urls.isEmpty() ) {
      applicationMap.put( fixApplicationUrl( application.getName() ), application );
    } else {
      for( String url : application.getUrls() ) {
        applicationMap.put( fixApplicationUrl( url ), application );
      }
    }
  }

  public Collection<Provider> getProviders() {
    return providerList;
  }

  public Provider getProvider( String role, String name ) {
    Provider provider = null;
    Map<String,Provider> nameMap = providerMap.get( role );
    if( nameMap != null) { 
      if( name != null ) {
        provider = nameMap.get( name );
      }
      else {
        provider = (Provider) nameMap.values().toArray()[0];
      }
    }
    return provider;
  }

  public void addProvider( Provider provider ) {
    providerList.add( provider );
    String role = provider.getRole();
    Map<String,Provider> nameMap = providerMap.get( role );
    if( nameMap == null ) {
      nameMap = new HashMap<String,Provider>();
      providerMap.put( role, nameMap );
    }
    nameMap.put( provider.getName(), provider );
  }

}
