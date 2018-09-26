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

   public static final String CONFIG_PAIRS_DELIMITER = ";";

   public static final String CONFIG_PAIR_DELIMITER = "=";

   public static final String CONFIG_PARAM_MAX_FAILOVER_ATTEMPTS = "maxFailoverAttempts";

   public static final String CONFIG_PARAM_FAILOVER_SLEEP = "failoverSleep";

   public static final String CONFIG_PARAM_MAX_RETRY_ATTEMPTS = "maxRetryAttempts";

   public static final String CONFIG_PARAM_RETRY_SLEEP = "retrySleep";

   public static final String CONFIG_PARAM_ENABLED = "enabled";

   public static final String CONFIG_PARAM_ZOOKEEPER_ENSEMBLE = "zookeeperEnsemble";

   public static final String CONFIG_PARAM_ZOOKEEPER_NAMESPACE = "zookeeperNamespace";

   public static final int DEFAULT_MAX_FAILOVER_ATTEMPTS = 3;

   public static final int DEFAULT_FAILOVER_SLEEP = 1000;

   public static final int DEFAULT_MAX_RETRY_ATTEMPTS = 3;

   public static final int DEFAULT_RETRY_SLEEP = 1000;

   public static final boolean DEFAULT_ENABLED = true;

}
