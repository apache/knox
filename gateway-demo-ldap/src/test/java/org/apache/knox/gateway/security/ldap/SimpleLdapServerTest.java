/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.knox.gateway.security.ldap;

import org.apache.directory.api.ldap.model.exception.LdapAuthenticationException;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.LdapNetworkConnection;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SimpleLdapServerTest {
  private static int port;
  private static File ldifFile;
  private static TcpTransport ldapTransport;
  private static SimpleLdapDirectoryServer ldap;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    ldifFile = new File( SimpleLdapServerTest.class.getResource( "/users.ldif" ).toURI());
    ldapTransport = new TcpTransport( 0 );
    ldap = new SimpleLdapDirectoryServer( "dc=hadoop,dc=apache,dc=org", ldifFile, ldapTransport );
    ldap.start();
    port = ldapTransport.getAcceptor().getLocalAddress().getPort();
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    if( ldap != null ) {
      ldap.stop( true );
    }
  }

  @Test
  public void testBind() throws LdapException, IOException {
    try(LdapConnection connection = new LdapNetworkConnection( "localhost", port )) {
      connection.bind( "uid=guest,ou=people,dc=hadoop,dc=apache,dc=org", "guest-password" );
    }

    try(LdapConnection connection = new LdapNetworkConnection( "localhost", port )) {
      connection.bind( "uid=nobody,ou=people,dc=hadoop,dc=apache,dc=org", "guest-password" );
      fail( "Expected LdapAuthenticationException" );
    } catch ( LdapAuthenticationException e ) {
      // Expected
      assertEquals("INVALID_CREDENTIALS: Bind failed: ERR_229 " +
                       "Cannot authenticate user uid=nobody,ou=people,dc=hadoop,dc=apache,dc=org",
          e.getMessage());
    }

    try(LdapConnection connection = new LdapNetworkConnection( "localhost", port )) {
      connection.bind( "uid=guest,ou=people,dc=hadoop,dc=apache,dc=org", "wrong-password" );
      fail( "Expected LdapAuthenticationException" );
    } catch ( LdapAuthenticationException e ) {
      // Expected
      assertEquals("INVALID_CREDENTIALS: Bind failed: ERR_229 " +
                       "Cannot authenticate user uid=guest,ou=people,dc=hadoop,dc=apache,dc=org",
          e.getMessage());
    }
  }
}