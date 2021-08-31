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

/**
 * The constants for xml elements and attributes are meant to help render/consume the following:
 * <pre>
 * {@code
 * <ha>
 * <service name='foo' failoverLimit='3' enabled='true'/>
 * </ha>
 * }
 * </pre>
 */
public interface HaDescriptorConstants {
   String ROOT_ELEMENT = "ha";

   String SERVICE_ELEMENT = "service";

   String SERVICE_NAME_ATTRIBUTE = "name";

   String MAX_FAILOVER_ATTEMPTS = "maxFailoverAttempts";

   String FAILOVER_SLEEP = "failoverSleep";

   String ENABLED_ATTRIBUTE = "enabled";

   String ZOOKEEPER_ENSEMBLE = "zookeeperEnsemble";

   String ZOOKEEPER_NAMESPACE = "zookeeperNamespace";

   String ENABLE_LOAD_BALANCING = "enableLoadBalancing";

   String ENABLE_STICKY_SESSIONS = "enableStickySession";

   String ENABLE_NO_FALLBACK = "noFallback";

   String STICKY_SESSION_COOKIE_NAME = "stickySessionCookieName";
}
