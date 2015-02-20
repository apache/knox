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
package org.apache.hadoop.gateway.util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.hadoop.gateway.GatewayMessages;
import org.apache.hadoop.gateway.deploy.ServiceDeploymentContributor;
import org.apache.hadoop.gateway.deploy.impl.ServiceDefinitionDeploymentContributor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRulesDescriptorFactory;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.service.definition.ServiceDefinition;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ServiceDefinitionsLoader {
  private static GatewayMessages log = MessagesFactory.get(GatewayMessages.class);

  private static String SERVICE_FILE_NAME = "service";

  private static String REWRITE_FILE = "rewrite.xml";

  public static Set<ServiceDeploymentContributor> loadServiceDefinitions(File servicesDir) {
    Set<ServiceDeploymentContributor> contributors = new HashSet<ServiceDeploymentContributor>();
    if ( servicesDir.exists() && servicesDir.isDirectory() ) {
      try {
        JAXBContext context = JAXBContext.newInstance(ServiceDefinition.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        Collection<File> files = FileUtils.listFiles(servicesDir, new IOFileFilter() {
          @Override
          public boolean accept(File file) {
            return file.getName().contains(SERVICE_FILE_NAME);
          }
          @Override
          public boolean accept(File dir, String name) {
            return name.contains(SERVICE_FILE_NAME);
          }
        }, TrueFileFilter.INSTANCE);
        for ( File file : files ) {
          try {
            FileInputStream inputStream = new FileInputStream(file);
            ServiceDefinition definition = (ServiceDefinition) unmarshaller.unmarshal(inputStream);
            //look for rewrite rules as a sibling (for now)
            UrlRewriteRulesDescriptor rewriteRulesDescriptor = loadRewriteRules(file.getParentFile());
            contributors.add(new ServiceDefinitionDeploymentContributor(definition, rewriteRulesDescriptor));
            log.addedServiceDefinition(definition.getName(), definition.getRole(), definition.getVersion());
          } catch ( FileNotFoundException e ) {
            log.failedToLoadServiceDefinition(file.getAbsolutePath(), e);
          }
        }
      } catch ( JAXBException e ) {
        log.failedToLoadServiceDefinition(SERVICE_FILE_NAME, e);
      }
    }
    return contributors;
  }

  private static UrlRewriteRulesDescriptor loadRewriteRules(File servicesDir) {
    File rewriteFile = new File(servicesDir, REWRITE_FILE);
    if ( rewriteFile.exists() ) {
      InputStream stream = null;
      try {
        stream = new FileInputStream(rewriteFile);
        Reader reader = new InputStreamReader(stream);
        UrlRewriteRulesDescriptor rules = UrlRewriteRulesDescriptorFactory.load(
            "xml", reader);
        reader.close();
        stream.close();
        return rules;
      } catch ( FileNotFoundException e ) {
        log.failedToLoadServiceDefinition(rewriteFile.getAbsolutePath(), e);
      } catch ( IOException e ) {
        log.failedToLoadServiceDefinition(rewriteFile.getAbsolutePath(), e);
      }
    }
    log.noRewriteFileFound(servicesDir.getAbsolutePath());
    return null;
  }
}
