/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway;

import org.apache.knox.test.category.ReleaseTest;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.ssl.KeyStoreTestUtil;
import org.apache.knox.test.TestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertNotNull;

import static org.hamcrest.MatcherAssert.assertThat;

@Category(ReleaseTest.class)
public class ShellTest {
  private static MiniDFSCluster miniDFSCluster;
  private static HdfsConfiguration configuration;
  private static int nameNodeHttpPort;
  private static String userName;

  private static GatewayTestDriver driver = new GatewayTestDriver();
  private static File baseDir;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    nameNodeHttpPort = TestUtils.findFreePort();
    configuration = new HdfsConfiguration();
    baseDir = new File(KeyStoreTestUtil.getClasspathDir(ShellTest.class));
    System.setProperty(MiniDFSCluster.PROP_TEST_BUILD_DATA, baseDir.getAbsolutePath());
    miniDFSCluster = new MiniDFSCluster.Builder(configuration)
        .nameNodePort(TestUtils.findFreePort())
        .nameNodeHttpPort(nameNodeHttpPort)
        .numDataNodes(2)
        .format(true)
        .racks(null)
        .build();
    userName = UserGroupInformation.createUserForTesting("guest", new String[] {"users"}).getUserName();
    assertNotNull(userName);

    setupKnox();
  }

  private static void setupKnox() throws Exception {
    System.setProperty("gateway.hadoop.kerberos.secured", "false");
    GatewayTestConfig config = new GatewayTestConfig();
    config.setGatewayPath( "gateway" );
    config.setHadoopKerberosSecured(false);
    driver.setResourceBase(ShellTest.class);
    driver.setupLdap(0);
    driver.setupGateway(config, "cluster", createTopology(), true);
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    miniDFSCluster.shutdown();
    driver.cleanup();
  }

  @Test
  public void basicInsecureShell() throws Exception {
    testPutGetScript("InsecureWebHdfsPutGet.groovy");
  }

  private void testPutGetScript(String script) throws IOException, URISyntaxException {
    DistributedFileSystem fileSystem = miniDFSCluster.getFileSystem();
    Path dir = new Path("/user/guest/example");
    fileSystem.delete(dir, true);
    fileSystem.mkdirs(dir, new FsPermission("777"));
    fileSystem.setOwner(dir, "guest", "users");
    Binding binding = new Binding();
    binding.setProperty("gateway", driver.getClusterUrl());
    URL readme = driver.getResourceUrl("README");
    File file = new File(readme.toURI());
    binding.setProperty("file", file.getAbsolutePath());
    GroovyShell shell = new GroovyShell(binding);
    shell.evaluate(driver.getResourceUrl(script).toURI());
    String status = (String) binding.getProperty("status");
    assertNotNull(status);
    String fetchedFile = (String) binding.getProperty("fetchedFile");
    assertNotNull(fetchedFile);
    assertThat(fetchedFile, containsString("README"));
  }

  @Test
  public void basicSecureShell() throws Exception {
    testPutGetScript("WebHdfsPutGet.groovy");
  }

  /**
   * Creates a topology that is deployed to the gateway instance for the test suite.
   * Note that this topology is shared by all of the test methods in this suite.
   * @return A populated XML structure for a topology file.
   */
  private static XMLTag createTopology() {
    return XMLDoc.newDocument(true)
        .addRoot("topology")
        .addTag( "gateway" )
        .addTag( "provider" )
        .addTag("role").addText("webappsec")
        .addTag("name").addText("WebAppSec")
        .addTag("enabled").addText("true")
        .addTag( "param" )
        .addTag("name").addText("csrf.enabled")
        .addTag("value").addText("false").gotoParent().gotoParent()
        .addTag("provider")
        .addTag("role").addText("authentication")
        .addTag("name").addText("ShiroProvider")
        .addTag("enabled").addText("true")
        .addTag( "param" )
        .addTag("name").addText("main.ldapRealm")
        .addTag("value").addText("org.apache.knox.gateway.shirorealm.KnoxLdapRealm").gotoParent()
        .addTag( "param" )
        .addTag("name").addText("main.ldapRealm.userDnTemplate")
        .addTag( "value" ).addText("uid={0},ou=people,dc=hadoop,dc=apache,dc=org").gotoParent()
        .addTag( "param" )
        .addTag("name").addText("main.ldapRealm.contextFactory.url")
        .addTag( "value" ).addText(driver.getLdapUrl()).gotoParent()
        .addTag( "param" )
        .addTag("name").addText("main.ldapRealm.contextFactory.authenticationMechanism")
        .addTag( "value" ).addText("simple").gotoParent()
        .addTag( "param" )
        .addTag("name").addText("urls./**")
        .addTag( "value" ).addText("authcBasic").gotoParent().gotoParent()
        .addTag("provider")
        .addTag("role").addText("identity-assertion")
        .addTag("enabled").addText("true")
        .addTag("name").addText("Default").gotoParent()
        .addTag("provider")
        .addTag( "role" ).addText( "authorization" )
        .addTag( "enabled" ).addText( "true" )
        .addTag("name").addText("AclsAuthz").gotoParent()
        .addTag("param")
        .addTag("name").addText( "webhdfs-acl" )
        .addTag("value").addText("hdfs;*;*").gotoParent()
        .gotoRoot()
        .addTag("service")
        .addTag("role").addText("WEBHDFS")
        .addTag("url").addText("http://localhost:" + nameNodeHttpPort + "/webhdfs/").gotoParent()
        .gotoRoot();
  }
}
