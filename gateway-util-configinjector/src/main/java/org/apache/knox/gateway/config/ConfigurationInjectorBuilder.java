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
package org.apache.knox.gateway.config;

import org.apache.knox.gateway.config.impl.ConfigurationAdapterFactory;
import org.apache.knox.gateway.config.impl.DefaultConfigurationBinding;
import org.apache.knox.gateway.config.impl.MappedConfigurationBinding;
import org.apache.knox.gateway.config.spi.ConfigurationInjector;

import java.util.Iterator;
import java.util.Locale;
import java.util.ServiceLoader;

public class ConfigurationInjectorBuilder {

  private static ConfigurationBinding DEFAULT_BINDING = new DefaultConfigurationBinding();
  private static ConfigurationInjector INSTANCE = null;

  private static synchronized ConfigurationInjector getInjector() {
    if( INSTANCE == null ) {
      INSTANCE = createInjector();
    }
    return INSTANCE;
  }

  private static synchronized ConfigurationInjector createInjector() {
    ConfigurationInjector injector = null;
    ServiceLoader<ConfigurationInjector> loader = ServiceLoader.load( ConfigurationInjector.class );
    if( loader != null ) {
      Iterator<ConfigurationInjector> iterator = loader.iterator();
      if( iterator != null ) {
        while( iterator.hasNext() ) {
          injector = iterator.next();
          break;
        }
      }
    }
    if( injector == null ) {
      throw new ConfigurationException( String.format(Locale.ROOT,
          "Failed to load an implementation of %s", ConfigurationInjector.class.getName() ) );
    }
    return injector;
  }

  private Object target = null;
  private ConfigurationAdapter source = null;
  private ConfigurationBinding binding = null;

  public static ConfigurationInjectorBuilder configuration() {
    return new ConfigurationInjectorBuilder();
  }

  public ConfigurationInjectorBuilder target( Object target ) {
    this.target = target;
    return this;
  }

  public ConfigurationInjectorBuilder source( Object source ) {
    this.source = ConfigurationAdapterFactory.get(source);
    return this;
  }

  public ConfigurationInjectorBuilder source( ConfigurationAdapter adapter ) {
    this.source = adapter;
    return this;
  }

  public ConfigurationInjectorBuilder binding( ConfigurationBinding binding ) {
    this.binding = binding;
    return this;
  }

  public ConfigurationInjectorBuilder bind( String targetName, String sourceName ) {
    ((MappedConfigurationBinding)binding()).bind( targetName, sourceName );
    return this;
  }

  public ConfigurationBinding binding() {
    if( binding == null ) {
      binding = new MappedConfigurationBinding();
    }
    return binding;
  }

  public void inject() throws ConfigurationException {
    ConfigurationInjector injector = getInjector();
    if( binding == null ) {
      binding = DEFAULT_BINDING;
    }
    injector.configure( target, source, binding );
  }

}
