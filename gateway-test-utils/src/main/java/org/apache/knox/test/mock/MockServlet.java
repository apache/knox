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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Queue;


import static org.junit.Assert.fail;

public class MockServlet extends HttpServlet {
  private static final Logger LOG = LogManager.getLogger(MockServlet.class);

  private final String name;
  private final Queue<MockInteraction> interactions;

  public MockServlet( String name, Queue<MockInteraction> interactions ) {
    this.name = name;
    this.interactions = interactions;
  }

  @Override
  protected void service( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException {
    LOG.debug( "service: request=" + request.getMethod() + " " + request.getRequestURL() + "?" + request.getQueryString() );
    try {
      if( interactions.isEmpty() ) {
        fail( "Mock servlet " + name + " received a request but the expected interaction queue is empty." );
      }
      MockInteraction interaction = interactions.remove();
      interaction.expect().match( request );
      interaction.respond().apply( response );
      LOG.debug( "service: response=" + response.getStatus() );
    } catch( AssertionError e ) {
      LOG.debug( "service: exception=" + e.getMessage() );
      e.printStackTrace(); // I18N not required.
      throw new ServletException( e );
    }
  }
}
