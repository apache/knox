/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.shirorealm;

import org.apache.knox.gateway.ShiroMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.shiro.ShiroException;
import org.apache.shiro.cache.Cache;
import org.apache.shiro.cache.CacheException;
import org.apache.shiro.io.ResourceUtils;
import org.apache.shiro.util.Destroyable;
import org.apache.shiro.util.Initializable;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.integrations.shiro.EhcacheShiro;
import org.ehcache.xml.XmlConfiguration;

import java.net.MalformedURLException;
import java.net.URL;

public class KnoxCacheManager implements org.apache.shiro.cache.CacheManager, Initializable, Destroyable {
  private static final ShiroMessages LOG = MessagesFactory.get(ShiroMessages.class);

  private org.ehcache.CacheManager manager;
  private String cacheManagerConfigFile = "classpath:org/ehcache/integrations/shiro/ehcache.xml";
  private boolean cacheManagerImplicitlyCreated;
  private XmlConfiguration cacheConfiguration;

  public CacheManager getCacheManager() {
    return manager;
  }

  public void setCacheManager(CacheManager cacheManager) {
    try {
      destroy();
    } catch (Exception e) {
      LOG.errorClosingManagedCacheManager(e);
    }
    manager = cacheManager;
    cacheManagerImplicitlyCreated = false;
  }

  public String getCacheManagerConfigFile() {
    return cacheManagerConfigFile;
  }

  public void setCacheManagerConfigFile(String cacheManagerConfigFile) {
    this.cacheManagerConfigFile = cacheManagerConfigFile;
  }

  @Override
  public <K, V> Cache<K, V> getCache(String name) throws CacheException {
    LOG.acquireEhcacheShiro(name);
    try {
      org.ehcache.Cache<Object, Object> cache = ensureCacheManager().getCache(name, Object.class, Object.class);

      if (cache == null) {
        LOG.noCacheFound(name);
        cache = createCache(name);
        LOG.ehcacheShiroAdded(name);
      } else {
        LOG.usingExistingEhcacheShiro(name);
      }
      return new EhcacheShiro<>(cache);
    } catch (MalformedURLException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
      throw new CacheException(e);
    }
  }

  private synchronized org.ehcache.Cache<Object, Object> createCache(String name)
          throws MalformedURLException, ClassNotFoundException, InstantiationException, IllegalAccessException {
    org.ehcache.Cache<Object, Object> cache = ensureCacheManager().getCache(name, Object.class, Object.class);
    if (cache == null) {
      XmlConfiguration xmlConfiguration = getConfiguration();
      CacheConfigurationBuilder<Object, Object> configurationBuilder = xmlConfiguration.newCacheConfigurationBuilderFromTemplate(
              "defaultCacheConfiguration", Object.class, Object.class);
      CacheConfiguration<Object, Object> cacheConfiguration = configurationBuilder.build();
      cache = ensureCacheManager().createCache(name, cacheConfiguration);
    }
    return cache;
  }

  private org.ehcache.CacheManager ensureCacheManager() throws MalformedURLException {
    if (manager == null) {
      manager = CacheManagerBuilder.newCacheManager(getConfiguration());
      manager.init();

      cacheManagerImplicitlyCreated = true;
    }

    return manager;
  }

  private URL getResource() {
    String URL = ResourceUtils.hasResourcePrefix(this.cacheManagerConfigFile) ?
            stripPrefix(this.cacheManagerConfigFile) : this.cacheManagerConfigFile;

    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    if (loader == null) {
      loader = this.getClass().getClassLoader();
    }

    return loader.getResource(URL);
  }


  private static String stripPrefix(String resourcePath) {
    return resourcePath.substring(resourcePath.indexOf(':') + 1);
  }

  private XmlConfiguration getConfiguration() throws MalformedURLException {
    if (cacheConfiguration == null) {
      cacheConfiguration = new XmlConfiguration(getResource());
    }

    return cacheConfiguration;
  }

  @Override
  public void destroy() {
    if (cacheManagerImplicitlyCreated && manager != null) {
      manager.close();
      manager = null;
    }
  }

  @Override
  public void init() throws ShiroException {
    try {
      ensureCacheManager();
    } catch (MalformedURLException e) {
      throw new ShiroException(e);
    }
  }
}
