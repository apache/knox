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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.pac4j.core.client.Client;

public class Pac4jClientConfigurationDecorator implements ClientConfigurationDecorator {

  private static final List<ClientConfigurationDecorator> DEFAULT_DECORATORS = Arrays.asList(new SAML2ClientConfigurationDecorator(), new AzureADClientConfigurationDecorator());
  private final List<ClientConfigurationDecorator> decorators;

  public Pac4jClientConfigurationDecorator() {
    this(DEFAULT_DECORATORS);
  }

  // package protected so that it's visible in unit tests
  Pac4jClientConfigurationDecorator(List<ClientConfigurationDecorator> decorators) {
    this.decorators = decorators;
  }

  @Override
  public void decorateClients(List<Client> clients, Map<String, String> properties) {
    decorators.forEach(decorator -> decorator.decorateClients(clients, properties));
  }

}
