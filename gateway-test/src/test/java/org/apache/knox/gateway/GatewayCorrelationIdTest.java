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
package org.apache.knox.gateway;

import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;
import org.apache.http.HttpStatus;
import org.apache.knox.gateway.audit.api.CorrelationContext;
import org.apache.knox.gateway.audit.log4j.correlation.Log4jCorrelationService;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.DefaultGatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.test.TestUtils;
import org.apache.knox.test.log.CollectAppender;
import org.apache.log4j.spi.LoggingEvent;
import org.hamcrest.MatcherAssert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.apache.knox.test.TestUtils.LOG_ENTER;
import static org.apache.knox.test.TestUtils.LOG_EXIT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class GatewayCorrelationIdTest {

  private static Logger LOG = LoggerFactory.getLogger( GatewayCorrelationIdTest.class );

  public static GatewayConfig config;
  public static GatewayServer gateway;
  public static String gatewayUrl;
  public static String clusterUrl;
  private static GatewayTestDriver driver = new GatewayTestDriver();

  @BeforeClass
  public static void setupSuite() throws Exception {
    LOG_ENTER();
    URL resource = GatewayCorrelationIdTest.class.getClassLoader().getResource("users-dynamic.ldif");
    assert resource != null;
    driver.setupLdap( 0, new File(resource.toURI()) );
    setupGateway();
    CollectAppender.queue.clear();
    LOG_EXIT();
  }

  @AfterClass
  public static void cleanupSuite() throws Exception {
    LOG_ENTER();
    gateway.stop();
    driver.cleanup();
    CollectAppender.queue.clear();
    LOG_EXIT();
  }

  public static void setupGateway() throws Exception {
    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File gatewayDir = new File( targetDir, "gateway-home-" + UUID.randomUUID() );
    gatewayDir.mkdirs();

    GatewayTestConfig testConfig = new GatewayTestConfig();
    config = testConfig;
    testConfig.setGatewayHomeDir( gatewayDir.getAbsolutePath() );

    File topoDir = new File( testConfig.getGatewayTopologyDir() );
    topoDir.mkdirs();

    File deployDir = new File( testConfig.getGatewayDeploymentDir() );
    deployDir.mkdirs();

    File descriptor = new File( topoDir, "test-cluster.xml" );
    FileOutputStream stream = new FileOutputStream( descriptor );
    createTopology().toStream( stream );
    stream.close();

    DefaultGatewayServices srvcs = new DefaultGatewayServices();
    Map<String,String> options = new HashMap<>();
    options.put( "persist-master", "false" );
    options.put( "master", "password" );
    try {
      srvcs.init( testConfig, options );
    } catch ( ServiceLifecycleException e ) {
      e.printStackTrace(); // I18N not required.
    }

    gateway = GatewayServer.startGateway( testConfig, srvcs );
    MatcherAssert.assertThat( "Failed to start gateway.", gateway, notNullValue() );

    LOG.info( "Gateway port = " + gateway.getAddresses()[ 0 ].getPort() );

    gatewayUrl = "http://localhost:" + gateway.getAddresses()[0].getPort() + "/" + config.getGatewayPath();
    clusterUrl = gatewayUrl + "/test-cluster";
  }

  private static XMLTag createTopology() {
    return XMLDoc.newDocument( true )
        .addRoot( "topology" )
        .addTag( "gateway" )
        .addTag( "provider" )
        .addTag( "role" ).addText( "authentication" )
        .addTag( "name" ).addText( "ShiroProvider" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm" )
        .addTag( "value" ).addText( "org.apache.knox.gateway.shirorealm.KnoxLdapRealm" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.userDnTemplate" )
        .addTag( "value" ).addText( "uid={0},ou=people,dc=hadoop,dc=apache,dc=org" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.url" )
        .addTag( "value" ).addText( driver.getLdapUrl() ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.authenticationMechanism" )
        .addTag( "value" ).addText( "simple" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "urls./**" )
        .addTag( "value" ).addText( "authcBasic" ).gotoParent().gotoParent()
        .addTag( "provider" )
        .addTag( "role" ).addText( "identity-assertion" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "name" ).addText( "Default" ).gotoParent()
        .addTag( "provider" )
        .gotoRoot()
        .addTag( "service" )
        .addTag( "role" ).addText( "test-service-role" )
        .gotoRoot();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testTestService() throws Exception {
    LOG_ENTER();
    String username = "guest";
    String password = "guest-password";
    String serviceUrl = clusterUrl + "/test-service-path/test-service-resource";

    Random rnd = new Random();

    // Make number of total requests between 1-100
    int numberTotalRequests = rnd.nextInt(99) + 1;
    Set<Callable<Void>> callables = new HashSet<>(numberTotalRequests);
    for (int i = 0; i < numberTotalRequests; i++) {
      callables.add(() -> {
        given()
            .auth().preemptive().basic( username, password )
            .then()
            .statusCode( HttpStatus.SC_OK )
            .contentType( "text/plain" )
            .body( is( "test-service-response" ) )
            .when().get( serviceUrl );
        return null;
      });
    }

    // Make number of concurrent requests between 1-10
    int numberConcurrentRequests = rnd.nextInt( 9) + 1;

    LOG.info("Executing %d total requests with %d concurrently", numberTotalRequests, numberConcurrentRequests);

    ExecutorService executor = Executors.newFixedThreadPool(numberConcurrentRequests);
    executor.invokeAll(callables);
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);
    assertThat(executor.isTerminated(), is(true));

    // Use a set to make sure to dedupe any requestIds to get only unique ones
    Set<String> requestIds = new HashSet<>();
    for (LoggingEvent accessEvent : CollectAppender.queue) {
      CorrelationContext cc = (CorrelationContext)accessEvent.getMDC( Log4jCorrelationService.MDC_CORRELATION_CONTEXT_KEY );
      // There are some events that do not have a CorrelationContext associated (ie: deploy)
      if(cc != null) {
        requestIds.add(cc.getRequestId());
      }
    }

    // There should be a unique correlation id for each request
    assertThat(requestIds.size(), is(numberTotalRequests));
    
    LOG_EXIT();
  }
}
