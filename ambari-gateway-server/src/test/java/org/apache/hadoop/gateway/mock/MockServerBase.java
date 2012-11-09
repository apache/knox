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
package org.apache.hadoop.gateway.mock;

import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.jersey.spi.container.servlet.ServletContainer;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;

public abstract class MockServerBase implements MockServer {

  private Logger log = LoggerFactory.getLogger( this.getClass() );

  private Server jetty;

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

  protected abstract Handler createHandler();

  protected ServletContextHandler createJerseyContext( String... packages ) {
    PackagesResourceConfig config = new PackagesResourceConfig( packages );
    Servlet servlet = new ServletContainer( config );
    ServletHolder holder = new ServletHolder( servlet );
    ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
    context.setContextPath( "/" );
    context.addServlet( holder, "/*" );
    return context;
  }

  public int getPort() {
    return jetty.getConnectors()[0].getLocalPort();
  }
}
