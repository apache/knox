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
package org.apache.knox.gateway.filter.rewrite.impl;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFunctionDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFunctionDescriptorFactory;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

public abstract class UrlRewriteFunctionProcessorFactory {

  private static final Map<Class<? extends UrlRewriteFunctionDescriptor>,Map<String,Class<? extends UrlRewriteFunctionProcessor>>> MAP
      = loadProcessors();

  private UrlRewriteFunctionProcessorFactory() {
  }

  public static UrlRewriteFunctionProcessor create( String name, UrlRewriteFunctionDescriptor descriptor )
      throws IllegalAccessException, InstantiationException {
    UrlRewriteFunctionProcessor processor;
    if( descriptor == null ) {
      descriptor = UrlRewriteFunctionDescriptorFactory.create( name );
    }
    Map<String,Class<? extends UrlRewriteFunctionProcessor>> typeMap;
    typeMap = MAP.get( descriptor.getClass() );
    if( typeMap == null ) {
      Class<? extends UrlRewriteFunctionDescriptor> descriptorInterface = getDescriptorInterface( descriptor );
      typeMap = MAP.get( descriptorInterface );
    }
    if( typeMap == null ) {
      throw new IllegalArgumentException( descriptor.getClass().getName() );
    } else {
      Class<? extends UrlRewriteFunctionProcessor> processorClass = typeMap.get( name );
      if( processorClass == null ) {
        throw new IllegalArgumentException( name );
      } else {
        processor = processorClass.newInstance();
      }
    }
    return processor;
  }

  private static Map<Class<? extends UrlRewriteFunctionDescriptor>,Map<String,Class<? extends UrlRewriteFunctionProcessor>>> loadProcessors() {
    Map<Class<? extends UrlRewriteFunctionDescriptor>,Map<String,Class<? extends UrlRewriteFunctionProcessor>>> descriptorMap
        = new HashMap<>();
    ServiceLoader<UrlRewriteFunctionProcessor> processors = ServiceLoader.load( UrlRewriteFunctionProcessor.class );
    for( UrlRewriteFunctionProcessor processor : processors ) {
      Class<? extends UrlRewriteFunctionDescriptor> descriptorInterface = getDescriptorInterface( processor );
      Map<String, Class<? extends UrlRewriteFunctionProcessor>> typeMap = descriptorMap
          .computeIfAbsent(descriptorInterface, k -> new HashMap<>());
      String functionName = processor.name();
      typeMap.put( functionName, processor.getClass() );
    }
    return descriptorMap;
  }

  private static Class<? extends UrlRewriteFunctionDescriptor> getDescriptorInterface(
      UrlRewriteFunctionDescriptor descriptor ) {
    Class<? extends UrlRewriteFunctionDescriptor> descriptorClass = null;
    for( Type interfaceType : descriptor.getClass().getGenericInterfaces() ) {
      Class genericClass = (Class)interfaceType;
      if( UrlRewriteFunctionDescriptor.class.isAssignableFrom( genericClass ) ) {
        descriptorClass = uncheckedDescriptorClassCast( genericClass );
        break;
      }
    }
    return descriptorClass;
  }

  private static Class<? extends UrlRewriteFunctionDescriptor> getDescriptorInterface(
      UrlRewriteFunctionProcessor processor ) {
    Class<? extends UrlRewriteFunctionDescriptor> descriptorClass = null;
    Class<? extends UrlRewriteFunctionProcessor> processorClass = processor.getClass();
    for( Type interfaceType : processorClass.getGenericInterfaces() ) {
      if( UrlRewriteFunctionProcessor.class.isAssignableFrom(
          (Class)((ParameterizedType)interfaceType).getRawType() ) ) {
        ParameterizedType interfaceClass = (ParameterizedType)interfaceType;
        descriptorClass = uncheckedDescriptorClassCast( interfaceClass.getActualTypeArguments()[ 0 ] );
        break;
      }
    }
    return descriptorClass;
  }

  @SuppressWarnings("unchecked")
  private static Class<? extends UrlRewriteFunctionDescriptor> uncheckedDescriptorClassCast( Type type ) {
    return (Class<? extends UrlRewriteFunctionDescriptor>)type;
  }

}
