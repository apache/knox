/**
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
package org.apache.hadoop.gateway.ha.provider.impl;

import org.apache.hadoop.gateway.ha.provider.HaDescriptor;
import org.apache.hadoop.gateway.ha.provider.HaServiceConfig;
import org.apache.hadoop.gateway.ha.provider.impl.i18n.HaMessages;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.List;

public class HaDescriptorManager implements HaDescriptorConstants {

   private static final HaMessages LOG = MessagesFactory.get(HaMessages.class);

   public static void store(HaDescriptor descriptor, Writer writer) throws IOException {
      try {
         DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
         DocumentBuilder builder = builderFactory.newDocumentBuilder();
         Document document = builder.newDocument();
         document.setXmlStandalone(true);

         Element root = document.createElement(ROOT_ELEMENT);
         document.appendChild(root);

         List<HaServiceConfig> serviceConfigs = descriptor.getServiceConfigs();
         if (serviceConfigs != null && !serviceConfigs.isEmpty()) {
            for (HaServiceConfig config : serviceConfigs) {
               Element serviceElement = document.createElement(SERVICE_ELEMENT);
               serviceElement.setAttribute(SERVICE_NAME_ATTRIBUTE, config.getServiceName());
               serviceElement.setAttribute(MAX_FAILOVER_ATTEMPTS, Integer.toString(config.getMaxFailoverAttempts()));
               serviceElement.setAttribute(FAILOVER_SLEEP, Integer.toString(config.getFailoverSleep()));
               serviceElement.setAttribute(MAX_RETRY_ATTEMPTS, Integer.toString(config.getMaxRetryAttempts()));
               serviceElement.setAttribute(RETRY_SLEEP, Integer.toString(config.getRetrySleep()));
               serviceElement.setAttribute(ENABLED_ATTRIBUTE, Boolean.toString(config.isEnabled()));
               root.appendChild(serviceElement);
            }
         }

         TransformerFactory transformerFactory = TransformerFactory.newInstance();
         transformerFactory.setAttribute("indent-number", 2);
         Transformer transformer = transformerFactory.newTransformer();
         transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
         transformer.setOutputProperty(OutputKeys.INDENT, "yes");
         StreamResult result = new StreamResult(writer);
         DOMSource source = new DOMSource(document);
         transformer.transform(source, result);

      } catch (ParserConfigurationException e) {
         LOG.failedToWriteHaDescriptor(e);
         throw new IOException(e);
      } catch (TransformerException e) {
         LOG.failedToWriteHaDescriptor(e);
         throw new IOException(e);
      }
   }

   public static HaDescriptor load(InputStream inputStream) throws IOException {
      HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
      DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
      try {
         DocumentBuilder builder = builderFactory.newDocumentBuilder();
         Document document = builder.parse(inputStream);
         NodeList nodeList = document.getElementsByTagName(SERVICE_ELEMENT);
         if (nodeList != null && nodeList.getLength() > 0) {
            for (int i = 0; i < nodeList.getLength(); i++) {
               Element element = (Element) nodeList.item(i);
               HaServiceConfig config = HaDescriptorFactory.createServiceConfig(element.getAttribute(SERVICE_NAME_ATTRIBUTE),
                     element.getAttribute(ENABLED_ATTRIBUTE),
                     element.getAttribute(MAX_FAILOVER_ATTEMPTS),
                     element.getAttribute(FAILOVER_SLEEP),
                     element.getAttribute(MAX_RETRY_ATTEMPTS),
                     element.getAttribute(RETRY_SLEEP));
               descriptor.addServiceConfig(config);
            }
         }
      } catch (ParserConfigurationException e) {
         LOG.failedToLoadHaDescriptor(e);
         throw new IOException(e);
      } catch (SAXException e) {
         LOG.failedToLoadHaDescriptor(e);
         throw new IOException(e);
      }
      return descriptor;
   }

}
