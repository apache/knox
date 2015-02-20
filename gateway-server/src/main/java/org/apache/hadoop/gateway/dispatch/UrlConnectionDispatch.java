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

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.gateway.GatewayMessages;
import org.apache.hadoop.gateway.GatewayResources;
import org.apache.hadoop.gateway.audit.api.Action;
import org.apache.hadoop.gateway.audit.api.ActionOutcome;
import org.apache.hadoop.gateway.audit.api.AuditServiceFactory;
import org.apache.hadoop.gateway.audit.api.Auditor;
import org.apache.hadoop.gateway.audit.api.ResourceType;
import org.apache.hadoop.gateway.audit.log4j.audit.AuditConstants;
import org.apache.hadoop.gateway.filter.AbstractGatewayFilter;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.i18n.resources.ResourcesFactory;
import org.apache.hadoop.gateway.util.urltemplate.Parser;
import org.apache.hadoop.gateway.util.urltemplate.Resolver;
import org.apache.hadoop.gateway.util.urltemplate.Rewriter;
import org.apache.hadoop.gateway.util.urltemplate.Template;
import org.apache.hadoop.security.authentication.client.AuthenticatedURL;
import org.apache.hadoop.security.authentication.client.AuthenticationException;
import org.apache.hadoop.security.authentication.client.KerberosAuthenticator;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Enumeration;

/**
 *
 */
public class UrlConnectionDispatch extends AbstractGatewayFilter {

  private static final GatewayMessages LOG = MessagesFactory.get( GatewayMessages.class );
  private static final GatewayResources RES = ResourcesFactory.get( GatewayResources.class );
  private static Auditor auditor = AuditServiceFactory.getAuditService().getAuditor( AuditConstants.DEFAULT_AUDITOR_NAME,
          AuditConstants.KNOX_SERVICE_NAME, AuditConstants.KNOX_COMPONENT_NAME );

  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
    String method = request.getMethod().toUpperCase();
    if (method.equals("GET")) {
      try {
        doGet(getDispatchUrl(request), request, response);
      } catch ( URISyntaxException e ) {
        throw new ServletException(e);
      }
    } else {
      response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
  }

  protected static URI getDispatchUrl(HttpServletRequest request) {
    StringBuffer str = request.getRequestURL();
    String query = request.getQueryString();
    if ( query != null ) {
      str.append('?');
      str.append(query);
    }
    URI url = URI.create(str.toString());
    return url;
  }

  public void doGet( URI url, HttpServletRequest request, HttpServletResponse response ) throws IOException, URISyntaxException {
    String sourcePathInfo = request.getPathInfo();
    String sourcePattern = getConfig().getInitParameter( "pattern" );
    String targetPattern = getConfig().getInitParameter( "target" );

    //TODO: Some of the compilation should be done at servlet init for performance reasons.
    Template sourceTemplate = Parser.parse( sourcePattern );
    Template targetTemplate = Parser.parse( targetPattern );

    Resolver resolver = new DispatchParamResolver( getConfig(), request );
    URI sourceUri = new URI( sourcePathInfo );
    URI targetUri = Rewriter.rewrite( sourceUri, sourceTemplate, targetTemplate, resolver, null );

//    //TODO: This should be more at filter init.
//    Pattern sourceRegex = UrlRewriter.compileUrlRegex( sourcePattern );
//    Matcher matcher = sourceRegex.matcher( sourcePathInfo );
//    String targetUrl = MessageFormat.format( targetPattern, Regex.toGroupArray( matcher ) );
//    System.out.println( "Source URI: " + expect.getRequestURI() );
//    System.out.println( "Source URL: " + expect.getRequestURL() );
//    System.out.println( "Source Query: " + expect.getQueryString() );
//    System.out.println( "Source pathInfo: " + sourcePathInfo );
//    System.out.println( "Source pattern: " + sourcePattern );
//    System.out.println( "Target pattern: " + targetPattern );
//    System.out.println( "Resolved target: " + targetUrl );

    StringBuilder paramStr = new StringBuilder();
    Enumeration paramNames = request.getParameterNames();
    if( paramNames.hasMoreElements() ) {
      paramStr.append( "?" );
    }
    while( paramNames.hasMoreElements() ) {
      String paramName = (String)paramNames.nextElement();
      String paramValue = request.getParameter( paramName );
      paramStr.append( paramName );
      paramStr.append( "=" );
      paramStr.append( URLEncoder.encode( paramValue, "UTF-8" ) );
      if( paramNames.hasMoreElements() ) {
        paramStr.append( "&" );
      }
    }
    String urlStr = targetUri.toString() + paramStr.toString();
    try {
      URL clientUrl = new URL( urlStr );
      //System.out.println( "Resolved query: " + clientUrl );
      AuthenticatedURL.Token token = new AuthenticatedURL.Token();
      KerberosAuthenticator authenticator = new KerberosAuthenticator();
      auditor.audit( Action.DISPATCH, urlStr, ResourceType.URI, ActionOutcome.UNAVAILABLE );
      HttpURLConnection conn = new AuthenticatedURL( authenticator ).openConnection( clientUrl, token );
      //System.out.println( "STATUS=" + conn.getResponseCode() );
      InputStream input = conn.getInputStream();
      if( input != null ) {
        OutputStream output = response.getOutputStream();
        try {
          IOUtils.copy( input, output );
        } finally {
          output.flush();
          input.close();
        }
      }
      auditor.audit( Action.DISPATCH, urlStr, ResourceType.URI, ActionOutcome.SUCCESS );
    } catch( AuthenticationException e ) {
      response.sendError( HttpServletResponse.SC_UNAUTHORIZED );
      LOG.failedToEstablishConnectionToUrl( urlStr, e );
      auditor.audit( Action.DISPATCH, urlStr, ResourceType.URI, ActionOutcome.FAILURE, RES.responseStatus( HttpServletResponse.SC_UNAUTHORIZED ) );
    } catch( FileNotFoundException e ) {
      response.sendError( HttpServletResponse.SC_NOT_FOUND );
      LOG.failedToEstablishConnectionToUrl( urlStr, e );
      auditor.audit( Action.DISPATCH, urlStr, ResourceType.URI, ActionOutcome.FAILURE, RES.responseStatus( HttpServletResponse.SC_NOT_FOUND ) );
    }

  }

}
