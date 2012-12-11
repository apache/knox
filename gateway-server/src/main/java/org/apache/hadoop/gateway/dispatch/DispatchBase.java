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
package org.apache.hadoop.gateway.dispatch;

import org.apache.hadoop.gateway.filter.AbstractGatewayFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class DispatchBase extends AbstractGatewayFilter implements Dispatch {
  
  private static Map<String,Adapter> METHOD_ADAPTERS = createMethodAdapters();

  @Override
  protected void doFilter( HttpServletRequest request, HttpServletResponse response, FilterChain chain ) throws IOException, ServletException {
    String method = request.getMethod().toUpperCase();
    Adapter adapter = METHOD_ADAPTERS.get( method );
    if( adapter != null ) {
      try {
        adapter.doMethod( this, request, response );
      } catch( URISyntaxException e ) {
        throw new ServletException( e );
      }
    } else {
      response.sendError( HttpServletResponse.SC_METHOD_NOT_ALLOWED );
    }
  }

  public void doGet( HttpServletRequest request, HttpServletResponse response ) throws IOException, URISyntaxException {
    response.sendError( HttpServletResponse.SC_METHOD_NOT_ALLOWED );
  }

  public void doPost( HttpServletRequest request, HttpServletResponse response ) throws IOException, URISyntaxException {
    response.sendError( HttpServletResponse.SC_METHOD_NOT_ALLOWED );
  }

  public void doPut( HttpServletRequest request, HttpServletResponse response ) throws IOException, URISyntaxException {
    response.sendError( HttpServletResponse.SC_METHOD_NOT_ALLOWED );
  }

  public void doDelete( HttpServletRequest request, HttpServletResponse response ) throws IOException, URISyntaxException {
    response.sendError( HttpServletResponse.SC_METHOD_NOT_ALLOWED );
  }

  public void doOptions( HttpServletRequest request, HttpServletResponse response ) throws IOException, URISyntaxException {
    response.sendError( HttpServletResponse.SC_METHOD_NOT_ALLOWED );
  }

  private static Map<String,Adapter> createMethodAdapters() {
    Map<String,Adapter> map = new HashMap<String,Adapter>();
    map.put( "GET", new GetAdapter() );
    map.put( "POST", new PostAdapter() );
    map.put( "PUT", new PutAdapter() );
    map.put( "DELETE", new DeleteAdapter() );
    map.put( "OPTIONS", new OptionsAdapter() );
    return Collections.unmodifiableMap( map );
  }

  private interface Adapter {
    public void doMethod( Dispatch dispatch, HttpServletRequest request, HttpServletResponse response )
        throws IOException, ServletException, URISyntaxException;
  }

  private static class GetAdapter implements Adapter {
    public void doMethod( Dispatch dispatch, HttpServletRequest request, HttpServletResponse response )
        throws IOException, ServletException, URISyntaxException {
      dispatch.doGet( request, response );
    }
  }

  private static class PostAdapter implements Adapter {
    public void doMethod( Dispatch dispatch, HttpServletRequest request, HttpServletResponse response )
        throws IOException, ServletException, URISyntaxException {
      dispatch.doPost( request, response );
    }
  }

  private static class PutAdapter implements Adapter {
    public void doMethod( Dispatch dispatch, HttpServletRequest request, HttpServletResponse response )
        throws IOException, ServletException, URISyntaxException {
      dispatch.doPut( request, response );
    }
  }

  private static class DeleteAdapter implements Adapter {
    public void doMethod( Dispatch dispatch, HttpServletRequest request, HttpServletResponse response )
        throws IOException, ServletException, URISyntaxException {
      dispatch.doDelete( request, response );
    }
  }

  private static class OptionsAdapter implements Adapter {
    public void doMethod( Dispatch dispatch, HttpServletRequest request, HttpServletResponse response )
        throws IOException, ServletException, URISyntaxException {
      dispatch.doOptions( request, response );
    }
  }

}
