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
package org.apache.knox.gateway.services.factory;

import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.hamcrest.CoreMatchers.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.TestService;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.MasterService;
import org.easymock.EasyMock;
import org.junit.Rule;
import org.junit.rules.ExpectedException;

class ServiceFactoryTest {

  @SuppressWarnings("deprecation")
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  protected final GatewayServices gatewayServices = EasyMock.createNiceMock(GatewayServices.class);
  protected final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
  protected final Map<String, String> options = new HashMap<>();

  protected void initConfig() {
    final MasterService masterService = EasyMock.createNiceMock(MasterService.class);
    expect(gatewayServices.getService(ServiceType.MASTER_SERVICE)).andReturn(masterService).anyTimes();
    final KeystoreService keystoreservice = EasyMock.createNiceMock(KeystoreService.class);
    expect(gatewayServices.getService(ServiceType.KEYSTORE_SERVICE)).andReturn(keystoreservice).anyTimes();
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    expect(gatewayServices.getService(ServiceType.ALIAS_SERVICE)).andReturn(aliasService).anyTimes();
    final RemoteConfigurationRegistryClientService registryClientService = EasyMock.createNiceMock(RemoteConfigurationRegistryClientService.class);
    expect(gatewayServices.getService(ServiceType.REMOTE_REGISTRY_CLIENT_SERVICE)).andReturn(registryClientService).anyTimes();
    replay(gatewayServices);
    expect(gatewayConfig.getServiceParameter(anyString(), anyString())).andReturn("").anyTimes();
    replay(gatewayConfig);
  }

  protected void testBasics(AbstractServiceFactory serviceFactory, ServiceType nonMatchingServiceType, ServiceType matchingServiceType) throws Exception {
    shouldReturnCorrectServiceType(serviceFactory, matchingServiceType);
    shouldReturnNullForNonMatchingServiceType(serviceFactory, nonMatchingServiceType);
    shouldReturnCustomImplementation(serviceFactory, matchingServiceType);
    shouldFailWithClassNotFoundExceptionForUnknownImplementationWithClassNotOnClasspath(serviceFactory, matchingServiceType);
  }

  private void shouldReturnCorrectServiceType(AbstractServiceFactory serviceFactory, ServiceType serviceType) {
    assertEquals(serviceType, serviceFactory.getServiceType());
  }

  private void shouldReturnNullForNonMatchingServiceType(AbstractServiceFactory serviceFactory, ServiceType serviceType) throws Exception {
    assertNull(serviceFactory.create(gatewayServices, serviceType, gatewayConfig, options));
  }

  private void shouldFailWithClassNotFoundExceptionForUnknownImplementationWithClassNotOnClasspath(AbstractServiceFactory serviceFactory, ServiceType serviceType)
      throws Exception {
    expectedException.expect(ServiceLifecycleException.class);
    final String implementation = "this.is.my.non.existing.Service";
    expectedException.expectMessage(String.format(Locale.ROOT, "Error while instantiating %s service implementation %s", serviceType.getShortName(), implementation));
    expectedException.expectCause(isA(ClassNotFoundException.class));
    serviceFactory.create(gatewayServices, serviceType, null, null, implementation);
  }

  private void shouldReturnCustomImplementation(AbstractServiceFactory serviceFactory, ServiceType matchingServiceType) throws Exception {
    final Service service = serviceFactory.create(gatewayServices, matchingServiceType, null, null, "org.apache.knox.gateway.services.TestService");
    assertTrue(service instanceof TestService);
  }

  protected boolean isMasterServiceSet(Service serviceToCheck) throws Exception {
    return isServiceSet(serviceToCheck, "masterService");
  }

  protected boolean isKeystoreServiceSet(Service serviceToCheck) throws Exception {
    return isServiceSet(serviceToCheck, "keystoreService");
  }

  protected boolean isAliasServiceSet(Service serviceToCheck) throws Exception {
    return isServiceSet(serviceToCheck, "aliasService");
  }

  private boolean isServiceSet(Service serviceToCheck, String expectedServiceName) throws Exception {
    final Field aliasServiceField = FieldUtils.getDeclaredField(serviceToCheck.getClass(), expectedServiceName, true);
    final Object aliasServiceValue = aliasServiceField.get(serviceToCheck);
    return aliasServiceValue != null;
  }
}
