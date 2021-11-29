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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
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
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClient;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.services.topology.monitor.DescriptorsMonitor;
import org.apache.knox.gateway.services.topology.monitor.SharedProviderConfigMonitor;
import org.apache.knox.gateway.topology.ClusterConfigurationMonitorService;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.topology.TopologyEvent;
import org.apache.knox.gateway.topology.TopologyListener;
import org.apache.knox.gateway.topology.TopologyMonitor;
import org.apache.knox.gateway.topology.TopologyProvider;
import org.apache.knox.gateway.topology.Version;
import org.apache.knox.gateway.topology.discovery.ClusterConfigurationMonitor;
import org.apache.knox.gateway.topology.discovery.ServiceDiscovery;
import org.apache.knox.gateway.topology.monitor.RemoteConfigurationMonitor;
import org.apache.knox.gateway.topology.monitor.RemoteConfigurationMonitorFactory;
import org.apache.knox.gateway.topology.simple.SimpleDescriptor;
import org.apache.knox.gateway.topology.simple.SimpleDescriptorFactory;
import org.apache.knox.gateway.topology.validation.TopologyValidator;
import org.apache.knox.gateway.util.ServiceDefinitionsLoader;
import org.apache.knox.gateway.util.TopologyUtils;
import org.eclipse.persistence.jaxb.JAXBContextProperties;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultTopologyService extends FileAlterationListenerAdaptor implements TopologyService, TopologyMonitor,
    TopologyProvider, FileFilter, FileAlterationListener, ServiceDefinitionChangeListener {

  private static final JAXBContext jaxbContext = getJAXBContext();
  private static final String TOPOLOGY_CLOSING_XML_ELEMENT = "</topology>";
  private static final String REDEPLOY_TIME_TEMPLATE = "   <redeployTime>%d</redeployTime>\n" + TOPOLOGY_CLOSING_XML_ELEMENT;

  private static final Auditor auditor = AuditServiceFactory.getAuditService().getAuditor(
    AuditConstants.DEFAULT_AUDITOR_NAME, AuditConstants.KNOX_SERVICE_NAME,
    AuditConstants.KNOX_COMPONENT_NAME);

  public static final List<String> SUPPORTED_TOPOLOGY_FILE_EXTENSIONS = Collections.unmodifiableList(Arrays.asList("xml", "conf"));

  private static final GatewayMessages log = MessagesFactory.get(GatewayMessages.class);
  private final Map<String, FileAlterationMonitor> monitors = new ConcurrentHashMap<>();
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

  private Topology loadTopology(File file) throws IOException, SAXException, InterruptedException {
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

  @Override
  public Topology parse(final InputStream content) throws IOException, SAXException {
    return TopologyUtils.parse(content);
  }

  private Topology loadTopologyAttempt(File file) throws IOException, SAXException {
    Topology topology;
    try (InputStream in = FileUtils.openInputStream(file)) {
      topology = parse(in);
      if (topology != null) {
        topology.setUri(file.toURI());
        topology.setName(FilenameUtils.removeExtension(file.getName()));
        topology.setTimestamp(file.lastModified());
      }
    }
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

      // Since KNOX-2689, updating the topology file's timestamp is not enough.
      // We need to make an actual change in the topology XML to redeploy it
      // This change is: updating a new XML element called redeployTime
      try {
        final String currentTopologyContent = FileUtils.readFileToString(topologyFile, StandardCharsets.UTF_8);
        String updated = currentTopologyContent.replaceAll("^*<redeployTime>.*", "");

        //add the current timestamp
        updated = updated.replace(TOPOLOGY_CLOSING_XML_ELEMENT, String.format(Locale.getDefault(), REDEPLOY_TIME_TEMPLATE, System.currentTimeMillis()));

        //save the updated content in the file
        FileUtils.write(topologyFile, updated, StandardCharsets.UTF_8);
      } catch (IOException e) {
        auditor.audit(Action.REDEPLOY, topology.getName(), ResourceType.TOPOLOGY, ActionOutcome.FAILURE);
        log.failedToRedeployTopology(topology.getName(), e);
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
        if (shouldMarkTopologyUpdated(newTopology.getValue(), oldTopology)) {
          events.add(new TopologyEvent(TopologyEvent.Type.UPDATED, newTopology.getValue()));
        }
      } else {
        events.add(new TopologyEvent(TopologyEvent.Type.CREATED, newTopology.getValue()));
      }
    }
    return events;
  }

  private boolean shouldMarkTopologyUpdated(Topology newTopology, Topology oldTopology) {
    final boolean timestampUpdated = newTopology.getTimestamp() > oldTopology.getTimestamp();
    return config.topologyRedeploymentRequiresChanges() ? timestampUpdated && !oldTopology.equals(newTopology) : timestampUpdated;
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

  private void initListener(String monitorName, FileAlterationMonitor monitor, File directory, FileFilter filter, FileAlterationListener listener) {
    monitors.put(monitorName, monitor);
    FileAlterationObserver observer = new FileAlterationObserver(directory, filter);
    observer.addListener(listener);
    monitor.addObserver(observer);
  }

  private void initListener(String monitorName, File directory, FileFilter filter, FileAlterationListener listener) {
    // Increasing the monitoring interval to 5 seconds as profiling has shown
    // this is rather expensive in terms of generated garbage objects.
    initListener(monitorName, new FileAlterationMonitor(5000L), directory, filter, listener);
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
  public void redeployTopology(String topologyName) {
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
        if (!events.isEmpty()) {
          notifyChangeListeners(events);
        }
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
    for (Entry<String, FileAlterationMonitor> monitor : monitors.entrySet()) {
      monitor.getValue().start();
      log.startedMonitor(monitor.getKey());
    }

    // Start the remote configuration monitor, if it has been initialized
    if (remoteMonitor != null) {
      try {
        remoteMonitor.start();
      } catch (Exception e) {
        log.remoteConfigurationMonitorStartFailure(remoteMonitor.getClass().getTypeName(), e.getLocalizedMessage());
      }
    }

    // Trigger descriptor discovery (KNOX-2301)
    triggerDescriptorDiscovery();
  }

  private void triggerDescriptorDiscovery() {
    for (File descriptor : getDescriptors()) {
      descriptorsMonitor.onFileChange(descriptor);
    }
  }

  @Override
  public void stopMonitor() throws Exception {
    // Stop the local configuration monitors
    for (Entry<String, FileAlterationMonitor> monitor : monitors.entrySet()) {
      monitor.getValue().stop();
      log.stoppedMonitor(monitor.getKey());
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
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {

    this.config = config;
    String gatewayConfDir = config.getGatewayConfDir();
    if (gatewayConfDir != null) {
      System.setProperty(ServiceDiscovery.CONFIG_DIR_PROPERTY, gatewayConfDir);
    }

    // Register a cluster configuration monitor listener for change notifications.
    // The cluster monitor service will start before this service, so the listener must be registered
    // beforehand or we risk the possibility of missing configuration change notifications.
    GatewayServices gwServices = GatewayServer.getGatewayServices();
    if (gwServices != null) {
      ClusterConfigurationMonitorService ccms =
              gwServices.getService(ServiceType.CLUSTER_CONFIGURATION_MONITOR_SERVICE);
      ccms.addListener(new TopologyDiscoveryTrigger(this, ccms));
    }

    try {
      listeners  = new HashSet<>();
      topologies = new HashMap<>();

      topologiesDirectory = calculateAbsoluteTopologiesDir(config);

      File configDirectory = calculateAbsoluteConfigDir(config);
      descriptorsDirectory = new File(configDirectory, "descriptors");
      sharedProvidersDirectory = new File(configDirectory, "shared-providers");

      // Add support for conf/topologies
      initListener("topologies", topologiesDirectory, this, this);
      log.configuredMonitoringTopologyChangesInDirectory(topologiesDirectory.getAbsolutePath());

      // Add support for conf/descriptors
      descriptorsMonitor = new DescriptorsMonitor(config, topologiesDirectory, aliasService);
      initListener("simple descriptors", descriptorsDirectory, descriptorsMonitor, descriptorsMonitor);
      log.configuredMonitoringDescriptorChangesInDirectory(descriptorsDirectory.getAbsolutePath());

      // Add support for conf/shared-providers
      SharedProviderConfigMonitor spm = new SharedProviderConfigMonitor(descriptorsMonitor, descriptorsDirectory);
      initListener("shared provider configurations", sharedProvidersDirectory, spm, spm);
      log.configuredMonitoringProviderConfigChangesInDirectory(sharedProvidersDirectory.getAbsolutePath());

      // Initialize the remote configuration monitor, if it has been configured
      remoteMonitor = RemoteConfigurationMonitorFactory.get(config);
    } catch (Exception e) {
      throw new ServiceLifecycleException(e.getMessage(), e);
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
  private static Collection<File> listFiles(File directory) {
    return FileUtils.listFiles(directory, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
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
   * Listener for cluster config change events, which will trigger re-generation (including re-discovery) of the
   * affected topologies.
   */
  private static class TopologyDiscoveryTrigger implements ClusterConfigurationMonitor.ConfigurationChangeListener {

    private final TopologyService topologyService;
    private final ClusterConfigurationMonitorService ccms;

    TopologyDiscoveryTrigger(TopologyService topologyService, ClusterConfigurationMonitorService ccms) {
      this.topologyService = topologyService;
      this.ccms = ccms;
    }

    @Override
    public void onConfigurationChange(final String source, final String clusterName) {
      log.noticedClusterConfigurationChange(source, clusterName);
      boolean affectedDescriptors = false;

      // Identify any descriptors associated with the cluster configuration change
      for (File descriptor : topologyService.getDescriptors()) {
        try {
          SimpleDescriptor sd = SimpleDescriptorFactory.parse(descriptor.getAbsolutePath());
          if (source.equals(sd.getDiscoveryAddress()) && clusterName.equals(sd.getCluster())) {
            affectedDescriptors = true;
            log.triggeringTopologyRegeneration(source, clusterName, descriptor.getAbsolutePath());
            // 'Touch' the descriptor to trigger re-generation of the associated topology
            descriptor.setLastModified(System.currentTimeMillis());
          }
        } catch (IOException e) {
          log.errorRespondingToConfigChange(source, clusterName, descriptor.getName(), e);
        }
      }

      if (!affectedDescriptors) {
        // If no descriptors are affected by this configuration, then clear the cache to prevent future notifications
        ccms.clearCache(source, clusterName);
      }
    }
  }

}
