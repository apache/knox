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

  private boolean isStickySessionEnabled = DEFAULT_STICKY_SESSIONS_ENABLED;

  private boolean isLoadBalancingEnabled = DEFAULT_LOAD_BALANCING_ENABLED;

  private boolean isNoFallbackEnabled = DEFAULT_NO_FALLBACK_ENABLED;

  private String stickySessionCookieName = DEFAULT_STICKY_SESSION_COOKIE_NAME;

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
  public boolean isStickySessionEnabled() {
    return  this.isStickySessionEnabled;
  }

  @Override
  public void setStickySessionEnabled(boolean stickySessionEnabled) {
    this.isStickySessionEnabled = stickySessionEnabled;
  }

  @Override
  public String getStickySessionCookieName() {
    return this.stickySessionCookieName;
  }

  @Override
  public void setStickySessionCookieName(String stickySessionCookieName) {
    this.stickySessionCookieName = stickySessionCookieName;
  }

  @Override
  public boolean isLoadBalancingEnabled() {
    return this.isLoadBalancingEnabled;
  }

  @Override
  public void setLoadBalancingEnabled(boolean isLoadBalancingEnabled) {
    this.isLoadBalancingEnabled = isLoadBalancingEnabled;
  }

  @Override
  public boolean isNoFallbackEnabled() {
    return isNoFallbackEnabled;
  }

  @Override
  public void setNoFallbackEnabled(boolean noFallbackEnabled) {
    isNoFallbackEnabled = noFallbackEnabled;
  }
}
