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
import com.cloudera.api.swagger.model.ApiClusterRef;
import com.cloudera.api.swagger.model.ApiHostRef;
import com.cloudera.api.swagger.model.ApiRoleConfig;
import com.cloudera.api.swagger.model.ApiRoleConfigList;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import okhttp3.Call;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;
import org.apache.knox.gateway.topology.discovery.cm.model.hive.HiveOnTezServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.solr.SolrServiceModelGenerator;
import org.easymock.EasyMock;
import org.junit.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ServiceModelFactoryTest extends AbstractCMDiscoveryTest {

  @Test
  public void testValidServiceProducesModel() throws ApiException {
    ApiService service = createApiServiceMock(SolrServiceModelGenerator.SERVICE_TYPE);

    Map<String, String> serviceProps = new HashMap<>();
    serviceProps.put("solr_use_ssl", "true");
    ApiServiceConfig serviceConfig = createApiServiceConfigMock(serviceProps);

    Map<String, String> roleProps = new HashMap<>();
    roleProps.put("solr_http_port", "8983");
    roleProps.put("solr_https_port", "8985");
    ApiRoleConfigList roleConfigList = roleConfigList(SolrServiceModelGenerator.ROLE_TYPE, roleProps);

    Set<ServiceModel> models = ServiceModelFactory.generateServiceModels(null, service, serviceConfig, roleConfigList, null);

    assertEquals("Expected exactly one Solr model", 1, models.size());
    assertEquals(SolrServiceModelGenerator.SERVICE_TYPE, models.iterator().next().getServiceType());
  }

  @Test
  public void testInvalidRoleTypeProducesNoModel() throws ApiException {
    // The Solr generator only handles SOLR_SERVER roles; an unexpected role type yields no model (invalid config).
    ApiService service = createApiServiceMock(SolrServiceModelGenerator.SERVICE_TYPE);
    ApiRoleConfigList roleConfigList = roleConfigList("SOME_OTHER_ROLE", Collections.emptyMap());

    Set<ServiceModel> models = ServiceModelFactory.generateServiceModels(null, service, createApiServiceConfigMock(Collections.emptyMap()), roleConfigList, null);

    assertTrue("A role type no generator handles should produce no model", models.isEmpty());
  }

  @Test
  public void testInvalidTransportModeProducesNoModel() throws ApiException {
    // The Hive-on-Tez generator recognizes HIVE_ON_TEZ/HIVESERVER2 but rejects the config when the HS2 transport
    // mode is neither "http" nor "all". Cloudera Manager's default is "binary" (see the hive_server2_transport_mode
    // role config), which is invalid for Knox discovery. This exercises the "handled=false with configuration issues"
    // branch, distinct from an unhandled (wrong) role type: a valid service type whose configuration is invalid must
    // yield no model.
    ApiService service = createApiServiceMock(HiveOnTezServiceModelGenerator.SERVICE_TYPE);

    // Role config assembled from a real HIVE_ON_TEZ readRolesConfig response; the generator reads the transport mode
    // and thrift HTTP port directly from these role config keys.
    Map<String, String> roleProps = new HashMap<>();
    roleProps.put("hive_server2_transport_mode", "binary");
    roleProps.put("hive_server2_thrift_http_port", "10001");
    ApiRoleConfigList roleConfigList = roleConfigList(HiveOnTezServiceModelGenerator.ROLE_TYPE, roleProps);

    Set<ServiceModel> models = ServiceModelFactory.generateServiceModels(null, service, createApiServiceConfigMock(Collections.emptyMap()), roleConfigList, null);

    assertTrue("A service with an invalid transport mode should produce no model", models.isEmpty());
  }

  @Test
  public void testNoGeneratorsForServiceTypeProducesNoModel() throws ApiException {
    ApiService service = createApiServiceMock("NO_SUCH_SERVICE_TYPE");
    ApiRoleConfigList roleConfigList = roleConfigList("NO_SUCH_ROLE_TYPE", Collections.emptyMap());

    Set<ServiceModel> models = ServiceModelFactory.generateServiceModels(null, service, createApiServiceConfigMock(Collections.emptyMap()), roleConfigList, null);

    assertTrue("A service type with no registered generators should produce no model", models.isEmpty());
  }

  @Test
  public void testEmptyRoleConfigListProducesNoModel() throws ApiException {
    ApiService service = createApiServiceMock(SolrServiceModelGenerator.SERVICE_TYPE);
    ApiRoleConfigList roleConfigList = new ApiRoleConfigList().items(Collections.emptyList());

    Set<ServiceModel> models = ServiceModelFactory.generateServiceModels(null, service, createApiServiceConfigMock(Collections.emptyMap()), roleConfigList, null);

    assertTrue("No roles should produce no model", models.isEmpty());
  }

  @Test
  public void testAllGeneratorsHandleMonitorReconstructedServiceWithoutNPE() {
    // Regression guard for PollingConfigurationAnalyzer#getCurrentServiceConfiguration: the monitor derives the
    // "current" configuration by running the model generators against an ApiService it builds itself (see
    // PollingConfigurationAnalyzer#findService) rather than a fully-populated discovery object. A generator that
    // dereferences an ApiService field the monitor leaves unset - e.g. YarnUI/JobHistoryUI read
    // service.getClusterRef().getClusterName() - would throw an NPE inside generateService() that is not an
    // ApiException, escaping the analyzer's try/catch and aborting the entire polling cycle for every cluster,
    // recurring every interval. Run EVERY registered generator against a monitor-shaped ApiService and assert none
    // throws a NullPointerException.

    // The only nested call any generator makes during generateService() is readServiceConfig (YarnUI/JobHistoryUI,
    // for the HDFS SSL lookup); hand back an empty ApiServiceConfig so it resolves without contacting CM.
    final DiscoveryApiClient client = new StubDiscoveryApiClient(createApiServiceConfigMock(Collections.emptyMap()));

    // A non-null hdfs_service gives those generators a real service name for the nested readServiceConfig call.
    final Map<String, String> serviceProps = new HashMap<>();
    serviceProps.put("hdfs_service", "hdfs");
    final ApiServiceConfig serviceConfig = createApiServiceConfigMock(serviceProps);

    final List<String> generatorsThatThrewNPE = new ArrayList<>();
    for (ServiceModelGenerator generator : ServiceLoader.load(ServiceModelGenerator.class)) {
      // Shaped exactly as PollingConfigurationAnalyzer#findService's fallback builds it: name + type + a populated
      // clusterRef, with every other ApiService field (displayName, serviceUrl, ...) left null.
      final ApiService service = new ApiService()
          .name(generator.getServiceType() + "-1")
          .type(generator.getServiceType())
          .clusterRef(new ApiClusterRef().clusterName("Cluster 1"));

      // A role of the type this generator handles, so its handles() returns true and generateService() is exercised.
      final ApiRoleConfigList roleConfigList = roleConfigList(generator.getRoleType(), Collections.emptyMap());

      try {
        ServiceModelFactory.generateServiceModels(client, service, serviceConfig, roleConfigList, null);
      } catch (NullPointerException npe) {
        generatorsThatThrewNPE.add(generator.getClass().getSimpleName());
      } catch (Exception tolerated) {
        // Config-/API-driven exceptions (an unhandled config shape, the stub's canned response, etc.) are unrelated
        // to the null-field defect this guard targets; only a NullPointerException is a failure here.
      }
    }

    assertTrue("Generators threw NullPointerException on a monitor-reconstructed ApiService (missing clusterRef and "
        + "other fields discovery would populate): " + generatorsThatThrewNPE, generatorsThatThrewNPE.isEmpty());
  }

  /**
   * A {@link DiscoveryApiClient} whose {@link #execute} never touches the network: it returns a canned
   * ApiServiceConfig for the nested readServiceConfig calls the YarnUI/JobHistoryUI generators make while building
   * their models. Constructed with harmless nice-mock configuration so the real client setup (base path, etc.)
   * succeeds without a live Cloudera Manager.
   */
  private static final class StubDiscoveryApiClient extends DiscoveryApiClient {
    private final ApiServiceConfig cannedServiceConfig;

    StubDiscoveryApiClient(final ApiServiceConfig cannedServiceConfig) {
      super(stubGatewayConfig(), stubDiscoveryConfig(), stubAliasService(), null);
      this.cannedServiceConfig = cannedServiceConfig;
    }

    private static GatewayConfig stubGatewayConfig() {
      final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
      EasyMock.replay(gatewayConfig);
      return gatewayConfig;
    }

    private static AliasService stubAliasService() {
      final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
      EasyMock.replay(aliasService);
      return aliasService;
    }

    private static ServiceDiscoveryConfig stubDiscoveryConfig() {
      final ServiceDiscoveryConfig config = EasyMock.createNiceMock(ServiceDiscoveryConfig.class);
      EasyMock.expect(config.getAddress()).andReturn("http://localhost:1234").anyTimes();
      EasyMock.expect(config.getUser()).andReturn("itsme").anyTimes();
      EasyMock.expect(config.getPasswordAlias()).andReturn(null).anyTimes();
      EasyMock.expect(config.getCluster()).andReturn("Cluster 1").anyTimes();
      EasyMock.replay(config);
      return config;
    }

    @Override
    boolean isKerberos() {
      return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ApiResponse<T> execute(Call call, Type returnType) {
      return (ApiResponse<T>) new StubApiResponse<>(cannedServiceConfig);
    }
  }

  private static final class StubApiResponse<T> extends ApiResponse<T> {
    private final T data;

    StubApiResponse(final T data) {
      super(200, Collections.emptyMap());
      this.data = data;
    }

    @Override
    public T getData() {
      return data;
    }
  }

  private ApiRoleConfigList roleConfigList(final String roleType, final Map<String, String> roleProps) {
    ApiRoleConfig roleConfig = new ApiRoleConfig()
        .name(roleType + "-1")
        .roleType(roleType)
        .hostRef(new ApiHostRef().hostname("host1").hostId("hostId1"))
        .config(createApiConfigListMock(roleProps));
    return new ApiRoleConfigList().addItemsItem(roleConfig);
  }
}
