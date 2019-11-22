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
package org.apache.knox.gateway.svcregfunc.impl;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriter;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.apache.knox.gateway.ha.provider.HaProvider;
import org.apache.knox.gateway.ha.provider.HaServletContextListener;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.hostmap.HostMapper;
import org.apache.knox.gateway.services.hostmap.HostMapperService;
import org.apache.knox.gateway.services.registry.ServiceRegistry;
import org.apache.knox.gateway.svcregfunc.api.ServiceMappedHostFunctionDescriptor;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.ServiceLoader;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.Assert.fail;

public class ServiceMappedHostFunctionProcessorTest {

  HostMapperService hms;
  HostMapper hm;
  ServiceRegistry reg;
  GatewayServices svc;
  UrlRewriteEnvironment env;
  UrlRewriteContext ctx;
  ServiceMappedHostFunctionDescriptor desc;

  @Before
  public void setUp() {
    hm = EasyMock.createNiceMock( HostMapper.class );
    EasyMock.expect( hm.resolveInboundHostName( "test-host" ) ).andReturn( "test-internal-host" ).anyTimes();

    hms = EasyMock.createNiceMock( HostMapperService.class );
    EasyMock.expect( hms.getHostMapper( "test-cluster" ) ).andReturn( hm ).anyTimes();

    reg = EasyMock.createNiceMock( ServiceRegistry.class );
    EasyMock.expect( reg.lookupServiceURL( "test-cluster", "test-service" ) ).andReturn( "test-scheme://test-host:777/test-path" ).anyTimes();

    svc = EasyMock.createNiceMock( GatewayServices.class );
    EasyMock.expect( svc.getService( ServiceType.SERVICE_REGISTRY_SERVICE ) ).andReturn( reg ).anyTimes();
    EasyMock.expect( svc.getService( ServiceType.HOST_MAPPING_SERVICE ) ).andReturn( hms ).anyTimes();

    env = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    EasyMock.expect( env.getAttribute( GatewayServices.GATEWAY_SERVICES_ATTRIBUTE ) ).andReturn( svc ).anyTimes();
    EasyMock.expect( env.getAttribute( GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE ) ).andReturn( "test-cluster" ).anyTimes();

    ctx = EasyMock.createNiceMock( UrlRewriteContext.class );
    EasyMock.expect( ctx.getDirection() ).andReturn( UrlRewriter.Direction.IN ).anyTimes();

    desc = EasyMock.createNiceMock( ServiceMappedHostFunctionDescriptor.class );

     HaProvider haProvider = EasyMock.createNiceMock( HaProvider.class );

     EasyMock.expect(env.getAttribute(HaServletContextListener.PROVIDER_ATTRIBUTE_NAME)).andReturn(haProvider).anyTimes();

     EasyMock.expect(haProvider.isHaEnabled(EasyMock.anyObject(String.class))).andReturn(Boolean.FALSE).anyTimes();

     EasyMock.replay( hm, hms, reg, svc, env, desc, ctx, haProvider );
  }

  @Test
  public void testServiceLoader() throws Exception {
    ServiceLoader loader = ServiceLoader.load( UrlRewriteFunctionProcessor.class );
    Iterator iterator = loader.iterator();
    assertThat( "Service iterator empty.", iterator.hasNext() );
    while( iterator.hasNext() ) {
      Object object = iterator.next();
      if( object instanceof ServiceMappedHostFunctionProcessor ) {
        return;
      }
    }
    fail( "Failed to find " + ServiceMappedHostFunctionProcessor.class.getName() + " via service loader." );
  }

  @Test
  public void testName() throws Exception {
    ServiceMappedHostFunctionProcessor func = new ServiceMappedHostFunctionProcessor();
    assertThat( func.name(), is( "serviceMappedHost" ) );
  }

  @Test
  public void testInitialize() throws Exception {
    ServiceMappedHostFunctionProcessor func = new ServiceMappedHostFunctionProcessor();
    try {
      func.initialize( null, desc );
      fail( "Should have thrown an IllegalArgumentException" );
    } catch( IllegalArgumentException e ) {
      assertThat( e.getMessage(), containsString( "environment" ) );
    }

    func = new ServiceMappedHostFunctionProcessor();
    try {
      func.initialize( env, null );
    } catch( Exception e ) {
      e.printStackTrace();
      fail( "Should not have thrown an exception" );
    }

    func.initialize( env, desc );

    assertThat( func.cluster(), is( "test-cluster" ) );
    assertThat( func.registry(), sameInstance( reg ) );
  }

  @Test
  public void testDestroy() throws Exception {
    ServiceMappedHostFunctionProcessor func = new ServiceMappedHostFunctionProcessor();
    func.initialize( env, desc );
    func.destroy();

    assertThat( func.cluster(), nullValue() );
    assertThat( func.registry(), nullValue() );
  }

  @Test
  public void testResolve() throws Exception {
    ServiceMappedHostFunctionProcessor func = new ServiceMappedHostFunctionProcessor();
    func.initialize( env, desc );

    assertThat( func.resolve( ctx, Collections.singletonList("test-service")), contains( "test-internal-host" ) );
    assertThat( func.resolve( ctx, Collections.singletonList("invalid-test-service")), contains( "invalid-test-service" ) );
    assertThat( func.resolve( ctx, null ), nullValue() );

    func.destroy();
  }

}
