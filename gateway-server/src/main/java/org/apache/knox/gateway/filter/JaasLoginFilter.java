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

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 *
 */
public class JaasLoginFilter extends AbstractGatewayFilter {

  public static final String JGSS_LOGIN_MOUDLE = "com.sun.security.jgss.login";

  @Override
  public void doFilter( final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain ) throws IOException, ServletException {

//KAM:2013014[ Removing due to dependency issues.  This class isn't used anyway
//    if( !isUserAuthenticated( request ) ) {
//      Credentials credentials = getUserCredentials( request );
//      CredentialsProvider credentialsProvider = new CredentialsProvider( credentials );
//
//      try {
//
//        LoginContext loginContext = new LoginContext( JGSS_LOGIN_MOUDLE, credentialsProvider );
//        loginContext.login();
//        Subject subject = loginContext.getSubject();
//
//        // Adding the user principal to the public credentials because we need the username in the subject later.
//        subject.getPublicCredentials().add( new PrincipalCredentials( credentials.getUserPrincipal() ) );
//        //System.out.println( "Subject=" + subject );
//
//        PrivilegedExceptionAction<Void> action = new PrivilegedExceptionAction<>() {
//          @Override
//          public Void run() throws Exception {
//            chain.doFilter( request, response );
//            return null;
//          }
//        };
//
//        Subject.doAs( subject, action );
//
//      } catch( PrivilegedActionException e ) {
//        e.printStackTrace();
//        throw new ServletException( e );
//      } catch( LoginException e ) {
//        e.printStackTrace();
//        HttpServletResponse httpResponse = (HttpServletResponse)response;
//        httpResponse.sendError( HttpServletResponse.SC_UNAUTHORIZED );
//      }
//    }
//]
  }

//    URL loginUrl = ClassLoader.getSystemResource( "jaas.conf" );
//    System.setProperty( "java.security.auth.login.config", loginUrl.getFile() );
//    URL krbUrl = ClassLoader.getSystemResource( "krb5.conf" );
//    System.setProperty( "java.security.krb5.conf", krbUrl.getFile() );
//    System.setProperty( "java.security.krb5.debug", "true" );
//    System.setProperty( "javax.security.auth.useSubjectCredsOnly", "true" );
//    System.setProperty( "java.security.krb5.realm", "VM.HOME" );
//    System.setProperty( "java.security.krb5.kdc", "192.168.1.170" ); //org.apache.hadoop-secure.home" );

//    URL url = new URL( "" );
//    AuthenticatedURL.Token token = new AuthenticatedURL.Token();
//    Authenticator authenticator = null; // url, token
//    HttpURLConnection conn = new AuthenticatedURL( authenticator ).openConnection();
}
