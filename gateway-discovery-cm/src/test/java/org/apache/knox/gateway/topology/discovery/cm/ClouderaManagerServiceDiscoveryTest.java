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
package org.apache.knox.gateway.topology.discovery.cm;

import com.cloudera.api.swagger.client.ApiException;
import com.cloudera.api.swagger.client.ApiResponse;
import com.cloudera.api.swagger.model.ApiConfig;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiHostRef;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiRoleList;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import com.cloudera.api.swagger.model.ApiServiceList;
import com.squareup.okhttp.Call;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.topology.discovery.ServiceDiscovery;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;
import org.easymock.EasyMock;
import org.junit.Test;


import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


public class ClouderaManagerServiceDiscoveryTest {

  @Test
  public void testHiveServiceDiscovery() {
    doTestHiveServiceDiscovery(false);
  }

  @Test
  public void testHiveServiceDiscoverySSL() {
    doTestHiveServiceDiscovery(true);
  }

  private void doTestHiveServiceDiscovery(final boolean enableSSL) {
    final String clusterName    = "test-cluster-1";
    final String hostName       = "test-host-1";
    final String thriftPort     = "10001";
    final String thriftPath     = "cliService";
    final String expectedScheme = (enableSSL ? "https" : "http");

    ServiceDiscovery.Cluster cluster =
        doTestHiveServiceDiscovery(clusterName, hostName, thriftPort, thriftPath, enableSSL);
    assertEquals(clusterName, cluster.getName());
    List<String> hiveURLs = cluster.getServiceURLs("HIVE");
    assertNotNull(hiveURLs);
    assertEquals(1, hiveURLs.size());
    assertEquals((expectedScheme + "://" + hostName + ":" +thriftPort + "/" + thriftPath), hiveURLs.get(0));
  }

  @Test
  public void testWebHDFSServiceDiscovery() {
    final String clusterName = "test-cluster-1";
    final String hostName    = "test-host-1";
    final String nameService = "nameservice1";
    final String nnPort      = "50070";
    final String dfsHttpPort = "50075";

    ServiceDiscovery.Cluster cluster = doTestHDFSDiscovery(clusterName, hostName, nameService, nnPort, dfsHttpPort);
    assertEquals(clusterName, cluster.getName());
    List<String> webhdfsURLs = cluster.getServiceURLs("WEBHDFS");
    assertNotNull(webhdfsURLs);
    assertEquals(1, webhdfsURLs.size());
    assertEquals("http://" + hostName + ":" + dfsHttpPort + "/webhdfs",
                 webhdfsURLs.get(0));
  }

  @Test
  public void testWebHDFSServiceDiscoveryWithSSL() {
    final String clusterName  = "test-cluster-1";
    final String hostName     = "test-host-1";
    final String nameService  = "nameservice1";
    final String nnPort       = "50070";
    final String dfsHttpPort  = "50075";
    final String dfsHttpsPort = "50079";

    ServiceDiscovery.Cluster cluster =
        doTestHDFSDiscovery(clusterName, hostName, nameService, nnPort, dfsHttpPort, dfsHttpsPort);
    assertEquals(clusterName, cluster.getName());
    List<String> webhdfsURLs = cluster.getServiceURLs("WEBHDFS");
    assertNotNull(webhdfsURLs);
    assertEquals(1, webhdfsURLs.size());
    assertEquals("https://" + hostName + ":" + dfsHttpsPort + "/webhdfs",
                 webhdfsURLs.get(0));
  }

  @Test
  public void testNameNodeServiceDiscovery() {
    final String clusterName = "test-cluster-2";
    final String hostName    = "test-host-2";
    final String nameService = "nameservice2";
    final String nnPort      = "50070";
    final String dfsHttpPort = "50071";

    ServiceDiscovery.Cluster cluster = doTestHDFSDiscovery(clusterName, hostName, nameService, nnPort, dfsHttpPort);
    assertEquals(clusterName, cluster.getName());
    List<String> nnURLs = cluster.getServiceURLs("NAMENODE");
    assertNotNull(nnURLs);
    assertEquals(1, nnURLs.size());
    assertEquals(("hdfs://" + hostName + ":" + nnPort), nnURLs.get(0));
  }

  @Test
  public void testNameNodeServiceDiscoveryHA() {
    final String clusterName = "test-cluster-2";
    final String hostName    = "test-host-2";
    final String nameService = "nameservice2";
    final String nnPort      = "50070";
    final String dfsHttpPort = "50071";

    ServiceDiscovery.Cluster cluster =
        doTestHDFSDiscovery(clusterName, hostName, nameService, nnPort, dfsHttpPort, null, true);
    assertEquals(clusterName, cluster.getName());
    List<String> nnURLs = cluster.getServiceURLs("NAMENODE");
    assertNotNull(nnURLs);
    assertEquals(1, nnURLs.size());
    assertEquals(("hdfs://" + nameService), nnURLs.get(0));
  }

  @Test
  public void testHdfsUIServiceDiscovery() {
    final String clusterName = "test-cluster-3";
    final String hostName    = "test-host-3";
    final String nameService = "nameservice3";
    final String nnPort      = "50070";
    final String dfsHttpPort = "50071";

    ServiceDiscovery.Cluster cluster = doTestHDFSDiscovery(clusterName, hostName, nameService, nnPort, dfsHttpPort);
    assertEquals(clusterName, cluster.getName());
    List<String> hdfsUIURLs = cluster.getServiceURLs("HDFSUI");
    assertNotNull(hdfsUIURLs);
    assertEquals(1, hdfsUIURLs.size());
    assertEquals(("http://" + hostName + ":" + dfsHttpPort), hdfsUIURLs.get(0));
  }

  private ServiceDiscovery.Cluster doTestHiveServiceDiscovery(final String  clusterName,
                                                              final String  hostName,
                                                              final String  thriftPort,
                                                              final String  thriftPath,
                                                              final boolean enableSSL) {
    final String hs2SafetyValveValue =
          "<property><name>hive.server2.transport.mode</name><value>http</value></property>\n" +
          "<property><name>hive.server2.thrift.http.port</name><value>" + thriftPort + "</value></property>\n" +
          "<property><name>hive.server2.thrift.http.path</name><value>" + thriftPath + "</value></property>";

    GatewayConfig gwConf = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.replay(gwConf);

    ServiceDiscoveryConfig sdConfig = createMockDiscoveryConfig();

    // Create the test client for providing test response content
    TestDiscoveryApiClient mockClient = new TestDiscoveryApiClient(sdConfig, null);

    // Prepare the service list response for the cluster
    ApiServiceList serviceList = EasyMock.createNiceMock(ApiServiceList.class);
    EasyMock.expect(serviceList.getItems())
        .andReturn(Collections.singletonList(createMockApiService("HIVE-1", "HIVE")))
        .anyTimes();
    EasyMock.replay(serviceList);
    mockClient.addResponse(ApiServiceList.class, new TestApiServiceListResponse(serviceList));

    // Prepare the HIVE service config response for the cluster
    ApiServiceConfig hiveServiceConfig = createMockApiServiceConfig();
    mockClient.addResponse(ApiServiceConfig.class, new TestApiServiceConfigResponse(hiveServiceConfig));

    // Prepare the HS2 role
    ApiRole hs2Role =
        createMockApiRole("HIVE-1-HIVESERVER2-d0b64dd7b7611e22bc976ede61678d9e", "HIVESERVER2", hostName);
    ApiRoleList hiveRoleList = EasyMock.createNiceMock(ApiRoleList.class);
    EasyMock.expect(hiveRoleList.getItems()).andReturn(Collections.singletonList(hs2Role)).anyTimes();
    EasyMock.replay(hiveRoleList);
    mockClient.addResponse(ApiRoleList.class, new TestApiRoleListResponse(hiveRoleList));

    // Configure the HS2 role
    Map<String, String> roleProperties = new HashMap<>();
    roleProperties.put("hive_hs2_config_safety_valve", hs2SafetyValveValue);
    roleProperties.put("hive.server2.use.SSL", String.valueOf(enableSSL));
    ApiConfigList hiveRoleConfigList = createMockApiConfigList(roleProperties);
    mockClient.addResponse(ApiConfigList.class, new TestApiConfigListResponse(hiveRoleConfigList));

    // Invoke the service discovery
    ClouderaManagerServiceDiscovery cmsd = new ClouderaManagerServiceDiscovery(true);
    ServiceDiscovery.Cluster cluster = cmsd.discover(gwConf, sdConfig, clusterName, mockClient);
    assertNotNull(cluster);
    return cluster;
  }

  private ServiceDiscovery.Cluster doTestHDFSDiscovery(final String clusterName,
                                                       final String hostName,
                                                       final String nameService,
                                                       final String nnPort,
                                                       final String dfsHttpPort) {
    return doTestHDFSDiscovery(clusterName, hostName, nameService, nnPort, dfsHttpPort, null);
  }

  private ServiceDiscovery.Cluster doTestHDFSDiscovery(final String clusterName,
                                                       final String hostName,
                                                       final String nameService,
                                                       final String nnPort,
                                                       final String dfsHttpPort,
                                                       final String dfsHttpsPort) {
    return doTestHDFSDiscovery(clusterName, hostName, nameService, nnPort, dfsHttpPort, dfsHttpsPort, false);
  }

  private ServiceDiscovery.Cluster doTestHDFSDiscovery(final String  clusterName,
                                                       final String  hostName,
                                                       final String  nameService,
                                                       final String  nnPort,
                                                       final String  dfsHttpPort,
                                                       final String  dfsHttpsPort,
                                                       final boolean enableHA) {

    GatewayConfig gwConf = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.replay(gwConf);

    ServiceDiscoveryConfig sdConfig = createMockDiscoveryConfig();

    // Create the test client for providing test response content
    TestDiscoveryApiClient mockClient = new TestDiscoveryApiClient(sdConfig, null);

    // Prepare the service list response for the cluster
    ApiServiceList serviceList = EasyMock.createNiceMock(ApiServiceList.class);
    EasyMock.expect(serviceList.getItems())
        .andReturn(Collections.singletonList(createMockApiService("NAMENODE-1", "HDFS")))
        .anyTimes();
    EasyMock.replay(serviceList);
    mockClient.addResponse(ApiServiceList.class, new TestApiServiceListResponse(serviceList));

    // Prepare the HDFS service config response for the cluster
    Map<String, String> serviceProps = new HashMap<>();
    serviceProps.put("hdfs_hadoop_ssl_enabled", String.valueOf(dfsHttpsPort != null && !dfsHttpsPort.isEmpty()));
    serviceProps.put("dfs_webhdfs_enabled", "true");
    ApiServiceConfig hdfsServiceConfig = createMockApiServiceConfig(serviceProps);
    mockClient.addResponse(ApiServiceConfig.class, new TestApiServiceConfigResponse(hdfsServiceConfig));

    // Prepare the NameNode role
    ApiRole nnRole = createMockApiRole("HDFS-1-NAMENODE-d0b64dd7b7611e22bc976ede61678d9e", "NAMENODE", hostName);
    ApiRoleList nnRoleList = EasyMock.createNiceMock(ApiRoleList.class);
    EasyMock.expect(nnRoleList.getItems()).andReturn(Collections.singletonList(nnRole)).anyTimes();
    EasyMock.replay(nnRoleList);
    mockClient.addResponse(ApiRoleList.class, new TestApiRoleListResponse(nnRoleList));

    // Configure the NameNode role
    Map<String, String> roleProperties = new HashMap<>();
    roleProperties.put("dfs_federation_namenode_nameservice", nameService);
    roleProperties.put("autofailover_enabled", String.valueOf(enableHA));
    roleProperties.put("namenode_port", nnPort);
    roleProperties.put("dfs_http_port", dfsHttpPort);
    if (dfsHttpsPort != null && !dfsHttpsPort.isEmpty()) {
      roleProperties.put("dfs_https_port", dfsHttpsPort);
    }
    ApiConfigList nnRoleConfigList = createMockApiConfigList(roleProperties);
    mockClient.addResponse(ApiConfigList.class, new TestApiConfigListResponse(nnRoleConfigList));

    // Invoke the service discovery
    ClouderaManagerServiceDiscovery cmsd = new ClouderaManagerServiceDiscovery(true);
    ServiceDiscovery.Cluster cluster = cmsd.discover(gwConf, sdConfig, clusterName, mockClient);
    assertNotNull(cluster);
    assertEquals(clusterName, cluster.getName());
    return cluster;
  }

  private static ServiceDiscoveryConfig createMockDiscoveryConfig() {
    return createMockDiscoveryConfig("http://localhost:1234", "itsme");
  }

  private static ServiceDiscoveryConfig createMockDiscoveryConfig(String address, String username) {
    ServiceDiscoveryConfig config = EasyMock.createNiceMock(ServiceDiscoveryConfig.class);
    EasyMock.expect(config.getAddress()).andReturn(address).anyTimes();
    EasyMock.expect(config.getUser()).andReturn(username).anyTimes();
    EasyMock.expect(config.getPasswordAlias()).andReturn(null).anyTimes();
    EasyMock.replay(config);
    return config;
  }

  private static ApiService createMockApiService(String name, String type) {
    ApiService s = EasyMock.createNiceMock(ApiService.class);
    EasyMock.expect(s.getName()).andReturn(name).anyTimes();
    EasyMock.expect(s.getType()).andReturn(type).anyTimes();
    EasyMock.replay(s);
    return s;
  }

  private static ApiRole createMockApiRole(String name, String type, String hostname) {
    ApiRole r = EasyMock.createNiceMock(ApiRole.class);
    EasyMock.expect(r.getName()).andReturn(name).anyTimes();
    EasyMock.expect(r.getType()).andReturn(type).anyTimes();
    ApiHostRef hostRef = EasyMock.createNiceMock(ApiHostRef.class);
    EasyMock.expect(hostRef.getHostname()).andReturn(hostname).anyTimes();
    EasyMock.replay(hostRef);
    EasyMock.expect(r.getHostRef()).andReturn(hostRef).anyTimes();
    EasyMock.replay(r);
    return r;
  }

  private static ApiServiceConfig createMockApiServiceConfig() {
    return createMockApiServiceConfig(Collections.emptyMap());
  }

  private static ApiServiceConfig createMockApiServiceConfig(Map<String, String> properties) {
    ApiServiceConfig serviceConfig = EasyMock.createNiceMock(ApiServiceConfig.class);
    List<ApiConfig> serviceConfigs = new ArrayList<>();

    for (Map.Entry<String, String> property : properties.entrySet()) {
      ApiConfig config = EasyMock.createNiceMock(ApiConfig.class);
      EasyMock.expect(config.getName()).andReturn(property.getKey()).anyTimes();
      EasyMock.expect(config.getValue()).andReturn(property.getValue()).anyTimes();
      EasyMock.replay(config);
      serviceConfigs.add(config);
    }

    EasyMock.expect(serviceConfig.getItems()).andReturn(serviceConfigs).anyTimes();
    EasyMock.replay(serviceConfig);
    return serviceConfig;
  }

  private static ApiConfigList createMockApiConfigList(Map<String, String> properties) {
    ApiConfigList configList = EasyMock.createNiceMock(ApiConfigList.class);
    List<ApiConfig> roleConfigs = new ArrayList<>();

    for (Map.Entry<String, String> property : properties.entrySet()) {
      ApiConfig config = EasyMock.createNiceMock(ApiConfig.class);
      EasyMock.expect(config.getName()).andReturn(property.getKey()).anyTimes();
      EasyMock.expect(config.getValue()).andReturn(property.getValue()).anyTimes();
      EasyMock.replay(config);
      roleConfigs.add(config);
    }

    EasyMock.expect(configList.getItems()).andReturn(roleConfigs).anyTimes();
    EasyMock.replay(configList);
    return configList;
  }

  private static class TestDiscoveryApiClient extends DiscoveryApiClient {

    private Map<Type, ApiResponse<?>> responseMap = new HashMap<>();

    TestDiscoveryApiClient(ServiceDiscoveryConfig sdConfig, AliasService aliasService) {
      super(sdConfig, aliasService);
    }

    void addResponse(Type type, ApiResponse<?> response) {
      responseMap.put(type, response);
    }

    @Override
    boolean isKerberos() {
      return false;
    }

    @Override
    public <T> ApiResponse<T> execute(Call call, Type returnType) throws ApiException {
      return (ApiResponse<T>) responseMap.get(returnType);
    }
  }

  private static class TestResponseBase<T> extends ApiResponse<T> {
    protected T data;

    TestResponseBase(T data) {
      super(200, Collections.emptyMap());
      this.data = data;
    }

    @Override
    public T getData() {
      return data;
    }
  }

  private static class TestApiServiceListResponse extends TestResponseBase<ApiServiceList> {
    TestApiServiceListResponse(ApiServiceList data) {
      super(data);
    }
  }

  private static class TestApiServiceConfigResponse extends TestResponseBase<ApiServiceConfig> {
    TestApiServiceConfigResponse(ApiServiceConfig data) {
      super(data);
    }
  }

  private static class TestApiRoleListResponse extends TestResponseBase<ApiRoleList> {
    TestApiRoleListResponse(ApiRoleList data) {
      super(data);
    }
  }

  private static class TestApiConfigListResponse extends TestResponseBase<ApiConfigList> {
    TestApiConfigListResponse(ApiConfigList data) {
      super(data);
    }
  }

}
