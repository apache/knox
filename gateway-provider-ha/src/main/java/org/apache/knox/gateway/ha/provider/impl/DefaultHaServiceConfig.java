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

import org.apache.knox.gateway.ha.provider.HaServiceConfig;

public class DefaultHaServiceConfig implements HaServiceConfig, HaServiceConfigConstants {

  private String name;

  private boolean enabled = DEFAULT_ENABLED;

  private int maxFailoverAttempts = DEFAULT_MAX_FAILOVER_ATTEMPTS;

  private int failoverSleep = DEFAULT_FAILOVER_SLEEP;

  private boolean isCookieHaEnabled = DEFAULT_COOKIE_HA_ENABLED;

  private String cookieHaCookieName = DEFAULT_COOKIE_HA_COOKIE_NAME;

  private String zookeeperEnsemble;

  private String zookeeperNamespace;

  public DefaultHaServiceConfig(String name) {
    this.name = name;
  }

  @Override
  public String getServiceName() {
    return name;
  }

  @Override
  public void setServiceName(String name) {
    this.name = name;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public int getMaxFailoverAttempts() {
    return maxFailoverAttempts;
  }

  @Override
  public void setMaxFailoverAttempts(int maxFailoverAttempts) {
    this.maxFailoverAttempts = maxFailoverAttempts;
  }

  @Override
  public int getFailoverSleep() {
    return failoverSleep;
  }

  @Override
  public void setFailoverSleep(int failoverSleep) {
    this.failoverSleep = failoverSleep;
  }

  @Override
  public String getZookeeperEnsemble() {
    return zookeeperEnsemble;
  }

  @Override
  public void setZookeeperEnsemble(String zookeeperEnsemble) {
    this.zookeeperEnsemble = zookeeperEnsemble;
  }

  @Override
  public String getZookeeperNamespace() {
    return zookeeperNamespace;
  }

  @Override
  public void setZookeeperNamespace(String zookeeperNamespace) {
    this.zookeeperNamespace = zookeeperNamespace;
  }

  @Override
  public boolean isCookieHaEnabled() {
    return  this.isCookieHaEnabled;
  }

  @Override
  public void setCookieHaEnabled(boolean isCookieHaEnabled) {
    this.isCookieHaEnabled = isCookieHaEnabled;
  }

  @Override
  public String getCookieHaCookieName() {
    return this.cookieHaCookieName;
  }

  @Override
  public void setCookieHaCookieName(String cookieHaCookieName) {
    this.cookieHaCookieName = cookieHaCookieName;
  }
}
