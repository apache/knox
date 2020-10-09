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
package org.apache.knox.gateway.ha.provider;

public interface HaServiceConfig {
  void setServiceName(String name);

  String getServiceName();

  boolean isEnabled();

  void setEnabled(boolean enabled);

  void setMaxFailoverAttempts(int limit);

  int getMaxFailoverAttempts();

  void setFailoverSleep(int sleep);

  int getFailoverSleep();

  String getZookeeperEnsemble();

  void setZookeeperEnsemble(String zookeeperEnsemble);

  String getZookeeperNamespace();

  void setZookeeperNamespace(String zookeeperNamespace);

  boolean isLoadBalancingEnabled();

  void setLoadBalancingEnabled(boolean isLoadBalancingEnabled);

  boolean isStickySessionEnabled();

  void setStickySessionEnabled(boolean stickySessionEnabled);

  String getStickySessionCookieName();

  void setStickySessionCookieName(String stickySessionCookieName);

  boolean isNoFallbackEnabled();

  void setNoFallbackEnabled(boolean noFallbackEnabled);
}
