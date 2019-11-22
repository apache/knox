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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.newBufferedWriter;
import static org.apache.knox.gateway.deploy.impl.ApplicationDeploymentContributor.REWRITE_RULES_FILE_NAME;
import static org.apache.knox.gateway.deploy.impl.ApplicationDeploymentContributor.SERVICE_DEFINITION_FILE_NAME;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.knox.gateway.filter.rewrite.impl.xml.XmlUrlRewriteRulesExporter;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.service.definition.Route;
import org.apache.knox.gateway.service.definition.ServiceDefinition;
import org.apache.knox.gateway.service.definition.ServiceDefinitionChangeListener;
import org.apache.knox.gateway.service.definition.ServiceDefinitionPair;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.registry.ServiceDefEntry;
import org.apache.knox.gateway.services.registry.ServiceDefinitionRegistry;
import org.apache.knox.gateway.services.registry.ServiceDefinitionRegistryException;
import org.apache.knox.gateway.util.ServiceDefinitionsLoader;
import org.apache.knox.gateway.util.urltemplate.Matcher;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Template;

public class DefaultServiceDefinitionRegistry implements ServiceDefinitionRegistry, Service {

  private static GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);

  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Lock writeLock = lock.writeLock();
  private final Lock readLock = lock.readLock();
  private final Collection<ServiceDefinitionChangeListener> listeners = new HashSet<>();
  private final XmlUrlRewriteRulesExporter xmlRewriteRulesExporter = new XmlUrlRewriteRulesExporter();

  private GatewayConfig gatewayConfig;
  private Matcher<ServiceDefEntry> entries = new Matcher<>();
  private Set<ServiceDefinitionPair> serviceDefinitions;
  private JAXBContext jaxbContext;

  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
    this.gatewayConfig = config;
    writeLock.lock();
    try {
      populateServiceDefinitions();
    } finally {
      writeLock.unlock();
    }
  }

  private void populateServiceDefinitions() {
    serviceDefinitions = ServiceDefinitionsLoader.loadServiceDefinitions(new File(gatewayConfig.getGatewayServicesDir()));

    for (ServiceDefinition serviceDefinition : getServices()) {
      List<Route> routes = serviceDefinition.getRoutes();
      for (Route route : routes) {
        try {
          Template template = Parser.parseTemplate(route.getPath());
          addServiceDefEntry(template, serviceDefinition);
        } catch (URISyntaxException e) {
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
    readLock.lock();
    try {
      Matcher<ServiceDefEntry>.Match match = null;
      try {
        match = entries.match(Parser.parseLiteral(urlPattern));
      } catch (URISyntaxException e) {
        LOG.failedToParsePath(urlPattern, e);
      }
      if (match != null) {
        return match.getValue();
      }
      return null;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Set<ServiceDefinitionPair> getServiceDefinitions() {
    readLock.lock();
    try {
      return Collections.unmodifiableSet(serviceDefinitions);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public void saveServiceDefinition(ServiceDefinitionPair serviceDefinition) throws ServiceDefinitionRegistryException {
    saveOrUpdateServiceDefinition(serviceDefinition, false);
    LOG.savedServiceDefinitionChange(serviceDefinition.getService().getName(), serviceDefinition.getService().getRole(), serviceDefinition.getService().getVersion());
  }

  @Override
  public void saveOrUpdateServiceDefinition(ServiceDefinitionPair serviceDefinition) throws ServiceDefinitionRegistryException {
    saveOrUpdateServiceDefinition(serviceDefinition, true);
    LOG.updatedServiceDefinitionChange(serviceDefinition.getService().getName(), serviceDefinition.getService().getRole(), serviceDefinition.getService().getVersion());
  }

  public void saveOrUpdateServiceDefinition(ServiceDefinitionPair serviceDefinition, boolean allowUpdate) throws ServiceDefinitionRegistryException {
    final ServiceDefinition service = serviceDefinition.getService();
    final Optional<ServiceDefinition> persistedServiceDefinition = findServiceDefinition(service.getName(), service.getRole(), service.getVersion());
    if (persistedServiceDefinition.isPresent() && !allowUpdate) {
      throw new ServiceDefinitionRegistryException("The requested service definition (" + serviceDefinition.toString() + ") already exists!");
    } else {
      writeLock.lock();
      try {
        final Path serviceDefinitionFolderPath = createServiceDefinitionFolders(service.getName(), service.getVersion());
        writeOutServiceDefinitionFile(service, serviceDefinitionFolderPath);
        if (serviceDefinition.getRewriteRules() != null) {
          writeOutRewriteRules(serviceDefinition.getRewriteRules(), serviceDefinitionFolderPath);
        }
        populateServiceDefinitions();
      } catch (JAXBException | IOException e) {
        throw new ServiceDefinitionRegistryException("Error while persisting service definition " + serviceDefinition.toString(), e);
      } finally {
        writeLock.unlock();
      }
      notifyListeners(service.getName(), service.getRole(), service.getVersion());
    }
  }

  private Path createServiceDefinitionFolders(String name, String version) throws IOException {
    final Path serviceDefinitionPath = Paths.get(gatewayConfig.getGatewayServicesDir(), name, version);
    return Files.createDirectories(serviceDefinitionPath);
  }

  private void writeOutServiceDefinitionFile(ServiceDefinition serviceDefinition, Path serviceDefinitionFolderPath) throws JAXBException, IOException {
    final Marshaller marshaller = getJaxbContext().createMarshaller();
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
    try (BufferedWriter serviceDefinitionXmlFileWriter = newBufferedWriter(Paths.get(serviceDefinitionFolderPath.toAbsolutePath().toString(), SERVICE_DEFINITION_FILE_NAME),
        UTF_8);) {
      marshaller.marshal(serviceDefinition, serviceDefinitionXmlFileWriter);
    }
  }

  private void writeOutRewriteRules(UrlRewriteRulesDescriptor rewriteRules, Path serviceDefinitionFolderPath) throws IOException {
    try (BufferedWriter rewriteRuleXmlFileWriter = newBufferedWriter(Paths.get(serviceDefinitionFolderPath.toAbsolutePath().toString(), REWRITE_RULES_FILE_NAME), UTF_8);) {
      xmlRewriteRulesExporter.store(rewriteRules, rewriteRuleXmlFileWriter, true);
    }
  }

  private JAXBContext getJaxbContext() throws JAXBException {
    if (jaxbContext == null) {
      jaxbContext = JAXBContext.newInstance(ServiceDefinition.class);
    }
    return jaxbContext;
  }

  @Override
  public void deleteServiceDefinition(String name, String role, String version) throws ServiceDefinitionRegistryException {
    final Optional<ServiceDefinition> serviceDefinition = findServiceDefinition(name, role, version);
    if (serviceDefinition.isPresent()) {
      writeLock.lock();
      try {
        removeServiceDefinitionFolders(name, version);
        populateServiceDefinitions();
      } catch (IOException e) {
        throw new ServiceDefinitionRegistryException("Error while deleting service definition " + serviceDefinition.toString(), e);
      } finally {
        writeLock.unlock();
      }
      notifyListeners(name, role, version);
      LOG.deletedServiceDefinitionChange(name, role, version);
    } else {
      throw new ServiceDefinitionRegistryException("There is no service definition with the given attributes: " + name + "," + role + "," + version);
    }
  }

  private Set<ServiceDefinition> getServices() {
    return serviceDefinitions.stream().map(serviceDefinitionPair -> serviceDefinitionPair.getService()).collect(Collectors.toSet());
  }

  private Optional<ServiceDefinition> findServiceDefinition(String name, String role, String version) {
    return getServices().stream().filter(sd -> sd.getName().equalsIgnoreCase(name) && sd.getRole().equalsIgnoreCase(role) && sd.getVersion().equalsIgnoreCase(version)).findFirst();
  }

  private void removeServiceDefinitionFolders(String name, String version) throws IOException {
    // removing the given version folder and its content
    final Path serviceDefinitionPath = Paths.get(gatewayConfig.getGatewayServicesDir(), name, version);
    Files.walkFileTree(serviceDefinitionPath, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }
    });

    // if there was no more versions for the given service the service folder can be
    // removed too
    final Path serviceFolderPath = Paths.get(gatewayConfig.getGatewayServicesDir(), name).toAbsolutePath();
    final File serviceFolder = serviceFolderPath.toFile();
    if (serviceFolder.isDirectory() && serviceFolder.listFiles().length == 0) {
      Files.delete(serviceFolderPath);
    }
  }

  @Override
  public void addServiceDefinitionChangeListener(ServiceDefinitionChangeListener listener) {
    listeners.add(listener);
  }

  private void notifyListeners(String name, String role, String version) {
    listeners.forEach(listener -> listener.onServiceDefinitionChange(name, role, version));
  }
}
