/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
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
package org.apache.knox.gateway.topology.discovery.ambari;

import org.easymock.EasyMock;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SparkServiceURLCreatorTest {

  @Test
  public void testSparkHistoryUI() {
    doTestSparkHistoryUI("SPARK_JOBHISTORYSERVER");
  }

  @Test
  public void testSpark2HistoryUI() {
    doTestSparkHistoryUI("SPARK2_JOBHISTORYSERVER");
  }

  private void doTestSparkHistoryUI(String componentName) {
    final String PORT = "4545";

    AmbariComponent ac = EasyMock.createNiceMock(AmbariComponent.class);
    List<String> hostNames = Arrays.asList("host1", "host2");
    EasyMock.expect(ac.getHostNames()).andReturn(hostNames).anyTimes();
    EasyMock.expect(ac.getConfigProperty("spark.history.ui.port")).andReturn(PORT).anyTimes();
    EasyMock.replay(ac);

    AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
    EasyMock.expect(cluster.getComponent(componentName)).andReturn(ac).anyTimes();
    EasyMock.replay(cluster);

    SparkHistoryUIServiceURLCreator c = new SparkHistoryUIServiceURLCreator();
    c.init(cluster);
    List<String> urls = c.create("SPARKHISTORYUI", null);
    assertNotNull(urls);
    assertFalse(urls.isEmpty());
    assertEquals(2, urls.size());
    assertEquals("http://host1:" + PORT, urls.get(0));
    assertEquals("http://host2:" + PORT, urls.get(1));
  }


  @Test
  public void testSparkHistoryUI_SSL() {
    doTestSparkHistoryUI_SSL("SPARK_JOBHISTORYSERVER", true, false, "4321");
  }

  @Test
  public void testSparkHistoryUI_SSL_FALLBACK_FLAG() {
    doTestSparkHistoryUI_SSL("SPARK_JOBHISTORYSERVER", false, true, "4321");
  }

  @Test
  public void testSparkHistoryUI_SSL_FALLBACK_PORT() {
    doTestSparkHistoryUI_SSL("SPARK_JOBHISTORYSERVER", true, false, null);
  }

  @Test
  public void testSparkHistoryUI_SSL_FALLBACK_FLAG_AND_PORT() {
    doTestSparkHistoryUI_SSL("SPARK_JOBHISTORYSERVER", false, true, null);
  }

  @Test
  public void testSpark2HistoryUI_SSL() {
    doTestSparkHistoryUI_SSL("SPARK2_JOBHISTORYSERVER", true, false, "4321");
  }

  @Test
  public void testSparkwHistoryUI_SSL_FALLBACK_FLAG() {
    doTestSparkHistoryUI_SSL("SPARK2_JOBHISTORYSERVER", false, true, "4321");
  }

  @Test
  public void testSpark2HistoryUI_SSL_FALLBACK_PORT() {
    doTestSparkHistoryUI_SSL("SPARK2_JOBHISTORYSERVER", true, false, null);
  }

  @Test
  public void testSpark2HistoryUI_SSL_FALLBACK_FLAG_AND_PORT() {
    doTestSparkHistoryUI_SSL("SPARK2_JOBHISTORYSERVER", false, true, null);
  }

  private void doTestSparkHistoryUI_SSL(String componentName, Boolean sslSHS, Boolean sslSpark, String sslSHSPort) {
    final String PORT = "4545";

    boolean isSSLConfigured = false;

    AmbariComponent ac = EasyMock.createNiceMock(AmbariComponent.class);
    List<String> hostNames = Arrays.asList("host1", "host2");
    EasyMock.expect(ac.getHostNames()).andReturn(hostNames).anyTimes();
    EasyMock.expect(ac.getConfigProperty("spark.history.ui.port")).andReturn(PORT).anyTimes();
    if (sslSHS != null) {
      isSSLConfigured = true;
      EasyMock.expect(ac.getConfigProperty("spark.ssl.historyServer.enabled")).andReturn(String.valueOf(sslSHS)).anyTimes();
    }
    if (sslSpark != null) {
      isSSLConfigured = true;
      EasyMock.expect(ac.getConfigProperty("spark.ssl.enabled")).andReturn(String.valueOf(sslSpark)).anyTimes();
    }
    if (sslSHSPort != null) {
      EasyMock.expect(ac.getConfigProperty("spark.ssl.historyServer.port")).andReturn(sslSHSPort).anyTimes();
    }
    EasyMock.replay(ac);

    AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
    EasyMock.expect(cluster.getComponent(componentName)).andReturn(ac).anyTimes();
    EasyMock.replay(cluster);

    SparkHistoryUIServiceURLCreator c = new SparkHistoryUIServiceURLCreator();
    c.init(cluster);
    List<String> urls = c.create("SPARKHISTORYUI", null);
    assertNotNull(urls);
    assertFalse(urls.isEmpty());
    assertEquals(2, urls.size());

    if (isSSLConfigured) {
      String expectedPort = sslSHSPort != null ? sslSHSPort : String.valueOf(Integer.valueOf(PORT) + 400);
      assertEquals("https://host1:" + expectedPort, urls.get(0));
      assertEquals("https://host2:" + expectedPort, urls.get(1));
    } else {
      assertEquals("http://host1:" + PORT, urls.get(0));
      assertEquals("http://host2:" + PORT, urls.get(1));
    }
  }


  @Test
  public void testSparkAndSpark2HistoryUI() {
    final String PORT  = "4545";
    final String PORT2 = "6767";

    AmbariComponent ac = EasyMock.createNiceMock(AmbariComponent.class);
    EasyMock.expect(ac.getHostNames()).andReturn(Arrays.asList("host1", "host2")).anyTimes();
    EasyMock.expect(ac.getConfigProperty("spark.history.ui.port")).andReturn(PORT).anyTimes();
    EasyMock.replay(ac);

    AmbariComponent ac2 = EasyMock.createNiceMock(AmbariComponent.class);
    EasyMock.expect(ac2.getHostNames()).andReturn(Arrays.asList("host3", "host4")).anyTimes();
    EasyMock.expect(ac2.getConfigProperty("spark.history.ui.port")).andReturn(PORT2).anyTimes();
    EasyMock.replay(ac2);

    AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
    EasyMock.expect(cluster.getComponent("SPARK_JOBHISTORYSERVER")).andReturn(ac).anyTimes();
    EasyMock.expect(cluster.getComponent("SPARK2_JOBHISTORYSERVER")).andReturn(ac2).anyTimes();
    EasyMock.replay(cluster);

    SparkHistoryUIServiceURLCreator c = new SparkHistoryUIServiceURLCreator();
    c.init(cluster);
    List<String> urls = c.create("SPARKHISTORYUI", null);
    assertNotNull(urls);
    assertFalse(urls.isEmpty());
    assertEquals(2, urls.size());
    assertEquals("http://host1:" + PORT, urls.get(0));
    assertEquals("http://host2:" + PORT, urls.get(1));
  }


  @Test
  public void testLivyServer() {
    doTestLivyServer("LIVY_SERVER");
  }

  @Test
  public void testLivy2Server() {
    doTestLivyServer("LIVY2_SERVER");
  }

  private void doTestLivyServer(String componentName) {
    final String PORT = "4545";

    AmbariComponent ac = EasyMock.createNiceMock(AmbariComponent.class);
    List<String> hostNames = Arrays.asList("host1", "host2");
    EasyMock.expect(ac.getHostNames()).andReturn(hostNames).anyTimes();
    EasyMock.expect(ac.getConfigProperty("livy.server.port")).andReturn(PORT).anyTimes();
    EasyMock.replay(ac);

    AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
    EasyMock.expect(cluster.getComponent(componentName)).andReturn(ac).anyTimes();
    EasyMock.replay(cluster);

    LivyServiceURLCreator c = new LivyServiceURLCreator();
    c.init(cluster);
    List<String> urls = c.create("LIVYSERVER", null);
    assertNotNull(urls);
    assertFalse(urls.isEmpty());
    assertEquals(2, urls.size());
    assertEquals("http://host1:" + PORT, urls.get(0));
    assertEquals("http://host2:" + PORT, urls.get(1));
  }

  @Test
  public void testLivyServer_SSL() {
    doTestLivyServerSSL("LIVY_SERVER");
  }

  @Test
  public void testLivy2Server_SSL() {
    doTestLivyServerSSL("LIVY2_SERVER");
  }

  private void doTestLivyServerSSL(String componentName) {
    final String PORT = "4545";

    AmbariComponent ac = EasyMock.createNiceMock(AmbariComponent.class);
    List<String> hostNames = Arrays.asList("host1", "host2");
    EasyMock.expect(ac.getHostNames()).andReturn(hostNames).anyTimes();
    EasyMock.expect(ac.getConfigProperty("livy.server.port")).andReturn(PORT).anyTimes();
    EasyMock.expect(ac.getConfigProperty("livy.keystore")).andReturn("mykeystore").anyTimes();
    EasyMock.replay(ac);

    AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
    EasyMock.expect(cluster.getComponent(componentName)).andReturn(ac).anyTimes();
    EasyMock.replay(cluster);

    LivyServiceURLCreator c = new LivyServiceURLCreator();
    c.init(cluster);
    List<String> urls = c.create("LIVYSERVER", null);
    assertNotNull(urls);
    assertFalse(urls.isEmpty());
    assertEquals(2, urls.size());
    assertEquals("https://host1:" + PORT, urls.get(0));
    assertEquals("https://host2:" + PORT, urls.get(1));
  }

  @Test
  public void testLivyAndLivy2Server() {
    final String PORT  = "4545";
    final String PORT2 = "2323";

    AmbariComponent ac = EasyMock.createNiceMock(AmbariComponent.class);
    EasyMock.expect(ac.getHostNames()).andReturn(Arrays.asList("host1", "host2")).anyTimes();
    EasyMock.expect(ac.getConfigProperty("livy.server.port")).andReturn(PORT).anyTimes();
    EasyMock.replay(ac);

    AmbariComponent ac2 = EasyMock.createNiceMock(AmbariComponent.class);
    EasyMock.expect(ac2.getHostNames()).andReturn(Arrays.asList("host3", "host4")).anyTimes();
    EasyMock.expect(ac2.getConfigProperty("livy.server.port")).andReturn(PORT2).anyTimes();
    EasyMock.replay(ac2);

    AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
    EasyMock.expect(cluster.getComponent("LIVY_SERVER")).andReturn(ac).anyTimes();
    EasyMock.expect(cluster.getComponent("LIVY2_SERVER")).andReturn(ac2).anyTimes();
    EasyMock.replay(cluster);

    LivyServiceURLCreator c = new LivyServiceURLCreator();
    c.init(cluster);
    List<String> urls = c.create("LIVYSERVER", null);
    assertNotNull(urls);
    assertFalse(urls.isEmpty());
    assertEquals(2, urls.size());
    assertEquals("http://host1:" + PORT, urls.get(0));
    assertEquals("http://host2:" + PORT, urls.get(1));
  }


  @Test
  public void testSparkThriftUI() {
    doTestSparkThriftUI("SPARK_THRIFTSERVER", null);
  }

  @Test
  public void testSpark2ThriftUI() {
    doTestSparkThriftUI("SPARK2_THRIFTSERVER", null);
  }

  @Test
  public void testSparkThriftUIWithEndpointPath() { doTestSparkThriftUIWithEndpointPath("SPARK_THRIFTSERVER"); }

  @Test
  public void testSpark2ThriftUIWithEndpointPath() { doTestSparkThriftUIWithEndpointPath("SPARK2_THRIFTSERVER"); }

  @Test
  public void testSparkThriftUIBinaryTransport() { doTestSparkThriftUIBinaryTransport("SPARK_THRIFTSERVER"); }

  @Test
  public void testSpark2ThriftUIBinaryTransport() { doTestSparkThriftUIBinaryTransport("SPARK2_THRIFTSERVER"); }


  private void doTestSparkThriftUIWithEndpointPath(String componentName) {
    doTestSparkThriftUI(componentName, "http", "mypath");
  }

  private void doTestSparkThriftUIBinaryTransport(String componentName) {
    doTestSparkThriftUI(componentName, "binary", null);
  }


  private void doTestSparkThriftUI(String componentName, String endpointPath) {
    doTestSparkThriftUI(componentName, "http", endpointPath);
  }


  private void doTestSparkThriftUI(String componentName, String transportMode, String endpointPath) {
    final String PORT = "4545";

    AmbariComponent ac = EasyMock.createNiceMock(AmbariComponent.class);
    List<String> hostNames = Arrays.asList("host1", "host2");
    EasyMock.expect(ac.getHostNames()).andReturn(hostNames).anyTimes();
    EasyMock.expect(ac.getConfigProperty("hive.server2.thrift.http.port")).andReturn(PORT).anyTimes();
    EasyMock.expect(ac.getConfigProperty("hive.server2.transport.mode")).andReturn(transportMode).anyTimes();
    EasyMock.expect(ac.getConfigProperty("hive.server2.http.endpoint")).andReturn(endpointPath).anyTimes();
    EasyMock.replay(ac);

    AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
    EasyMock.expect(cluster.getComponent(componentName)).andReturn(ac).anyTimes();
    EasyMock.replay(cluster);

    SparkThriftServerUIServiceURLCreator c = new SparkThriftServerUIServiceURLCreator();
    c.init(cluster);
    List<String> urls = c.create("THRIFTSERVERUI", null);
    assertNotNull(urls);

    if ("http".equalsIgnoreCase(transportMode)) {
      assertFalse(urls.isEmpty());
      assertEquals(2, urls.size());
      assertEquals("http://host1:" + PORT + (endpointPath != null ? "/" + endpointPath : ""), urls.get(0));
      assertEquals("http://host2:" + PORT + (endpointPath != null ? "/" + endpointPath : ""), urls.get(1));
    } else {
      assertTrue(urls.isEmpty());
    }
  }


}
