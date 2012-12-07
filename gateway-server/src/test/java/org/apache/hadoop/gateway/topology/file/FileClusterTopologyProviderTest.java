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

import org.apache.commons.io.IOUtils;
import org.apache.commons.vfs2.FileChangeEvent;
import org.apache.commons.vfs2.FileListener;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.impl.DefaultFileMonitor;
import org.apache.hadoop.gateway.topology.Topology;
import org.apache.hadoop.gateway.topology.TopologyEvent;
import org.apache.hadoop.gateway.topology.TopologyListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.matchers.JUnitMatchers.hasItem;

public class FileClusterTopologyProviderTest {

  @Before
  public void setUp() throws Exception {
  }

  @After
  public void tearDown() throws Exception {
  }

//  @Test
//  public void testFileMonitor() throws IOException {
//    FileSystemManager manager = VFS.getManager();
//    FileObject dir = manager.resolveFile( "/Users/kevin.minder/tmp" );
//    DefaultFileMonitor monitor = new DefaultFileMonitor( new FileListener() {
//      @Override
//      public void fileCreated( FileChangeEvent event ) throws Exception {
//        System.out.println( "File created " + event.getFile().getName().getFriendlyURI() );
//      }
//      @Override
//      public void fileDeleted( FileChangeEvent event ) throws Exception {
//        System.out.println( "File deleted " + event.getFile().getName().getFriendlyURI() );
//      }
//      @Override
//      public void fileChanged( FileChangeEvent event ) throws Exception {
//        System.out.println( "File modified " + event.getFile().getName().getFriendlyURI() );
//      }
//    } );
//    monitor.setRecursive( false );
//    monitor.addFile( dir );
//    monitor.start();
//    System.out.println( "Waiting" );
//    System.in.read();
//  }

//  @Test
//  public void testRamFileSystemMonitor() throws IOException, InterruptedException {
//    FileSystemManager manager = VFS.getManager();
//    FileObject dir = manager.resolveFile( "ram:///dir" );
//    dir.createFolder();
//    DefaultFileMonitor monitor = new DefaultFileMonitor( new FileListener() {
//      @Override
//      public void fileCreated( FileChangeEvent event ) throws Exception {
//        System.out.println( "Created " + event.getFile().getName().getFriendlyURI() );
//      }
//      @Override
//      public void fileDeleted( FileChangeEvent event ) throws Exception {
//        System.out.println( "Deleted " + event.getFile().getName().getFriendlyURI() );
//      }
//      @Override
//      public void fileChanged( FileChangeEvent event ) throws Exception {
//        System.out.println( "Modified " + event.getFile().getName().getFriendlyURI() );
//      }
//    } );
//    monitor.addFile( dir );
//    monitor.start();
//    FileObject file = createFile( dir, "one", "org/apache/hadoop/gateway/topology/file/topology-one.xml", 1L );
//    file = createFile( dir, "two", "org/apache/hadoop/gateway/topology/file/topology-two.xml", 2L );
//    Thread.sleep( 4000 );
//    file = createFile( dir, "two", "org/apache/hadoop/gateway/topology/file/topology-one.xml", 3L );
//    file = createFile( dir, "one", "org/apache/hadoop/gateway/topology/file/topology-two.xml", 2L );
//
//    System.out.println( "Waiting" );
//    System.in.read();
//  }

  private FileObject createDir( String name ) throws FileSystemException {
    FileSystemManager fsm = VFS.getManager();
    FileObject dir = fsm.resolveFile( name );
    dir.createFolder();
    assertTrue( "Failed to create test dir " + dir.getName().getFriendlyURI(), dir.exists() );
    return dir;
  }

  private FileObject createFile( FileObject parent, String name, String resource, long timestamp ) throws IOException {
    FileObject file = parent.resolveFile( name );
    if( !file.exists() ) {
      file.createFile();
    }
    InputStream input = ClassLoader.getSystemResourceAsStream( resource );
    OutputStream output = file.getContent().getOutputStream();
    IOUtils.copy( input, output );
    output.flush();
    input.close();
    output.close();
    file.getContent().setLastModifiedTime( timestamp );
    assertTrue( "Failed to create test file " + file.getName().getFriendlyURI(), file.exists() );
    assertTrue( "Failed to populate test file " + file.getName().getFriendlyURI(), file.getContent().getSize() > 0 );

//    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
//    IOUtils.copy( file.getContent().getInputStream(), buffer );
//    System.out.println( new String( buffer.toString( "UTF-8" ) ) );

    return file;
  }

  @Test
  public void testGetClusterTopologies() throws Exception {

    FileObject dir = createDir( "ram:///test/dir" );
    createFile( dir, "one", "org/apache/hadoop/gateway/topology/file/topology-one.xml", 1L );

    TestTopologyListener topoListener = new TestTopologyListener();
    FileListenerDelegator fileListener = new FileListenerDelegator();
    NoOpFileMonitor monitor = new NoOpFileMonitor( fileListener );

    FileTopologyProvider provider = new FileTopologyProvider( monitor, dir );
    provider.addTopologyChangeListener( topoListener );
    fileListener.delegate = provider;

    // Unit test "hack" to force monitor to execute.
    provider.reloadTopologies();

    Collection<Topology> topologies = provider.getTopologies();
    assertThat( topologies, notNullValue() );
    assertThat( topologies.size(), is( 1 ) );
    Topology topology = topologies.iterator().next();
    assertThat( topology.getName(), is( "one" ) );
    assertThat( topology.getTimestamp(), is( 1L ) );
    assertThat( topoListener.events.size(), is( 1 ) );
    topoListener.events.clear();

    // Add a file to the directory.
    FileObject two = createFile( dir, "two", "org/apache/hadoop/gateway/topology/file/topology-two.xml", 1L );
    fileListener.fileCreated( new FileChangeEvent( two ) );
    topologies = provider.getTopologies();
    assertThat( topologies.size(), is( 2 ) );
    Set<String> names = new HashSet<String>( Arrays.asList( "one", "two" ) );
    Iterator<Topology> iterator = topologies.iterator();
    topology = iterator.next();
    assertThat( names, hasItem( topology.getName() ) );
    names.remove( topology.getName() );
    topology = iterator.next();
    assertThat( names, hasItem( topology.getName() ) );
    names.remove( topology.getName() );
    assertThat( names.size(), is( 0 ) );
    assertThat( topoListener.events.size(), is( 1 ) );
    List<TopologyEvent> events = topoListener.events.get( 0 );
    assertThat( events.size(), is( 1 ) );
    TopologyEvent event = events.get( 0 );
    assertThat( event.getType(), is( TopologyEvent.Type.CREATED ) );
    assertThat( event.getTopology(), notNullValue() );

    // Update a file in the directory.
    two = createFile( dir, "two", "org/apache/hadoop/gateway/topology/file/topology-three.xml", 2L );
    fileListener.fileChanged( new FileChangeEvent( two ) );
    topologies = provider.getTopologies();
    assertThat( topologies.size(), is( 2 ) );
    names = new HashSet<String>( Arrays.asList( "one", "two" ) );
    iterator = topologies.iterator();
    topology = iterator.next();
    assertThat( names, hasItem( topology.getName() ) );
    names.remove( topology.getName() );
    topology = iterator.next();
    assertThat( names, hasItem( topology.getName() ) );
    names.remove( topology.getName() );
    assertThat( names.size(), is( 0 ) );

    // Remove a file from the directory.
    two.delete();
    fileListener.fileDeleted( new FileChangeEvent( two ) );
    topologies = provider.getTopologies();
    assertThat( topologies.size(), is( 1 ) );
    topology = topologies.iterator().next();
    assertThat( topology.getName(), is( "one" ) );
    assertThat( topology.getTimestamp(), is( 1L ) );
  }

  private class FileListenerDelegator implements FileListener {
    private FileListener delegate;

    @Override
    public void fileCreated( FileChangeEvent event ) throws Exception {
      delegate.fileCreated( event );
    }

    @Override
    public void fileDeleted( FileChangeEvent event ) throws Exception {
      delegate.fileDeleted( event );
    }

    @Override
    public void fileChanged( FileChangeEvent event ) throws Exception {
      delegate.fileChanged( event );
    }
  }

  private class NoOpFileMonitor extends DefaultFileMonitor {

    public NoOpFileMonitor( FileListener listener ) {
      super( listener );
    }

    @Override
    public void start() {
      // NOOP
    }

    @Override
    public void stop() {
      // NOOP
    }

  }

  private class TestTopologyListener implements TopologyListener {

    public ArrayList<List<TopologyEvent>> events = new ArrayList<List<TopologyEvent>>();

    @Override
    public void handleTopologyEvent( List<TopologyEvent> events ) {
      this.events.add( events );
    }

  }

}
