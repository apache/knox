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
package org.apache.knox.gateway.topology.xml;

import org.apache.commons.digester3.Digester;
import org.apache.commons.digester3.binder.DigesterLoader;
import org.apache.knox.gateway.service.definition.CustomDispatch;
import org.apache.knox.gateway.topology.Application;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.topology.Version;
import org.apache.knox.gateway.topology.builder.TopologyBuilder;
import org.apache.knox.test.TestUtils;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;

import static org.apache.commons.digester3.binder.DigesterLoader.newLoader;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertEquals;

public class TopologyRulesModuleTest {
  private static DigesterLoader loader;

  @Before
  public void setUp() {
    loader = newLoader( new KnoxFormatXmlTopologyRules(), new AmbariFormatXmlTopologyRules() );
  }

  @Test
  public void testParseSimpleTopologyXmlInKnoxFormat() throws IOException, SAXException, URISyntaxException {
    Digester digester = loader.newDigester();
    String name = "org/apache/knox/gateway/topology/xml/simple-topology-knox-format.xml";
    URL url = ClassLoader.getSystemResource( name );
    assertThat( "Failed to find URL for resource " + name, url, notNullValue() );
    File file = new File( url.getFile() );
    TopologyBuilder topologyBuilder = digester.parse( url );
    Topology topology = topologyBuilder.build();
    assertThat( "Failed to parse resource " + name, topology, notNullValue() );
    topology.setTimestamp( file.lastModified() );

    assertThat( topology.getName(), is( "topology" ) );
    assertThat( topology.getTimestamp(), is( file.lastModified() ) );
    assertThat( topology.getServices().size(), is( 3 ) );

    Service comp = topology.getServices().iterator().next();
    assertThat( comp, notNullValue() );
    assertThat( comp.getRole(), is("WEBHDFS") );
    assertThat( comp.getVersion().toString(), is( "2.4.0" ) );
    assertThat( comp.getUrls().size(), is( 2 ) );
    assertThat( comp.getUrls(), hasItem( "http://host1:80/webhdfs" ) );
    assertThat( comp.getUrls(), hasItem( "http://host2:80/webhdfs" ) );

    Provider provider = topology.getProviders().iterator().next();
    assertThat( provider, notNullValue() );
    assertThat( provider.isEnabled(), is(true) );
    assertThat( provider.getRole(), is( "authentication" ) );
    assertThat( provider.getParams().size(), is(5));

    Service service = topology.getService("WEBHDFS", "webhdfs", new Version(2,4,0));
    assertEquals(comp, service);

    comp = topology.getService("RESOURCEMANAGER", null, new Version("2.5.0"));
    assertThat( comp, notNullValue() );
    assertThat( comp.getRole(), is("RESOURCEMANAGER") );
    assertThat( comp.getVersion().toString(), is("2.5.0") );
    assertThat(comp.getUrl(), is("http://host1:8088/ws") );

    comp = topology.getService("HIVE", "hive", null);
    assertThat( comp, notNullValue() );
    assertThat( comp.getRole(), is("HIVE") );
    assertThat( comp.getName(), is("hive") );
    assertThat( comp.getUrl(), is("http://host2:10001/cliservice" ) );
  }

  @Test
  public void testParseServiceParamsInKnoxFormat() throws IOException, SAXException {
    Digester digester = loader.newDigester();
    String name = "org/apache/knox/gateway/topology/xml/service-param-topology-knox-format.xml";
    URL url = ClassLoader.getSystemResource( name );
    assertThat( "Failed to find URL for resource " + name, url, notNullValue() );
    File file = new File( url.getFile() );
    TopologyBuilder topologyBuilder = digester.parse( url );
    Topology topology = topologyBuilder.build();
    assertThat( "Failed to parse resource " + name, topology, notNullValue() );
    topology.setTimestamp( file.lastModified() );

    assertThat( topology.getName(), is( "test-topology-name" ) );
    assertThat( topology.getTimestamp(), is( file.lastModified() ) );
    assertThat( topology.getServices().size(), is( 1 ) );

    Provider provider = topology.getProviders().iterator().next();
    assertThat( provider, notNullValue() );
    assertThat( provider.getRole(), is( "test-provider-role" ) );
    assertThat( provider.getName(), is( "test-provider-name" ) );
    assertThat( provider.getParams(), hasEntry( is( "test-provider-param-name-1" ), is( "test-provider-param-value-1" ) ) );
    assertThat( provider.getParams(), hasEntry( is( "test-provider-param-name-2" ), is( "test-provider-param-value-2" ) ) );

    Service service = topology.getServices().iterator().next();
    assertThat( service, notNullValue() );
    assertThat( service.getRole(), is( "test-service-role" ) );
    assertThat( service.getUrls().size(), is( 2 ) );
    assertThat( service.getUrls(), hasItem( "test-service-scheme://test-service-host1:42/test-service-path" ) );
    assertThat( service.getUrls(), hasItem( "test-service-scheme://test-service-host2:42/test-service-path" ) );
    assertThat( service.getName(), is( "test-service-name" ) );
    assertThat( service.getParams(), hasEntry( is( "test-service-param-name-1" ), is( "test-service-param-value-1" ) ) );
    assertThat( service.getParams(), hasEntry( is( "test-service-param-name-2" ), is( "test-service-param-value-2" ) ) );
    assertThat( service.getVersion(), is( new Version(1,0,0)));
  }


  @Test
  public void testParseSimpleTopologyXmlInHadoopFormat() throws IOException, SAXException, URISyntaxException {
    Digester digester = loader.newDigester();
    String name = "org/apache/knox/gateway/topology/xml/simple-topology-ambari-format.conf";
    URL url = ClassLoader.getSystemResource( name );
    assertThat( "Failed to find URL for resource " + name, url, notNullValue() );
    File file = new File( url.getFile() );
    TopologyBuilder topologyBuilder = digester.parse( url );
    Topology topology = topologyBuilder.build();
    assertThat( "Failed to parse resource " + name, topology, notNullValue() );
    topology.setTimestamp( file.lastModified() );

    assertThat( topology.getName(), is( "topology2" ) );
    assertThat( topology.getTimestamp(), is( file.lastModified() ) );
    assertThat( topology.getServices().size(), is( 4 ) );
    assertThat( topology.getProviders().size(), is( 2 ) );

    Service webhdfsService = topology.getService( "WEBHDFS", null, null);
    assertThat( webhdfsService, notNullValue() );
    assertThat( webhdfsService.getRole(), is( "WEBHDFS" ) );
    assertThat( webhdfsService.getName(), nullValue() );
    assertThat( webhdfsService.getUrls().size(), is( 2 ) );
    assertThat( webhdfsService.getUrls(), hasItem( "http://host1:50070/webhdfs" ) );
    assertThat( webhdfsService.getUrls(), hasItem( "http://host2:50070/webhdfs" ) );

    Service webhcatService = topology.getService( "WEBHCAT", null, null);
    assertThat( webhcatService, notNullValue() );
    assertThat( webhcatService.getRole(), is( "WEBHCAT" ) );
    assertThat( webhcatService.getName(), nullValue() );
    assertThat( webhcatService.getUrls().size(), is( 1 ) );
    assertThat( webhcatService.getUrls(), hasItem( "http://host:50111/templeton" ) );

    Service oozieService = topology.getService( "OOZIE", null, null);
    assertThat( oozieService, notNullValue() );
    assertThat( oozieService.getRole(), is( "OOZIE" ) );
    assertThat( oozieService.getName(), nullValue() );
    assertThat( webhcatService.getUrls().size(), is( 1 ) );
    assertThat( oozieService.getUrls(), hasItem( "http://host:11000/oozie" ) );

    Service hiveService = topology.getService( "HIVE", null, null);
    assertThat( hiveService, notNullValue() );
    assertThat( hiveService.getRole(), is( "HIVE" ) );
    assertThat( hiveService.getName(), nullValue() );
    assertThat( webhcatService.getUrls().size(), is( 1 ) );
    assertThat( hiveService.getUrls(), hasItem( "http://host:10000" ) );

    Provider authenticationProvider = topology.getProvider( "authentication", "ShiroProvider" );
    assertThat( authenticationProvider, notNullValue() );
    assertThat( authenticationProvider.isEnabled(), is( true ) );
    assertThat( authenticationProvider.getRole(), is( "authentication" ) );
    assertThat( authenticationProvider.getName(), is( "ShiroProvider" ) );
    assertThat( authenticationProvider.getParams().size(), is( 5 ) );
    assertThat( authenticationProvider.getParams().get("main.ldapRealm.contextFactory.url"), is( "ldap://localhost:33389" ) );

    Provider identityAssertionProvider = topology.getProvider( "identity-assertion", "Default" );
    assertThat( identityAssertionProvider, notNullValue() );
    assertThat( identityAssertionProvider.isEnabled(), is( false ) );
    assertThat( identityAssertionProvider.getRole(), is( "identity-assertion" ) );
    assertThat( identityAssertionProvider.getName(), is( "Default" ) );
    assertThat( identityAssertionProvider.getParams().size(), is( 2 ) );
    assertThat( identityAssertionProvider.getParams().get("name"), is( "user.name" ) );
  }

  @Test
  public void testParseServiceParamsInAmbariFormat() throws IOException, SAXException {
    Digester digester = loader.newDigester();
    String name = "org/apache/knox/gateway/topology/xml/service-param-topology-ambari-format.conf";
    URL url = ClassLoader.getSystemResource( name );
    assertThat( "Failed to find URL for resource " + name, url, notNullValue() );
    File file = new File( url.getFile() );
    TopologyBuilder topologyBuilder = digester.parse( url );
    Topology topology = topologyBuilder.build();
    assertThat( "Failed to parse resource " + name, topology, notNullValue() );
    topology.setTimestamp( file.lastModified() );

    assertThat( topology.getName(), is( "test-topology-name" ) );
    assertThat( topology.getTimestamp(), is( file.lastModified() ) );

    assertThat( topology.getProviders().size(), is( 1 ) );
    Provider provider = topology.getProviders().iterator().next();
    assertThat( provider, notNullValue() );
    assertThat( provider.getRole(), is( "test-provider-role" ) );
    assertThat( provider.getName(), is( "test-provider-name" ) );
    assertThat( provider.isEnabled(), is( true ) );
    assertThat( provider.getParams(), hasEntry( is( "test-provider-param-name-1" ), is( "test-provider-param-value-1" ) ) );
    assertThat( provider.getParams(), hasEntry( is( "test-provider-param-name-2" ), is( "test-provider-param-value-2" ) ) );

    assertThat( topology.getServices().size(), is( 1 ) );
    Service service = topology.getServices().iterator().next();
    assertThat( service, notNullValue() );
    assertThat( service.getRole(), is( "test-service-role" ) );
    assertThat( service.getUrls().size(), is( 2 ) );
    assertThat( service.getUrls(), hasItem( "test-service-scheme://test-service-host1:42/test-service-path" ) );
    assertThat( service.getUrls(), hasItem( "test-service-scheme://test-service-host2:42/test-service-path" ) );
    assertThat( service.getName(), is( "test-service-name" ) );
    assertThat( service.getParams(), hasEntry( is( "test-service-param-name-1" ), is( "test-service-param-value-1" ) ) );
    assertThat( service.getParams(), hasEntry( is( "test-service-param-name-2" ), is( "test-service-param-value-2" ) ) );
  }

  @Test
  public void testParseTopologyWithApplication() throws IOException, SAXException {
    Digester digester = loader.newDigester();
    String name = "topology-with-application.xml";
    URL url = TestUtils.getResourceUrl( TopologyRulesModuleTest.class, name );
    assertThat( "Failed to find URL for resource " + name, url, notNullValue() );
    File file = new File( url.getFile() );
    TopologyBuilder topologyBuilder = digester.parse( url );
    Topology topology = topologyBuilder.build();
    assertThat( "Failed to parse resource " + name, topology, notNullValue() );
    topology.setTimestamp( file.lastModified() );

    Application app = topology.getApplications().iterator().next();
    assertThat( "Failed to find application", app, notNullValue() );
    assertThat( app.getName(), is("test-app-name") );
    assertThat( app.getUrl(), is("test-app-path") );
    assertThat( app.getUrls().get( 0 ), is("test-app-path") );
    assertThat( app.getParams().get( "test-param-name" ), is( "test-param-value" ) );
  }

  @Test
  public void testParseTopologyWithDispatch() throws IOException, SAXException {
    final Digester digester = loader.newDigester();
    final String name = "topology-with-dispatch.xml";
    final URL url = TestUtils.getResourceUrl( TopologyRulesModuleTest.class, name );
    assertThat( "Failed to find URL for resource " + name, url, notNullValue() );
    final File file = new File( url.getFile() );
    final TopologyBuilder topologyBuilder = digester.parse( url );
    final Topology topology = topologyBuilder.build();
    assertThat( "Failed to parse resource " + name, topology, notNullValue() );
    topology.setTimestamp( file.lastModified() );

    final Collection<Service> services =  topology.getServices();
    final CustomDispatch dispatch = services.iterator().next().getDispatch();

    assertThat( "Failed to find dispatch", dispatch, notNullValue() );
    assertThat( dispatch.getContributorName(), is("testContributor") );
    assertThat( dispatch.getHaContributorName(), is("testHAContributor") );
    assertThat( dispatch.getClassName(), is("org.apache.hadoop.gateway.hbase.HBaseDispatch") );
    assertThat( dispatch.getHaClassName(), is("testHAClassName") );
    assertThat( dispatch.getHttpClientFactory(), is("testHttpClientFactory") );
    assertThat( dispatch.getUseTwoWaySsl(), is(true) );
    assertThat( dispatch.getParams().size(), is(0) );
  }

  @Test
  public void testParseTopologyWithDispatchParameters() throws IOException, SAXException {
    final Digester digester = loader.newDigester();
    final String name = "topology-with-dispatch-parameters.xml";
    final URL url = TestUtils.getResourceUrl( TopologyRulesModuleTest.class, name );
    assertThat( "Failed to find URL for resource " + name, url, notNullValue() );
    final File file = new File( url.getFile() );
    final TopologyBuilder topologyBuilder = digester.parse( url );
    final Topology topology = topologyBuilder.build();
    assertThat( "Failed to parse resource " + name, topology, notNullValue() );
    topology.setTimestamp( file.lastModified() );

    final Collection<Service> services =  topology.getServices();
    final CustomDispatch dispatch = services.iterator().next().getDispatch();

    assertThat( "Failed to find dispatch", dispatch, notNullValue() );
    assertThat( dispatch.getContributorName(), is("testContributor") );
    assertThat( dispatch.getHaContributorName(), is("testHAContributor") );
    assertThat( dispatch.getClassName(), is("org.apache.hadoop.gateway.hbase.HBaseDispatch") );
    assertThat( dispatch.getHaClassName(), is("testHAClassName") );
    assertThat( dispatch.getHttpClientFactory(), is("testHttpClientFactory") );
    assertThat( dispatch.getUseTwoWaySsl(), is(true) );
    assertThat( dispatch.getParams().size(), is(2) );
    assertThat( dispatch.getParams().get("abc"), is("def") );
    assertThat( dispatch.getParams().get("ghi"), is("123") );
  }
}
