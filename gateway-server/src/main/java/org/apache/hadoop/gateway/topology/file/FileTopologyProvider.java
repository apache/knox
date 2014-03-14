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
package org.apache.hadoop.gateway.topology.file;

import org.apache.commons.digester3.Digester;
import org.apache.commons.digester3.binder.DigesterLoader;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.vfs2.FileChangeEvent;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileListener;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.impl.DefaultFileMonitor;
import org.apache.hadoop.gateway.GatewayMessages;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.topology.Topology;
import org.apache.hadoop.gateway.topology.TopologyEvent;
import org.apache.hadoop.gateway.topology.TopologyListener;
import org.apache.hadoop.gateway.topology.TopologyMonitor;
import org.apache.hadoop.gateway.topology.TopologyProvider;
import org.apache.hadoop.gateway.topology.builder.TopologyBuilder;
import org.apache.hadoop.gateway.topology.xml.AmbariFormatXmlTopologyRules;
import org.apache.hadoop.gateway.topology.xml.KnoxFormatXmlTopologyRules;
import org.xml.sax.SAXException;

import java.io.File;
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

//import org.codehaus.plexus.util.FileUtils;

public class FileTopologyProvider implements TopologyProvider, TopologyMonitor, FileListener {

  private static GatewayMessages log = MessagesFactory.get( GatewayMessages.class );
  private static DigesterLoader digesterLoader = newLoader( new KnoxFormatXmlTopologyRules(), new AmbariFormatXmlTopologyRules() );
  private static final List<String> SUPPORTED_TOPOLOGY_FILE_EXTENSIONS = new ArrayList<String>();
  static {
      SUPPORTED_TOPOLOGY_FILE_EXTENSIONS.add("xml");
      SUPPORTED_TOPOLOGY_FILE_EXTENSIONS.add("conf");
  }

  private DefaultFileMonitor monitor;
  private FileObject directory;
  private Set<TopologyListener> listeners;
  private volatile Map<FileName, Topology> topologies;

  // For unit testing.
  FileTopologyProvider( DefaultFileMonitor monitor, FileObject directory ) throws IOException, SAXException {
    this.directory = directory;
    this.monitor = ( monitor != null ) ? monitor : new DefaultFileMonitor( this );
    this.monitor.setRecursive( false );
    this.monitor.addFile( this.directory );
    this.listeners = new HashSet<TopologyListener>();
    this.topologies = new HashMap<FileName, Topology>(); //loadTopologies( this.directory );
  }

  public FileTopologyProvider( File directory ) throws IOException, SAXException {
    this( null, VFS.getManager().toFileObject( directory ) );
  }

  private static Topology loadTopology( FileObject file ) throws IOException, SAXException, URISyntaxException, InterruptedException {
    final long TIMEOUT = 250; //ms
    final long DELAY = 50; //ms
    log.loadingTopologyFile( file.getName().getFriendlyURI() );
    Topology topology;
    long start = System.currentTimeMillis();
    while( true ) {
      try {
        topology = loadTopologyAttempt( file );
        break;
      } catch ( IOException e ) {
        if( System.currentTimeMillis() - start < TIMEOUT ) {
          log.failedToLoadTopologyRetrying( file.getName().getFriendlyURI(), Long.toString( DELAY ), e );
          Thread.sleep( DELAY );
        } else {
          throw e;
        }
      } catch ( SAXException e ) {
        if( System.currentTimeMillis() - start < TIMEOUT ) {
          log.failedToLoadTopologyRetrying( file.getName().getFriendlyURI(), Long.toString( DELAY ), e );
          Thread.sleep( DELAY );
        } else {
          throw e;
        }
      }
    }
    return topology;
  }

  private static Topology loadTopologyAttempt( FileObject file ) throws IOException, SAXException, URISyntaxException {
    Topology topology;Digester digester = digesterLoader.newDigester();
    FileContent content = file.getContent();
    TopologyBuilder topologyBuilder = digester.parse( content.getInputStream() );
    topology = topologyBuilder.build();
    topology.setUri( file.getURL().toURI() );
    topology.setName( FilenameUtils.removeExtension( file.getName().getBaseName() ) );
    topology.setTimestamp( content.getLastModifiedTime() );
    return topology;
  }

  private Map<FileName, Topology> loadTopologies( FileObject directory ) throws FileSystemException {
    Map<FileName, Topology> map = new HashMap<FileName, Topology>();
    if( directory.exists() && directory.getType().hasChildren() ) {
      for( FileObject file : directory.getChildren() ) {
        if( file.exists() && !file.getType().hasChildren() && SUPPORTED_TOPOLOGY_FILE_EXTENSIONS.contains( file.getName().getExtension() )) {
          try {
            map.put( file.getName(), loadTopology( file ) );
          } catch( IOException e ) {
            // Maybe it makes sense to throw exception
            log.failedToLoadTopology( file.getName().getFriendlyURI(), e );
          } catch( SAXException e ) {
            // Maybe it makes sense to throw exception
            log.failedToLoadTopology( file.getName().getFriendlyURI(), e );
          } catch ( Exception e ) {
            // Maybe it makes sense to throw exception
            log.failedToLoadTopology( file.getName().getFriendlyURI(), e );
          }
        }
      }
    }
    return map;
  }

  public void reloadTopologies() {
    try {
      synchronized ( this ) {
        Map<FileName, Topology> oldTopologies = topologies;
        Map<FileName, Topology> newTopologies = loadTopologies( directory );
        List<TopologyEvent> events = createChangeEvents( oldTopologies, newTopologies );
        topologies = newTopologies;
        notifyChangeListeners( events );
      }
    } catch( FileSystemException e ) {
      // Maybe it makes sense to throw exception
      log.failedToReloadTopologies( e );
    }
  }

  private static List<TopologyEvent> createChangeEvents(
    Map<FileName, Topology> oldTopologies,
    Map<FileName, Topology> newTopologies ) {
    ArrayList<TopologyEvent> events = new ArrayList<TopologyEvent>();
    // Go through the old topologies and find anything that was deleted.
    for( FileName fileName : oldTopologies.keySet() ) {
      if( !newTopologies.containsKey( fileName ) ) {
        events.add( new TopologyEvent( TopologyEvent.Type.DELETED, oldTopologies.get( fileName ) ) );
      }
    }
    // Go through the new topologies and figure out what was updated vs added.
    for( FileName fileName : newTopologies.keySet() ) {
      if( oldTopologies.containsKey( fileName ) ) {
        Topology oldTopology = oldTopologies.get( fileName );
        Topology newTopology = newTopologies.get( fileName );
        if( newTopology.getTimestamp() > oldTopology.getTimestamp() ) {
          events.add( new TopologyEvent( TopologyEvent.Type.UPDATED, newTopologies.get( fileName ) ) );
        }
      } else {
        events.add( new TopologyEvent( TopologyEvent.Type.CREATED, newTopologies.get( fileName ) ) );
      }
    }
    return events ;
  }

  private void notifyChangeListeners( List<TopologyEvent> events ) {
    for( TopologyListener listener : listeners ) {
      try {
        listener.handleTopologyEvent( events );
      } catch( RuntimeException e ) {
        log.failedToHandleTopologyEvents( e );
      }
    }
  }

  @Override
  public Collection<Topology> getTopologies() {
    Map<FileName, Topology> map = topologies;
    return Collections.unmodifiableCollection( map.values() );
  }

  @Override
  public void addTopologyChangeListener( TopologyListener listener ) {
    listeners.add( listener );
  }

  @Override
  public void startMonitor() {
    monitor.start();
  }

  @Override
  public void stopMonitor() {
    monitor.stop();
  }

  private void handleFileEvent( FileChangeEvent fileChangeEvent ) throws FileSystemException {
    FileObject file = fileChangeEvent.getFile();
    if( file != null && ( !file.getType().hasChildren() || file.equals( directory ) ) ) {
      reloadTopologies();
    }
  }

  @Override
  public void fileCreated( FileChangeEvent fileChangeEvent ) throws FileSystemException {
    handleFileEvent( fileChangeEvent );
  }

  @Override
  public void fileDeleted( FileChangeEvent fileChangeEvent ) throws FileSystemException {
    handleFileEvent( fileChangeEvent );
  }

  @Override
  public void fileChanged( FileChangeEvent fileChangeEvent ) throws FileSystemException {
    handleFileEvent( fileChangeEvent );
  }

}
