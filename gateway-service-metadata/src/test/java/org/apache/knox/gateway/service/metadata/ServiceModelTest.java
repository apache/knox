/*
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
package org.apache.knox.gateway.service.metadata;

import static org.apache.knox.gateway.service.metadata.ServiceModel.HIVE_SERVICE_NAME;
import static org.apache.knox.gateway.service.metadata.ServiceModel.HIVE_SERVICE_URL_TEMPLATE;
import static org.apache.knox.gateway.service.metadata.ServiceModel.IMPALA_SERVICE_NAME;
import static org.apache.knox.gateway.service.metadata.ServiceModel.IMPALA_SERVICE_URL_TEMPLATE;
import static org.apache.knox.gateway.service.metadata.ServiceModel.SERVICE_URL_TEMPLATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;

import org.apache.knox.gateway.service.definition.Metadata;
import org.apache.knox.gateway.service.definition.Sample;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Version;
import org.easymock.EasyMock;
import org.junit.Test;

public class ServiceModelTest {

  private static final String SERVER_SCHEME = "https";
  private static final String SERVER_NAME = "localhost";
  private static final int SERVER_PORT = 8443;

  @Test
  public void shouldReturnEmptyStringAsServiceNameIfServiceNotSet() throws Exception {
    final ServiceModel serviceModel = new ServiceModel();
    assertEquals("", serviceModel.getServiceName());
  }

  @Test
  public void shouldReturnTheGivenServiceRoleWhenFetchingServiceName() throws Exception {
    final ServiceModel serviceModel = new ServiceModel();
    final Service service = EasyMock.createNiceMock(Service.class);
    serviceModel.setService(service);
    final String roleName = "sampleRole";
    EasyMock.expect(service.getRole()).andReturn(roleName).anyTimes();
    EasyMock.replay(service);
    assertEquals(roleName, serviceModel.getServiceName());
  }

  @Test
  public void shouldReturnEmptyStringAsServiceVersionIfServiceNotSet() throws Exception {
    final ServiceModel serviceModel = new ServiceModel();
    assertEquals("", serviceModel.getVersion());
  }

  @Test
  public void shouldReturnEmptyStringAsServiceVersionIfServiceVersionIsNotSet() throws Exception {
    final ServiceModel serviceModel = new ServiceModel();
    final Service service = EasyMock.createNiceMock(Service.class);
    serviceModel.setService(service);
    assertEquals("", serviceModel.getVersion());
  }

  @Test
  public void shouldReturnTheGivenServiceVersionWhenFetchingVersion() throws Exception {
    final ServiceModel serviceModel = new ServiceModel();
    final Service service = EasyMock.createNiceMock(Service.class);
    serviceModel.setService(service);
    final Version version = new Version(1, 2, 3);
    EasyMock.expect(service.getVersion()).andReturn(version).anyTimes();
    EasyMock.replay(service);
    assertEquals(version.toString(), serviceModel.getVersion());
  }

  @Test
  public void shouldReturnServiceRoleAsShortDescriptionIfMetadataIsNotSet() throws Exception {
    final ServiceModel serviceModel = new ServiceModel();
    final Service service = EasyMock.createNiceMock(Service.class);
    serviceModel.setService(service);
    EasyMock.expect(service.getRole()).andReturn("sampleRole").anyTimes();
    EasyMock.replay(service);
    assertEquals("Samplerole", serviceModel.getShortDescription());
  }

  @Test
  public void shouldReturnShortDescriptionFromMetadataIfMetadataIsSet() throws Exception {
    final ServiceModel serviceModel = new ServiceModel();
    final Metadata metadata = EasyMock.createNiceMock(Metadata.class);
    serviceModel.setServiceMetadata(metadata);
    final String shortDesc = "shortDescription";
    EasyMock.expect(metadata.getShortDesc()).andReturn(shortDesc).anyTimes();
    EasyMock.replay(metadata);
    assertEquals(shortDesc, serviceModel.getShortDescription());
  }

  @Test
  public void shouldReturnUnknownTypeIfMetadataIsNotSet() throws Exception {
    final ServiceModel serviceModel = new ServiceModel();
    assertEquals(ServiceModel.Type.UNKNOWN, serviceModel.getType());
  }

  @Test
  public void shouldReturnTypeFromMetadataIfMetadataIsSet() throws Exception {
    final ServiceModel serviceModel = new ServiceModel();
    final Metadata metadata = EasyMock.createNiceMock(Metadata.class);
    serviceModel.setServiceMetadata(metadata);
    final ServiceModel.Type type = ServiceModel.Type.API;
    EasyMock.expect(metadata.getType()).andReturn(type.name()).anyTimes();
    EasyMock.replay(metadata);
    assertEquals(type, serviceModel.getType());
  }

  @Test
  public void shouldUseServiceNameAsContextIfMetadataIsNotSet() throws Exception {
    final ServiceModel serviceModel = new ServiceModel();
    final Service service = EasyMock.createNiceMock(Service.class);
    serviceModel.setService(service);
    EasyMock.expect(service.getRole()).andReturn("sampleRole").anyTimes();
    EasyMock.replay(service);
    assertEquals("/samplerole", serviceModel.getContext());
  }

  @Test
  public void shouldReturnContextFromMetadataIfMetadataIsSet() throws Exception {
    final ServiceModel serviceModel = new ServiceModel();
    final Metadata metadata = EasyMock.createNiceMock(Metadata.class);
    serviceModel.setServiceMetadata(metadata);
    final String context = "/testContext";
    EasyMock.expect(metadata.getContext()).andReturn(context).anyTimes();
    EasyMock.replay(metadata);
    assertEquals(context, serviceModel.getContext());
  }

  @Test
  public void shouldReturnProperHiveServiceUrl() throws Exception {
    final ServiceModel serviceModel = new ServiceModel();
    final String gatewayPath = "gateway";
    final String topologyName = "sandbox";
    serviceModel.setGatewayPath(gatewayPath);
    serviceModel.setTopologyName(topologyName);
    serviceModel.setRequest(setUpHttpRequestMock());
    final Service service = EasyMock.createNiceMock(Service.class);
    serviceModel.setService(service);
    EasyMock.expect(service.getRole()).andReturn(HIVE_SERVICE_NAME).anyTimes();
    EasyMock.replay(service);
    assertEquals(String.format(Locale.ROOT, HIVE_SERVICE_URL_TEMPLATE, SERVER_NAME, SERVER_PORT, gatewayPath, topologyName), serviceModel.getServiceUrl());
  }

  @Test
  public void shouldReturnProperImpalaServiceUrl() throws Exception {
    final ServiceModel serviceModel = new ServiceModel();
    final String gatewayPath = "gateway";
    final String topologyName = "sandbox";
    serviceModel.setGatewayPath(gatewayPath);
    serviceModel.setTopologyName(topologyName);
    serviceModel.setRequest(setUpHttpRequestMock());
    final Service service = EasyMock.createNiceMock(Service.class);
    serviceModel.setService(service);
    EasyMock.expect(service.getRole()).andReturn(IMPALA_SERVICE_NAME).anyTimes();
    EasyMock.replay(service);
    assertEquals(String.format(Locale.ROOT, IMPALA_SERVICE_URL_TEMPLATE, SERVER_NAME, SERVER_PORT, gatewayPath, topologyName), serviceModel.getServiceUrl());
  }

  public HttpServletRequest setUpHttpRequestMock() {
    final HttpServletRequest httpServletRequestMock = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(httpServletRequestMock.getScheme()).andReturn(SERVER_SCHEME).anyTimes();
    EasyMock.expect(httpServletRequestMock.getServerName()).andReturn(SERVER_NAME).anyTimes();
    EasyMock.expect(httpServletRequestMock.getServerPort()).andReturn(SERVER_PORT).anyTimes();
    EasyMock.replay(httpServletRequestMock);
    return httpServletRequestMock;
  }

  @Test
  public void shouldReturnSimpleServiceUrl() throws Exception {
    final ServiceModel serviceModel = new ServiceModel();
    final String gatewayPath = "gateway";
    final String topologyName = "sandbox";
    serviceModel.setGatewayPath(gatewayPath);
    serviceModel.setTopologyName(topologyName);
    serviceModel.setRequest(setUpHttpRequestMock());
    final Metadata metadata = EasyMock.createNiceMock(Metadata.class);
    serviceModel.setServiceMetadata(metadata);
    final String context = "/testContext";
    EasyMock.expect(metadata.getContext()).andReturn(context).anyTimes();
    EasyMock.replay(metadata);
    assertEquals(String.format(Locale.ROOT, SERVICE_URL_TEMPLATE, SERVER_SCHEME, SERVER_NAME, SERVER_PORT, gatewayPath, topologyName, context), serviceModel.getServiceUrl());
  }

  @Test
  public void shouldReturnServiceUrlWithBackendHostPlaceholder() throws Exception {
    final ServiceModel serviceModel = new ServiceModel();
    final String gatewayPath = "gateway";
    final String topologyName = "sandbox";
    serviceModel.setGatewayPath(gatewayPath);
    serviceModel.setTopologyName(topologyName);
    serviceModel.setRequest(setUpHttpRequestMock());

    final Service service = EasyMock.createNiceMock(Service.class);
    serviceModel.setService(service);
    EasyMock.expect(service.getRole()).andReturn("service").anyTimes();
    final String backendHost = "https://localhost:5555";
    EasyMock.expect(service.getUrl()).andReturn(backendHost); // backend host comes from the service object
    final Metadata metadata = EasyMock.createNiceMock(Metadata.class);
    serviceModel.setServiceMetadata(metadata);
    final String context = "/testContext?backendHost={{BACKEND_HOST}}";
    EasyMock.expect(metadata.getContext()).andReturn(context).anyTimes();
    EasyMock.replay(service, metadata);
    assertEquals(String.format(Locale.ROOT, SERVICE_URL_TEMPLATE, SERVER_SCHEME, SERVER_NAME, SERVER_PORT, gatewayPath, topologyName,
        context.replace("{{BACKEND_HOST}}", backendHost)), serviceModel.getServiceUrl());

    final String serviceUrl = "https://serviceHost:8888";
    serviceModel.setServiceUrl(serviceUrl); // backend host comes from the given service URL
    assertEquals(
        String.format(Locale.ROOT, SERVICE_URL_TEMPLATE, SERVER_SCHEME, SERVER_NAME, SERVER_PORT, gatewayPath, topologyName, context.replace("{{BACKEND_HOST}}", serviceUrl)),
        serviceModel.getServiceUrl());
  }

  @Test
  public void shouldReturnServiceUrlWithSchemeHostAndPortPlaceholders() throws Exception {
    final ServiceModel serviceModel = new ServiceModel();
    final String gatewayPath = "gateway";
    final String topologyName = "sandbox";
    serviceModel.setGatewayPath(gatewayPath);
    serviceModel.setTopologyName(topologyName);
    serviceModel.setRequest(setUpHttpRequestMock());

    final Service service = EasyMock.createNiceMock(Service.class);
    serviceModel.setService(service);
    EasyMock.expect(service.getRole()).andReturn("service").anyTimes();
    EasyMock.expect(service.getUrl()).andReturn("https://localhost:5555");

    final Metadata metadata = EasyMock.createNiceMock(Metadata.class);
    serviceModel.setServiceMetadata(metadata);
    final String context = "/testContext?scheme={{SCHEME}}&host={{HOST}}&port={{PORT}}";
    EasyMock.expect(metadata.getContext()).andReturn(context).anyTimes();

    EasyMock.replay(service, metadata);
    assertEquals(String.format(Locale.ROOT, SERVICE_URL_TEMPLATE, SERVER_SCHEME, SERVER_NAME, SERVER_PORT, gatewayPath, topologyName,
        context.replace("{{SCHEME}}", "https").replace("{{HOST}}", "localhost").replace("{{PORT}}", "5555")), serviceModel.getServiceUrl());
  }

  @Test
  public void shouldReturnProperSampleValueEvenIfPathDoesNotStartWithSlash() throws Exception {
    final ServiceModel serviceModel = new ServiceModel();
    final String gatewayPath = "gateway";
    final String topologyName = "sandbox";
    serviceModel.setGatewayPath(gatewayPath);
    serviceModel.setTopologyName(topologyName);
    serviceModel.setRequest(setUpHttpRequestMock());

    final Metadata metadata = EasyMock.createNiceMock(Metadata.class);
    final Sample sampleStartsWithSlash = new Sample();
    sampleStartsWithSlash.setDescription("sampleStartsWithSlash");
    sampleStartsWithSlash.setMethod("PUT");
    sampleStartsWithSlash.setPath("/sampleStartsWithSlashPath/operation");
    final Sample sampleStartsWithoutSlash = new Sample();
    sampleStartsWithoutSlash.setDescription("sampleStartsWithoutSlash");
    sampleStartsWithoutSlash.setPath("sampleStartsWithoutSlashPath/operation"); // note the missing starting slash
    EasyMock.expect(metadata.getSamples()).andReturn(Arrays.asList(sampleStartsWithSlash, sampleStartsWithoutSlash)).anyTimes();
    serviceModel.setServiceMetadata(metadata);

    final String context = "/testContext";
    EasyMock.expect(metadata.getContext()).andReturn(context).anyTimes();
    EasyMock.replay(metadata);
    final List<Sample> samples = serviceModel.getSamples();
    assertNotNull(samples);
    assertFalse(samples.isEmpty());
    assertEquals(2, samples.size());

    assertEquals(samples.get(0).getValue(), "curl -iv -X PUT \"https://localhost:8443/gateway/sandbox/testContext/sampleStartsWithSlashPath/operation\"");
    assertEquals(samples.get(1).getValue(), "curl -iv -X GET \"https://localhost:8443/gateway/sandbox/testContext/sampleStartsWithoutSlashPath/operation\"");
  }
}
