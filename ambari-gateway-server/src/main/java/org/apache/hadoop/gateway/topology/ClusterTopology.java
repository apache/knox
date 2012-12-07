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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ClusterTopology {

  private String name;
  private Collection<ClusterTopologyComponent> components = new ArrayList<ClusterTopologyComponent>();
  private Map<String, ClusterTopologyFilterProvider> providers = new HashMap<String, ClusterTopologyFilterProvider>();


  public String getName() {
    return name;
  }

  public void setName( String name ) {
    this.name = name;
  }

  private long timestamp;

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp( long timestamp ) {
    this.timestamp = timestamp;
  }

  public Collection<ClusterTopologyComponent> getComponents() {
    return this.components;
  }

  public void addComponent( ClusterTopologyComponent component ) {
    components.add( component );
  }

  public Collection<ClusterTopologyFilterProvider> getProviders() {
    return this.providers.values();
  }

  public ClusterTopologyFilterProvider getProvider(String role) {
    return this.providers.get(role);
  }

  public void addProvider( ClusterTopologyFilterProvider provider ) {
    providers.put( provider.getRole(), provider );
  }
}
