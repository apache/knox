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
      String maxRetryAttempts = configMap.get(CONFIG_PARAM_MAX_RETRY_ATTEMPTS);
      String retrySleep = configMap.get(CONFIG_PARAM_RETRY_SLEEP);
      String zookeeperEnsemble = configMap.get(CONFIG_PARAM_ZOOKEEPER_ENSEMBLE);
      String zookeeperNamespace = configMap.get(CONFIG_PARAM_ZOOKEEPER_NAMESPACE);
      return createServiceConfig(serviceName, enabledValue, maxFailoverAttempts,
          failoverSleep, maxRetryAttempts, retrySleep,
          zookeeperEnsemble, zookeeperNamespace);
   }

   public static HaServiceConfig createServiceConfig(String serviceName, String enabledValue,
                                                     String maxFailoverAttemptsValue, String failoverSleepValue,
                                                     String maxRetryAttemptsValue, String retrySleepValue,
                                                     String zookeeperEnsemble, String zookeeperNamespace) {
      boolean enabled = DEFAULT_ENABLED;
      int maxFailoverAttempts = DEFAULT_MAX_FAILOVER_ATTEMPTS;
      int failoverSleep = DEFAULT_FAILOVER_SLEEP;
      int maxRetryAttempts = DEFAULT_MAX_RETRY_ATTEMPTS;
      int retrySleep = DEFAULT_RETRY_SLEEP;
      if (enabledValue != null && enabledValue.trim().length() > 0) {
         enabled = Boolean.parseBoolean(enabledValue);
      }
      if (maxFailoverAttemptsValue != null && maxFailoverAttemptsValue.trim().length() > 0) {
         maxFailoverAttempts = Integer.parseInt(maxFailoverAttemptsValue);
      }
      if (failoverSleepValue != null && failoverSleepValue.trim().length() > 0) {
         failoverSleep = Integer.parseInt(failoverSleepValue);
      }
      if (maxRetryAttemptsValue != null && maxRetryAttemptsValue.trim().length() > 0) {
         maxRetryAttempts = Integer.parseInt(maxRetryAttemptsValue);
      }
      if (retrySleepValue != null && retrySleepValue.trim().length() > 0) {
         retrySleep = Integer.parseInt(retrySleepValue);
      }
      DefaultHaServiceConfig serviceConfig = new DefaultHaServiceConfig(serviceName);
      serviceConfig.setEnabled(enabled);
      serviceConfig.setMaxFailoverAttempts(maxFailoverAttempts);
      serviceConfig.setFailoverSleep(failoverSleep);
      serviceConfig.setMaxRetryAttempts(maxRetryAttempts);
      serviceConfig.setRetrySleep(retrySleep);
      serviceConfig.setZookeeperEnsemble(zookeeperEnsemble);
      serviceConfig.setZookeeperNamespace(zookeeperNamespace);
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
