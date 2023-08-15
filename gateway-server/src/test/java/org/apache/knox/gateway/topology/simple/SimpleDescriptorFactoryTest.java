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
package org.apache.knox.gateway.topology.simple;

import org.junit.Test;

import java.io.File;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SimpleDescriptorFactoryTest {

    private enum FileType {
        JSON,
        YAML,
        YML
    }

    @Test
    public void testParseJSONSimpleDescriptor() throws Exception {
        testParseSimpleDescriptor(FileType.JSON, false);
        testParseSimpleDescriptor(FileType.JSON, true);
    }

    @Test
    public void testParseYAMLSimpleDescriptor() throws Exception {
        testParseSimpleDescriptor(FileType.YML, true);
        testParseSimpleDescriptor(FileType.YAML, false);
        testParseSimpleDescriptor(FileType.YAML, true);
    }

    @Test
    public void testParseJSONSimpleDescriptorWithServiceVersions() throws Exception {
        testParseSimpleDescriptorWithServiceVersions(FileType.JSON);
    }

    @Test
    public void testParseYAMLSimpleDescriptorWithServiceVersions() throws Exception {
        testParseSimpleDescriptorWithServiceVersions(FileType.YML);
        testParseSimpleDescriptorWithServiceVersions(FileType.YAML);
    }

    @Test
    public void testParseJSONSimpleDescriptorWithServiceParams() throws Exception {
        testParseSimpleDescriptorWithServiceParams(FileType.JSON);
    }

    @Test
    public void testParseYAMLSimpleDescriptorWithServiceParams() throws Exception {
        testParseSimpleDescriptorWithServiceParams(FileType.YML);
        testParseSimpleDescriptorWithServiceParams(FileType.YAML);
    }

    @Test
    public void testParseJSONSimpleDescriptorWithApplications() throws Exception {
        testParseSimpleDescriptorWithApplications(FileType.JSON);
    }

    @Test
    public void testParseYAMLSimpleDescriptorApplications() throws Exception {
        testParseSimpleDescriptorWithApplications(FileType.YML);
        testParseSimpleDescriptorWithApplications(FileType.YAML);
    }


    @Test
    public void testParseJSONSimpleDescriptorWithServicesAndApplications() throws Exception {
        testParseSimpleDescriptorWithServicesAndApplications(FileType.JSON);
    }

    @Test
    public void testParseYAMLSimpleDescriptorWithServicesAndApplications() throws Exception {
        testParseSimpleDescriptorWithServicesAndApplications(FileType.YML);
        testParseSimpleDescriptorWithServicesAndApplications(FileType.YAML);
    }


    private void testParseSimpleDescriptor(FileType type, boolean provisionEncryptQueryStringCredential) throws Exception {
        final String   discoveryType    = "AMBARI";
        final String   discoveryAddress = "http://c6401.ambari.apache.org:8080";
        final String   discoveryUser    = "joeblow";
        final String   providerConfig   = "ambari-cluster-policy.xml";
        final String   clusterName      = "myCluster";

        final Map<String, List<String>> services = new HashMap<>();
        services.put("NODEMANAGER", null);
        services.put("JOBTRACKER", null);
        services.put("RESOURCEMANAGER", null);
        services.put("HIVE", Arrays.asList("http://c6401.ambari.apache.org",
                                           "http://c6402.ambari.apache.org",
                                           "http://c6403.ambari.apache.org"));
        services.put("AMBARIUI", Collections.singletonList("http://c6401.ambari.apache.org:8080"));

        String fileName = "test-topology." + getFileExtensionForType(type);
        File testFile = null;
        try {
            testFile = writeDescriptorFile(type,
                                           fileName,
                                           discoveryType,
                                           discoveryAddress,
                                           discoveryUser,
                                           providerConfig,
                                           clusterName,
                                           provisionEncryptQueryStringCredential,
                                           services);
            SimpleDescriptor sd = SimpleDescriptorFactory.parse(testFile.getAbsolutePath());
            validateSimpleDescriptor(sd, discoveryType, discoveryAddress, providerConfig, clusterName, provisionEncryptQueryStringCredential, services);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (testFile != null) {
                try {
                    testFile.delete();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    private void testParseSimpleDescriptorWithServiceVersions(FileType type) throws Exception {
        final String   discoveryType    = "AMBARI";
        final String   discoveryAddress = "http://c6401.ambari.apache.org:8080";
        final String   discoveryUser    = "joeblow";
        final String   providerConfig   = "ambari-cluster-policy.xml";
        final String   clusterName      = "myCluster";

        final Map<String, List<String>> services = new HashMap<>();
        services.put("NODEMANAGER", null);
        services.put("JOBTRACKER", null);
        services.put("RESOURCEMANAGER", null);
        services.put("WEBHDFS", null);
        services.put("HIVE", Arrays.asList("http://c6401.ambari.apache.org",
                                           "http://c6402.ambari.apache.org",
                                           "http://c6403.ambari.apache.org"));
        services.put("AMBARIUI", Collections.singletonList("http://c6401.ambari.apache.org:8080"));

        Map<String, String> serviceVersions = new HashMap<>();
        serviceVersions.put("HIVE", "0.13.0");
        serviceVersions.put("WEBHDFS", "2.4.0");

        String fileName = "test-topology." + getFileExtensionForType(type);
        File testFile = null;
        try {
            testFile = writeDescriptorFile(type,
                                           fileName,
                                           discoveryType,
                                           discoveryAddress,
                                           discoveryUser,
                                           providerConfig,
                                           clusterName,
                                           true,
                                           services,
                                           serviceVersions);
            SimpleDescriptor sd = SimpleDescriptorFactory.parse(testFile.getAbsolutePath());
            validateSimpleDescriptor(sd, discoveryType, discoveryAddress, providerConfig, clusterName, true, services, serviceVersions);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (testFile != null) {
                try {
                    testFile.delete();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }


    private void testParseSimpleDescriptorWithServiceParams(FileType type) throws Exception {

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

        String fileName = "test-topology." + getFileExtensionForType(type);
        File testFile = null;
        try {
            testFile = writeDescriptorFile(type,
                                           fileName,
                                           discoveryType,
                                           discoveryAddress,
                                           discoveryUser,
                                           providerConfig,
                                           clusterName,
                                           true,
                                           services,
                                           null,
                                           serviceParams);
            SimpleDescriptor sd = SimpleDescriptorFactory.parse(testFile.getAbsolutePath());
            validateSimpleDescriptor(sd, discoveryType, discoveryAddress, providerConfig, clusterName, true, services, null, serviceParams);
        } finally {
            if (testFile != null) {
                try {
                    testFile.delete();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    private void testParseSimpleDescriptorWithApplications(FileType type) throws Exception {

        final String   discoveryType    = "AMBARI";
        final String   discoveryAddress = "http://c6401.ambari.apache.org:8080";
        final String   discoveryUser    = "admin";
        final String   providerConfig   = "ambari-cluster-policy.xml";
        final String   clusterName      = "myCluster";

        final Map<String, List<String>> apps = new HashMap<>();
        apps.put("app-one", null);
        apps.put("appTwo", null);
        apps.put("thirdApps", null);
        apps.put("appfour", Arrays.asList("http://host1:1234", "http://host2:5678", "http://host1:1357"));
        apps.put("AppFive", Collections.singletonList("http://host5:8080"));

        final Map<String, Map<String, String>> appParams = new HashMap<>();
        Map<String, String> oneParams = new HashMap<>();
        oneParams.put("appone.cookie.secure.only", "true");
        oneParams.put("appone.token.ttl", "100000");
        appParams.put("app-one", oneParams);
        Map<String, String> fiveParams = new HashMap<>();
        fiveParams.put("myproperty", "true");
        fiveParams.put("anotherparam", "100000");
        appParams.put("AppFive", fiveParams);

        String fileName = "test-topology." + getFileExtensionForType(type);
        File testFile = null;
        try {
            testFile = writeDescriptorFile(type,
                                           fileName,
                                           discoveryType,
                                           discoveryAddress,
                                           discoveryUser,
                                           providerConfig,
                                           clusterName,
                                           true,
                                           null,
                                           null,
                                           null,
                                           apps,
                                           appParams);
            SimpleDescriptor sd = SimpleDescriptorFactory.parse(testFile.getAbsolutePath());
            validateSimpleDescriptor(sd,
                                     discoveryType,
                                     discoveryAddress,
                                     providerConfig,
                                     clusterName,
                                     true,
                                     null,
                                     null,
                                     null,
                                     apps,
                                     appParams);
        } finally {
            if (testFile != null) {
                try {
                    testFile.delete();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    private void testParseSimpleDescriptorWithServicesAndApplications(FileType type) throws Exception {

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

        final Map<String, List<String>> apps = new HashMap<>();
        apps.put("app-one", null);
        apps.put("appTwo", null);
        apps.put("thirdApps", null);
        apps.put("appfour", Arrays.asList("http://host1:1234", "http://host2:5678", "http://host1:1357"));
        apps.put("AppFive", Collections.singletonList("http://host5:8080"));

        final Map<String, Map<String, String>> appParams = new HashMap<>();
        Map<String, String> oneParams = new HashMap<>();
        oneParams.put("appone.cookie.secure.only", "true");
        oneParams.put("appone.token.ttl", "100000");
        appParams.put("app-one", oneParams);
        Map<String, String> fiveParams = new HashMap<>();
        fiveParams.put("myproperty", "true");
        fiveParams.put("anotherparam", "100000");
        appParams.put("AppFive", fiveParams);

        String fileName = "test-topology." + getFileExtensionForType(type);
        File testFile = null;
        try {
            testFile = writeDescriptorFile(type,
                                           fileName,
                                           discoveryType,
                                           discoveryAddress,
                                           discoveryUser,
                                           providerConfig,
                                           clusterName,
                                           true,
                                           services,
                                           null,
                                           serviceParams,
                                           apps,
                                           appParams);
            SimpleDescriptor sd = SimpleDescriptorFactory.parse(testFile.getAbsolutePath());
            validateSimpleDescriptor(sd,
                                     discoveryType,
                                     discoveryAddress,
                                     providerConfig,
                                     clusterName,
                                     true,
                                     services,
                                     null,
                                     serviceParams,
                                     apps,
                                     appParams);
        } finally {
            if (testFile != null) {
                try {
                    testFile.delete();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    private String getFileExtensionForType(FileType type) {
        String extension = null;
        switch (type) {
            case JSON:
                extension = "json";
                break;
            case YML:
                extension = "yml";
                break;
            case YAML:
                extension = "yaml";
                break;
        }
        return extension;
    }

    private File writeDescriptorFile(FileType                  type,
                                     String                    path,
                                     String                    discoveryType,
                                     String                    discoveryAddress,
                                     String                    discoveryUser,
                                     String                    providerConfig,
                                     String                    clusterName,
                                     boolean provisionEncryptQueryStringCredential,
                                     Map<String, List<String>> services) throws Exception {
        return writeDescriptorFile(type,
                                   path,
                                   discoveryType,
                                   discoveryAddress,
                                   discoveryUser,
                                   providerConfig,
                                   clusterName,
                                   provisionEncryptQueryStringCredential,
                                   services,
                                   null);
    }

    private File writeDescriptorFile(FileType                  type,
                                     String                    path,
                                     String                    discoveryType,
                                     String                    discoveryAddress,
                                     String                    discoveryUser,
                                     String                    providerConfig,
                                     String                    clusterName,
                                     boolean provisionEncryptQueryStringCredential,
                                     Map<String, List<String>> services,
                                     Map<String, String>       serviceVersions) throws Exception {
        return writeDescriptorFile(type,
                                   path,
                                   discoveryType,
                                   discoveryAddress,
                                   discoveryUser,
                                   providerConfig,
                                   clusterName,
                                   provisionEncryptQueryStringCredential,
                                   services,
                                   serviceVersions,
                                   null);
    }

    private File writeDescriptorFile(FileType                         type,
                                     String                           path,
                                     String                           discoveryType,
                                     String                           discoveryAddress,
                                     String                           discoveryUser,
                                     String                           providerConfig,
                                     String                           clusterName,
                                     boolean provisionEncryptQueryStringCredential,
                                     Map<String, List<String>>        services,
                                     Map<String, String>              serviceVersions,
                                     Map<String, Map<String, String>> serviceParams) throws Exception {
        return writeDescriptorFile(type,
                                   path,
                                   discoveryType,
                                   discoveryAddress,
                                   discoveryUser,
                                   providerConfig,
                                   clusterName,
                                   provisionEncryptQueryStringCredential,
                                   services,
                                   serviceVersions,
                                   serviceParams,
                                   null,
                                   null);
    }


    private File writeDescriptorFile(FileType                         type,
                                     String                           path,
                                     String                           discoveryType,
                                     String                           discoveryAddress,
                                     String                           discoveryUser,
                                     String                           providerConfig,
                                     String                           clusterName,
                                     boolean provisionEncryptQueryStringCredential,
                                     Map<String, List<String>>        services,
                                     Map<String, String>              serviceVersions,
                                     Map<String, Map<String, String>> serviceParams,
                                     Map<String, List<String>>        apps,
                                     Map<String, Map<String, String>> appParams) throws Exception {
        File result = null;
        switch (type) {
            case JSON:
                result = writeJSON(path,
                                   discoveryType,
                                   discoveryAddress,
                                   discoveryUser,
                                   providerConfig,
                                   clusterName,
                                   provisionEncryptQueryStringCredential,
                                   services,
                                   serviceVersions,
                                   serviceParams,
                                   apps,
                                   appParams);
                break;
            case YAML:
            case YML:
                result = writeYAML(path,
                                   discoveryType,
                                   discoveryAddress,
                                   discoveryUser,
                                   providerConfig,
                                   clusterName,
                                   provisionEncryptQueryStringCredential,
                                   services,
                                   serviceVersions,
                                   serviceParams,
                                   apps,
                                   appParams);
                break;
        }
        return result;
    }


    private File writeJSON(String                           path,
                           String                           discoveryType,
                           String                           discoveryAddress,
                           String                           discoveryUser,
                           String                           providerConfig,
                           String                           clusterName,
                           boolean provisionEncryptQueryStringCredential,
                           Map<String, List<String>>        services,
                           Map<String, String>              serviceVersions,
                           Map<String, Map<String, String>> serviceParams,
                           Map<String, List<String>>        apps,
                           Map<String, Map<String, String>> appParams) throws Exception {
      Path pathObject = Paths.get(path);
      try (OutputStream outputStream = Files.newOutputStream(pathObject);
           Writer fw = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
        fw.write("{" + "\n");
        fw.write("\"discovery-type\":\"" + discoveryType + "\",\n");
        fw.write("\"discovery-address\":\"" + discoveryAddress + "\",\n");
        fw.write("\"discovery-user\":\"" + discoveryUser + "\",\n");
        fw.write("\"provider-config-ref\":\"" + providerConfig + "\",\n");
        fw.write("\"cluster\":\"" + clusterName + "\"");
        if (!provisionEncryptQueryStringCredential) {
          fw.write("\"provision-encrypt-query-string-credential\":\"" + provisionEncryptQueryStringCredential + "\"");
        }

        if (services != null && !services.isEmpty()) {
          fw.write(",\n\"services\":[\n");
          writeServiceOrApplicationJSON(fw, services, serviceParams, serviceVersions);
          fw.write("]\n");
        }

        if (apps != null && !apps.isEmpty()) {
          fw.write(",\n\"applications\":[\n");
          writeServiceOrApplicationJSON(fw, apps, appParams, null);
          fw.write("]\n");
        }

        fw.write("}\n");
        fw.flush();
      }
      return pathObject.toFile();
    }

    private void writeServiceOrApplicationJSON(Writer fw,
                                               Map<String, List<String>>        elementURLs,
                                               Map<String, Map<String, String>> elementParams,
                                               Map<String, String>              serviceVersions) throws Exception {
        if (elementURLs != null) {
            int i = 0;
            for (String name : elementURLs.keySet()) {
                fw.write("{\"name\":\"" + name + "\"");

                if (serviceVersions != null) {
                    String ver = serviceVersions.get(name);
                    if (ver != null) {
                        fw.write(",\n\"version\":\"" + ver + "\"");
                    }
                }

                // Service params
                if (elementParams != null && !elementParams.isEmpty()) {
                    Map<String, String> params = elementParams.get(name);
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
                List<String> urls = elementURLs.get(name);
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
                if (i++ < elementURLs.size() - 1) {
                    fw.write(",");
                }
                fw.write("\n");
            }
        }
    }

    private File writeYAML(String                           path,
                           String                           discoveryType,
                           String                           discoveryAddress,
                           String                           discoveryUser,
                           String                           providerConfig,
                           String                           clusterName,
                           boolean provisionEncryptQueryStringCredential,
                           Map<String, List<String>>        services,
                           Map<String, String>              serviceVersions,
                           Map<String, Map<String, String>> serviceParams,
                           Map<String, List<String>>        apps,
                           Map<String, Map<String, String>> appParams) throws Exception {

      Path pathObject = Paths.get(path);
      try (OutputStream outputStream = Files.newOutputStream(pathObject);
           Writer fw = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
        fw.write("---" + "\n");
        fw.write("discovery-type: " + discoveryType + "\n");
        fw.write("discovery-address: " + discoveryAddress + "\n");
        fw.write("discovery-user: " + discoveryUser + "\n");
        fw.write("provider-config-ref: " + providerConfig + "\n");
        fw.write("cluster: " + clusterName + "\n");
        if (!provisionEncryptQueryStringCredential) {
          fw.write("provision-encrypt-query-string-credential: " + provisionEncryptQueryStringCredential + "\n");
        }

        if (services != null && !services.isEmpty()) {
          fw.write("services:\n");
          writeServiceOrApplicationYAML(fw, services, serviceParams, serviceVersions);
        }

        if (apps != null && !apps.isEmpty()) {
          fw.write("applications:\n");
          writeServiceOrApplicationYAML(fw, apps, appParams, null);
        }

        fw.flush();
      }
      return pathObject.toFile();
    }

    private void writeServiceOrApplicationYAML(Writer                           fw,
                                               Map<String, List<String>>        elementURLs,
                                               Map<String, Map<String, String>> elementParams,
                                               Map<String, String>              serviceVersions) throws Exception {
        for (String name : elementURLs.keySet()) {
            fw.write("    - name: " + name + "\n");

            if (serviceVersions != null) {
                String ver = serviceVersions.get(name);
                if (ver != null) {
                    fw.write("      version: " + ver + "\n");
                }
            }

            // Service params
            if (elementParams != null && !elementParams.isEmpty()) {
                if (elementParams.containsKey(name)) {
                    Map<String, String> params = elementParams.get(name);
                    fw.write("      params:\n");
                    for (String paramName : params.keySet()) {
                        fw.write("            " + paramName + ": " + params.get(paramName) + "\n");
                    }
                }
            }

            // Service URLs
            List<String> urls = elementURLs.get(name);
            if (urls != null) {
                fw.write("      urls:\n");
                for (String url : urls) {
                    fw.write("          - " + url + "\n");
                }
            }
        }
    }


    private void validateSimpleDescriptor(SimpleDescriptor          sd,
                                          String                    discoveryType,
                                          String                    discoveryAddress,
                                          String                    providerConfig,
                                          String                    clusterName,
                                          boolean provisionEncryptQueryStringCredential,
                                          Map<String, List<String>> expectedServices) {
        validateSimpleDescriptor(sd, discoveryType, discoveryAddress, providerConfig, clusterName, provisionEncryptQueryStringCredential, expectedServices, null);
    }


    private void validateSimpleDescriptor(SimpleDescriptor          sd,
                                          String                    discoveryType,
                                          String                    discoveryAddress,
                                          String                    providerConfig,
                                          String                    clusterName,
                                          boolean provisionEncryptQueryStringCredential,
                                          Map<String, List<String>> expectedServices,
                                          Map<String, String>       expectedServiceVersions) {
        validateSimpleDescriptor(sd, discoveryType, discoveryAddress, providerConfig, clusterName, provisionEncryptQueryStringCredential, expectedServices, expectedServiceVersions, null);
    }


    private void validateSimpleDescriptor(SimpleDescriptor                 sd,
                                          String                           discoveryType,
                                          String                           discoveryAddress,
                                          String                           providerConfig,
                                          String                           clusterName,
                                          boolean provisionEncryptQueryStringCredential,
                                          Map<String, List<String>>        expectedServices,
                                          Map<String, String>              expectedServiceVersions,
                                          Map<String, Map<String, String>> expectedServiceParameters) {
        validateSimpleDescriptor(sd,
                                 discoveryType,
                                 discoveryAddress,
                                 providerConfig,
                                 clusterName,
                                 provisionEncryptQueryStringCredential,
                                 expectedServices,
                                 expectedServiceVersions,
                                 expectedServiceParameters,
                                 null,
                                 null);
    }

    private void validateSimpleDescriptor(SimpleDescriptor                 sd,
                                          String                           discoveryType,
                                          String                           discoveryAddress,
                                          String                           providerConfig,
                                          String                           clusterName,
                                          boolean provisionEncryptQueryStringCredential,
                                          Map<String, List<String>>        expectedServices,
                                          Map<String, String>              expectedServiceVersions,
                                          Map<String, Map<String, String>> expectedServiceParameters,
                                          Map<String, List<String>>        expectedApps,
                                          Map<String, Map<String, String>> expectedAppParameters) {
        assertNotNull(sd);
        assertEquals(discoveryType, sd.getDiscoveryType());
        assertEquals(discoveryAddress, sd.getDiscoveryAddress());
        assertEquals(providerConfig, sd.getProviderConfig());
        assertEquals(clusterName, sd.getCluster());
        assertEquals(provisionEncryptQueryStringCredential, sd.isProvisionEncryptQueryStringCredential());

        List<SimpleDescriptor.Service> actualServices = sd.getServices();

        if (expectedServices == null) {
            assertTrue(actualServices.isEmpty());
        } else {
            assertEquals(expectedServices.size(), actualServices.size());

            for (SimpleDescriptor.Service actualService : actualServices) {
                assertTrue(expectedServices.containsKey(actualService.getName()));
                assertEquals(expectedServices.get(actualService.getName()), actualService.getURLs());

                if (expectedServiceVersions != null) {
                    String expectedVersion = expectedServiceVersions.get(actualService.getName());
                    if (expectedVersion != null) {
                        String actualVersion = actualService.getVersion();
                        assertNotNull(actualVersion);
                        assertEquals("Unexpected version for " + actualService.getName(),
                                     expectedVersion,
                                     actualVersion);
                    }
                }

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

        List<SimpleDescriptor.Application> actualApps = sd.getApplications();

        if (expectedApps == null) {
            assertTrue(actualApps.isEmpty());
        } else {
            assertEquals(expectedApps.size(), actualApps.size());

            for (SimpleDescriptor.Application actualApp : actualApps) {
                assertTrue(expectedApps.containsKey(actualApp.getName()));
                assertEquals(expectedApps.get(actualApp.getName()), actualApp.getURLs());

                // Validate service parameters
                if (expectedServiceParameters != null) {
                    if (expectedAppParameters.containsKey(actualApp.getName())) {
                        Map<String, String> expectedParams = expectedAppParameters.get(actualApp.getName());

                        Map<String, String> actualAppParams = actualApp.getParams();
                        assertNotNull(actualAppParams);

                        // Validate the size of the service parameter set
                        assertEquals(expectedParams.size(), actualAppParams.size());

                        // Validate the parameter contents
                        for (String paramName : actualAppParams.keySet()) {
                            assertTrue(expectedParams.containsKey(paramName));
                            assertEquals(expectedParams.get(paramName), actualAppParams.get(paramName));
                        }
                    }
                }
            }
        }
    }

}
