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
package org.apache.knox.gateway.topology;

import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.collections.map.MultiKeyMap;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class Topology {

  private URI uri;
  private String name;
  private String defaultServicePath;
  private long timestamp;
  private boolean isGenerated;
  private long redeployTime = -1L;
  public List<Provider> providerList = new ArrayList<>();
  private Map<String,Map<String,Provider>> providerMap = new HashMap<>();
  public List<Service> services = new ArrayList<>();
  private MultiKeyMap serviceMap;
  List<Application> applications = new ArrayList<>();
  private Map<String,Application> applicationMap = new HashMap<>();

  private ProviderComparator providerComparator = new ProviderComparator();
  private ServiceComparator serviceComparator = new ServiceComparator();
  private ApplicationComparator appComparator = new ApplicationComparator();

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

  public String getDefaultServicePath() {
    return defaultServicePath;
  }

  public void setDefaultServicePath(String servicePath) {
    defaultServicePath = servicePath;
  }

  public void setGenerated(boolean isGenerated) {
    this.isGenerated = isGenerated;
  }

  public boolean isGenerated() {
    return isGenerated;
  }

  public long getRedeployTime() {
    return redeployTime;
  }

  public void setRedeployTime(long redeployTime) {
    this.redeployTime = redeployTime;
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
    Map<String, Provider> nameMap = providerMap.computeIfAbsent(role, k -> new HashMap<>());
    nameMap.put( provider.getName(), provider );
  }

  @Override
  public int hashCode() {
    return (new HashCodeBuilder(17, 31)).append(name)
                                .append(providerList.stream().sorted(providerComparator).collect(Collectors.toList()))
                                .append(services.stream().sorted(serviceComparator).collect(Collectors.toList()))
                                .append(applications.stream().sorted(appComparator).collect(Collectors.toList()))
                                .append(getRedeployTime())
                                .build();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    // Has to be a Topology
    if (!(obj instanceof Topology)) {
      return false;
    }

    Topology other = (Topology) obj;
    if (Objects.equals(this.name, other.name)) {
      // Order is NOT significant for providers, services, and applications

      if (equalProviders(other)) {  // Providers
        if (equalServices(other)) { // Services
          if (equalApplications(other)) { // Applications
            if (getRedeployTime() == other.getRedeployTime()) {
              return true;
            }
          }
        }
      }
    }

    return false;
  }

  private boolean equalProviders(Topology other) {
    if (providerList != null) {
      if (other.providerList == null) {
        return false;
      }

      List<Provider> mySorted = providerList.stream().sorted(providerComparator).collect(Collectors.toList());
      List<Provider> otherSorted = other.providerList.stream().sorted(providerComparator).collect(Collectors.toList());
      return mySorted.equals(otherSorted);
    } else {
      return (other.providerList == null);
    }
  }

  private boolean equalServices(Topology other) {
    if (services != null) {
      if (other.services == null) {
        return false;
      }

      List<Service> mySorted = services.stream().sorted(serviceComparator).collect(Collectors.toList());
      List<Service> otherSorted = other.services.stream().sorted(serviceComparator).collect(Collectors.toList());
      return mySorted.equals(otherSorted);
    } else {
      return (other.services == null);
    }
  }

  private boolean equalApplications(Topology other) {
    if (applications != null) {
      if (other.applications == null) {
        return false;
      }

      List<Application> mySorted = applications.stream().sorted(appComparator).collect(Collectors.toList());
      List<Application> otherSorted = other.applications.stream().sorted(appComparator).collect(Collectors.toList());
      return mySorted.equals(otherSorted);
    } else {
      return (other.applications == null);
    }
  }

  private static final class ProviderComparator implements Comparator<Provider> {
    @Override
    public int compare(Provider o1, Provider o2) {
      String name1 = o1.getName();
      return name1 != null ? name1.compareToIgnoreCase(o2.getName()) : 0;
    }
  }

  private static final class ServiceComparator implements Comparator<Service> {
    @Override
    public int compare(Service o1, Service o2) {
      String name1 = o1.getName();
      return name1 != null ? name1.compareToIgnoreCase(o2.getName()) : 0;
    }
  }

  private static final class ApplicationComparator implements Comparator<Application> {
    @Override
    public int compare(Application o1, Application o2) {
      String name1 = o1.getName();
      return name1 != null ? name1.compareToIgnoreCase(o2.getName()) : 0;
    }
  }

}
