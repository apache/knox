/**
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
package org.apache.hadoop.gateway.deploy;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.gateway.GatewayTestConfig;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.services.DefaultGatewayServices;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.hadoop.gateway.topology.Param;
import org.apache.hadoop.gateway.topology.Provider;
import org.apache.hadoop.gateway.topology.Service;
import org.apache.hadoop.gateway.topology.Topology;
import org.apache.hadoop.test.log.NoOpAppender;
import org.apache.log4j.Appender;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.apache.hadoop.test.TestUtils.LOG_ENTER;
import static org.apache.hadoop.test.TestUtils.LOG_EXIT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.Assert.fail;

public class DeploymentFactoryFuncTest {

  private static final long SHORT_TIMEOUT = 1000L;
  private static final long MEDIUM_TIMEOUT = 10 * SHORT_TIMEOUT;
  private static final long LONG_TIMEOUT = 10 * MEDIUM_TIMEOUT;

  @Test( timeout = SHORT_TIMEOUT )
  public void testGenericProviderDeploymentContributor() throws ParserConfigurationException, SAXException, IOException, TransformerException {
    LOG_ENTER();
    GatewayConfig config = new GatewayTestConfig();
    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File gatewayDir = new File( targetDir, "gateway-home-" + UUID.randomUUID() );
    gatewayDir.mkdirs();

    ((GatewayTestConfig) config).setGatewayHomeDir( gatewayDir.getAbsolutePath() );

    File deployDir = new File( config.getGatewayDeploymentDir() );
    deployDir.mkdirs();

//    ((GatewayTestConfig) config).setDeploymentDir( "clusters" );

    addStacksDir(config, targetDir);
    DefaultGatewayServices srvcs = new DefaultGatewayServices();
    Map<String,String> options = new HashMap<String,String>();
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

    WebArchive war = DeploymentFactory.createDeployment( config, topology );

    Document gateway = parse( war.get( "WEB-INF/gateway.xml" ).getAsset().openStream() );
    //dump( gateway );

    //by default the first filter will be the X-Forwarded header filter
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[1]/role", equalTo( "xforwardedheaders" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[1]/name", equalTo( "XForwardedHeaderFilter" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[1]/class", equalTo( "org.apache.hadoop.gateway.filter.XForwardedHeaderFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/role", equalTo( "federation" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/name", equalTo( "HeaderPreAuth" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/class", equalTo( "org.apache.hadoop.gateway.preauth.filter.HeaderPreAuthFederationFilter" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/param[1]/name", equalTo( "filter" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/param[1]/value", equalTo( "org.opensource.ExistingFilter" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/param[2]/name", equalTo( "test-param-name" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/param[2]/value", equalTo( "test-param-value" ) ) );
    LOG_EXIT();
  }

  @Test( timeout = LONG_TIMEOUT )
  public void testInvalidGenericProviderDeploymentContributor() throws ParserConfigurationException, SAXException, IOException, TransformerException {
    LOG_ENTER();
    GatewayConfig config = new GatewayTestConfig();
    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File gatewayDir = new File( targetDir, "gateway-home-" + UUID.randomUUID() );
    gatewayDir.mkdirs();
    ((GatewayTestConfig) config).setGatewayHomeDir( gatewayDir.getAbsolutePath() );
    File deployDir = new File( config.getGatewayDeploymentDir() );
    deployDir.mkdirs();
    addStacksDir(config, targetDir);

    DefaultGatewayServices srvcs = new DefaultGatewayServices();
    Map<String,String> options = new HashMap<String,String>();
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
    Param param; // = new ProviderParam();
    // Missing filter param.
    //param.setName( "filter" );
    //param.setValue( "org.opensource.ExistingFilter" );
    //provider.addParam( param );
    param = new Param();
    param.setName( "test-param-name" );
    param.setValue( "test-param-value" );
    provider.addParam( param );
    topology.addProvider( provider );

    Enumeration<Appender> appenders = NoOpAppender.setUp();
    try {
      DeploymentFactory.createDeployment( config, topology );
      fail( "Should have throws IllegalArgumentException" );
    } catch ( DeploymentException e ) {
      // Expected.
    } finally {
      NoOpAppender.tearDown( appenders );
    }
    LOG_EXIT();
  }

  @Test( timeout = MEDIUM_TIMEOUT )
  public void testSimpleTopology() throws IOException, SAXException, ParserConfigurationException, URISyntaxException {
    LOG_ENTER();
    GatewayConfig config = new GatewayTestConfig();
    //Testing without x-forwarded headers filter
    ((GatewayTestConfig)config).setXForwardedEnabled(false);
    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File gatewayDir = new File( targetDir, "gateway-home-" + UUID.randomUUID() );
    gatewayDir.mkdirs();
    ((GatewayTestConfig) config).setGatewayHomeDir( gatewayDir.getAbsolutePath() );
    File deployDir = new File( config.getGatewayDeploymentDir() );
    deployDir.mkdirs();
    addStacksDir(config, targetDir);

    DefaultGatewayServices srvcs = new DefaultGatewayServices();
    Map<String,String> options = new HashMap<String,String>();
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

    WebArchive war = DeploymentFactory.createDeployment( config, topology );
//    File dir = new File( System.getProperty( "user.dir" ) );
//    File file = war.as( ExplodedExporter.class ).exportExploded( dir, "test-cluster.war" );

    Document web = parse( war.get( "WEB-INF/web.xml" ).getAsset().openStream() );
    assertThat( web, hasXPath( "/web-app/servlet/servlet-name", equalTo( "test-cluster" ) ) );
    assertThat( web, hasXPath( "/web-app/servlet/servlet-class", equalTo( "org.apache.hadoop.gateway.GatewayServlet" ) ) );
    assertThat( web, hasXPath( "/web-app/servlet/init-param/param-name", equalTo( "gatewayDescriptorLocation" ) ) );
    assertThat( web, hasXPath( "/web-app/servlet/init-param/param-value", equalTo( "gateway.xml" ) ) );
    assertThat( web, hasXPath( "/web-app/servlet-mapping/servlet-name", equalTo( "test-cluster" ) ) );
    assertThat( web, hasXPath( "/web-app/servlet-mapping/url-pattern", equalTo( "/*" ) ) );

    Document gateway = parse( war.get( "WEB-INF/gateway.xml" ).getAsset().openStream() );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/pattern", equalTo( "/webhdfs/v1/?**" ) ) );
    //assertThat( gateway, hasXPath( "/gateway/resource[1]/target", equalTo( "http://localhost:50070/webhdfs/v1/?{**}" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[1]/role", equalTo( "authentication" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[1]/class", equalTo( "org.apache.hadoop.gateway.filter.ResponseCookieFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/role", equalTo( "authentication" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/class", equalTo( "org.apache.shiro.web.servlet.ShiroFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[3]/role", equalTo( "authentication" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[3]/class", equalTo( "org.apache.hadoop.gateway.filter.ShiroSubjectIdentityAdapter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[4]/role", equalTo( "rewrite" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[4]/class", equalTo( "org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteServletFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[5]/role", equalTo( "identity-assertion" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[5]/class", equalTo( "org.apache.hadoop.gateway.identityasserter.filter.IdentityAsserterFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[6]/role", equalTo( "authorization" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[6]/name", equalTo( "AclsAuthz" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[6]/class", equalTo( "org.apache.hadoop.gateway.filter.AclsAuthorizationFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[7]/role", equalTo( "dispatch" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[7]/name", equalTo( "webhdfs" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[7]/class", equalTo( "org.apache.hadoop.gateway.dispatch.GatewayDispatchFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[2]/pattern", equalTo( "/webhdfs/v1/**?**" ) ) );
    //assertThat( gateway, hasXPath( "/gateway/resource[2]/target", equalTo( "http://localhost:50070/webhdfs/v1/{path=**}?{**}" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[1]/role", equalTo( "authentication" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[1]/class", equalTo( "org.apache.hadoop.gateway.filter.ResponseCookieFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[2]/role", equalTo( "authentication" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[2]/class", equalTo( "org.apache.shiro.web.servlet.ShiroFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[3]/role", equalTo( "authentication" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[3]/class", equalTo( "org.apache.hadoop.gateway.filter.ShiroSubjectIdentityAdapter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[4]/role", equalTo( "rewrite" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[4]/class", equalTo( "org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteServletFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[5]/role", equalTo( "identity-assertion" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[5]/class", equalTo( "org.apache.hadoop.gateway.identityasserter.filter.IdentityAsserterFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[6]/role", equalTo( "authorization" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[6]/name", equalTo( "AclsAuthz" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[6]/class", equalTo( "org.apache.hadoop.gateway.filter.AclsAuthorizationFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[7]/role", equalTo( "dispatch" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[7]/name", equalTo( "webhdfs" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[7]/class", equalTo( "org.apache.hadoop.gateway.dispatch.GatewayDispatchFilter" ) ) );

    LOG_EXIT();
  }


   @Test( timeout = LONG_TIMEOUT )
   public void testWebXmlGeneration() throws IOException, SAXException, ParserConfigurationException, URISyntaxException {
      LOG_ENTER();
      GatewayConfig config = new GatewayTestConfig();
      File targetDir = new File(System.getProperty("user.dir"), "target");
      File gatewayDir = new File(targetDir, "gateway-home-" + UUID.randomUUID());
      gatewayDir.mkdirs();
      ((GatewayTestConfig) config).setGatewayHomeDir(gatewayDir.getAbsolutePath());
      File deployDir = new File(config.getGatewayDeploymentDir());
      deployDir.mkdirs();

      DefaultGatewayServices srvcs = new DefaultGatewayServices();
      Map<String, String> options = new HashMap<String, String>();
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

      for (int i = 0; i < 100; i++) {
         createAndTestDeployment(config, topology);
      }
      LOG_EXIT();
   }

   private void createAndTestDeployment(GatewayConfig config, Topology topology) throws IOException, SAXException, ParserConfigurationException {

      WebArchive war = DeploymentFactory.createDeployment(config, topology);
//      File dir = new File( System.getProperty( "user.dir" ) );
//      File file = war.as( ExplodedExporter.class ).exportExploded( dir, "test-cluster.war" );

      Document web = parse(war.get("WEB-INF/web.xml").getAsset().openStream());
      assertThat(web, hasXPath("/web-app/servlet/servlet-class", equalTo("org.apache.hadoop.gateway.GatewayServlet")));
      assertThat(web, hasXPath("/web-app/servlet/init-param/param-name", equalTo("gatewayDescriptorLocation")));
      assertThat(web, hasXPath("/web-app/servlet/init-param/param-value", equalTo("gateway.xml")));
      assertThat(web, hasXPath("/web-app/servlet-mapping/servlet-name", equalTo("test-cluster")));
      assertThat(web, hasXPath("/web-app/servlet-mapping/url-pattern", equalTo("/*")));
      //testing the order of listener classes generated
      assertThat(web, hasXPath("/web-app/listener[2]/listener-class", equalTo("org.apache.hadoop.gateway.services.GatewayServicesContextListener")));
      assertThat(web, hasXPath("/web-app/listener[3]/listener-class", equalTo("org.apache.hadoop.gateway.ha.provider.HaServletContextListener")));
      assertThat(web, hasXPath("/web-app/listener[4]/listener-class", equalTo("org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteServletContextListener")));
   }

  @Test( timeout = MEDIUM_TIMEOUT )
  public void testDeploymentWithServiceParams() throws Exception {
    LOG_ENTER();
    GatewayConfig config = new GatewayTestConfig();
    File targetDir = new File(System.getProperty("user.dir"), "target");
    File gatewayDir = new File(targetDir, "gateway-home-" + UUID.randomUUID());
    gatewayDir.mkdirs();
    ((GatewayTestConfig) config).setGatewayHomeDir(gatewayDir.getAbsolutePath());
    File deployDir = new File(config.getGatewayDeploymentDir());
    deployDir.mkdirs();
    addStacksDir(config, targetDir);

    DefaultGatewayServices srvcs = new DefaultGatewayServices();
    Map<String, String> options = new HashMap<String, String>();
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
    service.setUrls( Arrays.asList( new String[]{ "http://hive-host:50001/" } ) );
    param = new Param();
    param.setName( "someparam" );
    param.setValue( "somevalue" );
    service.addParam( param );
    topology.addService( service );

    service = new Service();
    service.setRole( "WEBHBASE" );
    service.setUrls( Arrays.asList( new String[]{ "http://hbase-host:50002/" } ) );
    param = new Param();
    param.setName( "replayBufferSize" );
    param.setValue( "33" );
    service.addParam( param );
    topology.addService( service );

    service = new Service();
    service.setRole( "OOZIE" );
    service.setUrls( Arrays.asList( new String[]{ "http://hbase-host:50003/" } ) );
    param = new Param();
    param.setName( "otherparam" );
    param.setValue( "65" );
    service.addParam( param );
    topology.addService( service );

    WebArchive war = DeploymentFactory.createDeployment( config, topology );
    Document doc = parse( war.get( "WEB-INF/gateway.xml" ).getAsset().openStream() );
//    dump( doc );

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

  private Document parse( InputStream stream ) throws IOException, SAXException, ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    InputSource source = new InputSource( stream );
    return builder.parse( source );
  }

  private void addStacksDir(GatewayConfig config, File targetDir) {
    File stacksDir = new File( config.getGatewayServicesDir() );
    stacksDir.mkdirs();
    //TODO: [sumit] This is a hack for now, need to find a better way to locate the source resources for 'stacks' to be tested
    String pathToStacksSource = "gateway-service-definitions/src/main/resources/services";
    File stacksSourceDir = new File( targetDir.getParent(), pathToStacksSource);
    if (!stacksSourceDir.exists()) {
      stacksSourceDir = new File( targetDir.getParentFile().getParent(), pathToStacksSource);
    }
    if (stacksSourceDir.exists()) {
      try {
        FileUtils.copyDirectoryToDirectory(stacksSourceDir, stacksDir);
      } catch ( IOException e) {
        fail(e.getMessage());
      }
    }

  }

  private void dump( Document document ) throws TransformerException {
    Transformer transformer = TransformerFactory.newInstance().newTransformer();
    transformer.setOutputProperty( OutputKeys.INDENT, "yes" );
    StreamResult result = new StreamResult( new StringWriter() );
    DOMSource source = new DOMSource( document );
    transformer.transform( source, result );
    String xmlString = result.getWriter().toString();
    System.out.println( xmlString );
  }

  private Node node( Node scope, String expression ) throws XPathExpressionException {
    return (Node)XPathFactory.newInstance().newXPath().compile( expression ).evaluate( scope, XPathConstants.NODE );
  }

  private String value( Node scope, String expression ) throws XPathExpressionException {
    return XPathFactory.newInstance().newXPath().compile( expression ).evaluate( scope );
  }

}
