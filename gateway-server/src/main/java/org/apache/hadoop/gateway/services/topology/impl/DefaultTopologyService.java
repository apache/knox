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

package org.apache.hadoop.gateway.services.topology.impl;


import org.apache.commons.digester3.Digester;
import org.apache.commons.digester3.binder.DigesterLoader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.hadoop.gateway.GatewayMessages;
import org.apache.hadoop.gateway.audit.api.Action;
import org.apache.hadoop.gateway.audit.api.ActionOutcome;
import org.apache.hadoop.gateway.audit.api.AuditServiceFactory;
import org.apache.hadoop.gateway.audit.api.Auditor;
import org.apache.hadoop.gateway.audit.api.ResourceType;
import org.apache.hadoop.gateway.audit.log4j.audit.AuditConstants;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.hadoop.gateway.services.topology.TopologyService;
import org.apache.hadoop.gateway.topology.Topology;
import org.apache.hadoop.gateway.topology.TopologyEvent;
import org.apache.hadoop.gateway.topology.TopologyListener;
import org.apache.hadoop.gateway.topology.TopologyMonitor;
import org.apache.hadoop.gateway.topology.TopologyProvider;
import org.apache.hadoop.gateway.topology.builder.TopologyBuilder;
import org.apache.hadoop.gateway.topology.xml.AmbariFormatXmlTopologyRules;
import org.apache.hadoop.gateway.topology.xml.KnoxFormatXmlTopologyRules;
import org.eclipse.persistence.jaxb.JAXBContextProperties;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.digester3.binder.DigesterLoader.newLoader;


public class DefaultTopologyService
    extends FileAlterationListenerAdaptor
    implements TopologyService, TopologyMonitor, TopologyProvider, FileFilter, FileAlterationListener {
  private static Auditor auditor = AuditServiceFactory.getAuditService().getAuditor(
    AuditConstants.DEFAULT_AUDITOR_NAME, AuditConstants.KNOX_SERVICE_NAME,
    AuditConstants.KNOX_COMPONENT_NAME);
  private static final List<String> SUPPORTED_TOPOLOGY_FILE_EXTENSIONS = new ArrayList<String>();
  static {
    SUPPORTED_TOPOLOGY_FILE_EXTENSIONS.add("xml");
    SUPPORTED_TOPOLOGY_FILE_EXTENSIONS.add("conf");
  }
  private static GatewayMessages log = MessagesFactory.get(GatewayMessages.class);
  private static DigesterLoader digesterLoader = newLoader(new KnoxFormatXmlTopologyRules(), new AmbariFormatXmlTopologyRules());
  private FileAlterationMonitor monitor;
  private File directory;
  private Set<TopologyListener> listeners;
  private volatile Map<File, Topology> topologies;

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
      } catch (IOException e) {
        if (System.currentTimeMillis() - start < TIMEOUT) {
          log.failedToLoadTopologyRetrying(file.getAbsolutePath(), Long.toString(DELAY), e);
          Thread.sleep(DELAY);
        } else {
          throw e;
        }
      } catch (SAXException e) {
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
    long start = System.currentTimeMillis();
    long limit = 1000L; // One second.
    long elapsed = 1;
    while (elapsed <= limit) {
      try {
        long origTimestamp = topologyFile.lastModified();
        long setTimestamp = Math.max(System.currentTimeMillis(), topologyFile.lastModified() + elapsed);
        if (topologyFile.setLastModified(setTimestamp)) {
          long newTimstamp = topologyFile.lastModified();
          if (newTimstamp > origTimestamp) {
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
  }

  private List<TopologyEvent> createChangeEvents(
      Map<File, Topology> oldTopologies,
      Map<File, Topology> newTopologies) {
    ArrayList<TopologyEvent> events = new ArrayList<TopologyEvent>();
    // Go through the old topologies and find anything that was deleted.
    for (File file : oldTopologies.keySet()) {
      if (!newTopologies.containsKey(file)) {
        events.add(new TopologyEvent(TopologyEvent.Type.DELETED, oldTopologies.get(file)));
      }
    }
    // Go through the new topologies and figure out what was updated vs added.
    for (File file : newTopologies.keySet()) {
      if (oldTopologies.containsKey(file)) {
        Topology oldTopology = oldTopologies.get(file);
        Topology newTopology = newTopologies.get(file);
        if (newTopology.getTimestamp() > oldTopology.getTimestamp()) {
          events.add(new TopologyEvent(TopologyEvent.Type.UPDATED, newTopologies.get(file)));
        }
      } else {
        events.add(new TopologyEvent(TopologyEvent.Type.CREATED, newTopologies.get(file)));
      }
    }
    return events;
  }

  private File calculateAbsoluteTopologiesDir(GatewayConfig config) {

    File topoDir = new File(config.getGatewayTopologyDir());
    topoDir = topoDir.getAbsoluteFile();
    return topoDir;
  }

  private void initListener(FileAlterationMonitor monitor, File directory) {
    this.directory = directory;
    this.monitor = monitor;


    FileAlterationObserver observer = new FileAlterationObserver(this.directory, this);
    observer.addListener(this);
    monitor.addObserver(observer);

    this.listeners = new HashSet<TopologyListener>();
    this.topologies = new HashMap<File, Topology>(); //loadTopologies( this.directory );
  }

  private void initListener(File directory) throws IOException, SAXException {
    initListener(new FileAlterationMonitor(1000L), directory);
  }

  private Map<File, Topology> loadTopologies(File directory) {
    Map<File, Topology> map = new HashMap<File, Topology>();
    if (directory.exists() && directory.canRead()) {
      for (File file : directory.listFiles(this)) {
        try {
          Topology loadTopology = loadTopology(file);
          if (null != loadTopology) {
            map.put(file, loadTopology);
          } else {
            auditor.audit(Action.LOAD, file.getAbsolutePath(), ResourceType.TOPOLOGY,
              ActionOutcome.FAILURE);
            log.failedToLoadTopology(file.getAbsolutePath());
          }
        } catch (IOException e) {
          // Maybe it makes sense to throw exception
          auditor.audit(Action.LOAD, file.getAbsolutePath(), ResourceType.TOPOLOGY,
            ActionOutcome.FAILURE);
          log.failedToLoadTopology(file.getAbsolutePath(), e);
        } catch (SAXException e) {
          // Maybe it makes sense to throw exception
          auditor.audit(Action.LOAD, file.getAbsolutePath(), ResourceType.TOPOLOGY,
            ActionOutcome.FAILURE);
          log.failedToLoadTopology(file.getAbsolutePath(), e);
        } catch (Exception e) {
          // Maybe it makes sense to throw exception
          auditor.audit(Action.LOAD, file.getAbsolutePath(), ResourceType.TOPOLOGY,
            ActionOutcome.FAILURE);
          log.failedToLoadTopology(file.getAbsolutePath(), e);
        }
      }
    }
    return map;
  }

  public void deployTopology(Topology t){

    try {
      File temp = new File(directory.getAbsolutePath() + "/" + t.getName() + ".xml.temp");
      Package topologyPkg = Topology.class.getPackage();
      String pkgName = topologyPkg.getName();
      String bindingFile = pkgName.replace(".", "/") + "/topology_binding-xml.xml";

      Map<String, Object> properties = new HashMap<String, Object>(1);
      properties.put(JAXBContextProperties.OXM_METADATA_SOURCE, bindingFile);
      JAXBContext jc = JAXBContext.newInstance(pkgName, Topology.class.getClassLoader(), properties);
      Marshaller mr = jc.createMarshaller();

      mr.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
      mr.marshal(t, temp);

      File topology = new File(directory.getAbsolutePath() + "/" + t.getName() + ".xml");
      if(!temp.renameTo(topology)) {
        FileUtils.forceDelete(temp);
        throw new IOException("Could not rename temp file");
      }

    } catch (JAXBException e) {
      auditor.audit(Action.DEPLOY, t.getName(), ResourceType.TOPOLOGY, ActionOutcome.FAILURE);
      log.failedToDeployTopology(t.getName(), e);
    } catch (IOException io) {
      auditor.audit(Action.DEPLOY, t.getName(), ResourceType.TOPOLOGY, ActionOutcome.FAILURE);
      log.failedToDeployTopology(t.getName(), io);
    }
    reloadTopologies();
  }

  public void redeployTopologies(String topologyName) {

    for (Topology topology : getTopologies()) {
      if (topologyName == null || topologyName.equals(topology.getName())) {
        redeployTopology(topology);
      }
    }

  }

  public void reloadTopologies() {
    try {
      synchronized (this) {
        Map<File, Topology> oldTopologies = topologies;
        Map<File, Topology> newTopologies = loadTopologies(directory);
        List<TopologyEvent> events = createChangeEvents(oldTopologies, newTopologies);
        topologies = newTopologies;
        notifyChangeListeners(events);
      }
    } catch (Exception e) {
      // Maybe it makes sense to throw exception
      log.failedToReloadTopologies(e);
    }
  }

  public void deleteTopology(Topology t) {
    File topoDir = directory;

    if(topoDir.exists() && topoDir.canRead()) {
      File[] results = topoDir.listFiles();
      for (File f : results) {
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

  public Collection<Topology> getTopologies() {
    Map<File, Topology> map = topologies;
    return Collections.unmodifiableCollection(map.values());
  }

  @Override
  public void addTopologyChangeListener(TopologyListener listener) {
    listeners.add(listener);
  }

  @Override
  public void startMonitor() throws Exception {
    monitor.start();
  }

  @Override
  public void stopMonitor() throws Exception {
    monitor.stop();
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

    try {
      initListener(calculateAbsoluteTopologiesDir(config));
    } catch (IOException io) {
      throw new ServiceLifecycleException(io.getMessage());
    } catch (SAXException sax) {
      throw new ServiceLifecycleException(sax.getMessage());
    }

  }
}
