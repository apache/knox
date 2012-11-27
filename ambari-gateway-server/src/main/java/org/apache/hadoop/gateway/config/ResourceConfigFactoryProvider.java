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
package org.apache.hadoop.gateway.config;

import org.apache.hadoop.gateway.topology.ClusterComponent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

public class ResourceConfigFactoryProvider {

  private volatile static Map<String, ResourceConfigFactory> FACTORIES = null;

  private static Map<String, ResourceConfigFactory> loadFactories() {
    Map<String, ResourceConfigFactory> factoryMap = new HashMap<String, ResourceConfigFactory>();
    ServiceLoader<ResourceConfigFactory> loader = ServiceLoader.load( ResourceConfigFactory.class );
    Iterator<ResourceConfigFactory> factories = loader.iterator();
    while( factories.hasNext() ) {
      ResourceConfigFactory factory = factories.next();
      Set<String> roles = factory.getSupportedRoles();
      for( String role : roles ) {
        factoryMap.put( role, factory );
      }
    }
    return factoryMap;
  }

  private static Map<String, ResourceConfigFactory> getFactories() {
    Map<String, ResourceConfigFactory> factories = FACTORIES;
    if( factories == null ) {
      factories = loadFactories();
      FACTORIES = factories;
    }
    return factories;
  }

  public static ResourceConfigFactory getResourceConfigFactory( ClusterComponent component ) {
    return getFactories().get( component.getRole() );
  }

}
