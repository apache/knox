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
package org.apache.hadoop.gateway.security.ldap;

import org.apache.commons.io.FileUtils;
import org.apache.directory.api.ldap.model.entry.DefaultModification;
import org.apache.directory.api.ldap.model.entry.ModificationOperation;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.server.core.api.CoreSession;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.partition.Partition;
import org.apache.directory.server.core.factory.DirectoryServiceFactory;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.store.LdifFileLoader;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.directory.server.protocol.shared.transport.Transport;
import org.apache.log4j.PropertyConfigurator;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.ServerSocket;
import java.util.UUID;

public class SimpleLdapDirectoryServer {

  private DirectoryServiceFactory factory;

  private DirectoryService service;

  private LdapServer server;

  public SimpleLdapDirectoryServer( String rootDn, File usersLdif, Transport... transports ) throws Exception {
    if( !usersLdif.exists() ) {
      throw new FileNotFoundException( usersLdif.getAbsolutePath() );
    }

    factory = new SimpleDirectoryServiceFactory();
    factory.init( UUID.randomUUID().toString() );
    service = factory.getDirectoryService();

    enabledPosixSchema( service );

    Partition partition = factory.getPartitionFactory().createPartition(
        service.getSchemaManager(), service.getDnFactory(), "users", rootDn, 500,
        service.getInstanceLayout().getInstanceDirectory() );
    service.addPartition( partition );

    CoreSession session = service.getAdminSession();
    LdifFileLoader lfl = new LdifFileLoader( session, usersLdif, null );
    lfl.execute();

    server = new LdapServer();
    server.setTransports( transports );
    server.setDirectoryService( service );
  }

  private static void enabledPosixSchema( DirectoryService service ) throws LdapException {
    service.getSchemaManager().getLoadedSchema( "nis" ).enable();
    service.getAdminSession().modify(
        new Dn( "cn=nis,ou=schema" ),
        new DefaultModification( ModificationOperation.REPLACE_ATTRIBUTE, "m-disabled", "FALSE" ) );
  }

  public void start() throws Exception {
    service.startup();
    server.start();
  }

  public void stop( boolean clean ) throws Exception {
    server.stop();
    service.shutdown();
    if( clean ) {
      FileUtils.deleteDirectory( service.getInstanceLayout().getInstanceDirectory() );
    }
  }

  public static void main( String[] args ) throws Exception {
    PropertyConfigurator.configure( System.getProperty( "log4j.configuration" ) );

    SimpleLdapDirectoryServer ldap;

    File file;
    if ( args.length < 1 ) {
      file = new File( "conf/users.ldif" );
    } else {
      File dir = new File( args[0] );
      if( !dir.exists() || !dir.isDirectory() ) {
        throw new FileNotFoundException( dir.getAbsolutePath() );
      }
      file = new File( dir, "users.ldif" );
    }

    if( !file.exists() || !file.canRead() ) {
      throw new FileNotFoundException( file.getAbsolutePath() );
    }

    int port = 33389;

    // Make sure the port is free.
    ServerSocket socket = new ServerSocket( port );
    socket.close();

    TcpTransport transport = new TcpTransport( port );
    ldap = new SimpleLdapDirectoryServer( "dc=hadoop,dc=apache,dc=org", file, transport );
    ldap.start();
  }

}
