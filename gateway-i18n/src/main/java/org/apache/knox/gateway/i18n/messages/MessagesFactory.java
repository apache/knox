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
package org.apache.knox.gateway.i18n.messages;

import org.apache.knox.gateway.i18n.messages.loggers.jdk.JdkMessageLoggerFactory;

import java.lang.reflect.Proxy;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

public class MessagesFactory {

  private static MessageLoggerFactory loggers = getMessageLoggerFactory();
  private static Map<Class<?>, Object> proxies = new ConcurrentHashMap<>();

  @SuppressWarnings( "unchecked" )
  public static <T> T get( Class<T> clazz ) {
    Object proxy = proxies.get( clazz );
    if( proxy == null ) {
      Messages anno = clazz.getAnnotation( Messages.class );
      if( anno == null ) {
        throw new IllegalArgumentException( clazz.getName() + " missing @" + Messages.class.getCanonicalName() );
      }
      MessagesInvoker invoker = new MessagesInvoker( clazz, loggers );
      proxy = Proxy.newProxyInstance( clazz.getClassLoader(), new Class[]{ clazz }, invoker );
      proxies.put( clazz, proxy );
    }
    return (T)proxy;
  }

  private static MessageLoggerFactory getMessageLoggerFactory() {
    MessageLoggerFactory factory;
    ServiceLoader<MessageLoggerFactory> loader = ServiceLoader.load( MessageLoggerFactory.class );
    Iterator<MessageLoggerFactory> factories = loader.iterator();
    if(factories.hasNext()) {
      factory = loader.iterator().next();
    } else {
      factory = new JdkMessageLoggerFactory();
    }
    return factory;
  }

}
