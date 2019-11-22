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
package org.apache.knox.gateway;

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.registry.ServiceDefinitionRegistry;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.discovery.ServiceDiscovery;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryType;
import org.apache.knox.gateway.topology.simple.SimpleDescriptor;
import org.apache.knox.gateway.topology.simple.SimpleDescriptorHandler;
import org.apache.knox.test.TestUtils;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Test;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class SimpleDescriptorHandlerFuncTest {
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
          "\n" +
          "        <provider>\n" +
          "            <role>identity-assertion</role>\n" +
          "            <name>Default</name>\n" +
          "            <enabled>true</enabled>\n" +
          "        </provider>\n" +
          "\n" +
          "        <provider>\n" +
          "            <role>hostmap</role>\n" +
          "            <name>static</name>\n" +
          "            <enabled>true</enabled>\n" +
          "            <param><name>localhost</name><value>sandbox,sandbox.hortonworks.com</value></param>\n" +
          "        </provider>\n" +
          "    </gateway>\n";


  /*
   * KNOX-1136
   * <p>
   * Test that a credential store is created, and a encryptQueryString alias is defined, with a password that is not
   * random (but is derived from the master secret and the topology name).
   * <p>
   * N.B. This test depends on the NoOpServiceDiscovery extension being configured in META-INF/services
   */
  @Test
  public void testSimpleDescriptorHandlerQueryStringCredentialAliasCreation() throws Exception {

    final String testMasterSecret = "mysecret";
    final String discoveryType = "NO_OP";
    final String clusterName = "dummy";

    final Map<String, List<String>> serviceURLs = new HashMap<>();
    serviceURLs.put("RESOURCEMANAGER", Collections.singletonList("http://myhost:1234/resource"));

    File testRootDir = TestUtils.createTempDir(getClass().getSimpleName());
    File testConfDir = new File(testRootDir, "conf");
    File testProvDir = new File(testConfDir, "shared-providers");
    File testTopoDir = new File(testConfDir, "topologies");
    File testDeployDir = new File(testConfDir, "deployments");

    // Write the externalized provider config to a temp file
    File providerConfig = new File(testProvDir, "ambari-cluster-policy.xml");
    FileUtils.write(providerConfig, TEST_PROVIDER_CONFIG, StandardCharsets.UTF_8);

    File topologyFile = null;
    try {
      File destDir = new File(System.getProperty("java.io.tmpdir")).getCanonicalFile();

      // Mock out the simple descriptor
      SimpleDescriptor testDescriptor = EasyMock.createNiceMock(SimpleDescriptor.class);
      EasyMock.expect(testDescriptor.getName()).andReturn("mysimpledescriptor").anyTimes();
      EasyMock.expect(testDescriptor.getDiscoveryAddress()).andReturn(null).anyTimes();
      EasyMock.expect(testDescriptor.getDiscoveryType()).andReturn(discoveryType).anyTimes();
      EasyMock.expect(testDescriptor.getDiscoveryUser()).andReturn(null).anyTimes();
      EasyMock.expect(testDescriptor.getProviderConfig()).andReturn(providerConfig.getAbsolutePath()).anyTimes();
      EasyMock.expect(testDescriptor.getClusterName()).andReturn(clusterName).anyTimes();
      List<SimpleDescriptor.Service> serviceMocks = new ArrayList<>();
      for (String serviceName : serviceURLs.keySet()) {
        SimpleDescriptor.Service svc = EasyMock.createNiceMock(SimpleDescriptor.Service.class);
        EasyMock.expect(svc.getName()).andReturn(serviceName).anyTimes();
        EasyMock.expect(svc.getURLs()).andReturn(serviceURLs.get(serviceName)).anyTimes();
        EasyMock.expect(svc.getParams()).andReturn(Collections.emptyMap()).anyTimes();
        EasyMock.replay(svc);
        serviceMocks.add(svc);
      }
      EasyMock.expect(testDescriptor.getServices()).andReturn(serviceMocks).anyTimes();
      EasyMock.replay(testDescriptor);

      // Try setting up enough of the GatewayServer to support the test...
      GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
      InetSocketAddress gatewayAddress = new InetSocketAddress(0);
      EasyMock.expect(config.getGatewayTopologyDir()).andReturn(testTopoDir.getAbsolutePath()).anyTimes();
      EasyMock.expect(config.getGatewayDeploymentDir()).andReturn(testDeployDir.getAbsolutePath()).anyTimes();
      EasyMock.expect(config.getGatewayAddress()).andReturn(gatewayAddress).anyTimes();
      EasyMock.expect(config.getGatewayPortMappings()).andReturn(Collections.emptyMap()).anyTimes();
      EasyMock.expect(config.getThreadPoolMax()).andReturn(200).anyTimes();
      EasyMock.replay(config);

      // Setup the Gateway Services
      GatewayServices gatewayServices = EasyMock.createNiceMock(GatewayServices.class);

      //Service Definition Registry
      final ServiceDefinitionRegistry serviceDefinitionRegistry = EasyMock.createNiceMock(ServiceDefinitionRegistry.class);
      EasyMock.expect(serviceDefinitionRegistry.getServiceDefinitions()).andReturn(Collections.emptySet()).anyTimes();
      EasyMock.replay(serviceDefinitionRegistry);
      EasyMock.expect(gatewayServices.getService(ServiceType.SERVICE_DEFINITION_REGISTRY)).andReturn(serviceDefinitionRegistry).anyTimes();

      // Master Service
      MasterService ms = EasyMock.createNiceMock(MasterService.class);
      EasyMock.expect(ms.getMasterSecret()).andReturn(testMasterSecret.toCharArray()).anyTimes();
      EasyMock.replay(ms);
      EasyMock.expect(gatewayServices.getService(ServiceType.MASTER_SERVICE)).andReturn(ms).anyTimes();

      // Keystore Service
      KeystoreService ks = EasyMock.createNiceMock(KeystoreService.class);
      EasyMock.expect(ks.isCredentialStoreForClusterAvailable(testDescriptor.getName())).andReturn(false).once();
      ks.createCredentialStoreForCluster(testDescriptor.getName());
      EasyMock.expectLastCall().once();
      KeyStore credStore = EasyMock.createNiceMock(KeyStore.class);
      EasyMock.expect(ks.getCredentialStoreForCluster(testDescriptor.getName())).andReturn(credStore).anyTimes();
      EasyMock.replay(ks);
      EasyMock.expect(gatewayServices.getService(ServiceType.KEYSTORE_SERVICE)).andReturn(ks).anyTimes();

      // Alias Service
      AliasService as = EasyMock.createNiceMock(AliasService.class);
      // Captures for validating the alias creation for a generated topology
      Capture<String> capturedCluster = EasyMock.newCapture();
      Capture<String> capturedAlias = EasyMock.newCapture();
      Capture<String> capturedPwd = EasyMock.newCapture();
      as.addAliasForCluster(capture(capturedCluster), capture(capturedAlias), capture(capturedPwd));
      EasyMock.expectLastCall().anyTimes();
      EasyMock.replay(as);
      EasyMock.expect(gatewayServices.getService(ServiceType.ALIAS_SERVICE)).andReturn(as).anyTimes();

      // Topology Service
      TopologyService ts = EasyMock.createNiceMock(TopologyService.class);
      ts.addTopologyChangeListener(anyObject());
      EasyMock.expectLastCall().anyTimes();
      ts.reloadTopologies();
      EasyMock.expectLastCall().anyTimes();
      EasyMock.expect(ts.getTopologies()).andReturn(Collections.emptyList()).anyTimes();
      EasyMock.replay(ts);
      EasyMock.expect(gatewayServices.getService(ServiceType.TOPOLOGY_SERVICE)).andReturn(ts).anyTimes();

      EasyMock.replay(gatewayServices);

      // Start a GatewayService with the GatewayServices mock
      GatewayServer server = GatewayServer.startGateway(config, gatewayServices);
      assertNotNull(server);

      // Invoke the simple descriptor handler, which will also create the credential store
      // (because it doesn't exist) and the encryptQueryString alias
      Map<String, File> files = SimpleDescriptorHandler.handle(config,
                                                               testDescriptor,
                                                               providerConfig.getParentFile(),
                                                               destDir);
      topologyFile = files.get("topology");

      // Validate the AliasService interaction
      assertEquals("Unexpected cluster name for the alias (should be the topology name).",
                   testDescriptor.getName(), capturedCluster.getValue());
      assertEquals("Unexpected alias name.", "encryptQueryString", capturedAlias.getValue());
      assertEquals("Unexpected alias value (should be master secret + topology name.",
                   testMasterSecret + testDescriptor.getName(), capturedPwd.getValue());

    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    } finally {
      FileUtils.forceDelete(testRootDir);
      if (topologyFile != null) {
        topologyFile.delete();
      }
    }
  }

  ///////////////////////////////////////////////////////////////////////////////////////////////////////
  // Test classes for effectively "skipping" service discovery for this test.
  ///////////////////////////////////////////////////////////////////////////////////////////////////////

  public static final class NoOpServiceDiscoveryType implements ServiceDiscoveryType {
    @Override
    public String getType() {
      return NoOpServiceDiscovery.TYPE;
    }

    @Override
    public ServiceDiscovery newInstance() {
      return new NoOpServiceDiscovery();
    }
  }

  private static final class NoOpServiceDiscovery implements ServiceDiscovery {
    static final String TYPE = "NO_OP";

    @Override
    public String getType() {
      return TYPE;
    }

    @Override
    public Map<String, Cluster> discover(GatewayConfig gwConfig, ServiceDiscoveryConfig config) {
      return Collections.emptyMap();
    }

    @Override
    public Cluster discover(GatewayConfig gwConfig, ServiceDiscoveryConfig config, String clusterName) {
      return null;
    }
  }
}
