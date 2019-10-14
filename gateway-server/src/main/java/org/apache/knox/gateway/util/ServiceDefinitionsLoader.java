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
package org.apache.knox.gateway.util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.deploy.ServiceDeploymentContributor;
import org.apache.knox.gateway.deploy.impl.ServiceDefinitionDeploymentContributor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptorFactory;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.service.definition.ServiceDefinition;
import org.apache.knox.gateway.service.definition.ServiceDefinitionComparator;
import org.apache.knox.gateway.service.definition.ServiceDefinitionPair;
import org.apache.knox.gateway.service.definition.ServiceDefinitionPairComparator;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class ServiceDefinitionsLoader {
  private static final JAXBContext jaxbContext = getJAXBContext();

  private static final GatewayMessages log = MessagesFactory.get(GatewayMessages.class);

  private static final String SERVICE_FILE_NAME = "service";

  private static final String REWRITE_FILE = "rewrite.xml";

  private static JAXBContext getJAXBContext() {
    try {
      return JAXBContext.newInstance(ServiceDefinition.class);
    } catch (JAXBException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Unmarshaller createUnmarshaller() {
    try {
      return jaxbContext.createUnmarshaller();
    } catch (JAXBException e) {
      throw new RuntimeException("Could not create unmarshaller", e);
    }
  }

  public static Set<ServiceDeploymentContributor> loadServiceDefinitionDeploymentContributors(File servicesDir) {
    final Set<ServiceDeploymentContributor> contributors = new HashSet<>();
    loadServiceDefinitions(servicesDir).forEach(
        serviceDefinitionPair -> contributors.add(new ServiceDefinitionDeploymentContributor(serviceDefinitionPair.getService(), serviceDefinitionPair.getRewriteRules())));
    return contributors;
  }

  public static Set<ServiceDefinitionPair> loadServiceDefinitions(File servicesDir) {
    final Set<ServiceDefinitionPair> serviceDefinitions = new TreeSet<>(new ServiceDefinitionPairComparator());
    if (servicesDir.exists() && servicesDir.isDirectory()) {
      final Unmarshaller unmarshaller = createUnmarshaller();
      getFileList(servicesDir).forEach(serviceFile -> {
        try {
          serviceDefinitions.add(loadServiceDefinition(unmarshaller, serviceFile));
        } catch (FileNotFoundException e) {
          log.failedToFindServiceDefinitionFile(serviceFile.getAbsolutePath(), e);
        } catch (IOException | JAXBException e) {
          log.failedToLoadServiceDefinition(serviceFile.getAbsolutePath(), e);
        }
      });
    }

    return serviceDefinitions;
  }

  private static ServiceDefinitionPair loadServiceDefinition(Unmarshaller unmarshaller, File serviceFile) throws IOException, JAXBException {
    try (InputStream inputStream = Files.newInputStream(serviceFile.toPath())) {
      final ServiceDefinition service = (ServiceDefinition) unmarshaller.unmarshal(serviceFile);
      final UrlRewriteRulesDescriptor rewriteRules = loadRewriteRules(serviceFile.getParentFile());
      return new ServiceDefinitionPair(service, rewriteRules);
    }
  }

  public static Set<ServiceDefinition> getServiceDefinitions(File servicesDir) {
    final Set<ServiceDefinition> services = new TreeSet<>(new ServiceDefinitionComparator());
    loadServiceDefinitions(servicesDir).forEach(serviceDefinitionPair -> services.add(serviceDefinitionPair.getService()));
    return services;
  }

  private static Collection<File> getFileList(File servicesDir) {
    Collection<File> files;
    if ( servicesDir.exists() && servicesDir.isDirectory() ) {
       files = FileUtils.listFiles(servicesDir, new IOFileFilter() {
        @Override
        public boolean accept(File file) {
          return acceptName(file.getName());
        }

        @Override
        public boolean accept(File dir, String name) {
          return acceptName(name);
        }

        private boolean acceptName(String name) {
          return name.contains(SERVICE_FILE_NAME) &&
              !name.startsWith(".") &&
              name.endsWith(".xml");
        }
      }, TrueFileFilter.INSTANCE);
    } else {
      files = new HashSet<>();
    }

    return files;
  }

  public static UrlRewriteRulesDescriptor loadRewriteRules(File servicesDir) {
    File rewriteFile = new File(servicesDir, REWRITE_FILE);
    if ( rewriteFile.exists() ) {
      try (InputStream stream = Files.newInputStream(rewriteFile.toPath());
           Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        return UrlRewriteRulesDescriptorFactory.load("xml", reader);
      } catch ( FileNotFoundException e ) {
        log.failedToFindRewriteFile(rewriteFile.getAbsolutePath(), e);
      } catch ( IOException e ) {
        log.failedToLoadRewriteFile(rewriteFile.getAbsolutePath(), e);
      }
    }
    log.noRewriteFileFound(servicesDir.getAbsolutePath());
    return null;
  }
}
