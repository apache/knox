/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.discovery.cm.auth;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.topology.discovery.cm.ClouderaManagerServiceDiscoveryMessages;

import javax.security.auth.login.Configuration;
import java.lang.reflect.Constructor;
import java.net.URI;

class ConfigurationFactory {

  private static final ClouderaManagerServiceDiscoveryMessages log =
      MessagesFactory.get(ClouderaManagerServiceDiscoveryMessages.class);

  private static final Class implClazz;
  static {
    // Oracle and OpenJDK use the Sun implementation
    String implName = System.getProperty("java.vendor").contains("IBM") ?
        "com.ibm.security.auth.login.ConfigFile" : "com.sun.security.auth.login.ConfigFile";

    log.usingJAASConfigurationFileImplementation(implName);
    Class clazz = null;
    try {
      clazz = Class.forName(implName, false, Thread.currentThread().getContextClassLoader());
    } catch (ClassNotFoundException e) {
      log.failedToLoadJAASConfigurationFileImplementation(implName, e);
    }

    implClazz = clazz;
  }

  static Configuration create(URI uri) {
    Configuration config = null;

    if (implClazz != null) {
      try {
        Constructor ctor = implClazz.getDeclaredConstructor(URI.class);
        config = (Configuration) ctor.newInstance(uri);
      } catch (Exception e) {
        log.failedToInstantiateJAASConfigurationFileImplementation(implClazz.getCanonicalName(), e);
      }
    } else {
      log.noJAASConfigurationFileImplementation();
    }

    return config;
  }
}
