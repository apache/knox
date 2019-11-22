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
package org.apache.knox.gateway.filter.rewrite.api;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

public abstract class UrlRewriteFunctionDescriptorFactory {

  private static Map<String,Class<? extends UrlRewriteFunctionDescriptor>> MAP
      = loadDescriptors();

  private UrlRewriteFunctionDescriptorFactory() {
  }

  @SuppressWarnings("unchecked")
  public static <T extends UrlRewriteFunctionDescriptor<?>> T create( String name ) {
    try {
      Class<? extends UrlRewriteFunctionDescriptor> descriptorClass = MAP.get( name );
      return (T)descriptorClass.newInstance();
    } catch( InstantiationException | IllegalAccessException e ) {
      throw new IllegalArgumentException( name );
    }
  }

  private static Map<String,Class<? extends UrlRewriteFunctionDescriptor>> loadDescriptors() {
    Map<String,Class<? extends UrlRewriteFunctionDescriptor>> map
        = new HashMap<>();
    ServiceLoader<? extends UrlRewriteFunctionDescriptor> descriptors
        = ServiceLoader.load( UrlRewriteFunctionDescriptor.class );
    for( UrlRewriteFunctionDescriptor descriptor : descriptors ) {
      String functionName = descriptor.name();
      Class<? extends UrlRewriteFunctionDescriptor> descriptorClass = descriptor.getClass() ;
      map.put( functionName, descriptorClass );
    }
    return map;
  }

  public static Set<String> getNames() {
    return MAP.keySet();
  }
}
