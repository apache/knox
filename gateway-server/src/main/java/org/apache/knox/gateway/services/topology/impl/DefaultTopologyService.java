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
package org.apache.knox.gateway.services.topology.impl;

import org.apache.commons.digester3.Digester;
import org.apache.commons.digester3.binder.DigesterLoader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.GatewayServer;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.service.definition.ServiceDefinition;
import org.apache.knox.gateway.service.definition.ServiceDefinitionChangeListener;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClient;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.ClusterConfigurationMonitorService;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.topology.TopologyEvent;
import org.apache.knox.gateway.topology.TopologyListener;
import org.apache.knox.gateway.topology.TopologyMonitor;
import org.apache.knox.gateway.topology.TopologyProvider;
import org.apache.knox.gateway.topology.Version;
import org.apache.knox.gateway.topology.builder.TopologyBuilder;
import org.apache.knox.gateway.topology.discovery.ClusterConfigurationMonitor;
import org.apache.knox.gateway.topology.discovery.ServiceDiscovery;
import org.apache.knox.gateway.topology.monitor.RemoteConfigurationMonitor;
import org.apache.knox.gateway.topology.monitor.RemoteConfigurationMonitorFactory;
import org.apache.knox.gateway.topology.simple.ProviderConfigurationParser;
import org.apache.knox.gateway.topology.simple.SimpleDescriptor;
import org.apache.knox.gateway.topology.simple.SimpleDescriptorFactory;
import org.apache.knox.gateway.topology.simple.SimpleDescriptorHandler;
import org.apache.knox.gateway.topology.validation.TopologyValidator;
import org.apache.knox.gateway.topology.xml.AmbariFormatXmlTopologyRules;
import org.apache.knox.gateway.topology.xml.KnoxFormatXmlTopologyRules;
import org.apache.knox.gateway.util.ServiceDefinitionsLoader;
import org.eclipse.persistence.jaxb.JAXBContextProperties;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static org.apache.commons.digester3.binder.DigesterLoader.newLoader;

public class DefaultTopologyService
    extends FileAlterationListenerAdaptor
    implements TopologyService, TopologyMonitor, TopologyProvider, FileFilter, FileAlterationListener, ServiceDefinitionChangeListener {

  private static final JAXBContext jaxbContext = getJAXBContext();

  private static Auditor auditor = AuditServiceFactory.getAuditService().getAuditor(
    AuditConstants.DEFAULT_AUDITOR_NAME, AuditConstants.KNOX_SERVICE_NAME,
    AuditConstants.KNOX_COMPONENT_NAME);

  private static final List<String> SUPPORTED_TOPOLOGY_FILE_EXTENSIONS = new ArrayList<>();
  static {
    SUPPORTED_TOPOLOGY_FILE_EXTENSIONS.add("xml");
    SUPPORTED_TOPOLOGY_FILE_EXTENSIONS.add("conf");
  }

  private static GatewayMessages log = MessagesFactory.get(GatewayMessages.class);
  private static DigesterLoader digesterLoader = newLoader(new KnoxFormatXmlTopologyRules(), new AmbariFormatXmlTopologyRules());
  private List<FileAlterationMonitor> monitors = new ArrayList<>();
  private File topologiesDirectory;
  private File sharedProvidersDirectory;
  private File descriptorsDirectory;

  private DescriptorsMonitor descriptorsMonitor;

  private Set<TopologyListener> listeners;
  private Map<File, Topology> topologies;
  private AliasService aliasService;

  private RemoteConfigurationMonitor remoteMonitor;

  private GatewayConfig config;

  private static JAXBContext getJAXBContext() {
    String pkgName = Topology.class.getPackage().getName();
    String bindingFile = pkgName.replace(".", "/") + "/topology_binding-xml.xml";

    Map<String, Object> properties = new HashMap<>(1);
    properties.put(JAXBContextProperties.OXM_METADATA_SOURCE, bindingFile);
    try {
      return JAXBContext.newInstance(pkgName, Topology.class.getClassLoader(), properties);
    } catch (JAXBException e) {
      throw new IllegalStateException(e);
    }
  }

  private Topology loadTopology(File file) throws IOException, SAXException, URISyntaxException, InterruptedException {
    final long TIMEOUT = 250; //ms
    final long DELAY = 50; //ms
    log.loadingTopologyFile(file.getAbsolutePath());
    Topology topology;
    long start = System.currentTimeMillis();
    while (true) {
      try {
        topology = loadTopologyAttempt(file);
        break;
      } catch (IOException | SAXException e) {
        if (System.currentTimeMillis() - start < TIMEOUT) {
          log.failedToLoadTopologyRetrying(file.getAbsolutePath(), Long.toString(DELAY), e);
          Thread.sleep(DELAY);
        } else {
          throw e;
        }
      }
    }
    return topology;
  }

  private Topology loadTopologyAttempt(File file) throws IOException, SAXException, URISyntaxException {
    Topology topology;
    Digester digester = digesterLoader.newDigester();
    TopologyBuilder topologyBuilder = digester.parse(FileUtils.openInputStream(file));
    if (null == topologyBuilder) {
      return null;
    }
    topology = topologyBuilder.build();
    topology.setUri(file.toURI());
    topology.setName(FilenameUtils.removeExtension(file.getName()));
    topology.setTimestamp(file.lastModified());
    return topology;
  }

  private void redeployTopology(Topology topology) {
    File topologyFile = new File(topology.getUri());
    try {
      TopologyValidator tv = new TopologyValidator(topology);

      if(!tv.validateTopology()) {
        if(config != null && config.isTopologyValidationEnabled()) {
          /* If strict validation enabled we fail */
          throw new SAXException(tv.getErrorString());
        } else {
          /* Log and move on */
          log.failedToValidateTopology(topology.getName(), tv.getErrorString());
        }
      }

      long start = System.currentTimeMillis();
      long limit = 1000L; // One second.
      long elapsed = 1;
      while (elapsed <= limit) {
        try {
          long origTimestamp = topologyFile.lastModified();
          long setTimestamp = Math.max(System.currentTimeMillis(), topologyFile.lastModified() + elapsed);
          if(topologyFile.setLastModified(setTimestamp)) {
            long newTimstamp = topologyFile.lastModified();
            if(newTimstamp > origTimestamp) {
              break;
            } else {
              Thread.sleep(10);
              elapsed = System.currentTimeMillis() - start;
              continue;
            }
          } else {
            auditor.audit(Action.REDEPLOY, topology.getName(), ResourceType.TOPOLOGY,
                ActionOutcome.FAILURE);
            log.failedToRedeployTopology(topology.getName());
            break;
          }
        } catch (InterruptedException e) {
          auditor.audit(Action.REDEPLOY, topology.getName(), ResourceType.TOPOLOGY,
              ActionOutcome.FAILURE);
          log.failedToRedeployTopology(topology.getName(), e);
          e.printStackTrace();
        }
      }
    } catch (SAXException e) {
      auditor.audit(Action.REDEPLOY, topology.getName(), ResourceType.TOPOLOGY, ActionOutcome.FAILURE);
      log.failedToRedeployTopology(topology.getName(), e);
    }
  }

  private List<TopologyEvent> createChangeEvents(
      Map<File, Topology> oldTopologies,
      Map<File, Topology> newTopologies) {
    ArrayList<TopologyEvent> events = new ArrayList<>();
    // Go through the old topologies and find anything that was deleted.
    for (Entry<File, Topology> oldTopology : oldTopologies.entrySet()) {
      if (!newTopologies.containsKey(oldTopology.getKey())) {
        events.add(new TopologyEvent(TopologyEvent.Type.DELETED, oldTopology.getValue()));
      }
    }
    // Go through the new topologies and figure out what was updated vs added.
    for (Entry<File, Topology> newTopology : newTopologies.entrySet()) {
      if (oldTopologies.containsKey(newTopology.getKey())) {
        Topology oldTopology = oldTopologies.get(newTopology.getKey());
        if (newTopology.getValue().getTimestamp() > oldTopology.getTimestamp()) {
          events.add(new TopologyEvent(TopologyEvent.Type.UPDATED, newTopology.getValue()));
        }
      } else {
        events.add(new TopologyEvent(TopologyEvent.Type.CREATED, newTopology.getValue()));
      }
    }
    return events;
  }

  private File calculateAbsoluteTopologiesDir(GatewayConfig config) {
    File topoDir = new File(config.getGatewayTopologyDir());
    topoDir = topoDir.getAbsoluteFile();
    return topoDir;
  }

  private File calculateAbsoluteConfigDir(GatewayConfig config) {
    File configDir;

    String path = config.getGatewayConfDir();
    configDir = (path != null) ? new File(path) : (new File(config.getGatewayTopologyDir())).getParentFile();

    return configDir.getAbsoluteFile();
  }

  private void  initListener(FileAlterationMonitor  monitor,
                            File                   directory,
                            FileFilter             filter,
                            FileAlterationListener listener) {
    monitors.add(monitor);
    FileAlterationObserver observer = new FileAlterationObserver(directory, filter);
    observer.addListener(listener);
    monitor.addObserver(observer);
  }

  private void initListener(File directory, FileFilter filter, FileAlterationListener listener) throws IOException, SAXException {
    // Increasing the monitoring interval to 5 seconds as profiling has shown
    // this is rather expensive in terms of generated garbage objects.
    initListener(new FileAlterationMonitor(5000L), directory, filter, listener);
  }

  private Map<File, Topology> loadTopologies(File directory) {
    Map<File, Topology> map = new HashMap<>();
    if (directory.isDirectory() && directory.canRead()) {
      File[] existingTopologies = directory.listFiles(this);
      if (existingTopologies != null) {
        for (File file : existingTopologies) {
          try {
            Topology loadTopology = loadTopology(file);
            if (null != loadTopology) {
              map.put(file, loadTopology);
            } else {
              auditor.audit(Action.LOAD, file.getAbsolutePath(), ResourceType.TOPOLOGY,
                      ActionOutcome.FAILURE);
              log.failedToLoadTopology(file.getAbsolutePath());
            }
          } catch (Exception e) {
            // Maybe it makes sense to throw exception
            auditor.audit(Action.LOAD, file.getAbsolutePath(), ResourceType.TOPOLOGY,
                    ActionOutcome.FAILURE);
            log.failedToLoadTopology(file.getAbsolutePath(), e);
          }
        }
      }
    }
    return map;
  }

  public void setAliasService(AliasService as) {
    this.aliasService = as;
  }

  @Override
  public void deployTopology(Topology t){

    try {
      File temp = new File(topologiesDirectory.getAbsolutePath() + "/" + t.getName() + ".xml.temp");
      Marshaller mr = jaxbContext.createMarshaller();

      mr.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
      mr.marshal(t, temp);

      File topology = new File(topologiesDirectory.getAbsolutePath() + "/" + t.getName() + ".xml");
      if(!temp.renameTo(topology)) {
        FileUtils.forceDelete(temp);
        throw new IOException("Could not rename temp file");
      }

      // This code will check if the topology is valid, and retrieve the errors if it is not.
      TopologyValidator validator = new TopologyValidator( topology.getAbsolutePath() );
      if( !validator.validateTopology() ){
        throw new SAXException( validator.getErrorString() );
      }


    } catch (JAXBException | SAXException | IOException e) {
      auditor.audit(Action.DEPLOY, t.getName(), ResourceType.TOPOLOGY, ActionOutcome.FAILURE);
      log.failedToDeployTopology(t.getName(), e);
    }
    reloadTopologies();
  }

  @Override
  public void redeployTopologies(String topologyName) {

    for (Topology topology : getTopologies()) {
      if (topologyName == null || topologyName.equals(topology.getName())) {
        redeployTopology(topology);
      }
    }

  }

  @Override
  public void reloadTopologies() {
    try {
      synchronized (this) {
        Map<File, Topology> oldTopologies = topologies;
        Map<File, Topology> newTopologies = loadTopologies(topologiesDirectory);
        List<TopologyEvent> events = createChangeEvents(oldTopologies, newTopologies);
        topologies = newTopologies;
        notifyChangeListeners(events);
      }
    } catch (Exception e) {
      // Maybe it makes sense to throw exception
      log.failedToReloadTopologies(e);
    }
  }

  @Override
  public void deleteTopology(Topology t) {
    File topoDir = topologiesDirectory;

    if(topoDir.isDirectory() && topoDir.canRead()) {
      for (File f : listFiles(topoDir)) {
        String fName = FilenameUtils.getBaseName(f.getName());
        if(fName.equals(t.getName())) {
          f.delete();
        }
      }
    }
    reloadTopologies();
  }

  private void notifyChangeListeners(List<TopologyEvent> events) {
    for (TopologyListener listener : listeners) {
      try {
        listener.handleTopologyEvent(events);
      } catch (RuntimeException e) {
        auditor.audit(Action.LOAD, "Topology_Event", ResourceType.TOPOLOGY, ActionOutcome.FAILURE);
        log.failedToHandleTopologyEvents(e);
      }
    }
  }

  @Override
  public Map<String, List<String>> getServiceTestURLs(Topology t, GatewayConfig config) {
    File tFile = null;
    Map<String, List<String>> urls = new HashMap<>();
    if (topologiesDirectory.isDirectory() && topologiesDirectory.canRead()) {
      for (File f : listFiles(topologiesDirectory)) {
        if (FilenameUtils.removeExtension(f.getName()).equals(t.getName())) {
          tFile = f;
        }
      }
    }
    Set<ServiceDefinition> defs;
    if(tFile != null) {
      defs = ServiceDefinitionsLoader.getServiceDefinitions(new File(config.getGatewayServicesDir()));

      for(ServiceDefinition def : defs) {
        urls.put(def.getRole(), def.getTestURLs());
      }
    }
    return urls;
  }

  @Override
  public Collection<Topology> getTopologies() {
    Map<File, Topology> map = topologies;
    return Collections.unmodifiableCollection(map.values());
  }

  @Override
  public boolean deployProviderConfiguration(String name, String content) {
    boolean result;

    // Whether the remote configuration registry is being employed or not, write the file locally
    result =  writeConfig(sharedProvidersDirectory, name, content);

    // If the remote configuration registry is being employed, persist it there also
    if (remoteMonitor != null) {
      RemoteConfigurationRegistryClient client = remoteMonitor.getClient();
      if (client != null) {
        String entryPath = "/knox/config/shared-providers/" + name;
        client.createEntry(entryPath, content);
        result = (client.getEntryData(entryPath) != null);
      }
    }

    return result;
  }

  @Override
  public Collection<File> getProviderConfigurations() {
    List<File> providerConfigs = new ArrayList<>();
    for (File providerConfig : listFiles(sharedProvidersDirectory)) {
      if (SharedProviderConfigMonitor.SUPPORTED_EXTENSIONS.contains(FilenameUtils.getExtension(providerConfig.getName()))) {
        providerConfigs.add(providerConfig);
      }
    }
    return providerConfigs;
  }

  @Override
  public boolean deleteProviderConfiguration(String name) {
    return deleteProviderConfiguration(name, false);
  }

  @Override
  public boolean deleteProviderConfiguration(String name, boolean force) {
    boolean result = false;

    // Determine if the file exists, and if so, if there are any descriptors referencing it
    boolean hasReferences = false;
    File providerConfig = getExistingFile(sharedProvidersDirectory, name);
    if (providerConfig != null) {
      List<String> references = descriptorsMonitor.getReferencingDescriptors(providerConfig.getAbsolutePath());
      hasReferences = !references.isEmpty();
    } else {
      result = true; // If it already does NOT exist, then the delete effectively succeeded
    }

    // If the local file does not exist, or it does exist and there are NOT any referencing descriptors
    if (force || (providerConfig == null || !hasReferences)) {

      // If the remote config monitor is configured, attempt to delete the provider configuration from the remote
      // registry, even if it does not exist locally.
      deleteRemoteEntry("/knox/config/shared-providers", name);

      // Whether the remote configuration registry is being employed or not, delete the local file if it exists
      result = providerConfig == null || !providerConfig.exists() || providerConfig.delete();

    } else {
      log.preventedDeletionOfSharedProviderConfiguration(providerConfig.getAbsolutePath());
    }

    return result;
  }

  @Override
  public boolean deployDescriptor(String name, String content) {
    boolean result;

    // Whether the remote configuration registry is being employed or not, write the file locally
    result = writeConfig(descriptorsDirectory, name, content);

    // If the remote configuration registry is being employed, persist it there also
    if (remoteMonitor != null) {
      RemoteConfigurationRegistryClient client = remoteMonitor.getClient();
      if (client != null) {
        String entryPath = "/knox/config/descriptors/" + name;
        client.createEntry(entryPath, content);
        result = (client.getEntryData(entryPath) != null);
      }
    }

    return result;
  }

  @Override
  public Collection<File> getDescriptors() {
    List<File> descriptors = new ArrayList<>();
    for (File descriptor : listFiles(descriptorsDirectory)) {
      if (DescriptorsMonitor.SUPPORTED_EXTENSIONS.contains(FilenameUtils.getExtension(descriptor.getName()))) {
        descriptors.add(descriptor);
      }
    }
    return descriptors;
  }

  @Override
  public boolean deleteDescriptor(String name) {
    boolean result;

    // If the remote config monitor is configured, delete the descriptor from the remote registry
    deleteRemoteEntry("/knox/config/descriptors", name);

    // Whether the remote configuration registry is being employed or not, delete the local file
    File descriptor = getExistingFile(descriptorsDirectory, name);
    result = (descriptor == null) || descriptor.delete();

    return result;
  }

  @Override
  public void addTopologyChangeListener(TopologyListener listener) {
    listeners.add(listener);
  }

  @Override
  public void onServiceDefinitionChange(String name, String role, String version) {
    getTopologies().stream().filter(topology -> topology.getServices().stream().anyMatch(service -> isRelevantService(service, role, name, version))).forEach(topology -> {
      log.redeployingTopologyOnServiceDefinitionChange(topology.getName(), name, role, version);
      redeployTopology(topology);
    });
  }

  private boolean isRelevantService(Service service, String role, String name, String version) {
    return service.getRole().equalsIgnoreCase(role)
        && (service.getName() == null || service.getName().equalsIgnoreCase(name) && (service.getVersion() == null || service.getVersion().equals(new Version(version))));
  }

  @Override
  public void startMonitor() throws Exception {
    // Start the local configuration monitors
    for (FileAlterationMonitor monitor : monitors) {
      monitor.start();
    }

    // Start the remote configuration monitor, if it has been initialized
    if (remoteMonitor != null) {
      try {
        remoteMonitor.start();
      } catch (Exception e) {
        log.remoteConfigurationMonitorStartFailure(remoteMonitor.getClass().getTypeName(), e.getLocalizedMessage());
      }
    }
  }

  @Override
  public void stopMonitor() throws Exception {
    // Stop the local configuration monitors
    for (FileAlterationMonitor monitor : monitors) {
      monitor.stop();
    }

    // Stop the remote configuration monitor, if it has been initialized
    if (remoteMonitor != null) {
      remoteMonitor.stop();
    }
  }

  @Override
  public boolean accept(File file) {
    boolean accept = false;
    if (!file.isDirectory() && file.canRead()) {
      String extension = FilenameUtils.getExtension(file.getName());
      if (SUPPORTED_TOPOLOGY_FILE_EXTENSIONS.contains(extension)) {
        accept = true;
      }
    }
    return accept;
  }

  @Override
  public void onFileCreate(File file) {
    onFileChange(file);
  }

  @Override
  public void onFileDelete(java.io.File file) {
    onFileChange(file);
  }

  @Override
  public void onFileChange(File file) {
    reloadTopologies();
  }

  @Override
  public void stop() {

  }

  @Override
  public void start() {
    // Register a cluster configuration monitor listener for change notifications
    ClusterConfigurationMonitorService ccms =
                  GatewayServer.getGatewayServices().getService(ServiceType.CLUSTER_CONFIGURATION_MONITOR_SERVICE);
    ccms.addListener(new TopologyDiscoveryTrigger(this, ccms));
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {

    this.config = config;
    String gatewayConfDir = config.getGatewayConfDir();
    if (gatewayConfDir != null) {
      System.setProperty(ServiceDiscovery.CONFIG_DIR_PROPERTY, gatewayConfDir);
    }

    try {
      listeners  = new HashSet<>();
      topologies = new HashMap<>();

      topologiesDirectory = calculateAbsoluteTopologiesDir(config);

      File configDirectory = calculateAbsoluteConfigDir(config);
      descriptorsDirectory = new File(configDirectory, "descriptors");
      sharedProvidersDirectory = new File(configDirectory, "shared-providers");

      // Add support for conf/topologies
      initListener(topologiesDirectory, this, this);

      // Add support for conf/descriptors
      descriptorsMonitor = new DescriptorsMonitor(config, topologiesDirectory, aliasService);
      initListener(descriptorsDirectory,
                   descriptorsMonitor,
                   descriptorsMonitor);
      log.monitoringDescriptorChangesInDirectory(descriptorsDirectory.getAbsolutePath());

      // Add support for conf/shared-providers
      SharedProviderConfigMonitor spm = new SharedProviderConfigMonitor(descriptorsMonitor, descriptorsDirectory);
      initListener(sharedProvidersDirectory, spm, spm);
      log.monitoringProviderConfigChangesInDirectory(sharedProvidersDirectory.getAbsolutePath());

      // For all the descriptors currently in the descriptors dir at start-up time, determine if topology regeneration
      // is required.
      // This happens prior to the start-up loading of the topologies.
      String[] descriptorFilenames =  descriptorsDirectory.list();
      if (descriptorFilenames != null) {
        for (String descriptorFilename : descriptorFilenames) {
          if (DescriptorsMonitor.isDescriptorFile(descriptorFilename)) {
            String topologyName = FilenameUtils.getBaseName(descriptorFilename);
            File existingDescriptorFile = getExistingFile(descriptorsDirectory, topologyName);

            // If there isn't a corresponding topology file, or if the descriptor has been modified since the
            // corresponding topology file was generated, then trigger generation of one
            File matchingTopologyFile = getExistingFile(topologiesDirectory, topologyName);
            if (matchingTopologyFile == null || matchingTopologyFile.lastModified() < existingDescriptorFile.lastModified()) {
              descriptorsMonitor.onFileChange(existingDescriptorFile);
            } else {
              // If regeneration is NOT required, then we at least need to report the provider configuration
              // reference relationship (KNOX-1144)
              String normalizedDescriptorPath = FilenameUtils.normalize(existingDescriptorFile.getAbsolutePath());

              // Parse the descriptor to determine the provider config reference
              SimpleDescriptor sd = SimpleDescriptorFactory.parse(normalizedDescriptorPath);
              if (sd != null) {
                File referencedProviderConfig =
                           getExistingFile(sharedProvidersDirectory, FilenameUtils.getBaseName(sd.getProviderConfig()));
                if (referencedProviderConfig != null) {
                  List<String> references =
                         descriptorsMonitor.getReferencingDescriptors(referencedProviderConfig.getAbsolutePath());
                  if (!references.contains(normalizedDescriptorPath)) {
                    references.add(normalizedDescriptorPath);
                  }
                }
              }
            }
          }
        }
      }

      // Initialize the remote configuration monitor, if it has been configured
      remoteMonitor = RemoteConfigurationMonitorFactory.get(config);

    } catch (IOException | SAXException io) {
      throw new ServiceLifecycleException(io.getMessage());
    }
  }

  /**
   * Delete the entry in the remote configuration registry, which matches the specified resource name.
   *
   * @param entryParent The remote registry path in which the entry exists.
   * @param name        The name of the entry (typically without any file extension).
   *
   * @return true, if the entry is deleted, or did not exist; otherwise, false.
   */
  private boolean deleteRemoteEntry(String entryParent, String name) {
    boolean result = true;

    if (remoteMonitor != null) {
      RemoteConfigurationRegistryClient client = remoteMonitor.getClient();
      if (client != null) {
        List<String> existingProviderConfigs = client.listChildEntries(entryParent);
        for (String entryName : existingProviderConfigs) {
          if (FilenameUtils.getBaseName(entryName).equals(name)) {
            String entryPath = entryParent + "/" + entryName;
            client.deleteEntry(entryPath);
            result = !client.entryExists(entryPath);
            if (!result) {
              log.failedToDeletedRemoteConfigFile("descriptor", name);
            }
            break;
          }
        }
      }
    }

    return result;
  }

  /**
   * Utility method for listing the files in the specified directory.
   * This method is "nicer" than the File#listFiles() because it will not return null.
   *
   * @param directory The directory whose files should be returned.
   *
   * @return A List of the Files on the directory.
   */
  private static List<File> listFiles(File directory) {
    List<File> result;
    File[] files = directory.listFiles();
    if (files != null) {
      result = Arrays.asList(files);
    } else {
      result = Collections.emptyList();
    }
    return result;
  }

  /**
   * Search for a file in the specified directory whose base name (filename without extension) matches the
   * specified basename.
   *
   * @param directory The directory in which to search.
   * @param basename  The basename of interest.
   *
   * @return The matching File
   */
  private static File getExistingFile(File directory, String basename) {
    File match = null;
    for (File file : listFiles(directory)) {
      if (FilenameUtils.getBaseName(file.getName()).equals(basename)) {
        match = file;
        break;
      }
    }
    return match;
  }

  /**
   * Write the specified content to a file.
   *
   * @param dest    The destination directory.
   * @param name    The name of the file.
   * @param content The contents of the file.
   *
   * @return true, if the write succeeds; otherwise, false.
   */
  private static boolean writeConfig(File dest, String name, String content) {
    boolean result = false;

    File destFile = new File(dest, name);
    try {
      FileUtils.writeStringToFile(destFile, content, StandardCharsets.UTF_8);
      log.wroteConfigurationFile(destFile.getAbsolutePath());
      result = true;
    } catch (IOException e) {
      log.failedToWriteConfigurationFile(destFile.getAbsolutePath(), e);
    }

    return result;
  }


  /**
   * Change handler for simple descriptors
   */
  public static class DescriptorsMonitor extends FileAlterationListenerAdaptor
                                          implements FileFilter {

    static final List<String> SUPPORTED_EXTENSIONS = new ArrayList<>();
    static {
      SUPPORTED_EXTENSIONS.add("json");
      SUPPORTED_EXTENSIONS.add("yml");
      SUPPORTED_EXTENSIONS.add("yaml");
    }

    private GatewayConfig gatewayConfig;

    private File topologiesDir;

    private AliasService aliasService;

    private Map<String, List<String>> providerConfigReferences = new HashMap<>();


    static boolean isDescriptorFile(String filename) {
      return SUPPORTED_EXTENSIONS.contains(FilenameUtils.getExtension(filename));
    }

    public DescriptorsMonitor(GatewayConfig config, File topologiesDir, AliasService aliasService) {
      this.gatewayConfig  = config;
      this.topologiesDir  = topologiesDir;
      this.aliasService   = aliasService;
    }

    List<String> getReferencingDescriptors(String providerConfigPath) {
      String normalizedPath = FilenameUtils.normalize(providerConfigPath);
      return providerConfigReferences.computeIfAbsent(normalizedPath, p -> new ArrayList<>());
    }

    @Override
    public void onFileCreate(File file) {
      onFileChange(file);
    }

    @Override
    public void onFileDelete(File file) {
      // For simple descriptors, we need to make sure to delete any corresponding full topology descriptors to trigger undeployment
      for (String ext : DefaultTopologyService.SUPPORTED_TOPOLOGY_FILE_EXTENSIONS) {
        File topologyFile =
                new File(topologiesDir, FilenameUtils.getBaseName(file.getName()) + "." + ext);
        if (topologyFile.exists()) {
          log.deletingTopologyForDescriptorDeletion(topologyFile.getName(), file.getName());
          topologyFile.delete();
        }
      }

      String normalizedFilePath = FilenameUtils.normalize(file.getAbsolutePath());
      String reference = null;
      for (Map.Entry<String, List<String>> entry : providerConfigReferences.entrySet()) {
        if (entry.getValue().contains(normalizedFilePath)) {
          reference = entry.getKey();
          break;
        }
      }

      if (reference != null) {
        providerConfigReferences.get(reference).remove(normalizedFilePath);
        log.removedProviderConfigurationReference(normalizedFilePath, reference);
      }
    }

    @Override
    public void onFileChange(File file) {
      try {
        // When a simple descriptor has been created or modified, generate the new topology descriptor
        Map<String, File> result = SimpleDescriptorHandler.handle(gatewayConfig, file, topologiesDir, aliasService);
        log.generatedTopologyForDescriptorChange(result.get(SimpleDescriptorHandler.RESULT_TOPOLOGY).getName(),
            file.getName());

        // Add the provider config reference relationship for handling updates to the provider config
        String providerConfig =
            FilenameUtils.normalize(result.get(SimpleDescriptorHandler.RESULT_REFERENCE).getAbsolutePath());
        if (!providerConfigReferences.containsKey(providerConfig)) {
          providerConfigReferences.put(providerConfig, new ArrayList<>());
        }
        List<String> refs = providerConfigReferences.get(providerConfig);
        String descriptorName = FilenameUtils.normalize(file.getAbsolutePath());
        if (!refs.contains(descriptorName)) {
          // Need to check if descriptor had previously referenced another provider config, so it can be removed
          for (List<String> descs : providerConfigReferences.values()) {
            descs.remove(descriptorName);
          }

          // Add the current reference relationship
          refs.add(descriptorName);
          log.addedProviderConfigurationReference(descriptorName, providerConfig);
        }
      } catch (IllegalArgumentException e) {
        log.simpleDescriptorHandlingError(file.getName(), e);

        // If the referenced provider configuration is invalid, remove any existing reference relationships for the
        // referencing descriptor.
        String descriptorName = FilenameUtils.normalize(file.getAbsolutePath());
        // Need to check if descriptor had previously referenced another provider config, so it can be removed
        for (List<String> descs : providerConfigReferences.values()) {
          descs.remove(descriptorName);
        }
      } catch (Exception e) {
        log.simpleDescriptorHandlingError(file.getName(), e);
      }
    }

    @Override
    public boolean accept(File file) {
      boolean accept = false;
      if (!file.isDirectory() && file.canRead()) {
        String extension = FilenameUtils.getExtension(file.getName());
        if (SUPPORTED_EXTENSIONS.contains(extension)) {
          accept = true;
        }
      }
      return accept;
    }
  }

  /**
   * Change handler for shared provider configurations
   */
  public static class SharedProviderConfigMonitor extends FileAlterationListenerAdaptor implements FileFilter {

    static final List<String> SUPPORTED_EXTENSIONS = ProviderConfigurationParser.SUPPORTED_EXTENSIONS;

    private DescriptorsMonitor descriptorsMonitor;
    private File descriptorsDir;


    SharedProviderConfigMonitor(DescriptorsMonitor descMonitor, File descriptorsDir) {
      this.descriptorsMonitor = descMonitor;
      this.descriptorsDir     = descriptorsDir;
    }

    @Override
    public void onFileCreate(File file) {
      onFileChange(file);
    }

    @Override
    public void onFileDelete(File file) {
      onFileChange(file);
    }

    @Override
    public void onFileChange(File file) {
      // For shared provider configuration, we need to update any simple descriptors that reference it
      for (File descriptor : getReferencingDescriptors(file)) {
        descriptor.setLastModified(System.currentTimeMillis());
      }
    }

    private List<File> getReferencingDescriptors(File sharedProviderConfig) {
      List<File> references = new ArrayList<>();

      for (File descriptor : listFiles(descriptorsDir)) {
        if (DescriptorsMonitor.SUPPORTED_EXTENSIONS.contains(FilenameUtils.getExtension(descriptor.getName()))) {
          for (String reference : descriptorsMonitor.getReferencingDescriptors(FilenameUtils.normalize(sharedProviderConfig.getAbsolutePath()))) {
            references.add(new File(reference));
          }
        }
      }

      return references;
    }

    @Override
    public boolean accept(File file) {
      boolean accept = false;
      if (!file.isDirectory() && file.canRead()) {
        String extension = FilenameUtils.getExtension(file.getName());
        if (SUPPORTED_EXTENSIONS.contains(extension)) {
          accept = true;
        }
      }
      return accept;
    }
  }

  /**
   * Listener for Ambari config change events, which will trigger re-generation (including re-discovery) of the
   * affected topologies.
   */
  private static class TopologyDiscoveryTrigger implements ClusterConfigurationMonitor.ConfigurationChangeListener {

    private TopologyService topologyService;
    private ClusterConfigurationMonitorService ccms;

    TopologyDiscoveryTrigger(TopologyService topologyService, ClusterConfigurationMonitorService ccms) {
      this.topologyService = topologyService;
      this.ccms = ccms;
    }

    @Override
    public void onConfigurationChange(String source, String clusterName) {
      log.noticedClusterConfigurationChange(source, clusterName);
      try {
        boolean affectedDescriptors = false;
        // Identify any descriptors associated with the cluster configuration change
        for (File descriptor : topologyService.getDescriptors()) {
          String descriptorContent = FileUtils.readFileToString(descriptor, StandardCharsets.UTF_8);
          if (descriptorContent.contains(source)) {
            if (descriptorContent.contains(clusterName)) {
              affectedDescriptors = true;
              log.triggeringTopologyRegeneration(source, clusterName, descriptor.getAbsolutePath());
              // 'Touch' the descriptor to trigger re-generation of the associated topology
              descriptor.setLastModified(System.currentTimeMillis());
            }
          }
        }

        if (!affectedDescriptors) {
          // If not descriptors are affected by this configuration, then clear the cache to prevent future notifications
          ccms.clearCache(source, clusterName);
        }
      } catch (Exception e) {
        log.errorRespondingToConfigChange(source, clusterName, e);
      }
    }
  }

}
