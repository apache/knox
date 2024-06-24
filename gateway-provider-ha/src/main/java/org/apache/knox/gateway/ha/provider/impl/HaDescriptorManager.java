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
import org.apache.knox.gateway.ha.provider.impl.i18n.HaMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.List;

public class HaDescriptorManager implements HaDescriptorConstants {

   private static final HaMessages LOG = MessagesFactory.get(HaMessages.class);

   public static void store(HaDescriptor descriptor, Writer writer) throws IOException {
      try {
         Document document = XmlUtils.createDocument();

         Element root = document.createElement(ROOT_ELEMENT);
         document.appendChild(root);

         List<HaServiceConfig> serviceConfigs = descriptor.getServiceConfigs();
         if (serviceConfigs != null && !serviceConfigs.isEmpty()) {
            for (HaServiceConfig config : serviceConfigs) {
               Element serviceElement = document.createElement(SERVICE_ELEMENT);
               serviceElement.setAttribute(SERVICE_NAME_ATTRIBUTE, config.getServiceName());
               serviceElement.setAttribute(MAX_FAILOVER_ATTEMPTS, Integer.toString(config.getMaxFailoverAttempts()));
               serviceElement.setAttribute(FAILOVER_SLEEP, Integer.toString(config.getFailoverSleep()));
               serviceElement.setAttribute(ENABLED_ATTRIBUTE, Boolean.toString(config.isEnabled()));
               if (config.getZookeeperEnsemble() != null) {
                 serviceElement.setAttribute(ZOOKEEPER_ENSEMBLE, config.getZookeeperEnsemble());
               }
               if (config.getZookeeperNamespace() != null) {
                 serviceElement.setAttribute(ZOOKEEPER_NAMESPACE, config.getZookeeperNamespace());
               }
               serviceElement.setAttribute(ENABLE_LOAD_BALANCING, Boolean.toString(config.isLoadBalancingEnabled()));
               serviceElement.setAttribute(ENABLE_STICKY_SESSIONS, Boolean.toString(config.isStickySessionEnabled()));
               serviceElement.setAttribute(ENABLE_NO_FALLBACK, Boolean.toString(config.isNoFallbackEnabled()));
               if (config.getStickySessionCookieName() != null) {
                 serviceElement.setAttribute(STICKY_SESSION_COOKIE_NAME, config.getStickySessionCookieName());
               }
               if(config.getStickySessionDisabledUserAgents() != null && !config.getStickySessionDisabledUserAgents().isEmpty()) {
                 serviceElement.setAttribute(DISABLE_LB_USER_AGENTS, config.getStickySessionDisabledUserAgents());
               }
               serviceElement.setAttribute(FAILOVER_NON_IDEMPOTENT, Boolean.toString(config.isFailoverNonIdempotentRequestEnabled()));
               serviceElement.setAttribute(USE_ROUTES_FOR_STICKY_COOKIE_PATH, Boolean.toString(config.isUseRoutesForStickyCookiePath()));
               root.appendChild(serviceElement);
            }
         }

         Transformer t = XmlUtils.getTransformer( true, true, 2, false );
         XmlUtils.writeXml( document, writer, t );
      } catch (ParserConfigurationException | TransformerException e) {
         LOG.failedToWriteHaDescriptor(e);
         throw new IOException(e);
      }
   }

   public static HaDescriptor load(InputStream inputStream) throws IOException {
      HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
      try {
         Document document = XmlUtils.readXml( inputStream );
         NodeList nodeList = document.getElementsByTagName(SERVICE_ELEMENT);
         if (nodeList != null && nodeList.getLength() > 0) {
            for (int i = 0; i < nodeList.getLength(); i++) {
               Element element = (Element) nodeList.item(i);
               HaServiceConfig config = HaDescriptorFactory.createServiceConfig(element.getAttribute(SERVICE_NAME_ATTRIBUTE),
                     element.getAttribute(ENABLED_ATTRIBUTE),
                     element.getAttribute(MAX_FAILOVER_ATTEMPTS),
                     element.getAttribute(FAILOVER_SLEEP),
                     element.getAttribute(ZOOKEEPER_ENSEMBLE),
                     element.getAttribute(ZOOKEEPER_NAMESPACE),
                     element.getAttribute(ENABLE_LOAD_BALANCING),
                     element.getAttribute(ENABLE_STICKY_SESSIONS),
                     element.getAttribute(STICKY_SESSION_COOKIE_NAME),
                     element.getAttribute(ENABLE_NO_FALLBACK),
                     element.getAttribute(DISABLE_LB_USER_AGENTS),
                     element.getAttribute(FAILOVER_NON_IDEMPOTENT),
                     element.getAttribute(USE_ROUTES_FOR_STICKY_COOKIE_PATH));
               descriptor.addServiceConfig(config);
            }
         }
      } catch (ParserConfigurationException | SAXException e) {
         LOG.failedToLoadHaDescriptor(e);
         throw new IOException(e);
      }
     return descriptor;
   }

}
