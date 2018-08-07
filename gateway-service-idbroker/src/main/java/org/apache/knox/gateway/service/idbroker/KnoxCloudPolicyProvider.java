/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.service.idbroker;

import java.util.Properties;

import javax.security.auth.Subject;

public interface KnoxCloudPolicyProvider {

  /**
   * initialize config provider with the context from the topology
   * params that are relevant to the particular config provider
   * @param context
   */
  void init(Properties context);

  /**
   * Name of the specific provider implementation to be resolved
   * by the KnoxCloudPolicyProviderFactory via ServiceLoader and the name
   * configured within the topology.
   * @return
   */
  String getName();

  /**
   * Get the string representation of the cloud specific policy when appropriate.
   * Some implementations may NOP this method as they may not support external policy
   * filtering or management. They may also return a parseable string that represents
   * configuration for specific cloud vendor client.
   * @param username
   * @param subject
   * @return
   */
  String getPolicy(String username, Subject subject);
}