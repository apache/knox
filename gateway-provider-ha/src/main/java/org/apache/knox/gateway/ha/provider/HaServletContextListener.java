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
package org.apache.knox.gateway.ha.provider;

import org.apache.knox.gateway.ha.provider.impl.DefaultHaProvider;
import org.apache.knox.gateway.ha.provider.impl.HaDescriptorManager;
import org.apache.knox.gateway.ha.provider.impl.i18n.HaMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.registry.ServiceRegistry;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

public class HaServletContextListener implements ServletContextListener {

   public static final String PROVIDER_ATTRIBUTE_NAME = "haProvider";

   public static final String DESCRIPTOR_LOCATION_INIT_PARAM_NAME = "haDescriptorLocation";

   public static final String DESCRIPTOR_DEFAULT_FILE_NAME = "ha.xml";

   public static final String DESCRIPTOR_DEFAULT_LOCATION = "/WEB-INF/" + DESCRIPTOR_DEFAULT_FILE_NAME;

   private static final HaMessages LOG = MessagesFactory.get(HaMessages.class);


   @Override
   public void contextInitialized(ServletContextEvent event) {
      HaDescriptor descriptor;
      ServletContext servletContext = event.getServletContext();
      try {
         URL url = locateDescriptor(servletContext);
         descriptor = loadDescriptor(url);
      } catch (IOException e) {
         throw new IllegalStateException(e);
      }
      setupHaProvider(descriptor, servletContext);
   }

   @Override
   public void contextDestroyed(ServletContextEvent event) {
      event.getServletContext().removeAttribute(PROVIDER_ATTRIBUTE_NAME);
   }

   public static HaProvider getHaProvider(ServletContext context) {
      return (HaProvider) context.getAttribute(PROVIDER_ATTRIBUTE_NAME);
   }

   private void setupHaProvider(HaDescriptor descriptor, ServletContext servletContext) {
      GatewayServices services = (GatewayServices) servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
      String clusterName = (String) servletContext.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE);
      ServiceRegistry serviceRegistry = services.getService(GatewayServices.SERVICE_REGISTRY_SERVICE);
      HaProvider provider = new DefaultHaProvider(descriptor);
      List<String> serviceNames = descriptor.getEnabledServiceNames();
      for (String serviceName : serviceNames) {
         provider.addHaService(serviceName, serviceRegistry.lookupServiceURLs(clusterName, serviceName));
      }
      servletContext.setAttribute(PROVIDER_ATTRIBUTE_NAME, provider);
   }

   private static URL locateDescriptor(ServletContext context) throws IOException {
      String param = context.getInitParameter(DESCRIPTOR_LOCATION_INIT_PARAM_NAME);
      if (param == null) {
         param = DESCRIPTOR_DEFAULT_LOCATION;
      }
      URL url;
      try {
         url = context.getResource(param);
      } catch (MalformedURLException e) {
         // Ignore it and try using the value directly as a URL.
         url = null;
      }
      if (url == null) {
         url = new URL(param);
      }
      if (url == null) {
         throw new FileNotFoundException(param);
      }
      return url;
   }

   private static HaDescriptor loadDescriptor(URL url) throws IOException {
      InputStream stream = url.openStream();
      HaDescriptor descriptor = HaDescriptorManager.load(stream);
      try {
         stream.close();
      } catch (IOException e) {
         LOG.failedToLoadHaDescriptor(e);
      }
      return descriptor;
   }

}
