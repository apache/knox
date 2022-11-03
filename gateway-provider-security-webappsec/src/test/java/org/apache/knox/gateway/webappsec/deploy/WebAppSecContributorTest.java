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
package org.apache.knox.gateway.webappsec.deploy;


import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.descriptor.FilterDescriptor;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.GatewayDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.descriptor.impl.ResourceDescriptorImpl;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

public class WebAppSecContributorTest {
  private static final String ROLE = "webappsec";
  private static final String NAME = "WebAppSec";
  private static final String CSRF_SUFFIX = "_CSRF";
  private static final String CORS_SUFFIX = "_CORS";
  private static final String XFRAME_OPTIONS_SUFFIX = "_XFRAMEOPTIONS";
  private static final String XCONTENT_TYPE_OPTIONS_SUFFIX = "_XCONTENTTYPEOPTIONS";
  private static final String XSS_PROTECTION_SUFFIX = "_XSSPROTECTION";
  private static final String STRICT_TRANSPORT_SUFFIX = "_STRICTTRANSPORT";
  private static final String RATE_LIMITING_SUFFIX = "_RATE.LIMITING";

  private DeploymentContext mockContext;
  private Topology mockTopology;
  private Provider mockProvider;
  private Service mockService;
  private GatewayDescriptor mockDescriptor;
  private ResourceDescriptor resourceDescriptor;

  private void mockContributor(Map<String, String> providerParams) {
    mockContext = EasyMock.createNiceMock(DeploymentContext.class);
    mockTopology = EasyMock.createNiceMock(Topology.class);
    mockProvider = EasyMock.createNiceMock(Provider.class);
    mockService = EasyMock.createNiceMock(Service.class);
    mockDescriptor = EasyMock.createNiceMock(GatewayDescriptor.class);
    EasyMock.expect(mockContext.getTopology()).andReturn(mockTopology).anyTimes();
    EasyMock.expect(mockTopology.getProvider(ROLE, NAME)).andReturn(mockProvider).anyTimes();
    EasyMock.expect(mockProvider.isEnabled()).andReturn(true).anyTimes();
    EasyMock.expect(mockProvider.getParams()).andReturn(providerParams).anyTimes();
    EasyMock.replay(mockContext, mockTopology, mockProvider, mockService, mockDescriptor);
    resourceDescriptor = new ResourceDescriptorImpl(mockDescriptor);
  }

  @Test
  public void testRateLimiting() {
    Map<String, String> providerParams = new HashMap<>();
    providerParams.put("rate.limiting.enabled", "true");
    providerParams.put("rate.limiting.maxRequestsPerSec", "5");
    providerParams.put("rate.limiting.delayMs", "1000");
    providerParams.put("rate.limiting.maxWaitMs", "2000");
    providerParams.put("rate.limiting.throttledRequests", "10");
    providerParams.put("rate.limiting.throttleMs", "3000");
    providerParams.put("rate.limiting.maxRequestMs", "4000");
    providerParams.put("rate.limiting.maxIdleTrackerMs", "5000");
    providerParams.put("rate.limiting.insertHeaders", "true");
    providerParams.put("rate.limiting.trackSessions", "true");
    providerParams.put("rate.limiting.remotePort", "true");
    providerParams.put("rate.limiting.ipWhitelist", "1.0.0.0,2.0.0.0");
    providerParams.put("rate.limiting.managedAttr", "true");
    mockContributor(providerParams);

    WebAppSecContributor webAppSecContributor = new WebAppSecContributor();
    webAppSecContributor.initializeContribution(mockContext);
    webAppSecContributor.contributeFilter(mockContext, mockProvider, mockService, resourceDescriptor, null);

    Assert.assertEquals(1, resourceDescriptor.filters().size());
    FilterDescriptor filterDescriptor = resourceDescriptor.filters().get(0);
    Assert.assertTrue(filterDescriptor.name().equals(NAME + RATE_LIMITING_SUFFIX));
    Assert.assertEquals(providerParams.size(), filterDescriptor.params().size());
    for (FilterParamDescriptor filterParamDescriptor : filterDescriptor.params()) {
      Assert.assertEquals(filterParamDescriptor.value(), providerParams.get("rate.limiting." + filterParamDescriptor.name()));
    }
  }

  @Test
  public void testCORS() {
    Map<String, String> providerParams = new HashMap<>();
    providerParams.put("cors.enabled", "true");
    providerParams.put("cors.allowgenerichttprequests", "true");
    providerParams.put("cors.alloworigin", "origin1 origin2");
    providerParams.put("cors.allowsubdomains", "true");
    providerParams.put("cors.supportedmethods", "GET");
    providerParams.put("cors.supportedheaders", "supportheader");
    providerParams.put("cors.exposedheaders", "exposeheader");
    providerParams.put("cors.supportscredentials", "true");
    providerParams.put("cors.maxage", "5");
    providerParams.put("cors.tagrequests", "true");
    mockContributor(providerParams);

    WebAppSecContributor webAppSecContributor = new WebAppSecContributor();
    webAppSecContributor.initializeContribution(mockContext);
    webAppSecContributor.contributeFilter(mockContext, mockProvider, mockService, resourceDescriptor, null);

    Assert.assertEquals(1, resourceDescriptor.filters().size());
    FilterDescriptor filterDescriptor = resourceDescriptor.filters().get(0);
    Assert.assertTrue(filterDescriptor.name().equals(NAME + CORS_SUFFIX));
    Assert.assertEquals(providerParams.size(), filterDescriptor.params().size());
    for (FilterParamDescriptor filterParamDescriptor : filterDescriptor.params()) {
      Assert.assertEquals(filterParamDescriptor.value(), providerParams.get(filterParamDescriptor.name()));
    }
  }

  @Test
  public void testCRSF() {
    Map<String, String> providerParams = new HashMap<>();
    providerParams.put("csrf.enabled", "true");
    providerParams.put("csrf.customheader", "X-XSRF");
    providerParams.put("csrf.methodstoignore", "GET,POST");
    mockContributor(providerParams);

    WebAppSecContributor webAppSecContributor = new WebAppSecContributor();
    webAppSecContributor.initializeContribution(mockContext);
    webAppSecContributor.contributeFilter(mockContext, mockProvider, mockService, resourceDescriptor, null);

    Assert.assertEquals(1, resourceDescriptor.filters().size());
    FilterDescriptor filterDescriptor = resourceDescriptor.filters().get(0);
    Assert.assertTrue(filterDescriptor.name().equals(NAME + CSRF_SUFFIX));
    Assert.assertEquals(providerParams.size(), filterDescriptor.params().size());
    for (FilterParamDescriptor filterParamDescriptor : filterDescriptor.params()) {
      Assert.assertEquals(filterParamDescriptor.value(), providerParams.get(filterParamDescriptor.name()));
    }
  }

  @Test
  public void testXFrame() {
    Map<String, String> providerParams = new HashMap<>();
    providerParams.put("xframe.options.enabled", "true");
    providerParams.put("xframe.value", "SAMEORIGIN");
    mockContributor(providerParams);

    WebAppSecContributor webAppSecContributor = new WebAppSecContributor();
    webAppSecContributor.initializeContribution(mockContext);
    webAppSecContributor.contributeFilter(mockContext, mockProvider, mockService, resourceDescriptor, null);

    Assert.assertEquals(1, resourceDescriptor.filters().size());
    FilterDescriptor filterDescriptor = resourceDescriptor.filters().get(0);
    Assert.assertTrue(filterDescriptor.name().equals(NAME + XFRAME_OPTIONS_SUFFIX));
    Assert.assertEquals(providerParams.size(), filterDescriptor.params().size());
    for (FilterParamDescriptor filterParamDescriptor : filterDescriptor.params()) {
      Assert.assertEquals(filterParamDescriptor.value(), providerParams.get(filterParamDescriptor.name()));
    }
  }

  @Test
  public void testXContent() {
    Map<String, String> providerParams = new HashMap<>();
    providerParams.put("xcontent-type.options.enabled", "true");
    providerParams.put("xcontent-type.options", "nosniff");
    mockContributor(providerParams);

    WebAppSecContributor webAppSecContributor = new WebAppSecContributor();
    webAppSecContributor.initializeContribution(mockContext);
    webAppSecContributor.contributeFilter(mockContext, mockProvider, mockService, resourceDescriptor, null);

    Assert.assertEquals(1, resourceDescriptor.filters().size());
    FilterDescriptor filterDescriptor = resourceDescriptor.filters().get(0);
    Assert.assertTrue(filterDescriptor.name().equals(NAME + XCONTENT_TYPE_OPTIONS_SUFFIX));
    Assert.assertEquals(providerParams.size(), filterDescriptor.params().size());
    for (FilterParamDescriptor filterParamDescriptor : filterDescriptor.params()) {
      Assert.assertEquals(filterParamDescriptor.value(), providerParams.get(filterParamDescriptor.name()));
    }
  }

  @Test
  public void testXSSProtection() {
    Map<String, String> providerParams = new HashMap<>();
    providerParams.put("xss.protection.enabled", "true");
    mockContributor(providerParams);

    WebAppSecContributor webAppSecContributor = new WebAppSecContributor();
    webAppSecContributor.initializeContribution(mockContext);
    webAppSecContributor.contributeFilter(mockContext, mockProvider, mockService, resourceDescriptor, null);

    Assert.assertEquals(1, resourceDescriptor.filters().size());
    FilterDescriptor filterDescriptor = resourceDescriptor.filters().get(0);
    Assert.assertTrue(filterDescriptor.name().equals(NAME + XSS_PROTECTION_SUFFIX));
    Assert.assertEquals(providerParams.size(), filterDescriptor.params().size());
    for (FilterParamDescriptor filterParamDescriptor : filterDescriptor.params()) {
      Assert.assertEquals(filterParamDescriptor.value(), providerParams.get(filterParamDescriptor.name()));
    }
  }

  @Test
  public void testStrictTransportSecurity() {
    Map<String, String> providerParams = new HashMap<>();
    providerParams.put("strict.transport.enabled", "true");
    providerParams.put("strict.transport", "max-age=30000");
    mockContributor(providerParams);

    WebAppSecContributor webAppSecContributor = new WebAppSecContributor();
    webAppSecContributor.initializeContribution(mockContext);
    webAppSecContributor.contributeFilter(mockContext, mockProvider, mockService, resourceDescriptor, null);

    Assert.assertEquals(1, resourceDescriptor.filters().size());
    FilterDescriptor filterDescriptor = resourceDescriptor.filters().get(0);
    Assert.assertTrue(filterDescriptor.name().equals(NAME + STRICT_TRANSPORT_SUFFIX));
    Assert.assertEquals(providerParams.size(), filterDescriptor.params().size());
    for (FilterParamDescriptor filterParamDescriptor : filterDescriptor.params()) {
      Assert.assertEquals(filterParamDescriptor.value(), providerParams.get(filterParamDescriptor.name()));
    }
  }

  @Test
  public void testAllFiltersAdded() {
    Map<String, String> providerParams = new HashMap<>();
    providerParams.put("rate.limiting.enabled", "true");
    providerParams.put("cors.enabled", "true");
    providerParams.put("csrf.enabled", "true");
    providerParams.put("xframe.options.enabled", "true");
    providerParams.put("xcontent-type.options.enabled", "true");
    providerParams.put("xss.protection.enabled", "true");
    providerParams.put("strict.transport.enabled", "true");
    mockContributor(providerParams);

    WebAppSecContributor webAppSecContributor = new WebAppSecContributor();
    webAppSecContributor.initializeContribution(mockContext);
    webAppSecContributor.contributeFilter(mockContext, mockProvider, mockService, resourceDescriptor, null);

    List<String> filterNames = Arrays.asList(NAME + RATE_LIMITING_SUFFIX, NAME + CORS_SUFFIX,
            NAME + CSRF_SUFFIX, NAME + XFRAME_OPTIONS_SUFFIX, NAME + XCONTENT_TYPE_OPTIONS_SUFFIX, NAME + XSS_PROTECTION_SUFFIX, NAME + STRICT_TRANSPORT_SUFFIX);

    Assert.assertEquals(filterNames.size(), resourceDescriptor.filters().size());

    for (FilterDescriptor filterDescriptor : resourceDescriptor.filters()) {
      Assert.assertTrue(filterNames.contains(filterDescriptor.name()));
    }
  }
}
