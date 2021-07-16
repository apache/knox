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

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.ha.provider.HaDescriptor;
import org.apache.knox.gateway.ha.provider.HaServiceConfig;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class HaDescriptorFactory implements HaServiceConfigConstants {

  public static HaDescriptor createDescriptor() {
    return new DefaultHaDescriptor();
  }

  public static HaServiceConfig createServiceConfig(String serviceName, String config) {
    final Map<String, String> configMap = parseHaConfiguration(config);

    final String zookeeperEnsemble = configMap.get(CONFIG_PARAM_ZOOKEEPER_ENSEMBLE);
    final String zookeeperNamespace = configMap.get(CONFIG_PARAM_ZOOKEEPER_NAMESPACE);

    final boolean enabled = Boolean.parseBoolean(configMap.getOrDefault(CONFIG_PARAM_ENABLED, Boolean.toString(DEFAULT_ENABLED)));
    final int maxFailoverAttempts = Integer.parseInt(configMap.getOrDefault(CONFIG_PARAM_MAX_FAILOVER_ATTEMPTS, Integer.toString(DEFAULT_MAX_FAILOVER_ATTEMPTS)));
    final int failoverSleep = Integer.parseInt(configMap.getOrDefault(CONFIG_PARAM_FAILOVER_SLEEP, Integer.toString(DEFAULT_FAILOVER_SLEEP)));
    final boolean stickySessionsEnabled = Boolean.parseBoolean(configMap.getOrDefault(CONFIG_STICKY_SESSIONS_ENABLED, Boolean.toString(DEFAULT_STICKY_SESSIONS_ENABLED)));
    final boolean loadBalancingEnabled = Boolean.parseBoolean(configMap.getOrDefault(CONFIG_LOAD_BALANCING_ENABLED, Boolean.toString(DEFAULT_LOAD_BALANCING_ENABLED)));
    final boolean noFallbackEnabled = Boolean.parseBoolean(configMap.getOrDefault(CONFIG_NO_FALLBACK_ENABLED, Boolean.toString(DEFAULT_NO_FALLBACK_ENABLED)));
    final String stickySessionCookieName = configMap.getOrDefault(STICKY_SESSION_COOKIE_NAME, DEFAULT_STICKY_SESSION_COOKIE_NAME);

    final String disableLoadBalancingForUserAgentsConfig = configMap.getOrDefault(DISABLE_LB_USER_AGENTS, DEFAULT_DISABLE_LB_USER_AGENTS);
    /* configured user agents are comma separate list which *can* contain whitespaces */
    List<String> disableLoadBalancingForUserAgents = Collections.EMPTY_LIST;
    if(StringUtils.isNotBlank(disableLoadBalancingForUserAgentsConfig)) {
      disableLoadBalancingForUserAgents = Arrays.asList(disableLoadBalancingForUserAgentsConfig
              .trim()
              .split("\\s*,\\s*"));
    }
    return createServiceConfig(serviceName, enabled, maxFailoverAttempts, failoverSleep, zookeeperEnsemble, zookeeperNamespace, stickySessionsEnabled, loadBalancingEnabled,
            stickySessionCookieName, noFallbackEnabled, disableLoadBalancingForUserAgents);
  }

  /**
   * This method should only be used by older tests.
   * new tests should use the createServiceConfig(String serviceName, String config) method
   * @param serviceName
   * @param enabledValue
   * @param maxFailoverAttemptsValue
   * @param failoverSleepValue
   * @param zookeeperEnsemble
   * @param zookeeperNamespace
   * @param loadBalancingEnabledValue
   * @param stickySessionsEnabledValue
   * @param stickySessionCookieNameValue
   * @param noFallbackEnabledValue
   * @return
   */
   @Deprecated
   public static HaServiceConfig createServiceConfig(String serviceName, String enabledValue,
                                                     String maxFailoverAttemptsValue, String failoverSleepValue,
                                                     String zookeeperEnsemble, String zookeeperNamespace,
                                                     String loadBalancingEnabledValue, String stickySessionsEnabledValue,
                                                     String stickySessionCookieNameValue,
                                                     String noFallbackEnabledValue) {
      boolean enabled = DEFAULT_ENABLED;
      int maxFailoverAttempts = DEFAULT_MAX_FAILOVER_ATTEMPTS;
      int failoverSleep = DEFAULT_FAILOVER_SLEEP;
      boolean stickySessionsEnabled = DEFAULT_STICKY_SESSIONS_ENABLED;
      boolean loadBalancingEnabled = DEFAULT_LOAD_BALANCING_ENABLED;
      boolean noFallbackEnabled = DEFAULT_NO_FALLBACK_ENABLED;
      String stickySessionCookieName = DEFAULT_STICKY_SESSION_COOKIE_NAME;
      if (enabledValue != null && !enabledValue.trim().isEmpty()) {
         enabled = Boolean.parseBoolean(enabledValue);
      }
      if (maxFailoverAttemptsValue != null && !maxFailoverAttemptsValue.trim().isEmpty()) {
         maxFailoverAttempts = Integer.parseInt(maxFailoverAttemptsValue);
      }
      if (failoverSleepValue != null && !failoverSleepValue.trim().isEmpty()) {
         failoverSleep = Integer.parseInt(failoverSleepValue);
      }
      if (stickySessionsEnabledValue != null && !stickySessionsEnabledValue.trim().isEmpty()) {
         stickySessionsEnabled = Boolean.parseBoolean(stickySessionsEnabledValue);
      }
      if (loadBalancingEnabledValue != null && !loadBalancingEnabledValue.trim().isEmpty()) {
         loadBalancingEnabled = Boolean.parseBoolean(loadBalancingEnabledValue);
      }
      if (stickySessionCookieNameValue != null && !stickySessionCookieNameValue.trim().isEmpty()) {
         stickySessionCookieName = stickySessionCookieNameValue;
      }
      if (noFallbackEnabledValue != null && !noFallbackEnabledValue.trim().isEmpty()) {
         noFallbackEnabled = Boolean.parseBoolean(noFallbackEnabledValue);
      }

     return createServiceConfig(serviceName, enabled, maxFailoverAttempts, failoverSleep, zookeeperEnsemble, zookeeperNamespace, stickySessionsEnabled, loadBalancingEnabled,
             stickySessionCookieName, noFallbackEnabled, Arrays.asList(DEFAULT_DISABLE_LB_USER_AGENTS));
   }

  private static DefaultHaServiceConfig createServiceConfig(final String serviceName, final boolean enabled,
          final int maxFailoverAttempts, final int failoverSleepValue,
          final String zookeeperEnsemble, final String zookeeperNamespace,
          final boolean stickySessionsEnabled, final boolean loadBalancingEnabled,
          final String stickySessionCookieName,
          final boolean noFallbackEnabled, final List<String> disableStickySessionForUserAgents) {
    DefaultHaServiceConfig serviceConfig = new DefaultHaServiceConfig(serviceName);
    serviceConfig.setEnabled(enabled);
    serviceConfig.setMaxFailoverAttempts(maxFailoverAttempts);
    serviceConfig.setFailoverSleep(failoverSleepValue);
    serviceConfig.setZookeeperEnsemble(zookeeperEnsemble);
    serviceConfig.setZookeeperNamespace(zookeeperNamespace);
    serviceConfig.setStickySessionEnabled(stickySessionsEnabled);
    serviceConfig.setLoadBalancingEnabled(loadBalancingEnabled);
    serviceConfig.setStickySessionCookieName(stickySessionCookieName);
    serviceConfig.setNoFallbackEnabled(noFallbackEnabled);
    serviceConfig.setDisableStickySessionForUserAgents(disableStickySessionForUserAgents);
    return serviceConfig;
  }

   private static Map<String, String> parseHaConfiguration(String configuration) {
      Map<String, String> parameters = new HashMap<>();
      if (configuration != null) {
         String[] pairs = configuration.split(CONFIG_PAIRS_DELIMITER);
         for (String pair : pairs) {
            String[] tokens = pair.split(CONFIG_PAIR_DELIMITER);
            if (tokens.length == 2) {
               parameters.put(tokens[0], tokens[1]);
            }
         }
      }
      return parameters;
   }
}
