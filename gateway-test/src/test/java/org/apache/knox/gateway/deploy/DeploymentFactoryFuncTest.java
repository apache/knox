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
package org.apache.knox.gateway.deploy;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.GatewayTestConfig;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.filter.XForwardedHeaderFilter;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletFilter;
import org.apache.knox.gateway.services.DefaultGatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.topology.Application;
import org.apache.knox.gateway.topology.Param;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.util.XmlUtils;
import org.apache.knox.test.TestUtils;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import static org.apache.knox.test.TestUtils.LOG_ENTER;
import static org.apache.knox.test.TestUtils.LOG_EXIT;
import static org.apache.knox.test.TestUtils.LONG_TIMEOUT;
import static org.apache.knox.test.TestUtils.MEDIUM_TIMEOUT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class DeploymentFactoryFuncTest {

  @Test( timeout = LONG_TIMEOUT )
  public void testGenericProviderDeploymentContributor() throws ParserConfigurationException, SAXException, IOException {
    LOG_ENTER();
    GatewayTestConfig config = new GatewayTestConfig();
    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File gatewayDir = new File( targetDir, "gateway-home-" + UUID.randomUUID() );
    gatewayDir.mkdirs();

    config.setGatewayHomeDir( gatewayDir.getAbsolutePath() );

    File deployDir = new File( config.getGatewayDeploymentDir() );
    deployDir.mkdirs();

    //    ((GatewayTestConfig) config).setDeploymentDir( "clusters" );

    DefaultGatewayServices srvcs = new DefaultGatewayServices();
    Map<String,String> options = new HashMap<>();
    options.put("persist-master", "false");
    options.put("master", "password");
    try {
      DeploymentFactory.setGatewayServices(srvcs);
      srvcs.init(config, options);
    } catch (ServiceLifecycleException e) {
      e.printStackTrace(); // I18N not required.
    }

    Topology topology = new Topology();
    topology.setName( "test-cluster" );
    Service service = new Service();
    service.setRole( "WEBHDFS" );
    service.addUrl( "http://localhost:50070/test-service-url" );
    topology.addService( service );

    Provider provider = new Provider();
    provider.setRole( "federation" );
    provider.setName( "HeaderPreAuth" );
    provider.setEnabled( true );
    Param param = new Param();
    param.setName( "filter" );
    param.setValue( "org.opensource.ExistingFilter" );
    provider.addParam( param );
    param = new Param();
    param.setName( "test-param-name" );
    param.setValue( "test-param-value" );
    provider.addParam( param );
    topology.addProvider( provider );

    EnterpriseArchive war = DeploymentFactory.createDeployment( config, topology );

    Document gateway = XmlUtils.readXml( war.get( "%2F/WEB-INF/gateway.xml" ).getAsset().openStream() );

    //by default the first filter will be the X-Forwarded header filter
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[1]/role", equalTo( "xforwardedheaders" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[1]/name", equalTo( "XForwardedHeaderFilter" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[1]/class", equalTo( "org.apache.knox.gateway.filter.XForwardedHeaderFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/role", equalTo( "federation" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/name", equalTo( "HeaderPreAuth" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/class", equalTo( "org.apache.knox.gateway.preauth.filter.HeaderPreAuthFederationFilter" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/param[1]/name", equalTo( "filter" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/param[1]/value", equalTo( "org.opensource.ExistingFilter" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/param[2]/name", equalTo( "test-param-name" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/param[2]/value", equalTo( "test-param-value" ) ) );

    // testing for the adding of missing identity assertion provider - since it isn't explicitly added above
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[4]/role", equalTo( "identity-assertion" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[4]/name", equalTo( "Default" ) ) );

    LOG_EXIT();
  }

  @Test( timeout = LONG_TIMEOUT )
  public void testInvalidGenericProviderDeploymentContributor() {
    LOG_ENTER();
    GatewayTestConfig config = new GatewayTestConfig();
    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File gatewayDir = new File( targetDir, "gateway-home-" + UUID.randomUUID() );
    gatewayDir.mkdirs();
    config.setGatewayHomeDir( gatewayDir.getAbsolutePath() );
    File deployDir = new File( config.getGatewayDeploymentDir() );
    deployDir.mkdirs();

    DefaultGatewayServices srvcs = new DefaultGatewayServices();
    Map<String,String> options = new HashMap<>();
    options.put("persist-master", "false");
    options.put("master", "password");
    try {
      DeploymentFactory.setGatewayServices(srvcs);
      srvcs.init(config, options);
    } catch (ServiceLifecycleException e) {
      e.printStackTrace(); // I18N not required.
    }

    Topology topology = new Topology();
    topology.setName( "test-cluster" );
    Service service = new Service();
    service.setRole( "WEBHDFS" );
    service.addUrl( "http://localhost:50070/test-service-url" );
    topology.addService( service );

    Provider provider = new Provider();
    provider.setRole( "authentication" );
    provider.setName( "generic" );
    provider.setEnabled( true );
    Param param = new Param();
    param.setName( "test-param-name" );
    param.setValue( "test-param-value" );
    provider.addParam( param );
    topology.addProvider( provider );

    try {
      DeploymentFactory.createDeployment( config, topology );
      fail( "Should have throws IllegalArgumentException" );
    } catch ( DeploymentException e ) {
      // Expected.
      assertEquals("Failed to contribute provider. Role: authentication Name: generic. " +
                       "Please check the topology for errors in name and role and that " +
                       "the provider is on the classpath.",
          e.getMessage());
    }
    LOG_EXIT();
  }

  @Test( timeout = LONG_TIMEOUT )
  public void testSimpleTopology() throws IOException, SAXException, ParserConfigurationException {
    LOG_ENTER();
    GatewayTestConfig config = new GatewayTestConfig();
    //Testing without x-forwarded headers filter
    config.setXForwardedEnabled(false);
    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File gatewayDir = new File( targetDir, "gateway-home-" + UUID.randomUUID() );
    gatewayDir.mkdirs();
    config.setGatewayHomeDir( gatewayDir.getAbsolutePath() );
    File deployDir = new File( config.getGatewayDeploymentDir() );
    deployDir.mkdirs();

    DefaultGatewayServices srvcs = new DefaultGatewayServices();
    Map<String,String> options = new HashMap<>();
    options.put("persist-master", "false");
    options.put("master", "password");
    try {
      DeploymentFactory.setGatewayServices(srvcs);
      srvcs.init(config, options);
    } catch (ServiceLifecycleException e) {
      e.printStackTrace(); // I18N not required.
    }

    Topology topology = new Topology();
    topology.setName( "test-cluster" );
    Service service = new Service();
    service.setRole( "WEBHDFS" );
    service.addUrl( "http://localhost:50070/webhdfs" );
    topology.addService( service );
    Provider provider = new Provider();
    provider.setRole( "authentication" );
    provider.setName( "ShiroProvider" );
    provider.setEnabled( true );
    Param param = new Param();
    param.setName( "contextConfigLocation" );
    param.setValue( "classpath:app-context-security.xml" );
    provider.addParam( param );
    topology.addProvider( provider );
    Provider asserter = new Provider();
    asserter.setRole( "identity-assertion" );
    asserter.setName("Default");
    asserter.setEnabled( true );
    topology.addProvider( asserter );
    Provider authorizer = new Provider();
    authorizer.setRole( "authorization" );
    authorizer.setName("AclsAuthz");
    authorizer.setEnabled( true );
    topology.addProvider( authorizer );

    EnterpriseArchive war = DeploymentFactory.createDeployment( config, topology );
    //    File dir = new File( System.getProperty( "user.dir" ) );
    //    File file = war.as( ExplodedExporter.class ).exportExploded( dir, "test-cluster.war" );

    Document web = XmlUtils.readXml( war.get( "%2F/WEB-INF/web.xml" ).getAsset().openStream() );
    assertThat( web, hasXPath( "/web-app" ) );
    assertThat( web, hasXPath( "/web-app/servlet" ) );
    assertThat( web, hasXPath( "/web-app/servlet/servlet-name" ) );
    assertThat( web, hasXPath( "/web-app/servlet/servlet-name", equalTo( "test-cluster-knox-gateway-servlet" ) ) );
    assertThat( web, hasXPath( "/web-app/servlet/servlet-class", equalTo( "org.apache.knox.gateway.GatewayServlet" ) ) );
    assertThat( web, hasXPath( "/web-app/servlet/init-param/param-name", equalTo( "gatewayDescriptorLocation" ) ) );
    assertThat( web, hasXPath( "/web-app/servlet/init-param/param-value", equalTo( "/WEB-INF/gateway.xml" ) ) );
    assertThat( web, hasXPath( "/web-app/servlet-mapping/servlet-name", equalTo( "test-cluster-knox-gateway-servlet" ) ) );
    assertThat( web, hasXPath( "/web-app/servlet-mapping/url-pattern", equalTo( "/*" ) ) );

    Document gateway = XmlUtils.readXml( war.get( "%2F/WEB-INF/gateway.xml" ).getAsset().openStream() );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/pattern", equalTo( "/webhdfs/v1/?**" ) ) );
    //assertThat( gateway, hasXPath( "/gateway/resource[1]/target", equalTo( "http://localhost:50070/webhdfs/v1/?{**}" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[1]/role", equalTo( "authentication" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[1]/class", equalTo( "org.apache.knox.gateway.filter.ResponseCookieFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/role", equalTo( "authentication" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/class", equalTo( "org.apache.shiro.web.servlet.ShiroFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[3]/role", equalTo( "authentication" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[3]/class", equalTo( "org.apache.knox.gateway.filter.ShiroSubjectIdentityAdapter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[4]/role", equalTo( "rewrite" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[4]/class", equalTo( "org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[5]/role", equalTo( "identity-assertion" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[5]/class", equalTo( "org.apache.knox.gateway.identityasserter.filter.IdentityAsserterFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[6]/role", equalTo( "authorization" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[6]/name", equalTo( "AclsAuthz" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[6]/class", equalTo( "org.apache.knox.gateway.filter.AclsAuthorizationFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[7]/role", equalTo( "dispatch" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[7]/name", equalTo( "webhdfs" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[7]/class", equalTo( "org.apache.knox.gateway.dispatch.GatewayDispatchFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[2]/pattern", equalTo( "/webhdfs/v1/**?**" ) ) );
    //assertThat( gateway, hasXPath( "/gateway/resource[2]/target", equalTo( "http://localhost:50070/webhdfs/v1/{path=**}?{**}" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[1]/role", equalTo( "authentication" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[1]/class", equalTo( "org.apache.knox.gateway.filter.ResponseCookieFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[2]/role", equalTo( "authentication" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[2]/class", equalTo( "org.apache.shiro.web.servlet.ShiroFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[3]/role", equalTo( "authentication" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[3]/class", equalTo( "org.apache.knox.gateway.filter.ShiroSubjectIdentityAdapter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[4]/role", equalTo( "rewrite" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[4]/class", equalTo( "org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[5]/role", equalTo( "identity-assertion" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[5]/class", equalTo( "org.apache.knox.gateway.identityasserter.filter.IdentityAsserterFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[6]/role", equalTo( "authorization" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[6]/name", equalTo( "AclsAuthz" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[6]/class", equalTo( "org.apache.knox.gateway.filter.AclsAuthorizationFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[7]/role", equalTo( "dispatch" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[7]/name", equalTo( "webhdfs" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[7]/class", equalTo( "org.apache.knox.gateway.dispatch.GatewayDispatchFilter" ) ) );

    LOG_EXIT();
  }


  @Test( timeout = LONG_TIMEOUT )
  public void testWebXmlGeneration() throws IOException, SAXException, ParserConfigurationException {
    LOG_ENTER();
    GatewayTestConfig config = new GatewayTestConfig();
    File targetDir = new File(System.getProperty("user.dir"), "target");
    File gatewayDir = new File(targetDir, "gateway-home-" + UUID.randomUUID());
    gatewayDir.mkdirs();
    config.setGatewayHomeDir(gatewayDir.getAbsolutePath());
    File deployDir = new File(config.getGatewayDeploymentDir());
    deployDir.mkdirs();

    DefaultGatewayServices srvcs = new DefaultGatewayServices();
    Map<String, String> options = new HashMap<>();
    options.put("persist-master", "false");
    options.put("master", "password");
    try {
      DeploymentFactory.setGatewayServices(srvcs);
      srvcs.init(config, options);
    } catch (ServiceLifecycleException e) {
      e.printStackTrace(); // I18N not required.
    }

    Topology topology = new Topology();
    topology.setName("test-cluster");
    Service service = new Service();
    service.setRole("WEBHDFS");
    service.addUrl("http://localhost:50070/webhdfs");
    topology.addService(service);
    Provider provider = new Provider();
    provider.setRole("authentication");
    provider.setName("ShiroProvider");
    provider.setEnabled(true);
    Param param = new Param();
    param.setName("contextConfigLocation");
    param.setValue("classpath:app-context-security.xml");
    provider.addParam(param);
    topology.addProvider(provider);
    Provider asserter = new Provider();
    asserter.setRole("identity-assertion");
    asserter.setName("Default");
    asserter.setEnabled(true);
    topology.addProvider(asserter);
    Provider authorizer = new Provider();
    authorizer.setRole("authorization");
    authorizer.setName("AclsAuthz");
    authorizer.setEnabled(true);
    topology.addProvider(authorizer);
    Provider ha = new Provider();
    ha.setRole("ha");
    ha.setName("HaProvider");
    ha.setEnabled(true);
    topology.addProvider(ha);

    for (int i = 0; i < 10; i++) {
      createAndTestDeployment(config, topology);
    }
    LOG_EXIT();
  }

  private void createAndTestDeployment(GatewayConfig config, Topology topology) throws IOException, SAXException, ParserConfigurationException {

    EnterpriseArchive war = DeploymentFactory.createDeployment(config, topology);
    //      File dir = new File( System.getProperty( "user.dir" ) );
    //      File file = war.as( ExplodedExporter.class ).exportExploded( dir, "test-cluster.war" );

    Document web = XmlUtils.readXml(war.get("%2F/WEB-INF/web.xml").getAsset().openStream());
    assertThat(web, hasXPath("/web-app/servlet/servlet-class", equalTo("org.apache.knox.gateway.GatewayServlet")));
    assertThat(web, hasXPath("/web-app/servlet/init-param/param-name", equalTo("gatewayDescriptorLocation")));
    assertThat(web, hasXPath("/web-app/servlet/init-param/param-value", equalTo("/WEB-INF/gateway.xml")));
    assertThat(web, hasXPath("/web-app/servlet-mapping/servlet-name", equalTo("test-cluster-knox-gateway-servlet")));
    assertThat(web, hasXPath("/web-app/servlet-mapping/url-pattern", equalTo("/*")));
    //testing the order of listener classes generated
    assertThat(web, hasXPath("/web-app/listener[2]/listener-class", equalTo("org.apache.knox.gateway.services.GatewayServicesContextListener")));
    assertThat(web, hasXPath("/web-app/listener[3]/listener-class", equalTo("org.apache.knox.gateway.services.GatewayMetricsServletContextListener")));
    assertThat(web, hasXPath("/web-app/listener[4]/listener-class", equalTo("org.apache.knox.gateway.ha.provider" +
        ".HaServletContextListener")));
    assertThat(web, hasXPath("/web-app/listener[5]/listener-class", equalTo("org.apache.knox.gateway.filter" +
        ".rewrite.api.UrlRewriteServletContextListener")));
  }

  @Test( timeout = LONG_TIMEOUT )
  public void testDeploymentWithServiceParams() throws Exception {
    LOG_ENTER();
    GatewayTestConfig config = new GatewayTestConfig();
    File targetDir = new File(System.getProperty("user.dir"), "target");
    File gatewayDir = new File(targetDir, "gateway-home-" + UUID.randomUUID());
    gatewayDir.mkdirs();
    config.setGatewayHomeDir(gatewayDir.getAbsolutePath());
    File deployDir = new File(config.getGatewayDeploymentDir());
    deployDir.mkdirs();

    DefaultGatewayServices srvcs = new DefaultGatewayServices();
    Map<String, String> options = new HashMap<>();
    options.put("persist-master", "false");
    options.put("master", "password");
    try {
      DeploymentFactory.setGatewayServices(srvcs);
      srvcs.init(config, options);
    } catch (ServiceLifecycleException e) {
      e.printStackTrace(); // I18N not required.
    }

    Service service;
    Param param;
    Topology topology = new Topology();
    topology.setName( "test-cluster" );

    service = new Service();
    service.setRole( "HIVE" );
    service.setUrls(Collections.singletonList("http://hive-host:50001/"));
    param = new Param();
    param.setName( "someparam" );
    param.setValue( "somevalue" );
    service.addParam( param );
    topology.addService( service );

    service = new Service();
    service.setRole( "WEBHBASE" );
    service.setUrls(Collections.singletonList("http://hbase-host:50002/"));
    param = new Param();
    param.setName( "replayBufferSize" );
    param.setValue( "33" );
    service.addParam( param );
    topology.addService( service );

    service = new Service();
    service.setRole( "OOZIE" );
    service.setUrls(Collections.singletonList("http://hbase-host:50003/"));
    param = new Param();
    param.setName( "otherparam" );
    param.setValue( "65" );
    service.addParam( param );
    topology.addService( service );

    EnterpriseArchive war = DeploymentFactory.createDeployment( config, topology );
    Document doc = XmlUtils.readXml( war.get( "%2F/WEB-INF/gateway.xml" ).getAsset().openStream() );

    Node resourceNode, filterNode, paramNode;
    String value;

    resourceNode = node( doc, "gateway/resource[role/text()='HIVE']" );
    assertThat( resourceNode, is(not(nullValue())));
    filterNode = node( resourceNode, "filter[role/text()='dispatch']" );
    assertThat( filterNode, is(not(nullValue())));
    paramNode = node( filterNode, "param[name/text()='someparam']" );
    value = value( paramNode, "value/text()" );
    assertThat( value, is( "somevalue" ) ) ;

    resourceNode = node( doc, "gateway/resource[role/text()='WEBHBASE']" );
    assertThat( resourceNode, is(not(nullValue())));
    filterNode = node( resourceNode, "filter[role/text()='dispatch']" );
    assertThat( filterNode, is(not(nullValue())));
    paramNode = node( filterNode, "param[name/text()='replayBufferSize']" );
    value = value( paramNode, "value/text()" );
    assertThat( value, is( "33" ) ) ;

    resourceNode = node( doc, "gateway/resource[role/text()='OOZIE']" );
    assertThat( resourceNode, is(not(nullValue())));
    filterNode = node( resourceNode, "filter[role/text()='dispatch']" );
    assertThat( filterNode, is(not(nullValue())));
    paramNode = node( filterNode, "param[name/text()='otherparam']" );
    value = value( paramNode, "value/text()" );
    assertThat( value, is( "65" ) ) ;

    FileUtils.deleteQuietly( deployDir );

    LOG_EXIT();
  }

  @Test( timeout = MEDIUM_TIMEOUT )
  public void testDeploymentWithApplication() throws Exception {
    LOG_ENTER();
    GatewayTestConfig config = new GatewayTestConfig();
    File targetDir = new File(System.getProperty("user.dir"), "target");
    File gatewayDir = new File(targetDir, "gateway-home-" + UUID.randomUUID());
    gatewayDir.mkdirs();
    config.setGatewayHomeDir(gatewayDir.getAbsolutePath());
    File deployDir = new File(config.getGatewayDeploymentDir());
    deployDir.mkdirs();
    URL serviceUrl = TestUtils.getResourceUrl( DeploymentFactoryFuncTest.class, "test-apps/minimal-test-app/service.xml" );
    File serviceFile = new File( serviceUrl.toURI() );
    File appsDir = serviceFile.getParentFile().getParentFile();
    config.setGatewayApplicationsDir(appsDir.getAbsolutePath());

    DefaultGatewayServices srvcs = new DefaultGatewayServices();
    Map<String, String> options = new HashMap<>();
    options.put("persist-master", "false");
    options.put("master", "password");
    try {
      DeploymentFactory.setGatewayServices(srvcs);
      srvcs.init(config, options);
    } catch (ServiceLifecycleException e) {
      e.printStackTrace(); // I18N not required.
    }

    Topology topology = new Topology();
    topology.setName( "test-topology" );

    Application app;

    app = new Application();
    app.setName( "minimal-test-app" );
    app.addUrl( "/minimal-test-app-path" );
    topology.addApplication( app );

    EnterpriseArchive archive = DeploymentFactory.createDeployment( config, topology );
    assertThat( archive, notNullValue() );

    Document doc;

    doc = XmlUtils.readXml( archive.get( "META-INF/topology.xml" ).getAsset().openStream() );
    assertThat( doc, notNullValue() );

    doc = XmlUtils.readXml( archive.get( "%2Fminimal-test-app-path/WEB-INF/gateway.xml" ).getAsset().openStream() );
    assertThat( doc, notNullValue() );
    assertThat( doc, hasXPath("/gateway/resource/pattern", equalTo("/**?**")));
    assertThat( doc, hasXPath("/gateway/resource/filter[1]/role", equalTo("xforwardedheaders")));
    assertThat( doc, hasXPath("/gateway/resource/filter[1]/name", equalTo("XForwardedHeaderFilter")));
    assertThat( doc, hasXPath("/gateway/resource/filter[1]/class", equalTo(XForwardedHeaderFilter.class.getName())));
    assertThat( doc, hasXPath("/gateway/resource/filter[2]/role", equalTo("rewrite")));
    assertThat( doc, hasXPath("/gateway/resource/filter[2]/name", equalTo("url-rewrite")));
    assertThat( doc, hasXPath("/gateway/resource/filter[2]/class", equalTo(UrlRewriteServletFilter.class.getName())));

    LOG_EXIT();
  }

  @Test( timeout = MEDIUM_TIMEOUT )
  public void testDeploymentWithServicesAndApplications() throws Exception {
    LOG_ENTER();
    GatewayTestConfig config = new GatewayTestConfig();
    File targetDir = new File(System.getProperty("user.dir"), "target");
    File gatewayDir = new File(targetDir, "gateway-home-" + UUID.randomUUID());
    gatewayDir.mkdirs();
    config.setGatewayHomeDir(gatewayDir.getAbsolutePath());
    File deployDir = new File(config.getGatewayDeploymentDir());
    deployDir.mkdirs();
    URL serviceUrl = TestUtils.getResourceUrl( DeploymentFactoryFuncTest.class, "test-apps/minimal-test-app/service.xml" );
    File serviceFile = new File( serviceUrl.toURI() );
    File appsDir = serviceFile.getParentFile().getParentFile();
    config.setGatewayApplicationsDir(appsDir.getAbsolutePath());

    DefaultGatewayServices srvcs = new DefaultGatewayServices();
    Map<String, String> options = new HashMap<>();
    options.put("persist-master", "false");
    options.put("master", "password");
    try {
      DeploymentFactory.setGatewayServices(srvcs);
      srvcs.init(config, options);
    } catch (ServiceLifecycleException e) {
      e.printStackTrace(); // I18N not required.
    }

    Topology topology = new Topology();
    topology.setName( "test-topology" );

    Application app;

    topology.setName( "test-cluster" );
    Service service = new Service();
    service.setRole( "WEBHDFS" );
    service.addUrl( "http://localhost:50070/test-service-url" );
    topology.addService( service );

    app = new Application();
    app.setName( "minimal-test-app" );
    app.addUrl( "/minimal-test-app-path-one" );
    topology.addApplication( app );

    app.setName( "minimal-test-app" );
    app.addUrl( "/minimal-test-app-path-two" );
    topology.addApplication( app );

    EnterpriseArchive archive = DeploymentFactory.createDeployment( config, topology );
    assertThat( archive, notNullValue() );

    Document doc;
    org.jboss.shrinkwrap.api.Node node;

    node = archive.get( "META-INF/topology.xml" );
    assertThat( "Find META-INF/topology.xml", node, notNullValue() );
    doc = XmlUtils.readXml( node.getAsset().openStream() );
    assertThat( "Parse META-INF/topology.xml", doc, notNullValue() );

    node = archive.get( "%2F" );
    assertThat( "Find %2F", node, notNullValue() );
    node = archive.get( "%2F/WEB-INF/gateway.xml" );
    assertThat( "Find %2F/WEB-INF/gateway.xml", node, notNullValue() );
    doc = XmlUtils.readXml( node.getAsset().openStream() );
    assertThat( "Parse %2F/WEB-INF/gateway.xml", doc, notNullValue() );

    WebArchive war = archive.getAsType( WebArchive.class, "%2Fminimal-test-app-path-one" );
    assertThat( "Find %2Fminimal-test-app-path-one", war, notNullValue() );
    node = war.get( "/WEB-INF/gateway.xml" );
    assertThat( "Find %2Fminimal-test-app-path-one/WEB-INF/gateway.xml", node, notNullValue() );
    doc = XmlUtils.readXml( node.getAsset().openStream() );
    assertThat( "Parse %2Fminimal-test-app-path-one/WEB-INF/gateway.xml", doc, notNullValue() );

    war = archive.getAsType( WebArchive.class, "%2Fminimal-test-app-path-two" );
    assertThat( "Find %2Fminimal-test-app-path-two", war, notNullValue() );
    node = war.get( "/WEB-INF/gateway.xml" );
    assertThat( "Find %2Fminimal-test-app-path-two/WEB-INF/gateway.xml", node, notNullValue() );
    doc = XmlUtils.readXml( node.getAsset().openStream() );
    assertThat( "Parse %2Fminimal-test-app-path-two/WEB-INF/gateway.xml", doc, notNullValue() );

    LOG_EXIT();
  }

  /*
   * Test the case where topology has federation provider configured
   * and service uses anonymous authentication in which case we should
   * add AnonymousFilter to the filter chain.
   */
  @Test( timeout = LONG_TIMEOUT )
  public void testServiceAnonAuth() throws IOException, SAXException, ParserConfigurationException {
    LOG_ENTER();
    final GatewayTestConfig config = new GatewayTestConfig();
    config.setXForwardedEnabled(false);
    final File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    final File gatewayDir = new File( targetDir, "gateway-home-" + UUID.randomUUID() );
    gatewayDir.mkdirs();
    config.setGatewayHomeDir( gatewayDir.getAbsolutePath() );
    final File deployDir = new File( config.getGatewayDeploymentDir() );
    deployDir.mkdirs();

    final DefaultGatewayServices srvcs = new DefaultGatewayServices();
    final Map<String,String> options = new HashMap<>();
    options.put("persist-master", "false");
    options.put("master", "password");
    try {
      DeploymentFactory.setGatewayServices(srvcs);
      srvcs.init(config, options);
    } catch (ServiceLifecycleException e) {
      e.printStackTrace(); // I18N not required.
    }

    final Topology federationTopology = new Topology();
    final Topology authenticationTopology = new Topology();

    federationTopology.setName( "test-cluster" );
    authenticationTopology.setName( "test-cluster" );

    final Service service = new Service();
    service.setRole( "AMBARIUI" );
    service.addUrl( "http://localhost:50070/" );
    federationTopology.addService( service );
    authenticationTopology.addService( service );

    /* Add federation provider to first topology */
    final Provider provider = new Provider();
    provider.setRole( "federation" );
    provider.setName( "SSOCookieProvider" );
    provider.setEnabled( true );
    Param param = new Param();
    param.setName( "sso.authentication.provider.url" );
    param.setValue( "https://www.local.com:8443/gateway/knoxsso/api/v1/websso" );
    provider.addParam( param );
    federationTopology.addProvider( provider );

    /* Add authentication provider to second topology */
    final Provider provider2 = new Provider();
    provider2.setRole( "authentication" );
    provider2.setName( "ShiroProvider" );
    provider2.setEnabled( true );
    Param param2 = new Param();
    param2.setName( "contextConfigLocation" );
    param2.setValue( "classpath:app-context-security.xml" );
    provider2.addParam( param2 );
    authenticationTopology.addProvider( provider2 );


    final Provider asserter = new Provider();
    asserter.setRole( "identity-assertion" );
    asserter.setName("Default");
    asserter.setEnabled( true );
    federationTopology.addProvider( asserter );
    Provider authorizer = new Provider();
    authorizer.setRole( "authorization" );
    authorizer.setName("AclsAuthz");
    authorizer.setEnabled( true );
    federationTopology.addProvider( authorizer );
    authenticationTopology.addProvider( authorizer );

    final EnterpriseArchive war = DeploymentFactory.createDeployment( config, federationTopology );
    final EnterpriseArchive war2 = DeploymentFactory.createDeployment( config, federationTopology );

    final Document web = XmlUtils.readXml( war.get( "%2F/WEB-INF/web.xml" ).getAsset().openStream() );
    assertNotNull(web);
    final Document web2 = XmlUtils.readXml( war2.get( "%2F/WEB-INF/web.xml" ).getAsset().openStream() );
    assertNotNull(web2);

    /* Make sure AnonymousAuthFilter is added to the chain */
    final Document gateway = XmlUtils.readXml( war.get( "%2F/WEB-INF/gateway.xml" ).getAsset().openStream() );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/pattern", equalTo( "/ambari" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[1]/role", equalTo( "authentication" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[1]/class", equalTo( "org.apache.knox.gateway.filter.AnonymousAuthFilter" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/role", equalTo( "rewrite" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/class", equalTo( "org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletFilter" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[3]/role", equalTo( "authorization" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[3]/class", equalTo( "org.apache.knox.gateway.filter.AclsAuthorizationFilter" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[4]/role", equalTo( "dispatch" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[4]/class", equalTo( "org.apache.knox.gateway.dispatch.GatewayDispatchFilter" ) ) );

    final Document gateway2 = XmlUtils.readXml( war.get( "%2F/WEB-INF/gateway.xml" ).getAsset().openStream() );

    assertThat( gateway2, hasXPath( "/gateway/resource[1]/pattern", equalTo( "/ambari" ) ) );
    assertThat( gateway2, hasXPath( "/gateway/resource[1]/filter[1]/role", equalTo( "authentication" ) ) );
    assertThat( gateway2, hasXPath( "/gateway/resource[1]/filter[1]/class", equalTo( "org.apache.knox.gateway.filter.AnonymousAuthFilter" ) ) );
    assertThat( gateway2, hasXPath( "/gateway/resource[1]/filter[2]/role", equalTo( "rewrite" ) ) );
    assertThat( gateway2, hasXPath( "/gateway/resource[1]/filter[2]/class", equalTo( "org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletFilter" ) ) );
    assertThat( gateway2, hasXPath( "/gateway/resource[1]/filter[3]/role", equalTo( "authorization" ) ) );
    assertThat( gateway2, hasXPath( "/gateway/resource[1]/filter[3]/class", equalTo( "org.apache.knox.gateway.filter.AclsAuthorizationFilter" ) ) );
    assertThat( gateway2, hasXPath( "/gateway/resource[1]/filter[4]/role", equalTo( "dispatch" ) ) );
    assertThat( gateway2, hasXPath( "/gateway/resource[1]/filter[4]/class", equalTo( "org.apache.knox.gateway.dispatch.GatewayDispatchFilter" ) ) );

    LOG_EXIT();
  }

  private Node node( Node scope, String expression ) throws XPathExpressionException {
    return (Node)XPathFactory.newInstance().newXPath().compile( expression ).evaluate( scope, XPathConstants.NODE );
  }

  private String value( Node scope, String expression ) throws XPathExpressionException {
    return XPathFactory.newInstance().newXPath().compile( expression ).evaluate( scope );
  }
}
