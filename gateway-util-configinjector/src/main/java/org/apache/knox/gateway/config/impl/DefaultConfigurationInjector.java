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

import org.apache.commons.beanutils.ConvertUtilsBean2;
import org.apache.knox.gateway.config.Alias;
import org.apache.knox.gateway.config.ConfigurationAdapter;
import org.apache.knox.gateway.config.ConfigurationBinding;
import org.apache.knox.gateway.config.ConfigurationException;
import org.apache.knox.gateway.config.Configure;
import org.apache.knox.gateway.config.Default;
import org.apache.knox.gateway.config.Optional;
import org.apache.knox.gateway.config.spi.ConfigurationInjector;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

public class DefaultConfigurationInjector implements ConfigurationInjector {

  private static ConvertUtilsBean2 DEFAULT_CONVERTER = new ConvertUtilsBean2();

  @Override
  public void configure( Object target, ConfigurationAdapter adapter, ConfigurationBinding binding )
      throws ConfigurationException {
    Class type = target.getClass();
    while( type != null ) {
      injectClass( type, target, adapter, binding );
      type = type.getSuperclass();
    }
  }

  private void injectClass( Class type, Object target, ConfigurationAdapter config, ConfigurationBinding binding )
      throws ConfigurationException {
    Field[] fields = type.getDeclaredFields();
    Arrays.sort(fields, new Comparator<Field>() {
      @Override
      public int compare(Field field1, Field field2) {
        if ("serviceRole".equals(field1.getName())) {
          return -1;
        } else if ("serviceRole".equals(field2.getName())) {
          return 1;
        } else {
          return field1.getName().compareTo(field2.getName());
        }
      };
    });
    for( Field field : fields ) {
      injectFieldValue( field, target, config, binding );
    }
    Method[] methods = type.getDeclaredMethods();
    for( Method method : methods ) {
      injectMethodValue( method, target, config, binding );
    }
  }

  private void injectFieldValue( Field field, Object target, ConfigurationAdapter adapter, ConfigurationBinding binding )
      throws ConfigurationException {
    Configure annotation = field.getAnnotation( Configure.class );
    if( annotation != null ) {
      Alias alias = field.getAnnotation( Alias.class );
      String name = getConfigName( field, alias );
      String bind = getBindName( target, name, binding );
      Object value = retrieveValue( target, bind, name, field.getType(), adapter );
      if( value == null ) {
        Optional optional = field.getAnnotation( Optional.class );
        if( optional == null ) {
          throw new ConfigurationException( String.format( Locale.ROOT,
              "Failed to find configuration for %s bound to %s of %s via %s",
              bind, name, target.getClass().getName(), adapter.getClass().getName() ) );
        }
      } else {
        try {
          if( !field.isAccessible() ) {
            field.setAccessible( true );
          }
          field.set( target, value );
        } catch( Exception e ) {
          throw new ConfigurationException( String.format( Locale.ROOT,
              "Failed to inject field configuration property %s of %s",
              name, target.getClass().getName() ), e );
        }
      }
    }
  }

  private void injectMethodValue( Method method, Object target, ConfigurationAdapter adapter, ConfigurationBinding binding )
      throws ConfigurationException {
    Configure methodTag = method.getAnnotation( Configure.class );
    if( methodTag != null ) {
      Alias aliasTag = method.getAnnotation( Alias.class );
      String methodName = getConfigName( method, aliasTag );
      Class[] argTypes = method.getParameterTypes();
      Object[] args = new Object[ argTypes.length ];
      Annotation[][] argTags = method.getParameterAnnotations();
      for( int i=0; i<argTypes.length; i++ ) {
        String argName = getConfigName( methodName, argTags[i] );
        String bndName = getBindName( target, argName, binding );
        Object argValue = retrieveValue( target, bndName, argName, argTypes[i], adapter );
        if( argValue == null ) {
          Default defTag = findAnnotation( argTags[i], Default.class );
          if( defTag != null ) {
            String strValue = defTag.value();
            argValue = convertValue( target, argName, strValue, argTypes[i] );
          } else {
            throw new ConfigurationException( String.format( Locale.ROOT,
                "Failed to find configuration for %s as %s of %s via %s",
                bndName, argName, target.getClass().getName(), adapter.getClass().getName() ) );
          }
        }
        args[ i ] = argValue;
      }
      if( !method.isAccessible() ) {
        method.setAccessible( true );
      }
      try {
        method.invoke( target, args );
      } catch( Exception e ) {
        throw new ConfigurationException( String.format( Locale.ROOT,
            "Failed to inject method configuration via %s of %s",
            methodName, target.getClass().getName() ), e );
      }
    }
  }

  private Object convertValue( Object target, String name, Object strValue, Class<?> type ) {
    Object objValue;
    try {
      objValue = DEFAULT_CONVERTER.convert( strValue, type );
    } catch( Exception e ) {
      throw new ConfigurationException( String.format( Locale.ROOT,
          "Failed to convert configuration for %s of %s to %s",
          name, target.getClass().getName(), type.getName() ), e );
    }
    return objValue;
  }

  private Object retrieveValue( Object target, String bind, String name, Class<?> type, ConfigurationAdapter adapter) {
    Object value;
    try {
      value = adapter.getConfigurationValue( bind );
    } catch( Exception e ) {
      throw new ConfigurationException( String.format( Locale.ROOT,
          "Failed to retrieve configuration for %s bound to %s of %s via %s",
          bind, name, target.getClass().getName(), adapter.getClass().getName() ), e );
    }
    // Otherwise null sometimes ends up being converted to 0.
    if( value != null ) {
      value = convertValue( target, name, value, type );
    }
    return value;
  }

  private <T extends Annotation> T findAnnotation( Annotation[] annotations, Class<T> type ) {
    T found = null;
    for( Annotation current : annotations ) {
      if( type.isAssignableFrom( current.annotationType() ) ) {
        found = (T)current;
        break;
      }
    }
    return found;
  }

  private static String pickName( String implied, Alias explicit ) {
    String name = implied;
    if( explicit != null ) {
      String tagValue = explicit.value().trim();
      if(!tagValue.isEmpty()) {
        name = tagValue;
      }
    }
    return name;
  }

  private static String getBindName( Object target, String name, ConfigurationBinding binding ) {
    String bind;
    try {
      bind = binding.getConfigurationName( name );
    } catch( Exception e ) {
      throw new ConfigurationException( String.format( Locale.ROOT,
          "Failed to bind configuration for %s of %s via %s",
          name, target.getClass().getName(), binding.getClass().getName() ), e );
    }
    if( bind == null ) {
      bind = name;
    }
    return bind;
  }

  private static String getConfigName( Field field, Alias tag ) {
    return pickName( field.getName(), tag );
  }

  private static String getConfigName( String name, Annotation[] tags ) {
    if( tags != null ) {
      for( Annotation tag : tags ) {
        if(tag instanceof Alias) {
          Alias aliasTag = (Alias) tag;
          String aliasValue = aliasTag.value().trim();
          if(!aliasValue.isEmpty()) {
            name = aliasValue;
            break;
          }
        }
      }
    }
    return name;
  }

  private static String getConfigName( Method method, Alias tag ) {
    return pickName( getConfigName( method ), tag );
  }

  private static String getConfigName( Method method ) {
    String methodName = method.getName();
    if( methodName != null ) {
        StringBuilder name = new StringBuilder( methodName.length() );
        if( methodName.length() > 3 &&
            methodName.startsWith( "set" ) &&
            Character.isUpperCase( methodName.charAt( 3 ) ) ) {
          name.append( methodName.substring( 3 ) );
          name.setCharAt( 0, Character.toLowerCase( name.charAt( 0 ) ) );
        } else {
          name.append( name );
        }
        return name.toString();
    }
    return null;
  }

}
