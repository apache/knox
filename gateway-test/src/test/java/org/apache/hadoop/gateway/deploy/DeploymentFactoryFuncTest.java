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

import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.config.impl.GatewayConfigImpl;
import org.apache.hadoop.gateway.topology.Provider;
import org.apache.hadoop.gateway.topology.ProviderParam;
import org.apache.hadoop.gateway.topology.Service;
import org.apache.hadoop.gateway.topology.Topology;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Ignore;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.xml.HasXPath.hasXPath;

public class DeploymentFactoryFuncTest {

  @Test
  public void testSimpleTopology() throws IOException, SAXException, ParserConfigurationException {
    GatewayConfig config = new GatewayConfigImpl();
    Topology topology = new Topology();
    topology.setName( "test-cluster" );
    Service service = new Service();
    service.setRole( "NAMENODE" );
    service.setUrl( new URL( "http://localhost:50070/webhdfs/v1" ) );
    topology.addService( service );
    Provider provider = new Provider();
    provider.setRole( "authentication" );
    provider.setEnabled( true );
    ProviderParam param = new ProviderParam();
    param.setName( "contextConfigLocation" );
    param.setValue( "classpath:app-context-security.xml" );
    provider.addParam( param );
    topology.addProvider( provider );
    Provider asserter = new Provider();
    asserter.setRole( "identity-assertion" );
    asserter.setName("Pseudo");
    asserter.setEnabled( true );
    topology.addProvider( asserter );
    Provider authorizer = new Provider();
    authorizer.setRole( "authorization" );
    authorizer.setName("AclsAuthz");
    authorizer.setEnabled( true );
    topology.addProvider( authorizer );

    WebArchive war = DeploymentFactory.createDeployment( config, topology );
    //File dir = new File( System.getProperty( "user.dir" ) );
    //File file = war.as( ExplodedExporter.class ).exportExploded( dir, "test-cluster.war" );

    Document wad = parse( war.get( "WEB-INF/web.xml" ).getAsset().openStream() );
    assertThat( wad, hasXPath( "/web-app/servlet/servlet-name", equalTo( "test-cluster" ) ) );
    assertThat( wad, hasXPath( "/web-app/servlet/servlet-class", equalTo( "org.apache.hadoop.gateway.GatewayServlet" ) ) );
    assertThat( wad, hasXPath( "/web-app/servlet/init-param/param-name", equalTo( "gatewayDescriptorLocation" ) ) );
    assertThat( wad, hasXPath( "/web-app/servlet/init-param/param-value", equalTo( "gateway.xml" ) ) );
    assertThat( wad, hasXPath( "/web-app/servlet-mapping/servlet-name", equalTo( "test-cluster" ) ) );
    assertThat( wad, hasXPath( "/web-app/servlet-mapping/url-pattern", equalTo( "/*" ) ) );

    Document gateway = parse( war.get( "WEB-INF/gateway.xml" ).getAsset().openStream() );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/pattern", equalTo( "/namenode/api/v1/?**" ) ) );
    //assertThat( gateway, hasXPath( "/gateway/resource[1]/target", equalTo( "http://localhost:50070/webhdfs/v1/?{**}" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[1]/role", equalTo( "authentication" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[1]/class", equalTo( "org.apache.hadoop.gateway.filter.ResponseCookieFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/role", equalTo( "authentication" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[2]/class", equalTo( "org.apache.shiro.web.servlet.ShiroFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[3]/role", equalTo( "authentication" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[3]/class", equalTo( "org.apache.hadoop.gateway.filter.PostAuthenticationFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[4]/role", equalTo( "rewrite" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[4]/class", equalTo( "org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteServletFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[5]/role", equalTo( "identity-assertion" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[5]/class", equalTo( "org.apache.hadoop.gateway.identityasserter.filter.IdentityAsserterFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[6]/role", equalTo( "authorization" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[6]/name", equalTo( "AclsAuthz" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[6]/class", equalTo( "org.apache.hadoop.gateway.filter.AclsAuthorizationFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[7]/role", equalTo( "dispatch" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[7]/name", equalTo( "http-client" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[7]/class", equalTo( "org.apache.hadoop.gateway.dispatch.HttpClientDispatch" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[2]/pattern", equalTo( "/namenode/api/v1/**?**" ) ) );
    //assertThat( gateway, hasXPath( "/gateway/resource[2]/target", equalTo( "http://localhost:50070/webhdfs/v1/{path=**}?{**}" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[1]/role", equalTo( "authentication" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[1]/class", equalTo( "org.apache.hadoop.gateway.filter.ResponseCookieFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[2]/role", equalTo( "authentication" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[2]/class", equalTo( "org.apache.shiro.web.servlet.ShiroFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[3]/role", equalTo( "authentication" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[3]/class", equalTo( "org.apache.hadoop.gateway.filter.PostAuthenticationFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[4]/role", equalTo( "rewrite" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[4]/class", equalTo( "org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteServletFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[5]/role", equalTo( "identity-assertion" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[5]/class", equalTo( "org.apache.hadoop.gateway.identityasserter.filter.IdentityAsserterFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[6]/role", equalTo( "authorization" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[6]/name", equalTo( "AclsAuthz" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[1]/filter[6]/class", equalTo( "org.apache.hadoop.gateway.filter.AclsAuthorizationFilter" ) ) );

    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[7]/role", equalTo( "dispatch" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[7]/name", equalTo( "http-client" ) ) );
    assertThat( gateway, hasXPath( "/gateway/resource[2]/filter[7]/class", equalTo( "org.apache.hadoop.gateway.dispatch.HttpClientDispatch" ) ) );
  }

  private Document parse( InputStream stream ) throws IOException, SAXException, ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    InputSource source = new InputSource( stream );
    return builder.parse( source );
  }

}
