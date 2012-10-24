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
package org.apache.hadoop.gateway.pivot;

import org.apache.hadoop.gateway.util.Regex;
import org.apache.hadoop.gateway.util.Streams;
import org.apache.hadoop.gateway.util.UrlRewriter;
import org.apache.hadoop.gateway.util.Urls;
import org.apache.hadoop.security.authentication.client.AuthenticatedURL;
import org.apache.hadoop.security.authentication.client.AuthenticationException;
import org.apache.hadoop.security.authentication.client.KerberosAuthenticator;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class UrlConnectionPivot extends AbstractGatewayPivot {

  @Override
  public void doGet( HttpServletRequest request, HttpServletResponse response ) throws IOException {

    String sourcePathInfo = request.getPathInfo();
    String sourcePattern = getConfig().getInitParameter( "source" );
    String targetPattern = getConfig().getInitParameter( "target" );

    //TODO: This should be more at filter init.
    Pattern sourceRegex = UrlRewriter.compileUrlRegex( sourcePattern );
    Matcher matcher = sourceRegex.matcher( sourcePathInfo );
    String targetUrl = MessageFormat.format( targetPattern, Regex.toGroupArray( matcher ) );
//    System.out.println( "Source URI: " + request.getRequestURI() );
//    System.out.println( "Source URL: " + request.getRequestURL() );
//    System.out.println( "Source Query: " + request.getQueryString() );
//    System.out.println( "Source pathInfo: " + sourcePathInfo );
//    System.out.println( "Source pattern: " + sourcePattern );
//    System.out.println( "Target pattern: " + targetPattern );
//    System.out.println( "Resolved target: " + targetUrl );

    StringBuilder paramStr = new StringBuilder();
    Enumeration<String> paramNames = request.getParameterNames();
    if( paramNames.hasMoreElements() ) {
      paramStr.append( "?" );
    }
    while( paramNames.hasMoreElements() ) {
      String paramName = paramNames.nextElement();
      String paramValue = request.getParameter( paramName );
      paramStr.append( paramName );
      paramStr.append( "=" );
      paramStr.append( URLEncoder.encode( paramValue, "UTF-8" ) );
      if( paramNames.hasMoreElements() ) {
        paramStr.append( "&" );
      }
    }

    try {
      URL clientUrl = new URL( targetUrl + paramStr.toString() );
      //System.out.println( "Resolved query: " + clientUrl );
      AuthenticatedURL.Token token = new AuthenticatedURL.Token();
      KerberosAuthenticator authenticator = new KerberosAuthenticator();
      HttpURLConnection conn = new AuthenticatedURL( authenticator ).openConnection( clientUrl, token );
      //System.out.println( "STATUS=" + conn.getResponseCode() );
      InputStream input = conn.getInputStream();
      if( input != null ) {
        OutputStream output = response.getOutputStream();
        try {
          Streams.drainStream( input, output );
        } finally {
          output.flush();
          input.close();
        }
      }
    } catch( AuthenticationException e ) {
      response.sendError( HttpServletResponse.SC_UNAUTHORIZED );
      e.printStackTrace();
    } catch( FileNotFoundException e ) {
      response.sendError( HttpServletResponse.SC_NOT_FOUND );
      e.printStackTrace();
    }

  }

}
