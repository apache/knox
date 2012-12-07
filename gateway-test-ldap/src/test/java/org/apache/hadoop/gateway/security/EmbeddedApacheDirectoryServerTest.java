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

import org.apache.hadoop.test.category.ManualTests;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.IOException;
import java.net.URL;
import java.util.Hashtable;

import static org.junit.Assert.fail;

@Category( ManualTests.class )
public class EmbeddedApacheDirectoryServerTest {

  private static EmbeddedApacheDirectoryServer ldap;

  @Test
  public void demoWait() throws IOException {
    System.out.println( "Press any key to exit LDAP server." );
    System.in.read();
  }

  @BeforeClass
  public static void setupSuite() throws Exception{
    URL usersUrl = ClassLoader.getSystemResource( "users.ldif" );
    ldap = new EmbeddedApacheDirectoryServer( "dc=hadoop,dc=apache,dc=org", null, 33389 );
    ldap.start();
    ldap.loadLdif( usersUrl );
  }

  @AfterClass
  public static void cleanupSuite() throws Exception {
    ldap.stop();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testJndiLdapAuthenticate() {

    Hashtable env = new Hashtable();
    env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" );
    env.put( Context.PROVIDER_URL, "ldap://localhost:33389" );
    env.put( Context.SECURITY_AUTHENTICATION, "simple" );
    env.put( Context.SECURITY_PRINCIPAL, "uid=allowedUser,ou=people,dc=hadoop,dc=apache,dc=org" );
    env.put( Context.SECURITY_CREDENTIALS, "password" );

    try {
      DirContext ctx = new InitialDirContext( env );
      ctx.close();
    } catch( NamingException e ) {
      e.printStackTrace();
      fail( "Should have been able to find the allowedUser and create initial context." );
    }

    env = new Hashtable();
    env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" );
    env.put( Context.PROVIDER_URL, "ldap://localhost:33389" );
    env.put( Context.SECURITY_AUTHENTICATION, "simple" );
    env.put( Context.SECURITY_PRINCIPAL, "uid=allowedUser,ou=people,dc=hadoop,dc=apache,dc=org" );
    env.put( Context.SECURITY_CREDENTIALS, "invalid-password" );

    try {
      DirContext ctx = new InitialDirContext( env );
      fail( "Should have thrown a NamingException to indicate invalid credentials." );
    } catch( NamingException e ) {
      // This exception is expected.
    }
  }

}
