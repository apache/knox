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
package org.apache.hadoop.gateway.security;

import com.google.common.io.Files;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.partition.Partition;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.store.LdifFileLoader;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.directory.shared.ldap.name.LdapDN;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.net.URL;

public class EmbeddedApacheDirectoryServer {

  private DefaultDirectoryService directory;

  private LdapServer transport;

  private Partition partition;

  public static void main( String[] args ) throws Exception {
    EmbeddedApacheDirectoryServer ldap;
    ldap = new EmbeddedApacheDirectoryServer( "dc=hadoop,dc=apache,dc=org", null, 33389 );
    ldap.start();
    
    URL userUrl = null;
    if ( args.length > 0 ) {
      File file = new File( args[0], "users.ldif" );
      if( !file.exists() || !file.canRead() ) {
        throw new FileNotFoundException( file.getAbsolutePath() );
      }
      userUrl = file.toURL();
    } else {
      userUrl = Thread.currentThread().getContextClassLoader().getResource( "users.ldif" );
      if( userUrl == null ) {
        throw new FileNotFoundException( "classpath:user.ldif" );
      } else if( userUrl.toExternalForm().startsWith( "jar:file:" ) ) {
        throw new IllegalArgumentException( "Loading user.ldif from within jar unsupported. Provide directory containing user.ldif as first argument. " );
      }
    }
    ldap.loadLdif( userUrl );
  }

  public EmbeddedApacheDirectoryServer( String rootDn, File workDir, int ldapPort ) throws Exception {
    partition = createRootParition( rootDn );
    directory = createDirectory( partition, workDir );
    transport = createTransport( directory, ldapPort );
  }

  public LdapServer getTransport() {
    return transport;
  }

  public DirectoryService getDirectory() {
    return directory;
  }

  public Partition getPartition() {
    return partition;
  }


  private static Partition createRootParition( String dn ) {
    JdbmPartition partition = new JdbmPartition();
    partition.setId( "root" );
    partition.setSuffix( dn );
    return partition;
  }

  private static DefaultDirectoryService createDirectory( Partition rootPartition, File workDir ) throws Exception {
    DefaultDirectoryService directory = new DefaultDirectoryService();
    directory.addPartition( rootPartition );
    directory.setExitVmOnShutdown( false );
    directory.setShutdownHookEnabled( true );
    directory.getChangeLog().setEnabled( false );
    directory.setDenormalizeOpAttrsEnabled( true );
    directory.setWorkingDirectory( initWorkDir( workDir ) );
    return directory;
  }

  private static LdapServer createTransport( DirectoryService directory, int ldapPort ) {
    LdapServer transport = new LdapServer();
    transport.setDirectoryService( directory );
    if( ldapPort <= 0 ) {
      transport.setTransports( new TcpTransport() );
    } else {
      transport.setTransports( new TcpTransport( ldapPort ) );
    }
    return transport;
  }

  private static File initWorkDir( File workDir ) {
    File dir = workDir;
    if( dir == null ) {
      dir = new File( System.getProperty( "user.dir" ), EmbeddedApacheDirectoryServer.class.getName() );
    }
    if( dir.exists() ) {
      dir = Files.createTempDir();
    }
    return dir;
  }

  public void start() throws Exception {
    directory.startup();
    transport.start();

    LdapDN dn = partition.getSuffixDn();
    String dc = dn.getUpName().split("[=,]")[1];
    ServerEntry entry = directory.newEntry( dn );
    entry.add( "objectClass", "top", "domain", "extensibleObject" );
    entry.add( "dc", dc );
    directory.getAdminSession().add( entry );
  }

  public void stop() throws Exception {
    try {
      transport.stop();
      directory.shutdown();
    } finally {
      deleteDir( directory.getWorkingDirectory() );
    }
  }

  public void loadLdif( URL url ) throws URISyntaxException {
    File file = new File( url.toURI() );
    LdifFileLoader loader = new LdifFileLoader(
        directory.getAdminSession(), file, null, Thread.currentThread().getContextClassLoader() );
    loader.execute();
  }

  private static boolean deleteDir( File dir ) {
    if( dir.isDirectory() ) {
      String[] children = dir.list();
      for( String child : children ) {
        boolean success = deleteDir( new File( dir, child ) );
        if( !success ) {
          return false;
        }
      }
    }
    return dir.delete();
  }

}
