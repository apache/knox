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
package org.apache.knox.gateway.mock;

import org.apache.knox.test.mock.MockServlet;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

/**
 *
 */
public class MockConsoleFactory {

  public static Handler create() {
    ServletHolder consoleHolder = new ServletHolder( "console", MockServlet.class );
    consoleHolder.setInitParameter( "contentType", "text/html" );
    consoleHolder.setInitParameter( "content", "<html>Console UI goes here.</html>" );

    ServletContextHandler consoleContext = new ServletContextHandler( ServletContextHandler.SESSIONS );
    consoleContext.setContextPath( "/console" );
    consoleContext.setResourceBase( "target/classes" );
    consoleContext.addServlet( consoleHolder, "/*" );

    return consoleContext;
  }

}
