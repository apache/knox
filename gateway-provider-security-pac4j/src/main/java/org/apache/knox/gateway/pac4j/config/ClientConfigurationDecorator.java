/*
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
package org.apache.knox.gateway.pac4j.config;

import java.util.List;
import java.util.Map;

import org.pac4j.core.client.Client;

/**
 * Defines the contract of decorating different type of Pac4J client configurations
 */
public interface ClientConfigurationDecorator {

  /**
   * Decorates the given clients' configuration using the given properties (if applicable)
   *
   * @param clients
   *          the client, whose configuration should be decorated
   * @param properties
   *          the properties which may contain the required information to decorate the clients
   */
  void decorateClients(List<Client> clients, Map<String, String> properties);

}
