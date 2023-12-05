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

import static org.apache.knox.gateway.services.security.AliasService.NO_CLUSTER_NAME;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.hamcrest.CoreMatchers.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.TestService;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.services.token.impl.DerbyDBTokenStateService;
import org.apache.knox.gateway.util.JDBCUtils;
import org.apache.knox.test.TestUtils;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.ExpectedException;

class ServiceFactoryTest {

  @SuppressWarnings("deprecation")
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  protected final GatewayServices gatewayServices = EasyMock.createNiceMock(GatewayServices.class);
  protected final GatewayConfigImpl gatewayConfig = EasyMock.createNiceMock(GatewayConfigImpl.class);
  protected final Map<String, String> options = new HashMap<>();
  protected File tempDbFolder;

  protected void initConfig() {
    initConfig(false);
  }

  protected void initConfig(boolean expectDbCredentialLookup) {
    final String masterSecret = "M4st3RSecret!";
    final MasterService masterService = EasyMock.createNiceMock(MasterService.class);
    expect(masterService.getMasterSecret()).andReturn(masterSecret.toCharArray()).anyTimes();
    replay(masterService);
    expect(gatewayServices.getService(ServiceType.MASTER_SERVICE)).andReturn(masterService).anyTimes();
    final KeystoreService keystoreservice = EasyMock.createNiceMock(KeystoreService.class);
    expect(gatewayServices.getService(ServiceType.KEYSTORE_SERVICE)).andReturn(keystoreservice).anyTimes();
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    if (expectDbCredentialLookup) {
      try {
        aliasService.addAliasForCluster(NO_CLUSTER_NAME, JDBCUtils.DATABASE_USER_ALIAS_NAME, DerbyDBTokenStateService.DEFAULT_TOKEN_DB_USER_NAME);
        EasyMock.expectLastCall().anyTimes();
        aliasService.addAliasForCluster(NO_CLUSTER_NAME, JDBCUtils.DATABASE_PASSWORD_ALIAS_NAME, masterSecret);
        EasyMock.expectLastCall().anyTimes();
        expect(aliasService.getPasswordFromAliasForGateway(JDBCUtils.DATABASE_USER_ALIAS_NAME)).andReturn(DerbyDBTokenStateService.DEFAULT_TOKEN_DB_USER_NAME.toCharArray()).anyTimes();
        expect(aliasService.getPasswordFromAliasForGateway(JDBCUtils.DATABASE_PASSWORD_ALIAS_NAME)).andReturn(masterSecret.toCharArray()).anyTimes();

        // prepare GatewayConfig
        expect(gatewayConfig.getDatabaseType()).andReturn(JDBCUtils.DERBY_DB_TYPE).anyTimes();
        tempDbFolder = TestUtils.createTempDir(this.getClass().getName());
        expect(gatewayConfig.getGatewaySecurityDir()).andReturn(tempDbFolder.getAbsolutePath()).anyTimes();
        expect(gatewayConfig.getDatabaseName()).andReturn(Paths.get(tempDbFolder.getAbsolutePath(), DerbyDBTokenStateService.DB_NAME).toString()).anyTimes();
      } catch (AliasServiceException | IOException e) {
        // NOP
      }
    }
    replay(aliasService);
    expect(gatewayServices.getService(ServiceType.ALIAS_SERVICE)).andReturn(aliasService).anyTimes();
    final RemoteConfigurationRegistryClientService registryClientService = EasyMock.createNiceMock(RemoteConfigurationRegistryClientService.class);
    expect(gatewayServices.getService(ServiceType.REMOTE_REGISTRY_CLIENT_SERVICE)).andReturn(registryClientService).anyTimes();
    replay(gatewayServices);
    expect(gatewayConfig.getServiceParameter(anyString(), anyString())).andReturn("").anyTimes();
    replay(gatewayConfig);
  }

  @After
  public void tearDown() throws IOException {
    if (tempDbFolder != null) {
      FileUtils.forceDelete(tempDbFolder);
    }
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

  protected boolean isAliasServiceSetOnParent(Service serviceToCheck) throws Exception {
    return isServiceSetOnParent(serviceToCheck, "aliasService");
  }

  private boolean isServiceSet(Service serviceToCheck, String expectedServiceName) throws Exception {
    return isServiceSet(serviceToCheck.getClass(), serviceToCheck, expectedServiceName);
  }

  private boolean isServiceSetOnParent(Service serviceToCheck, String expectedServiceName) throws Exception {
    return isServiceSet(serviceToCheck.getClass().getSuperclass(), serviceToCheck, expectedServiceName);
  }

  private boolean isServiceSet(Class<?> clazz, Service serviceToCheck, String expectedServiceName) throws Exception {
    final Field expectedServiceField = FieldUtils.getDeclaredField(clazz, expectedServiceName, true);
    final Object expectedServiceValue = expectedServiceField.get(serviceToCheck);
    return expectedServiceValue != null;
  }
}
