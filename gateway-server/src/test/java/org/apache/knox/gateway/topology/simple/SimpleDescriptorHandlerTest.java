/*
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
package org.apache.knox.gateway.topology.simple;

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.topology.validation.TopologyValidator;
import org.apache.knox.gateway.util.XmlUtils;
import org.easymock.EasyMock;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasXPath;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SimpleDescriptorHandlerTest {

    private static final String TEST_PROVIDER_CONFIG =
            "    <gateway>\n" +
            "        <provider>\n" +
            "            <role>authentication</role>\n" +
            "            <name>ShiroProvider</name>\n" +
            "            <enabled>true</enabled>\n" +
            "            <param>\n" +
            "                <name>sessionTimeout</name>\n" +
            "                <value>30</value>\n" +
            "            </param>\n" +
            "            <param>\n" +
            "                <name>main.ldapRealm</name>\n" +
            "                <value>org.apache.knox.gateway.shirorealm.KnoxLdapRealm</value>\n" +
            "            </param>\n" +
            "            <param>\n" +
            "                <name>main.ldapContextFactory</name>\n" +
            "                <value>org.apache.knox.gateway.shirorealm.KnoxLdapContextFactory</value>\n" +
            "            </param>\n" +
            "            <param>\n" +
            "                <name>main.ldapRealm.contextFactory</name>\n" +
            "                <value>$ldapContextFactory</value>\n" +
            "            </param>\n" +
            "            <param>\n" +
            "                <name>main.ldapRealm.userDnTemplate</name>\n" +
            "                <value>uid={0},ou=people,dc=hadoop,dc=apache,dc=org</value>\n" +
            "            </param>\n" +
            "            <param>\n" +
            "                <name>main.ldapRealm.contextFactory.url</name>\n" +
            "                <value>ldap://localhost:33389</value>\n" +
            "            </param>\n" +
            "            <param>\n" +
            "                <name>main.ldapRealm.contextFactory.authenticationMechanism</name>\n" +
            "                <value>simple</value>\n" +
            "            </param>\n" +
            "            <param>\n" +
            "                <name>urls./**</name>\n" +
            "                <value>authcBasic</value>\n" +
            "            </param>\n" +
            "        </provider>\n" +
            "        <provider>\n" +
            "            <role>identity-assertion</role>\n" +
            "            <name>Default</name>\n" +
            "            <enabled>true</enabled>\n" +
            "        </provider>\n" +
            "        <provider>\n" +
            "            <role>hostmap</role>\n" +
            "            <name>static</name>\n" +
            "            <enabled>true</enabled>\n" +
            "            <param><name>localhost</name><value>sandbox,sandbox.hortonworks.com</value></param>\n" +
            "        </provider>\n" +
            "    </gateway>\n";

    private static final String TEST_HA_PROVIDER_CONFIG =
            "    <gateway>\n" +
            "        <provider>\n" +
            "            <role>ha</role>\n" +
            "            <name>HaProvider</name>\n" +
            "            <enabled>true</enabled>\n" +
            "            <param><name>HIVE</name><value>enabled=auto</value></param>\n" +
            "            <param><name>WEBHDFS</name><value>enabled=true</value></param>\n" +
            "            <param><name>WEBHBASE</name><value>enabled=auto;maxFailoverAttempts=2;failoverSleep=10</value></param>\n" +
            "            <param><name>ATLAS</name><value>enabled=auto;maxFailoverAttempts=2;failoverSleep=10</value></param>\n" +
            "            <param><name>ATLAS-API</name><value>enabled=false</value></param>\n" +
            "        </provider>\n" +
            "    </gateway>\n";


    @Test
    public void testSkipDiscovery_NoDiscoveryConfig() throws Exception {
        // There should be no exception because in this case, discovery should be skipped altogether
        doTestDiscoveryConfig(null, null, null, null, null);
    }

    private void doTestDiscoveryConfig(final String discoveryType,
                                       final String address,
                                       final String clusterName,
                                       final String user,
                                       final String pwdAlias) throws Exception {
        GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.replay(gc);

        // Write the externalized provider config to a temp file
        File providerConfig = new File(System.getProperty("java.io.tmpdir"), "test-providers.xml");
        FileUtils.write(providerConfig, TEST_PROVIDER_CONFIG, StandardCharsets.UTF_8);

        // Mock out the simple descriptor
        SimpleDescriptor testDescriptor = EasyMock.createNiceMock(SimpleDescriptor.class);
        EasyMock.expect(testDescriptor.getName()).andReturn("mysimpledescriptor").anyTimes();
        EasyMock.expect(testDescriptor.getProviderConfig()).andReturn(providerConfig.getAbsolutePath()).anyTimes();
        EasyMock.expect(testDescriptor.getDiscoveryAddress()).andReturn(address).anyTimes();
        EasyMock.expect(testDescriptor.getDiscoveryType()).andReturn(discoveryType).anyTimes();
        EasyMock.expect(testDescriptor.getDiscoveryUser()).andReturn(user).anyTimes();
        EasyMock.expect(testDescriptor.getDiscoveryPasswordAlias()).andReturn(pwdAlias).anyTimes();
        EasyMock.expect(testDescriptor.getCluster()).andReturn(clusterName).anyTimes();
        List<SimpleDescriptor.Service> serviceMocks;
        SimpleDescriptor.Service svc = EasyMock.createNiceMock(SimpleDescriptor.Service.class);
        EasyMock.expect(svc.getName()).andReturn("KNOXTOKEN").anyTimes();
        EasyMock.expect(svc.getVersion()).andReturn(null).anyTimes();
        EasyMock.expect(svc.getURLs()).andReturn(Collections.emptyList()).anyTimes();

        Map<String, String> serviceParams = new HashMap<>();
        serviceParams.put("knox.token.ttl", "120000");
        EasyMock.expect(svc.getParams()).andReturn(serviceParams).anyTimes();

        EasyMock.replay(svc);
        serviceMocks = Collections.singletonList(svc);

        EasyMock.expect(testDescriptor.getServices()).andReturn(serviceMocks).anyTimes();
        EasyMock.replay(testDescriptor);

        File destDir = new File(System.getProperty("java.io.tmpdir")).getCanonicalFile();
        File topologyFile = null;

        try {
            // Invoke the simple descriptor handler
            Map<String, File> files =
                    SimpleDescriptorHandler.handle(gc,
                            testDescriptor,
                            providerConfig.getParentFile(), // simple desc co-located with provider config
                            destDir);
            topologyFile = files.get("topology");
            assertTrue(topologyFile.exists());
        } finally {
            providerConfig.delete();
            if (topologyFile != null) {
                topologyFile.delete();
            }
        }
    }

    /*
     * KNOX-1006
     *
     * N.B. This test depends on the PropertiesFileServiceDiscovery extension being configured:
     *             org.apache.knox.gateway.topology.discovery.test.extension.PropertiesFileServiceDiscovery
     */
    @Test
    public void testSimpleDescriptorHandler() throws Exception {

        final String type = "PROPERTIES_FILE";
        final String clusterName = "dummy";

        // Create a properties file to be the source of service discovery details for this test
        final File discoveryConfig = File.createTempFile(getClass().getName() + "_discovery-config", ".properties");

        final String address = discoveryConfig.getAbsolutePath();

        final Properties DISCOVERY_PROPERTIES = new Properties();
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".name", clusterName);
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".NAMENODE.url", "hdfs://namenodehost:8020");
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".JOBTRACKER.url", "rpc://jobtrackerhostname:8050");
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".WEBHDFS.url", "http://webhdfshost:1234");
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".WEBHCAT.url", "http://webhcathost:50111/templeton");
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".OOZIE.url", "http://ooziehost:11000/oozie");
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".WEBHBASE.url", "http://webhbasehost:1234");
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".HIVE.url", "http://hivehostname:10001/clipath");
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".RESOURCEMANAGER.url", "http://remanhost:8088/ws");

        try (OutputStream outputStream = Files.newOutputStream(discoveryConfig.toPath())){
            DISCOVERY_PROPERTIES.store(outputStream, null);
        } catch (FileNotFoundException e) {
            fail(e.getMessage());
        }

        final Map<String, List<String>> serviceURLs = new HashMap<>();
        serviceURLs.put("NAMENODE", null);
        serviceURLs.put("JOBTRACKER", null);
        serviceURLs.put("WEBHDFS", null);
        serviceURLs.put("WEBHCAT", null);
        serviceURLs.put("OOZIE", null);
        serviceURLs.put("WEBHBASE", null);
        serviceURLs.put("HIVE", null);
        serviceURLs.put("RESOURCEMANAGER", null);
        serviceURLs.put("AMBARIUI", Collections.singletonList("http://c6401.ambari.apache.org:8080"));
        serviceURLs.put("KNOXSSO", null);

        // Write the externalized provider config to a temp file
        File providerConfig = new File(System.getProperty("java.io.tmpdir"), "ambari-cluster-policy.xml");
        FileUtils.write(providerConfig, TEST_PROVIDER_CONFIG, StandardCharsets.UTF_8);

        File topologyFile = null;
        try {
            File destDir = new File(System.getProperty("java.io.tmpdir")).getCanonicalFile();

            Map<String, Map<String, String>> serviceParameters = new HashMap<>();
            Map<String, String> knoxssoParams = new HashMap<>();
            knoxssoParams.put("knoxsso.cookie.secure.only", "true");
            knoxssoParams.put("knoxsso.token.ttl", "100000");
            serviceParameters.put("KNOXSSO", knoxssoParams);

            GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
            EasyMock.replay(gc);

            // Mock out the simple descriptor
            SimpleDescriptor testDescriptor = EasyMock.createNiceMock(SimpleDescriptor.class);
            EasyMock.expect(testDescriptor.getName()).andReturn("mysimpledescriptor").anyTimes();
            EasyMock.expect(testDescriptor.getDiscoveryAddress()).andReturn(address).anyTimes();
            EasyMock.expect(testDescriptor.getDiscoveryType()).andReturn(type).anyTimes();
            EasyMock.expect(testDescriptor.getDiscoveryUser()).andReturn(null).anyTimes();
            EasyMock.expect(testDescriptor.getProviderConfig()).andReturn(providerConfig.getAbsolutePath()).anyTimes();
            EasyMock.expect(testDescriptor.getCluster()).andReturn(clusterName).anyTimes();
            List<SimpleDescriptor.Service> serviceMocks = new ArrayList<>();
            for (String serviceName : serviceURLs.keySet()) {
                SimpleDescriptor.Service svc = EasyMock.createNiceMock(SimpleDescriptor.Service.class);
                EasyMock.expect(svc.getName()).andReturn(serviceName).anyTimes();
                EasyMock.expect(svc.getVersion()).andReturn("WEBHDFS".equals(serviceName) ? "2.4.0" : null).anyTimes();
                EasyMock.expect(svc.getURLs()).andReturn(serviceURLs.get(serviceName)).anyTimes();
                EasyMock.expect(svc.getParams()).andReturn(serviceParameters.get(serviceName)).anyTimes();
                EasyMock.replay(svc);
                serviceMocks.add(svc);
            }
            EasyMock.expect(testDescriptor.getServices()).andReturn(serviceMocks).anyTimes();
            EasyMock.replay(testDescriptor);

            // Invoke the simple descriptor handler
            Map<String, File> files =
               SimpleDescriptorHandler.handle(gc,
                                              testDescriptor,
                                              providerConfig.getParentFile(), // simple desc co-located with provider config
                                              destDir);
            topologyFile = files.get("topology");

            // Validate the resulting topology descriptor
            assertTrue(topologyFile.exists());

            // Validate the topology descriptor's correctness
            TopologyValidator validator = new TopologyValidator( topologyFile.getAbsolutePath() );
            if( !validator.validateTopology() ){
                throw new SAXException( validator.getErrorString() );
            }

            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();

            // Parse the topology descriptor
            Document topologyXml = XmlUtils.readXml(topologyFile);

            // KNOX-1105 Mark generated topology files
            assertThat("Expected the \"generated\" marker element in the topology XML, with value of \"true\".",
                       topologyXml,
                       hasXPath("/topology/generated", is("true")));

            // Validate the provider configuration
            Node gatewayNode = (Node) xpath.compile("/topology/gateway").evaluate(topologyXml, XPathConstants.NODE);
            ProviderConfiguration testProviderConfiguration =
                        ProviderConfigurationParser.parseXML(new ByteArrayInputStream(TEST_PROVIDER_CONFIG.getBytes(StandardCharsets.UTF_8)));
            validateGeneratedProviderConfiguration(testProviderConfiguration, gatewayNode);

            // Validate the service declarations
            Map<String, List<String>> topologyServiceURLs = new HashMap<>();
            NodeList serviceNodes =
                        (NodeList) xpath.compile("/topology/service").evaluate(topologyXml, XPathConstants.NODESET);
            for (int serviceNodeIndex=0; serviceNodeIndex < serviceNodes.getLength(); serviceNodeIndex++) {
                Node serviceNode = serviceNodes.item(serviceNodeIndex);

                // Validate the role
                Node roleNode = (Node) xpath.compile("role/text()").evaluate(serviceNode, XPathConstants.NODE);
                assertNotNull(roleNode);
                String role = roleNode.getNodeValue();

                // Validate the explicit version for the WEBHDFS service
                if ("WEBHDFS".equals(role)) {
                    Node versionNode = (Node) xpath.compile("version/text()").evaluate(serviceNode, XPathConstants.NODE);
                    assertNotNull(versionNode);
                    String version = versionNode.getNodeValue();
                    assertEquals("2.4.0", version);
                }

                // Validate the URLs
                NodeList urlNodes = (NodeList) xpath.compile("url/text()").evaluate(serviceNode, XPathConstants.NODESET);
                for(int urlNodeIndex = 0 ; urlNodeIndex < urlNodes.getLength(); urlNodeIndex++) {
                    Node urlNode = urlNodes.item(urlNodeIndex);
                    assertNotNull(urlNode);
                    String url = urlNode.getNodeValue();

                    // If the service should have a URL (some don't require it)
                    if (serviceURLs.containsKey(role)) {
                        assertNotNull("Declared service should have a URL.", url);
                        if (!topologyServiceURLs.containsKey(role)) {
                            topologyServiceURLs.put(role, new ArrayList<>());
                        }
                        topologyServiceURLs.get(role).add(url); // Add it for validation later
                    }
                }

                // If params were declared in the descriptor, then validate them in the resulting topology file
                Map<String, String> params = serviceParameters.get(role);
                if (params != null) {
                    NodeList paramNodes = (NodeList) xpath.compile("param").evaluate(serviceNode, XPathConstants.NODESET);
                    for (int paramNodeIndex = 0; paramNodeIndex < paramNodes.getLength(); paramNodeIndex++) {
                        Node paramNode = paramNodes.item(paramNodeIndex);
                        String paramName = (String) xpath.compile("name/text()").evaluate(paramNode, XPathConstants.STRING);
                        String paramValue = (String) xpath.compile("value/text()").evaluate(paramNode, XPathConstants.STRING);
                        assertTrue(params.keySet().contains(paramName));
                        assertEquals(params.get(paramName), paramValue);
                    }
                }

            }
            assertEquals("Unexpected number of service declarations.", (serviceURLs.size() - 1), topologyServiceURLs.size());

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            providerConfig.delete();
            discoveryConfig.delete();
            if (topologyFile != null) {
                topologyFile.delete();
            }
        }
    }


    /*
     * KNOX-1006
     *
     * Verify the behavior of the SimpleDescriptorHandler when service discovery fails to produce a valid URL for
     * a service.
     *
     * N.B. This test depends on the PropertiesFileServiceDiscovery extension being configured:
     *             org.apache.knox.gateway.topology.discovery.test.extension.PropertiesFileServiceDiscovery
     */
    @Test
    public void testInvalidServiceURLFromDiscovery() throws Exception {
        final String CLUSTER_NAME = "myproperties";

        // Configure the PropertiesFile Service Discovery implementation for this test
        final String DEFAULT_VALID_SERVICE_URL = "http://localhost:9999/thiswillwork";
        Properties serviceDiscoverySourceProps = new Properties();
        serviceDiscoverySourceProps.setProperty(CLUSTER_NAME + ".NAMENODE.url",
                                                DEFAULT_VALID_SERVICE_URL.replace("http", "hdfs"));
        serviceDiscoverySourceProps.setProperty(CLUSTER_NAME + ".JOBTRACKER.url",
                                                DEFAULT_VALID_SERVICE_URL.replace("http", "rpc"));
        serviceDiscoverySourceProps.setProperty(CLUSTER_NAME + ".WEBHDFS.url",         DEFAULT_VALID_SERVICE_URL);
        serviceDiscoverySourceProps.setProperty(CLUSTER_NAME + ".WEBHCAT.url",         DEFAULT_VALID_SERVICE_URL);
        serviceDiscoverySourceProps.setProperty(CLUSTER_NAME + ".OOZIE.url",           DEFAULT_VALID_SERVICE_URL);
        serviceDiscoverySourceProps.setProperty(CLUSTER_NAME + ".WEBHBASE.url",        DEFAULT_VALID_SERVICE_URL);
        serviceDiscoverySourceProps.setProperty(CLUSTER_NAME + ".HIVE.url",            "{SCHEME}://localhost:10000/");
        serviceDiscoverySourceProps.setProperty(CLUSTER_NAME + ".RESOURCEMANAGER.url", DEFAULT_VALID_SERVICE_URL);
        serviceDiscoverySourceProps.setProperty(CLUSTER_NAME + ".AMBARIUI.url",        DEFAULT_VALID_SERVICE_URL);

        File serviceDiscoverySource = File.createTempFile("service-discovery", ".properties");
        try (OutputStream outputStream = Files.newOutputStream(serviceDiscoverySource.toPath())) {
          serviceDiscoverySourceProps.store(outputStream, "Test Service Discovery Source");
        }

        // Prepare a mock SimpleDescriptor
        final String type = "PROPERTIES_FILE";
        final String address = serviceDiscoverySource.getAbsolutePath();
        final Map<String, List<String>> serviceURLs = new HashMap<>();
        serviceURLs.put("NAMENODE", null);
        serviceURLs.put("JOBTRACKER", null);
        serviceURLs.put("WEBHDFS", null);
        serviceURLs.put("WEBHCAT", null);
        serviceURLs.put("OOZIE", null);
        serviceURLs.put("WEBHBASE", null);
        serviceURLs.put("HIVE", null);
        serviceURLs.put("RESOURCEMANAGER", null);
        serviceURLs.put("AMBARIUI", Collections.singletonList("http://c6401.ambari.apache.org:8080"));

        // Write the externalized provider config to a temp file
        File providerConfig = writeProviderConfig("ambari-cluster-policy.xml", TEST_PROVIDER_CONFIG);

        File topologyFile = null;
        try {
            File destDir = (new File(".")).getCanonicalFile();

            GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
            EasyMock.replay(gc);

            // Mock out the simple descriptor
            SimpleDescriptor testDescriptor = EasyMock.createNiceMock(SimpleDescriptor.class);
            EasyMock.expect(testDescriptor.getName()).andReturn("mysimpledescriptor").anyTimes();
            EasyMock.expect(testDescriptor.getDiscoveryAddress()).andReturn(address).anyTimes();
            EasyMock.expect(testDescriptor.getDiscoveryType()).andReturn(type).anyTimes();
            EasyMock.expect(testDescriptor.getDiscoveryUser()).andReturn(null).anyTimes();
            EasyMock.expect(testDescriptor.getProviderConfig()).andReturn(providerConfig.getAbsolutePath()).anyTimes();
            EasyMock.expect(testDescriptor.getCluster()).andReturn(CLUSTER_NAME).anyTimes();
            List<SimpleDescriptor.Service> serviceMocks = new ArrayList<>();
            for (String serviceName : serviceURLs.keySet()) {
                SimpleDescriptor.Service svc = EasyMock.createNiceMock(SimpleDescriptor.Service.class);
                EasyMock.expect(svc.getName()).andReturn(serviceName).anyTimes();
                EasyMock.expect(svc.getURLs()).andReturn(serviceURLs.get(serviceName)).anyTimes();
                EasyMock.replay(svc);
                serviceMocks.add(svc);
            }
            EasyMock.expect(testDescriptor.getServices()).andReturn(serviceMocks).anyTimes();
            EasyMock.replay(testDescriptor);

            // Invoke the simple descriptor handler
            Map<String, File> files =
                SimpleDescriptorHandler.handle(gc,
                                               testDescriptor,
                                               providerConfig.getParentFile(), // simple desc co-located with provider config
                                               destDir);

            topologyFile = files.get("topology");

            // Validate the resulting topology descriptor
            assertTrue(topologyFile.exists());

            // Validate the topology descriptor's correctness
            TopologyValidator validator = new TopologyValidator( topologyFile.getAbsolutePath() );
            if( !validator.validateTopology() ){
                throw new SAXException( validator.getErrorString() );
            }

            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();

            // Parse the topology descriptor
            Document topologyXml = XmlUtils.readXml(topologyFile);

            // Validate the provider configuration
            Node gatewayNode = (Node) xpath.compile("/topology/gateway").evaluate(topologyXml, XPathConstants.NODE);
            ProviderConfiguration testProviderConfiguration =
                ProviderConfigurationParser.parseXML(new ByteArrayInputStream(TEST_PROVIDER_CONFIG.getBytes(StandardCharsets.UTF_8)));
            validateGeneratedProviderConfiguration(testProviderConfiguration, gatewayNode);

            // Validate the service declarations
            List<String> topologyServices = new ArrayList<>();
            Map<String, List<String>> topologyServiceURLs = new HashMap<>();
            NodeList serviceNodes =
                    (NodeList) xpath.compile("/topology/service").evaluate(topologyXml, XPathConstants.NODESET);
            for (int serviceNodeIndex=0; serviceNodeIndex < serviceNodes.getLength(); serviceNodeIndex++) {
                Node serviceNode = serviceNodes.item(serviceNodeIndex);
                Node roleNode = (Node) xpath.compile("role/text()").evaluate(serviceNode, XPathConstants.NODE);
                assertNotNull(roleNode);
                String role = roleNode.getNodeValue();
                topologyServices.add(role);
                NodeList urlNodes = (NodeList) xpath.compile("url/text()").evaluate(serviceNode, XPathConstants.NODESET);
                for(int urlNodeIndex = 0 ; urlNodeIndex < urlNodes.getLength(); urlNodeIndex++) {
                    Node urlNode = urlNodes.item(urlNodeIndex);
                    assertNotNull(urlNode);
                    String url = urlNode.getNodeValue();
                    assertNotNull("Every declared service should have a URL.", url);
                    if (!topologyServiceURLs.containsKey(role)) {
                        topologyServiceURLs.put(role, new ArrayList<>());
                    }
                    topologyServiceURLs.get(role).add(url);
                }
            }

            // There should not be a service element for HIVE, since it had no valid URLs
            assertEquals("Unexpected number of service declarations.", serviceURLs.size() - 1, topologyServices.size());
            assertFalse("The HIVE service should have been omitted from the generated topology.", topologyServices.contains("HIVE"));

            assertEquals("Unexpected number of service URLs.", serviceURLs.size() - 1, topologyServiceURLs.size());

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            serviceDiscoverySource.delete();
            providerConfig.delete();
            if (topologyFile != null) {
                topologyFile.delete();
            }
        }
    }

    /*
     * KNOX-1216
     */
    @Test (expected = IllegalArgumentException.class)
    public void testMissingProviderConfigReference() throws Exception {
        // Prepare a mock SimpleDescriptor
        final Map<String, List<String>> serviceURLs = new HashMap<>();
        serviceURLs.put("NAMENODE", null);
        serviceURLs.put("JOBTRACKER", null);
        serviceURLs.put("WEBHDFS", null);
        serviceURLs.put("WEBHCAT", null);
        serviceURLs.put("OOZIE", null);
        serviceURLs.put("WEBHBASE", null);
        serviceURLs.put("HIVE", null);
        serviceURLs.put("RESOURCEMANAGER", null);
        serviceURLs.put("AMBARIUI", Collections.singletonList("http://c6401.ambari.apache.org:8080"));

        File destDir = new File(System.getProperty("java.io.tmpdir")).getCanonicalFile();

        GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.replay(gc);

        // Mock out the simple descriptor
        SimpleDescriptor testDescriptor = EasyMock.createNiceMock(SimpleDescriptor.class);
        EasyMock.expect(testDescriptor.getName()).andReturn("mysimpledescriptor").anyTimes();
        EasyMock.expect(testDescriptor.getDiscoveryType()).andReturn("DUMMY").anyTimes();
        EasyMock.expect(testDescriptor.getDiscoveryUser()).andReturn(null).anyTimes();
        EasyMock.expect(testDescriptor.getProviderConfig()).andReturn(null).anyTimes();
        List<SimpleDescriptor.Service> serviceMocks = new ArrayList<>();
        for (String serviceName : serviceURLs.keySet()) {
            SimpleDescriptor.Service svc = EasyMock.createNiceMock(SimpleDescriptor.Service.class);
            EasyMock.expect(svc.getName()).andReturn(serviceName).anyTimes();
            EasyMock.expect(svc.getVersion()).andReturn("WEBHDFS".equals(serviceName) ? "2.4.0" : null).anyTimes();
            EasyMock.expect(svc.getURLs()).andReturn(serviceURLs.get(serviceName)).anyTimes();
            EasyMock.replay(svc);
            serviceMocks.add(svc);
        }
        EasyMock.expect(testDescriptor.getServices()).andReturn(serviceMocks).anyTimes();
        EasyMock.replay(testDescriptor);

        SimpleDescriptorHandler.handle(gc, testDescriptor, destDir, destDir);
    }

    /*
     * KNOX-1153
     *
     * N.B. This test depends on the PropertiesFileServiceDiscovery extension being configured:
     *             org.apache.knox.gateway.topology.discovery.test.extension.PropertiesFileServiceDiscovery
     */
    @Test
    public void testSimpleDescriptorHandlerHaProviderConfigOverrides() throws Exception {

        final String type = "PROPERTIES_FILE";
        final String clusterName = "dummy";

        // Create a properties file to be the source of service discovery details for this test
        final File discoveryConfig = File.createTempFile(getClass().getName() + "_discovery-config", ".properties");

        final String address = discoveryConfig.getAbsolutePath();

        final String HIVE_HA_ENABLED   = "true";
        final String HIVE_HA_ENSEMBLE  = "http://zkhost1:1281,http://zkhost2:1281";
        final String HIVE_HA_NAMESPACE = "hiveserver2";

        final String WEBHBASE_HA_ENABLED   = "false";
        final String WEBHBASE_HA_ENSEMBLE  = "http://zkhost1:1281,http://zkhost2:1281";

        final String ATLAS_HA_ENABLED   = "true";
        final String ATLAS_HA_ENSEMBLE  = "http://zkhost5:1281,http://zkhost6:1281,http://zkhost7:1281";

        final Properties DISCOVERY_PROPERTIES = new Properties();
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".name", clusterName);
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".NAMENODE.url", "hdfs://namenodehost:8020");
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".WEBHDFS.url", "http://webhdfshost1:1234,http://webhdfshost2:1234");
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".HIVE.url", "http://hivehostname:10001/clipath");
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".RESOURCEMANAGER.url", "http://remanhost:8088/ws");
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".WEBHBASE.url", "http://webhbasehost:8080");
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".HIVE.haEnabled", HIVE_HA_ENABLED);
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".HIVE.ensemble", HIVE_HA_ENSEMBLE);
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".HIVE.namespace", HIVE_HA_NAMESPACE);
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".WEBHBASE.haEnabled", WEBHBASE_HA_ENABLED);
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".WEBHBASE.ensemble", WEBHBASE_HA_ENSEMBLE);
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".ATLAS.haEnabled", ATLAS_HA_ENABLED);
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".ATLAS.ensemble", ATLAS_HA_ENSEMBLE);
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".ATLAS-API.ensemble", ATLAS_HA_ENSEMBLE);
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".ATLAS-API.url", "http://atlasapihost:2222");

        try (OutputStream outputStream = Files.newOutputStream(discoveryConfig.toPath())){
            DISCOVERY_PROPERTIES.store(outputStream, null);
        } catch (FileNotFoundException e) {
            fail(e.getMessage());
        }

        final Map<String, List<String>> serviceURLs = new HashMap<>();
        serviceURLs.put("NAMENODE", null);
        serviceURLs.put("WEBHDFS", null);
        serviceURLs.put("RESOURCEMANAGER", null);
        serviceURLs.put("ATLAS", null);
        serviceURLs.put("ATLAS-API", null);
        serviceURLs.put("HIVE", null);

        // Write the externalized provider config to a temp file
        File providerConfig = new File(System.getProperty("java.io.tmpdir"), "ambari-cluster-policy.xml");
        FileUtils.write(providerConfig, TEST_HA_PROVIDER_CONFIG, StandardCharsets.UTF_8);

        File topologyFile = null;
        try {
            File destDir = new File(System.getProperty("java.io.tmpdir")).getCanonicalFile();

            GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
            EasyMock.replay(gc);

            // Mock out the simple descriptor
            SimpleDescriptor testDescriptor = EasyMock.createNiceMock(SimpleDescriptor.class);
            EasyMock.expect(testDescriptor.getName()).andReturn("mysimpledescriptor").anyTimes();
            EasyMock.expect(testDescriptor.getDiscoveryAddress()).andReturn(address).anyTimes();
            EasyMock.expect(testDescriptor.getDiscoveryType()).andReturn(type).anyTimes();
            EasyMock.expect(testDescriptor.getDiscoveryUser()).andReturn(null).anyTimes();
            EasyMock.expect(testDescriptor.getProviderConfig()).andReturn(providerConfig.getAbsolutePath()).anyTimes();
            EasyMock.expect(testDescriptor.getCluster()).andReturn(clusterName).anyTimes();
            List<SimpleDescriptor.Service> serviceMocks = new ArrayList<>();
            for (String serviceName : serviceURLs.keySet()) {
                SimpleDescriptor.Service svc = EasyMock.createNiceMock(SimpleDescriptor.Service.class);
                EasyMock.expect(svc.getName()).andReturn(serviceName).anyTimes();
                EasyMock.expect(svc.getVersion()).andReturn("WEBHDFS".equals(serviceName) ? "2.4.0" : null).anyTimes();
                EasyMock.expect(svc.getURLs()).andReturn(serviceURLs.get(serviceName)).anyTimes();
                EasyMock.expect(svc.getParams()).andReturn(null).anyTimes();
                EasyMock.replay(svc);
                serviceMocks.add(svc);
            }
            EasyMock.expect(testDescriptor.getServices()).andReturn(serviceMocks).anyTimes();
            EasyMock.replay(testDescriptor);

            // Invoke the simple descriptor handler
            Map<String, File> files =
                SimpleDescriptorHandler.handle(gc,
                                               testDescriptor,
                                               providerConfig.getParentFile(), // simple desc co-located with provider config
                                               destDir);
            topologyFile = files.get("topology");

            // Validate the resulting topology descriptor
            assertTrue(topologyFile.exists());

            // Validate the topology descriptor's correctness
            TopologyValidator validator = new TopologyValidator( topologyFile.getAbsolutePath() );
            if( !validator.validateTopology() ){
                throw new SAXException( validator.getErrorString() );
            }

            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();

            // Parse the topology descriptor
            Document topologyXml = XmlUtils.readXml(topologyFile);

            // KNOX-1105 Mark generated topology files
            assertThat("Expected the \"generated\" marker element in the topology XML, with value of \"true\".",
                       topologyXml,
                       hasXPath("/topology/generated", is("true")));

            // Validate the provider configuration
            Node gatewayNode = (Node) xpath.compile("/topology/gateway").evaluate(topologyXml, XPathConstants.NODE);
            ProviderConfiguration testProviderConfiguration =
                ProviderConfigurationParser.parseXML(new ByteArrayInputStream(TEST_HA_PROVIDER_CONFIG.getBytes(StandardCharsets.UTF_8)));
            validateGeneratedProviderConfiguration(testProviderConfiguration, gatewayNode);

            // Validate the service declarations
            List<String> generatedServiceDeclarations = new ArrayList<>();
            Map<String, List<String>> topologyServiceURLs = new HashMap<>();
            NodeList serviceNodes =
                (NodeList) xpath.compile("/topology/service").evaluate(topologyXml, XPathConstants.NODESET);
            for (int serviceNodeIndex=0; serviceNodeIndex < serviceNodes.getLength(); serviceNodeIndex++) {
                Node serviceNode = serviceNodes.item(serviceNodeIndex);

                // Validate the role
                Node roleNode = (Node) xpath.compile("role/text()").evaluate(serviceNode, XPathConstants.NODE);
                assertNotNull(roleNode);
                String role = roleNode.getNodeValue();
                generatedServiceDeclarations.add(role);

                // Validate the explicit version for the WEBHDFS service
                if ("WEBHDFS".equals(role)) {
                    Node versionNode = (Node) xpath.compile("version/text()").evaluate(serviceNode, XPathConstants.NODE);
                    assertNotNull(versionNode);
                    String version = versionNode.getNodeValue();
                    assertEquals("2.4.0", version);

                    // Not expecting any service params because WEBHDFS HA is not ZK-based
                    NodeList paramNodes = (NodeList) xpath.compile("param").evaluate(serviceNode, XPathConstants.NODESET);
                    assertNotNull(paramNodes);
                    assertEquals(0, paramNodes.getLength());

                    // Expecting multiple URLs because of HA enablement
                    NodeList urlNodes = (NodeList) xpath.compile("url").evaluate(serviceNode, XPathConstants.NODESET);
                    assertNotNull(urlNodes);
                    assertEquals(2, urlNodes.getLength());
                }

                // Validate the HIVE service params
                if ("HIVE".equals(role)) {
                    // Expecting HA-related service params
                    NodeList paramNodes = (NodeList) xpath.compile("param").evaluate(serviceNode, XPathConstants.NODESET);
                    assertNotNull(paramNodes);
                    Map<String, String> hiveServiceParams = new HashMap<>();
                    for (int paramNodeIndex=0; paramNodeIndex < paramNodes.getLength(); paramNodeIndex++) {
                        Node paramNode = paramNodes.item(paramNodeIndex);
                        Node nameNode = (Node) xpath.compile("name/text()").evaluate(paramNode, XPathConstants.NODE);
                        assertNotNull(nameNode);
                        Node valueNode = (Node) xpath.compile("value/text()").evaluate(paramNode, XPathConstants.NODE);
                        assertNotNull(valueNode);
                        hiveServiceParams.put(nameNode.getNodeValue(), valueNode.getNodeValue());
                    }
                    assertEquals("Expected true because enabled=auto and service config indicates HA is enabled",
                                 HIVE_HA_ENABLED, hiveServiceParams.get("haEnabled"));
                    assertEquals(HIVE_HA_ENSEMBLE, hiveServiceParams.get("zookeeperEnsemble"));
                    assertEquals(HIVE_HA_NAMESPACE, hiveServiceParams.get("zookeeperNamespace"));
                }

                // Validate the ATLAS service params
                if ("ATLAS".equals(role)) {
                    // Expecting HA-related service params
                    NodeList paramNodes = (NodeList) xpath.compile("param").evaluate(serviceNode, XPathConstants.NODESET);
                    assertNotNull(paramNodes);
                    Map<String, String> atlasServiceParams = new HashMap<>();
                    for (int paramNodeIndex=0; paramNodeIndex < paramNodes.getLength(); paramNodeIndex++) {
                        Node paramNode = paramNodes.item(paramNodeIndex);
                        Node nameNode = (Node) xpath.compile("name/text()").evaluate(paramNode, XPathConstants.NODE);
                        assertNotNull(nameNode);
                        Node valueNode = (Node) xpath.compile("value/text()").evaluate(paramNode, XPathConstants.NODE);
                        assertNotNull(valueNode);
                        atlasServiceParams.put(nameNode.getNodeValue(), valueNode.getNodeValue());
                    }
                    assertEquals("Expected true because enabled=auto and service config indicates HA is enabled",
                                 ATLAS_HA_ENABLED, atlasServiceParams.get("haEnabled"));
                    assertEquals(ATLAS_HA_ENSEMBLE, atlasServiceParams.get("zookeeperEnsemble"));
                    assertNull(atlasServiceParams.get("zookeeperNamespace"));
                }

                // Validate the ATLAS service params
                if ("ATLAS-API".equals(role)) {
                    // Expecting non-HA-related service params
                    NodeList paramNodes = (NodeList) xpath.compile("param").evaluate(serviceNode, XPathConstants.NODESET);
                    assertNotNull(paramNodes);
                    Map<String, String> atlasApiServiceParams = new HashMap<>();
                    for (int paramNodeIndex=0; paramNodeIndex < paramNodes.getLength(); paramNodeIndex++) {
                        Node paramNode = paramNodes.item(paramNodeIndex);
                        Node nameNode = (Node) xpath.compile("name/text()").evaluate(paramNode, XPathConstants.NODE);
                        assertNotNull(nameNode);
                        Node valueNode = (Node) xpath.compile("value/text()").evaluate(paramNode, XPathConstants.NODE);
                        assertNotNull(valueNode);
                        atlasApiServiceParams.put(nameNode.getNodeValue(), valueNode.getNodeValue());
                    }
                    // There should be no HA-related params because the enabled attribute is false
                    assertNull(atlasApiServiceParams.get("zookeeperEnsemble"));
                    assertNull(atlasApiServiceParams.get("zookeeperNamespace"));

                    // Instead, the URL should be declared for the service
                    NodeList urlNodes = (NodeList) xpath.compile("url").evaluate(serviceNode, XPathConstants.NODESET);
                    assertNotNull(urlNodes);
                    assertEquals(1, urlNodes.getLength());
                    assertEquals(DISCOVERY_PROPERTIES.getProperty(clusterName + ".ATLAS-API.url"), urlNodes.item(0).getTextContent());
                }

                // Validate the URLs
                NodeList urlNodes = (NodeList) xpath.compile("url/text()").evaluate(serviceNode, XPathConstants.NODESET);
                for(int urlNodeIndex = 0 ; urlNodeIndex < urlNodes.getLength(); urlNodeIndex++) {
                    Node urlNode = urlNodes.item(urlNodeIndex);
                    assertNotNull(urlNode);
                    String url = urlNode.getNodeValue();

                    // If the service should have a URL (some don't require it)
                    if (serviceURLs.containsKey(role)) {
                        assertNotNull("Declared service should have a URL.", url);
                        if (!topologyServiceURLs.containsKey(role)) {
                            topologyServiceURLs.put(role, new ArrayList<>());
                        }
                        topologyServiceURLs.get(role).add(url); // Add it for validation later
                    }
                }
            }
            assertEquals("Unexpected number of service declarations.", serviceURLs.size(), generatedServiceDeclarations.size());

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            providerConfig.delete();
            discoveryConfig.delete();
            if (topologyFile != null) {
                topologyFile.delete();
            }
        }
    }

    @Test
    public void shouldAllowKnoxServiceWithoutUrlsAndParams() throws Exception {
      final String clusterName = "testCluster";
      File providerConfig =  File.createTempFile("testKnoxProvider", ".xml");
      FileUtils.write(providerConfig, TEST_PROVIDER_CONFIG, StandardCharsets.UTF_8);

      final File discoveryConfig = File.createTempFile("testKnoxDiscoveryConfig", ".properties");
      final Properties discoveryProperties = new Properties();
      discoveryProperties.setProperty(clusterName + ".name", clusterName);

      try (OutputStream outputStream = Files.newOutputStream(discoveryConfig.toPath())){
          discoveryProperties.store(outputStream, null);
      }

      File topologyFile = null;
      try {
        final File destDir = new File(System.getProperty("java.io.tmpdir")).getCanonicalFile();
        final GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);

        final SimpleDescriptor testDescriptor = EasyMock.createNiceMock(SimpleDescriptor.class);
        EasyMock.expect(testDescriptor.getName()).andReturn("testKnoxDescriptor").anyTimes();
        EasyMock.expect(testDescriptor.getProviderConfig()).andReturn(providerConfig.getAbsolutePath()).anyTimes();
        EasyMock.expect(testDescriptor.getDiscoveryAddress()).andReturn(discoveryConfig.getAbsolutePath()).anyTimes();
        EasyMock.expect(testDescriptor.getDiscoveryType()).andReturn("PROPERTIES_FILE").anyTimes();
        EasyMock.expect(testDescriptor.getDiscoveryUser()).andReturn(null).anyTimes();
        EasyMock.expect(testDescriptor.getCluster()).andReturn(clusterName).anyTimes();

        final SimpleDescriptor.Service knoxService = EasyMock.createNiceMock(SimpleDescriptor.Service.class);
        EasyMock.expect(knoxService.getName()).andReturn("KNOX").anyTimes();
        EasyMock.expect(knoxService.getURLs()).andReturn(null).anyTimes();
        EasyMock.expect(knoxService.getParams()).andReturn(null).anyTimes();
        List<SimpleDescriptor.Service> serviceMocks = Collections.singletonList(knoxService);
        EasyMock.expect(testDescriptor.getServices()).andReturn(serviceMocks).anyTimes();
        EasyMock.replay(gc, knoxService, testDescriptor);

        final Map<String, File> handleResult = SimpleDescriptorHandler.handle(gc, testDescriptor, destDir, destDir);
        topologyFile = handleResult.get(SimpleDescriptorHandler.RESULT_TOPOLOGY);
        assertTrue(topologyFile.exists());
        assertTrue(new TopologyValidator(topologyFile.getAbsolutePath()).validateTopology());

        final Document topologyXml = XmlUtils.readXml(topologyFile);
        assertThat(topologyXml, hasXPath("/topology/service/role", is(equalTo("KNOX"))));
      } finally {
        providerConfig.delete();
        discoveryConfig.delete();
        if (topologyFile != null) {
          topologyFile.delete();
        }
      }
    }

    private File writeProviderConfig(String path, String content) throws IOException {
        File f = new File(path);
        FileUtils.write(f, content, StandardCharsets.UTF_8);
        return f;
    }

    private void validateGeneratedProviderConfiguration(ProviderConfiguration expected, Node generatedGatewayNode) throws Exception {
        assertNotNull(expected);

        // Parse a ProviderConfiguration from the specified XML node
        StringWriter writer = new StringWriter();
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new DOMSource(generatedGatewayNode), new StreamResult(writer));
        ProviderConfiguration generatedProviderConfiguration =
                        ProviderConfigurationParser.parseXML(new ByteArrayInputStream(writer.toString().getBytes(StandardCharsets.UTF_8)));
        assertNotNull(generatedProviderConfiguration);

        // Compare the generated ProviderConfiguration to the expected one
        Set<ProviderConfiguration.Provider> expectedProviders = expected.getProviders();
        Set<ProviderConfiguration.Provider> actualProviders = generatedProviderConfiguration.getProviders();
        assertEquals("The number of providers should be the same.", expectedProviders.size(), actualProviders.size());

        for (ProviderConfiguration.Provider expectedProvider : expectedProviders) {
            assertTrue("Failed to validate provider with role " + expectedProvider.getRole(),
                       validateProvider(expectedProvider, actualProviders));
        }
    }

    /*
     * Verify that the expected provider is included in the specified set of actual providers.
     *
     * @param expected        A Provider that should be among the specified actual providers
     * @param actualProviders The set of actual providers.
     */
    private boolean validateProvider(ProviderConfiguration.Provider expected, Set<ProviderConfiguration.Provider> actualProviders) {
        boolean foundMatch = false;

        for (ProviderConfiguration.Provider actual : actualProviders) {
            if (expected.getRole().equals(actual.getRole())) {
                if (expected.getName().equals(actual.getName())) {
                    if (expected.isEnabled() == actual.isEnabled()) {
                        Map<String, String> expectedParams = expected.getParams();
                        Map<String, String> actualParams = actual.getParams();
                        if (expectedParams.size() == actualParams.size()) {
                            int matchingParamCount = 0;
                            for (String expectedParamKey : expectedParams.keySet()) {
                                if (actualParams.containsKey(expectedParamKey) && expectedParams.get(expectedParamKey).equals(actualParams.get(expectedParamKey))) {
                                    matchingParamCount++;
                                }
                            }
                            foundMatch = (matchingParamCount == expectedParams.size());
                        }
                    }
                }
            }

            if (foundMatch) {
                break;
            }
        }

        return foundMatch;
    }
}
