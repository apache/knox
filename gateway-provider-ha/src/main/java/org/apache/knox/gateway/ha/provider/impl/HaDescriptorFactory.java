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

import org.apache.knox.gateway.ha.provider.HaDescriptor;
import org.apache.knox.gateway.ha.provider.HaServiceConfig;

import java.util.HashMap;
import java.util.Map;

public abstract class HaDescriptorFactory implements HaServiceConfigConstants {

   public static HaDescriptor createDescriptor() {
      return new DefaultHaDescriptor();
   }

   public static HaServiceConfig createServiceConfig(String serviceName, String config) {
      Map<String, String> configMap = parseHaConfiguration(config);
      String enabledValue = configMap.get(CONFIG_PARAM_ENABLED);
      String maxFailoverAttempts = configMap.get(CONFIG_PARAM_MAX_FAILOVER_ATTEMPTS);
      String failoverSleep = configMap.get(CONFIG_PARAM_FAILOVER_SLEEP);
      String zookeeperEnsemble = configMap.get(CONFIG_PARAM_ZOOKEEPER_ENSEMBLE);
      String zookeeperNamespace = configMap.get(CONFIG_PARAM_ZOOKEEPER_NAMESPACE);
      String stickySessionEnabled = configMap.get(CONFIG_STICKY_SESSIONS_ENABLED);
      String loadBalancingEnabled = configMap.get(CONFIG_LOAD_BALANCING_ENABLED);
      String stickySessionCookieName = configMap.get(STICKY_SESSION_COOKIE_NAME);
      String noFallbackEnabled = configMap.get(CONFIG_NO_FALLBACK_ENABLED);
      return createServiceConfig(serviceName, enabledValue, maxFailoverAttempts, failoverSleep,
          zookeeperEnsemble, zookeeperNamespace, loadBalancingEnabled, stickySessionEnabled, stickySessionCookieName, noFallbackEnabled);
   }

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

      DefaultHaServiceConfig serviceConfig = new DefaultHaServiceConfig(serviceName);
      serviceConfig.setEnabled(enabled);
      serviceConfig.setMaxFailoverAttempts(maxFailoverAttempts);
      serviceConfig.setFailoverSleep(failoverSleep);
      serviceConfig.setZookeeperEnsemble(zookeeperEnsemble);
      serviceConfig.setZookeeperNamespace(zookeeperNamespace);
      serviceConfig.setStickySessionEnabled(stickySessionsEnabled);
      serviceConfig.setLoadBalancingEnabled(loadBalancingEnabled);
      serviceConfig.setStickySessionCookieName(stickySessionCookieName);
      serviceConfig.setNoFallbackEnabled(noFallbackEnabled);
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
