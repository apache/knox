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
import org.apache.hadoop.gateway.topology.Topology;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.xml.HasXPath.hasXPath;

public class DeploymentFactoryTest {

  @Test
  public void testEmptyTopology() throws IOException, SAXException, ParserConfigurationException {
    GatewayConfig config = new GatewayConfigImpl();

    Topology topology = new Topology();
    topology.setName( "test-cluster" );

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
    assertThat( gateway, hasXPath( "/gateway" ) );
  }

  private Document parse( InputStream stream ) throws IOException, SAXException, ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    InputSource source = new InputSource( stream );
    return builder.parse( source );
  }

}
