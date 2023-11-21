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
package org.apache.knox.gateway.topology.hadoop.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.topology.simple.ProviderConfiguration;
import org.apache.knox.gateway.topology.simple.SimpleDescriptor;
import org.apache.knox.gateway.topology.simple.SimpleDescriptor.Application;
import org.apache.knox.gateway.topology.simple.SimpleDescriptor.Service;
import org.apache.knox.gateway.util.JsonUtils;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HadoopXmlResourceParserTest {

  @Rule
  public TemporaryFolder tempDir = new TemporaryFolder();

  private GatewayConfig gatewayConfigMock;
  private HadoopXmlResourceParser hadoopXmlResourceParser;
  private File providersDir;
  private List<String> readOnlyProviders = new ArrayList<>();
  private List<String> readOnlyTopologies = new ArrayList<>();

  @Before
  public void setUp() throws IOException {
    providersDir = tempDir.newFolder("shared-providers");
    gatewayConfigMock = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfigMock.getGatewayProvidersConfigDir()).andReturn(providersDir.getAbsolutePath()).anyTimes();
    EasyMock.expect(gatewayConfigMock.getReadOnlyOverrideProviderNames()).andReturn(readOnlyProviders).anyTimes();
    EasyMock.expect(gatewayConfigMock.getReadOnlyOverrideTopologyNames()).andReturn(readOnlyTopologies).anyTimes();
    EasyMock.replay(gatewayConfigMock);
    hadoopXmlResourceParser = new HadoopXmlResourceParser(gatewayConfigMock);
  }

  @Test
  public void testCMDescriptorParser() throws Exception {
    final String testConfigPath = this.getClass().getClassLoader().getResource("testDescriptor.xml").getPath();
    final HadoopXmlResourceParserResult parserResult = hadoopXmlResourceParser.parse(testConfigPath);
    final Set<SimpleDescriptor> descriptors = parserResult.getDescriptors();
    assertEquals(2, descriptors.size());
    final Iterator<SimpleDescriptor> descriptorsIterator = descriptors.iterator();
    validateTopology1Descriptors(descriptorsIterator.next());
    validateTopology2Descriptors(descriptorsIterator.next(), true);
    validateTestDescriptorProviderConfigs(parserResult.getProviders(), "ldap://localhost:33389");
  }

  @Test
  public void testFilteredDescriptorName() throws Exception {
    readOnlyTopologies.add("topology1");
    final String testConfigPath = this.getClass().getClassLoader().getResource("testDescriptor.xml").getPath();
    final HadoopXmlResourceParserResult parserResult = hadoopXmlResourceParser.parse(testConfigPath);
    final Set<SimpleDescriptor> descriptors = parserResult.getDescriptors();
    assertEquals(1, descriptors.size());
    final Iterator<SimpleDescriptor> descriptorsIterator = descriptors.iterator();
    validateTopology2Descriptors(descriptorsIterator.next(), true);
    validateTestDescriptorProviderConfigs(parserResult.getProviders(), "ldap://localhost:33389");
  }

  @Test
  public void testFilteredProviderName() throws Exception {
    readOnlyProviders.add("knoxsso");
    final String testConfigPath = this.getClass().getClassLoader().getResource("testDescriptor.xml").getPath();
    final HadoopXmlResourceParserResult parserResult = hadoopXmlResourceParser.parse(testConfigPath);
    final Set<SimpleDescriptor> descriptors = parserResult.getDescriptors();
    assertEquals(2, descriptors.size());
    final Iterator<SimpleDescriptor> descriptorsIterator = descriptors.iterator();
    validateTopology1Descriptors(descriptorsIterator.next());
    validateTopology2Descriptors(descriptorsIterator.next(), true);
    assertEquals(1, parserResult.getProviders().size());
    assertNotNull(parserResult.getProviders().get("admin"));
  }

  @Test
  public void testCMDescriptorParserWrongDescriptorContent() throws Exception {
    final String testConfigPath = this.getClass().getClassLoader().getResource("testDescriptorConfigurationWithWrongDescriptor.xml").getPath();
    final Set<SimpleDescriptor> descriptors = hadoopXmlResourceParser.parse(testConfigPath).getDescriptors();
    assertEquals(1, descriptors.size());
    final Iterator<SimpleDescriptor> descriptorsIterator = descriptors.iterator();
    validateTopology1Descriptors(descriptorsIterator.next());
  }

  @Test
  public void testCMDescriptorParserWrongXMLContent() throws Exception {
    final String testConfigPath = this.getClass().getClassLoader().getResource("testDescriptorConfigurationWithNonHadoopStyleConfiguration.xml").getPath();
    final Set<SimpleDescriptor> descriptors = hadoopXmlResourceParser.parse(testConfigPath).getDescriptors();
    assertTrue(descriptors.isEmpty());
  }

  @Test
  public void testDescriptorParserWithServices() throws Exception {
    final String testConfigPath = this.getClass().getClassLoader().getResource("testDescriptorWithServiceList.xml").getPath();

    final Set<SimpleDescriptor> descriptors = hadoopXmlResourceParser.parse(testConfigPath).getDescriptors();
    assertEquals(1, descriptors.size());
    final Iterator<SimpleDescriptor> descriptorsIterator = descriptors.iterator();
    final SimpleDescriptor topology1 = descriptorsIterator.next();
    assertNotNull(topology1);
    // services= NIFI,ATLAS ,hive, hue ,IMPALA#
    // + RANGER and FLINK service declarations in separate lines
    assertEquals(7, topology1.getServices().size());
    assertNotNull(topology1.getService("NIFI"));
    assertNotNull(topology1.getService("ATLAS"));
    assertNotNull(topology1.getService("HIVE")); //should be uppercase
    assertNotNull(topology1.getService("HUE")); //should be uppercase
    assertNotNull(topology1.getService("IMPALA"));
    assertNotNull(topology1.getService("RANGER"));
    assertNotNull(topology1.getService("FLINK"));
    assertEquals("3.0", topology1.getService("FLINK").getVersion());
    assertNull(topology1.getService("AMBARI"));
  }

  @Test
  public void testCMDescriptorParserModifyingProviderParams() {
    String testConfigPath = this.getClass().getClassLoader().getResource("testDescriptor.xml").getPath();
    HadoopXmlResourceParserResult parserResult = hadoopXmlResourceParser.parse(testConfigPath);
    validateTestDescriptorProviderConfigs(parserResult.getProviders(), "ldap://localhost:33389");

    //saving admin and knoxsso shared-providers with LDAP authentication provider only
    parserResult.getProviders().forEach((key, value) -> {
      final File knoxProviderConfigFile = new File(providersDir, key + ".json");
      final String providersConfiguration = JsonUtils.renderAsJsonString(value);
      try {
        FileUtils.writeStringToFile(knoxProviderConfigFile, providersConfiguration, StandardCharsets.UTF_8);
      } catch (IOException e) {
        fail("Could not save " + knoxProviderConfigFile.getAbsolutePath());
      }
    });

    //updating LDAP URL from ldap://localhost:33389 to ldaps://localhost:33390 in 'admin'
    testConfigPath = this.getClass().getClassLoader().getResource("testDescriptorWithAdminProviderConfigUpdatedLdapUrl.xml").getPath();
    parserResult = hadoopXmlResourceParser.parse(testConfigPath);
    validateTestDescriptorProviderConfigs(parserResult.getProviders(), "ldaps://localhost:33390", true, true);
  }

  @Test
  public void testCMDescriptorParserRemovingProviderParams() {
    String testConfigPath = this.getClass().getClassLoader().getResource("testDescriptor.xml").getPath();
    HadoopXmlResourceParserResult parserResult = hadoopXmlResourceParser.parse(testConfigPath);
    //saving admin and knoxsso shared-providers with LDAP authentication provider only
    parserResult.getProviders().forEach((key, value) -> {
      final File knoxProviderConfigFile = new File(providersDir, key + ".json");
      final String providersConfiguration = JsonUtils.renderAsJsonString(value);
      try {
        FileUtils.writeStringToFile(knoxProviderConfigFile, providersConfiguration, StandardCharsets.UTF_8);
      } catch (IOException e) {
        fail("Could not save " + knoxProviderConfigFile.getAbsolutePath());
      }
    });

    //removed 'main.ldapRealm.userDnTemplate' parameter from 'admin'
    testConfigPath = this.getClass().getClassLoader().getResource("testDescriptorWithAdminProviderConfigRemovedUserDnTemplate.xml").getPath();
    parserResult = hadoopXmlResourceParser.parse(testConfigPath);
    validateTestDescriptorProviderConfigs(parserResult.getProviders(), "ldap://localhost:33389", true, false);
  }

  @Test
  public void testDelete() throws Exception {
    String testConfigPath = this.getClass().getClassLoader().getResource("testDelete.xml").getPath();
    HadoopXmlResourceParserResult result = hadoopXmlResourceParser.parse(testConfigPath);
    assertEquals(new HashSet<>(Arrays.asList("topology1", "topology2")), result.getDeletedDescriptors());
    assertEquals(new HashSet<>(Arrays.asList("admin", "knoxsso")), result.getDeletedProviders());
  }

  @Test
  public void testReferencedProviderIsNotDeleted() throws Exception {
    String testConfigPath = this.getClass().getClassLoader().getResource("testDelete2.xml").getPath();
    HadoopXmlResourceParserResult result = hadoopXmlResourceParser.parse(testConfigPath);
    assertEquals(new HashSet<>(Arrays.asList("unused")), result.getDeletedProviders());
  }

  private void validateTopology1Descriptors(SimpleDescriptor descriptor) {
    assertTrue(descriptor.isReadOnly());
    assertEquals("topology1", descriptor.getName());
    assertEquals("ClouderaManager", descriptor.getDiscoveryType());
    assertEquals("http://host:123", descriptor.getDiscoveryAddress());
    assertEquals("user", descriptor.getDiscoveryUser());
    assertEquals("alias", descriptor.getDiscoveryPasswordAlias());
    assertEquals("Cluster 1", descriptor.getCluster());
    assertEquals("topology1-provider", descriptor.getProviderConfig());
    assertTrue(descriptor.isProvisionEncryptQueryStringCredential());
    assertEquals(2, descriptor.getApplications().size());

    assertApplication(descriptor, "knoxauth", Collections.singletonMap("param1.name", "param1.value"));
    assertApplication(descriptor, "admin-ui", null);

    final Map<String, String> expectedServiceParameters = Stream.of(new String[][] { { "httpclient.connectionTimeout", "5m" }, { "httpclient.socketTimeout", "100m" }, })
        .collect(Collectors.toMap(data -> data[0], data -> data[1]));
    assertService(descriptor, "HIVE", "1.0", Collections.singletonList("http://localhost:456"), expectedServiceParameters);
  }

  @Test
  public void testInvalidProviderConfig() {
    String testConfigPath = this.getClass().getClassLoader().getResource("testInvalidProvider.xml").getPath();
    HadoopXmlResourceParserResult parserResult = hadoopXmlResourceParser.parse(testConfigPath);
    assertEquals(1, parserResult.getProviders().size());
    assertNotNull(parserResult.getProviders().get("valid"));
  }

  private void validateTopology2Descriptors(SimpleDescriptor descriptor, boolean nifiExpected) {
    assertTrue(descriptor.isReadOnly());
    assertEquals("topology2", descriptor.getName());
    assertEquals("Ambari", descriptor.getDiscoveryType());
    assertEquals("http://host:456", descriptor.getDiscoveryAddress());
    assertEquals("Cluster 2", descriptor.getCluster());
    assertEquals("topology2-provider", descriptor.getProviderConfig());
    assertFalse(descriptor.isProvisionEncryptQueryStringCredential());
    assertTrue(descriptor.getApplications().isEmpty());

    final Map<String, String> expectedServiceParameters = Stream.of(new String[][] { { "httpclient.connectionTimeout", "5m" }, { "httpclient.socketTimeout", "100m" }, })
        .collect(Collectors.toMap(data -> data[0], data -> data[1]));
    assertService(descriptor, "ATLAS-API", null, Collections.singletonList("http://localhost:456"), expectedServiceParameters);
    if (nifiExpected) {
      assertService(descriptor, "NIFI", null, null, null);
    } else {
      assertNull(descriptor.getService("NIFI"));
    }
  }

  private void assertApplication(SimpleDescriptor descriptor, String expectedApplicationName, Map<String, String> expectedParams) {
    final Application application = descriptor.getApplication(expectedApplicationName);
    assertNotNull(application);
    if (expectedParams != null) {
      assertTrue(application.getParams().entrySet().containsAll(expectedParams.entrySet()));
    } else {
      assertNull(application.getParams());
    }
  }

  private void assertService(SimpleDescriptor descriptor, String expectedServiceName, String expectedVersion, List<String> expectedUrls, Map<String, String> expectedParams) {
    final Service service = descriptor.getService(expectedServiceName);
    assertNotNull(service);
    if (expectedVersion != null) {
      assertEquals(expectedVersion, service.getVersion());
    } else {
      assertNull(service.getVersion());
    }

    if (expectedUrls != null) {
      assertTrue(service.getURLs().containsAll(expectedUrls));
    } else {
      assertNull(service.getURLs());
    }

    if (expectedParams != null) {
      assertTrue(service.getParams().entrySet().containsAll(expectedParams.entrySet()));
    } else {
      assertNull(service.getParams());
    }
  }

  private void validateTestDescriptorProviderConfigs(Map<String, ProviderConfiguration> providers, String expectedLdapUrl) {
    validateTestDescriptorProviderConfigs(providers, expectedLdapUrl, false, true);
  }

  private void validateTestDescriptorProviderConfigs(Map<String, ProviderConfiguration> providers, String expectedLdapUrl, boolean onlyAdminIsExpected, boolean expectUserDnTemplateParam) {
    assertNotNull(providers);
    assertEquals(onlyAdminIsExpected ? 1 : 2, providers.size());
    final ProviderConfiguration adminProviderConfig = providers.get("admin");
    assertTrue(adminProviderConfig.isReadOnly());
    assertNotNull(adminProviderConfig);
    assertEquals(1, adminProviderConfig.getProviders().size());
    final ProviderConfiguration.Provider authenticationProvider = adminProviderConfig.getProviders().iterator().next();
    assertEquals("authentication", authenticationProvider.getRole());
    assertEquals("ShiroProvider", authenticationProvider.getName());
    assertTrue(authenticationProvider.isEnabled());
    assertEquals(expectUserDnTemplateParam ? 10 : 9, authenticationProvider.getParams().size());
    assertEquals("30", authenticationProvider.getParams().get("sessionTimeout"));
    assertEquals("org.apache.knox.gateway.shirorealm.KnoxLdapContextFactory", authenticationProvider.getParams().get("main.ldapContextFactory"));
    assertEquals("org.apache.hadoop.gateway.shirorealm.KnoxLdapRealm", authenticationProvider.getParams().get("main.ldapRealm"));
    assertEquals("$ldapContextFactory", authenticationProvider.getParams().get("main.ldapRealm.contextFactory"));
    assertEquals("simple", authenticationProvider.getParams().get("main.ldapRealm.contextFactory.authenticationMechanism"));
    assertEquals(expectedLdapUrl, authenticationProvider.getParams().get("main.ldapRealm.contextFactory.url"));
    assertEquals("uid=guest,ou=people,dc=hadoop,dc=apache,dc=org", authenticationProvider.getParams().get("main.ldapRealm.contextFactory.systemUsername"));
    assertEquals("${ALIAS=knoxLdapSystemPassword}", authenticationProvider.getParams().get("main.ldapRealm.contextFactory.systemPassword"));
    if (expectUserDnTemplateParam) {
      assertEquals("uid={0},ou=people,dc=hadoop,dc=apache,dc=org", authenticationProvider.getParams().get("main.ldapRealm.userDnTemplate"));
    } else {
      assertNull(authenticationProvider.getParams().get("main.ldapRealm.userDnTemplate"));
    }
    assertEquals("authcBasic", authenticationProvider.getParams().get("urls./**"));
    if (!onlyAdminIsExpected) {
      final ProviderConfiguration knoxSsoProviderConfig = providers.get("knoxsso");
      assertNotNull(knoxSsoProviderConfig);
      assertEquals(adminProviderConfig, knoxSsoProviderConfig);
    }
  }
}
