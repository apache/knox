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
package org.apache.hadoop.gateway.service.knoxsso;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.Date;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.security.token.JWTokenAuthority;
import org.apache.hadoop.gateway.services.security.token.impl.JWT;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

@Path( "/knoxsso/websso" )
public class WebSSOResource {
  @Context 
  private HttpServletRequest request;

  @Context 
  private HttpServletResponse response;

  @GET
  @Produces({APPLICATION_JSON, APPLICATION_XML})
  public Response doGet() {
    return getAuthenticationToken(HttpServletResponse.SC_TEMPORARY_REDIRECT);
  }

  @POST
  @Produces({APPLICATION_JSON, APPLICATION_XML})
  public Response doPost() {
    return getAuthenticationToken(HttpServletResponse.SC_SEE_OTHER);
  }

  private Response getAuthenticationToken(int statusCode) {
    GatewayServices services = (GatewayServices) request.getServletContext()
        .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    boolean removeOriginalUrlCookie = true;
    String original = getCookieValue((HttpServletRequest) request, "original-url");
    if (original == null) {
      // in the case where there is no SAML redirects done before here
      // we need to get it from the request parameters
      removeOriginalUrlCookie = false;
      original = request.getParameter("originalUrl");
    }
    
    JWTokenAuthority ts = services.getService(GatewayServices.TOKEN_SERVICE);
    Principal p = ((HttpServletRequest)request).getUserPrincipal();

    JWT token = ts.issueToken(p, "RS256");
    
    addJWTHadoopCookie(original, token);
    addHadoopCookie(original, p);
    
    if (removeOriginalUrlCookie) {
      removeOriginalUrlCookie(response);
    }
    
    System.out.println( new Date() + "about to redirect to original: " + original );
    response.setStatus(statusCode);
    response.setHeader("Location", original);
    try {
      response.getOutputStream().close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  public void addJWTHadoopCookie(String original, JWT token) {
    System.out.println( "adding JWT cookie: " + token.toString() );
    Cookie c = new Cookie("hadoop-jwt",  token.toString());
    c.setPath("/");
    try {
      String domain = getDomainName(original);
      c.setDomain(domain);
      // c.setHttpOnly(false);
      // c.setSecure(false);
    }
    catch(Exception e) {
      e.printStackTrace();
    }
    c.setMaxAge(120);
    response.addCookie(c);
  }

  public void addHadoopCookie(String original, Principal p) {
    System.out.println( "adding cookie with username: " + p.getName() );
    Cookie c = new Cookie("hadoop-auth", p.getName());
    c.setPath("/");
    try {
      String domain = getDomainName(original);
      System.out.println("Setting domain on cookie: " + domain);
      c.setDomain(domain);
    }
    catch(Exception e) {
      e.printStackTrace();
    }
    c.setMaxAge(120);
    response.addCookie(c);
  }

  private void removeOriginalUrlCookie(HttpServletResponse response) {
    Cookie c = new Cookie("original-url", null);
    c.setMaxAge(0);
    c.setPath("/knoxsso/websso");
    response.addCookie(c);
  }

  public String getDomainName(String url) throws URISyntaxException {
      URI uri = new URI(url);
      String domain = uri.getHost();
      return domain.startsWith("www.") ? domain.substring(4) : domain.substring(domain.indexOf('.'));
  }

  private String getCookieValue(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    String value = null;
    for(Cookie cookie : cookies){
      System.out.println( "cookie name: " + cookie.getName());
      System.out.println( "cookie value: " + cookie.getValue());
      if(name.equals(cookie.getName())){
          value = cookie.getValue();
      }
    }
    return value;
  }
}
