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

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteStepDescriptor;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteStepProcessor;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

public abstract class UrlRewriteStepProcessorFactory {

  private static final Map<Class<? extends UrlRewriteStepDescriptor>,Map<String,Class<? extends UrlRewriteStepProcessor>>> MAP
      = loadStepProcessors();

  private UrlRewriteStepProcessorFactory() {
  }

  public static UrlRewriteStepProcessor create( UrlRewriteStepDescriptor descriptor ) throws IllegalAccessException, InstantiationException {
    UrlRewriteStepProcessor processor;
    Map<String,Class<? extends UrlRewriteStepProcessor>> typeMap;
    typeMap = MAP.get( descriptor.getClass() );
    if( typeMap == null ) {
      Class<? extends UrlRewriteStepDescriptor> descriptorInterface = getDescriptorInterface( descriptor );
      typeMap = MAP.get( descriptorInterface );
    }
    if( typeMap == null ) {
      throw new IllegalArgumentException( descriptor.getClass().getName() );
    } else {
      String type = descriptor.type();
      Class<? extends UrlRewriteStepProcessor> processorClass = typeMap.get( type );
      if( processorClass == null ) {
        throw new IllegalArgumentException( type );
      } else {
        processor = processorClass.newInstance();
      }
    }
    return processor;
  }

  private static Map<Class<? extends UrlRewriteStepDescriptor>,Map<String,Class<? extends UrlRewriteStepProcessor>>> loadStepProcessors() {
    Map<Class<? extends UrlRewriteStepDescriptor>,Map<String,Class<? extends UrlRewriteStepProcessor>>> descriptorMap
        = new HashMap<>();
    ServiceLoader<UrlRewriteStepProcessor> processors = ServiceLoader.load( UrlRewriteStepProcessor.class );
    for( UrlRewriteStepProcessor processor : processors ) {
      Class<? extends UrlRewriteStepDescriptor> descriptorInterface = getDescriptorInterface( processor );
      Map<String, Class<? extends UrlRewriteStepProcessor>> typeMap = descriptorMap
          .computeIfAbsent(descriptorInterface, k -> new HashMap<>());
      String processorType = processor.getType();
      typeMap.put( processorType, processor.getClass() );
    }
    return descriptorMap;
  }

  private static Class<? extends UrlRewriteStepDescriptor> getDescriptorInterface( UrlRewriteStepDescriptor descriptor ) {
    Class<? extends UrlRewriteStepDescriptor> descriptorClass = null;
    for( Type interfaceType : descriptor.getClass().getGenericInterfaces() ) {
      Class genericClass = (Class)interfaceType;
      if( UrlRewriteStepDescriptor.class.isAssignableFrom( genericClass ) ) {
        descriptorClass = uncheckedStepDescriptorClassCast( genericClass );
        break;
      }
    }
    return descriptorClass;
  }

  private static Class<? extends UrlRewriteStepDescriptor> getDescriptorInterface( UrlRewriteStepProcessor processor ) {
    Class<? extends UrlRewriteStepDescriptor> descriptorClass = null;
    Class<? extends UrlRewriteStepProcessor> processorClass = processor.getClass();
    for( Type interfaceType : processorClass.getGenericInterfaces() ) {
      if( UrlRewriteStepProcessor.class.isAssignableFrom( (Class)((ParameterizedType)interfaceType).getRawType() ) ) {
        ParameterizedType interfaceClass = (ParameterizedType)interfaceType;
        descriptorClass = uncheckedStepDescriptorClassCast( interfaceClass.getActualTypeArguments()[ 0 ] );
        break;
      }
    }
    return descriptorClass;
  }

  @SuppressWarnings("unchecked")
  private static Class<? extends UrlRewriteStepDescriptor> uncheckedStepDescriptorClassCast( Type type ) {
    return (Class<? extends UrlRewriteStepDescriptor>)type;
  }

}
