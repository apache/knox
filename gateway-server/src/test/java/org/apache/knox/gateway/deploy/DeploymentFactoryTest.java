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

import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.gateway.topology.Application;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.util.XmlUtils;
import org.apache.knox.test.TestUtils;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.Assert.fail;

public class DeploymentFactoryTest {

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testEmptyTopology() throws IOException, SAXException, ParserConfigurationException, TransformerException {
    GatewayConfig config = new GatewayConfigImpl();

    Topology topology = new Topology();
    topology.setName( "test-topology" );

    EnterpriseArchive archive = DeploymentFactory.createDeployment( config, topology );

    Document xml = XmlUtils.readXml( archive.get( "/META-INF/topology.xml" ).getAsset().openStream() );
    assertThat( xml, hasXPath( "/topology/gateway" ) );
    assertThat( xml, hasXPath( "/topology/name", equalTo( "test-topology" ) ) );
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void test_validateNoAppsWithRootUrlsInServicesTopology() {
    DeploymentFactory.validateNoAppsWithRootUrlsInServicesTopology( null );

    Topology topology = new Topology();
    topology.setName( "test-topology" );
    DeploymentFactory.validateNoAppsWithRootUrlsInServicesTopology( topology );

    Service service;
    Application application;

    topology = new Topology();
    topology.setName( "test-topology" );
    service = new Service();
    service.setName( "test-service" );
    service.setRole( "test-service" );
    topology.addService( service );
    application = new Application();
    application.setName( "test-application" );
    topology.addApplication( application );

    topology = new Topology();
    topology.setName( "test-topology" );
    service = new Service();
    service.setName( "test-service" );
    service.setRole( "test-service" );
    topology.addService( service );
    application = new Application();
    application.setName( "test-application" );
    application.addUrl( "" );
    topology.addApplication( application );
    try {
      DeploymentFactory.validateNoAppsWithRootUrlsInServicesTopology( topology );
      fail( "Expected DeploymentException" );
    } catch ( DeploymentException e ) {
      // Expected.
    }

    topology = new Topology();
    topology.setName( "test-topology" );
    service = new Service();
    service.setName( "test-service" );
    service.setRole( "test-service" );
    topology.addService( service );
    application = new Application();
    application.setName( "test-application" );
    application.addUrl( "/" );
    topology.addApplication( application );
    try {
      DeploymentFactory.validateNoAppsWithRootUrlsInServicesTopology( topology );
      fail( "Expected DeploymentException" );
    } catch ( DeploymentException e ) {
      // Expected.
    }

    topology = new Topology();
    topology.setName( "test-topology" );
    service = new Service();
    service.setName( "test-service" );
    service.setRole( "test-service" );
    topology.addService( service );
    application = new Application();
    application.setName( "test-application" );
    application.addUrl( "/" );
    topology.addApplication( application );
    try {
      DeploymentFactory.validateNoAppsWithRootUrlsInServicesTopology( topology );
      fail( "Expected DeploymentException" );
    } catch ( DeploymentException e ) {
      // Expected.
    }

    topology = new Topology();
    topology.setName( "test-topology" );
    service = new Service();
    service.setName( "test-service" );
    service.setRole( "test-service" );
    topology.addService( service );
    application = new Application();
    application.setName( "test-application" );
    application.addUrl( "/test-application" );
    application.addUrl( "/" );
    topology.addApplication( application );
    try {
      DeploymentFactory.validateNoAppsWithRootUrlsInServicesTopology( topology );
      fail( "Expected DeploymentException" );
    } catch ( DeploymentException e ) {
      // Expected.
    }

  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void test_validateNoAppsWithDuplicateUrlsInTopology() {
    DeploymentFactory.validateNoAppsWithDuplicateUrlsInTopology( null );

    Topology topology = new Topology();
    topology.setName( "test-topology" );
    DeploymentFactory.validateNoAppsWithDuplicateUrlsInTopology( topology );

    Application application;

    topology = new Topology();
    topology.setName( "test-topology" );
    application = new Application();
    application.setName( "test-application-1" );
    topology.addApplication( application );
    DeploymentFactory.validateNoAppsWithDuplicateUrlsInTopology( topology );

    topology = new Topology();
    topology.setName( "test-topology" );
    application = new Application();
    application.setName( "test-application-1" );
    topology.addApplication( application );
    application = new Application();
    application.setName( "test-application-2" );
    topology.addApplication( application );
    DeploymentFactory.validateNoAppsWithDuplicateUrlsInTopology( topology );

    topology = new Topology();
    topology.setName( "test-topology" );
    application = new Application();
    application.setName( "test-application-1" );
    topology.addApplication( application );
    application = new Application();
    application.setName( "test-application-2" );
    application.addUrl( "/test-application-2" );
    topology.addApplication( application );
    DeploymentFactory.validateNoAppsWithDuplicateUrlsInTopology( topology );

    topology = new Topology();
    topology.setName( "test-topology" );
    application = new Application();
    application.setName( "test-application-1" );
    topology.addApplication( application );
    application = new Application();
    application.setName( "test-application-2" );
    application.addUrl( "/" );
    topology.addApplication( application );
    DeploymentFactory.validateNoAppsWithDuplicateUrlsInTopology( topology );

    topology = new Topology();
    topology.setName( "test-topology" );
    application = new Application();
    application.setName( "test-application-1" );
    topology.addApplication( application );
    application = new Application();
    application.setName( "test-application-2" );
    application.addUrl( "/" );
    topology.addApplication( application );
    DeploymentFactory.validateNoAppsWithDuplicateUrlsInTopology( topology );

    topology = new Topology();
    topology.setName( "test-topology" );
    application = new Application();
    application.setName( "test-application-1" );
    application.addUrl( "/test-application-1" );
    topology.addApplication( application );
    application = new Application();
    application.setName( "test-application-2" );
    application.addUrl( "/test-application-2" );
    topology.addApplication( application );
    DeploymentFactory.validateNoAppsWithDuplicateUrlsInTopology( topology );

    topology = new Topology();
    topology.setName( "test-topology" );
    application = new Application();
    application.setName( "test-application-1" );
    application.addUrl( "/test-application-dup" );
    topology.addApplication( application );
    application = new Application();
    application.setName( "test-application-2" );
    application.addUrl( "/test-application-dup" );
    topology.addApplication( application );
    try {
      DeploymentFactory.validateNoAppsWithDuplicateUrlsInTopology( topology );
      fail( "Expected DeploymentException" );
    } catch ( DeploymentException e ) {
      // Expected.
    }

    topology = new Topology();
    topology.setName( "test-topology" );
    application = new Application();
    application.setName( "test-application-1" );
    application.addUrl( "/" );
    topology.addApplication( application );
    application = new Application();
    application.setName( "test-application-2" );
    application.addUrl( "/" );
    topology.addApplication( application );
    try {
      DeploymentFactory.validateNoAppsWithDuplicateUrlsInTopology( topology );
      fail( "Expected DeploymentException" );
    } catch ( DeploymentException e ) {
      // Expected.
    }

    topology = new Topology();
    topology.setName( "test-topology" );
    application = new Application();
    application.setName( "test-application-1" );
    application.addUrl( "" );
    topology.addApplication( application );
    application = new Application();
    application.setName( "test-application-2" );
    application.addUrl( "/" );
    topology.addApplication( application );
    try {
      DeploymentFactory.validateNoAppsWithDuplicateUrlsInTopology( topology );
      fail( "Expected DeploymentException" );
    } catch ( DeploymentException e ) {
      // Expected.
    }

    topology = new Topology();
    topology.setName( "test-topology" );
    application = new Application();
    application.setName( "test-application-1" );
    topology.addApplication( application );
    application = new Application();
    application.setName( "test-application-2" );
    application.addUrl( "/test-application-1" );
    topology.addApplication( application );
    try {
      DeploymentFactory.validateNoAppsWithDuplicateUrlsInTopology( topology );
      fail( "Expected DeploymentException" );
    } catch ( DeploymentException e ) {
      // Expected.
    }

    topology = new Topology();
    topology.setName( "test-topology" );
    application = new Application();
    application.setName( "test-application-1" );
    topology.addApplication( application );
    application = new Application();
    application.setName( "test-application-1" );
    topology.addApplication( application );
    try {
      DeploymentFactory.validateNoAppsWithDuplicateUrlsInTopology( topology );
      fail( "Expected DeploymentException" );
    } catch ( DeploymentException e ) {
      // Expected.
    }

    topology = new Topology();
    topology.setName( "test-topology" );
    application = new Application();
    application.setName( "test-application-1" );
    application.addUrl( "/test-application-1" );
    application.addUrl( "/test-application-3" );
    topology.addApplication( application );
    application = new Application();
    application.setName( "test-application-2" );
    application.addUrl( "/test-application-2" );
    application.addUrl( "/test-application-3" );
    topology.addApplication( application );
    try {
      DeploymentFactory.validateNoAppsWithDuplicateUrlsInTopology( topology );
      fail( "Expected DeploymentException" );
    } catch ( DeploymentException e ) {
      // Expected.
    }

  }

}
