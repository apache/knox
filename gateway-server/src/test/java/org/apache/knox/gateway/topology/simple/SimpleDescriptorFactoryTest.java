/**
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
package org.apache.knox.gateway.topology.simple;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.*;

import org.junit.Test;
import static org.junit.Assert.*;


public class SimpleDescriptorFactoryTest {


    @Test
    public void testParseJSONSimpleDescriptor() throws Exception {

        final String   discoveryType    = "AMBARI";
        final String   discoveryAddress = "http://c6401.ambari.apache.org:8080";
        final String   discoveryUser    = "admin";
        final String   providerConfig   = "ambari-cluster-policy.xml";
        final String   clusterName      = "myCluster";

        final Map<String, List<String>> services = new HashMap<>();
        services.put("NODEMANAGER", null);
        services.put("JOBTRACKER", null);
        services.put("RESOURCEMANAGER", null);
        services.put("HIVE", Arrays.asList("http://c6401.ambari.apache.org", "http://c6402.ambari.apache.org", "http://c6403.ambari.apache.org"));
        services.put("AMBARIUI", Arrays.asList("http://c6401.ambari.apache.org:8080"));

        String fileName = "test-topology.json";
        File testJSON = null;
        try {
            testJSON = writeJSON(fileName, discoveryType, discoveryAddress, discoveryUser, providerConfig, clusterName, services);
            SimpleDescriptor sd = SimpleDescriptorFactory.parse(testJSON.getAbsolutePath());
            validateSimpleDescriptor(sd, discoveryType, discoveryAddress, providerConfig, clusterName, services);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (testJSON != null) {
                try {
                    testJSON.delete();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    @Test
    public void testParseYAMLSimpleDescriptor() throws Exception {

        final String   discoveryType    = "AMBARI";
        final String   discoveryAddress = "http://c6401.ambari.apache.org:8080";
        final String   discoveryUser    = "joeblow";
        final String   providerConfig   = "ambari-cluster-policy.xml";
        final String   clusterName      = "myCluster";

        final Map<String, List<String>> services = new HashMap<>();
        services.put("NODEMANAGER", null);
        services.put("JOBTRACKER", null);
        services.put("RESOURCEMANAGER", null);
        services.put("HIVE", Arrays.asList("http://c6401.ambari.apache.org", "http://c6402.ambari.apache.org", "http://c6403.ambari.apache.org"));
        services.put("AMBARIUI", Arrays.asList("http://c6401.ambari.apache.org:8080"));

        String fileName = "test-topology.yml";
        File testYAML = null;
        try {
            testYAML = writeYAML(fileName, discoveryType, discoveryAddress, discoveryUser, providerConfig, clusterName, services);
            SimpleDescriptor sd = SimpleDescriptorFactory.parse(testYAML.getAbsolutePath());
            validateSimpleDescriptor(sd, discoveryType, discoveryAddress, providerConfig, clusterName, services);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (testYAML != null) {
                try {
                    testYAML.delete();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }


    private void validateSimpleDescriptor(SimpleDescriptor    sd,
                                          String              discoveryType,
                                          String              discoveryAddress,
                                          String              providerConfig,
                                          String              clusterName,
                                          Map<String, List<String>> expectedServices) {
        assertNotNull(sd);
        assertEquals(discoveryType, sd.getDiscoveryType());
        assertEquals(discoveryAddress, sd.getDiscoveryAddress());
        assertEquals(providerConfig, sd.getProviderConfig());
        assertEquals(clusterName, sd.getClusterName());

        List<SimpleDescriptor.Service> actualServices = sd.getServices();

        assertEquals(expectedServices.size(), actualServices.size());

        for (SimpleDescriptor.Service actualService : actualServices) {
            assertTrue(expectedServices.containsKey(actualService.getName()));
            assertEquals(expectedServices.get(actualService.getName()), actualService.getURLs());
        }
    }


    private File writeJSON(String path, String content) throws Exception {
        File f = new File(path);

        Writer fw = new FileWriter(f);
        fw.write(content);
        fw.flush();
        fw.close();

        return f;
    }


    private File writeJSON(String path,
                           String discoveryType,
                           String discoveryAddress,
                           String discoveryUser,
                           String providerConfig,
                           String clusterName,
                           Map<String, List<String>> services) throws Exception {
        File f = new File(path);

        Writer fw = new FileWriter(f);
        fw.write("{" + "\n");
        fw.write("\"discovery-type\":\"" + discoveryType + "\",\n");
        fw.write("\"discovery-address\":\"" + discoveryAddress + "\",\n");
        fw.write("\"discovery-user\":\"" + discoveryUser + "\",\n");
        fw.write("\"provider-config-ref\":\"" + providerConfig + "\",\n");
        fw.write("\"cluster\":\"" + clusterName + "\",\n");
        fw.write("\"services\":[\n");

        int i = 0;
        for (String name : services.keySet()) {
            fw.write("{\"name\":\"" + name + "\"");
            List<String> urls = services.get(name);
            if (urls != null) {
                fw.write(", \"urls\":[");
                Iterator<String> urlIter = urls.iterator();
                while (urlIter.hasNext()) {
                    fw.write("\"" + urlIter.next() + "\"");
                    if (urlIter.hasNext()) {
                        fw.write(", ");
                    }
                }
                fw.write("]");
            }
            fw.write("}");
            if (i++ < services.size() - 1) {
                fw.write(",");
            }
            fw.write("\n");
        }
        fw.write("]\n");
        fw.write("}\n");
        fw.flush();
        fw.close();

        return f;
    }

    private File writeYAML(String path,
                           String discoveryType,
                           String discoveryAddress,
                           String discoveryUser,
                           String providerConfig,
                           String clusterName,
                           Map<String, List<String>> services) throws Exception {
        File f = new File(path);

        Writer fw = new FileWriter(f);
        fw.write("---" + "\n");
        fw.write("discovery-type: " + discoveryType + "\n");
        fw.write("discovery-address: " + discoveryAddress + "\n");
        fw.write("discovery-user: " + discoveryUser + "\n");
        fw.write("provider-config-ref: " + providerConfig + "\n");
        fw.write("cluster: " + clusterName+ "\n");
        fw.write("services:\n");
        for (String name : services.keySet()) {
            fw.write("    - name: " + name + "\n");
            List<String> urls = services.get(name);
            if (urls != null) {
                fw.write("      urls:\n");
                for (String url : urls) {
                    fw.write("          - " + url + "\n");
                }
            }
        }
        fw.flush();
        fw.close();

        return f;
    }


}
