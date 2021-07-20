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

public interface HaServiceConfigConstants {
   String CONFIG_PAIRS_DELIMITER = ";";

   String CONFIG_PAIR_DELIMITER = "=";

   String CONFIG_PARAM_MAX_FAILOVER_ATTEMPTS = "maxFailoverAttempts";

   String CONFIG_PARAM_FAILOVER_SLEEP = "failoverSleep";

   String CONFIG_PARAM_ENABLED = "enabled";

   String CONFIG_PARAM_ZOOKEEPER_ENSEMBLE = "zookeeperEnsemble";

   String CONFIG_PARAM_ZOOKEEPER_NAMESPACE = "zookeeperNamespace";

   String CONFIG_STICKY_SESSIONS_ENABLED = "enableStickySession";

   String CONFIG_LOAD_BALANCING_ENABLED = "enableLoadBalancing";

   String CONFIG_NO_FALLBACK_ENABLED = "noFallback";

   String STICKY_SESSION_COOKIE_NAME = "stickySessionCookieName";

   /**
    * Disable loadbalancing feature based on user agent.
    * The code will look for "contains" match
    */
   String DISABLE_LB_USER_AGENTS = "disableLoadBalancingForUserAgents";

   int DEFAULT_MAX_FAILOVER_ATTEMPTS = 3;

   int DEFAULT_FAILOVER_SLEEP = 1000;

   boolean DEFAULT_ENABLED = true;

   boolean DEFAULT_STICKY_SESSIONS_ENABLED = false;

   boolean DEFAULT_LOAD_BALANCING_ENABLED = false;

   boolean DEFAULT_NO_FALLBACK_ENABLED = false;

   String DEFAULT_STICKY_SESSION_COOKIE_NAME = "KNOX_BACKEND";

   String DEFAULT_DISABLE_LB_USER_AGENTS = "ClouderaODBCDriverforApacheHive";
}
