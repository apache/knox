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

import org.apache.commons.codec.binary.Base64;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.UsernamePasswordCredentials;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 *
 */
//TODO: Remove the Authenticate header from the expect so that downstream filters and dispatch don't see it.
public class BasicAuthChallengeFilter extends AbstractGatewayFilter {

  @Override
  public void doFilter( HttpServletRequest request, HttpServletResponse response, FilterChain chain ) throws IOException, ServletException {

//KAM:2013014[ Removing due to dependency issues.  This class isn't used anyway
//    boolean challenge = false;
//
//    // If the user isn't already authenticated.
//    if( Subject.getSubject( AccessController.getContext() ) == null ) {
//      UsernamePasswordCredentials credentials = createValidatedCredentials( request );
//      if( credentials == null ) {
//        challenge = true;
//      } else {
//        setUserCredentials( request, credentials );
//      }
//    }
//    if( challenge ) {
//      response.setHeader( AUTH.WWW_AUTH, "Basic realm=\"" + getConfig().getInitParameter( "realm" ) + "\"" );
//      response.sendError( HttpServletResponse.SC_UNAUTHORIZED );
//    } else {
//      chain.doFilter( request, response );
//      if( HttpServletResponse.SC_UNAUTHORIZED == response.getStatus() &&
//          response.getHeader( AUTH.WWW_AUTH ) == null ) {
//        response.setHeader( AUTH.WWW_AUTH, "Basic realm=\"" + getConfig().getInitParameter( "realm" ) + "\"" );
//      }
//    }

  }

  private static UsernamePasswordCredentials createValidatedCredentials( HttpServletRequest request ) {
    UsernamePasswordCredentials credentials = null;
    String basicAuthResponse = request.getHeader( AUTH.WWW_AUTH_RESP );
    if( basicAuthResponse != null ) {
      String[] parts = basicAuthResponse.split( " " );
      if( parts.length == 2 ) {
        String usernamePassword = new String( Base64.decodeBase64( parts[1] ), StandardCharsets.UTF_8);
        parts = usernamePassword.split( ":" );
        if( parts.length == 2 ) {
          String username = parts[0];
          String password = parts[1];
          if( username.length() > 0 && password.length() > 0 ) {
            credentials = new UsernamePasswordCredentials( username, password );
          }
        }
      }
      //System.out.println( basicAuthResponse );
    }
    return credentials;
  }
}
