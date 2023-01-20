/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway;

import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.http.HttpConfig;
import org.apache.hadoop.minikdc.MiniKdc;
import org.apache.hadoop.security.SecurityUtil;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.ssl.KeyStoreTestUtil;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.BasicUserPrincipal;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.util.EntityUtils;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.Principal;
import java.util.Locale;
import java.util.Properties;

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
import static org.apache.hadoop.hdfs.DFSConfigKeys.IPC_CLIENT_CONNECT_MAX_RETRIES_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category(ReleaseTest.class)
public class SecureClusterTest {

  private static MiniDFSCluster miniDFSCluster;
  private static MiniKdc kdc;
  private static HdfsConfiguration configuration;
  private static int nameNodeHttpPort;
  private static String userName;

  private static GatewayTestDriver driver = new GatewayTestDriver();
  private static File baseDir;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    nameNodeHttpPort = TestUtils.findFreePort();
    configuration = new HdfsConfiguration();
    baseDir = new File(KeyStoreTestUtil.getClasspathDir(SecureClusterTest.class));
    System.setProperty(MiniDFSCluster.PROP_TEST_BUILD_DATA, baseDir.getAbsolutePath());
    initKdc();
    miniDFSCluster = new MiniDFSCluster.Builder(configuration)
        .nameNodePort(TestUtils.findFreePort())
        .nameNodeHttpPort(nameNodeHttpPort)
        .numDataNodes(0)
        .format(true)
        .racks(null)
        .build();
  }

  private static void initKdc() throws Exception {
    Properties kdcConf = MiniKdc.createConf();
    kdc = new MiniKdc(kdcConf, baseDir);
    kdc.start();

    configuration = new HdfsConfiguration();
    SecurityUtil.setAuthenticationMethod(UserGroupInformation.AuthenticationMethod.KERBEROS, configuration);
    UserGroupInformation.setConfiguration(configuration);
    assertTrue("Expected configuration to enable security", UserGroupInformation.isSecurityEnabled());
    userName = UserGroupInformation.createUserForTesting("guest", new String[]{"users"}).getUserName();
    File keytabFile = new File(baseDir, userName + ".keytab");
    String keytab = keytabFile.getAbsolutePath();
    // Windows will not reverse name lookup "127.0.0.1" to "localhost".
    String krbInstance = Path.WINDOWS ? "127.0.0.1" : "localhost";
    kdc.createPrincipal(keytabFile, userName + "/" + krbInstance, "HTTP/" + krbInstance);
    String hdfsPrincipal = userName + "/" + krbInstance + "@" + kdc.getRealm();
    String spnegoPrincipal = "HTTP/" + krbInstance + "@" + kdc.getRealm();

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

    setupKnox(keytab, hdfsPrincipal);
  }

  private static void setupKnox(String keytab, String hdfsPrincipal) throws Exception {
    //kerberos setup for http client
    File jaasConf = setupJaasConf(baseDir, keytab, hdfsPrincipal);
    System.setProperty("java.security.krb5.conf", kdc.getKrb5conf().getAbsolutePath());
    System.setProperty("java.security.auth.login.config", jaasConf.getAbsolutePath());
    System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
    System.setProperty("sun.security.krb5.debug", "true");

    //knox setup
    System.setProperty("gateway.hadoop.kerberos.secured", "true");
    GatewayTestConfig config = new GatewayTestConfig();
    config.setGatewayPath("gateway");
    config.setHadoopKerberosSecured(true);
    config.setKerberosConfig(kdc.getKrb5conf().getAbsolutePath());
    config.setKerberosLoginConfig(jaasConf.getAbsolutePath());
    driver.setResourceBase(SecureClusterTest.class);
    driver.setupLdap(0);
    driver.setupGateway(config, "cluster", createTopology(), true);
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

  @Test
  public void basicGetUserHomeRequest() throws Exception {
    CloseableHttpClient client = getHttpClient();
    String method = "GET";
    String uri = driver.getClusterUrl() + "/webhdfs/v1?op=GETHOMEDIRECTORY";
    HttpHost target = new HttpHost("localhost", driver.getGatewayPort(), "http");
    HttpRequest request = new BasicHttpRequest(method, uri);
    CloseableHttpResponse response = client.execute(target, request);
    String json = EntityUtils.toString(response.getEntity());
    response.close();
    assertEquals("{\"Path\":\"/user/" + userName + "\"}", json);
  }

  private CloseableHttpClient getHttpClient() {
    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(AuthScope.ANY, new Credentials() {
      @Override
      public Principal getUserPrincipal() {
        return new BasicUserPrincipal("guest");
      }

      @Override
      public String getPassword() {
        return "guest-password";
      }
    });

    return HttpClients.custom()
        .setDefaultCredentialsProvider(credentialsProvider)
        .build();
  }

  private static File setupJaasConf(File baseDir, String keyTabFile, String principal) throws IOException {
    File file = new File(baseDir, "jaas.conf");
    if (!file.exists()) {
      file.createNewFile();
    } else {
      file.delete();
      file.createNewFile();
    }
    try(OutputStream outputStream = Files.newOutputStream(file.toPath());
        Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
      String content = String.format(Locale.ROOT,
          "com.sun.security.jgss.initiate {\n" +
              "com.sun.security.auth.module.Krb5LoginModule required\n" +
              "renewTGT=true\n" +
              "doNotPrompt=true\n" +
              "useKeyTab=true\n" +
              "keyTab=\"%s\"\n" +
              "principal=\"%s\"\n" +
              "isInitiator=true\n" +
              "storeKey=true\n" +
              "useTicketCache=true\n" +
              "client=true;\n" +
              "};\n", keyTabFile, principal);
      writer.write(content);
    }
    return file;
  }

  /**
   * Creates a topology that is deployed to the gateway instance for the test suite.
   * Note that this topology is shared by all of the test methods in this suite.
   * @return A populated XML structure for a topology file.
   */
  private static XMLTag createTopology() {
    return XMLDoc.newDocument(true)
        .addRoot("topology")
        .addTag("gateway")
        .addTag("provider")
        .addTag("role").addText("webappsec")
        .addTag("name").addText("WebAppSec")
        .addTag("enabled").addText("true")
        .addTag("param")
        .addTag("name").addText("csrf.enabled")
        .addTag("value").addText("false").gotoParent().gotoParent()
        .addTag("provider")
        .addTag("role").addText("authentication")
        .addTag("name").addText("ShiroProvider")
        .addTag("enabled").addText("true")
        .addTag("param")
        .addTag("name").addText("main.ldapRealm")
        .addTag("value").addText("org.apache.knox.gateway.shirorealm.KnoxLdapRealm").gotoParent()
        .addTag("param")
        .addTag("name").addText("main.ldapRealm.userDnTemplate")
        .addTag("value").addText("uid={0},ou=people,dc=hadoop,dc=apache,dc=org").gotoParent()
        .addTag("param")
        .addTag("name").addText("main.ldapRealm.contextFactory.url")
        .addTag("value").addText(driver.getLdapUrl()).gotoParent()
        .addTag("param")
        .addTag("name").addText("main.ldapRealm.contextFactory.authenticationMechanism")
        .addTag("value").addText("simple").gotoParent()
        .addTag("param")
        .addTag("name").addText("urls./**")
        .addTag("value").addText("authcBasic").gotoParent().gotoParent()
        .addTag("provider")
        .addTag("role").addText("identity-assertion")
        .addTag("enabled").addText("true")
        .addTag("name").addText("Default").gotoParent()
        .addTag("provider")
        .addTag("role").addText("authorization")
        .addTag("enabled").addText("true")
        .addTag("name").addText("AclsAuthz").gotoParent()
        .addTag("param")
        .addTag("name").addText("webhdfs-acl")
        .addTag("value").addText("hdfs;*;*").gotoParent()
        .gotoRoot()
        .addTag("service")
        .addTag("role").addText("WEBHDFS")
        .addTag("url").addText("http://localhost:" + nameNodeHttpPort + "/webhdfs/").gotoParent()
        .gotoRoot();
  }
}
