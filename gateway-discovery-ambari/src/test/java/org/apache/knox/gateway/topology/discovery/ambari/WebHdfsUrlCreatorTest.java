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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WebHdfsUrlCreatorTest {


  @Test
  public void testHttpEndpointAddress() {
    final String HTTP_ADDRESS  = "host1:20070";
    final String HTTPS_ADDRESS = "host2:20470";

    AmbariCluster.ServiceConfiguration hdfsSvcConfig = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);

    Map<String, String> configProps = new HashMap<>();
    configProps.put(HDFSURLCreatorBase.HTTP_POLICY_PROPERTY, HDFSURLCreatorBase.HTTP_ONLY_POLICY);
    configProps.put(HDFSURLCreatorBase.HTTP_ADDRESS_PROPERTY, HTTP_ADDRESS);
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY, HTTPS_ADDRESS);

    EasyMock.expect(hdfsSvcConfig.getProperties()).andReturn(configProps).anyTimes();
    EasyMock.replay(hdfsSvcConfig);

    AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
    EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site"))
            .andReturn(hdfsSvcConfig)
            .anyTimes();
    EasyMock.replay(cluster);

    WebHdfsUrlCreator c = new WebHdfsUrlCreator();
    c.init(cluster);
    List<String> urls = c.create("WEBHDFS", null);
    assertNotNull(urls);
    assertFalse(urls.isEmpty());
    assertEquals(1, urls.size());
    assertEquals("http://" + HTTP_ADDRESS + "/webhdfs", urls.get(0));
  }


  @Test
  public void testHttpsEndpointAddress() {
    final String HTTP_ADDRESS  = "host1:20070";
    final String HTTPS_ADDRESS = "host2:20470";

    AmbariCluster.ServiceConfiguration hdfsSvcConfig = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);

    Map<String, String> configProps = new HashMap<>();
    configProps.put(HDFSURLCreatorBase.HTTP_POLICY_PROPERTY, HDFSURLCreatorBase.HTTPS_ONLY_POLICY);
    configProps.put(HDFSURLCreatorBase.HTTP_ADDRESS_PROPERTY, HTTP_ADDRESS);
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY, HTTPS_ADDRESS);

    EasyMock.expect(hdfsSvcConfig.getProperties()).andReturn(configProps).anyTimes();
    EasyMock.replay(hdfsSvcConfig);

    AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
    EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site"))
            .andReturn(hdfsSvcConfig)
            .anyTimes();
    EasyMock.replay(cluster);

    WebHdfsUrlCreator c = new WebHdfsUrlCreator();
    c.init(cluster);
    List<String> urls = c.create("WEBHDFS", null);
    assertNotNull(urls);
    assertFalse(urls.isEmpty());
    assertEquals(1, urls.size());
    assertEquals("https://" + HTTPS_ADDRESS + "/webhdfs", urls.get(0));
  }


  @Test
  public void testFederatedHttpEndpointAddress() {
    final String HTTP_ADDRESS  = "host1:20070";
    final String HTTPS_ADDRESS = "host2:20470";

    AmbariCluster.ServiceConfiguration coreSiteConfig = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
    Map<String, String> corSiteProps = new HashMap<>();
    corSiteProps.put("fs.defaultFS", "hdfs://X");
    EasyMock.expect(coreSiteConfig.getProperties()).andReturn(corSiteProps).anyTimes();
    EasyMock.replay(coreSiteConfig);

    AmbariCluster.ServiceConfiguration hdfsSiteConfig = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
    Map<String, String> configProps = new HashMap<>();
    configProps.put(HDFSURLCreatorBase.HTTP_POLICY_PROPERTY, HDFSURLCreatorBase.HTTP_ONLY_POLICY);
    configProps.put(HDFSURLCreatorBase.HTTP_ADDRESS_PROPERTY, HTTP_ADDRESS);
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY, HTTPS_ADDRESS);
    configProps.put("dfs.nameservices", "X,Y");
    configProps.put("dfs.ha.namenodes.X", "nn1,nn2");
    configProps.put(HDFSURLCreatorBase.HTTP_ADDRESS_PROPERTY + ".X.nn1", "host3:20070");
    configProps.put(HDFSURLCreatorBase.HTTP_ADDRESS_PROPERTY + ".X.nn2", "host4:20070");

    EasyMock.expect(hdfsSiteConfig.getProperties()).andReturn(configProps).anyTimes();
    EasyMock.replay(hdfsSiteConfig);

    AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
    EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site"))
            .andReturn(hdfsSiteConfig)
            .anyTimes();
    EasyMock.expect(cluster.getServiceConfiguration("HDFS", "core-site"))
            .andReturn(coreSiteConfig)
            .anyTimes();
    EasyMock.replay(cluster);

    WebHdfsUrlCreator c = new WebHdfsUrlCreator();
    c.init(cluster);
    List<String> urls = c.create("WEBHDFS", null);
    assertNotNull(urls);
    assertFalse(urls.isEmpty());
    assertEquals(2, urls.size());
    assertTrue(urls.contains("http://" + "host3:20070" + "/webhdfs"));
    assertTrue(urls.contains("http://" + "host4:20070" + "/webhdfs"));
  }


  @Test
  public void testFederatedHttpsEndpointAddress() {
    final String HTTP_ADDRESS  = "host1:20070";
    final String HTTPS_ADDRESS = "host2:20470";

    AmbariCluster.ServiceConfiguration coreSiteConfig = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
    Map<String, String> corSiteProps = new HashMap<>();
    corSiteProps.put("fs.defaultFS", "hdfs://Y");
    EasyMock.expect(coreSiteConfig.getProperties()).andReturn(corSiteProps).anyTimes();
    EasyMock.replay(coreSiteConfig);

    AmbariCluster.ServiceConfiguration hdfsSiteConfig = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
    Map<String, String> configProps = new HashMap<>();
    configProps.put(HDFSURLCreatorBase.HTTP_POLICY_PROPERTY, HDFSURLCreatorBase.HTTPS_ONLY_POLICY);
    configProps.put(HDFSURLCreatorBase.HTTP_ADDRESS_PROPERTY, HTTP_ADDRESS);
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY, HTTPS_ADDRESS);
    configProps.put("dfs.nameservices", "X,Y");
    configProps.put("dfs.ha.namenodes.Y", "nn7,nn8");
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY + ".Y.nn7", "host5:20470");
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY + ".Y.nn8", "host6:20470");

    EasyMock.expect(hdfsSiteConfig.getProperties()).andReturn(configProps).anyTimes();
    EasyMock.replay(hdfsSiteConfig);

    AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
    EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site"))
            .andReturn(hdfsSiteConfig)
            .anyTimes();
    EasyMock.expect(cluster.getServiceConfiguration("HDFS", "core-site"))
            .andReturn(coreSiteConfig)
            .anyTimes();
    EasyMock.replay(cluster);

    WebHdfsUrlCreator c = new WebHdfsUrlCreator();
    c.init(cluster);
    List<String> urls = c.create("WEBHDFS", null);
    assertNotNull(urls);
    assertFalse(urls.isEmpty());
    assertEquals(2, urls.size());
    assertTrue(urls.contains("https://" + "host5:20470" + "/webhdfs"));
    assertTrue(urls.contains("https://" + "host6:20470" + "/webhdfs"));
  }


  @Test
  public void testFederatedHttpsWebHdfsEndpointAddressWithValidNS() {
    final String HTTP_ADDRESS  = "host1:20070";
    final String HTTPS_ADDRESS = "host2:20470";

    AmbariCluster.ServiceConfiguration coreSiteConfig = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
    Map<String, String> corSiteProps = new HashMap<>();
    corSiteProps.put("fs.defaultFS", "hdfs://Y");
    EasyMock.expect(coreSiteConfig.getProperties()).andReturn(corSiteProps).anyTimes();
    EasyMock.replay(coreSiteConfig);

    AmbariCluster.ServiceConfiguration hdfsSiteConfig = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
    Map<String, String> configProps = new HashMap<>();
    configProps.put(HDFSURLCreatorBase.HTTP_POLICY_PROPERTY, HDFSURLCreatorBase.HTTPS_ONLY_POLICY);
    configProps.put(HDFSURLCreatorBase.HTTP_ADDRESS_PROPERTY, HTTP_ADDRESS);
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY, HTTPS_ADDRESS);
    configProps.put("dfs.nameservices", "X,Y");
    configProps.put("dfs.ha.namenodes.X", "nn2,nn3");
    configProps.put("dfs.ha.namenodes.Y", "nn7,nn8");
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY + ".X.nn2", "host3:20470");
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY + ".X.nn3", "host4:20470");
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY + ".Y.nn7", "host5:20470");
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY + ".Y.nn8", "host6:20470");

    EasyMock.expect(hdfsSiteConfig.getProperties()).andReturn(configProps).anyTimes();
    EasyMock.replay(hdfsSiteConfig);

    AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
    EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site"))
            .andReturn(hdfsSiteConfig)
            .anyTimes();
    EasyMock.expect(cluster.getServiceConfiguration("HDFS", "core-site"))
            .andReturn(coreSiteConfig)
            .anyTimes();
    EasyMock.replay(cluster);

    Map<String, String> serviceParams = new HashMap<>();
    serviceParams.put("discovery-nameservice", "X");

    WebHdfsUrlCreator c = new WebHdfsUrlCreator();
    c.init(cluster);
    List<String> urls = c.create("WEBHDFS", serviceParams);
    assertNotNull(urls);
    assertFalse(urls.isEmpty());
    assertEquals(2, urls.size());
    assertTrue(urls.contains("https://" + "host3:20470" + "/webhdfs"));
    assertTrue(urls.contains("https://" + "host4:20470" + "/webhdfs"));
  }


  @Test
  public void testFederatedHttpsWebHdfsEndpointAddressWithInvalidNS() {
    final String HTTP_ADDRESS  = "host1:20070";
    final String HTTPS_ADDRESS = "host2:20470";

    AmbariCluster.ServiceConfiguration coreSiteConfig = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
    Map<String, String> corSiteProps = new HashMap<>();
    corSiteProps.put("fs.defaultFS", "hdfs://Y");
    EasyMock.expect(coreSiteConfig.getProperties()).andReturn(corSiteProps).anyTimes();
    EasyMock.replay(coreSiteConfig);

    AmbariCluster.ServiceConfiguration hdfsSiteConfig = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
    Map<String, String> configProps = new HashMap<>();
    configProps.put(HDFSURLCreatorBase.HTTP_POLICY_PROPERTY, HDFSURLCreatorBase.HTTPS_ONLY_POLICY);
    configProps.put(HDFSURLCreatorBase.HTTP_ADDRESS_PROPERTY, HTTP_ADDRESS);
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY, HTTPS_ADDRESS);
    configProps.put("dfs.nameservices", "X,Y");
    configProps.put("dfs.ha.namenodes.X", "nn2,nn3");
    configProps.put("dfs.ha.namenodes.Y", "nn7,nn8");
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY + ".X.nn2", "host3:20470");
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY + ".X.nn3", "host4:20470");
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY + ".Y.nn7", "host5:20470");
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY + ".Y.nn8", "host6:20470");

    EasyMock.expect(hdfsSiteConfig.getProperties()).andReturn(configProps).anyTimes();
    EasyMock.replay(hdfsSiteConfig);

    AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
    EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site"))
            .andReturn(hdfsSiteConfig)
            .anyTimes();
    EasyMock.expect(cluster.getServiceConfiguration("HDFS", "core-site"))
            .andReturn(coreSiteConfig)
            .anyTimes();
    EasyMock.replay(cluster);

    Map<String, String> serviceParams = new HashMap<>();
    serviceParams.put("discovery-nameservice", "Z");

    WebHdfsUrlCreator c = new WebHdfsUrlCreator();
    c.init(cluster);
    List<String> urls = c.create("WEBHDFS", serviceParams);
    assertNotNull(urls);
    assertTrue(urls.isEmpty());
  }


  @Test
  public void testFederatedHttpsNamenodeEndpointAddressWithValidDeclaredNS() {
    final String HTTP_ADDRESS  = "host1:20070";
    final String HTTPS_ADDRESS = "host2:20470";

    AmbariComponent nnComp = EasyMock.createNiceMock(AmbariComponent.class);
    EasyMock.expect(nnComp.getConfigProperty("dfs.nameservices")).andReturn("X,Y").anyTimes();
    EasyMock.replay(nnComp);

    AmbariCluster.ServiceConfiguration coreSiteConfig = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
    Map<String, String> corSiteProps = new HashMap<>();
    corSiteProps.put("fs.defaultFS", "hdfs://Y");
    EasyMock.expect(coreSiteConfig.getProperties()).andReturn(corSiteProps).anyTimes();
    EasyMock.replay(coreSiteConfig);

    AmbariCluster.ServiceConfiguration hdfsSiteConfig = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
    Map<String, String> configProps = new HashMap<>();
    configProps.put(HDFSURLCreatorBase.HTTP_POLICY_PROPERTY, HDFSURLCreatorBase.HTTPS_ONLY_POLICY);
    configProps.put(HDFSURLCreatorBase.HTTP_ADDRESS_PROPERTY, HTTP_ADDRESS);
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY, HTTPS_ADDRESS);
    configProps.put("dfs.ha.namenodes.X", "nn2,nn3");
    configProps.put("dfs.ha.namenodes.Y", "nn7,nn8");
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY + ".X.nn2", "host3:20470");
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY + ".X.nn3", "host4:20470");
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY + ".Y.nn7", "host5:20470");
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY + ".Y.nn8", "host6:20470");

    EasyMock.expect(hdfsSiteConfig.getProperties()).andReturn(configProps).anyTimes();
    EasyMock.replay(hdfsSiteConfig);

    AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
    EasyMock.expect(cluster.getComponent("NAMENODE"))
            .andReturn(nnComp)
            .anyTimes();
    EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site"))
            .andReturn(hdfsSiteConfig)
            .anyTimes();
    EasyMock.expect(cluster.getServiceConfiguration("HDFS", "core-site"))
            .andReturn(coreSiteConfig)
            .anyTimes();
    EasyMock.replay(cluster);

    NameNodeUrlCreator c = new NameNodeUrlCreator();
    c.init(cluster);

    Map<String, String> serviceParams = new HashMap<>();
    serviceParams.put("discovery-nameservice", "X");

    List<String> urls = c.create("NAMENODE", serviceParams);
    assertNotNull(urls);
    assertFalse(urls.isEmpty());
    assertEquals(1, urls.size());
    assertTrue(urls.contains("hdfs://X"));
  }


  @Test
  public void testFederatedHttpsNamenodeEndpointAddressWithInvalidDeclaredNS() {
    final String HTTP_ADDRESS  = "host1:20070";
    final String HTTPS_ADDRESS = "host2:20470";

    AmbariComponent nnComp = EasyMock.createNiceMock(AmbariComponent.class);
    EasyMock.expect(nnComp.getConfigProperty("dfs.nameservices")).andReturn("X,Y").anyTimes();
    EasyMock.replay(nnComp);

    AmbariCluster.ServiceConfiguration coreSiteConfig = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
    Map<String, String> corSiteProps = new HashMap<>();
    corSiteProps.put("fs.defaultFS", "hdfs://Y");
    EasyMock.expect(coreSiteConfig.getProperties()).andReturn(corSiteProps).anyTimes();
    EasyMock.replay(coreSiteConfig);

    AmbariCluster.ServiceConfiguration hdfsSiteConfig = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
    Map<String, String> configProps = new HashMap<>();
    configProps.put(HDFSURLCreatorBase.HTTP_POLICY_PROPERTY, HDFSURLCreatorBase.HTTPS_ONLY_POLICY);
    configProps.put(HDFSURLCreatorBase.HTTP_ADDRESS_PROPERTY, HTTP_ADDRESS);
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY, HTTPS_ADDRESS);
    configProps.put("dfs.ha.namenodes.Y", "nn7,nn8");
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY + ".Y.nn7", "host5:20470");
    configProps.put(HDFSURLCreatorBase.HTTPS_ADDRESS_PROPERTY + ".Y.nn8", "host6:20470");

    EasyMock.expect(hdfsSiteConfig.getProperties()).andReturn(configProps).anyTimes();
    EasyMock.replay(hdfsSiteConfig);

    AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
    EasyMock.expect(cluster.getComponent("NAMENODE"))
            .andReturn(nnComp)
            .anyTimes();
    EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site"))
            .andReturn(hdfsSiteConfig)
            .anyTimes();
    EasyMock.expect(cluster.getServiceConfiguration("HDFS", "core-site"))
            .andReturn(coreSiteConfig)
            .anyTimes();
    EasyMock.replay(cluster);

    NameNodeUrlCreator c = new NameNodeUrlCreator();
    c.init(cluster);

    Map<String, String> serviceParams = new HashMap<>();
    serviceParams.put("discovery-nameservice", "Z");

    List<String> urls = c.create("NAMENODE", serviceParams);
    assertNotNull(urls);
    assertTrue(urls.isEmpty());
  }

}
