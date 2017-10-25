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
    public void testParseJSONSimpleDescriptorWithServiceParams() throws Exception {

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
        services.put("AMBARIUI", Collections.singletonList("http://c6401.ambari.apache.org:8080"));
        services.put("KNOXSSO", null);
        services.put("KNOXTOKEN", null);
        services.put("CustomRole", Collections.singletonList("http://c6402.ambari.apache.org:1234"));

        final Map<String, Map<String, String>> serviceParams = new HashMap<>();
        Map<String, String> knoxSSOParams = new HashMap<>();
        knoxSSOParams.put("knoxsso.cookie.secure.only", "true");
        knoxSSOParams.put("knoxsso.token.ttl", "100000");
        serviceParams.put("KNOXSSO", knoxSSOParams);

        Map<String, String> knoxTokenParams = new HashMap<>();
        knoxTokenParams.put("knox.token.ttl", "36000000");
        knoxTokenParams.put("knox.token.audiences", "tokenbased");
        knoxTokenParams.put("knox.token.target.url", "https://localhost:8443/gateway/tokenbased");
        serviceParams.put("KNOXTOKEN", knoxTokenParams);

        Map<String, String> customRoleParams = new HashMap<>();
        customRoleParams.put("custom.param.1", "value1");
        customRoleParams.put("custom.param.2", "value2");
        serviceParams.put("CustomRole", customRoleParams);

        String fileName = "test-topology.json";
        File testJSON = null;
        try {
            testJSON = writeJSON(fileName,
                                 discoveryType,
                                 discoveryAddress,
                                 discoveryUser,
                                 providerConfig,
                                 clusterName,
                                 services,
                                 serviceParams);
            SimpleDescriptor sd = SimpleDescriptorFactory.parse(testJSON.getAbsolutePath());
            validateSimpleDescriptor(sd, discoveryType, discoveryAddress, providerConfig, clusterName, services, serviceParams);
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


    @Test
    public void testParseYAMLSimpleDescriptorWithServiceParams() throws Exception {

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
        services.put("KNOXSSO", null);
        services.put("KNOXTOKEN", null);
        services.put("CustomRole", Collections.singletonList("http://c6402.ambari.apache.org:1234"));

        final Map<String, Map<String, String>> serviceParams = new HashMap<>();
        Map<String, String> knoxSSOParams = new HashMap<>();
        knoxSSOParams.put("knoxsso.cookie.secure.only", "true");
        knoxSSOParams.put("knoxsso.token.ttl", "100000");
        serviceParams.put("KNOXSSO", knoxSSOParams);

        Map<String, String> knoxTokenParams = new HashMap<>();
        knoxTokenParams.put("knox.token.ttl", "36000000");
        knoxTokenParams.put("knox.token.audiences", "tokenbased");
        knoxTokenParams.put("knox.token.target.url", "https://localhost:8443/gateway/tokenbased");
        serviceParams.put("KNOXTOKEN", knoxTokenParams);

        Map<String, String> customRoleParams = new HashMap<>();
        customRoleParams.put("custom.param.1", "value1");
        customRoleParams.put("custom.param.2", "value2");
        serviceParams.put("CustomRole", customRoleParams);

        String fileName = "test-topology.yml";
        File testYAML = null;
        try {
            testYAML = writeYAML(fileName, discoveryType, discoveryAddress, discoveryUser, providerConfig, clusterName, services, serviceParams);
            SimpleDescriptor sd = SimpleDescriptorFactory.parse(testYAML.getAbsolutePath());
            validateSimpleDescriptor(sd, discoveryType, discoveryAddress, providerConfig, clusterName, services, serviceParams);
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


    private void validateSimpleDescriptor(SimpleDescriptor          sd,
                                          String                    discoveryType,
                                          String                    discoveryAddress,
                                          String                    providerConfig,
                                          String                    clusterName,
                                          Map<String, List<String>> expectedServices) {
        validateSimpleDescriptor(sd, discoveryType, discoveryAddress, providerConfig, clusterName, expectedServices, null);
    }


    private void validateSimpleDescriptor(SimpleDescriptor                 sd,
                                          String                           discoveryType,
                                          String                           discoveryAddress,
                                          String                           providerConfig,
                                          String                           clusterName,
                                          Map<String, List<String>>        expectedServices,
                                          Map<String, Map<String, String>> expectedServiceParameters) {
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

            // Validate service parameters
            if (expectedServiceParameters != null) {
                if (expectedServiceParameters.containsKey(actualService.getName())) {
                    Map<String, String> expectedParams = expectedServiceParameters.get(actualService.getName());

                    Map<String, String> actualServiceParams = actualService.getParams();
                    assertNotNull(actualServiceParams);

                    // Validate the size of the service parameter set
                    assertEquals(expectedParams.size(), actualServiceParams.size());

                    // Validate the parameter contents
                    for (String paramName : actualServiceParams.keySet()) {
                        assertTrue(expectedParams.containsKey(paramName));
                        assertEquals(expectedParams.get(paramName), actualServiceParams.get(paramName));
                    }
                }
            }
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
        return writeJSON(path, discoveryType, discoveryAddress, discoveryUser, providerConfig, clusterName, services, null);
    }

    private File writeJSON(String path,
                           String discoveryType,
                           String discoveryAddress,
                           String discoveryUser,
                           String providerConfig,
                           String clusterName,
                           Map<String, List<String>> services,
                           Map<String, Map<String, String>> serviceParams) throws Exception {
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

            // Service params
            if (serviceParams != null && !serviceParams.isEmpty()) {
                Map<String, String> params = serviceParams.get(name);
                if (params != null && !params.isEmpty()) {
                    fw.write(",\n\"params\":{\n");
                    Iterator<String> paramNames = params.keySet().iterator();
                    while (paramNames.hasNext()) {
                        String paramName = paramNames.next();
                        String paramValue = params.get(paramName);
                        fw.write("\"" + paramName + "\":\"" + paramValue + "\"");
                        fw.write(paramNames.hasNext() ? ",\n" : "");
                    }
                    fw.write("\n}");
                }
            }

            // Service URLs
            List<String> urls = services.get(name);
            if (urls != null) {
                fw.write(",\n\"urls\":[");
                Iterator<String> urlIter = urls.iterator();
                while (urlIter.hasNext()) {
                    fw.write("\"" + urlIter.next() + "\"");
                    if (urlIter.hasNext()) {
                        fw.write(", ");
                    }
                }
                fw.write("]\n");
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


    private File writeYAML(String                    path,
                           String                    discoveryType,
                           String                    discoveryAddress,
                           String                    discoveryUser,
                           String                    providerConfig,
                           String                    clusterName,
                           Map<String, List<String>> services) throws Exception {
        return writeYAML(path, discoveryType, discoveryAddress, discoveryUser, providerConfig, clusterName, services, null);
    }


    private File writeYAML(String                           path,
                           String                           discoveryType,
                           String                           discoveryAddress,
                           String                           discoveryUser,
                           String                           providerConfig,
                           String                           clusterName,
                           Map<String, List<String>>        services,
                           Map<String, Map<String, String>> serviceParams) throws Exception {
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

            // Service params
            if (serviceParams != null && !serviceParams.isEmpty()) {
                if (serviceParams.containsKey(name)) {
                    Map<String, String> params = serviceParams.get(name);
                    fw.write("      params:\n");
                    for (String paramName : params.keySet()) {
                        fw.write("            " + paramName + ": " + params.get(paramName) + "\n");
                    }
                }
            }

            // Service URLs
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
