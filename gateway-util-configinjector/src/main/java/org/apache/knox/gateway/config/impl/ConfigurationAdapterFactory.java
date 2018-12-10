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
package org.apache.knox.gateway.config.impl;


import org.apache.knox.gateway.config.ConfigurationAdapter;
import org.apache.knox.gateway.config.ConfigurationException;
import org.apache.knox.gateway.config.spi.ConfigurationAdapterDescriptor;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

public class ConfigurationAdapterFactory {

  private static Map<Class<?>, Class<? extends ConfigurationAdapter>> ADAPTERS;

  private static synchronized Map<Class<?>, Class<? extends ConfigurationAdapter>> getAdapters() {
    if( ADAPTERS == null ) {
      loadAdapters();
    }
    return ADAPTERS;
  }

  private static void loadAdapters() {
    Map<Class<?>, Class<? extends ConfigurationAdapter>> all =
        new HashMap<>();
    ServiceLoader<ConfigurationAdapterDescriptor> loader = ServiceLoader.load( ConfigurationAdapterDescriptor.class );
    if( loader != null ) {
      Iterator<ConfigurationAdapterDescriptor> i = loader.iterator();
      if( i != null ) {
        while( i.hasNext() ) {
          ConfigurationAdapterDescriptor descriptor = i.next();
          Map<Class<?>, Class<? extends ConfigurationAdapter>> add = descriptor.providedConfigurationAdapters();
          if( add != null ) {
            all.putAll( add );
          }
        }
      }
    }
    ADAPTERS = Collections.unmodifiableMap( all );
  }

  public static ConfigurationAdapter get( Object config ) throws ConfigurationException {
    if( config == null ) {
      throw new NullPointerException( "Configuration adapter instantiation impossible for null config object." );
    }
    try {
      Map<Class<?>, Class<? extends ConfigurationAdapter>> adapters = getAdapters();
      Class configType = config.getClass();
      Class adapterType = findAdapterTypeForConfigTypeOrParent( adapters, configType );
      if( adapterType == null ) {
        throw new ConfigurationException( "No configuration adapter found for config type " + configType.getName() );
      }
      Constructor c = findConstructorForConfigType( adapterType, configType );
      if( !c.isAccessible() ) {
        c.setAccessible( true );
      }
      Object adapter = c.newInstance( config );
      return ConfigurationAdapter.class.cast( adapter );
    } catch( ConfigurationException e ) {
      throw e;
    } catch( Exception e ) {
      throw new ConfigurationException( "Configuration adapter instantiation failed.", e );
    }
  }

  public static Constructor findConstructorForConfigType( Class<?> adapterType, Class<?> configType ) throws NoSuchMethodException {
    Constructor constructor = null;
    Constructor[] constructors = adapterType.getConstructors();
    for( Constructor candidate : constructors ) {
      Class<?>[] paramTypes = candidate.getParameterTypes();
      if( paramTypes.length == 1 ) {
        Class<?> paramType = paramTypes[0];
        if( paramType.isAssignableFrom( configType ) ) {
          constructor = candidate;
          break;
        }
      }
    }
    if( constructor == null ) {
      throw new NoSuchMethodException( "No constructor for " + adapterType.getName() + " that will accept " + configType.getName() );
    }
    return constructor;
  }

  public static Class<? extends ConfigurationAdapter> findAdapterTypeForConfigTypeOrParent(
      Map<Class<?>, Class<? extends ConfigurationAdapter>> adapters, Class<?> configType ) {
    Class<? extends ConfigurationAdapter> adapterType = null;
    while( configType != null ) {
      adapterType = findAdapterTypeForConfigType( adapters, configType );
      if( adapterType != null ) {
        break;
      }
      configType = configType.getSuperclass();
    }
    return adapterType;
  }

  public static Class<? extends ConfigurationAdapter> findAdapterTypeForConfigType(
      Map<Class<?>, Class<? extends ConfigurationAdapter>> adapters, Class<?> configType ) {
    Class<? extends ConfigurationAdapter> adapterType = adapters.get( configType );
    if( adapterType == null ) {
      for( Class interfaceType : configType.getInterfaces() ) {
        adapterType = findAdapterTypeForConfigTypeOrParent( adapters, interfaceType );
        if( adapterType != null ) {
          break;
        }
      }
    }
    return adapterType;
  }

}
