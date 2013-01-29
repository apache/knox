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
package org.apache.hadoop.gateway.filter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import com.wealdtech.DataError;
import com.wealdtech.ServerError;
import com.wealdtech.hawk.Hawk;
import com.wealdtech.hawk.HawkCredentials;
import com.wealdtech.hawk.HawkServer;
import com.wealdtech.hawk.HawkServerConfiguration;


import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class HawkAuthenticationFilter implements Filter {

  private transient HawkCredentialStore store = new HawkCredentialStore();
  private transient HawkServer server;
  private transient HawkCredentials credentials;


  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    HawkServerConfiguration configuration = null;
    
    // TODO: credentials must come from the HawkCredentialStore member and
    // must be unique per keyId
    credentials = new HawkCredentials.Builder()
      .keyId("dh37fgj492je")
      .key("werxhqb98rpaxn39848xrunpaw3489ruxnpa98w4rxn")
      .algorithm(HawkCredentials.Algorithm.SHA256)
      .build();
    
    this.server = new HawkServer.Builder().configuration(configuration).build();
  }

  public void destroy() {
    
  }

  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
      throws IOException, ServletException {
    if (request.getParameter("bewit") != null) {
      handleAuthenticationBewit(request, response);
    }
    else {
      handleAuthenticationHeader(request, response);
    }
  }


  private void handleAuthenticationHeader(ServletRequest request,
      ServletResponse response) {
    // Obtain parameters available from the request
    URI uri = null;
    HttpServletRequest req = (HttpServletRequest)request;
    HttpServletResponse res = (HttpServletResponse)response;
    try
    {
      System.out.println("request uri: " + req.getRequestURI());
      String qs = req.getQueryString();
      System.out.println("query string: " + qs);
      String scheme = req.getScheme();
      if (scheme == null) {
        scheme = "http";
      }
      if (qs != null) {
        uri = new URI(scheme + "://" + req.getHeader("Host") + req.getRequestURI() + "?" + qs);
      }
      else {
        uri = new URI(scheme + "://" + req.getHeader("Host") + req.getRequestURI());
      }
      System.out.println("server side uri: " + uri);
    }
    catch (URISyntaxException use)
    {
      System.out.println("Not authenticated: " + use.getLocalizedMessage());
      addAuthenticateHeader(res);
      sendUnauthorized(res);
    }

    ImmutableMap<String, String> authorizationHeaders = ImmutableMap.of();
    try
    {
      authorizationHeaders = this.server.splitAuthorizationHeader(req.getHeader("Authorization"));
    }
    catch (DataError de)
    {
      System.out.println("Not authenticated: " + de.getLocalizedMessage());
      addAuthenticateHeader(res);
      sendUnauthorized(res);
    }

    String hash = null;
    if (authorizationHeaders.get("hash") != null)
    {
      // Need to calculate hash of the body
      try {
        hash = Hawk.calculateMac(this.credentials, CharStreams.toString(req.getReader()));
      } catch (DataError e) {
        System.out.println("Not authenticated: " + e.getLocalizedMessage());
        addAuthenticateHeader(res);
        sendUnauthorized(res);
      } catch (ServerError e) {
        System.out.println("Not authenticated: " + e.getLocalizedMessage());
        addAuthenticateHeader(res);
        sendInternalSystemError(res);
      } catch (IOException e) {
        System.out.println("Not authenticated: " + e.getLocalizedMessage());
        addAuthenticateHeader(res);
        sendInternalSystemError(res);
      }
      System.out.println("server calculated MAC: " + hash);
    }

    try
    {
      server.authenticate(this.credentials, uri, req.getMethod(), authorizationHeaders, hash);
      System.out.println("Authenticated by header");
      res.setStatus(200);
    }
    catch (DataError de)
    {
      System.out.println("Not authenticated: " + de.getLocalizedMessage());
      addAuthenticateHeader(res);
      sendUnauthorized(res);
    }
    catch (ServerError se)
    {
      System.out.println("Not authenticated: " + se.getLocalizedMessage());
      addAuthenticateHeader(res);
      sendInternalSystemError(res);
    }    
  }

  private void handleAuthenticationBewit(ServletRequest request,
      ServletResponse response) {
    HttpServletRequest req = (HttpServletRequest)request;
    HttpServletResponse res = (HttpServletResponse)response;
    if (!req.getMethod().equals("GET"))
    {
      System.out.println("Not authenticated: HTTP method " + req.getMethod() + " not supported with bewit");
      addAuthenticateHeader(res);
      sendUnauthorized(res);
    }
    
    URI uri = null;
    try
    {
      uri = new URI(req.getScheme() + "://" + req.getHeader("Host") + req.getRequestURI());
    }
    catch (URISyntaxException use)
    {
      System.out.println("Not authenticated: " + use.getLocalizedMessage());
      addAuthenticateHeader(res);
      sendUnauthorized(res);
    }

    try
    {
      server.authenticate(this.credentials, uri);
      System.out.println("Authenticated by bewit");
      res.setStatus(200);
    }
    catch (DataError de)
    {
      System.out.println("Not authenticated: " + de.getLocalizedMessage());
      addAuthenticateHeader(res);
      sendUnauthorized(res);
    }
    catch (ServerError se)
    {
      System.out.println("Not authenticated: " + se.getLocalizedMessage());
      addAuthenticateHeader(res);
      sendInternalSystemError(res);
    }    
  }

  private void sendInternalSystemError(HttpServletResponse res) {
    sendErrorCode(res, 500);
  }

  private void sendUnauthorized(HttpServletResponse res) {
    sendErrorCode(res, 401);
  }

  private void sendErrorCode(HttpServletResponse res, int code) {
    try {
      res.sendError(code);
    } catch (IOException e) {
      // TODO: log appropriately
      e.printStackTrace();
    }
  }

  private void addAuthenticateHeader(HttpServletResponse res) {
    String authenticate = server.generateAuthenticateHeader();
    res.setHeader("WWW-Authenticate", authenticate);
  }
}
