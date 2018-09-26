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
import static org.junit.Assert.assertNull;


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
        List<String> urls = builder.create(SERVICE_NAME, null);
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
        urls = builder.create(SERVICE_NAME, null);
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
        urls = builder.create(SERVICE_NAME, null);
        assertEquals(HOSTNAMES.length, urls.size());
        validateServiceURLs(urls, HOSTNAMES, expectedScheme, HTTP_PORT, HTTP_PATH);
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
        String url = builder.create("JOBTRACKER", null).get(0);
        assertEquals("rpc://" + ADDRESS, url);
    }

    @Test
    public void testNameNodeURL() throws Exception {
        final String ADDRESS = "host1:1234";
        String url = getTestNameNodeURL(ADDRESS);
        assertEquals("hdfs://" + ADDRESS, url);
    }

    @Test
    public void testNameNodeURLHADefaultNameService() throws Exception {
        final String DEFAULT_NAMESERVICE = "ns1";
        String url = getTestNameNodeURL(DEFAULT_NAMESERVICE, "ns1");
        assertEquals("hdfs://" + DEFAULT_NAMESERVICE, url);
    }

    @Test
    public void testNameNodeURLHADeclared() throws Exception {
        final String delcaredNS = "ns1";
        Map<String, String> params = new HashMap<>();
        params.put("discovery-nameservice", delcaredNS);

        String url = getTestNameNodeURL("nn1", "ns1", params);
        assertEquals("hdfs://" + delcaredNS, url);
    }

    @Test
    public void testNameNodeURLHADeclaredMultipleNameservices() throws Exception {
        final String delcaredNS = "ns2";
        Map<String, String> params = new HashMap<>();
        params.put("discovery-nameservice", delcaredNS);

        String url = getTestNameNodeURL("nn1", "ns1,ns2", params);
        assertEquals("hdfs://" + delcaredNS, url);
    }

    @Test
    public void testNameNodeURLHADeclaredInvalid() throws Exception {
        final String delcaredNS = "MyInvalidNameService";
        Map<String, String> params = new HashMap<>();
        params.put("discovery-nameservice", delcaredNS);

        String url = getTestNameNodeURL("nn1", "ns1,ns2", params);
        assertNull("Invalid nameservice declaration should still yield a URL.", url);
    }

    private String getTestNameNodeURL(String address) throws Exception {
        return getTestNameNodeURL(address, null);
    }

    private String getTestNameNodeURL(String defaultFSAddress, String nameservices) throws Exception {
        return getTestNameNodeURL(defaultFSAddress, nameservices, Collections.emptyMap());
    }

    private String getTestNameNodeURL(String defaultFSAddress, String nameservices, Map<String, String> serviceParams) throws Exception {
        AmbariCluster.ServiceConfiguration coreSC = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        Map<String, String> coreProps = new HashMap<>();
        coreProps.put("fs.defaultFS", "hdfs://" + defaultFSAddress);
        EasyMock.expect(coreSC.getProperties()).andReturn(coreProps).anyTimes();
        EasyMock.replay(coreSC);

        AmbariCluster.ServiceConfiguration hdfsSC = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        Map<String, String> hdfsProps = new HashMap<>();
        if (nameservices != null) {
            hdfsProps.put("dfs.nameservices", nameservices);
        }
        EasyMock.expect(hdfsSC.getProperties()).andReturn(hdfsProps).anyTimes();
        EasyMock.replay(hdfsSC);
        AmbariComponent nnComp = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(nnComp.getConfigProperty("dfs.nameservices")).andReturn(nameservices).anyTimes();
        EasyMock.replay(nnComp);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getServiceConfiguration("HDFS", "core-site")).andReturn(coreSC).anyTimes();
        EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site")).andReturn(hdfsSC).anyTimes();
        EasyMock.expect(cluster.getComponent("NAMENODE")).andReturn(nnComp).anyTimes();
        EasyMock.replay(cluster);

        // Create the URL
        List<String> urls = ServiceURLFactory.newInstance(cluster).create("NAMENODE", serviceParams);
        assertNotNull(urls);
        String url = null;
        if (!urls.isEmpty()) {
            url = urls.get(0);
        }
        return url;
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
        String url = builder.create("WEBHCAT", null).get(0);
        assertEquals("http://" + HOSTNAME + ":" + PORT + "/templeton", url);
    }


    @Test
    public void testOozieURLFromInternalMapping() throws Exception {
        testOozieURL(null, "OOZIE");
    }

    @Test
    public void testOozieURLFromExternalMapping() throws Exception {
        testOozieURL(TEST_MAPPING_CONFIG, "OOZIE");
    }

    @Test
    public void testOozieURLFromInternalMappingWithExternalOverrides() throws Exception {
        File tmpFile = File.createTempFile("knox-discovery-external-url-mapping", ".xml");
        System.setProperty(AmbariDynamicServiceURLCreator.MAPPING_CONFIG_OVERRIDE_PROPERTY, tmpFile.getAbsolutePath());
        try {
            FileUtils.writeStringToFile(tmpFile, OOZIE_OVERRIDE_MAPPING_FILE_CONTENTS, java.nio.charset.Charset.forName("utf-8"));
            testOozieURL(null, "OOZIE", "http://host3:2222/OVERRIDE");
        } finally {
            System.clearProperty(AmbariDynamicServiceURLCreator.MAPPING_CONFIG_OVERRIDE_PROPERTY);
            FileUtils.deleteQuietly(tmpFile);
        }
    }

    @Test
    public void testOozieUIURLFromInternalMapping() throws Exception {
        testOozieURL(null, "OOZIEUI");
    }

    private void testOozieURL(Object mappingConfiguration, String serviceName) throws Exception {
        testOozieURL(mappingConfiguration, serviceName, null);
    }

    private void testOozieURL(Object mappingConfiguration, String serviceName, String altExpectation) throws Exception {
        final String URL = "http://host3:2222";

        AmbariComponent oozieServer = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(oozieServer.getConfigProperty("oozie.base.url")).andReturn(URL).anyTimes();
        EasyMock.replay(oozieServer);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("OOZIE_SERVER")).andReturn(oozieServer).anyTimes();
        EasyMock.replay(cluster);

        // Run the test
        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, mappingConfiguration);
        List<String> urls = builder.create(serviceName, null);
        assertNotNull(urls);
        assertFalse(urls.isEmpty());
        String url = urls.get(0);
        assertEquals((altExpectation != null ? altExpectation : URL), url);
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
        final String HBASE_MASTER_PORT_PROPERTY = "hbase.master.info.port";
        final String HBASE_REST_PORT_PROPERTY = "hbase.rest.port";

        AmbariComponent hbaseMaster = EasyMock.createNiceMock(AmbariComponent.class);
        Map<String, String> hbaseMasterConfig = new HashMap<>();
        hbaseMasterConfig.put(HBASE_MASTER_PORT_PROPERTY, "60088");
        hbaseMasterConfig.put(HBASE_REST_PORT_PROPERTY, "60080");
        EasyMock.expect(hbaseMaster.getConfigProperties()).andReturn(hbaseMasterConfig).anyTimes();
        EasyMock.expect(hbaseMaster.getConfigProperty(HBASE_MASTER_PORT_PROPERTY))
                .andReturn(hbaseMasterConfig.get(HBASE_MASTER_PORT_PROPERTY)).anyTimes();
        EasyMock.expect(hbaseMaster.getConfigProperty(HBASE_REST_PORT_PROPERTY))
                .andReturn(hbaseMasterConfig.get(HBASE_REST_PORT_PROPERTY)).anyTimes();
        List<String> hbaseMasterHosts = Arrays.asList(HOSTNAMES);
        EasyMock.expect(hbaseMaster.getHostNames()).andReturn(hbaseMasterHosts).anyTimes();
        EasyMock.replay(hbaseMaster);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("HBASE_MASTER")).andReturn(hbaseMaster).anyTimes();
        EasyMock.replay(cluster);

        // Run the test
        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, mappingConfiguration);
        List<String> urls = builder.create("WEBHBASE", null);
        validateServiceURLs(urls, HOSTNAMES, "http", hbaseMasterConfig.get(HBASE_REST_PORT_PROPERTY), null);
    }


    @Test
    public void testWebHdfsURLHttp() throws Exception {
        final String ADDRESS = "host3:1357";
        assertEquals(("http://" + ADDRESS + "/webhdfs"), getTestHdfsURL("WEBHDFS", ADDRESS, false));
    }


    @Test
    public void testWebHdfsURLHttps() throws Exception {
        final String ADDRESS = "host3:1357";
        assertEquals(("https://" + ADDRESS + "/webhdfs"), getTestHdfsURL("WEBHDFS", ADDRESS, true));
    }


    @Test
    public void testHdfsUIURLHttp() throws Exception {
        final String ADDRESS = "host3:1357";
        assertEquals(("http://" + ADDRESS), getTestHdfsURL("HDFSUI", ADDRESS, false));
    }


    @Test
    public void testHdfsUIURLHttps() throws Exception {
        final String ADDRESS = "host3:1357";
        assertEquals(("https://" + ADDRESS), getTestHdfsURL("HDFSUI", ADDRESS, true));
    }


    private String getTestHdfsURL(String serviceName, String address, boolean isHttps) throws Exception {
        AmbariCluster.ServiceConfiguration hdfsSC = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        Map<String, String> hdfsProps = new HashMap<>();
        hdfsProps.put("dfs.namenode.http-address", address);
        hdfsProps.put("dfs.namenode.https-address", address);
        hdfsProps.put("dfs.http.policy", (isHttps) ? "HTTPS_ONLY" : "HTTP_ONLY");
        EasyMock.expect(hdfsSC.getProperties()).andReturn(hdfsProps).anyTimes();
        EasyMock.replay(hdfsSC);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site")).andReturn(hdfsSC).anyTimes();
        EasyMock.replay(cluster);

        // Create the URL
        List<String> urls = ServiceURLFactory.newInstance(cluster).create(serviceName, null);
        assertNotNull(urls);
        assertFalse(urls.isEmpty());
        return urls.get(0);
    }


    @Test
    public void testWebHdfsURLHASingleNameService() throws Exception {
        final String NAMESERVICES   = "myNameServicesCluster";
        final String HTTP_ADDRESS_1 = "host1:50070";
        final String HTTP_ADDRESS_2 = "host2:50077";

        final String EXPECTED_ADDR_1 = "http://" + HTTP_ADDRESS_1 + "/webhdfs";
        final String EXPECTED_ADDR_2 = "http://" + HTTP_ADDRESS_2 + "/webhdfs";

        AmbariCluster.ServiceConfiguration hdfsSC = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        Map<String, String> hdfsProps = new HashMap<>();
        hdfsProps.put("dfs.nameservices", NAMESERVICES);
        hdfsProps.put("dfs.namenode.http-address." + NAMESERVICES + ".nn1", HTTP_ADDRESS_1);
        hdfsProps.put("dfs.namenode.http-address." + NAMESERVICES + ".nn2", HTTP_ADDRESS_2);
        EasyMock.expect(hdfsSC.getProperties()).andReturn(hdfsProps).anyTimes();
        EasyMock.replay(hdfsSC);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site")).andReturn(hdfsSC).anyTimes();
        EasyMock.replay(cluster);

        // Create the URL
        List<String> webhdfsURLs = ServiceURLFactory.newInstance(cluster).create("WEBHDFS", null);
        assertEquals(2, webhdfsURLs.size());
        assertTrue(webhdfsURLs.contains(EXPECTED_ADDR_1));
        assertTrue(webhdfsURLs.contains(EXPECTED_ADDR_2));
    }


    @Test
    public void testHdfsUIURLHASingleNameService() throws Exception {
        final String NAMESERVICES   = "myNameServicesCluster";
        final String HTTP_ADDRESS_1 = "host1:50070";
        final String HTTP_ADDRESS_2 = "host2:50077";

        final String EXPECTED_ADDR_1 = "http://" + HTTP_ADDRESS_1;
        final String EXPECTED_ADDR_2 = "http://" + HTTP_ADDRESS_2;

        AmbariCluster.ServiceConfiguration hdfsSC = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        Map<String, String> hdfsProps = new HashMap<>();
        hdfsProps.put("dfs.nameservices", NAMESERVICES);
        hdfsProps.put("dfs.namenode.http-address." + NAMESERVICES + ".nn1", HTTP_ADDRESS_1);
        hdfsProps.put("dfs.namenode.http-address." + NAMESERVICES + ".nn2", HTTP_ADDRESS_2);
        EasyMock.expect(hdfsSC.getProperties()).andReturn(hdfsProps).anyTimes();
        EasyMock.replay(hdfsSC);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site")).andReturn(hdfsSC).anyTimes();
        EasyMock.replay(cluster);

        // Create the URL
        List<String> webhdfsURLs = ServiceURLFactory.newInstance(cluster).create("HDFSUI", null);
        assertEquals(2, webhdfsURLs.size());
        assertTrue(webhdfsURLs.contains(EXPECTED_ADDR_1));
        assertTrue(webhdfsURLs.contains(EXPECTED_ADDR_2));
    }


    /**
     * Test federated NameNode scenario, which chooses the "first" nameservice because there is no information from
     * which one can be selected from among the set.
     */
    @Test
    public void testWebHdfsURLHAMultipleNameServicesNoDefaultFS() throws Exception {
        final String NS1 = "myns1";
        final String NS2 = "myns2";
        final String NAMESERVICES   = NS1 + "," + NS2;
        final String HTTP_ADDRESS_11 = "host11:50070";
        final String HTTP_ADDRESS_12 = "host12:50077";
        final String HTTP_ADDRESS_21 = "host21:50070";
        final String HTTP_ADDRESS_22 = "host22:50077";

        final String EXPECTED_ADDR_1 = "http://" + HTTP_ADDRESS_11 + "/webhdfs";
        final String EXPECTED_ADDR_2 = "http://" + HTTP_ADDRESS_12 + "/webhdfs";

        AmbariCluster.ServiceConfiguration hdfsSC = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        Map<String, String> hdfsProps = new HashMap<>();
        hdfsProps.put("dfs.nameservices", NAMESERVICES);
        hdfsProps.put("dfs.namenode.http-address." + NS1 + ".nn1", HTTP_ADDRESS_11);
        hdfsProps.put("dfs.namenode.http-address." + NS1 + ".nn2", HTTP_ADDRESS_12);
        hdfsProps.put("dfs.namenode.http-address." + NS2 + ".nn1", HTTP_ADDRESS_21);
        hdfsProps.put("dfs.namenode.http-address." + NS2 + ".nn2", HTTP_ADDRESS_22);
        EasyMock.expect(hdfsSC.getProperties()).andReturn(hdfsProps).anyTimes();
        EasyMock.replay(hdfsSC);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site")).andReturn(hdfsSC).anyTimes();
        EasyMock.replay(cluster);

        // Create the URL
        List<String> webhdfsURLs = ServiceURLFactory.newInstance(cluster).create("WEBHDFS", null);
        assertEquals(2, webhdfsURLs.size());
        assertTrue(webhdfsURLs.contains(EXPECTED_ADDR_1));
        assertTrue(webhdfsURLs.contains(EXPECTED_ADDR_2));
    }


    /**
     * Test federated NameNode scenario, which chooses the "first" nameservice because there is no information from
     * which one can be selected from among the set.
     */
    @Test
    public void testHdfsUIURLHAMultipleNameServicesNoDefaultFS() throws Exception {
        final String NS1 = "myns1";
        final String NS2 = "myns2";
        final String NAMESERVICES   = NS1 + "," + NS2;
        final String HTTP_ADDRESS_11 = "host11:50070";
        final String HTTP_ADDRESS_12 = "host12:50077";
        final String HTTP_ADDRESS_21 = "host21:50070";
        final String HTTP_ADDRESS_22 = "host22:50077";

        final String EXPECTED_ADDR_1 = "http://" + HTTP_ADDRESS_11;
        final String EXPECTED_ADDR_2 = "http://" + HTTP_ADDRESS_12;

        AmbariCluster.ServiceConfiguration hdfsSC = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        Map<String, String> hdfsProps = new HashMap<>();
        hdfsProps.put("dfs.nameservices", NAMESERVICES);
        hdfsProps.put("dfs.namenode.http-address." + NS1 + ".nn1", HTTP_ADDRESS_11);
        hdfsProps.put("dfs.namenode.http-address." + NS1 + ".nn2", HTTP_ADDRESS_12);
        hdfsProps.put("dfs.namenode.http-address." + NS2 + ".nn1", HTTP_ADDRESS_21);
        hdfsProps.put("dfs.namenode.http-address." + NS2 + ".nn2", HTTP_ADDRESS_22);
        EasyMock.expect(hdfsSC.getProperties()).andReturn(hdfsProps).anyTimes();
        EasyMock.replay(hdfsSC);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site")).andReturn(hdfsSC).anyTimes();
        EasyMock.replay(cluster);

        // Create the URL
        List<String> webhdfsURLs = ServiceURLFactory.newInstance(cluster).create("HDFSUI", null);
        assertEquals(2, webhdfsURLs.size());
        assertTrue(webhdfsURLs.contains(EXPECTED_ADDR_1));
        assertTrue(webhdfsURLs.contains(EXPECTED_ADDR_2));
    }


    /**
     * Test federated NameNode scenario, relying on the core-site property for identifying the default nameservice.
     */
    @Test
    public void testWebHdfsURLFederatedNNWithDefaultFS() throws Exception {
        final String NS1 = "myns1";
        final String NS2 = "myns2";
        final String NAMESERVICES   = NS1 + "," + NS2;
        final String HTTP_ADDRESS_11 = "host11:50070";
        final String HTTP_ADDRESS_12 = "host12:50077";
        final String HTTP_ADDRESS_21 = "host21:50070";
        final String HTTP_ADDRESS_22 = "host22:50077";

        final String EXPECTED_ADDR_1 = "http://" + HTTP_ADDRESS_21 + "/webhdfs";
        final String EXPECTED_ADDR_2 = "http://" + HTTP_ADDRESS_22 + "/webhdfs";

        AmbariCluster.ServiceConfiguration hdfsSC = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        Map<String, String> hdfsProps = new HashMap<>();
        hdfsProps.put("dfs.nameservices", NAMESERVICES);
        hdfsProps.put("dfs.namenode.http-address." + NS1 + ".nn1", HTTP_ADDRESS_11);
        hdfsProps.put("dfs.namenode.http-address." + NS1 + ".nn2", HTTP_ADDRESS_12);
        hdfsProps.put("dfs.namenode.http-address." + NS2 + ".nn1", HTTP_ADDRESS_21);
        hdfsProps.put("dfs.namenode.http-address." + NS2 + ".nn2", HTTP_ADDRESS_22);
        EasyMock.expect(hdfsSC.getProperties()).andReturn(hdfsProps).anyTimes();
        EasyMock.replay(hdfsSC);

        AmbariCluster.ServiceConfiguration coreSC = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        Map<String, String> coreProps = new HashMap<>();
        coreProps.put("fs.defaultFS", NS2);
        EasyMock.expect(coreSC.getProperties()).andReturn(coreProps).anyTimes();
        EasyMock.replay(coreSC);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site")).andReturn(hdfsSC).anyTimes();
        EasyMock.expect(cluster.getServiceConfiguration("HDFS", "core-site")).andReturn(coreSC).anyTimes();
        EasyMock.replay(cluster);

        // Create the URL
        List<String> webhdfsURLs = ServiceURLFactory.newInstance(cluster).create("WEBHDFS", null);
        assertEquals(2, webhdfsURLs.size());
        assertTrue(webhdfsURLs.contains(EXPECTED_ADDR_1));
        assertTrue(webhdfsURLs.contains(EXPECTED_ADDR_2));
    }


    /**
     * Test federated NameNode scenario, relying on the core-site property for identifying the default nameservice.
     */
    @Test
    public void testHdfsUIURLFederatedNNWithDefaultFS() throws Exception {
        final String NS1 = "myns1";
        final String NS2 = "myns2";
        final String NAMESERVICES   = NS1 + "," + NS2;
        final String HTTP_ADDRESS_11 = "host11:50070";
        final String HTTP_ADDRESS_12 = "host12:50077";
        final String HTTP_ADDRESS_21 = "host21:50070";
        final String HTTP_ADDRESS_22 = "host22:50077";

        final String EXPECTED_ADDR_1 = "http://" + HTTP_ADDRESS_21;
        final String EXPECTED_ADDR_2 = "http://" + HTTP_ADDRESS_22;

        AmbariCluster.ServiceConfiguration hdfsSC = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        Map<String, String> hdfsProps = new HashMap<>();
        hdfsProps.put("dfs.nameservices", NAMESERVICES);
        hdfsProps.put("dfs.namenode.http-address." + NS1 + ".nn1", HTTP_ADDRESS_11);
        hdfsProps.put("dfs.namenode.http-address." + NS1 + ".nn2", HTTP_ADDRESS_12);
        hdfsProps.put("dfs.namenode.http-address." + NS2 + ".nn1", HTTP_ADDRESS_21);
        hdfsProps.put("dfs.namenode.http-address." + NS2 + ".nn2", HTTP_ADDRESS_22);
        EasyMock.expect(hdfsSC.getProperties()).andReturn(hdfsProps).anyTimes();
        EasyMock.replay(hdfsSC);

        AmbariCluster.ServiceConfiguration coreSC = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        Map<String, String> coreProps = new HashMap<>();
        coreProps.put("fs.defaultFS", NS2);
        EasyMock.expect(coreSC.getProperties()).andReturn(coreProps).anyTimes();
        EasyMock.replay(coreSC);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site")).andReturn(hdfsSC).anyTimes();
        EasyMock.expect(cluster.getServiceConfiguration("HDFS", "core-site")).andReturn(coreSC).anyTimes();
        EasyMock.replay(cluster);

        // Create the URL
        List<String> webhdfsURLs = ServiceURLFactory.newInstance(cluster).create("HDFSUI", null);
        assertEquals(2, webhdfsURLs.size());
        assertTrue(webhdfsURLs.contains(EXPECTED_ADDR_1));
        assertTrue(webhdfsURLs.contains(EXPECTED_ADDR_2));
    }


    /**
     * Recent version of HDFS config include properties for mapping NN nodes to nameservices (e.g., dfs.ha.namenode.ns1).
     * This test verifies that discovery works correctly in those cases, when no nameservice is explicitly declared in
     * a descriptor.
     */
    @Test
    public void testWebHdfsURLFederatedNNWithDefaultFSAndHaNodes() throws Exception {
        final String NS1 = "myns1";
        final String NS2 = "myns2";
        final String NAMESERVICES   = NS1 + "," + NS2;
        final String HTTP_ADDRESS_11 = "host11:50070";
        final String HTTP_ADDRESS_12 = "host12:50077";
        final String HTTP_ADDRESS_21 = "host21:50070";
        final String HTTP_ADDRESS_22 = "host22:50077";

        final String EXPECTED_ADDR_1 = "http://" + HTTP_ADDRESS_21 + "/webhdfs";
        final String EXPECTED_ADDR_2 = "http://" + HTTP_ADDRESS_22 + "/webhdfs";

        AmbariCluster.ServiceConfiguration hdfsSC = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        Map<String, String> hdfsProps = new HashMap<>();
        hdfsProps.put("dfs.nameservices", NAMESERVICES);
        hdfsProps.put("dfs.namenode.http-address." + NS1 + ".nn11", HTTP_ADDRESS_11);
        hdfsProps.put("dfs.namenode.http-address." + NS1 + ".nn12", HTTP_ADDRESS_12);
        hdfsProps.put("dfs.namenode.http-address." + NS2 + ".nn21", HTTP_ADDRESS_21);
        hdfsProps.put("dfs.namenode.http-address." + NS2 + ".nn22", HTTP_ADDRESS_22);
        hdfsProps.put("dfs.ha.namenodes." + NS1, "nn11,nn12");
        hdfsProps.put("dfs.ha.namenodes." + NS2, "nn21,nn22");
        EasyMock.expect(hdfsSC.getProperties()).andReturn(hdfsProps).anyTimes();
        EasyMock.replay(hdfsSC);

        AmbariCluster.ServiceConfiguration coreSC = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        Map<String, String> coreProps = new HashMap<>();
        coreProps.put("fs.defaultFS", NS2);
        EasyMock.expect(coreSC.getProperties()).andReturn(coreProps).anyTimes();
        EasyMock.replay(coreSC);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site")).andReturn(hdfsSC).anyTimes();
        EasyMock.expect(cluster.getServiceConfiguration("HDFS", "core-site")).andReturn(coreSC).anyTimes();
        EasyMock.replay(cluster);

        // Create the URL
        List<String> webhdfsURLs = ServiceURLFactory.newInstance(cluster).create("WEBHDFS", null);
        assertEquals(2, webhdfsURLs.size());
        assertTrue(webhdfsURLs.contains(EXPECTED_ADDR_1));
        assertTrue(webhdfsURLs.contains(EXPECTED_ADDR_2));
    }


    /**
     * Recent version of HDFS config include properties for mapping NN nodes to nameservices (e.g., dfs.ha.namenode.ns1).
     * This test verifies that discovery works correctly in those cases, when no nameservice is explicitly declared in
     * a descriptor.
     */
    @Test
    public void testHdfsUIURLFederatedNNWithDefaultFSAndHaNodes() throws Exception {
        final String NS1 = "myns1";
        final String NS2 = "myns2";
        final String NAMESERVICES   = NS1 + "," + NS2;
        final String HTTP_ADDRESS_11 = "host11:50070";
        final String HTTP_ADDRESS_12 = "host12:50077";
        final String HTTP_ADDRESS_21 = "host21:50070";
        final String HTTP_ADDRESS_22 = "host22:50077";

        final String EXPECTED_ADDR_1 = "http://" + HTTP_ADDRESS_21;
        final String EXPECTED_ADDR_2 = "http://" + HTTP_ADDRESS_22;

        AmbariCluster.ServiceConfiguration hdfsSC = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        Map<String, String> hdfsProps = new HashMap<>();
        hdfsProps.put("dfs.nameservices", NAMESERVICES);
        hdfsProps.put("dfs.namenode.http-address." + NS1 + ".nn11", HTTP_ADDRESS_11);
        hdfsProps.put("dfs.namenode.http-address." + NS1 + ".nn12", HTTP_ADDRESS_12);
        hdfsProps.put("dfs.namenode.http-address." + NS2 + ".nn21", HTTP_ADDRESS_21);
        hdfsProps.put("dfs.namenode.http-address." + NS2 + ".nn22", HTTP_ADDRESS_22);
        hdfsProps.put("dfs.ha.namenodes." + NS1, "nn11,nn12");
        hdfsProps.put("dfs.ha.namenodes." + NS2, "nn21,nn22");
        EasyMock.expect(hdfsSC.getProperties()).andReturn(hdfsProps).anyTimes();
        EasyMock.replay(hdfsSC);

        AmbariCluster.ServiceConfiguration coreSC = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        Map<String, String> coreProps = new HashMap<>();
        coreProps.put("fs.defaultFS", NS2);
        EasyMock.expect(coreSC.getProperties()).andReturn(coreProps).anyTimes();
        EasyMock.replay(coreSC);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site")).andReturn(hdfsSC).anyTimes();
        EasyMock.expect(cluster.getServiceConfiguration("HDFS", "core-site")).andReturn(coreSC).anyTimes();
        EasyMock.replay(cluster);

        // Create the URL
        List<String> webhdfsURLs = ServiceURLFactory.newInstance(cluster).create("HDFSUI", null);
        assertEquals(2, webhdfsURLs.size());
        assertTrue(webhdfsURLs.contains(EXPECTED_ADDR_1));
        assertTrue(webhdfsURLs.contains(EXPECTED_ADDR_2));
    }


    /**
     * Recent version of HDFS config include properties for mapping NN nodes to nameservices (e.g., dfs.ha.namenode.ns1).
     * This test verifies that discovery works correctly in those cases, when a nameservice is declared in descriptor.
     */
    @Test
    public void testWebHdfsURLFederatedNNDeclaredNS() throws Exception {
        final String NS1 = "myns1";
        final String NS2 = "myns2";
        final String NAMESERVICES   = NS1 + "," + NS2;
        final String HTTP_ADDRESS_11 = "host11:50070";
        final String HTTP_ADDRESS_12 = "host12:50077";
        final String HTTP_ADDRESS_21 = "host21:50070";
        final String HTTP_ADDRESS_22 = "host22:50077";

        final String EXPECTED_ADDR_1 = "http://" + HTTP_ADDRESS_11 + "/webhdfs";
        final String EXPECTED_ADDR_2 = "http://" + HTTP_ADDRESS_12 + "/webhdfs";

        AmbariComponent namenode = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(namenode.getConfigProperty("dfs.nameservices")).andReturn(NAMESERVICES).anyTimes();
        EasyMock.replay(namenode);

        AmbariCluster.ServiceConfiguration hdfsSC = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        Map<String, String> hdfsProps = new HashMap<>();
        hdfsProps.put("dfs.namenode.http-address." + NS1 + ".nn11", HTTP_ADDRESS_11);
        hdfsProps.put("dfs.namenode.http-address." + NS1 + ".nn12", HTTP_ADDRESS_12);
        hdfsProps.put("dfs.namenode.http-address." + NS2 + ".nn21", HTTP_ADDRESS_21);
        hdfsProps.put("dfs.namenode.http-address." + NS2 + ".nn22", HTTP_ADDRESS_22);
        hdfsProps.put("dfs.ha.namenodes." + NS1, "nn11,nn12");
        hdfsProps.put("dfs.ha.namenodes." + NS2, "nn21,nn22");
        hdfsProps.put("dfs.nameservices", NAMESERVICES);
        EasyMock.expect(hdfsSC.getProperties()).andReturn(hdfsProps).anyTimes();
        EasyMock.replay(hdfsSC);

        AmbariCluster.ServiceConfiguration coreSC = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        Map<String, String> coreProps = new HashMap<>();
        coreProps.put("fs.defaultFS", NS2);
        EasyMock.expect(coreSC.getProperties()).andReturn(coreProps).anyTimes();
        EasyMock.replay(coreSC);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("NAMENODE")).andReturn(namenode).anyTimes();
        EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site")).andReturn(hdfsSC).anyTimes();
        EasyMock.expect(cluster.getServiceConfiguration("HDFS", "core-site")).andReturn(coreSC).anyTimes();
        EasyMock.replay(cluster);

        // Create the URL
        Map<String, String> params = new HashMap<>();
        params.put("discovery-nameservice", NS1); // Declare the non-default nameservice
        List<String> webhdfsURLs = ServiceURLFactory.newInstance(cluster).create("WEBHDFS", params);

        assertEquals(2, webhdfsURLs.size());
        assertTrue(webhdfsURLs.contains(EXPECTED_ADDR_1));
        assertTrue(webhdfsURLs.contains(EXPECTED_ADDR_2));
    }


    /**
     * Previous version of HDFS config DO NOT include properties for mapping NN nodes to nameservices.
     * This test verifies that discovery works correctly in those cases, when a nameservice is declared in descriptor.
     */
    @Test
    public void testWebHdfsURLFederatedNNDeclaredNSWithoutHaNodes() throws Exception {
        final String NS1 = "myns1";
        final String NS2 = "myns2";
        final String NAMESERVICES   = NS1 + "," + NS2;
        final String HTTP_ADDRESS_11 = "host11:50070";
        final String HTTP_ADDRESS_12 = "host12:50077";
        final String HTTP_ADDRESS_21 = "host21:50070";
        final String HTTP_ADDRESS_22 = "host22:50077";

        final String EXPECTED_ADDR_1 = "http://" + HTTP_ADDRESS_11 + "/webhdfs";
        final String EXPECTED_ADDR_2 = "http://" + HTTP_ADDRESS_12 + "/webhdfs";

        AmbariComponent namenode = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(namenode.getConfigProperty("dfs.nameservices")).andReturn(NAMESERVICES).anyTimes();
        EasyMock.replay(namenode);

        AmbariCluster.ServiceConfiguration hdfsSC = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        Map<String, String> hdfsProps = new HashMap<>();
        hdfsProps.put("dfs.namenode.http-address." + NS1 + ".nn1", HTTP_ADDRESS_11);
        hdfsProps.put("dfs.namenode.http-address." + NS1 + ".nn2", HTTP_ADDRESS_12);
        hdfsProps.put("dfs.namenode.http-address." + NS2 + ".nn1", HTTP_ADDRESS_21);
        hdfsProps.put("dfs.namenode.http-address." + NS2 + ".nn2", HTTP_ADDRESS_22);
        hdfsProps.put("dfs.nameservices", NAMESERVICES);
        EasyMock.expect(hdfsSC.getProperties()).andReturn(hdfsProps).anyTimes();
        EasyMock.replay(hdfsSC);

        AmbariCluster.ServiceConfiguration coreSC = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        Map<String, String> coreProps = new HashMap<>();
        coreProps.put("fs.defaultFS", NS2);
        EasyMock.expect(coreSC.getProperties()).andReturn(coreProps).anyTimes();
        EasyMock.replay(coreSC);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("NAMENODE")).andReturn(namenode).anyTimes();
        EasyMock.expect(cluster.getServiceConfiguration("HDFS", "hdfs-site")).andReturn(hdfsSC).anyTimes();
        EasyMock.expect(cluster.getServiceConfiguration("HDFS", "core-site")).andReturn(coreSC).anyTimes();
        EasyMock.replay(cluster);

        // Create the URL
        Map<String, String> params = new HashMap<>();
        params.put("discovery-nameservice", NS1); // Declare the non-default nameservice
        List<String> webhdfsURLs = ServiceURLFactory.newInstance(cluster).create("WEBHDFS", params);

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
        List<String> urls = builder.create("ATLAS-API", null);
        assertEquals(1, urls.size());
        assertEquals(ATLAS_REST_ADDRESS, urls.get(0));
    }


    @Test
    public void testAtlasURL() throws Exception {
        final String HTTP_PORT = "8787";
        final String HTTPS_PORT = "8989";

        final String[] HOSTNAMES = {"host1", "host4"};
        final List<String> atlasServerHosts = Arrays.asList(HOSTNAMES);

        AmbariComponent atlasServer = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(atlasServer.getHostNames()).andReturn(atlasServerHosts).anyTimes();
        EasyMock.expect(atlasServer.getConfigProperty("atlas.enableTLS")).andReturn("false").anyTimes();
        EasyMock.expect(atlasServer.getConfigProperty("atlas.server.http.port")).andReturn(HTTP_PORT).anyTimes();
        EasyMock.expect(atlasServer.getConfigProperty("atlas.server.https.port")).andReturn(HTTPS_PORT).anyTimes();
        EasyMock.replay(atlasServer);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("ATLAS_SERVER")).andReturn(atlasServer).anyTimes();
        EasyMock.replay(cluster);

        // Run the test
        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, null);
        List<String> urls = builder.create("ATLAS", null);
        validateServiceURLs(urls, HOSTNAMES, "http", HTTP_PORT, null);

        EasyMock.reset(atlasServer);
        EasyMock.expect(atlasServer.getHostNames()).andReturn(atlasServerHosts).anyTimes();
        EasyMock.expect(atlasServer.getConfigProperty("atlas.enableTLS")).andReturn("true").anyTimes();
        EasyMock.expect(atlasServer.getConfigProperty("atlas.server.http.port")).andReturn(HTTP_PORT).anyTimes();
        EasyMock.expect(atlasServer.getConfigProperty("atlas.server.https.port")).andReturn(HTTPS_PORT).anyTimes();
        EasyMock.replay(atlasServer);

        // Run the test
        urls = builder.create("ATLAS", null);
        validateServiceURLs(urls, HOSTNAMES, "https", HTTPS_PORT, null);
    }


    @Test
    public void testRangerURL() throws Exception {
        doTestRangerURLs("RANGER");
    }


    @Test
    public void testRangerUIURL() throws Exception {
        doTestRangerURLs("RANGERUI");
    }


    private void doTestRangerURLs(String serviceName) throws Exception {
        final String HTTP_PORT = "6080";
        final String HTTPS_PORT = "6182";
        final String EXT_URL = "http://host2:" + HTTP_PORT;

        final String[] HOSTNAMES = {"host1", "host3"};
        final List<String> rangerServerHosts = Arrays.asList(HOSTNAMES);

        AmbariComponent rangerAdmin = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(rangerAdmin.getHostNames()).andReturn(rangerServerHosts).anyTimes();
        EasyMock.expect(rangerAdmin.getConfigProperty("ranger.service.https.attrib.ssl.enabled")).andReturn("false").anyTimes();
        EasyMock.expect(rangerAdmin.getConfigProperty("ranger.service.http.port")).andReturn(HTTP_PORT).anyTimes();
        EasyMock.expect(rangerAdmin.getConfigProperty("ranger.service.https.port")).andReturn(HTTPS_PORT).anyTimes();
        EasyMock.expect(rangerAdmin.getConfigProperty("ranger.externalurl")).andReturn("http://host7:9898").anyTimes();
        EasyMock.expect(rangerAdmin.getConfigProperty("policymgr_external_url")).andReturn(EXT_URL).anyTimes();
        EasyMock.replay(rangerAdmin);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("RANGER_ADMIN")).andReturn(rangerAdmin).anyTimes();
        EasyMock.replay(cluster);

        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, null);

        // Run the test
        List<String> urls = builder.create(serviceName, null);
        assertEquals(1, urls.size());
        assertEquals(EXT_URL, urls.get(0));

        EasyMock.reset(rangerAdmin);
        EasyMock.expect(rangerAdmin.getHostNames()).andReturn(rangerServerHosts).anyTimes();
        EasyMock.expect(rangerAdmin.getConfigProperty("ranger.service.https.attrib.ssl.enabled")).andReturn("true").anyTimes();
        EasyMock.expect(rangerAdmin.getConfigProperty("ranger.service.http.port")).andReturn(HTTP_PORT).anyTimes();
        EasyMock.expect(rangerAdmin.getConfigProperty("ranger.service.https.port")).andReturn(HTTPS_PORT).anyTimes();
        EasyMock.expect(rangerAdmin.getConfigProperty("ranger.externalurl")).andReturn("http://host7:9898").anyTimes();
        EasyMock.expect(rangerAdmin.getConfigProperty("policymgr_external_url")).andReturn(EXT_URL).anyTimes();
        EasyMock.replay(rangerAdmin);

        // Run the test, making sure that the external URL is the result
        urls = builder.create(serviceName, null);
        assertEquals(1, urls.size());
        assertEquals(EXT_URL, urls.get(0));
    }


    @Test
    public void testZeppelinURL() throws Exception {
        final String HTTP_PORT = "8787";
        final String HTTPS_PORT = "8989";

        final String[] HOSTNAMES = {"host1", "host4"};
        final List<String> zeppelingServerHosts = Arrays.asList(HOSTNAMES);

        AmbariComponent zeppelinMaster = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(zeppelinMaster.getHostNames()).andReturn(zeppelingServerHosts).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.ssl")).andReturn("false").anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.port")).andReturn(HTTP_PORT).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.ssl.port")).andReturn(HTTPS_PORT).anyTimes();
        EasyMock.replay(zeppelinMaster);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("ZEPPELIN_MASTER")).andReturn(zeppelinMaster).anyTimes();
        EasyMock.replay(cluster);

        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, null);

        // Run the test
        validateServiceURLs(builder.create("ZEPPELIN", null), HOSTNAMES, "http", HTTP_PORT, null);

        EasyMock.reset(zeppelinMaster);
        EasyMock.expect(zeppelinMaster.getHostNames()).andReturn(zeppelingServerHosts).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.ssl")).andReturn("true").anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.port")).andReturn(HTTP_PORT).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.ssl.port")).andReturn(HTTPS_PORT).anyTimes();
        EasyMock.replay(zeppelinMaster);

        // Run the test
        validateServiceURLs(builder.create("ZEPPELIN", null), HOSTNAMES, "https", HTTPS_PORT, null);
    }


    @Test
    public void testZeppelinUiURL() throws Exception {
        final String HTTP_PORT = "8787";
        final String HTTPS_PORT = "8989";

        final String[] HOSTNAMES = {"host1", "host4"};
        final List<String> zeppelinServerHosts = Arrays.asList(HOSTNAMES);

        AmbariComponent zeppelinMaster = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(zeppelinMaster.getHostNames()).andReturn(zeppelinServerHosts).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.ssl")).andReturn("false").anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.port")).andReturn(HTTP_PORT).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.ssl.port")).andReturn(HTTPS_PORT).anyTimes();
        EasyMock.replay(zeppelinMaster);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("ZEPPELIN_MASTER")).andReturn(zeppelinMaster).anyTimes();
        EasyMock.replay(cluster);

        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, null);

        // Run the test
        validateServiceURLs(builder.create("ZEPPELINUI", null), HOSTNAMES, "http", HTTP_PORT, null);

        EasyMock.reset(zeppelinMaster);
        EasyMock.expect(zeppelinMaster.getHostNames()).andReturn(zeppelinServerHosts).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.ssl")).andReturn("true").anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.port")).andReturn(HTTP_PORT).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.ssl.port")).andReturn(HTTPS_PORT).anyTimes();
        EasyMock.replay(zeppelinMaster);

        // Run the test
        validateServiceURLs(builder.create("ZEPPELINUI", null), HOSTNAMES, "https", HTTPS_PORT, null);
    }


    @Test
    public void testZeppelinWsURL() throws Exception {
        final String HTTP_PORT = "8787";
        final String HTTPS_PORT = "8989";

        final String[] HOSTNAMES = {"host1", "host4"};
        final List<String> zeppelinServerHosts = Arrays.asList(HOSTNAMES);

        AmbariComponent zeppelinMaster = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(zeppelinMaster.getHostNames()).andReturn(zeppelinServerHosts).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.ssl")).andReturn("false").anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.port")).andReturn(HTTP_PORT).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.ssl.port")).andReturn(HTTPS_PORT).anyTimes();
        EasyMock.replay(zeppelinMaster);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("ZEPPELIN_MASTER")).andReturn(zeppelinMaster).anyTimes();
        EasyMock.replay(cluster);

        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, null);

        // Run the test
        validateServiceURLs(builder.create("ZEPPELINWS", null), HOSTNAMES, "ws", HTTP_PORT, null);

        EasyMock.reset(zeppelinMaster);
        EasyMock.expect(zeppelinMaster.getHostNames()).andReturn(zeppelinServerHosts).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.ssl")).andReturn("true").anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.port")).andReturn(HTTP_PORT).anyTimes();
        EasyMock.expect(zeppelinMaster.getConfigProperty("zeppelin.server.ssl.port")).andReturn(HTTPS_PORT).anyTimes();
        EasyMock.replay(zeppelinMaster);

        // Run the test
        validateServiceURLs(builder.create("ZEPPELINWS", null), HOSTNAMES, "wss", HTTPS_PORT, null);
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
        List<String> urls = builder.create("DRUID-COORDINATOR", null);
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
        List<String> urls = builder.create("DRUID-BROKER", null);
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
        List<String> urls = builder.create("DRUID-ROUTER", null);
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
        List<String> urls = builder.create("DRUID-OVERLORD", null);
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
        List<String> urls = builder.create("SUPERSET", null);
        validateServiceURLs(urls, HOSTNAMES, "http", PORT, null);
    }


    @Test
    public void testFalconURL() throws Exception {
        final String PORT = "8998";

        final String[] HOSTNAMES = {"host2"};
        final List<String> druidHosts = Arrays.asList(HOSTNAMES);

        AmbariComponent falconServer = EasyMock.createNiceMock(AmbariComponent.class);
        EasyMock.expect(falconServer.getHostNames()).andReturn(druidHosts).anyTimes();
        EasyMock.expect(falconServer.getConfigProperty("falcon_port")).andReturn(PORT).anyTimes();
        EasyMock.replay(falconServer);

        AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(cluster.getComponent("FALCON_SERVER")).andReturn(falconServer).anyTimes();
        EasyMock.replay(cluster);

        // Run the test
        AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, null);
        List<String> urls = builder.create("FALCON", null);
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
        List<String> urls = builder.create("DRUID-BROKER", null);
        assertNotNull(urls);
        assertEquals(1, urls.size());
        assertEquals("http://{HOST}:{PORT}", urls.get(0));

        urls = builder.create("HIVE", null);
        assertNotNull(urls);
        assertEquals(1, urls.size());
        assertEquals("http://{HOST}:{PORT}/{PATH}", urls.get(0));
    }

    @Test
    public void testExtensionServiceURLFromOverride() throws Exception {
        File tmpFile = File.createTempFile("knox-discovery-url-mapping-extension", ".xml");
        System.setProperty(AmbariDynamicServiceURLCreator.MAPPING_CONFIG_OVERRIDE_PROPERTY, tmpFile.getAbsolutePath());
        try {
            FileUtils.writeStringToFile(tmpFile, CUSTOM_AUGMENT_MAPPING_FILE_CONTENTS, java.nio.charset.Charset.forName("utf-8"));

            final String[] HOSTNAMES = {"host2", "host4"};

            // The extension service URL mapping leverages the HBase master config properties for convenience
            final String HBASE_MASTER_PORT_PROPERTY = "hbase.master.info.port";

            AmbariComponent hbaseMaster = EasyMock.createNiceMock(AmbariComponent.class);
            Map<String, String> hbaseMasterConfig = new HashMap<>();
            hbaseMasterConfig.put(HBASE_MASTER_PORT_PROPERTY, "60080");
            EasyMock.expect(hbaseMaster.getConfigProperties()).andReturn(hbaseMasterConfig).anyTimes();
            EasyMock.expect(hbaseMaster.getConfigProperty(HBASE_MASTER_PORT_PROPERTY))
                .andReturn(hbaseMasterConfig.get(HBASE_MASTER_PORT_PROPERTY)).anyTimes();
            List<String> hbaseMasterHosts = Arrays.asList(HOSTNAMES);
            EasyMock.expect(hbaseMaster.getHostNames()).andReturn(hbaseMasterHosts).anyTimes();
            EasyMock.replay(hbaseMaster);

            AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
            EasyMock.expect(cluster.getComponent("HBASE_MASTER")).andReturn(hbaseMaster).anyTimes();
            EasyMock.replay(cluster);

            // Run the test
            AmbariDynamicServiceURLCreator builder = newURLCreator(cluster, null);
            List<String> urls = builder.create("DISCOVERYTEST", null);
            validateServiceURLs(urls, HOSTNAMES, "http", "1234", "discoveryTest");
        } finally {
            System.clearProperty(AmbariDynamicServiceURLCreator.MAPPING_CONFIG_OVERRIDE_PROPERTY);
            FileUtils.deleteQuietly(tmpFile);
        }
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


    private static final String OOZIE_OVERRIDE_MAPPING_FILE_CONTENTS =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<service-discovery-url-mappings>\n" +
            "  <service name=\"OOZIE\">\n" +
            "    <url-pattern>{OOZIE_URL}/OVERRIDE</url-pattern>\n" +
            "    <properties>\n" +
            "      <property name=\"OOZIE_URL\">\n" +
            "        <component>OOZIE_SERVER</component>\n" +
            "        <config-property>oozie.base.url</config-property>\n" +
            "      </property>\n" +
            "    </properties>\n" +
            "  </service>\n" +
            "</service-discovery-url-mappings>\n";

    private static final String CUSTOM_AUGMENT_MAPPING_FILE_CONTENTS =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<service-discovery-url-mappings>\n" +
            "  <service name=\"DISCOVERYTEST\">\n" +
            "    <url-pattern>http://{HOST}:1234/discoveryTest</url-pattern>\n" +
            "    <properties>\n" +
            "      <property name=\"HOST\">\n" +
            "        <component>HBASE_MASTER</component>\n" +
            "        <hostname/>\n" +
            "      </property>\n" +
            "    </properties>\n" +
            "  </service>\n" +
            "</service-discovery-url-mappings>\n";
}

