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
import org.apache.commons.vfs2.FileChangeEvent;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileListener;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.impl.DefaultFileMonitor;
import org.apache.hadoop.gateway.topology.Topology;
import org.apache.hadoop.gateway.topology.TopologyEvent;
import org.apache.hadoop.gateway.topology.TopologyListener;
import org.apache.hadoop.gateway.topology.TopologyMonitor;
import org.apache.hadoop.gateway.topology.TopologyProvider;
import org.apache.hadoop.gateway.topology.xml.XmlTopologyRules;
import org.codehaus.plexus.util.FileUtils;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.digester3.binder.DigesterLoader.newLoader;

public class FileTopologyProvider implements TopologyProvider, TopologyMonitor, FileListener {

  private static DigesterLoader digesterLoader = newLoader( new XmlTopologyRules() );

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

  private static Topology loadTopology( FileObject file ) throws IOException, SAXException {
    Digester digester = digesterLoader.newDigester();
    FileContent content = file.getContent();
    Topology topology = digester.parse( content.getInputStream() );
    topology.setName( FileUtils.removeExtension( file.getName().getBaseName() ) );
    topology.setTimestamp( content.getLastModifiedTime() );
    return topology;
  }

  private Map<FileName, Topology> loadTopologies( FileObject directory ) throws FileSystemException {
    Map<FileName, Topology> map = new HashMap<FileName, Topology>();
    if( directory.exists() && directory.getType().hasChildren() ) {
      for( FileObject file : directory.getChildren() ) {
        if( file.exists() && !file.getType().hasChildren() ) {
          try {
            map.put( file.getName(), loadTopology( file ) );
          } catch( IOException e ) {
            e.printStackTrace();
          } catch( SAXException e ) {
            e.printStackTrace();
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
      e.printStackTrace();
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
        e.printStackTrace();
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

  @Override
  public void fileCreated( FileChangeEvent fileChangeEvent ) {
    reloadTopologies();
  }

  @Override
  public void fileDeleted( FileChangeEvent fileChangeEvent ) {
    reloadTopologies();
  }

  @Override
  public void fileChanged( FileChangeEvent fileChangeEvent ) {
    reloadTopologies();
  }

}
