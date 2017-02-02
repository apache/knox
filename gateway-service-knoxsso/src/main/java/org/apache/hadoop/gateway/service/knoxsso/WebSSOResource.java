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
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.WebApplicationException;

import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.security.token.JWTokenAuthority;
import org.apache.hadoop.gateway.services.security.token.TokenServiceException;
import org.apache.hadoop.gateway.services.security.token.impl.JWT;
import org.apache.hadoop.gateway.util.RegExUtils;
import org.apache.hadoop.gateway.util.Urls;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

@Path( WebSSOResource.RESOURCE_PATH )
public class WebSSOResource {
  private static final String SSO_COOKIE_NAME = "knoxsso.cookie.name";
  private static final String SSO_COOKIE_SECURE_ONLY_INIT_PARAM = "knoxsso.cookie.secure.only";
  private static final String SSO_COOKIE_MAX_AGE_INIT_PARAM = "knoxsso.cookie.max.age";
  private static final String SSO_COOKIE_DOMAIN_SUFFIX_PARAM = "knoxsso.cookie.domain.suffix";
  private static final String SSO_COOKIE_TOKEN_TTL_PARAM = "knoxsso.token.ttl";
  private static final String SSO_COOKIE_TOKEN_AUDIENCES_PARAM = "knoxsso.token.audiences";
  private static final String SSO_COOKIE_TOKEN_WHITELIST_PARAM = "knoxsso.redirect.whitelist.regex";
  private static final String SSO_ENABLE_SESSION_PARAM = "knoxsso.enable.session";
  private static final String ORIGINAL_URL_REQUEST_PARAM = "originalUrl";
  private static final String ORIGINAL_URL_COOKIE_NAME = "original-url";
  private static final String DEFAULT_SSO_COOKIE_NAME = "hadoop-jwt";
  // default for the whitelist - open up for development - relative paths and localhost only
  private static final String DEFAULT_WHITELIST = "^/.*$;^https?://(localhost|127.0.0.1|0:0:0:0:0:0:0:1|::1):\\d{0,9}/.*$";
  static final String RESOURCE_PATH = "/api/v1/websso";
  private static KnoxSSOMessages log = MessagesFactory.get( KnoxSSOMessages.class );
  private String cookieName = null;
  private boolean secureOnly = true;
  private int maxAge = -1;
  private long tokenTTL = 30000l;
  private String whitelist = null;
  private String domainSuffix = null;
  private String[] targetAudiences = null;
  private boolean enableSession = false;

  @Context
  private HttpServletRequest request;

  @Context
  private HttpServletResponse response;

  @Context
  ServletContext context;

  @PostConstruct
  public void init() {

    // configured cookieName
    cookieName = context.getInitParameter(SSO_COOKIE_NAME);
    if (cookieName == null) {
      cookieName = DEFAULT_SSO_COOKIE_NAME;
    }

    String secure = context.getInitParameter(SSO_COOKIE_SECURE_ONLY_INIT_PARAM);
    if (secure != null) {
      secureOnly = ("false".equals(secure) ? false : true);
      if (!secureOnly) {
        log.cookieSecureOnly(secureOnly);
      }
    }

    String age = context.getInitParameter(SSO_COOKIE_MAX_AGE_INIT_PARAM);
    if (age != null) {
      try {
        log.setMaxAge(age);
        maxAge = Integer.parseInt(age);
      }
      catch (NumberFormatException nfe) {
        log.invalidMaxAgeEncountered(age);
      }
    }

    domainSuffix = context.getInitParameter(SSO_COOKIE_DOMAIN_SUFFIX_PARAM);

    whitelist = context.getInitParameter(SSO_COOKIE_TOKEN_WHITELIST_PARAM);
    if (whitelist == null) {
      // default to local/relative targets
      whitelist = DEFAULT_WHITELIST;
    }

    String audiences = context.getInitParameter(SSO_COOKIE_TOKEN_AUDIENCES_PARAM);
    if (audiences != null) {
      targetAudiences = audiences.split(",");
    }

    String ttl = context.getInitParameter(SSO_COOKIE_TOKEN_TTL_PARAM);
    if (ttl != null) {
      try {
        tokenTTL = Long.parseLong(ttl);
      }
      catch (NumberFormatException nfe) {
        log.invalidTokenTTLEncountered(ttl);
      }
    }

    String enableSession = context.getInitParameter(SSO_ENABLE_SESSION_PARAM);
    this.enableSession = ("true".equals(enableSession));
  }

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
    String original = getCookieValue((HttpServletRequest) request, ORIGINAL_URL_COOKIE_NAME);
    if (original == null) {
      // in the case where there are no SAML redirects done before here
      // we need to get it from the request parameters
      removeOriginalUrlCookie = false;
      original = getOriginalUrlFromQueryParams();
      if (original == null) {
        log.originalURLNotFound();
        throw new WebApplicationException("Original URL not found in the request.", Response.Status.BAD_REQUEST);
      }
      boolean validRedirect = RegExUtils.checkWhitelist(whitelist, original);
      if (!validRedirect) {
        log.whiteListMatchFail(original, whitelist);
        throw new WebApplicationException("Original URL not valid according to the configured whitelist.",
                Response.Status.BAD_REQUEST);
      }
    }

    JWTokenAuthority ts = services.getService(GatewayServices.TOKEN_SERVICE);
    Principal p = ((HttpServletRequest)request).getUserPrincipal();

    try {
      JWT token = null;
      if (targetAudiences == null || targetAudiences.length == 0) {
        token = ts.issueToken(p, "RS256", getExpiry());
      } else {
        ArrayList<String> aud = new ArrayList<String>();
        for (int i = 0; i < targetAudiences.length; i++) {
          aud.add(targetAudiences[i]);
        }
        token = ts.issueToken(p, aud, "RS256", getExpiry());
      }

      // Coverity CID 1327959
      if( token != null ) {
        addJWTHadoopCookie( original, token );
      }

      if (removeOriginalUrlCookie) {
        removeOriginalUrlCookie(response);
      }

      log.aboutToRedirectToOriginal(original);
      response.setStatus(statusCode);
      response.setHeader("Location", original);
      try {
        response.getOutputStream().close();
      } catch (IOException e) {
        log.unableToCloseOutputStream(e.getMessage(), e.getStackTrace().toString());
      }
    }
    catch (TokenServiceException e) {
      log.unableToIssueToken(e);
    }
    URI location = null;
    try {
      location = new URI(original);
    }
    catch(URISyntaxException urise) {
      // todo log return error response
    }

    if (!enableSession) {
      // invalidate the session to avoid autologin
      // Coverity CID 1352857
      HttpSession session = request.getSession(false);
      if( session != null ) {
        session.invalidate();
      }
    }

    return Response.seeOther(location).entity("{ \"redirectTo\" : " + original + " }").build();
  }

  private String getOriginalUrlFromQueryParams() {
    String original = request.getParameter(ORIGINAL_URL_REQUEST_PARAM);
    StringBuffer buf = new StringBuffer(original);

    // Add any other query params.
    // Probably not ideal but will not break existing integrations by requiring
    // some encoding.
    Map<String, String[]> params = request.getParameterMap();
    for (Entry<String, String[]> entry : params.entrySet()) {
      if (!ORIGINAL_URL_REQUEST_PARAM.equals(entry.getKey())
          && !original.contains(entry.getKey() + "=")) {
        buf.append("&").append(entry.getKey());
        String[] values = entry.getValue();
        if (values.length > 0 && values[0] != null) {
          buf.append("=");
        }
        for (int i = 0; i < values.length; i++) {
          if (values[0] != null) {
            buf.append(values[i]);
            if (i < values.length-1) {
              buf.append("&").append(entry.getKey()).append("=");
            }
          }
        }
      }
    }

    return buf.toString();
  }

  private long getExpiry() {
    long expiry = 0l;
    if (tokenTTL == -1) {
      expiry = -1;
    }
    else {
      expiry = System.currentTimeMillis() + tokenTTL;
    }
    return expiry;
  }

  private void addJWTHadoopCookie(String original, JWT token) {
    log.addingJWTCookie(token.toString());
    Cookie c = new Cookie(cookieName,  token.toString());
    c.setPath("/");
    try {
      String domain = Urls.getDomainName(original, domainSuffix);
      if (domain != null) {
        c.setDomain(domain);
      }
      c.setHttpOnly(true);
      if (secureOnly) {
        c.setSecure(true);
      }
      if (maxAge != -1) {
        c.setMaxAge(maxAge);
      }
      response.addCookie(c);
      log.addedJWTCookie();
    }
    catch(Exception e) {
      log.unableAddCookieToResponse(e.getMessage(), e.getStackTrace().toString());
      throw new WebApplicationException("Unable to add JWT cookie to response.");
    }
  }

  private void removeOriginalUrlCookie(HttpServletResponse response) {
    Cookie c = new Cookie(ORIGINAL_URL_COOKIE_NAME, null);
    c.setMaxAge(0);
    c.setPath(RESOURCE_PATH);
    response.addCookie(c);
  }

  private String getCookieValue(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    String value = null;
    if (cookies != null) {
      for(Cookie cookie : cookies){
        if(name.equals(cookie.getName())){
          value = cookie.getValue();
        }
      }
    }
    if (value == null) {
      log.cookieNotFound(name);
    }
    return value;
  }
}
