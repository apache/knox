/**
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
package org.apache.hadoop.gateway.topology.simple;

import org.apache.hadoop.gateway.topology.validation.TopologyValidator;
import org.apache.hadoop.gateway.util.XmlUtils;
import org.easymock.EasyMock;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.util.*;

import static org.junit.Assert.*;


public class SimpleDescriptorHandlerTest {

    private static final String TEST_PROVIDER_CONFIG =
            "    <gateway>\n" +
                    "        <provider>\n" +
                    "            <role>authentication</role>\n" +
                    "            <name>ShiroProvider</name>\n" +
                    "            <enabled>true</enabled>\n" +
                    "            <param>\n" +
                    "                <!-- \n" +
                    "                session timeout in minutes,  this is really idle timeout,\n" +
                    "                defaults to 30mins, if the property value is not defined,, \n" +
                    "                current client authentication would expire if client idles contiuosly for more than this value\n" +
                    "                -->\n" +
                    "                <name>sessionTimeout</name>\n" +
                    "                <value>30</value>\n" +
                    "            </param>\n" +
                    "            <param>\n" +
                    "                <name>main.ldapRealm</name>\n" +
                    "                <value>org.apache.hadoop.gateway.shirorealm.KnoxLdapRealm</value>\n" +
                    "            </param>\n" +
                    "            <param>\n" +
                    "                <name>main.ldapContextFactory</name>\n" +
                    "                <value>org.apache.hadoop.gateway.shirorealm.KnoxLdapContextFactory</value>\n" +
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
                    "\n" +
                    "        <provider>\n" +
                    "            <role>identity-assertion</role>\n" +
                    "            <name>Default</name>\n" +
                    "            <enabled>true</enabled>\n" +
                    "        </provider>\n" +
                    "\n" +
                    "        <!--\n" +
                    "        Defines rules for mapping host names internal to a Hadoop cluster to externally accessible host names.\n" +
                    "        For example, a hadoop service running in AWS may return a response that includes URLs containing the\n" +
                    "        some AWS internal host name.  If the client needs to make a subsequent request to the host identified\n" +
                    "        in those URLs they need to be mapped to external host names that the client Knox can use to connect.\n" +
                    "\n" +
                    "        If the external hostname and internal host names are same turn of this provider by setting the value of\n" +
                    "        enabled parameter as false.\n" +
                    "\n" +
                    "        The name parameter specifies the external host names in a comma separated list.\n" +
                    "        The value parameter specifies corresponding internal host names in a comma separated list.\n" +
                    "\n" +
                    "        Note that when you are using Sandbox, the external hostname needs to be localhost, as seen in out\n" +
                    "        of box sandbox.xml.  This is because Sandbox uses port mapping to allow clients to connect to the\n" +
                    "        Hadoop services using localhost.  In real clusters, external host names would almost never be localhost.\n" +
                    "        -->\n" +
                    "        <provider>\n" +
                    "            <role>hostmap</role>\n" +
                    "            <name>static</name>\n" +
                    "            <enabled>true</enabled>\n" +
                    "            <param><name>localhost</name><value>sandbox,sandbox.hortonworks.com</value></param>\n" +
                    "        </provider>\n" +
                    "    </gateway>\n";


    /**
     * KNOX-1006
     *
     * N.B. This test depends on the DummyServiceDiscovery extension being configured:
     *             org.apache.hadoop.gateway.topology.discovery.test.extension.DummyServiceDiscovery
     */
    @Test
    public void testSimpleDescriptorHandler() throws Exception {

        final String type = "DUMMY";
        final String address = "http://c6401.ambari.apache.org:8080";
        final String clusterName = "dummy";
        final Map<String, List<String>> serviceURLs = new HashMap<>();
        serviceURLs.put("NAMENODE", null);
        serviceURLs.put("JOBTRACKER", null);
        serviceURLs.put("WEBHDFS", null);
        serviceURLs.put("WEBHCAT", null);
        serviceURLs.put("OOZIE", null);
        serviceURLs.put("WEBHBASE", null);
        serviceURLs.put("HIVE", null);
        serviceURLs.put("RESOURCEMANAGER", null);
        serviceURLs.put("AMBARIUI", Arrays.asList("http://c6401.ambari.apache.org:8080"));

        // Write the externalized provider config to a temp file
        File providerConfig = writeProviderConfig("ambari-cluster-policy.xml", TEST_PROVIDER_CONFIG);

        File topologyFile = null;
        try {
            File destDir = (new File(".")).getCanonicalFile();

            // Mock out the simple descriptor
            SimpleDescriptor testDescriptor = EasyMock.createNiceMock(SimpleDescriptor.class);
            EasyMock.expect(testDescriptor.getName()).andReturn("mysimpledescriptor").anyTimes();
            EasyMock.expect(testDescriptor.getDiscoveryAddress()).andReturn(address).anyTimes();
            EasyMock.expect(testDescriptor.getDiscoveryType()).andReturn(type).anyTimes();
            EasyMock.expect(testDescriptor.getDiscoveryUser()).andReturn(null).anyTimes();
            EasyMock.expect(testDescriptor.getProviderConfig()).andReturn(providerConfig.getAbsolutePath()).anyTimes();
            EasyMock.expect(testDescriptor.getClusterName()).andReturn(clusterName).anyTimes();
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
                           SimpleDescriptorHandler.handle(testDescriptor,
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
            Document extProviderConf = XmlUtils.readXml(new ByteArrayInputStream(TEST_PROVIDER_CONFIG.getBytes()));
            Node gatewayNode = (Node) xpath.compile("/topology/gateway").evaluate(topologyXml, XPathConstants.NODE);
            assertTrue("Resulting provider config should be identical to the referenced content.",
                       extProviderConf.getDocumentElement().isEqualNode(gatewayNode));

            // Validate the service declarations
            Map<String, List<String>> topologyServiceURLs = new HashMap<>();
            NodeList serviceNodes =
                        (NodeList) xpath.compile("/topology/service").evaluate(topologyXml, XPathConstants.NODESET);
            for (int serviceNodeIndex=0; serviceNodeIndex < serviceNodes.getLength(); serviceNodeIndex++) {
                Node serviceNode = serviceNodes.item(serviceNodeIndex);
                Node roleNode = (Node) xpath.compile("role/text()").evaluate(serviceNode, XPathConstants.NODE);
                assertNotNull(roleNode);
                String role = roleNode.getNodeValue();
                NodeList urlNodes = (NodeList) xpath.compile("url/text()").evaluate(serviceNode, XPathConstants.NODESET);
                for(int urlNodeIndex = 0 ; urlNodeIndex < urlNodes.getLength(); urlNodeIndex++) {
                    Node urlNode = urlNodes.item(urlNodeIndex);
                    assertNotNull(urlNode);
                    String url = urlNode.getNodeValue();
                    assertNotNull("Every declared service should have a URL.", url);
                    if (!topologyServiceURLs.containsKey(role)) {
                        topologyServiceURLs.put(role, new ArrayList<String>());
                    }
                    topologyServiceURLs.get(role).add(url);
                }
            }
            assertEquals("Unexpected number of service declarations.", serviceURLs.size(), topologyServiceURLs.size());

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            providerConfig.delete();
            if (topologyFile != null) {
                topologyFile.delete();
            }
        }
    }


    private File writeProviderConfig(String path, String content) throws IOException {
        File f = new File(path);

        Writer fw = new FileWriter(f);
        fw.write(content);
        fw.flush();
        fw.close();

        return f;
    }

}
