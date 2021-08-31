/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.ha.provider.impl;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.knox.gateway.ha.provider.HaDescriptor;
import org.apache.knox.gateway.ha.provider.HaProvider;
import org.apache.knox.gateway.ha.provider.HaServiceConfig;
import org.apache.knox.gateway.ha.provider.URLManager;
import org.apache.knox.gateway.ha.provider.URLManagerLoader;
import org.apache.knox.gateway.ha.provider.impl.i18n.HaMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

public class DefaultHaProvider implements HaProvider {

  private static final HaMessages LOG = MessagesFactory.get(HaMessages.class);

  private HaDescriptor descriptor;

  private ConcurrentHashMap<String, URLManager> haServices;

  private ReentrantReadWriteLock rwl = new ReentrantReadWriteLock(true);

  public DefaultHaProvider(HaDescriptor descriptor) {
    if ( descriptor == null ) {
      throw new IllegalArgumentException("Descriptor can not be null");
    }
    this.descriptor = descriptor;
    haServices = new ConcurrentHashMap<>();
  }

  @Override
  public HaDescriptor getHaDescriptor() {
    return descriptor;
  }

  @Override
  public void addHaService(String serviceName, List<String> urls) {
    HaServiceConfig haServiceConfig = descriptor.getServiceConfig(serviceName);
    URLManager manager = URLManagerLoader.loadURLManager(haServiceConfig);
    manager.setURLs(urls);
    haServices.put(serviceName, manager);
  }

  @Override
  public boolean isHaEnabled(String serviceName) {
    HaServiceConfig config = descriptor.getServiceConfig(serviceName);
    return config != null && config.isEnabled();
  }

  @Override
  public String getActiveURL(String serviceName) {
    rwl.readLock().lock();
    try {
      if (haServices.containsKey(serviceName)) {
        return haServices.get(serviceName).getActiveURL();
      }
      LOG.noActiveUrlFound(serviceName);
      return null;
    } finally {
      rwl.readLock().unlock();
    }
  }

  @Override
  public void setActiveURL(String serviceName, String url) {
    rwl.writeLock().lock();
    try {
      if (haServices.containsKey(serviceName)) {
        haServices.get(serviceName).setActiveURL(url);
      } else {
        LOG.noServiceFound(serviceName);
      }
    }
    finally {
      rwl.writeLock().unlock();
    }

  }

  @Override
  public void markFailedURL(String serviceName, String url) {
    rwl.writeLock().lock();
    try {
      if (haServices.containsKey(serviceName)) {
        haServices.get(serviceName).markFailed(url);
      } else {
        LOG.noServiceFound(serviceName);
      }
    } finally {
      rwl.writeLock().unlock();
    }
  }

  @Override
  public void makeNextActiveURLAvailable(String serviceName) {
    rwl.writeLock().lock();
    try {
      if (haServices.containsKey(serviceName)) {
        haServices.get(serviceName).makeNextActiveURLAvailable();
      } else {
        LOG.noServiceFound(serviceName);
      }
    } finally {
      rwl.writeLock().unlock();
    }
  }

  @Override
  public List<String> getURLs(String serviceName) {
    if ( haServices.containsKey(serviceName) ) {
      return haServices.get(serviceName).getURLs();
    } else {
      LOG.noServiceFound(serviceName);
      return Collections.emptyList();
    }
  }
}
