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
package org.apache.knox.gateway.filter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.knox.gateway.i18n.GatewaySpiMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import java.io.IOException;

public abstract class AbstractGatewayFilter implements Filter {

  public static final String SOURCE_REQUEST_URL_ATTRIBUTE_NAME = "sourceRequestUrl";
  public static final String TARGET_REQUEST_URL_ATTRIBUTE_NAME = "targetRequestUrl";
  public static final String SOURCE_REQUEST_CONTEXT_URL_ATTRIBUTE_NAME = "sourceRequestContextUrl";
  public static final String TARGET_SERVICE_ROLE = "targetServiceRole";
  public static final String DEFAULT_TOPOLOGY_FORWARD_ATTRIBUTE_NAME = "defaultTopologyForward";
  //  public static final String RESPONSE_STREAMER_ATTRIBUTE_NAME = "responseStreamer";
  private static final GatewaySpiMessages LOG = MessagesFactory.get( GatewaySpiMessages.class );

  private FilterConfig config;

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    this.config = filterConfig;
  }

  protected FilterConfig getConfig() {
    return config;
  }

  @Override
  public void doFilter( ServletRequest request, ServletResponse response, FilterChain chain )
      throws IOException, ServletException {
    try {
      doFilter( (HttpServletRequest)request, (HttpServletResponse)response, chain );
    } catch( IOException | ServletException e ) {
      LOG.failedToExecuteFilter( e );
      throw e;
    } catch( Throwable t ) {
      LOG.failedToExecuteFilter( t );
      throw new ServletException( t );
    }
  }

  protected abstract void doFilter( HttpServletRequest request, HttpServletResponse response, FilterChain chain )
      throws IOException, ServletException;

  @Override
  public void destroy() {
  }

//KAM:2013014[ Removing due to dependency issues.  This class isn't used anyway
//  protected boolean isUserAuthenticated( HttpServletRequest request ) {
//    return( getAuthenticatedUser( request ) != null );
//  }
//
//  protected Subject getAuthenticatedUser( HttpServletRequest request ) {
//    return Subject.getSubject( AccessController.getContext() );
//  }
//
//  protected Credentials getUserCredentials( HttpServletRequest request ) {
//    return (Credentials)request.getAttribute( CREDENTIALS_ATTRIBUTE );
//  }
//
//  public void setUserCredentials( HttpServletRequest request, Credentials credentials ) {
//    request.setAttribute( CREDENTIALS_ATTRIBUTE, credentials );
//  }
//]

}
