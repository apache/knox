/**
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
package org.apache.hadoop.gateway.topology.discovery.ambari;

import org.apache.commons.io.FileUtils;
import org.easymock.EasyMock;
import org.junit.Test;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;


public class AmbariDynamicServiceURLCreatorTest {

    @Test
    public void testHiveURLFromInternalMapping() throws Exception {
        testHiveURL(null);
    }

    @Test
    public void testHiveURLFromExternalMapping() throws Exception {
        testHiveURL(TEST_MAPPING_CONFIG);
    }

    private void testHiveURL(Object mappingConfiguration) throws Exception {

        final String   SERVICE_NAME = "HIVE";
        final String[] HOSTNAMES    = {"host3", "host2", "host4"};
        final String   HTTP_PATH    = "cliservice";
        final String   HTTP_PORT    = "10001";
        final String   BINARY_PORT  = "10000";

        String expectedScheme = "http";

        final List<String> hiveServerHosts = Arrays.asList(HOSTNAMES);

        AmbariComponent hiveServer = EasyMock.createNiceMock(AmbariComponent.class);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("HIVE_SERVER")).andReturn(hiveServer).anyTimes();
        EasyMock.replay(cluster);

        // Configure HTTP Transport
        EasyMock.expect(hiveServer.getHostNames()).andReturn(hiveServerHosts).anyTimes();
        EasyMock.expect(hiveServer.getConfigProperty("hive.server2.use.SSL")).andReturn("false").anyTimes();
        EasyMock.expect(hiveServer.getConfigProperty("hive.server2.thrift.http.path")).andReturn(HTTP_PATH).anyTimes();
        EasyMock.expect(hiveServer.getConfigProperty("hive.server2.thrift.http.port")).andReturn(HTTP_PORT).anyTimes();
        EasyMock.expect(hiveServer.getConfigProperty("hive.server2.transport.mode")).andReturn("http").anyTimes();
        EasyMock.replay(hiveServer);

        // Run the test
        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, mappingConfiguration);
        List<String> urls = builder.create(SERVICE_NAME);
        assertEquals(HOSTNAMES.length, urls.size());
        validateServiceURLs(urls, HOSTNAMES, expectedScheme, HTTP_PORT, HTTP_PATH);

        // Configure BINARY Transport
        EasyMock.reset(hiveServer);
        EasyMock.expect(hiveServer.getHostNames()).andReturn(hiveServerHosts).anyTimes();
        EasyMock.expect(hiveServer.getConfigProperty("hive.server2.use.SSL")).andReturn("false").anyTimes();
        EasyMock.expect(hiveServer.getConfigProperty("hive.server2.thrift.http.path")).andReturn("").anyTimes();
        EasyMock.expect(hiveServer.getConfigProperty("hive.server2.thrift.http.port")).andReturn(HTTP_PORT).anyTimes();
        EasyMock.expect(hiveServer.getConfigProperty("hive.server2.thrift.port")).andReturn(BINARY_PORT).anyTimes();
        EasyMock.expect(hiveServer.getConfigProperty("hive.server2.transport.mode")).andReturn("binary").anyTimes();
        EasyMock.replay(hiveServer);

        // Run the test
        urls = builder.create(SERVICE_NAME);
        assertEquals(HOSTNAMES.length, urls.size());
        validateServiceURLs(urls, HOSTNAMES, expectedScheme, HTTP_PORT, "");

        // Configure HTTPS Transport
        EasyMock.reset(hiveServer);
        EasyMock.expect(hiveServer.getHostNames()).andReturn(hiveServerHosts).anyTimes();
        EasyMock.expect(hiveServer.getConfigProperty("hive.server2.use.SSL")).andReturn("true").anyTimes();
        EasyMock.expect(hiveServer.getConfigProperty("hive.server2.thrift.http.path")).andReturn(HTTP_PATH).anyTimes();
        EasyMock.expect(hiveServer.getConfigProperty("hive.server2.thrift.http.port")).andReturn(HTTP_PORT).anyTimes();
        EasyMock.expect(hiveServer.getConfigProperty("hive.server2.transport.mode")).andReturn("http").anyTimes();
        EasyMock.replay(hiveServer);

        // Run the test
        expectedScheme = "https";
        urls = builder.create(SERVICE_NAME);
        assertEquals(HOSTNAMES.length, urls.size());
        validateServiceURLs(urls, HOSTNAMES, expectedScheme, HTTP_PORT, HTTP_PATH);
    }


    @Test
    public void testResourceManagerURLFromInternalMapping() throws Exception {
        testResourceManagerURL(null);
    }

    @Test
    public void testResourceManagerURLFromExternalMapping() throws Exception {
        testResourceManagerURL(TEST_MAPPING_CONFIG);
    }

    private void testResourceManagerURL(Object mappingConfiguration) throws Exception {

        final String HTTP_ADDRESS  = "host2:1111";
        final String HTTPS_ADDRESS = "host2:22222";

        // HTTP
        AmbariComponent resman = EasyMock.createNiceMock(AmbariComponent.class);
        setResourceManagerComponentExpectations(resman, HTTP_ADDRESS, HTTPS_ADDRESS, "HTTP");

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("RESOURCEMANAGER")).andReturn(resman).anyTimes();
        EasyMock.replay(cluster);

        // Run the test
        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, mappingConfiguration);
        String url = builder.create("RESOURCEMANAGER").get(0);
        assertEquals("http://" + HTTP_ADDRESS + "/ws", url);

        // HTTPS
        EasyMock.reset(resman);
        setResourceManagerComponentExpectations(resman, HTTP_ADDRESS, HTTPS_ADDRESS, "HTTPS_ONLY");

        // Run the test
        url = builder.create("RESOURCEMANAGER").get(0);
        assertEquals("https://" + HTTPS_ADDRESS + "/ws", url);
    }

    private void setResourceManagerComponentExpectations(final AmbariComponent resmanMock,
                                                         final String          httpAddress,
                                                         final String          httpsAddress,
                                                         final String          httpPolicy) {
        EasyMock.expect(resmanMock.getConfigProperty("yarn.resourcemanager.webapp.address")).andReturn(httpAddress).anyTimes();
        EasyMock.expect(resmanMock.getConfigProperty("yarn.resourcemanager.webapp.https.address")).andReturn(httpsAddress).anyTimes();
        EasyMock.expect(resmanMock.getConfigProperty("yarn.http.policy")).andReturn(httpPolicy).anyTimes();
        EasyMock.replay(resmanMock);
    }

    @Test
    public void testJobTrackerURLFromInternalMapping() throws Exception {
        testJobTrackerURL(null);
    }

    @Test
    public void testJobTrackerURLFromExternalMapping() throws Exception {
        testJobTrackerURL(TEST_MAPPING_CONFIG);
    }

    private void testJobTrackerURL(Object mappingConfiguration) throws Exception {
        final String ADDRESS = "host2:5678";

        AmbariComponent resman = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(resman.getConfigProperty("yarn.resourcemanager.address")).andReturn(ADDRESS).anyTimes();
        EasyMock.replay(resman);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("RESOURCEMANAGER")).andReturn(resman).anyTimes();
        EasyMock.replay(cluster);

        // Run the test
        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, mappingConfiguration);
        String url = builder.create("JOBTRACKER").get(0);
        assertEquals("rpc://" + ADDRESS, url);
    }

    @Test
    public void testNameNodeURLFromInternalMapping() throws Exception {
        testNameNodeURL(null);
    }

    @Test
    public void testNameNodeURLFromExternalMapping() throws Exception {
        testNameNodeURL(TEST_MAPPING_CONFIG);
    }

    private void testNameNodeURL(Object mappingConfiguration) throws Exception {
        final String ADDRESS = "host1:1234";

        AmbariComponent namenode = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(namenode.getConfigProperty("dfs.namenode.rpc-address")).andReturn(ADDRESS).anyTimes();
        EasyMock.replay(namenode);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("NAMENODE")).andReturn(namenode).anyTimes();
        EasyMock.replay(cluster);

        // Run the test
        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, mappingConfiguration);
        String url = builder.create("NAMENODE").get(0);
        assertEquals("hdfs://" + ADDRESS, url);
    }


    @Test
    public void testNameNodeHAURLFromInternalMapping() throws Exception {
        testNameNodeURLHA(null);
    }

    @Test
    public void testNameNodeHAURLFromExternalMapping() throws Exception {
        testNameNodeURLHA(TEST_MAPPING_CONFIG);
    }

    private void testNameNodeURLHA(Object mappingConfiguration) throws Exception {
        final String NAMESERVICE = "myNSCluster";

        AmbariComponent namenode = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(namenode.getConfigProperty("dfs.nameservices")).andReturn(NAMESERVICE).anyTimes();
        EasyMock.replay(namenode);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("NAMENODE")).andReturn(namenode).anyTimes();
        EasyMock.replay(cluster);

        // Run the test
        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, mappingConfiguration);
        String url = builder.create("NAMENODE").get(0);
        assertEquals("hdfs://" + NAMESERVICE, url);
    }


    @Test
    public void testWebHCatURLFromInternalMapping() throws Exception {
        testWebHCatURL(null);
    }

    @Test
    public void testWebHCatURLFromExternalMapping() throws Exception {
        testWebHCatURL(TEST_MAPPING_CONFIG);
    }

    private void testWebHCatURL(Object mappingConfiguration) throws Exception {

        final String HOSTNAME = "host3";
        final String PORT     = "1919";

        AmbariComponent webhcatServer = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(webhcatServer.getConfigProperty("templeton.port")).andReturn(PORT).anyTimes();
        List<String> webHcatServerHosts = Collections.singletonList(HOSTNAME);
        EasyMock.expect(webhcatServer.getHostNames()).andReturn(webHcatServerHosts).anyTimes();
        EasyMock.replay(webhcatServer);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("WEBHCAT_SERVER")).andReturn(webhcatServer).anyTimes();
        EasyMock.replay(cluster);

        // Run the test
        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, mappingConfiguration);
        String url = builder.create("WEBHCAT").get(0);
        assertEquals("http://" + HOSTNAME + ":" + PORT + "/templeton", url);
    }

    @Test
    public void testOozieURLFromInternalMapping() throws Exception {
        testOozieURL(null);
    }

    @Test
    public void testOozieURLFromExternalMapping() throws Exception {
        testOozieURL(TEST_MAPPING_CONFIG);
    }

    private void testOozieURL(Object mappingConfiguration) throws Exception {
        final String URL = "http://host3:2222";

        AmbariComponent oozieServer = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(oozieServer.getConfigProperty("oozie.base.url")).andReturn(URL).anyTimes();
        EasyMock.replay(oozieServer);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("OOZIE_SERVER")).andReturn(oozieServer).anyTimes();
        EasyMock.replay(cluster);

        // Run the test
        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, mappingConfiguration);
        String url = builder.create("OOZIE").get(0);
        assertEquals(URL, url);
    }

    @Test
    public void testWebHBaseURLFromInternalMapping() throws Exception {
        testWebHBaseURL(null);
    }

    @Test
    public void testWebHBaseURLFromExternalMapping() throws Exception {
        testWebHBaseURL(TEST_MAPPING_CONFIG);
    }

    private void testWebHBaseURL(Object mappingConfiguration) throws Exception {
        final String[] HOSTNAMES = {"host2", "host4"};

        AmbariComponent hbaseMaster = EasyMock.createNiceMock(AmbariComponent.class);
        List<String> hbaseMasterHosts = Arrays.asList(HOSTNAMES);
        EasyMock.expect(hbaseMaster.getHostNames()).andReturn(hbaseMasterHosts).anyTimes();
        EasyMock.replay(hbaseMaster);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("HBASE_MASTER")).andReturn(hbaseMaster).anyTimes();
        EasyMock.replay(cluster);

        // Run the test
        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, mappingConfiguration);
        List<String> urls = builder.create("WEBHBASE");
        validateServiceURLs(urls, HOSTNAMES, "http", "60080", null);
    }

    @Test
    public void testWebHdfsURLFromInternalMapping() throws Exception {
        testWebHdfsURL(null);
    }

    @Test
    public void testWebHdfsURLFromExternalMapping() throws Exception {
        testWebHdfsURL(TEST_MAPPING_CONFIG);
    }

    private void testWebHdfsURL(Object mappingConfiguration) throws Exception {
        final String ADDRESS = "host3:1357";
        assertEquals("http://" + ADDRESS + "/webhdfs", getTestWebHdfsURL(ADDRESS, mappingConfiguration));
    }


    private String getTestWebHdfsURL(String address, Object mappingConfiguration) throws Exception {
        AmbariCluster.ServiceConfiguration hdfsSC = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        Map<String, String> hdfsProps = new HashMap<>();
        hdfsProps.put("dfs.namenode.http-address", address);
        EasyMock.expect(hdfsSC.getProperties()).andReturn(hdfsProps).anyTimes();
        EasyMock.replay(hdfsSC);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site")).andReturn(hdfsSC).anyTimes();
        EasyMock.replay(cluster);

        // Create the URL
        List<String> urls = ServiceURLFactory.newInstance(cluster).create("WEBHDFS");
        assertNotNull(urls);
        assertFalse(urls.isEmpty());
        return urls.get(0);
    }

    @Test
    public void testWebHdfsURLHA() throws Exception {
        final String NAMESERVICES   = "myNameServicesCluster";
        final String HTTP_ADDRESS_1 = "host1:50070";
        final String HTTP_ADDRESS_2 = "host2:50077";

        final String EXPECTED_ADDR_1 = "http://" + HTTP_ADDRESS_1 + "/webhdfs";
        final String EXPECTED_ADDR_2 = "http://" + HTTP_ADDRESS_2 + "/webhdfs";

        AmbariComponent namenode = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(namenode.getConfigProperty("dfs.nameservices")).andReturn(NAMESERVICES).anyTimes();
        EasyMock.replay(namenode);

        AmbariCluster.ServiceConfiguration hdfsSC = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        Map<String, String> hdfsProps = new HashMap<>();
        hdfsProps.put("dfs.namenode.http-address." + NAMESERVICES + ".nn1", HTTP_ADDRESS_1);
        hdfsProps.put("dfs.namenode.http-address." + NAMESERVICES + ".nn2", HTTP_ADDRESS_2);
        EasyMock.expect(hdfsSC.getProperties()).andReturn(hdfsProps).anyTimes();
        EasyMock.replay(hdfsSC);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("NAMENODE")).andReturn(namenode).anyTimes();
        EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site")).andReturn(hdfsSC).anyTimes();
        EasyMock.replay(cluster);

        // Create the URL
        List<String> webhdfsURLs = ServiceURLFactory.newInstance(cluster).create("WEBHDFS");
        assertEquals(2, webhdfsURLs.size());
        assertTrue(webhdfsURLs.contains(EXPECTED_ADDR_1));
        assertTrue(webhdfsURLs.contains(EXPECTED_ADDR_2));
    }


    @Test
    public void testAtlasApiURL() throws Exception {
        final String ATLAS_REST_ADDRESS = "http://host2:21000";

        AmbariComponent atlasServer = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(atlasServer.getConfigProperty("atlas.rest.address")).andReturn(ATLAS_REST_ADDRESS).anyTimes();
        EasyMock.replay(atlasServer);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("ATLAS_SERVER")).andReturn(atlasServer).anyTimes();
        EasyMock.replay(cluster);

        // Run the test
        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, null);
        List<String> urls = builder.create("ATLAS-API");
        assertEquals(1, urls.size());
        assertEquals(ATLAS_REST_ADDRESS, urls.get(0));
    }


    @Test
    public void testAtlasURL() throws Exception {
        final String HTTP_PORT = "8787";
        final String HTTPS_PORT = "8989";

        final String[] HOSTNAMES = {"host1", "host4"};
        final List<String> atlastServerHosts = Arrays.asList(HOSTNAMES);

        AmbariComponent atlasServer = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(atlasServer.getHostNames()).andReturn(atlastServerHosts).anyTimes();
        EasyMock.expect(atlasServer.getConfigProperty("atlas.enableTLS")).andReturn("false").anyTimes();
        EasyMock.expect(atlasServer.getConfigProperty("atlas.server.http.port")).andReturn(HTTP_PORT).anyTimes();
        EasyMock.expect(atlasServer.getConfigProperty("atlas.server.https.port")).andReturn(HTTPS_PORT).anyTimes();
        EasyMock.replay(atlasServer);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("ATLAS_SERVER")).andReturn(atlasServer).anyTimes();
        EasyMock.replay(cluster);

        // Run the test
        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, null);
        List<String> urls = builder.create("ATLAS");
        validateServiceURLs(urls, HOSTNAMES, "http", HTTP_PORT, null);

        EasyMock.reset(atlasServer);
        EasyMock.expect(atlasServer.getHostNames()).andReturn(atlastServerHosts).anyTimes();
        EasyMock.expect(atlasServer.getConfigProperty("atlas.enableTLS")).andReturn("true").anyTimes();
        EasyMock.expect(atlasServer.getConfigProperty("atlas.server.http.port")).andReturn(HTTP_PORT).anyTimes();
        EasyMock.expect(atlasServer.getConfigProperty("atlas.server.https.port")).andReturn(HTTPS_PORT).anyTimes();
        EasyMock.replay(atlasServer);

        // Run the test
        urls = builder.create("ATLAS");
        validateServiceURLs(urls, HOSTNAMES, "https", HTTPS_PORT, null);
    }


    @Test
    public void testZeppelinURL() throws Exception {
        final String HTTP_PORT = "8787";
        final String HTTPS_PORT = "8989";

        final String[] HOSTNAMES = {"host1", "host4"};
        final List<String> atlastServerHosts = Arrays.asList(HOSTNAMES);

        AmbariComponent zeppelinMaster = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(zeppelinMaster.getHostNames()).andReturn(atlastServerHosts).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.ssl")).andReturn("false").anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.port")).andReturn(HTTP_PORT).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.ssl.port")).andReturn(HTTPS_PORT).anyTimes();
        EasyMock.replay(zeppelinMaster);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("ZEPPELIN_MASTER")).andReturn(zeppelinMaster).anyTimes();
        EasyMock.replay(cluster);

        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, null);

        // Run the test
        validateServiceURLs(builder.create("ZEPPELIN"), HOSTNAMES, "http", HTTP_PORT, null);

        EasyMock.reset(zeppelinMaster);
        EasyMock.expect(zeppelinMaster.getHostNames()).andReturn(atlastServerHosts).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.ssl")).andReturn("true").anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.port")).andReturn(HTTP_PORT).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.ssl.port")).andReturn(HTTPS_PORT).anyTimes();
        EasyMock.replay(zeppelinMaster);

        // Run the test
        validateServiceURLs(builder.create("ZEPPELIN"), HOSTNAMES, "https", HTTPS_PORT, null);
    }


    @Test
    public void testZeppelinUiURL() throws Exception {
        final String HTTP_PORT = "8787";
        final String HTTPS_PORT = "8989";

        final String[] HOSTNAMES = {"host1", "host4"};
        final List<String> atlastServerHosts = Arrays.asList(HOSTNAMES);

        AmbariComponent zeppelinMaster = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(zeppelinMaster.getHostNames()).andReturn(atlastServerHosts).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.ssl")).andReturn("false").anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.port")).andReturn(HTTP_PORT).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.ssl.port")).andReturn(HTTPS_PORT).anyTimes();
        EasyMock.replay(zeppelinMaster);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("ZEPPELIN_MASTER")).andReturn(zeppelinMaster).anyTimes();
        EasyMock.replay(cluster);

        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, null);

        // Run the test
        validateServiceURLs(builder.create("ZEPPELINUI"), HOSTNAMES, "http", HTTP_PORT, null);

        EasyMock.reset(zeppelinMaster);
        EasyMock.expect(zeppelinMaster.getHostNames()).andReturn(atlastServerHosts).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.ssl")).andReturn("true").anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.port")).andReturn(HTTP_PORT).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.ssl.port")).andReturn(HTTPS_PORT).anyTimes();
        EasyMock.replay(zeppelinMaster);

        // Run the test
        validateServiceURLs(builder.create("ZEPPELINUI"), HOSTNAMES, "https", HTTPS_PORT, null);
    }


    @Test
    public void testZeppelinWsURL() throws Exception {
        final String HTTP_PORT = "8787";
        final String HTTPS_PORT = "8989";

        final String[] HOSTNAMES = {"host1", "host4"};
        final List<String> atlastServerHosts = Arrays.asList(HOSTNAMES);

        AmbariComponent zeppelinMaster = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(zeppelinMaster.getHostNames()).andReturn(atlastServerHosts).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.ssl")).andReturn("false").anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.port")).andReturn(HTTP_PORT).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.ssl.port")).andReturn(HTTPS_PORT).anyTimes();
        EasyMock.replay(zeppelinMaster);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("ZEPPELIN_MASTER")).andReturn(zeppelinMaster).anyTimes();
        EasyMock.replay(cluster);

        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, null);

        // Run the test
        validateServiceURLs(builder.create("ZEPPELINWS"), HOSTNAMES, "ws", HTTP_PORT, null);

        EasyMock.reset(zeppelinMaster);
        EasyMock.expect(zeppelinMaster.getHostNames()).andReturn(atlastServerHosts).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.ssl")).andReturn("true").anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.port")).andReturn(HTTP_PORT).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.ssl.port")).andReturn(HTTPS_PORT).anyTimes();
        EasyMock.replay(zeppelinMaster);

        // Run the test
        validateServiceURLs(builder.create("ZEPPELINWS"), HOSTNAMES, "wss", HTTPS_PORT, null);
    }


    @Test
    public void testDruidCoordinatorURL() throws Exception {
        final String PORT = "8787";

        final String[] HOSTNAMES = {"host3", "host2"};
        final List<String> druidCoordinatorHosts = Arrays.asList(HOSTNAMES);

        AmbariComponent druidCoordinator = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(druidCoordinator.getHostNames()).andReturn(druidCoordinatorHosts).anyTimes();
        EasyMock.expect(druidCoordinator.getConfigProperty("druid.port")).andReturn(PORT).anyTimes();
        EasyMock.replay(druidCoordinator);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("DRUID_COORDINATOR")).andReturn(druidCoordinator).anyTimes();
        EasyMock.replay(cluster);

        // Run the test
        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, null);
        List<String> urls = builder.create("DRUID-COORDINATOR");
        validateServiceURLs(urls, HOSTNAMES, "http", PORT, null);
    }


    @Test
    public void testDruidBrokerURL() throws Exception {
        final String PORT = "8181";

        final String[] HOSTNAMES = {"host4", "host3"};
        final List<String> druidHosts = Arrays.asList(HOSTNAMES);

        AmbariComponent druidBroker = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(druidBroker.getHostNames()).andReturn(druidHosts).anyTimes();
        EasyMock.expect(druidBroker.getConfigProperty("druid.port")).andReturn(PORT).anyTimes();
        EasyMock.replay(druidBroker);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("DRUID_BROKER")).andReturn(druidBroker).anyTimes();
        EasyMock.replay(cluster);

        // Run the test
        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, null);
        List<String> urls = builder.create("DRUID-BROKER");
        validateServiceURLs(urls, HOSTNAMES, "http", PORT, null);
    }


    @Test
    public void testDruidRouterURL() throws Exception {
        final String PORT = "8282";

        final String[] HOSTNAMES = {"host5", "host7"};
        final List<String> druidHosts = Arrays.asList(HOSTNAMES);

        AmbariComponent druidRouter = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(druidRouter.getHostNames()).andReturn(druidHosts).anyTimes();
        EasyMock.expect(druidRouter.getConfigProperty("druid.port")).andReturn(PORT).anyTimes();
        EasyMock.replay(druidRouter);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("DRUID_ROUTER")).andReturn(druidRouter).anyTimes();
        EasyMock.replay(cluster);

        // Run the test
        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, null);
        List<String> urls = builder.create("DRUID-ROUTER");
        validateServiceURLs(urls, HOSTNAMES, "http", PORT, null);
    }


    @Test
    public void testDruidOverlordURL() throws Exception {
        final String PORT = "8383";

        final String[] HOSTNAMES = {"host4", "host1"};
        final List<String> druidHosts = Arrays.asList(HOSTNAMES);

        AmbariComponent druidOverlord = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(druidOverlord.getHostNames()).andReturn(druidHosts).anyTimes();
        EasyMock.expect(druidOverlord.getConfigProperty("druid.port")).andReturn(PORT).anyTimes();
        EasyMock.replay(druidOverlord);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("DRUID_OVERLORD")).andReturn(druidOverlord).anyTimes();
        EasyMock.replay(cluster);

        // Run the test
        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, null);
        List<String> urls = builder.create("DRUID-OVERLORD");
        validateServiceURLs(urls, HOSTNAMES, "http", PORT, null);
    }


    @Test
    public void testDruidSupersetURL() throws Exception {
        final String PORT = "8484";

        final String[] HOSTNAMES = {"host4", "host1"};
        final List<String> druidHosts = Arrays.asList(HOSTNAMES);

        AmbariComponent druidSuperset = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(druidSuperset.getHostNames()).andReturn(druidHosts).anyTimes();
        EasyMock.expect(druidSuperset.getConfigProperty("SUPERSET_WEBSERVER_PORT")).andReturn(PORT).anyTimes();
        EasyMock.replay(druidSuperset);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("DRUID_SUPERSET")).andReturn(druidSuperset).anyTimes();
        EasyMock.replay(cluster);

        // Run the test
        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, null);
        List<String> urls = builder.create("SUPERSET");
        validateServiceURLs(urls, HOSTNAMES, "http", PORT, null);
    }


    @Test
    public void testMissingServiceComponentURL() throws Exception {
        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("DRUID_BROKER")).andReturn(null).anyTimes();
        EasyMock.expect(cluster.getComponent("HIVE_SERVER")).andReturn(null).anyTimes();
        EasyMock.replay(cluster);

        // Run the test
        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, null);
        List<String> urls = builder.create("DRUID-BROKER");
        assertNotNull(urls);
        assertEquals(1, urls.size());
        assertEquals("http://{HOST}:{PORT}", urls.get(0));

        urls = builder.create("HIVE");
        assertNotNull(urls);
        assertEquals(1, urls.size());
        assertEquals("http://{HOST}:{PORT}/{PATH}", urls.get(0));
    }


    /**
     * Convenience method for creating AmbariDynamicServiceURLCreator instances from different mapping configuration
     * input sources.
     *
     * @param cluster       The Ambari ServiceDiscovery Cluster model
     * @param mappingConfig The mapping configuration, or null if the internal config should be used.
     *
     * @return An AmbariDynamicServiceURLCreator instance, capable of creating service URLs based on the specified
     *         cluster's configuration details.
     */
    private static AmbariDynamicServiceURLCreator newURLCreator(AmbariCluster cluster, Object mappingConfig) throws Exception {
        AmbariDynamicServiceURLCreator result = null;

        if (mappingConfig == null) {
            result = new AmbariDynamicServiceURLCreator(cluster);
        } else {
            if (mappingConfig instanceof String) {
                result = new AmbariDynamicServiceURLCreator(cluster, (String) mappingConfig);
            } else if (mappingConfig instanceof File) {
                result = new AmbariDynamicServiceURLCreator(cluster, (File) mappingConfig);
            }
        }

        return result;
    }


    /**
     * Validate the specifed HIVE URLs.
     *
     * @param urlsToValidate The URLs to validate
     * @param hostNames      The host names expected in the test URLs
     * @param scheme         The expected scheme for the URLs
     * @param port           The expected port for the URLs
     * @param path           The expected path for the URLs
     */
    private static void validateServiceURLs(List<String> urlsToValidate,
                                            String[]     hostNames,
                                            String       scheme,
                                            String       port,
                                            String       path) throws MalformedURLException {

        List<String> hostNamesToTest = new LinkedList<>(Arrays.asList(hostNames));
        for (String url : urlsToValidate) {
            URI test = null;
            try {
                // Make sure it's a valid URL
                test = new URI(url);
            } catch (URISyntaxException e) {
                fail(e.getMessage());
            }

            // Validate the scheme
            assertEquals(scheme, test.getScheme());

            // Validate the port
            assertEquals(port, String.valueOf(test.getPort()));

            // If the expected path is not specified, don't validate it
            if (path != null) {
                assertEquals("/" + path, test.getPath());
            }

            // Validate the host name
            assertTrue(hostNamesToTest.contains(test.getHost()));
            hostNamesToTest.remove(test.getHost());
        }
        assertTrue(hostNamesToTest.isEmpty());
    }


    private static final String TEST_MAPPING_CONFIG =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<service-discovery-url-mappings>\n" +
            "  <service name=\"NAMENODE\">\n" +
            "    <url-pattern>hdfs://{DFS_NAMENODE_ADDRESS}</url-pattern>\n" +
            "    <properties>\n" +
            "      <property name=\"DFS_NAMENODE_RPC_ADDRESS\">\n" +
            "        <component>NAMENODE</component>\n" +
            "        <config-property>dfs.namenode.rpc-address</config-property>\n" +
            "      </property>\n" +
            "      <property name=\"DFS_NAMESERVICES\">\n" +
            "        <component>NAMENODE</component>\n" +
            "        <config-property>dfs.nameservices</config-property>\n" +
            "      </property>\n" +
            "      <property name=\"DFS_NAMENODE_ADDRESS\">\n" +
            "        <config-property>\n" +
            "          <if property=\"DFS_NAMESERVICES\">\n" +
            "            <then>DFS_NAMESERVICES</then>\n" +
            "            <else>DFS_NAMENODE_RPC_ADDRESS</else>\n" +
            "          </if>\n" +
            "        </config-property>\n" +
            "      </property>\n" +
            "    </properties>\n" +
            "  </service>\n" +
            "\n" +
            "  <service name=\"JOBTRACKER\">\n" +
            "    <url-pattern>rpc://{YARN_RM_ADDRESS}</url-pattern>\n" +
            "    <properties>\n" +
            "      <property name=\"YARN_RM_ADDRESS\">\n" +
            "        <component>RESOURCEMANAGER</component>\n" +
            "        <config-property>yarn.resourcemanager.address</config-property>\n" +
            "      </property>\n" +
            "    </properties>\n" +
            "  </service>\n" +
            "\n" +
            "  <service name=\"WEBHCAT\">\n" +
            "    <url-pattern>http://{HOST}:{PORT}/templeton</url-pattern>\n" +
            "    <properties>\n" +
            "      <property name=\"HOST\">\n" +
            "        <component>WEBHCAT_SERVER</component>\n" +
            "        <hostname/>\n" +
            "      </property>\n" +
            "      <property name=\"PORT\">\n" +
            "        <component>WEBHCAT_SERVER</component>\n" +
            "        <config-property>templeton.port</config-property>\n" +
            "      </property>\n" +
            "    </properties>\n" +
            "  </service>\n" +
            "\n" +
            "  <service name=\"OOZIE\">\n" +
            "    <url-pattern>{OOZIE_ADDRESS}</url-pattern>\n" +
            "    <properties>\n" +
            "      <property name=\"OOZIE_ADDRESS\">\n" +
            "        <component>OOZIE_SERVER</component>\n" +
            "        <config-property>oozie.base.url</config-property>\n" +
            "      </property>\n" +
            "    </properties>\n" +
            "  </service>\n" +
            "\n" +
            "  <service name=\"WEBHBASE\">\n" +
            "    <url-pattern>http://{HOST}:60080</url-pattern>\n" +
            "    <properties>\n" +
            "      <property name=\"HOST\">\n" +
            "        <component>HBASE_MASTER</component>\n" +
            "        <hostname/>\n" +
            "      </property>\n" +
            "    </properties>\n" +
            "  </service>\n" +
            "  <service name=\"RESOURCEMANAGER\">\n" +
            "    <url-pattern>{SCHEME}://{WEBAPP_ADDRESS}/ws</url-pattern>\n" +
            "    <properties>\n" +
            "      <property name=\"WEBAPP_HTTP_ADDRESS\">\n" +
            "        <component>RESOURCEMANAGER</component>\n" +
            "        <config-property>yarn.resourcemanager.webapp.address</config-property>\n" +
            "      </property>\n" +
            "      <property name=\"WEBAPP_HTTPS_ADDRESS\">\n" +
            "        <component>RESOURCEMANAGER</component>\n" +
            "        <config-property>yarn.resourcemanager.webapp.https.address</config-property>\n" +
            "      </property>\n" +
            "      <property name=\"HTTP_POLICY\">\n" +
            "        <component>RESOURCEMANAGER</component>\n" +
            "        <config-property>yarn.http.policy</config-property>\n" +
            "      </property>\n" +
            "      <property name=\"SCHEME\">\n" +
            "        <config-property>\n" +
            "          <if property=\"HTTP_POLICY\" value=\"HTTPS_ONLY\">\n" +
            "            <then>https</then>\n" +
            "            <else>http</else>\n" +
            "          </if>\n" +
            "        </config-property>\n" +
            "      </property>\n" +
            "      <property name=\"WEBAPP_ADDRESS\">\n" +
            "        <component>RESOURCEMANAGER</component>\n" +
            "        <config-property>\n" +
            "          <if property=\"HTTP_POLICY\" value=\"HTTPS_ONLY\">\n" +
            "            <then>WEBAPP_HTTPS_ADDRESS</then>\n" +
            "            <else>WEBAPP_HTTP_ADDRESS</else>\n" +
            "          </if>\n" +
            "        </config-property>\n" +
            "      </property>\n" +
            "    </properties>\n" +
            "  </service>\n" +
            "  <service name=\"HIVE\">\n" +
            "    <url-pattern>{SCHEME}://{HOST}:{PORT}/{PATH}</url-pattern>\n" +
            "    <properties>\n" +
            "      <property name=\"HOST\">\n" +
            "        <component>HIVE_SERVER</component>\n" +
            "        <hostname/>\n" +
            "      </property>\n" +
            "      <property name=\"USE_SSL\">\n" +
            "        <component>HIVE_SERVER</component>\n" +
            "        <config-property>hive.server2.use.SSL</config-property>\n" +
            "      </property>\n" +
            "      <property name=\"PATH\">\n" +
            "        <component>HIVE_SERVER</component>\n" +
            "        <config-property>hive.server2.thrift.http.path</config-property>\n" +
            "      </property>\n" +
            "      <property name=\"PORT\">\n" +
            "        <component>HIVE_SERVER</component>\n" +
            "        <config-property>hive.server2.thrift.http.port</config-property>\n" +
            "      </property>\n" +
            "      <property name=\"SCHEME\">\n" +
            "        <config-property>\n" +
            "            <if property=\"USE_SSL\" value=\"true\">\n" +
            "                <then>https</then>\n" +
            "                <else>http</else>\n" +
            "            </if>\n" +
            "        </config-property>\n" +
            "      </property>\n" +
            "    </properties>\n" +
            "  </service>\n" +
            "</service-discovery-url-mappings>\n";


    private static final String OVERRIDE_MAPPING_FILE_CONTENTS =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<service-discovery-url-mappings>\n" +
            "  <service name=\"WEBHDFS\">\n" +
            "    <url-pattern>http://{WEBHDFS_ADDRESS}/webhdfs/OVERRIDE</url-pattern>\n" +
            "    <properties>\n" +
            "      <property name=\"WEBHDFS_ADDRESS\">\n" +
            "        <service-config name=\"HDFS\">hdfs-site</service-config>\n" +
            "        <config-property>dfs.namenode.http-address</config-property>\n" +
            "      </property>\n" +
            "    </properties>\n" +
            "  </service>\n" +
            "</service-discovery-url-mappings>\n";

}
