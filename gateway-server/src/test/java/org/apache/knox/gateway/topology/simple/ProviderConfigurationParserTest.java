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
package org.apache.knox.gateway.topology.simple;

import org.apache.commons.io.FileUtils;
import org.apache.knox.test.TestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ProviderConfigurationParserTest {

  private static File tmpDir;

  @BeforeClass
  public static void setUpBeforeClass() {
    try {
      tmpDir = TestUtils.createTempDir(ProviderConfigurationParser.class.getName());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @AfterClass
  public static void tearDownAfterClass() {
    if (tmpDir != null) {
      FileUtils.deleteQuietly(tmpDir);
    }
  }

  @Test
  public void testParseProviderConfigurationXML() throws Exception {
    final String XML =
    "<gateway>\n" +
    "  <provider>\n" +
    "    <role>TestNotEnabled</role>\n" +
    "    <name>TestProviderNotEnabled</name>\n" +
    "    <enabled>false</enabled>\n" +
    "  </provider>\n" +
    "  <provider>\n" +
    "    <role>TestEnabledNoParams</role>\n" +
    "    <name>TestProviderEnabledNoParams</name>\n" +
    "    <enabled>true</enabled>\n" +
    "  </provider>\n" +
    "  <provider>\n" +
    "    <role>TestEnabledWithParams</role>\n" +
    "    <name>TestProviderEnabledWithParams</name>\n" +
    "    <enabled>true</enabled>\n" +
    "    <param><name>param1</name><value>param1-value</value></param>\n" +
    "    <param><name>param2</name><value>param2-value</value></param>\n" +
    "    <param><name>param3</name><value>param3-value</value></param>\n" +
    "  </provider>\n" +
    "</gateway>\n";

    ProviderConfiguration pc = doTestParseProviderConfiguration(XML, "my-providers.xml");
    assertNotNull(pc);

    Set<ProviderConfiguration.Provider> providers = pc.getProviders();
    assertNotNull(providers);
    assertFalse(providers.isEmpty());
    assertEquals(3, providers.size());

    // Validate providers
    for (ProviderConfiguration.Provider provider : providers) {
      String role = provider.getRole();
      if ("TestNotEnabled".equals(role)) {
        assertEquals("TestProviderNotEnabled", provider.getName());
        assertFalse(provider.isEnabled());
        assertNotNull(provider.getParams());
        assertTrue(provider.getParams().isEmpty());
      } else if ("TestEnabledNoParams".equals(role)) {
        assertEquals("TestProviderEnabledNoParams", provider.getName());
        assertTrue(provider.isEnabled());
        assertNotNull(provider.getParams());
        assertTrue(provider.getParams().isEmpty());
      } else if ("TestEnabledWithParams".equals(role)) {
        assertEquals("TestProviderEnabledWithParams", provider.getName());
        assertTrue(provider.isEnabled());
        Map<String, String> params = provider.getParams();
        assertNotNull(params);
        assertEquals(3, params.size());

        // KNOX-1188
        int index = 1;
        for (String name : params.keySet()) {
          assertEquals("Param out of order", "param" + index++, name);
          assertEquals(name + "-value", params.get(name));
        }
      }
    }
  }


  @Test
  public void testParseProviderConfigurationJSON() throws Exception {
    final String JSON =
    "{\n" +
    "    \"providers\": [\n" +
    "    {\n" +
    "      \"role\":\"authentication\",\n" +
    "      \"name\":\"ShiroProvider\",\n" +
    "      \"enabled\":\"true\",\n" +
    "      \"params\":{\n" +
    "        \"main.ldapContextFactory\":\"org.apache.hadoop.gateway.shirorealm.KnoxLdapContextFactory\",\n" +
    "        \"main.ldapRealm\":\"org.apache.hadoop.gateway.shirorealm.KnoxLdapRealm\",\n" +
    "        \"main.ldapRealm.contextFactory\":\"$ldapContextFactory\",\n" +
    "        \"main.ldapRealm.userDnTemplate\":\"uid={0},ou=people,dc=hadoop,dc=apache,dc=org\",\n" +
    "        \"main.ldapRealm.contextFactory.url\":\"ldap://localhost:33389\",\n" +
    "        \"main.ldapRealm.contextFactory.authenticationMechanism\":\"simple\",\n" +
    "        \"sessionTimeout\":\"30\",\n" +
    "        \"urls./**\":\"authcBasic\"\n" +
    "      }\n" +
    "    },\n" +
    "    {\n" +
    "      \"role\":\"hostmap\",\n" +
    "      \"name\":\"static\",\n" +
    "      \"enabled\":\"true\",\n" +
    "      \"params\":{\n" +
    "        \"localhost\":\"sandbox,sandbox.hortonworks.com\"\n" +
    "      }\n" +
    "    },\n" +
    "    {\n" +
    "      \"role\":\"dummy\",\n" +
    "      \"name\":\"NoParamsDummyProvider\",\n" +
    "      \"enabled\":\"true\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"role\":\"ha\",\n" +
    "      \"name\":\"HaProvider\",\n" +
    "      \"enabled\":\"false\",\n" +
    "      \"params\":{\n" +
    "        \"HIVE\":\"maxFailoverAttempts=3;failoverSleep=1000;enabled=true\",\n" +
    "        \"WEBHDFS\":\"maxFailoverAttempts=3;failoverSleep=1000;enabled=true\"\n" +
    "      }\n" +
    "    }\n" +
    "  ]\n" +
    "}";

    ProviderConfiguration pc = doTestParseProviderConfiguration(JSON, "my-providers." + "json");
    assertNotNull(pc);

    Set<ProviderConfiguration.Provider> providers = pc.getProviders();
    assertNotNull(providers);
    assertFalse(providers.isEmpty());
    assertEquals(4, providers.size());

    // Validate the providers
    validateParsedProviders(providers);
  }


  @Test
  public void testParseProviderConfigurationYAML() throws Exception {
    doTestParseProviderConfigurationYAML("yaml");
  }


  @Test
  public void testParseProviderConfigurationYML() throws Exception {
    doTestParseProviderConfigurationYAML("yml");
  }


  // Common test for both YAML and YML file extensions
  private void doTestParseProviderConfigurationYAML(String extension) throws Exception {
    final String YAML =
        "---\n" +
        "providers: \n" +
        "  - role: authentication\n" +
        "    name: ShiroProvider\n" +
        "    enabled: true\n" +
        "    params:\n" +
        "      sessionTimeout: 30\n" +
        "      main.ldapRealm: org.apache.hadoop.gateway.shirorealm.KnoxLdapRealm\n" +
        "      main.ldapContextFactory: org.apache.hadoop.gateway.shirorealm.KnoxLdapContextFactory\n" +
        "      main.ldapRealm.contextFactory: $ldapContextFactory\n" +
        "      main.ldapRealm.userDnTemplate: uid={0},ou=people,dc=hadoop,dc=apache,dc=org\n" +
        "      main.ldapRealm.contextFactory.url: ldap://localhost:33389\n" +
        "      main.ldapRealm.contextFactory.authenticationMechanism: simple\n" +
        "      urls./**: authcBasic\n" +
        "  - role: hostmap\n" +
        "    name: static\n" +
        "    enabled: true\n" +
        "    params:\n" +
        "      localhost: sandbox,sandbox.hortonworks.com\n" +
        "  - role: dummy\n" +
        "    name: NoParamsDummyProvider\n" +
        "    enabled: true\n" +
        "  - role: ha\n" +
        "    name: HaProvider\n" +
        "    enabled: false\n" +
        "    params:\n" +
        "      WEBHDFS: maxFailoverAttempts=3;failoverSleep=1000;enabled=true\n" +
        "      HIVE: maxFailoverAttempts=3;failoverSleep=1000;enabled=true";
    ProviderConfiguration pc = doTestParseProviderConfiguration(YAML, "my-providers." + extension);

    assertNotNull(pc);

    Set<ProviderConfiguration.Provider> providers = pc.getProviders();
    assertNotNull(providers);
    assertFalse(providers.isEmpty());
    assertEquals(4, providers.size());

    // Validate the providers
    validateParsedProviders(providers);
  }


  private void validateParsedProviders(Set<ProviderConfiguration.Provider> providers) throws Exception {
    // Validate the providers
    for (ProviderConfiguration.Provider provider : providers) {
      String role = provider.getRole();
      if ("authentication".equals(role)) {
        assertEquals("ShiroProvider", provider.getName());
        assertTrue(provider.isEnabled());
        Map<String, String> params = provider.getParams();
        assertNotNull(params);
        assertEquals(8, params.size());
        assertEquals(params.get("sessionTimeout"), "30");
        assertEquals(params.get("main.ldapRealm"), "org.apache.hadoop.gateway.shirorealm.KnoxLdapRealm");
        assertEquals(params.get("main.ldapContextFactory"), "org.apache.hadoop.gateway.shirorealm.KnoxLdapContextFactory");
        assertEquals(params.get("main.ldapRealm.contextFactory"), "$ldapContextFactory");
        assertEquals(params.get("main.ldapRealm.userDnTemplate"), "uid={0},ou=people,dc=hadoop,dc=apache,dc=org");
        assertEquals(params.get("main.ldapRealm.contextFactory.url"), "ldap://localhost:33389");
        assertEquals(params.get("main.ldapRealm.contextFactory.authenticationMechanism"), "simple");
        assertEquals(params.get("urls./**"), "authcBasic");

        // Verify the param order was maintained during parsing (KNOX-1188)
        String[] expectedParameterOrder = new String[] {"main.ldapContextFactory",
                                                        "main.ldapRealm",
                                                        "main.ldapRealm.contextFactory",
                                                        "main.ldapRealm.contextFactory.authenticationMechanism",
                                                        "main.ldapRealm.contextFactory.url",
                                                        "main.ldapRealm.userDnTemplate",
                                                        "sessionTimeout",
                                                        "urls./**"};
        int index = 0;
        for (String name : params.keySet()) {
          assertEquals("Param out of order", expectedParameterOrder[index++], name);
        }
      } else if ("hostmap".equals(role)) {
        assertEquals("static", provider.getName());
        assertTrue(provider.isEnabled());
        Map<String, String> params = provider.getParams();
        assertNotNull(params);
        assertEquals(1, params.size());
        assertEquals(params.get("localhost"), "sandbox,sandbox.hortonworks.com");
      } else if ("ha".equals(role)) {
        assertEquals("HaProvider", provider.getName());
        assertFalse(provider.isEnabled());
        Map<String, String> params = provider.getParams();
        assertNotNull(params);
        assertEquals(2, params.size());
        assertEquals(params.get("WEBHDFS"), "maxFailoverAttempts=3;failoverSleep=1000;enabled=true");
        assertEquals(params.get("HIVE"), "maxFailoverAttempts=3;failoverSleep=1000;enabled=true");
      } else if ("dummy".equals(provider.getRole())) {
        assertEquals("NoParamsDummyProvider", provider.getName());
        assertTrue(provider.isEnabled());
        Map<String, String> params = provider.getParams();
        assertNotNull(params);
        assertTrue(params.isEmpty());
      }
    }
  }

  /**
   * Parse the specified configuration, and return the parse result for validation by the caller.
   *
   * @param config   The provider config content to parse.
   * @param fileName The name of the temporary file to which the content should be written prior to parsing.
   *
   * @return The resulting ProviderConfiguration
   */
  private ProviderConfiguration doTestParseProviderConfiguration(final String config, final String fileName) throws Exception {
    ProviderConfiguration pc;

    File testConfig = new File(tmpDir, fileName);

    try (OutputStream outputStream = Files.newOutputStream(testConfig.toPath());
         Writer fw = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
      fw.write(config);
      fw.flush();
    }

    try {
      pc = ProviderConfigurationParser.parse(testConfig.getAbsolutePath());
    } finally {
      FileUtils.deleteQuietly(testConfig);
    }

    return pc;
  }
}
