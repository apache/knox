/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.discovery.cm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.cloudera.api.swagger.client.ApiException;
import com.cloudera.api.swagger.client.ApiResponse;
import com.cloudera.api.swagger.model.ApiClusterRef;
import com.cloudera.api.swagger.model.ApiConfig;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiHostRef;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiRoleList;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import com.cloudera.api.swagger.model.ApiServiceList;
import okhttp3.Call;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.topology.discovery.ServiceDiscovery;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;
import org.apache.knox.gateway.topology.discovery.cm.model.atlas.AtlasAPIServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.atlas.AtlasServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.cm.ClouderaManagerUIServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.hbase.HBaseUIServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.hbase.WebHBaseServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.hdfs.NameNodeServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.hive.HiveOnTezServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.hive.HiveServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.hive.WebHCatServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.impala.ImpalaServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.impala.ImpalaUIServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.kudu.KuduUIServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.livy.LivyServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.nifi.NifiRegistryServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.nifi.NifiServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.oozie.OozieServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.phoenix.PhoenixServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.ranger.RangerServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.solr.SolrServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.spark.Spark3HistoryUIServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.spark.SparkHistoryUIServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.zeppelin.ZeppelinServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.monitor.ClouderaManagerClusterConfigurationMonitor;
import org.easymock.EasyMock;
import org.junit.Test;

import java.lang.reflect.Type;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


public class ClouderaManagerServiceDiscoveryTest {

  private static final String DISCOVERY_URL = "http://localhost:1234";
  private static final String ATLAS_SERVICE_NAME = "ATLAS-1";

  @Test
  public void testServiceDiscoveryRetry() throws Exception {
    //re-using an already existing test with 'true' retry flag
    doTestAtlasDiscovery(true, true);
  }

  @Test
  public void testJobTrackerServiceDiscovery() {
    doTestJobTrackerResourceManagerServiceDiscovery("JOBTRACKER");
  }

  @Test
  public void testResourceManagerApiServiceDiscovery() {
    doTestJobTrackerResourceManagerServiceDiscovery("RESOURCEMANAGERAPI");
  }

  private void doTestJobTrackerResourceManagerServiceDiscovery(String serviceName) {
    final String hostName = "resourcemanager-host-1";
    final String  port    = "8032";

    Map<String,String> serviceProperties = new HashMap<>();
    serviceProperties.put("hdfs_service", ""); // This is only necessary to satisfy code not covered by this test

    // Configure the role
    Map<String, String> roleProperties = new HashMap<>();
    roleProperties.put("yarn_resourcemanager_address", port);

    ServiceDiscovery.Cluster cluster =  doTestDiscovery(hostName,
                                                        "YARN-1",
                                                        "YARN",
                                                        "YARN-1-RESOURCEMANAGER-12345",
                                                        "RESOURCEMANAGER",
                                                        serviceProperties,
                                                        roleProperties);
    List<String> urls = cluster.getServiceURLs(serviceName);
    assertNotNull(urls);
    assertEquals(1, urls.size());
    assertEquals("rpc://" + hostName + ":" + port, urls.get(0));
  }

  @Test
  public void testAtlasDiscovery() {
    doTestAtlasDiscovery(false);
  }

  @Test
  public void testAtlasDiscoverySSL() {
    doTestAtlasDiscovery(true);
  }

  @Test
  public void testAtlasAPIDiscovery() {
    doTestAtlasAPIDiscovery(false);
  }

  @Test
  public void testAtlasAPIDiscoverySSL() {
    doTestAtlasAPIDiscovery(true);
  }

  private void doTestAtlasDiscovery(final boolean isSSL) {
    doTestAtlasDiscovery(isSSL, false);
  }

  private void doTestAtlasDiscovery(final boolean isSSL, boolean testRetry) {
    final String hostName       = "atlas-host-1";
    final String port           = "21000";
    final String sslPort        = "21003";
    ServiceDiscovery.Cluster cluster = doTestAtlasDiscovery(hostName, port, sslPort, isSSL, testRetry);
    List<String> atlastURLs = cluster.getServiceURLs(AtlasServiceModelGenerator.SERVICE);
    assertEquals(1, atlastURLs.size());
    assertEquals((isSSL ? "https" : "http") + "://" + hostName + ":" + (isSSL ? sslPort : port),
                 atlastURLs.get(0));
  }

  private void doTestAtlasAPIDiscovery(final boolean isSSL) {
    final String hostName       = "atlas-host-1";
    final String port           = "21000";
    final String sslPort        = "21003";
    ServiceDiscovery.Cluster cluster = doTestAtlasDiscovery(hostName, port, sslPort, isSSL);
    List<String> atlastURLs = cluster.getServiceURLs(AtlasAPIServiceModelGenerator.SERVICE);
    assertEquals(1, atlastURLs.size());
    assertEquals((isSSL ? "https" : "http") + "://" + hostName + ":" + (isSSL ? sslPort : port),
        atlastURLs.get(0));
  }

  @Test
  public void testHiveServiceDiscovery() {
    doTestHiveServiceDiscovery(false);
  }

  @Test
  public void testHiveServiceDiscoveryCustomThriftPath() {
    doTestHiveServiceDiscovery("testPath", false);
  }

  @Test
  public void testHiveServiceDiscoverySSL() {
    doTestHiveServiceDiscovery(true);
  }

  private void doTestHiveServiceDiscovery(final boolean enableSSL) {
    doTestHiveServiceDiscovery(null, enableSSL);
  }

  private void doTestHiveServiceDiscovery(final String thriftPath, final boolean enableSSL) {
    final String hostName       = "test-host-1";
    final String thriftPort     = "10001";
    final String expectedScheme = (enableSSL ? "https" : "http");
    final String expectedThriftPath = thriftPath != null ? thriftPath : "cliservice";

    ServiceDiscovery.Cluster cluster =
        doTestHiveServiceDiscovery(hostName, thriftPort, thriftPath, enableSSL);
    List<String> hiveURLs = cluster.getServiceURLs("HIVE");
    assertNotNull(hiveURLs);
    assertEquals(1, hiveURLs.size());
    assertEquals((expectedScheme + "://" + hostName + ":" + thriftPort + "/" + expectedThriftPath), hiveURLs.get(0));
  }

  @Test
  public void testHiveOnTezDiscovery() {
    doTestHiveOnTezServiceDiscovery(false);
  }

  @Test
  public void testHiveOnTezDiscoveryCustomThriftPath() {
    doTestHiveOnTezServiceDiscovery("customPath", false);
  }

  @Test
  public void testHiveOnTezDiscoverySSL() {
    doTestHiveOnTezServiceDiscovery(true);
  }

  private void doTestHiveOnTezServiceDiscovery(final boolean enableSSL) {
    doTestHiveOnTezServiceDiscovery(null, enableSSL);
  }

  private void doTestHiveOnTezServiceDiscovery(final String thriftPath, final boolean enableSSL) {
    final String hostName       = "test-host-1";
    final String thriftPort     = "10001";
    final String expectedScheme = (enableSSL ? "https" : "http");
    final String expectedThriftPath = thriftPath != null ? thriftPath : "cliservice";

    ServiceDiscovery.Cluster cluster =
        doTestHiveOnTezServiceDiscovery(hostName, thriftPort, thriftPath, enableSSL);
    List<String> hiveURLs = cluster.getServiceURLs("HIVE");
    assertNotNull(hiveURLs);
    assertEquals(1, hiveURLs.size());
    assertEquals((expectedScheme + "://" + hostName + ":" + thriftPort + "/" + expectedThriftPath), hiveURLs.get(0));
  }


  @Test
  public void testWebHDFSServiceDiscovery() {
    final String hostName    = "test-host-1";
    final String nameService = "nameservice1";
    final String nnPort      = "50070";
    final String dfsHttpPort = "50075";

    ServiceDiscovery.Cluster cluster = doTestHDFSDiscovery(hostName, nameService, nnPort, dfsHttpPort);
    List<String> webhdfsURLs = cluster.getServiceURLs("WEBHDFS");
    assertNotNull(webhdfsURLs);
    assertEquals(1, webhdfsURLs.size());
    assertEquals("http://" + hostName + ":" + dfsHttpPort + "/webhdfs",
                 webhdfsURLs.get(0));
  }

  @Test
  public void testWebHDFSServiceDiscoveryWithSSL() {
    final String hostName     = "test-host-1";
    final String nameService  = "nameservice1";
    final String nnPort       = "50070";
    final String dfsHttpPort  = "50075";
    final String dfsHttpsPort = "50079";

    ServiceDiscovery.Cluster cluster =
        doTestHDFSDiscovery(hostName, nameService, nnPort, dfsHttpPort, dfsHttpsPort);
    List<String> webhdfsURLs = cluster.getServiceURLs("WEBHDFS");
    assertNotNull(webhdfsURLs);
    assertEquals(1, webhdfsURLs.size());
    assertEquals("https://" + hostName + ":" + dfsHttpsPort + "/webhdfs",
                 webhdfsURLs.get(0));
  }

  @Test
  public void testNameNodeServiceDiscovery() {
    final String hostName    = "test-host-2";
    final String nameService = "nameservice2";
    final String nnPort      = "50070";
    final String dfsHttpPort = "50071";

    ServiceDiscovery.Cluster cluster = doTestHDFSDiscovery(hostName, nameService, nnPort, dfsHttpPort);
    List<String> nnURLs = cluster.getServiceURLs("NAMENODE");
    assertNotNull(nnURLs);
    assertEquals(1, nnURLs.size());
    assertEquals(("hdfs://" + hostName + ":" + nnPort), nnURLs.get(0));
  }

  @Test
  public void testNameNodeServiceDiscoveryHA() {
    final String hostName    = "test-host-2";
    final String nameService = "nameservice2";
    final String nnPort      = "50070";
    final String dfsHttpPort = "50071";

    ServiceDiscovery.Cluster cluster =
        doTestHDFSDiscovery(hostName, nameService, nnPort, dfsHttpPort, null, true);
    List<String> nnURLs = cluster.getServiceURLs("NAMENODE");
    assertNotNull(nnURLs);
    assertEquals(1, nnURLs.size());
    assertEquals(("hdfs://" + nameService), nnURLs.get(0));
  }

  @Test
  public void testHdfsUIServiceDiscovery() {
    final String hostName    = "test-host-3";
    final String nameService = "nameservice3";
    final String nnPort      = "50070";
    final String dfsHttpPort = "50071";

    ServiceDiscovery.Cluster cluster = doTestHDFSDiscovery(hostName, nameService, nnPort, dfsHttpPort);
    List<String> hdfsUIURLs = cluster.getServiceURLs("HDFSUI");
    assertNotNull(hdfsUIURLs);
    assertEquals(1, hdfsUIURLs.size());
    assertEquals(("http://" + hostName + ":" + dfsHttpPort), hdfsUIURLs.get(0));
  }

  @Test
  public void testHBaseUIDiscovery() {
    final String hostName    = "hbase-host";
    final String port        = "22002";
    ServiceDiscovery.Cluster cluster = doTestHBaseUIDiscovery(hostName, port, false);
    assertNotNull(cluster);
    List<String> hbaseURLs = cluster.getServiceURLs("HBASEUI");
    assertNotNull(hbaseURLs);
    assertEquals(1, hbaseURLs.size());
    assertEquals("http://" + hostName + ":" + port, hbaseURLs.get(0));
  }

  @Test
  public void testHBaseUIDiscoverySSL() {
    final String hostName    = "hbase-host";
    final String port        = "22003";
    ServiceDiscovery.Cluster cluster = doTestHBaseUIDiscovery(hostName, port, true);
    assertNotNull(cluster);
    List<String> hbaseURLs = cluster.getServiceURLs("HBASEUI");
    assertNotNull(hbaseURLs);
    assertEquals(1, hbaseURLs.size());
    assertEquals("https://" + hostName + ":" + port, hbaseURLs.get(0));
  }

  @Test
  public void testWebHBaseDiscovery() {
    final String hostName    = "hbase-host";
    final String port        = "22008";
    ServiceDiscovery.Cluster cluster = doTestWebHBaseDiscovery(hostName, port, false);
    assertNotNull(cluster);
    List<String> hbaseURLs = cluster.getServiceURLs("WEBHBASE");
    assertNotNull(hbaseURLs);
    assertEquals(1, hbaseURLs.size());
    assertEquals("http://" + hostName + ":" + port, hbaseURLs.get(0));
  }

  @Test
  public void testWebHBaseDiscoverySSL() {
    final String hostName    = "hbase-host";
    final String port        = "22009";
    ServiceDiscovery.Cluster cluster = doTestWebHBaseDiscovery(hostName, port, true);
    assertNotNull(cluster);
    List<String> hbaseURLs = cluster.getServiceURLs("WEBHBASE");
    assertNotNull(hbaseURLs);
    assertEquals(1, hbaseURLs.size());
    assertEquals("https://" + hostName + ":" + port, hbaseURLs.get(0));
  }

  @Test
  public void testLivyDiscovery() {
    final String hostName    = "livy-host";
    final String port        = "8998";
    ServiceDiscovery.Cluster cluster = doTestLivyDiscovery(hostName, port, false);
    assertNotNull(cluster);
    List<String> livyURLs = cluster.getServiceURLs("LIVYSERVER");
    assertNotNull(livyURLs);
    assertEquals(1, livyURLs.size());
    assertEquals("http://" + hostName + ":" + port, livyURLs.get(0));
  }

  @Test
  public void testLivyDiscoverySSL() {
    final String hostName    = "livy-host";
    final String port        = "8998";
    ServiceDiscovery.Cluster cluster = doTestLivyDiscovery(hostName, port, true);
    assertNotNull(cluster);
    List<String> livyURLs = cluster.getServiceURLs("LIVYSERVER");
    assertNotNull(livyURLs);
    assertEquals(1, livyURLs.size());
    assertEquals("https://" + hostName + ":" + port, livyURLs.get(0));
  }

  @Test
  public void testPhoenixDiscovery() {
    final String hostName    = "phoenix-host";
    final String port        = "8765";
    ServiceDiscovery.Cluster cluster = doTestPhoenixDiscovery(hostName, port, false);
    assertNotNull(cluster);
    List<String> phoenixURLs = cluster.getServiceURLs("AVATICA");
    assertNotNull(phoenixURLs);
    assertEquals(1, phoenixURLs.size());
    assertEquals("http://" + hostName + ":" + port, phoenixURLs.get(0));
  }

  @Test
  public void testPhoenixDiscoverySSL() {
    final String hostName    = "phoenix-host";
    final String port        = "8765";
    ServiceDiscovery.Cluster cluster = doTestPhoenixDiscovery(hostName, port, true);
    assertNotNull(cluster);
    List<String> phoenixURLs = cluster.getServiceURLs("AVATICA");
    assertNotNull(phoenixURLs);
    assertEquals(1, phoenixURLs.size());
    assertEquals("https://" + hostName + ":" + port, phoenixURLs.get(0));
  }

  @Test
  public void testIncludeHiveServiceIncludeWebHCatDiscovery() {
    testWebHCatDiscovery(true, true);
  }

  @Test
  public void testExcludeHiveServiceIncludeWebHCatDiscovery() {
    testWebHCatDiscovery(false, true);
  }

  @Test
  public void testIncludeHiveServiceExcludeWebHCatDiscovery() {
    testWebHCatDiscovery(true, false);
  }

  @Test
  public void testExcludeHiveServiceExcludeWebHCatDiscovery() {
    testWebHCatDiscovery(false, false);
  }

  private void testWebHCatDiscovery(boolean excludeHiveService, boolean excludeWebHCatRole) {
    final String hostName = "webhcat-host";
    final String port     = "22222";
    final String expectedURL = "http://" + hostName + ":" + port + "/templeton";

    // Configure the role
    Map<String, String> roleProperties = new HashMap<>();
    roleProperties.put("hive_webhcat_address_port", port);

    ServiceDiscovery.Cluster cluster = doTestDiscovery(hostName,
                                                       "HIVE-1",
                                                       WebHCatServiceModelGenerator.SERVICE_TYPE,
                                                       "HIVE-1-WEBHCAT-1",
                                                       WebHCatServiceModelGenerator.ROLE_TYPE,
                                                       Collections.emptyMap(),
                                                       roleProperties,
                                                       false,
                                                       excludeHiveService ? WebHCatServiceModelGenerator.SERVICE_TYPE : null,
                                                       excludeWebHCatRole ?  WebHCatServiceModelGenerator.ROLE_TYPE : null);

    List<String> urls = cluster.getServiceURLs("WEBHCAT");
    assertNotNull(urls);
    final boolean expectExclusion = excludeHiveService || excludeWebHCatRole;
    assertEquals(expectExclusion ? 0 : 1, urls.size());
    if (!expectExclusion) {
      assertEquals(expectedURL, urls.get(0));
    }
  }

  @Test
  public void testOozieDiscovery() {
    doTestOozieDiscovery("OOZIE", false);
  }

  @Test
  public void testOozieDiscoverySSL() {
    doTestOozieDiscovery("OOZIE", true);
  }


  @Test
  public void testOozieUIDiscovery() {
    doTestOozieDiscovery("OOZIEUI", false);
  }

  @Test
  public void testOozieUIDiscoverySSL() {
    doTestOozieDiscovery("OOZIEUI", true);
  }

  @Test
  public void testRangerDiscovery() {
    doTestRangerDiscovery("RANGER", false);
  }

  @Test
  public void testRangerDiscoverySSL() {
    doTestRangerDiscovery("RANGER", true);
  }

  @Test
  public void testRangerUIDiscovery() {
    doTestRangerDiscovery("RANGERUI", false);
  }

  @Test
  public void testRangerUIDiscoverySSL() {
    doTestRangerDiscovery("RANGERUI", true);
  }

  @Test
  public void testSolrDiscovery() {
    doTestSolrDiscovery(false);
  }

  @Test
  public void testSolrDiscoverySSL() {
    doTestSolrDiscovery(true);
  }

  @Test
  public void testSparkHistoryUIDiscovery() {
    doTestSparkHistoryUIDiscovery("spark-history-host", "18088", "18083", false);
  }

  @Test
  public void testSparkHistoryUIDiscoverySSL() {
    doTestSparkHistoryUIDiscovery("spark-history-host", "18088", "18083", true);
  }

  @Test
  public void testSpark3HistoryUIDiscovery() {
    doTestSpark3HistoryUIDiscovery("spark-history-host", "18089", "18489", false);
  }

  @Test
  public void testSpark3HistoryUIDiscoverySSL() {
    doTestSpark3HistoryUIDiscovery("spark-history-host", "18089", "18489", true);
  }

  @Test
  public void testZeppelinDiscovery() {
    doTestZeppelinDiscovery("ZEPPELIN", false);
  }

  @Test
  public void testZeppelinDiscoverySSL() {
    doTestZeppelinDiscovery("ZEPPELIN", true);
  }

  @Test
  public void testZeppelinUIDiscovery() {
    doTestZeppelinDiscovery("ZEPPELINUI", false);
  }

  @Test
  public void testZeppelinUIDiscoverySSL() {
    doTestZeppelinDiscovery("ZEPPELINUI", true);
  }

  @Test
  public void testZeppelinWSDiscovery() {
    doTestZeppelinDiscovery("ZEPPELINWS", false);
  }

  @Test
  public void testZeppelinWSDiscoverySSL() {
    doTestZeppelinDiscovery("ZEPPELINWS", true);
  }

  @Test
  public void testImpalaDiscovery() {
    doTestImpalaDiscovery(false);
  }

  @Test
  public void testImpalaDiscoverySSL() {
    doTestImpalaDiscovery(true);
  }

  @Test
  public void testImpalaUIDiscoveryWebServerNotEnabled() {
    doTestImpalaUIDiscovery(false, false);
  }

  @Test
  public void testImpalaUIDiscovery() {
    doTestImpalaUIDiscovery(false, true);
  }

  @Test
  public void testImpalaUIDiscoverySSL() {
    doTestImpalaUIDiscovery(true, true);
  }


  @Test
  public void testKuduUIDiscovery() {
    doTestKuduUIDiscovery(false);
  }

  @Test
  public void testKuduUIDiscoverySSL() {
    doTestKuduUIDiscovery(true);
  }

  private void doTestKuduUIDiscovery(final boolean isSSL) {
    final String hostName    = "kudu-host";
    final String port        = "8051";
    final String expectedURL = (isSSL ? "https" : "http") + "://" + hostName + ":" + port + "/";

    // Configure the role
    Map<String, String> roleProperties = new HashMap<>();
    roleProperties.put("ssl_enabled", String.valueOf(isSSL));
    roleProperties.put("webserver_port", port);

    ServiceDiscovery.Cluster cluster = doTestDiscovery(hostName,
                                                       "KUDU-1",
                                                       KuduUIServiceModelGenerator.SERVICE_TYPE,
                                                       "KUDU-KUDU_MASTER-12345",
                                                       KuduUIServiceModelGenerator.ROLE_TYPE,
                                                       Collections.emptyMap(),
                                                       roleProperties);

    List<String> urls = cluster.getServiceURLs(KuduUIServiceModelGenerator.SERVICE);
    assertEquals(1, urls.size());
    assertEquals(expectedURL, urls.get(0));
  }

  @Test
  public void testNiFiDiscovery() {
    doTestNiFiDiscovery(false);
  }

  @Test
  public void testNiFiDiscoverySSL() {
    doTestNiFiDiscovery(true);
  }

  private void doTestNiFiDiscovery(boolean sslEnabled) {
    final String hostName = "nifi-host";
    final String port = "8080";
    final String sslPort = "8443";
    final String servicePort = sslEnabled ? "8443" : "8080";
    final String expectedURL = (sslEnabled ? "https" : "http") + "://" + hostName + ":" + servicePort;

    // Configure the role
    Map<String, String> roleProperties = new HashMap<>();
    roleProperties.put("nifi.web.http.port", port);
    roleProperties.put("nifi.web.https.port", sslPort);
    roleProperties.put("ssl_enabled", String.valueOf(sslEnabled));

    ServiceDiscovery.Cluster cluster = doTestDiscovery(hostName,
        "NIFI-1", NifiServiceModelGenerator.SERVICE_TYPE,
        "nifi-NIFI_NODE-1",
        NifiServiceModelGenerator.ROLE_TYPE,
        Collections.emptyMap(),
        roleProperties);

    List<String> urls = cluster.getServiceURLs("NIFI");
    assertEquals(1, urls.size());
    assertEquals(expectedURL, urls.get(0));
  }

  @Test
  public void testNiFiRegistryDiscovery() {
    doTestNiFiRegistryDiscovery(false);
  }

  @Test
  public void testNiFiRegistryDiscoverySSL() {
    doTestNiFiRegistryDiscovery(true);
  }

  @Test
  public void testCMDiscoveryUI() {
    doTestCMDiscovery("CM-UI");
  }

  @Test
  public void testCMDiscoveryAPI() {
    doTestCMDiscovery("CM-API");
  }

  private void doTestCMDiscovery(final String serviceName) {
    ServiceDiscovery.Cluster cluster = doTestDiscovery("somehost",
        serviceName+"-1", ClouderaManagerUIServiceModelGenerator.SERVICE_TYPE,
        serviceName+"-1",
        ClouderaManagerUIServiceModelGenerator.ROLE_TYPE,
        Collections.emptyMap(),
        Collections.emptyMap());

    List<String> urls = cluster.getServiceURLs(serviceName);
    assertEquals(1, urls.size());
    if("CM-UI".equals(serviceName)) {
      assertEquals(DISCOVERY_URL, urls.get(0));
    } else {
      assertEquals(DISCOVERY_URL+"/api", urls.get(0));
    }
  }

  private void doTestNiFiRegistryDiscovery(boolean sslEnabled) {
    final String hostName = "nifi-registry-host";
    final String port = "18080";
    final String sslPort = "18443";
    final String servicePort = sslEnabled ? "18443" : "18080";
    final String expectedURL = (sslEnabled ? "https" : "http") + "://" + hostName + ":" + servicePort;

    // Configure the role
    Map<String, String> roleProperties = new HashMap<>();
    roleProperties.put("nifi.registry.web.http.port", port);
    roleProperties.put("nifi.registry.web.https.port", sslPort);
    roleProperties.put("ssl_enabled", String.valueOf(sslEnabled));

    ServiceDiscovery.Cluster cluster = doTestDiscovery(hostName,
        "NIFI_REGISTRY-1", NifiRegistryServiceModelGenerator.SERVICE_TYPE,
        "NIFI_REGISTRY_SERVER-1",
        NifiRegistryServiceModelGenerator.ROLE_TYPE,
        Collections.emptyMap(),
        roleProperties);

    List<String> urls = cluster.getServiceURLs("NIFI-REGISTRY");
    assertEquals(1, urls.size());
    assertEquals(expectedURL, urls.get(0));
  }

  private void doTestImpalaDiscovery(boolean sslEnabled) {
    final String hostName = "impalad-host";
    final String port     = "28000";
    final String expectedURL = (sslEnabled ? "https" : "http") + "://" + hostName + ":" + port + "/";

    Map<String, String> serviceProperties = new HashMap<>();
    if (sslEnabled) {
      serviceProperties.put("client_services_ssl_enabled", "true");
    }

    // Configure the role
    Map<String, String> roleProperties = new HashMap<>();
    roleProperties.put("hs2_http_port", port);
    roleProperties.put(ImpalaServiceModelGenerator.SPECIALIZATION, ImpalaServiceModelGenerator.NO_SPEC);

    ServiceDiscovery.Cluster cluster = doTestDiscovery(hostName,
                                                       "IMPALA-1",
                                                       ImpalaServiceModelGenerator.SERVICE_TYPE,
                                                       "IMAPALA-1-IMPALAD-1",
                                                       ImpalaServiceModelGenerator.ROLE_TYPE,
                                                       serviceProperties,
                                                       roleProperties);

    List<String> urls = cluster.getServiceURLs("IMPALA");
    assertEquals(1, urls.size());
    assertEquals(expectedURL, urls.get(0));
  }


  private void doTestImpalaUIDiscovery(boolean sslEnabled, boolean webserverEnabled) {
    final String hostName = "impalad-host";
    final String port     = "25000";
    final String expectedURL = (sslEnabled ? "https" : "http") + "://" + hostName + ":" + port + "/";

    Map<String, String> serviceProperties = new HashMap<>();
    if (sslEnabled) {
      serviceProperties.put("client_services_ssl_enabled", "true");
    }

    // Configure the role
    Map<String, String> roleProperties = new HashMap<>();
    roleProperties.put("hs2_http_port", port);
    if (webserverEnabled) {
      roleProperties.put("impalad_enable_webserver", "true");
      roleProperties.put("impalad_webserver_port", port);
    }

    ServiceDiscovery.Cluster cluster = doTestDiscovery(hostName,
                                                       "IMPALA-1",
                                                       ImpalaUIServiceModelGenerator.SERVICE_TYPE,
                                                       "IMAPALA-1-IMPALAD-1",
                                                       ImpalaUIServiceModelGenerator.ROLE_TYPE,
                                                       serviceProperties,
                                                       roleProperties);

    List<String> urls = cluster.getServiceURLs("IMPALAUI");
    if (webserverEnabled) {
      assertEquals(1, urls.size());
      assertEquals(expectedURL, urls.get(0));
    } else {
      assertEquals(0, urls.size());
    }
  }

  private void doTestOozieDiscovery(final String serviceName, final boolean isSSL) {
    final String hostName = "oozie-host";
    final String port        = "11000";
    final String sslPort     = "11003";
    final String expectedURL =
            (isSSL ? "https" : "http") + "://" + hostName + ":" + (isSSL ? sslPort : port) + "/oozie/";
    ServiceDiscovery.Cluster cluster = doTestOozieDiscovery(hostName, port, sslPort, isSSL);
    assertNotNull(cluster);
    List<String> oozieURLs = cluster.getServiceURLs(serviceName);
    assertNotNull(oozieURLs);
    assertEquals(1, oozieURLs.size());
    assertEquals(expectedURL, oozieURLs.get(0));
  }

  private ServiceDiscovery.Cluster doTestOozieDiscovery(final String  hostName,
                                                        final String  port,
                                                        final String  sslPort,
                                                        final boolean isSSL) {
    Map<String, String> serviceProperties = new HashMap<>();
    serviceProperties.put("oozie_use_ssl", String.valueOf(isSSL));

    // Configure the role
    Map<String, String> roleProperties = new HashMap<>();
    roleProperties.put("oozie_http_port", port);
    roleProperties.put("oozie_https_port", sslPort);

    return doTestDiscovery(hostName,
                           "OOZIE-1",
                           OozieServiceModelGenerator.SERVICE_TYPE,
                           "OOZIE-1-OOZIE_SERVER-12345",
                           OozieServiceModelGenerator.ROLE_TYPE,
                           serviceProperties,
                           roleProperties);
  }

  private void doTestZeppelinDiscovery(final String serviceName, final boolean isSSL) {
    final String hostName = "zeppelin-host";
    final String port     = "8886";
    final String sslPort  = "8887";

    String expectedScheme;
    String expectedContextPath;

    if ("ZEPPELINWS".equals(serviceName)) {
      expectedScheme = "ws";
      expectedContextPath = "/ws";
    } else {
      expectedScheme = "http";
      expectedContextPath = "";
    }
    if (isSSL) {
      expectedScheme += "s";
    }

    final String expectedURL =
        expectedScheme + "://" + hostName + ":" + (isSSL ? sslPort : port) + expectedContextPath;
    ServiceDiscovery.Cluster cluster = doTestZeppelinDiscovery(hostName, port, sslPort, isSSL);
    assertNotNull(cluster);
    List<String> zeppelinURLs = cluster.getServiceURLs(serviceName);
    assertNotNull(zeppelinURLs);
    assertEquals(1, zeppelinURLs.size());
    assertEquals(expectedURL, zeppelinURLs.get(0));
  }


  private ServiceDiscovery.Cluster doTestZeppelinDiscovery(final String  hostName,
                                                           final String  port,
                                                           final String  sslPort,
                                                           final boolean isSSL) {
    // Configure the role
    Map<String, String> roleProperties = new HashMap<>();
    roleProperties.put("zeppelin_server_port", port);
    roleProperties.put("zeppelin_server_ssl_port", sslPort);
    roleProperties.put("ssl_enabled", String.valueOf(isSSL));

    return doTestDiscovery(hostName,
                           "ZEPPELIN-1",
                           ZeppelinServiceModelGenerator.SERVICE_TYPE,
                           "ZEPPELIN-ZEPPELIN_SERVER-1",
                           ZeppelinServiceModelGenerator.ROLE_TYPE,
                           Collections.emptyMap(),
                           roleProperties);
  }


  private void doTestRangerDiscovery(final String serviceName, final boolean isSSL) {
    final String hostName    = "ranger-host";
    final String port        = "6080";
    final String sslPort     = "6083";
    final String expectedURL =
        (isSSL ? "https" : "http") + "://" + hostName + ":" + (isSSL ? sslPort : port);
    ServiceDiscovery.Cluster cluster = doTestRangerDiscovery(hostName, port, sslPort, isSSL);
    assertNotNull(cluster);
    List<String> rangerURLs = cluster.getServiceURLs(serviceName);
    assertNotNull(rangerURLs);
    assertEquals(1, rangerURLs.size());
    assertEquals(expectedURL, rangerURLs.get(0));
  }


  private void doTestSolrDiscovery(final boolean isSSL) {
    final String hostName    = "solr-host";
    final String port        = "8983";
    final String sslPort     = "8985";
    final String expectedURL =
        (isSSL ? "https" : "http") + "://" + hostName + ":" + (isSSL ? sslPort : port) + "/solr/";
    ServiceDiscovery.Cluster cluster = doTestSolrDiscovery(hostName, port, sslPort, isSSL);
    assertNotNull(cluster);
    List<String> solrURLs = cluster.getServiceURLs("SOLR");
    assertNotNull(solrURLs);
    assertEquals(1, solrURLs.size());
    assertEquals(expectedURL, solrURLs.get(0));
  }


  private ServiceDiscovery.Cluster doTestRangerDiscovery(final String  hostName,
                                                         final String  port,
                                                         final String  sslPort,
                                                         final boolean isSSL) {
    Map<String, String> serviceProperties = new HashMap<>();
    serviceProperties.put("ranger_service_http_port", port);
    serviceProperties.put("ranger_service_https_port", sslPort);

    // Configure the role
    Map<String, String> roleProperties = new HashMap<>();
    roleProperties.put("ssl_enabled", String.valueOf(isSSL));

    return doTestDiscovery(hostName,
                           "RANGER-1",
                           RangerServiceModelGenerator.SERVICE_TYPE,
                           "RANGER-RANGER_ADMIN-1",
                           RangerServiceModelGenerator.ROLE_TYPE,
                           serviceProperties,
                           roleProperties);
  }


  private ServiceDiscovery.Cluster doTestSolrDiscovery(final String  hostName,
                                                       final String  port,
                                                       final String  sslPort,
                                                       final boolean isSSL) {
    Map<String, String> serviceProperties = new HashMap<>();
    serviceProperties.put("solr_use_ssl", String.valueOf(isSSL));

    // Configure the role
    Map<String, String> roleProperties = new HashMap<>();
    roleProperties.put("solr_http_port", port);
    roleProperties.put("solr_https_port", sslPort);

    return doTestDiscovery(hostName,
                           "SOLR-1",
                           SolrServiceModelGenerator.SERVICE_TYPE,
                           "SOLR-SOLR_SERVER-1",
                           SolrServiceModelGenerator.ROLE_TYPE,
                           serviceProperties,
                           roleProperties);
  }


  private ServiceDiscovery.Cluster doTestSparkHistoryUIDiscovery(final String  hostName,
                                                                 final String  port,
                                                                 final String  sslPort,
                                                                 final boolean isSSL) {
    Map<String, String> roleProperties = sparkHistoryUIRoleProperties(port, sslPort, isSSL);

    return doTestDiscovery(hostName,
                           "SPARK_ON_YARN-1",
                           SparkHistoryUIServiceModelGenerator.SERVICE_TYPE,
                           "SPAR4fcf419a-SPARK_YARN_HISTORY_SERVER-12345",
                           SparkHistoryUIServiceModelGenerator.ROLE_TYPE,
                           Collections.emptyMap(),
                           roleProperties);
  }

  private ServiceDiscovery.Cluster doTestSpark3HistoryUIDiscovery(final String  hostName,
                                                                  final String  port,
                                                                  final String  sslPort,
                                                                  final boolean isSSL) {
    Map<String, String> roleProperties = sparkHistoryUIRoleProperties(port, sslPort, isSSL);

    return doTestDiscovery(hostName,
                           "SPARK3_ON_YARN-1",
                           Spark3HistoryUIServiceModelGenerator.SERVICE_TYPE,
                           "SPAR4fcf419a-SPARK3_YARN_HISTORY_SERVER-12345",
                           Spark3HistoryUIServiceModelGenerator.ROLE_TYPE,
                           Collections.emptyMap(),
                           roleProperties);
  }

  private Map<String, String> sparkHistoryUIRoleProperties(final String  port,
                                                           final String  sslPort,
                                                           final boolean isSSL) {
    Map<String, String> roleProperties = new HashMap<>();
    roleProperties.put("ssl_enabled", String.valueOf(isSSL));
    roleProperties.put("history_server_web_port", port);
    roleProperties.put("ssl_server_port", sslPort);

    return roleProperties;
  }

  private ServiceDiscovery.Cluster doTestAtlasDiscovery(final String  atlasHost,
      final String  port,
      final String  sslPort,
      final boolean isSSL) {
    return doTestAtlasDiscovery(atlasHost, port, sslPort, isSSL, false);
  }

  private ServiceDiscovery.Cluster doTestAtlasDiscovery(final String  atlasHost,
                                                        final String  port,
                                                        final String  sslPort,
                                                        final boolean isSSL,
                                                        final boolean testRetry) {
    // Configure the role
    Map<String, String> roleProperties = new HashMap<>();
    roleProperties.put("atlas_server_http_port", port);
    roleProperties.put("atlas_server_https_port", sslPort);
    roleProperties.put("ssl_enabled", String.valueOf(isSSL));

    return doTestDiscovery(atlasHost,
                           ATLAS_SERVICE_NAME,
                           AtlasServiceModelGenerator.SERVICE_TYPE,
                           "ATLAS-ATLAS_SERVER-1",
                           AtlasServiceModelGenerator.ROLE_TYPE,
                           Collections.emptyMap(),
                           roleProperties,
                           testRetry);
  }


  private ServiceDiscovery.Cluster doTestHiveServiceDiscovery(final String  hostName,
                                                              final String  thriftPort,
                                                              final String  thriftPath,
                                                              final boolean enableSSL) {
    final String safetyValveThriftPathFormat =
        "<property><name>hive.server2.thrift.http.path</name><value>%s</value></property>";

    final String safetyValveThriftPathConfig =
        thriftPath != null ? String.format(Locale.ROOT, safetyValveThriftPathFormat, thriftPath) : "";

    final String hs2SafetyValveValue =
          "<property><name>hive.server2.transport.mode</name><value>http</value></property>\n" +
          "<property><name>hive.server2.thrift.http.port</name><value>" + thriftPort + "</value></property>\n" +
          safetyValveThriftPathConfig;

    // Configure the role
    Map<String, String> roleProperties = new HashMap<>();
    roleProperties.put("hive_hs2_config_safety_valve", hs2SafetyValveValue);

    final Map<String, String> serviceProperties = Collections.singletonMap(HiveServiceModelGenerator.SSL_ENABLED, String.valueOf(enableSSL));

    return doTestDiscovery(hostName,
                           "HIVE-1",
                           HiveServiceModelGenerator.SERVICE_TYPE,
                           "HIVE-1-HIVESERVER2-12345",
                           HiveServiceModelGenerator.ROLE_TYPE,
                           serviceProperties,
                           roleProperties);
  }


  private ServiceDiscovery.Cluster doTestHiveOnTezServiceDiscovery(final String  hostName,
                                                                   final String  thriftPort,
                                                                   final String  thriftPath,
                                                                   final boolean enableSSL) {

    final String safetyValveFormat = "<property><name>hive.server2.thrift.http.path</name><value>%s</value></property>";

    final String hs2SafetyValveValue =
        thriftPath != null ? String.format(Locale.ROOT, safetyValveFormat, thriftPath) : null;

    // Configure the role
    Map<String, String> roleProperties = new HashMap<>();
    roleProperties.put("hive_server2_thrift_http_port", thriftPort);
    roleProperties.put("hive_server2_transport_mode", "http");
    roleProperties.put("hive_hs2_config_safety_valve", hs2SafetyValveValue);

    final Map<String, String> serviceProperties = Collections.singletonMap(HiveServiceModelGenerator.SSL_ENABLED, String.valueOf(enableSSL));

    return doTestDiscovery(hostName,
                           "HIVE_ON_TEZ-1",
                           HiveOnTezServiceModelGenerator.SERVICE_TYPE,
                           "HIVE_ON_TEZ-1-HIVESERVER2-12345",
                           HiveServiceModelGenerator.ROLE_TYPE,
                           serviceProperties,
                           roleProperties);
  }


  private ServiceDiscovery.Cluster doTestHDFSDiscovery(final String hostName,
                                                       final String nameService,
                                                       final String nnPort,
                                                       final String dfsHttpPort) {
    return doTestHDFSDiscovery(hostName, nameService, nnPort, dfsHttpPort, null);
  }


  private ServiceDiscovery.Cluster doTestHDFSDiscovery(final String hostName,
                                                       final String nameService,
                                                       final String nnPort,
                                                       final String dfsHttpPort,
                                                       final String dfsHttpsPort) {
    return doTestHDFSDiscovery(hostName, nameService, nnPort, dfsHttpPort, dfsHttpsPort, false);
  }


  private ServiceDiscovery.Cluster doTestHDFSDiscovery(final String  hostName,
                                                       final String  nameService,
                                                       final String  nnPort,
                                                       final String  dfsHttpPort,
                                                       final String  dfsHttpsPort,
                                                       final boolean enableHA) {

    // Prepare the HDFS service config response for the cluster
    Map<String, String> serviceProps = new HashMap<>();
    serviceProps.put("hdfs_hadoop_ssl_enabled", String.valueOf(dfsHttpsPort != null && !dfsHttpsPort.isEmpty()));
    serviceProps.put("dfs_webhdfs_enabled", "true");

    // Configure the role
    Map<String, String> roleProperties = new HashMap<>();
    roleProperties.put("dfs_federation_namenode_nameservice", nameService);
    roleProperties.put("autofailover_enabled", String.valueOf(enableHA));
    roleProperties.put("namenode_port", nnPort);
    roleProperties.put("dfs_http_port", dfsHttpPort);
    if (dfsHttpsPort != null && !dfsHttpsPort.isEmpty()) {
      roleProperties.put("dfs_https_port", dfsHttpsPort);
    }

    return doTestDiscovery(hostName,
                           "NAMENODE-1",
                           NameNodeServiceModelGenerator.SERVICE_TYPE,
                           "HDFS-1-NAMENODE-12345",
                           NameNodeServiceModelGenerator.ROLE_TYPE,
                           serviceProps,
                           roleProperties);
  }


  private ServiceDiscovery.Cluster doTestHBaseUIDiscovery(final String  hostName,
                                                          final String  port,
                                                          final boolean isSSL) {
    // Prepare the HBase service config response for the cluster
    Map<String, String> serviceProps = new HashMap<>();
    serviceProps.put("hbase_hadoop_ssl_enabled", String.valueOf(isSSL));

    // Configure the role
    Map<String, String> roleProperties = new HashMap<>();
    roleProperties.put("hbase_master_info_port", port);

    return doTestDiscovery(hostName,
                           "HBASE-1",
                           HBaseUIServiceModelGenerator.SERVICE_TYPE,
                           "HBASE-1-MASTER-12345",
                           HBaseUIServiceModelGenerator.ROLE_TYPE,
                           serviceProps,
                           roleProperties);
  }


  private ServiceDiscovery.Cluster doTestWebHBaseDiscovery(final String  hostName,
                                                           final String  port,
                                                           final boolean isSSL) {
    // Configure the role
    Map<String, String> roleProperties = new HashMap<>();
    roleProperties.put("hbase_restserver_port", port);
    roleProperties.put("hbase_restserver_ssl_enable", String.valueOf(isSSL));

    return doTestDiscovery(hostName,
                           "HBASE-1",
                           WebHBaseServiceModelGenerator.SERVICE_TYPE,
                           "HBASE-1-RESTSERVER",
                           WebHBaseServiceModelGenerator.ROLE_TYPE,
                           Collections.emptyMap(),
                           roleProperties);
  }


  private ServiceDiscovery.Cluster doTestLivyDiscovery(final String  hostName,
                                                       final String  port,
                                                       final Boolean isSSL) {
    // Configure the role
    Map<String, String> roleProperties = new HashMap<>();
    roleProperties.put("livy_server_port", port);
    roleProperties.put("ssl_enabled", String.valueOf(isSSL));

    return doTestDiscovery(hostName,
                           "LIVY-1",
                           LivyServiceModelGenerator.SERVICE_TYPE,
                           "LIVY-LIVY_SERVER-1",
                           LivyServiceModelGenerator.ROLE_TYPE,
                           Collections.emptyMap(),
                           roleProperties);
  }


  private ServiceDiscovery.Cluster doTestPhoenixDiscovery(final String hostName,
                                                          final String port,
                                                          final boolean isSSL) {
      // Configure the role
      Map<String, String> roleProperties = new HashMap<>();
      roleProperties.put("phoenix_query_server_port", port);
      roleProperties.put("ssl_enabled", String.valueOf(isSSL));

      return doTestDiscovery(hostName,
                             "PHOENIX-1",
                             PhoenixServiceModelGenerator.SERVICE_TYPE,
                             "PHOENIX-PHOENIX_QUERY_SERVER-1",
                             PhoenixServiceModelGenerator.ROLE_TYPE,
                             Collections.emptyMap(),
                             roleProperties);
  }


  private ServiceDiscovery.Cluster doTestDiscovery(final String hostName,
      final String serviceName,
      final String serviceType,
      final String roleName,
      final String roleType,
      final Map<String, String> serviceProperties,
      final Map<String, String> roleProperties) {
    return doTestDiscovery(hostName, serviceName, serviceType, roleName, roleType, serviceProperties, roleProperties, false);
  }

  private ServiceDiscovery.Cluster doTestDiscovery(final String hostName,
      final String serviceName,
      final String serviceType,
      final String roleName,
      final String roleType,
      final Map<String, String> serviceProperties,
      final Map<String, String> roleProperties,
      boolean testRetry) {
    return doTestDiscovery(hostName, serviceName, serviceType, roleName, roleType, serviceProperties, roleProperties, testRetry, null, null);
  }

  private ServiceDiscovery.Cluster doTestDiscovery(final String hostName,
                                                   final String serviceName,
                                                   final String serviceType,
                                                   final String roleName,
                                                   final String roleType,
                                                   final Map<String, String> serviceProperties,
                                                   final Map<String, String> roleProperties,
                                                   boolean testRetry,
                                                   String excludedServiceType,
                                                   String excludedRoleType) {
    final String clusterName = "cluster-1";

    GatewayConfig gwConf = EasyMock.createNiceMock(GatewayConfig.class);
    if (testRetry) {
      EasyMock.expect(gwConf.getClouderaManagerServiceDiscoveryMaximumRetryAttempts()).andReturn(GatewayConfig.DEFAULT_CM_SERVICE_DISCOVERY_MAX_RETRY_ATTEMPTS).anyTimes();
      EasyMock.expect(gwConf.getClusterMonitorPollingInterval(ClouderaManagerClusterConfigurationMonitor.getType())).andReturn(10).anyTimes();
    }
    EasyMock.expect(gwConf.getIncludedSSLCiphers()).andReturn(Collections.emptyList()).anyTimes();
    EasyMock.expect(gwConf.getIncludedSSLProtocols()).andReturn(Collections.emptySet()).anyTimes();
    if (excludedServiceType == null) {
      EasyMock.expect(gwConf.getClouderaManagerServiceDiscoveryExcludedServiceTypes()).andReturn(Collections.emptySet()).anyTimes();
    } else {
      EasyMock.expect(gwConf.getClouderaManagerServiceDiscoveryExcludedServiceTypes()).andReturn(Arrays.asList(excludedServiceType)).anyTimes();
    }
    if (excludedRoleType == null) {
      EasyMock.expect(gwConf.getClouderaManagerServiceDiscoveryExcludedRoleTypes()).andReturn(Collections.emptySet()).anyTimes();
    } else {
      EasyMock.expect(gwConf.getClouderaManagerServiceDiscoveryExcludedRoleTypes()).andReturn(Arrays.asList(excludedRoleType)).anyTimes();
    }
    EasyMock.replay(gwConf);

    ServiceDiscoveryConfig sdConfig = createMockDiscoveryConfig(clusterName);

    // Create the test client for providing test response content
    TestDiscoveryApiClient mockClient = testRetry ? new TestFaultyDiscoveryApiClient(gwConf, sdConfig, null) : new TestDiscoveryApiClient(gwConf, sdConfig, null);

    // Prepare the service list response for the cluster
    ApiServiceList serviceList = EasyMock.createNiceMock(ApiServiceList.class);
    final List<ApiService> apiServiceList = new ArrayList<>();
    apiServiceList.add(createMockApiService(serviceName, serviceType, clusterName));
    EasyMock.expect(serviceList.getItems())
            .andReturn(apiServiceList)
            .anyTimes();
    EasyMock.replay(serviceList);
    mockClient.addResponse(ApiServiceList.class, new TestApiServiceListResponse(serviceList));

    // Prepare the service config response for the cluster
    ApiServiceConfig serviceConfig = createMockApiServiceConfig(serviceProperties);
    mockClient.addResponse(ApiServiceConfig.class, new TestApiServiceConfigResponse(serviceConfig));

    // Prepare the role
    ApiRole role = createMockApiRole(roleName, roleType, hostName);
    ApiRoleList roleList = EasyMock.createNiceMock(ApiRoleList.class);
    EasyMock.expect(roleList.getItems()).andReturn(Collections.singletonList(role)).anyTimes();
    EasyMock.replay(roleList);
    mockClient.addResponse(ApiRoleList.class, new TestApiRoleListResponse(roleList));

    // Configure the role
    ApiConfigList roleConfigList = createMockApiConfigList(roleProperties);
    mockClient.addResponse(ApiConfigList.class, new TestApiConfigListResponse(roleConfigList));

    // Invoke the service discovery
    ClouderaManagerServiceDiscovery cmsd = new ClouderaManagerServiceDiscovery(true, gwConf);
    cmsd.onConfigurationChange(null, null); //to clear the repo
    ServiceDiscovery.Cluster cluster = cmsd.discover(gwConf, sdConfig, clusterName, Collections.emptySet(), mockClient);
    assertNotNull(cluster);
    assertEquals(clusterName, cluster.getName());
    if (serviceName.equals(ATLAS_SERVICE_NAME)) {
      assertEquals(testRetry ? 9 : 4, mockClient.getExecuteCount());
    }
    return cluster;
  }


  private static ServiceDiscoveryConfig createMockDiscoveryConfig(String clusterName) {
    return createMockDiscoveryConfig(DISCOVERY_URL, "itsme", clusterName);
  }

  private static ServiceDiscoveryConfig createMockDiscoveryConfig(String address, String username, String clusterName) {
    ServiceDiscoveryConfig config = EasyMock.createNiceMock(ServiceDiscoveryConfig.class);
    EasyMock.expect(config.getAddress()).andReturn(address).anyTimes();
    EasyMock.expect(config.getUser()).andReturn(username).anyTimes();
    EasyMock.expect(config.getPasswordAlias()).andReturn(null).anyTimes();
    EasyMock.expect(config.getCluster()).andReturn(clusterName).anyTimes();
    EasyMock.replay(config);
    return config;
  }

  private static ApiService createMockApiService(String name, String type, String clusterName) {
    ApiService s = EasyMock.createNiceMock(ApiService.class);
    EasyMock.expect(s.getName()).andReturn(name).anyTimes();
    EasyMock.expect(s.getType()).andReturn(type).anyTimes();

    ApiClusterRef clusterRef = EasyMock.createNiceMock(ApiClusterRef.class);
    EasyMock.expect(clusterRef.getClusterName()).andReturn(clusterName).anyTimes();
    EasyMock.replay(clusterRef);
    EasyMock.expect(s.getClusterRef()).andReturn(clusterRef).anyTimes();
    EasyMock.replay(s);
    return s;
  }

  private static ApiRole createMockApiRole(String name, String type, String hostname) {
    ApiRole r = EasyMock.createNiceMock(ApiRole.class);
    EasyMock.expect(r.getName()).andReturn(name).anyTimes();
    EasyMock.expect(r.getType()).andReturn(type).anyTimes();
    ApiHostRef hostRef = EasyMock.createNiceMock(ApiHostRef.class);
    EasyMock.expect(hostRef.getHostname()).andReturn(hostname).anyTimes();
    EasyMock.replay(hostRef);
    EasyMock.expect(r.getHostRef()).andReturn(hostRef).anyTimes();
    EasyMock.replay(r);
    return r;
  }

  private static ApiServiceConfig createMockApiServiceConfig(Map<String, String> properties) {
    ApiServiceConfig serviceConfig = EasyMock.createNiceMock(ApiServiceConfig.class);
    List<ApiConfig> serviceConfigs = new ArrayList<>();

    int i = 0;
    for (Map.Entry<String, String> property : properties.entrySet()) {
      ApiConfig config = EasyMock.createNiceMock(ApiConfig.class);
      if (i++ % 2 == 0) {
        EasyMock.expect(config.getName()).andReturn(property.getKey()).anyTimes();
      } else {
        EasyMock.expect(config.getRelatedName()).andReturn(property.getKey()).anyTimes();
      }
      EasyMock.expect(config.getValue()).andReturn(property.getValue()).anyTimes();
      EasyMock.replay(config);
      serviceConfigs.add(config);
    }

    EasyMock.expect(serviceConfig.getItems()).andReturn(serviceConfigs).anyTimes();
    EasyMock.replay(serviceConfig);
    return serviceConfig;
  }

  private static ApiConfigList createMockApiConfigList(Map<String, String> properties) {
    ApiConfigList configList = EasyMock.createNiceMock(ApiConfigList.class);
    List<ApiConfig> roleConfigs = new ArrayList<>();

    int i = 0;
    for (Map.Entry<String, String> property : properties.entrySet()) {
      ApiConfig config = EasyMock.createNiceMock(ApiConfig.class);
      if (i++ % 2 == 0) {
        EasyMock.expect(config.getName()).andReturn(property.getKey()).anyTimes();
      } else {
        EasyMock.expect(config.getRelatedName()).andReturn(property.getKey()).anyTimes();
      }
      EasyMock.expect(config.getValue()).andReturn(property.getValue()).anyTimes();
      EasyMock.replay(config);
      roleConfigs.add(config);
    }

    EasyMock.expect(configList.getItems()).andReturn(roleConfigs).anyTimes();
    EasyMock.replay(configList);
    return configList;
  }

  private static class TestDiscoveryApiClient extends DiscoveryApiClient {

    private Map<Type, ApiResponse<?>> responseMap = new HashMap<>();

    protected AtomicInteger executeCount = new AtomicInteger(0);

    TestDiscoveryApiClient(GatewayConfig gatewayConfig, ServiceDiscoveryConfig sdConfig, AliasService aliasService) {
      super(gatewayConfig, sdConfig, aliasService, null);
    }

    void addResponse(Type type, ApiResponse<?> response) {
      responseMap.put(type, response);
    }

    @Override
    boolean isKerberos() {
      return false;
    }

    @Override
    public <T> ApiResponse<T> execute(Call call, Type returnType) throws ApiException {
      executeCount.incrementAndGet();
      return (ApiResponse<T>) responseMap.get(returnType);
    }

    int getExecuteCount() {
      return executeCount.get();
    }
  }

  private static class TestFaultyDiscoveryApiClient extends TestDiscoveryApiClient {

    TestFaultyDiscoveryApiClient(GatewayConfig gatewayConfig, ServiceDiscoveryConfig sdConfig, AliasService aliasService) {
      super(gatewayConfig, sdConfig, aliasService);
    }

    @Override
    public <T> ApiResponse<T> execute(Call call, Type returnType) throws ApiException {
      if (executeCount.getAndIncrement() < GatewayConfig.DEFAULT_CM_SERVICE_DISCOVERY_MAX_RETRY_ATTEMPTS - 2) {
        throw new ApiException(new SocketTimeoutException("Failed to connect to CM HOST"));
      }
      return super.execute(call, returnType);
    }
  }

  private static class TestResponseBase<T> extends ApiResponse<T> {
    protected T data;

    TestResponseBase(T data) {
      super(200, Collections.emptyMap());
      this.data = data;
    }

    @Override
    public T getData() {
      return data;
    }
  }

  private static class TestApiServiceListResponse extends TestResponseBase<ApiServiceList> {
    TestApiServiceListResponse(ApiServiceList data) {
      super(data);
    }
  }

  private static class TestApiServiceConfigResponse extends TestResponseBase<ApiServiceConfig> {
    TestApiServiceConfigResponse(ApiServiceConfig data) {
      super(data);
    }
  }

  private static class TestApiRoleListResponse extends TestResponseBase<ApiRoleList> {
    TestApiRoleListResponse(ApiRoleList data) {
      super(data);
    }
  }

  private static class TestApiConfigListResponse extends TestResponseBase<ApiConfigList> {
    TestApiConfigListResponse(ApiConfigList data) {
      super(data);
    }
  }

  private static final class ApiClusterRefExt extends ApiClusterRef {
    private String name;

    ApiClusterRefExt(String name) {
      this.name = name;
    }

    @Override
    public String getClusterName() {
      return name;
    }
  }

}
