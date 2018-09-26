/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.services.registry.impl;

import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.service.definition.Route;
import org.apache.knox.gateway.service.definition.ServiceDefinition;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.registry.ServiceDefEntry;
import org.apache.knox.gateway.services.registry.ServiceDefinitionRegistry;
import org.apache.knox.gateway.util.ServiceDefinitionsLoader;
import org.apache.knox.gateway.util.urltemplate.Matcher;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Template;

import java.io.File;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultServiceDefinitionRegistry implements ServiceDefinitionRegistry, Service {

  private static GatewayMessages LOG = MessagesFactory.get( GatewayMessages.class );

  private Matcher<ServiceDefEntry> entries = new Matcher<>();

  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws
      ServiceLifecycleException {
    String stacks = config.getGatewayServicesDir();
    File stacksDir = new File(stacks);
    Set<ServiceDefinition> serviceDefinitions = ServiceDefinitionsLoader.getServiceDefinitions(stacksDir);

    for (ServiceDefinition serviceDefinition : serviceDefinitions) {
      List<Route> routes = serviceDefinition.getRoutes();
      for (Route route : routes) {
        try {
          Template template = Parser.parseTemplate(route.getPath());
          addServiceDefEntry(template, serviceDefinition);
        } catch ( URISyntaxException e ) {
          LOG.failedToParsePath(route.getPath(), e);
        }
      }
    }
  }

  private void addServiceDefEntry(Template template, ServiceDefinition serviceDefinition) {
    ServiceDefEntry entry = entries.get(template);
    if (entry == null) {
      entries.add(template, new DefaultServiceDefEntry(serviceDefinition.getRole(), serviceDefinition.getName(), template.getPattern()));
    }
  }

  @Override
  public void start() throws ServiceLifecycleException {

  }

  @Override
  public void stop() throws ServiceLifecycleException {

  }

  @Override
  public ServiceDefEntry getMatchingService(String urlPattern) {
    Matcher<ServiceDefEntry>.Match match = null;
    try {
      match = entries.match(Parser.parseLiteral(urlPattern));
    } catch ( URISyntaxException e ) {
      LOG.failedToParsePath(urlPattern, e);
    }
    if (match != null) {
      return match.getValue();
    }
    return null;
  }
}
