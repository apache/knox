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
import org.apache.hadoop.minikdc.MiniKdc;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.ssl.KeyStoreTestUtil;
import org.apache.knox.test.TestUtils;
import org.apache.knox.test.category.ReleaseTest;
import org.apache.log4j.PropertyConfigurator;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Properties;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test for KnoxShell Kerberos support
 */
@Category(ReleaseTest.class)
public class SecureKnoxShellTest {

  private static final String SCRIPT = "SecureWebHdfsPutGet.groovy";
  /**
   * Referring {@link MiniKdc} as {@link Object} to prevent the class loader
   * from trying to load it before @BeforeClass annotation is called. Need to
   * play this game because {@link MiniKdc} is not compatible with Java 7 so if
   * we detect Java 7 we quit the test.
   * <p>
   * As result we need to up cast this object to {@link MiniKdc} every place we
   * use it.
   *
   */
  private static Object kdc;
  private static String userName;
  private static HdfsConfiguration configuration;
  private static int nameNodeHttpPort;
  private static File baseDir;
  private static String krb5conf;
  private static String hdfsPrincipal;
  private static String spnegoPrincipal;
  private static String keytab;
  private static File ticketCache;

  private static MiniDFSCluster miniDFSCluster;
  private static GatewayTestDriver driver = new GatewayTestDriver();

  /**
   * Test should run if java major version is greater or equal to this
   * property.
   *
   * @since 0.10
   */
  private static int JAVA_MAJOR_VERSION_FOR_TEST = 8;

  public SecureKnoxShellTest() {
    super();
  }

  @BeforeClass
  public static void setupSuite() throws Exception {

    /*
     * Run the test only if the jre version matches the one we want, see
     * KNOX-769
     */
    org.junit.Assume.assumeTrue(isJreVersionOK());
    baseDir = new File(
        KeyStoreTestUtil.getClasspathDir(SecureKnoxShellTest.class));
    ticketCache = new File(
        KeyStoreTestUtil.getClasspathDir(SecureKnoxShellTest.class)
            + "/ticketCache");

    nameNodeHttpPort = TestUtils.findFreePort();
    configuration = new HdfsConfiguration();
    baseDir = new File(
        KeyStoreTestUtil.getClasspathDir(SecureKnoxShellTest.class));
    System.setProperty(MiniDFSCluster.PROP_TEST_BUILD_DATA,
        baseDir.getAbsolutePath());

    miniDFSCluster = new MiniDFSCluster.Builder(configuration)
        .nameNodePort(TestUtils.findFreePort())
        .nameNodeHttpPort(nameNodeHttpPort).numDataNodes(2).format(true)
        .racks(null).build();

    initKdc();
    setupKnox(keytab, hdfsPrincipal);
  }

  private static void initKdc() throws Exception {
    final Properties kdcConf = MiniKdc.createConf();
    kdc = new MiniKdc(kdcConf, baseDir);
    ((MiniKdc) kdc).start();

    userName = UserGroupInformation
        .createUserForTesting("guest", new String[] { "users" }).getUserName();
    final File keytabFile = new File(baseDir, userName + ".keytab");
    keytab = keytabFile.getAbsolutePath();
    // Windows will not reverse name lookup "127.0.0.1" to "localhost".
    final String krbInstance = Path.WINDOWS ? "127.0.0.1" : "localhost";
    ((MiniKdc) kdc).createPrincipal(keytabFile, userName + "/" + krbInstance,
        "HTTP/" + krbInstance);

    hdfsPrincipal =
        userName + "/" + krbInstance + "@" + ((MiniKdc) kdc).getRealm();
    spnegoPrincipal = "HTTP/" + krbInstance + "@" + ((MiniKdc) kdc).getRealm();

    krb5conf = ((MiniKdc) kdc).getKrb5conf().getAbsolutePath();

  }

  @AfterClass
  public static void cleanupSuite() throws Exception {
    /* No need to clean up if we did not start anything */
    if (isJreVersionOK()) {
      ((MiniKdc) kdc).stop();
      Files.deleteIfExists(ticketCache.toPath());
      miniDFSCluster.shutdown();
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
    final Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
    String content = String.format(Locale.ROOT, "com.sun.security.jgss.initiate {\n"
        + "com.sun.security.auth.module.Krb5LoginModule required\n"
        + "renewTGT=true\n" + "doNotPrompt=true\n" + "useKeyTab=true\n"
        + "keyTab=\"%s\"\n" + "principal=\"%s\"\n" + "isInitiator=true\n"
        + "storeKey=true\n" + "useTicketCache=true\n" +
        //"ticketCache=\"%s\"\n" +
        "debug=false\n" + "client=true;\n" + "};\n", keyTabFile, principal);
    writer.write(content);
    writer.close();
    return file;
  }

  private static void setupKnox(String keytab, String hdfsPrincipal)
      throws Exception {

    File jaasConf = setupJaasConf(baseDir, keytab, hdfsPrincipal);

    System.setProperty("java.security.krb5.conf",
        ((MiniKdc) kdc).getKrb5conf().getAbsolutePath());
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
    XMLTag xml = XMLDoc.newDocument(true).addRoot("topology").addTag("gateway")
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

    //System.out.println( "GATEWAY=" + xml.toString() );
    return xml;
  }

  private static void setupLogging() {
    PropertyConfigurator
        .configure(ClassLoader.getSystemResource("log4j.properties"));
  }

  /**
   * Check whether java version is >= {@link #JAVA_MAJOR_VERSION_FOR_TEST}
   *
   * @return
   * @since 0.10
   */
  public static boolean isJreVersionOK() {

    final String jreVersion = System.getProperty("java.version");
    int majorVersion = Integer.parseInt(String.valueOf(jreVersion.charAt(2)));

    if (majorVersion >= JAVA_MAJOR_VERSION_FOR_TEST) {
      return true;
    }
    return false;
  }

  /**
   * Test Kerberos login using KnoxShell using keytab.
   *
   * @throws Exception
   */
  @Test
  public void testCachedTicket() throws Exception {
    setupLogging();

    webhdfsPutGet();
  }

  /**
   * Do the heavy lifting here.
   *
   * @throws Exception
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
    //System.out.println(file.exists());
    binding.setProperty("file", file.getAbsolutePath());

    final GroovyShell shell = new GroovyShell(binding);

    shell.evaluate(getResourceUrl(SCRIPT).toURI());

    String status = (String) binding.getProperty("status");
    assertNotNull(status);
    //System.out.println(status);

    String fetchedFile = (String) binding.getProperty("fetchedFile");
    assertNotNull(fetchedFile);
    //`(fetchedFile);
    assertTrue(fetchedFile.contains("README"));
  }

  public URL getResourceUrl(String resource) {
    String filePath =
        this.getClass().getCanonicalName().replaceAll("\\.", "/") + "/"
            + resource;
    URL url = ClassLoader.getSystemResource(filePath);
    return url;
  }

}
