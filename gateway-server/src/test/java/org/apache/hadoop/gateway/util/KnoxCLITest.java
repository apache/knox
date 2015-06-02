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
package org.apache.hadoop.gateway.util;

import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.gateway.config.impl.GatewayConfigImpl;
import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.security.AliasService;
import org.apache.hadoop.gateway.services.security.MasterService;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author larry
 *
 */
public class KnoxCLITest {
  private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();

  @Before
  public void setup() throws Exception {
    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));
  }

  @Test
  public void testSuccessfulAlaisLifecycle() throws Exception {
    outContent.reset();
    String[] args1 = {"create-alias", "alias1", "--value", "testvalue1", "--master", "master"};
    int rc = 0;
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(new GatewayConfigImpl());
    rc = cli.run(args1);
    assertEquals(0, rc);
    assertTrue(outContent.toString(), outContent.toString().contains("alias1 has been successfully " +
        "created."));

    outContent.reset();
    String[] args2 = {"list-alias", "--master", 
        "master"};
    rc = cli.run(args2);
    assertEquals(0, rc);
    assertTrue(outContent.toString(), outContent.toString().contains("alias1"));

    outContent.reset();
    String[] args4 = {"delete-alias", "alias1", "--master", 
      "master"};
    rc = cli.run(args4);
    assertEquals(0, rc);
    assertTrue(outContent.toString(), outContent.toString().contains("alias1 has been successfully " +
        "deleted."));

    outContent.reset();
    rc = cli.run(args2);
    assertEquals(0, rc);
    assertFalse(outContent.toString(), outContent.toString().contains("alias1"));
  }
  
  @Test
  public void testListAndDeleteOfAliasForInvalidClusterName() throws Exception {
    outContent.reset();
    String[] args1 =
        { "create-alias", "alias1", "--cluster", "cluster1", "--value", "testvalue1", "--master",
            "master" };
    int rc = 0;
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(new GatewayConfigImpl());
    rc = cli.run(args1);
    assertEquals(0, rc);
    assertTrue(outContent.toString(), outContent.toString().contains(
      "alias1 has been successfully " + "created."));

    outContent.reset();
    String[] args2 = { "list-alias", "--cluster", "Invalidcluster1", "--master", "master" };
    rc = cli.run(args2);
    assertEquals(0, rc);
    System.out.println(outContent.toString());
    assertTrue(outContent.toString(),
      outContent.toString().contains("Invalid cluster name provided: Invalidcluster1"));

    outContent.reset();
    String[] args4 =
        { "delete-alias", "alias1", "--cluster", "Invalidcluster1", "--master", "master" };
    rc = cli.run(args4);
    assertEquals(0, rc);
    assertTrue(outContent.toString(),
      outContent.toString().contains("Invalid cluster name provided: Invalidcluster1"));

  }

  @Test
  public void testForInvalidArgument() throws Exception {
    outContent.reset();
    String[] args1 = { "--value", "testvalue1", "--master", "master" };
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(new GatewayConfigImpl());
    int rc = cli.run(args1);
    assertEquals(-2, rc);
    assertTrue(outContent.toString().contains("ERROR: Invalid Command"));
  }

  @Test
  public void testListAndDeleteOfAliasForValidClusterName() throws Exception {
    outContent.reset();
    String[] args1 =
        { "create-alias", "alias1", "--cluster", "cluster1", "--value", "testvalue1", "--master",
            "master" };
    int rc = 0;
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(new GatewayConfigImpl());
    rc = cli.run(args1);
    assertEquals(0, rc);
    assertTrue(outContent.toString(), outContent.toString().contains(
      "alias1 has been successfully " + "created."));

    outContent.reset();
    String[] args2 = { "list-alias", "--cluster", "cluster1", "--master", "master" };
    rc = cli.run(args2);
    assertEquals(0, rc);
    System.out.println(outContent.toString());
    assertTrue(outContent.toString(), outContent.toString().contains("alias1"));

    outContent.reset();
    String[] args4 =
        { "delete-alias", "alias1", "--cluster", "cluster1", "--master", "master" };
    rc = cli.run(args4);
    assertEquals(0, rc);
    assertTrue(outContent.toString(), outContent.toString().contains(
      "alias1 has been successfully " + "deleted."));

    outContent.reset();
    rc = cli.run(args2);
    assertEquals(0, rc);
    assertFalse(outContent.toString(), outContent.toString().contains("alias1"));

  }

  @Test
  public void testGatewayAndClusterStores() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();
    FileUtils.deleteQuietly( new File( config.getGatewaySecurityDir() ) );

    outContent.reset();
    String[] gwCreateArgs = {"create-alias", "alias1", "--value", "testvalue1", "--master", "master"};
    int rc = 0;
    KnoxCLI cli = new KnoxCLI();
    cli.setConf( config );
    rc = cli.run(gwCreateArgs);
    assertEquals(0, rc);
    assertTrue(outContent.toString(), outContent.toString().contains("alias1 has been successfully " +
        "created."));

    AliasService as = cli.getGatewayServices().getService(GatewayServices.ALIAS_SERVICE);

    outContent.reset();
    String[] clusterCreateArgs = {"create-alias", "alias2", "--value", "testvalue1", "--cluster", "test", 
        "--master", "master"};
    cli = new KnoxCLI();
    cli.setConf( config );
    rc = cli.run(clusterCreateArgs);
    assertEquals(0, rc);
    assertTrue(outContent.toString(), outContent.toString().contains("alias2 has been successfully " +
        "created."));

    outContent.reset();
    String[] args2 = {"list-alias", "--master", "master"};
    cli = new KnoxCLI();
    rc = cli.run(args2);
    assertEquals(0, rc);
    assertFalse(outContent.toString(), outContent.toString().contains("alias2"));
    assertTrue(outContent.toString(), outContent.toString().contains("alias1"));

    char[] passwordChars = as.getPasswordFromAliasForCluster("test", "alias2");
    assertNotNull(passwordChars);
    assertTrue(new String(passwordChars), "testvalue1".equals(new String(passwordChars)));

    outContent.reset();
    String[] args1 = {"list-alias", "--cluster", "test", "--master", "master"};
    cli = new KnoxCLI();
    rc = cli.run(args1);
    assertEquals(0, rc);
    assertFalse(outContent.toString(), outContent.toString().contains("alias1"));
    assertTrue(outContent.toString(), outContent.toString().contains("alias2"));

    outContent.reset();
    String[] args4 = {"delete-alias", "alias1", "--master", "master"};
    cli = new KnoxCLI();
    rc = cli.run(args4);
    assertEquals(0, rc);
    assertTrue(outContent.toString(), outContent.toString().contains("alias1 has been successfully " +
        "deleted."));
    
    outContent.reset();
    String[] args5 = {"delete-alias", "alias2", "--cluster", "test", "--master", "master"};
    cli = new KnoxCLI();
    rc = cli.run(args5);
    assertEquals(0, rc);
    assertTrue(outContent.toString(), outContent.toString().contains("alias2 has been successfully " +
        "deleted."));
  }

  private void createTestMaster() throws Exception {
    outContent.reset();
    String[] args = new String[]{ "create-master", "--master", "master", "--force" };
    KnoxCLI cli = new KnoxCLI();
    int rc = cli.run(args);
    assertThat( rc, is( 0 ) );
    MasterService ms = cli.getGatewayServices().getService("MasterService");
    String master = String.copyValueOf( ms.getMasterSecret() );
    assertThat( master, is( "master" ) );
    assertThat( outContent.toString(), containsString( "Master secret has been persisted to disk." ) );
  }

  @Test
  public void testCreateSelfSignedCert() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();
    FileUtils.deleteQuietly( new File( config.getGatewaySecurityDir() ) );
    createTestMaster();
    outContent.reset();
    KnoxCLI cli = new KnoxCLI();
    cli.setConf( config );
    String[] gwCreateArgs = {"create-cert", "--hostname", "hostname1", "--master", "master"};
    int rc = 0;
    rc = cli.run(gwCreateArgs);
    assertEquals(0, rc);
    assertTrue(outContent.toString(), outContent.toString().contains("gateway-identity has been successfully " +
        "created."));
  }

  @Test
  public void testCreateMaster() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();
    FileUtils.deleteQuietly( new File( config.getGatewaySecurityDir() ) );
    outContent.reset();
    String[] args = {"create-master", "--master", "master"};
    int rc = 0;
    KnoxCLI cli = new KnoxCLI();
    cli.setConf( config );
    rc = cli.run(args);
    assertEquals(0, rc);
    MasterService ms = cli.getGatewayServices().getService("MasterService");
    // assertTrue(ms.getClass().getName(), ms.getClass().getName().equals("kjdfhgjkhfdgjkh"));
    assertTrue( new String( ms.getMasterSecret() ), "master".equals( new String( ms.getMasterSecret() ) ) );
    assertTrue(outContent.toString(), outContent.toString().contains("Master secret has been persisted to disk."));
  }

  @Test
  public void testCreateMasterGenerate() throws Exception {
    String[] args = {"create-master", "--generate" };
    int rc = 0;
    GatewayConfigImpl config = new GatewayConfigImpl();
    File masterFile = new File( config.getGatewaySecurityDir(), "master" );

    // Need to delete the master file so that the change isn't ignored.
    if( masterFile.exists() ) {
      assertThat( "Failed to delete existing master file.", masterFile.delete(), is( true ) );
    }
    outContent.reset();
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(config);
    rc = cli.run(args);
    assertThat( rc, is( 0 ) );
    MasterService ms = cli.getGatewayServices().getService("MasterService");
    String master = String.copyValueOf( ms.getMasterSecret() );
    assertThat( master.length(), is( 36 ) );
    assertThat( master.indexOf( '-' ), is( 8 ) );
    assertThat( master.indexOf( '-', 9 ), is( 13 ) );
    assertThat( master.indexOf( '-', 14 ), is( 18 ) );
    assertThat( master.indexOf( '-', 19 ), is( 23 ) );
    assertThat( UUID.fromString( master ), notNullValue() );
    assertThat( outContent.toString(), containsString( "Master secret has been persisted to disk." ) );

    // Need to delete the master file so that the change isn't ignored.
    if( masterFile.exists() ) {
      assertThat( "Failed to delete existing master file.", masterFile.delete(), is( true ) );
    }
    outContent.reset();
    cli = new KnoxCLI();
    rc = cli.run(args);
    ms = cli.getGatewayServices().getService("MasterService");
    String master2 = String.copyValueOf( ms.getMasterSecret() );
    assertThat( master2.length(), is( 36 ) );
    assertThat( UUID.fromString( master2 ), notNullValue() );
    assertThat( master2, not( is( master ) ) );
    assertThat( rc, is( 0 ) );
    assertThat(outContent.toString(), containsString("Master secret has been persisted to disk."));
  }

  @Test
  public void testCreateMasterForce() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();
    File masterFile = new File( config.getGatewaySecurityDir(), "master" );

    // Need to delete the master file so that the change isn't ignored.
    if( masterFile.exists() ) {
      assertThat( "Failed to delete existing master file.", masterFile.delete(), is( true ) );
    }

    KnoxCLI cli = new KnoxCLI();
    cli.setConf(config);
    MasterService ms;
    int rc = 0;
    outContent.reset();

    String[] args = { "create-master", "--master", "test-master-1" };

    rc = cli.run(args);
    assertThat( rc, is( 0 ) );
    ms = cli.getGatewayServices().getService("MasterService");
    String master = String.copyValueOf( ms.getMasterSecret() );
    assertThat( master, is( "test-master-1" ) );
    assertThat( outContent.toString(), containsString( "Master secret has been persisted to disk." ) );

    outContent.reset();
    rc = cli.run(args);
    assertThat( rc, not(is(0)) );
    assertThat( outContent.toString(), containsString( "Master secret is already present on disk." ) );

    outContent.reset();
    args = new String[]{ "create-master", "--master", "test-master-2", "--force" };
    rc = cli.run(args);
    assertThat( rc, is( 0 ) );
    ms = cli.getGatewayServices().getService("MasterService");
    master = String.copyValueOf( ms.getMasterSecret() );
    assertThat( master, is( "test-master-2" ) );
    assertThat( outContent.toString(), containsString( "Master secret has been persisted to disk." ) );
  }

  @Test
  public void testListTopology() throws Exception {

    GatewayConfigMock config = new GatewayConfigMock();
    URL topoURL = ClassLoader.getSystemResource("conf-demo/conf/topologies/admin.xml");
    config.setConfDir( new File(topoURL.getFile()).getParentFile().getParent() );
    String args[] = {"list-topologies", "--master", "knox"};

    KnoxCLI cli = new KnoxCLI();
    cli.setConf( config );

    cli.run( args );
    assertThat(outContent.toString(), containsString("sandbox"));
    assertThat(outContent.toString(), containsString("admin"));
  }

  private class GatewayConfigMock extends GatewayConfigImpl{
    private String confDir;
    public void setConfDir(String location) {
      confDir = location;
    }

    @Override
    public String getGatewayConfDir(){
      return confDir;
    }
  }

  private static XMLTag createBadTopology() {
    XMLTag xml = XMLDoc.newDocument(true)
        .addRoot( "topology" )
        .addTag( "gateway" )

        .addTag( "provider" )
        .addTag( "role" ).addText( "authentication" )
        .addTag( "name" ).addText( "ShiroProvider" )
        .addTag( "enabled" ).addText( "123" )
        .addTag( "param" )
        .addTag( "name" ).addText( "" )
        .addTag( "value" ).addText( "org.apache.hadoop.gateway.shirorealm.KnoxLdapRealm" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.userDnTemplate" )
        .addTag( "value" ).addText( "uid={0},ou=people,dc=hadoop,dc=apache,dc=org" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.url" )
        .addTag( "value" ).addText( "ldap://localhost:8443" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.authenticationMechanism" )
        .addTag( "value" ).addText( "simple" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "urls./**" )
        .addTag( "value" ).addText( "authcBasic" ).gotoParent().gotoParent()
        .addTag( "provider" )
        .addTag( "role" ).addText( "identity-assertion" )
        .addTag( "enabled" ).addText( "vvv" )
        .addTag( "name" ).addText( "Default" ).gotoParent()
        .addTag( "provider" )
        .gotoRoot()
        .addTag( "service" )
        .addTag( "role" ).addText( "test-service-role" )
        .gotoRoot();
    return xml;
  }

  private static XMLTag createGoodTopology() {
    XMLTag xml = XMLDoc.newDocument( true )
        .addRoot( "topology" )
        .addTag( "gateway" )

        .addTag( "provider" )
        .addTag( "role" ).addText( "authentication" )
        .addTag( "name" ).addText( "ShiroProvider" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm" )
        .addTag( "value" ).addText( "org.apache.hadoop.gateway.shirorealm.KnoxLdapRealm" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.userDnTemplate" )
        .addTag( "value" ).addText( "uid={0},ou=people,dc=hadoop,dc=apache,dc=org" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.url" )
        .addTag( "value" ).addText( "ldap://localhost:8443").gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.authenticationMechanism" )
        .addTag( "value" ).addText( "simple" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "urls./**" )
        .addTag( "value" ).addText( "authcBasic" ).gotoParent().gotoParent()
        .addTag( "provider" )
        .addTag( "role" ).addText( "identity-assertion" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "name" ).addText( "Default" ).gotoParent()
        .addTag( "provider" )
        .gotoRoot()
        .addTag( "service" )
        .addTag( "role" ).addText( "test-service-role" )
        .gotoRoot();
    return xml;
  }

  private File writeTestTopology( String name, XMLTag xml ) throws IOException {
    // Create the test topology.

    GatewayConfigMock config = new GatewayConfigMock();
    URL topoURL = ClassLoader.getSystemResource("conf-demo/conf/topologies/admin.xml");
    config.setConfDir( new File(topoURL.getFile()).getParentFile().getParent() );

    File tempFile = new File( config.getGatewayTopologyDir(), name + ".xml." + UUID.randomUUID() );
    FileOutputStream stream = new FileOutputStream( tempFile );
    xml.toStream( stream );
    stream.close();
    File descriptor = new File( config.getGatewayTopologyDir(), name + ".xml" );
    tempFile.renameTo( descriptor );
    return descriptor;
  }

  @Test
  public void testValidateTopology() throws Exception {

    GatewayConfigMock config = new GatewayConfigMock();
    URL topoURL = ClassLoader.getSystemResource("conf-demo/conf/topologies/admin.xml");
    config.setConfDir( new File(topoURL.getFile()).getParentFile().getParent() );
    String args[] = {"validate-topology", "--master", "knox", "--cluster", "sandbox"};

    KnoxCLI cli = new KnoxCLI();
    cli.setConf( config );
    cli.run( args );

    assertThat(outContent.toString(), containsString(config.getGatewayTopologyDir()));
    assertThat(outContent.toString(), containsString("sandbox"));
    assertThat(outContent.toString(), containsString("success"));
    outContent.reset();


    String args2[] = {"validate-topology", "--master", "knox", "--cluster", "NotATopology"};
    cli.run(args2);

    assertThat(outContent.toString(), containsString("NotATopology"));
    assertThat(outContent.toString(), containsString("does not exist"));
    outContent.reset();

    String args3[] = {"validate-topology", "--master", "knox", "--path", config.getGatewayTopologyDir() + "/admin.xml"};
    cli.run(args3);

    assertThat(outContent.toString(), containsString("admin"));
    assertThat(outContent.toString(), containsString("success"));
    outContent.reset();

    String args4[] = {"validate-topology", "--master", "knox", "--path", "not/a/path"};
    cli.run(args4);
    assertThat(outContent.toString(), containsString("does not exist"));
    assertThat(outContent.toString(), containsString("not/a/path"));
  }

  @Test
  public void testValidateTopologyOutput() throws Exception {

    File bad = writeTestTopology( "test-cluster-bad", createBadTopology() );
    File good = writeTestTopology( "test-cluster-good", createGoodTopology() );

    GatewayConfigMock config = new GatewayConfigMock();
    URL topoURL = ClassLoader.getSystemResource("conf-demo/conf/topologies/admin.xml");
    config.setConfDir( new File(topoURL.getFile()).getParentFile().getParent() );
    String args[] = {"validate-topology", "--master", "knox", "--cluster", "test-cluster-bad"};

    KnoxCLI cli = new KnoxCLI();
    cli.setConf( config );
    cli.run( args );

    assertThat(outContent.toString(), containsString(config.getGatewayTopologyDir()));
    assertThat(outContent.toString(), containsString("test-cluster-bad"));
    assertThat(outContent.toString(), containsString("unsuccessful"));
    assertThat(outContent.toString(), containsString("Invalid content"));
    assertThat(outContent.toString(), containsString("Line"));


    outContent.reset();

    String args2[] = {"validate-topology", "--master", "knox", "--cluster", "test-cluster-good"};

    cli.run(args2);

    assertThat(outContent.toString(), containsString(config.getGatewayTopologyDir()));
    assertThat(outContent.toString(), containsString("success"));
    assertThat(outContent.toString(), containsString("test-cluster-good"));


  }

}
