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
package org.apache.knox.gateway.topology.discovery.ambari;

import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.topology.discovery.ServiceDiscovery;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;
import org.easymock.EasyMock;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Test the Ambari ServiceDiscovery implementation.
 *
 * N.B. These tests do NOT verify Ambari API responses. They DO validate the Ambari ServiceDiscovery implementation's
 *      treatment of the responses as they were observed at the time the tests are developed.
 */
public class AmbariServiceDiscoveryTest {

    @Test
    public void testSingleClusterDiscovery() throws Exception {
        final String discoveryAddress = "http://ambarihost:8080";
        final String clusterName = "testCluster";
        ServiceDiscovery sd = new TestAmbariServiceDiscovery(clusterName);

        GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.replay(gc);

        ServiceDiscoveryConfig sdc = EasyMock.createNiceMock(ServiceDiscoveryConfig.class);
        EasyMock.expect(sdc.getAddress()).andReturn(discoveryAddress).anyTimes();
        EasyMock.expect(sdc.getUser()).andReturn(null).anyTimes();
        EasyMock.replay(sdc);

        ServiceDiscovery.Cluster cluster = sd.discover(gc, sdc, clusterName);
        assertNotNull(cluster);
        assertEquals(clusterName, cluster.getName());
        assertTrue(AmbariCluster.class.isAssignableFrom(cluster.getClass()));
        assertEquals(6, ((AmbariCluster) cluster).getComponents().size());
    }

    @Test
    public void testSingleClusterDiscoveryWithDefaultAddress() throws Exception {
        final String discoveryAddress = "http://ambarihost:8080";
        final String clusterName = "testCluster";

        ServiceDiscovery sd = new TestAmbariServiceDiscovery(clusterName);

        GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(gc.getDefaultDiscoveryAddress()).andReturn(discoveryAddress).anyTimes();
        EasyMock.expect(gc.getDefaultDiscoveryCluster()).andReturn(clusterName).anyTimes();
        EasyMock.replay(gc);

        ServiceDiscoveryConfig sdc = EasyMock.createNiceMock(ServiceDiscoveryConfig.class);
        EasyMock.expect(sdc.getUser()).andReturn(null).anyTimes();
        EasyMock.replay(sdc);

        ServiceDiscovery.Cluster cluster = sd.discover(gc, sdc, clusterName);
        assertNotNull(cluster);
        assertEquals(clusterName, cluster.getName());
        assertTrue(AmbariCluster.class.isAssignableFrom(cluster.getClass()));
        assertEquals(6, ((AmbariCluster) cluster).getComponents().size());
    }

    @Test
    public void testSingleClusterDiscoveryWithDefaultClusterName() throws Exception {
        final String discoveryAddress = "http://ambarihost:8080";
        final String clusterName = "testCluster";

        ServiceDiscovery sd = new TestAmbariServiceDiscovery(clusterName);

        GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(gc.getDefaultDiscoveryAddress()).andReturn(discoveryAddress).anyTimes();
        EasyMock.expect(gc.getDefaultDiscoveryCluster()).andReturn(clusterName).anyTimes();
        EasyMock.replay(gc);

        ServiceDiscoveryConfig sdc = EasyMock.createNiceMock(ServiceDiscoveryConfig.class);
        EasyMock.expect(sdc.getAddress()).andReturn(discoveryAddress).anyTimes();
        EasyMock.expect(sdc.getUser()).andReturn(null).anyTimes();
        EasyMock.replay(sdc);

        ServiceDiscovery.Cluster cluster = sd.discover(gc, sdc, null);
        assertNotNull(cluster);
        assertEquals(clusterName, cluster.getName());
        assertTrue(AmbariCluster.class.isAssignableFrom(cluster.getClass()));
        assertEquals(6, ((AmbariCluster) cluster).getComponents().size());
    }

    @Test
    public void testSingleClusterDiscoveryWithDefaultAddressAndClusterName() throws Exception {
        final String discoveryAddress = "http://ambarihost:8080";
        final String clusterName = "testCluster";

        ServiceDiscovery sd = new TestAmbariServiceDiscovery(clusterName);

        GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(gc.getDefaultDiscoveryAddress()).andReturn(discoveryAddress).anyTimes();
        EasyMock.expect(gc.getDefaultDiscoveryCluster()).andReturn(clusterName).anyTimes();
        EasyMock.replay(gc);

        ServiceDiscoveryConfig sdc = EasyMock.createNiceMock(ServiceDiscoveryConfig.class);
        EasyMock.expect(sdc.getUser()).andReturn(null).anyTimes();
        EasyMock.replay(sdc);

        ServiceDiscovery.Cluster cluster = sd.discover(gc, sdc, null);
        assertNotNull(cluster);
        assertEquals(clusterName, cluster.getName());
        assertTrue(AmbariCluster.class.isAssignableFrom(cluster.getClass()));
        assertEquals(6, ((AmbariCluster) cluster).getComponents().size());
    }

    @Test
    public void testSingleClusterDiscoveryWithMissingAddressAndClusterName() throws Exception {
        final String clusterName = "testCluster";

        AliasService as = EasyMock.createNiceMock(AliasService.class);
        EasyMock.replay(as);

        ServiceDiscovery sd = new TestAmbariServiceDiscovery(clusterName, as);

        GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.replay(gc);

        ServiceDiscoveryConfig sdc = EasyMock.createNiceMock(ServiceDiscoveryConfig.class);
        EasyMock.expect(sdc.getUser()).andReturn(null).anyTimes();
        EasyMock.replay(sdc);

        ServiceDiscovery.Cluster cluster = sd.discover(gc, sdc, null);
        assertNull(cluster);
    }

    @Test
    public void testSingleClusterDiscoveryWithMissingAddress() throws Exception {
        final String clusterName = "testCluster";

        ServiceDiscovery sd = new TestAmbariServiceDiscovery(clusterName);

        GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.replay(gc);

        ServiceDiscoveryConfig sdc = EasyMock.createNiceMock(ServiceDiscoveryConfig.class);
        EasyMock.expect(sdc.getUser()).andReturn(null).anyTimes();
        EasyMock.replay(sdc);

        ServiceDiscovery.Cluster cluster = sd.discover(gc, sdc, clusterName);
        assertNull(cluster);
    }

    @Test
    public void testSingleClusterDiscoveryWithMissingClusterName() throws Exception {
        final String discoveryAddress = "http://ambarihost:8080";
        final String clusterName = "testCluster";

        ServiceDiscovery sd = new TestAmbariServiceDiscovery(clusterName);

        GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(gc.getDefaultDiscoveryAddress()).andReturn(discoveryAddress).anyTimes();
        EasyMock.replay(gc);

        ServiceDiscoveryConfig sdc = EasyMock.createNiceMock(ServiceDiscoveryConfig.class);
        EasyMock.expect(sdc.getUser()).andReturn(null).anyTimes();
        EasyMock.replay(sdc);

        ServiceDiscovery.Cluster cluster = sd.discover(gc, sdc, null);
        assertNull(cluster);
    }

    @Test
    public void testBulkClusterDiscovery() throws Exception {
        final String discoveryAddress = "http://ambarihost:8080";
        final String clusterName = "anotherCluster";
        ServiceDiscovery sd = new TestAmbariServiceDiscovery(clusterName);

        GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.replay(gc);

        ServiceDiscoveryConfig sdc = EasyMock.createNiceMock(ServiceDiscoveryConfig.class);
        EasyMock.expect(sdc.getAddress()).andReturn(discoveryAddress).anyTimes();
        EasyMock.expect(sdc.getUser()).andReturn(null).anyTimes();
        EasyMock.replay(sdc);

        Map<String, ServiceDiscovery.Cluster> clusters = sd.discover(gc, sdc);
        assertNotNull(clusters);
        assertEquals(1, clusters.size());
        ServiceDiscovery.Cluster cluster = clusters.get(clusterName);
        assertNotNull(cluster);
        assertEquals(clusterName, cluster.getName());
        assertTrue(AmbariCluster.class.isAssignableFrom(cluster.getClass()));
        assertEquals(6, ((AmbariCluster) cluster).getComponents().size());
    }

    @Test
    public void testClusterDiscoveryWithExternalComponentConfigAugmentation() throws Exception {
        final String discoveryAddress = "http://ambarihost:8080";
        final String clusterName = "myCluster";

        GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.replay(gc);

        // Create component config mapping override
        Properties compConfOverrideProps = new Properties();
        compConfOverrideProps.setProperty("DISCOVERY_TEST", "test-site");
        File compConfOverrides = File.createTempFile(getClass().getName()+"component-conf-overrides", ".properties");
        compConfOverrideProps.store(Files.newOutputStream(compConfOverrides.toPath()), "Test Config Overrides");
        System.setProperty(AmbariServiceDiscovery.COMPONENT_CONFIG_MAPPING_SYSTEM_PROPERTY,
                           compConfOverrides.getAbsolutePath());

        // Create URL mapping override
        final String URL_MAPPING_OVERRIDES =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<service-discovery-url-mappings>\n" +
                "  <service name=\"DISCOVERYTEST\">\n" +
                "    <url-pattern>{TEST_ADDRESS}/discoveryTest</url-pattern>\n" +
                "    <properties>\n" +
                "      <property name=\"TEST_ADDRESS\">\n" +
                "        <component>DISCOVERY_TEST</component>\n" +
                "        <config-property>discovery.test.base.url</config-property>\n" +
                "      </property>\n" +
                "    </properties>\n" +
                "  </service>\n" +
                "</service-discovery-url-mappings>\n";

        File urlMappingOverrides = File.createTempFile(getClass().getName()+"_url-overrides", ".xml");
        FileUtils.writeStringToFile(urlMappingOverrides,
                                    URL_MAPPING_OVERRIDES,
                                    StandardCharsets.UTF_8);
        System.setProperty(AmbariDynamicServiceURLCreator.MAPPING_CONFIG_OVERRIDE_PROPERTY,
                           urlMappingOverrides.getAbsolutePath());

        // Re-initialize the component config mappings to include the extension
        AmbariServiceDiscovery.initializeComponentConfigMappings();

        ServiceDiscovery sd = new TestAmbariServiceDiscovery(clusterName);

        ServiceDiscoveryConfig sdc = EasyMock.createNiceMock(ServiceDiscoveryConfig.class);
        EasyMock.expect(sdc.getAddress()).andReturn(discoveryAddress).anyTimes();
        EasyMock.expect(sdc.getUser()).andReturn(null).anyTimes();
        EasyMock.replay(sdc);

        try {
            ServiceDiscovery.Cluster cluster = sd.discover(gc, sdc, clusterName);
            assertNotNull(cluster);
            assertEquals(clusterName, cluster.getName());
            assertTrue(AmbariCluster.class.isAssignableFrom(cluster.getClass()));
            assertEquals(7, ((AmbariCluster) cluster).getComponents().size());

            List<String> discTestURLs = cluster.getServiceURLs("DISCOVERYTEST");
            assertNotNull(discTestURLs);
            assertEquals(1, discTestURLs.size());
            assertEquals("http://c6402.ambari.apache.org:11999/discoveryTest", discTestURLs.get(0));
        } finally {
            System.clearProperty(AmbariDynamicServiceURLCreator.MAPPING_CONFIG_OVERRIDE_PROPERTY);
            System.clearProperty(AmbariServiceDiscovery.COMPONENT_CONFIG_MAPPING_SYSTEM_PROPERTY);
            FileUtils.deleteQuietly(compConfOverrides);

            // Re-initialize the component config mappings without the extension
            AmbariServiceDiscovery.initializeComponentConfigMappings();
        }
    }

    /**
     * ServiceDiscovery implementation derived from AmbariServiceDiscovery, so the invokeREST method can be overridden
     * to eliminate the need to perform actual HTTP interactions with a real Ambari endpoint.
     */
    private static final class TestAmbariServiceDiscovery extends AmbariServiceDiscovery {

        static final String CLUSTER_PLACEHOLDER = TestRESTInvoker.CLUSTER_PLACEHOLDER;

        TestAmbariServiceDiscovery(String clusterName) {
            super(new TestRESTInvoker(clusterName));
        }

        TestAmbariServiceDiscovery(String clusterName, AliasService aliasService) {
            super(new TestRESTInvoker(clusterName));

            // Try to set the AliasService member, which is normally injected at runtime
            try {
                Field f = getClass().getSuperclass().getDeclaredField("aliasService");
                if (f != null) {
                    f.setAccessible(true);
                    f.set(this, aliasService);
                }
            } catch (Exception e) {
                //
            }
        }
    }

    private static final class TestRESTInvoker extends RESTInvoker {
        static final String CLUSTER_PLACEHOLDER = "CLUSTER_NAME";

        private Map<String, JSONObject> cannedResponses = new HashMap<>();

        TestRESTInvoker(String clusterName) {
            super(null, null);

            cannedResponses.put(AmbariServiceDiscovery.AMBARI_CLUSTERS_URI,
                    (JSONObject) JSONValue.parse(CLUSTERS_JSON_TEMPLATE.replaceAll(CLUSTER_PLACEHOLDER,
                            clusterName)));

            cannedResponses.put(String.format(Locale.ROOT, AmbariServiceDiscovery.AMBARI_HOSTROLES_URI, clusterName),
                    (JSONObject) JSONValue.parse(HOSTROLES_JSON_TEMPLATE.replaceAll(CLUSTER_PLACEHOLDER,
                            clusterName)));

            cannedResponses.put(String.format(Locale.ROOT, AmbariServiceDiscovery.AMBARI_SERVICECONFIGS_URI, clusterName),
                    (JSONObject) JSONValue.parse(SERVICECONFIGS_JSON_TEMPLATE.replaceAll(CLUSTER_PLACEHOLDER,
                            clusterName)));
        }

        @Override
        JSONObject invoke(String url, String username, String passwordAlias) {
            return cannedResponses.get(url.substring(url.indexOf("/api")));
        }
    }

    ////////////////////////////////////////////////////////////////////////
    //  JSON response templates, based on actual response content excerpts
    ////////////////////////////////////////////////////////////////////////

    private static final String CLUSTERS_JSON_TEMPLATE =
    "{\n" +
    "  \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters\",\n" +
    "  \"items\" : [\n" +
    "    {\n" +
    "      \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "      \"Clusters\" : {\n" +
    "        \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "        \"version\" : \"HDP-2.6\"\n" +
    "      }\n" +
    "    }\n" +
    "  ]" +
    "}";


    private static final String HOSTROLES_JSON_TEMPLATE =
    "{\n" +
    "  \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services?fields=components/host_components/HostRoles\",\n" +
    "  \"items\" : [\n" +
    "    {\n" +
    "      \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services/AMBARI_METRICS\",\n" +
    "      \"ServiceInfo\" : {\n" +
    "        \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "        \"service_name\" : \"AMBARI_METRICS\"\n" +
    "      },\n" +
    "      \"components\" : [\n" +
    "        {\n" +
    "          \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services/AMBARI_METRICS/components/METRICS_COLLECTOR\",\n" +
    "          \"ServiceComponentInfo\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"component_name\" : \"METRICS_COLLECTOR\",\n" +
    "            \"service_name\" : \"AMBARI_METRICS\"\n" +
    "          },\n" +
    "          \"host_components\" : [\n" +
    "            {\n" +
    "              \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/hosts/c6403.ambari.apache.org/host_components/METRICS_COLLECTOR\",\n" +
    "              \"HostRoles\" : {\n" +
    "                \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "                \"component_name\" : \"METRICS_COLLECTOR\",\n" +
    "                \"host_name\" : \"c6403.ambari.apache.org\",\n" +
    "                \"public_host_name\" : \"c6403.ambari.apache.org\",\n" +
    "                \"service_name\" : \"AMBARI_METRICS\",\n" +
    "                \"stack_id\" : \"HDP-2.6\",\n" +
    "              }\n" +
    "            }\n" +
    "          ]\n" +
    "        },\n" +
    "        {\n" +
    "          \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services/HBASE/components/HBASE_MASTER\",\n" +
    "          \"ServiceComponentInfo\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"component_name\" : \"HBASE_MASTER\",\n" +
    "            \"service_name\" : \"HBASE\"\n" +
    "          },\n" +
    "          \"host_components\" : [\n" +
    "            {\n" +
    "              \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/hosts/c6401.ambari.apache.org/host_components/HBASE_MASTER\",\n" +
    "              \"HostRoles\" : {\n" +
    "                \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "                \"component_name\" : \"HBASE_MASTER\",\n" +
    "                \"host_name\" : \"c6401.ambari.apache.org\",\n" +
    "                \"public_host_name\" : \"c6401.ambari.apache.org\",\n" +
    "                \"service_name\" : \"HBASE\",\n" +
    "                \"stack_id\" : \"HDP-2.6\",\n" +
    "              }\n" +
    "            }\n" +
    "          ]\n" +
    "        }\n" +
    "      ]\n" +
    "    },\n" +
    "    {\n" +
    "      \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services/HDFS\",\n" +
    "      \"ServiceInfo\" : {\n" +
    "        \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "        \"service_name\" : \"HDFS\"\n" +
    "      },\n" +
    "      \"components\" : [\n" +
    "        {\n" +
    "          \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services/HDFS/components/NAMENODE\",\n" +
    "          \"ServiceComponentInfo\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"component_name\" : \"NAMENODE\",\n" +
    "            \"service_name\" : \"HDFS\"\n" +
    "          },\n" +
    "          \"host_components\" : [\n" +
    "            {\n" +
    "              \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/hosts/c6401.ambari.apache.org/host_components/NAMENODE\",\n" +
    "              \"HostRoles\" : {\n" +
    "                \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "                \"component_name\" : \"NAMENODE\",\n" +
    "                \"host_name\" : \"c6401.ambari.apache.org\",\n" +
    "                \"public_host_name\" : \"c6401.ambari.apache.org\",\n" +
    "                \"service_name\" : \"HDFS\",\n" +
    "                \"stack_id\" : \"HDP-2.6\",\n" +
    "              }\n" +
    "            }\n" +
    "          ]\n" +
    "        },\n" +
    "        {\n" +
    "          \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services/HDFS/components/SECONDARY_NAMENODE\",\n" +
    "          \"ServiceComponentInfo\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"component_name\" : \"SECONDARY_NAMENODE\",\n" +
    "            \"service_name\" : \"HDFS\"\n" +
    "          },\n" +
    "          \"host_components\" : [\n" +
    "            {\n" +
    "              \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/hosts/c6402.ambari.apache.org/host_components/SECONDARY_NAMENODE\",\n" +
    "              \"HostRoles\" : {\n" +
    "                \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "                \"component_name\" : \"SECONDARY_NAMENODE\",\n" +
    "                \"host_name\" : \"c6402.ambari.apache.org\",\n" +
    "                \"public_host_name\" : \"c6402.ambari.apache.org\",\n" +
    "                \"service_name\" : \"HDFS\",\n" +
    "                \"stack_id\" : \"HDP-2.6\",\n" +
    "              }\n" +
    "            }\n" +
    "          ]\n" +
    "        }\n" +
    "      ]\n" +
    "    },\n" +
    "    {\n" +
    "      \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services/HIVE\",\n" +
    "      \"ServiceInfo\" : {\n" +
    "        \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "        \"service_name\" : \"HIVE\"\n" +
    "      },\n" +
    "      \"components\" : [\n" +
    "        {\n" +
    "          \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services/HIVE/components/HCAT\",\n" +
    "          \"ServiceComponentInfo\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"component_name\" : \"HCAT\",\n" +
    "            \"service_name\" : \"HIVE\"\n" +
    "          },\n" +
    "          \"host_components\" : [\n" +
    "            {\n" +
    "              \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/hosts/c6403.ambari.apache.org/host_components/HCAT\",\n" +
    "              \"HostRoles\" : {\n" +
    "                \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "                \"component_name\" : \"HCAT\",\n" +
    "                \"host_name\" : \"c6403.ambari.apache.org\",\n" +
    "                \"public_host_name\" : \"c6403.ambari.apache.org\",\n" +
    "                \"service_name\" : \"HIVE\",\n" +
    "                \"stack_id\" : \"HDP-2.6\",\n" +
    "              }\n" +
    "            }\n" +
    "          ]\n" +
    "        }\n" +
    "        {\n" +
    "          \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services/HIVE/components/HIVE_METASTORE\",\n" +
    "          \"ServiceComponentInfo\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"component_name\" : \"HIVE_METASTORE\",\n" +
    "            \"service_name\" : \"HIVE\"\n" +
    "          },\n" +
    "          \"host_components\" : [\n" +
    "            {\n" +
    "              \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/hosts/c6402.ambari.apache.org/host_components/HIVE_METASTORE\",\n" +
    "              \"HostRoles\" : {\n" +
    "                \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "                \"component_name\" : \"HIVE_METASTORE\",\n" +
    "                \"host_name\" : \"c6402.ambari.apache.org\",\n" +
    "                \"public_host_name\" : \"c6402.ambari.apache.org\",\n" +
    "                \"service_name\" : \"HIVE\",\n" +
    "                \"stack_id\" : \"HDP-2.6\",\n" +
    "              }\n" +
    "            }\n" +
    "          ]\n" +
    "        },\n" +
    "        {\n" +
    "          \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services/HIVE/components/HIVE_SERVER\",\n" +
    "          \"ServiceComponentInfo\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"component_name\" : \"HIVE_SERVER\",\n" +
    "            \"service_name\" : \"HIVE\"\n" +
    "          },\n" +
    "          \"host_components\" : [\n" +
    "            {\n" +
    "              \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/hosts/c6402.ambari.apache.org/host_components/HIVE_SERVER\",\n" +
    "              \"HostRoles\" : {\n" +
    "                \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "                \"component_name\" : \"HIVE_SERVER\",\n" +
    "                \"host_name\" : \"c6402.ambari.apache.org\",\n" +
    "                \"public_host_name\" : \"c6402.ambari.apache.org\",\n" +
    "                \"service_name\" : \"HIVE\",\n" +
    "                \"stack_id\" : \"HDP-2.6\",\n" +
    "              }\n" +
    "            }\n" +
    "          ]\n" +
    "        },\n" +
    "        {\n" +
    "          \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services/HIVE/components/WEBHCAT_SERVER\",\n" +
    "          \"ServiceComponentInfo\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"component_name\" : \"WEBHCAT_SERVER\",\n" +
    "            \"service_name\" : \"HIVE\"\n" +
    "          },\n" +
    "          \"host_components\" : [\n" +
    "            {\n" +
    "              \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/hosts/c6402.ambari.apache.org/host_components/WEBHCAT_SERVER\",\n" +
    "              \"HostRoles\" : {\n" +
    "                \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "                \"component_name\" : \"WEBHCAT_SERVER\",\n" +
    "                \"host_name\" : \"c6402.ambari.apache.org\",\n" +
    "                \"public_host_name\" : \"c6402.ambari.apache.org\",\n" +
    "                \"service_name\" : \"HIVE\",\n" +
    "                \"stack_id\" : \"HDP-2.6\",\n" +
    "              }\n" +
    "            }\n" +
    "          ]\n" +
    "        }\n" +
    "      ]\n" +
    "    },\n" +
    "    {\n" +
    "      \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services/OOZIE\",\n" +
    "      \"ServiceInfo\" : {\n" +
    "        \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "        \"service_name\" : \"OOZIE\"\n" +
    "      },\n" +
    "      \"components\" : [\n" +
    "        {\n" +
    "          \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services/OOZIE/components/OOZIE_SERVER\",\n" +
    "          \"ServiceComponentInfo\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"component_name\" : \"OOZIE_SERVER\",\n" +
    "            \"service_name\" : \"OOZIE\"\n" +
    "          },\n" +
    "          \"host_components\" : [\n" +
    "            {\n" +
    "              \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/hosts/c6402.ambari.apache.org/host_components/OOZIE_SERVER\",\n" +
    "              \"HostRoles\" : {\n" +
    "                \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "                \"component_name\" : \"OOZIE_SERVER\",\n" +
    "                \"host_name\" : \"c6402.ambari.apache.org\",\n" +
    "                \"public_host_name\" : \"c6402.ambari.apache.org\",\n" +
    "                \"service_name\" : \"OOZIE\",\n" +
    "                \"stack_id\" : \"HDP-2.6\"\n" +
    "              }\n" +
    "            }\n" +
    "          ]\n" +
    "        }\n" +
    "      ]\n" +
    "    },\n" +
    "    {\n" +
    "      \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services/YARN\",\n" +
    "      \"ServiceInfo\" : {\n" +
    "        \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "        \"service_name\" : \"YARN\"\n" +
    "      },\n" +
    "      \"components\" : [\n" +
    "        {\n" +
    "          \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services/YARN/components/APP_TIMELINE_SERVER\",\n" +
    "          \"ServiceComponentInfo\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"component_name\" : \"APP_TIMELINE_SERVER\",\n" +
    "            \"service_name\" : \"YARN\"\n" +
    "          },\n" +
    "          \"host_components\" : [\n" +
    "            {\n" +
    "              \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/hosts/c6402.ambari.apache.org/host_components/APP_TIMELINE_SERVER\",\n" +
    "              \"HostRoles\" : {\n" +
    "                \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "                \"component_name\" : \"APP_TIMELINE_SERVER\",\n" +
    "                \"host_name\" : \"c6402.ambari.apache.org\",\n" +
    "                \"public_host_name\" : \"c6402.ambari.apache.org\",\n" +
    "                \"service_name\" : \"YARN\",\n" +
    "                \"stack_id\" : \"HDP-2.6\"\n" +
    "              }\n" +
    "            }\n" +
    "          ]\n" +
    "        },\n" +
    "        {\n" +
    "          \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services/YARN/components/NODEMANAGER\",\n" +
    "          \"ServiceComponentInfo\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"component_name\" : \"NODEMANAGER\",\n" +
    "            \"service_name\" : \"YARN\"\n" +
    "          },\n" +
    "          \"host_components\" : [\n" +
    "            {\n" +
    "              \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/hosts/c6403.ambari.apache.org/host_components/NODEMANAGER\",\n" +
    "              \"HostRoles\" : {\n" +
    "                \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "                \"component_name\" : \"NODEMANAGER\",\n" +
    "                \"host_name\" : \"c6403.ambari.apache.org\",\n" +
    "                \"public_host_name\" : \"c6403.ambari.apache.org\",\n" +
    "                \"service_name\" : \"YARN\",\n" +
    "                \"stack_id\" : \"HDP-2.6\"\n" +
    "              }\n" +
    "            }\n" +
    "          ]\n" +
    "        },\n" +
    "        {\n" +
    "          \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services/YARN/components/RESOURCEMANAGER\",\n" +
    "          \"ServiceComponentInfo\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"component_name\" : \"RESOURCEMANAGER\",\n" +
    "            \"service_name\" : \"YARN\"\n" +
    "          },\n" +
    "          \"host_components\" : [\n" +
    "            {\n" +
    "              \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/hosts/c6402.ambari.apache.org/host_components/RESOURCEMANAGER\",\n" +
    "              \"HostRoles\" : {\n" +
    "                \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "                \"component_name\" : \"RESOURCEMANAGER\",\n" +
    "                \"ha_state\" : \"ACTIVE\",\n" +
    "                \"host_name\" : \"c6402.ambari.apache.org\",\n" +
    "                \"public_host_name\" : \"c6402.ambari.apache.org\",\n" +
    "                \"service_name\" : \"YARN\",\n" +
    "                \"stack_id\" : \"HDP-2.6\"\n" +
    "              }\n" +
    "            }\n" +
    "          ]\n" +
    "        }\n" +
    "      ]\n" +
    "    },\n" +
    "    {\n" +
    "      \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services/DISCOVERYTEST\",\n" +
    "      \"ServiceInfo\" : {\n" +
    "        \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "        \"service_name\" : \"DISCOVERYTEST\"\n" +
    "      },\n" +
    "      \"components\" : [\n" +
    "        {\n" +
    "          \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services/DISCOVERYTEST/components/DISCOVERY_TEST\",\n" +
    "          \"ServiceComponentInfo\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"component_name\" : \"DISCOVERY_TEST\",\n" +
    "            \"service_name\" : \"DISCOVERYTEST\"\n" +
    "          },\n" +
    "          \"host_components\" : [\n" +
    "            {\n" +
    "              \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/hosts/c6402.ambari.apache.org/host_components/DISCOVERY_TEST\",\n" +
    "              \"HostRoles\" : {\n" +
    "                \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "                \"component_name\" : \"DISCOVERY_TEST\",\n" +
    "                \"host_name\" : \"c6402.ambari.apache.org\",\n" +
    "                \"public_host_name\" : \"c6402.ambari.apache.org\",\n" +
    "                \"service_name\" : \"DISCOVERYTEST\",\n" +
    "                \"stack_id\" : \"HDP-2.6\"\n" +
    "              }\n" +
    "            }\n" +
    "          ]\n" +
    "        }\n" +
    "      ]\n" +
    "    },\n" +
    "    {\n" +
    "      \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services/ZOOKEEPER\",\n" +
    "      \"ServiceInfo\" : {\n" +
    "        \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "        \"service_name\" : \"ZOOKEEPER\"\n" +
    "      },\n" +
    "      \"components\" : [\n" +
    "        {\n" +
    "          \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/services/ZOOKEEPER/components/ZOOKEEPER_SERVER\",\n" +
    "          \"ServiceComponentInfo\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"component_name\" : \"ZOOKEEPER_SERVER\",\n" +
    "            \"service_name\" : \"ZOOKEEPER\"\n" +
    "          },\n" +
    "          \"host_components\" : [\n" +
    "            {\n" +
    "              \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/hosts/c6401.ambari.apache.org/host_components/ZOOKEEPER_SERVER\",\n" +
    "              \"HostRoles\" : {\n" +
    "                \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "                \"component_name\" : \"ZOOKEEPER_SERVER\",\n" +
    "                \"host_name\" : \"c6401.ambari.apache.org\",\n" +
    "                \"public_host_name\" : \"c6401.ambari.apache.org\",\n" +
    "                \"service_name\" : \"ZOOKEEPER\",\n" +
    "                \"stack_id\" : \"HDP-2.6\"\n" +
    "              }\n" +
    "            },\n" +
    "            {\n" +
    "              \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/hosts/c6402.ambari.apache.org/host_components/ZOOKEEPER_SERVER\",\n" +
    "              \"HostRoles\" : {\n" +
    "                \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "                \"component_name\" : \"ZOOKEEPER_SERVER\",\n" +
    "                \"host_name\" : \"c6402.ambari.apache.org\",\n" +
    "                \"public_host_name\" : \"c6402.ambari.apache.org\",\n" +
    "                \"service_name\" : \"ZOOKEEPER\",\n" +
    "                \"stack_id\" : \"HDP-2.6\"\n" +
    "              }\n" +
    "            },\n" +
    "            {\n" +
    "              \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/hosts/c6403.ambari.apache.org/host_components/ZOOKEEPER_SERVER\",\n" +
    "              \"HostRoles\" : {\n" +
    "                \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "                \"component_name\" : \"ZOOKEEPER_SERVER\",\n" +
    "                \"host_name\" : \"c6403.ambari.apache.org\",\n" +
    "                \"public_host_name\" : \"c6403.ambari.apache.org\",\n" +
    "                \"service_name\" : \"ZOOKEEPER\",\n" +
    "                \"stack_id\" : \"HDP-2.6\"\n" +
    "              }\n" +
    "            }\n" +
    "          ]\n" +
    "        }\n" +
    "      ]\n" +
    "    }\n" +
    "  ]\n" +
    "}\n";


    private static final String SERVICECONFIGS_JSON_TEMPLATE =
    "{\n" +
    "  \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/configurations/service_config_versions?is_current=true\",\n" +
    "  \"items\" : [\n" +
    "    {\n" +
    "      \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/configurations/service_config_versions?service_name=HBASE&service_config_version=1\",\n" +
    "      \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "      \"configurations\" : [\n" +
    "        {\n" +
    "          \"Config\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"stack_id\" : \"HDP-2.6\"\n" +
    "          },\n" +
    "          \"type\" : \"hbase-site\",\n" +
    "          \"tag\" : \"version1503410563715\",\n" +
    "          \"version\" : 1,\n" +
    "          \"properties\" : {\n" +
    "            \"hbase.master.info.bindAddress\" : \"0.0.0.0\",\n" +
    "            \"hbase.master.info.port\" : \"16010\",\n" +
    "            \"hbase.master.port\" : \"16000\",\n" +
    "            \"hbase.regionserver.info.port\" : \"16030\",\n" +
    "            \"hbase.regionserver.port\" : \"16020\",\n" +
    "            \"hbase.zookeeper.property.clientPort\" : \"2181\",\n" +
    "            \"hbase.zookeeper.quorum\" : \"c6403.ambari.apache.org,c6402.ambari.apache.org,c6401.ambari.apache.org\",\n" +
    "            \"hbase.zookeeper.useMulti\" : \"true\",\n" +
    "            \"zookeeper.znode.parent\" : \"/hbase-unsecure\"\n" +
    "          },\n" +
    "          \"properties_attributes\" : { }\n" +
    "        },\n" +
    "      ],\n" +
    "      \"is_current\" : true,\n" +
    "      \"service_config_version\" : 1,\n" +
    "      \"service_config_version_note\" : \"Initial configurations for HBase\",\n" +
    "      \"service_name\" : \"HBASE\",\n" +
    "      \"stack_id\" : \"HDP-2.6\",\n" +
    "      \"user\" : \"admin\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/configurations/service_config_versions?service_name=HDFS&service_config_version=2\",\n" +
    "      \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "      \"configurations\" : [\n" +
    "        {\n" +
    "          \"Config\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"stack_id\" : \"HDP-2.6\"\n" +
    "          },\n" +
    "          \"type\" : \"hdfs-site\",\n" +
    "          \"tag\" : \"version1\",\n" +
    "          \"version\" : 1,\n" +
    "          \"properties\" : {\n" +
    "            \"dfs.cluster.administrators\" : \" hdfs\",\n" +
    "            \"dfs.datanode.address\" : \"0.0.0.0:50010\",\n" +
    "            \"dfs.datanode.http.address\" : \"0.0.0.0:50075\",\n" +
    "            \"dfs.datanode.https.address\" : \"0.0.0.0:50475\",\n" +
    "            \"dfs.datanode.ipc.address\" : \"0.0.0.0:8010\",\n" +
    "            \"dfs.http.policy\" : \"HTTP_ONLY\",\n" +
    "            \"dfs.https.port\" : \"50470\",\n" +
    "            \"dfs.journalnode.http-address\" : \"0.0.0.0:8480\",\n" +
    "            \"dfs.journalnode.https-address\" : \"0.0.0.0:8481\",\n" +
    "            \"dfs.namenode.http-address\" : \"c6401.ambari.apache.org:50070\",\n" +
    "            \"dfs.namenode.https-address\" : \"c6401.ambari.apache.org:50470\",\n" +
    "            \"dfs.namenode.rpc-address\" : \"c6401.ambari.apache.org:8020\",\n" +
    "            \"dfs.namenode.secondary.http-address\" : \"c6402.ambari.apache.org:50090\",\n" +
    "            \"dfs.webhdfs.enabled\" : \"true\"\n" +
    "          },\n" +
    "          \"properties_attributes\" : {\n" +
    "            \"final\" : {\n" +
    "              \"dfs.webhdfs.enabled\" : \"true\",\n" +
    "              \"dfs.namenode.http-address\" : \"true\",\n" +
    "              \"dfs.support.append\" : \"true\",\n" +
    "              \"dfs.namenode.name.dir\" : \"true\",\n" +
    "              \"dfs.datanode.failed.volumes.tolerated\" : \"true\",\n" +
    "              \"dfs.datanode.data.dir\" : \"true\"\n" +
    "            }\n" +
    "          }\n" +
    "        },\n" +
    "        {\n" +
    "          \"Config\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"stack_id\" : \"HDP-2.6\"\n" +
    "          },\n" +
    "          \"type\" : \"core-site\",\n" +
    "          \"tag\" : \"version1502131215159\",\n" +
    "          \"version\" : 2,\n" +
    "          \"properties\" : {\n" +
    "            \"hadoop.http.authentication.simple.anonymous.allowed\" : \"true\",\n" +
    "            \"net.topology.script.file.name\" : \"/etc/hadoop/conf/topology_script.py\"\n" +
    "          },\n" +
    "          \"properties_attributes\" : {\n" +
    "            \"final\" : {\n" +
    "              \"fs.defaultFS\" : \"true\"\n" +
    "            }\n" +
    "          }\n" +
    "        }\n" +
    "      ],\n" +
    "      \"is_current\" : true,\n" +
    "      \"service_config_version\" : 2,\n" +
    "      \"service_config_version_note\" : \"knox trusted proxy support\",\n" +
    "      \"service_name\" : \"HDFS\",\n" +
    "      \"stack_id\" : \"HDP-2.6\",\n" +
    "      \"user\" : \"admin\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/configurations/service_config_versions?service_name=HIVE&service_config_version=3\",\n" +
    "      \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "      \"configurations\" : [\n" +
    "        {\n" +
    "          \"Config\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"stack_id\" : \"HDP-2.6\"\n" +
    "          },\n" +
    "          \"type\" : \"hive-env\",\n" +
    "          \"tag\" : \"version1\",\n" +
    "          \"version\" : 1,\n" +
    "          \"properties\" : {\n" +
    "            \"hive_security_authorization\" : \"None\",\n" +
    "            \"webhcat_user\" : \"hcat\"\n" +
    "          },\n" +
    "          \"properties_attributes\" : { }\n" +
    "        },\n" +
    "        {\n" +
    "          \"Config\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"stack_id\" : \"HDP-2.6\"\n" +
    "          },\n" +
    "          \"type\" : \"hiveserver2-site\",\n" +
    "          \"tag\" : \"version1\",\n" +
    "          \"version\" : 1,\n" +
    "          \"properties\" : {\n" +
    "            \"hive.metastore.metrics.enabled\" : \"true\",\n" +
    "            \"hive.security.authorization.enabled\" : \"false\",\n" +
    "            \"hive.service.metrics.hadoop2.component\" : \"hiveserver2\",\n" +
    "            \"hive.service.metrics.reporter\" : \"HADOOP2\"\n" +
    "          },\n" +
    "          \"properties_attributes\" : { }\n" +
    "        },\n" +
    "        {\n" +
    "          \"Config\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"stack_id\" : \"HDP-2.6\"\n" +
    "          },\n" +
    "          \"type\" : \"hive-interactive-site\",\n" +
    "          \"tag\" : \"version1\",\n" +
    "          \"version\" : 1,\n" +
    "          \"properties\" : {\n" +
    "            \"hive.server2.enable.doAs\" : \"false\",\n" +
    "            \"hive.server2.tez.default.queues\" : \"default\",\n" +
    "            \"hive.server2.tez.initialize.default.sessions\" : \"true\",\n" +
    "            \"hive.server2.tez.sessions.custom.queue.allowed\" : \"ignore\",\n" +
    "            \"hive.server2.tez.sessions.per.default.queue\" : \"1\",\n" +
    "            \"hive.server2.tez.sessions.restricted.configs\" : \"hive.execution.mode,hive.execution.engine\",\n" +
    "            \"hive.server2.thrift.http.port\" : \"10501\",\n" +
    "            \"hive.server2.thrift.port\" : \"10500\",\n" +
    "            \"hive.server2.webui.port\" : \"10502\",\n" +
    "            \"hive.server2.webui.use.ssl\" : \"false\",\n" +
    "            \"hive.server2.zookeeper.namespace\" : \"hiveserver2-hive2\"\n" +
    "          },\n" +
    "          \"properties_attributes\" : { }\n" +
    "        },\n" +
    "        {\n" +
    "          \"Config\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"stack_id\" : \"HDP-2.6\"\n" +
    "          },\n" +
    "          \"type\" : \"tez-interactive-site\",\n" +
    "          \"tag\" : \"version1\",\n" +
    "          \"version\" : 1,\n" +
    "          \"properties\" : {\n" +
    "            \"tez.am.am-rm.heartbeat.interval-ms.max\" : \"10000\",\n" +
    "            \"tez.am.client.heartbeat.poll.interval.millis\" : \"6000\",\n" +
    "            \"tez.am.client.heartbeat.timeout.secs\" : \"90\"\n" +
    "          },\n" +
    "          \"properties_attributes\" : { }\n" +
    "        },\n" +
    "        {\n" +
    "          \"Config\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"stack_id\" : \"HDP-2.6\"\n" +
    "          },\n" +
    "          \"type\" : \"hive-site\",\n" +
    "          \"tag\" : \"version1502130841736\",\n" +
    "          \"version\" : 2,\n" +
    "          \"properties\" : {\n" +
    "            \"hive.metastore.sasl.enabled\" : \"false\",\n" +
    "            \"hive.metastore.server.max.threads\" : \"100000\",\n" +
    "            \"hive.metastore.uris\" : \"thrift://c6402.ambari.apache.org:9083\",\n" +
    "            \"hive.server2.allow.user.substitution\" : \"true\",\n" +
    "            \"hive.server2.authentication\" : \"NONE\",\n" +
    "            \"hive.server2.authentication.spnego.keytab\" : \"HTTP/_HOST@EXAMPLE.COM\",\n" +
    "            \"hive.server2.authentication.spnego.principal\" : \"/etc/security/keytabs/spnego.service.keytab\",\n" +
    "            \"hive.server2.enable.doAs\" : \"true\",\n" +
    "            \"hive.server2.support.dynamic.service.discovery\" : \"true\",\n" +
    "            \"hive.server2.thrift.http.path\" : \"cliservice\",\n" +
    "            \"hive.server2.thrift.http.port\" : \"10001\",\n" +
    "            \"hive.server2.thrift.max.worker.threads\" : \"500\",\n" +
    "            \"hive.server2.thrift.port\" : \"10000\",\n" +
    "            \"hive.server2.thrift.sasl.qop\" : \"auth\",\n" +
    "            \"hive.server2.transport.mode\" : \"http\",\n" +
    "            \"hive.server2.use.SSL\" : \"false\",\n" +
    "            \"hive.server2.zookeeper.namespace\" : \"hiveserver2\"\n" +
    "          },\n" +
    "          \"properties_attributes\" : {\n" +
    "            \"hidden\" : {\n" +
    "              \"javax.jdo.option.ConnectionPassword\" : \"HIVE_CLIENT,WEBHCAT_SERVER,HCAT,CONFIG_DOWNLOAD\"\n" +
    "            }\n" +
    "          }\n" +
    "        },\n" +
    "        {\n" +
    "          \"Config\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"stack_id\" : \"HDP-2.6\"\n" +
    "          },\n" +
    "          \"type\" : \"webhcat-site\",\n" +
    "          \"tag\" : \"version1502131111746\",\n" +
    "          \"version\" : 2,\n" +
    "          \"properties\" : {\n" +
    "            \"templeton.port\" : \"50111\",\n" +
    "            \"templeton.zookeeper.hosts\" : \"c6403.ambari.apache.org:2181,c6401.ambari.apache.org:2181,c6402.ambari.apache.org:2181\",\n" +
    "            \"webhcat.proxyuser.knox.groups\" : \"users\",\n" +
    "            \"webhcat.proxyuser.knox.hosts\" : \"*\",\n" +
    "            \"webhcat.proxyuser.root.groups\" : \"*\",\n" +
    "            \"webhcat.proxyuser.root.hosts\" : \"c6401.ambari.apache.org\"\n" +
    "          },\n" +
    "          \"properties_attributes\" : { }\n" +
    "        }\n" +
    "      ],\n" +
    "      \"createtime\" : 1502131110745,\n" +
    "      \"group_id\" : -1,\n" +
    "      \"group_name\" : \"Default\",\n" +
    "      \"hosts\" : [ ],\n" +
    "      \"is_cluster_compatible\" : true,\n" +
    "      \"is_current\" : true,\n" +
    "      \"service_config_version\" : 3,\n" +
    "      \"service_config_version_note\" : \"knox trusted proxy support\",\n" +
    "      \"service_name\" : \"HIVE\",\n" +
    "      \"stack_id\" : \"HDP-2.6\",\n" +
    "      \"user\" : \"admin\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/configurations/service_config_versions?service_name=OOZIE&service_config_version=3\",\n" +
    "      \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "      \"configurations\" : [\n" +
    "        {\n" +
    "          \"Config\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"stack_id\" : \"HDP-2.6\"\n" +
    "          },\n" +
    "          \"type\" : \"oozie-site\",\n" +
    "          \"tag\" : \"version1502131137103\",\n" +
    "          \"version\" : 3,\n" +
    "          \"properties\" : {\n" +
    "            \"oozie.base.url\" : \"http://c6402.ambari.apache.org:11000/oozie\",\n" +
    "          },\n" +
    "          \"properties_attributes\" : { }\n" +
    "        }\n" +
    "      ],\n" +
    "      \"is_current\" : true,\n" +
    "      \"service_config_version\" : 3,\n" +
    "      \"service_name\" : \"OOZIE\",\n" +
    "      \"stack_id\" : \"HDP-2.6\",\n" +
    "      \"user\" : \"admin\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/configurations/service_config_versions?service_name=DISCOVERYTEST&service_config_version=3\",\n" +
    "      \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "      \"configurations\" : [\n" +
    "        {\n" +
    "          \"Config\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"stack_id\" : \"HDP-2.6\"\n" +
    "          },\n" +
    "          \"type\" : \"test-site\",\n" +
    "          \"tag\" : \"version1502131137103\",\n" +
    "          \"version\" : 3,\n" +
    "          \"properties\" : {\n" +
    "            \"discovery.test.base.url\" : \"http://c6402.ambari.apache.org:11999\",\n" +
    "          },\n" +
    "          \"properties_attributes\" : { }\n" +
    "        }\n" +
    "      ],\n" +
    "      \"is_current\" : true,\n" +
    "      \"service_config_version\" : 3,\n" +
    "      \"service_name\" : \"DISCOVERYTEST\",\n" +
    "      \"stack_id\" : \"HDP-2.6\",\n" +
    "      \"user\" : \"admin\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/configurations/service_config_versions?service_name=TEZ&service_config_version=1\",\n" +
    "      \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "      \"configurations\" : [\n" +
    "        {\n" +
    "          \"Config\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"stack_id\" : \"HDP-2.6\"\n" +
    "          },\n" +
    "          \"type\" : \"tez-site\",\n" +
    "          \"tag\" : \"version1\",\n" +
    "          \"version\" : 1,\n" +
    "          \"properties\" : {\n" +
    "            \"tez.use.cluster.hadoop-libs\" : \"false\"\n" +
    "          },\n" +
    "          \"properties_attributes\" : { }\n" +
    "        }\n" +
    "      ],\n" +
    "      \"createtime\" : 1502122253525,\n" +
    "      \"group_id\" : -1,\n" +
    "      \"group_name\" : \"Default\",\n" +
    "      \"hosts\" : [ ],\n" +
    "      \"is_cluster_compatible\" : true,\n" +
    "      \"is_current\" : true,\n" +
    "      \"service_config_version\" : 1,\n" +
    "      \"service_config_version_note\" : \"Initial configurations for Tez\",\n" +
    "      \"service_name\" : \"TEZ\",\n" +
    "      \"stack_id\" : \"HDP-2.6\",\n" +
    "      \"user\" : \"admin\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"href\" : \"http://c6401.ambari.apache.org:8080/api/v1/clusters/"+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"/configurations/service_config_versions?service_name=YARN&service_config_version=1\",\n" +
    "      \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "      \"configurations\" : [\n" +
    "        {\n" +
    "          \"Config\" : {\n" +
    "            \"cluster_name\" : \""+TestAmbariServiceDiscovery.CLUSTER_PLACEHOLDER+"\",\n" +
    "            \"stack_id\" : \"HDP-2.6\"\n" +
    "          },\n" +
    "          \"type\" : \"yarn-site\",\n" +
    "          \"tag\" : \"version1\",\n" +
    "          \"version\" : 1,\n" +
    "          \"properties\" : {\n" +
    "            \"hadoop.registry.rm.enabled\" : \"true\",\n" +
    "            \"hadoop.registry.zk.quorum\" : \"c6403.ambari.apache.org:2181,c6401.ambari.apache.org:2181,c6402.ambari.apache.org:2181\",\n" +
    "            \"yarn.acl.enable\" : \"false\",\n" +
    "            \"yarn.http.policy\" : \"HTTP_ONLY\",\n" +
    "            \"yarn.nodemanager.address\" : \"0.0.0.0:45454\",\n" +
    "            \"yarn.nodemanager.bind-host\" : \"0.0.0.0\",\n" +
    "            \"yarn.resourcemanager.address\" : \"c6402.ambari.apache.org:8050\",\n" +
    "            \"yarn.resourcemanager.admin.address\" : \"c6402.ambari.apache.org:8141\",\n" +
    "            \"yarn.resourcemanager.ha.enabled\" : \"false\",\n" +
    "            \"yarn.resourcemanager.hostname\" : \"c6402.ambari.apache.org\",\n" +
    "            \"yarn.resourcemanager.resource-tracker.address\" : \"c6402.ambari.apache.org:8025\",\n" +
    "            \"yarn.resourcemanager.scheduler.address\" : \"c6402.ambari.apache.org:8030\",\n" +
    "            \"yarn.resourcemanager.webapp.address\" : \"c6402.ambari.apache.org:8088\",\n" +
    "            \"yarn.resourcemanager.webapp.delegation-token-auth-filter.enabled\" : \"false\",\n" +
    "            \"yarn.resourcemanager.webapp.https.address\" : \"c6402.ambari.apache.org:8090\",\n" +
    "            \"yarn.resourcemanager.zk-address\" : \"c6403.ambari.apache.org:2181,c6401.ambari.apache.org:2181,c6402.ambari.apache.org:2181\"\n" +
    "          },\n" +
    "          \"properties_attributes\" : { }\n" +
    "        }\n" +
    "      ],\n" +
    "      \"is_current\" : true,\n" +
    "      \"service_config_version\" : 1,\n" +
    "      \"service_name\" : \"YARN\",\n" +
    "      \"stack_id\" : \"HDP-2.6\",\n" +
    "      \"user\" : \"admin\"\n" +
    "    }\n" +
    "  ]\n" +
    "}";
}
