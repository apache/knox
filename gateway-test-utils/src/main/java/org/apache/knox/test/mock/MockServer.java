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
package org.apache.knox.test.mock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.servlet.Servlet;
import java.util.LinkedList;
import java.util.Queue;

/**
 * An embedded Jetty server with a single servlet deployed on "/*".
 * It is used by populating a queue of "interactions".
 * Each interaction is an expected request and a resulting response.
 * These interactions are added to a queue in a fluent API style.
 * So in most of the tests like GatewayBasicFuncTest.testBasicJsonUseCase you will see calls like
 * driver.getMock( "WEBHDFS" ).expect()....respond()...;
 * This adds a single interaction to the mock server which is returned via the driver.getMock( "WEBHDFS" ) above.
 * Any number of interactions may be added.
 * When the request comes in it will check the request against the expected request.
 * If it matches return the response otherwise it will return a 500 error.
 * Typically at the end of a test you should check to make sure the interaction queue is consumed by calling isEmpty().
 * The reset() method can be used to ensure everything is cleaned up so that the mock server can be reused beteween tests.
 * The whole idea was modeled after how the REST testing framework REST-assured and aims to be a server side equivalent.
 */
public class MockServer {
  private static final Logger log = LogManager.getLogger( MockServer.class );

  private String name;
  private Server jetty;

  private Queue<MockInteraction> interactions = new LinkedList<>();

  public MockServer( String name ) {
    this.name = name;
  }

  public MockServer( String name, boolean start ) throws Exception {
    this.name = name;
    if( start ) {
      start();
    }
  }

  public String getName() {
    return name;
  }

  public void start() throws Exception {
    Handler context = createHandler();
    jetty = new Server(0);
    jetty.setHandler( context );
    jetty.start();
    log.info( "Mock server started on port " + getPort() );
  }

  public void stop() throws Exception {
    jetty.stop();
    jetty.join();
  }

  private ServletContextHandler createHandler() {
    Servlet servlet = new MockServlet( getName(), interactions );
    ServletHolder holder = new ServletHolder( servlet );
    ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
    context.setContextPath( "/" );
    context.addServlet( holder, "/*" );
    return context;
  }

  public int getPort() {
    return jetty.getURI().getPort();
  }

  public MockRequestMatcher expect() {
    MockInteraction interaction = new MockInteraction();
    interactions.add( interaction );
    return interaction.expect();
  }

  public MockResponseProvider respond() {
    MockInteraction interaction = new MockInteraction();
    interactions.add( interaction );
    return interaction.respond();
  }

  public int getCount() {
    return interactions.size();
  }

  public boolean isEmpty() {
    return interactions.isEmpty();
  }

  public void reset() {
    interactions.clear();
  }
}
