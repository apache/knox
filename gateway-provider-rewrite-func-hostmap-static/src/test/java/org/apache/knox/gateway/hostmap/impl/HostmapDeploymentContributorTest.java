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
package org.apache.knox.gateway.hostmap.impl;

import org.apache.commons.io.IOUtils;
import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.ProviderDeploymentContributor;
import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteRulesDescriptorImpl;
import org.apache.knox.gateway.hostmap.api.HostmapFunctionDescriptor;
import org.apache.knox.gateway.topology.Provider;
import org.easymock.EasyMock;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class HostmapDeploymentContributorTest {

  @Test
  public void testServiceLoader() throws Exception {
    ServiceLoader loader = ServiceLoader.load( ProviderDeploymentContributor.class );
    Iterator iterator = loader.iterator();
    assertThat( "Service iterator empty.", iterator.hasNext() );
    while( iterator.hasNext() ) {
      Object object = iterator.next();
      if( object instanceof HostmapDeploymentContributor ) {
        return;
      }
    }
    fail( "Failed to find " + HostmapDeploymentContributor.class.getName() + " via service loader." );
  }

  @Test
  public void testDeployment() throws IOException {
    WebArchive webArchive = ShrinkWrap.create( WebArchive.class, "test-acrhive" );

    UrlRewriteRulesDescriptorImpl rewriteRules = new UrlRewriteRulesDescriptorImpl();

    Map<String,String> providerParams = new HashMap<>();
    providerParams.put( "test-host-external", "test-host-internal" );
    Provider provider = new Provider();
    provider.setEnabled( true );
    provider.setName( "hostmap" );
    provider.setParams(  providerParams );

    DeploymentContext context = EasyMock.createNiceMock( DeploymentContext.class );
    EasyMock.expect( context.getDescriptor( "rewrite" ) ).andReturn( rewriteRules ).anyTimes();
    EasyMock.expect( context.getWebArchive() ).andReturn( webArchive ).anyTimes();
    EasyMock.replay( context );

    HostmapDeploymentContributor contributor = new HostmapDeploymentContributor();

    assertThat( contributor.getRole(), is("hostmap") );
    assertThat( contributor.getName(), is( "static" ) );

    // Just make sure it doesn't blow up.
    contributor.contributeFilter( null, null, null, null, null );

    // Just make sure it doesn't blow up.
    contributor.initializeContribution( context );

    contributor.contributeProvider( context, provider );

    HostmapFunctionDescriptor funcDesc = rewriteRules.getFunction( "hostmap" );
    assertThat( funcDesc.config(), is( "/WEB-INF/hostmap.txt" ) );

    Node node = webArchive.get( "/WEB-INF/hostmap.txt" );
    String asset = IOUtils.toString( node.getAsset().openStream(), StandardCharsets.UTF_8 );
    assertThat( asset, containsString( "test-host-external=test-host-internal" ) );

    // Just make sure it doesn't blow up.
    contributor.finalizeContribution( context );

  }

}
