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
package org.apache.knox.gateway.launcher;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Invoker {

  private static final String[] STRING_ARRAY = new String[0];
  private static final Class STRING_ARRAY_CLASS = STRING_ARRAY.getClass();

  static void invoke( Command command ) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    setSysProps( command.props );
    ClassLoader mainLoader = createClassLoader( command.classPath );
    Class mainClass = loadMainClass( mainLoader, command.mainClass );
    Method mainMethod = findMainMethod( mainClass, command.mainMethod );
    invokeMainMethod( mainLoader, mainMethod, command.mainArgs );
  }

  private static void setSysProps( Properties properties ) {
    for( String name : properties.stringPropertyNames() ) {
      String value = System.getProperty( name, null );
      if( value == null ) {
        value = resolveValue( properties, name );
        System.setProperty( name, value );
      }
    }
  }

  private static ClassLoader createClassLoader( List<URL> urls ) {
    URL[] urlArray = new URL[ urls.size() ];
    urls.toArray( urlArray );
    return new URLClassLoader( urlArray );
  }

  private static Class loadMainClass( ClassLoader loader, String className ) throws ClassNotFoundException {
    return loader.loadClass( className );
  }

  private static Method findMainMethod( Class mainClass, String methodName ) throws NoSuchMethodException {
    return mainClass.getMethod( methodName, STRING_ARRAY_CLASS );
  }

  private static void invokeMainMethod( ClassLoader loader, Method method, String[] args ) throws InvocationTargetException, IllegalAccessException {
    Thread.currentThread().setContextClassLoader( loader );
    method.invoke( method.getClass(), (Object)args );
  }

  private static String resolveValue( Properties properties, String name ) {
    String value = properties.getProperty( name );
    Pattern pattern = Pattern.compile( ".*?(\\$\\{)(.*?)(\\}).*" );
    Matcher matcher = pattern.matcher( value );
    while( matcher.matches() ) {
      StringBuilder resolvedValue = new StringBuilder( value.length() );
      resolvedValue.append( value.substring( 0, matcher.start( 1 ) ) );
      String varName = matcher.group( 2 );
      String varVal = properties.getProperty( varName );
      if( varVal != null ) {
        resolvedValue.append( varVal );
      }
      resolvedValue.append( value.substring( matcher.end( 3 ) ) );
      value = resolvedValue.toString();
      matcher = pattern.matcher( value );
    }
    return value;
  }

}
