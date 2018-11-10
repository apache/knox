/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.  The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway;

import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.http.HttpConfig;
import org.apache.hadoop.minikdc.MiniKdc;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.ssl.KeyStoreTestUtil;
import org.apache.knox.test.TestUtils;
import org.apache.knox.test.category.ReleaseTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Properties;

import static org.apache.hadoop.fs.CommonConfigurationKeysPublic.IPC_CLIENT_CONNECT_MAX_RETRIES_KEY;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_BLOCK_ACCESS_TOKEN_ENABLE_KEY;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_CLIENT_HTTPS_KEYSTORE_RESOURCE_KEY;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_DATANODE_HTTPS_ADDRESS_KEY;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_DATANODE_KERBEROS_PRINCIPAL_KEY;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_DATANODE_KEYTAB_FILE_KEY;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_DATA_ENCRYPTION_ALGORITHM_KEY;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_HTTP_POLICY_KEY;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_JOURNALNODE_HTTPS_ADDRESS_KEY;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_JOURNALNODE_KERBEROS_INTERNAL_SPNEGO_PRINCIPAL_KEY;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_JOURNALNODE_KERBEROS_PRINCIPAL_KEY;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_JOURNALNODE_KEYTAB_FILE_KEY;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_NAMENODE_HTTPS_ADDRESS_KEY;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_NAMENODE_KERBEROS_PRINCIPAL_KEY;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_NAMENODE_KEYTAB_FILE_KEY;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_SERVER_HTTPS_KEYSTORE_RESOURCE_KEY;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_WEB_AUTHENTICATION_KERBEROS_PRINCIPAL_KEY;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test for KnoxShell Kerberos support
 */
@Category(ReleaseTest.class)
public class SecureKnoxShellTest {
  private static final String SCRIPT = "SecureWebHdfsPutGet.groovy";
  private static MiniKdc kdc;
  private static String userName;
  private static HdfsConfiguration configuration;
  private static int nameNodeHttpPort;
  private static File baseDir;
  private static String krb5conf;
  private static String hdfsPrincipal;
  private static String spnegoPrincipal;
  private static String keytab;

  private static MiniDFSCluster miniDFSCluster;
  private static GatewayTestDriver driver = new GatewayTestDriver();

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    baseDir = new File(
        KeyStoreTestUtil.getClasspathDir(SecureKnoxShellTest.class));

    nameNodeHttpPort = TestUtils.findFreePort();
    configuration = new HdfsConfiguration();
    baseDir = new File(
        KeyStoreTestUtil.getClasspathDir(SecureKnoxShellTest.class));
    System.setProperty(MiniDFSCluster.PROP_TEST_BUILD_DATA,
        baseDir.getAbsolutePath());

    initKdc();
    miniDFSCluster = new MiniDFSCluster.Builder(configuration)
        .nameNodePort(TestUtils.findFreePort())
        .nameNodeHttpPort(nameNodeHttpPort).numDataNodes(2).format(true)
        .racks(null).build();

    setupKnox(keytab, hdfsPrincipal);
  }

  private static void initKdc() throws Exception {
    final Properties kdcConf = MiniKdc.createConf();
    kdc = new MiniKdc(kdcConf, baseDir);
    kdc.start();

    userName = UserGroupInformation
        .createUserForTesting("guest", new String[] { "users" }).getUserName();
    final File keytabFile = new File(baseDir, userName + ".keytab");
    keytab = keytabFile.getAbsolutePath();
    // Windows will not reverse name lookup "127.0.0.1" to "localhost".
    final String krbInstance = Path.WINDOWS ? "127.0.0.1" : "localhost";
    kdc.createPrincipal(keytabFile, userName + "/" + krbInstance,
        "HTTP/" + krbInstance);

    hdfsPrincipal =
        userName + "/" + krbInstance + "@" + kdc.getRealm();
    spnegoPrincipal = "HTTP/" + krbInstance + "@" + kdc.getRealm();

    configuration.set(DFS_NAMENODE_KERBEROS_PRINCIPAL_KEY, hdfsPrincipal);
    configuration.set(DFS_NAMENODE_KEYTAB_FILE_KEY, keytab);
    configuration.set(DFS_DATANODE_KERBEROS_PRINCIPAL_KEY, hdfsPrincipal);
    configuration.set(DFS_DATANODE_KEYTAB_FILE_KEY, keytab);
    configuration.set(DFS_WEB_AUTHENTICATION_KERBEROS_PRINCIPAL_KEY, spnegoPrincipal);
    configuration.set(DFS_JOURNALNODE_KEYTAB_FILE_KEY, keytab);
    configuration.set(DFS_JOURNALNODE_KERBEROS_PRINCIPAL_KEY, hdfsPrincipal);
    configuration.set(DFS_JOURNALNODE_KERBEROS_INTERNAL_SPNEGO_PRINCIPAL_KEY, spnegoPrincipal);
    configuration.setBoolean(DFS_BLOCK_ACCESS_TOKEN_ENABLE_KEY, true);
    configuration.set(DFS_DATA_ENCRYPTION_ALGORITHM_KEY, "authentication");
    configuration.set(DFS_HTTP_POLICY_KEY, HttpConfig.Policy.HTTP_AND_HTTPS.name());
    configuration.set(DFS_NAMENODE_HTTPS_ADDRESS_KEY, "localhost:0");
    configuration.set(DFS_DATANODE_HTTPS_ADDRESS_KEY, "localhost:0");
    configuration.set(DFS_JOURNALNODE_HTTPS_ADDRESS_KEY, "localhost:0");
    configuration.setInt(IPC_CLIENT_CONNECT_MAX_RETRIES_KEY, 10);
    configuration.set("hadoop.proxyuser." + userName + ".hosts", "*");
    configuration.set("hadoop.proxyuser." + userName + ".groups", "*");
    configuration.setBoolean("dfs.permissions", true);

    String keystoresDir = baseDir.getAbsolutePath();
    File sslClientConfFile = new File(keystoresDir + "/ssl-client.xml");
    File sslServerConfFile = new File(keystoresDir + "/ssl-server.xml");
    KeyStoreTestUtil.setupSSLConfig(keystoresDir, keystoresDir, configuration, false);
    configuration.set(DFS_CLIENT_HTTPS_KEYSTORE_RESOURCE_KEY,
        sslClientConfFile.getName());
    configuration.set(DFS_SERVER_HTTPS_KEYSTORE_RESOURCE_KEY,
        sslServerConfFile.getName());

    krb5conf = kdc.getKrb5conf().getAbsolutePath();
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    if(kdc != null) {
      kdc.stop();
    }
    if(miniDFSCluster != null) {
      miniDFSCluster.shutdown();
    }
    if(driver != null) {
      driver.cleanup();
    }
  }

  private static File setupJaasConf(File baseDir, String keyTabFile,
      String principal) throws IOException {
    final File file = new File(baseDir, "jaas.conf");
    if (!file.exists()) {
      file.createNewFile();
    } else {
      file.delete();
      file.createNewFile();
    }
    try(OutputStream outputStream = Files.newOutputStream(file.toPath());
        Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
      String content = String.format(Locale.ROOT, "com.sun.security.jgss.initiate {\n"
                                                      + "com.sun.security.auth.module.Krb5LoginModule required\n"
                                                      + "renewTGT=true\n" + "doNotPrompt=true\n" + "useKeyTab=true\n"
                                                      + "keyTab=\"%s\"\n" + "principal=\"%s\"\n" + "isInitiator=true\n"
                                                      + "storeKey=true\n" + "useTicketCache=true\n" +
                                                      "debug=false\n" + "client=true;\n" + "};\n", keyTabFile, principal);
      writer.write(content);
    }
    return file;
  }

  private static void setupKnox(String keytab, String hdfsPrincipal)
      throws Exception {

    File jaasConf = setupJaasConf(baseDir, keytab, hdfsPrincipal);

    System.setProperty("java.security.krb5.conf", kdc.getKrb5conf().getAbsolutePath());
    System.setProperty("java.security.auth.login.config",
        jaasConf.getAbsolutePath());
    System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
    System.setProperty("sun.security.krb5.debug", "false");

    System.setProperty("gateway.hadoop.kerberos.secured", "false");
    GatewayTestConfig config = new GatewayTestConfig();
    config.setGatewayPath("gateway");
    config.setHadoopKerberosSecured(false);

    driver.setResourceBase(SecureKnoxShellTest.class);
    driver.setupLdap(0);
    driver.setupGateway(config, "secure", createSecureTopology(), true);
  }

  /**
   * Creates a Secure topology that is deployed to the gateway instance for the
   * test suite.
   *
   * @return A populated XML structure for a topology file.
   */
  private static XMLTag createSecureTopology() {
    return XMLDoc.newDocument(true).addRoot("topology").addTag("gateway")
        .addTag("provider").addTag("role").addText("authentication")
        .addTag("name").addText("HadoopAuth").addTag("enabled").addText("true")

        .addTag("param").addTag("name").addText("config.prefix").addTag("value")
        .addText("hadoop.auth.config").gotoParent()

        .addTag("param").addTag("name")
        .addText("hadoop.auth.config.signature.secret").addTag("value")
        .addText("knox").gotoParent()

        .addTag("param").addTag("name").addText("hadoop.auth.config.type")
        .addTag("value").addText("kerberos").gotoParent()

        .addTag("param").addTag("name")
        .addText("hadoop.auth.config.simple.anonymous.allowed").addTag("value")
        .addText("false").gotoParent()

        .addTag("param").addTag("name")
        .addText("hadoop.auth.config.token.validity").addTag("value")
        .addText("1800").gotoParent()

        .addTag("param").addTag("name")
        .addText("hadoop.auth.config.cookie.domain").addTag("value")
        .addText("localhost").gotoParent()

        .addTag("param").addTag("name")
        .addText("hadoop.auth.config.cookie.path").addTag("value")
        .addText("gateway/secure").gotoParent()

        .addTag("param").addTag("name")
        .addText("hadoop.auth.config.kerberos.principal").addTag("value")
        .addText(spnegoPrincipal).gotoParent()

        .addTag("param").addTag("name")
        .addText("hadoop.auth.config.kerberos.keytab").addTag("value")
        .addText(keytab).gotoParent()

        .addTag("param").addTag("name")
        .addText("hadoop.auth.config.kerberos.name.rules").addTag("value")
        .addText("DEFAULT").gotoParent().gotoParent()

        .addTag("provider").addTag("role").addText("identity-assertion")
        .addTag("enabled").addText("true").addTag("name").addText("Default")
        .gotoParent().addTag("provider").addTag("role").addText("authorization")
        .addTag("enabled").addText("true").addTag("name").addText("AclsAuthz")
        .gotoParent().gotoParent().gotoRoot()

        .addTag("service").addTag("role").addText("WEBHDFS").addTag("url")
        .addText("http://localhost:" + nameNodeHttpPort + "/webhdfs/")
        .gotoParent().gotoRoot();
  }

  @Test
  public void testCachedTicket() throws Exception {
    webhdfsPutGet();
  }

  /**
   * Do the heavy lifting here.
   */
  private void webhdfsPutGet() throws Exception {
    DistributedFileSystem fileSystem = miniDFSCluster.getFileSystem();
    Path dir = new Path("/user/guest/example");
    fileSystem.delete(dir, true);
    fileSystem.mkdirs(dir, new FsPermission("777"));
    fileSystem.setOwner(dir, "guest", "users");

    final File jaasFile = setupJaasConf(baseDir, keytab, hdfsPrincipal);

    final Binding binding = new Binding();

    binding.setProperty("jaasConf", jaasFile.getAbsolutePath());
    binding.setProperty("krb5conf", krb5conf);
    binding.setProperty("gateway", driver.getClusterUrl());

    URL readme = driver.getResourceUrl("README");
    File file = new File(readme.toURI());
    binding.setProperty("file", file.getAbsolutePath());

    final GroovyShell shell = new GroovyShell(binding);

    shell.evaluate(TestUtils.getResourceUrl(SCRIPT).toURI());

    String status = (String) binding.getProperty("status");
    assertNotNull(status);

    String fetchedFile = (String) binding.getProperty("fetchedFile");
    assertNotNull(fetchedFile);
    assertTrue(fetchedFile.contains("README"));
  }
}
