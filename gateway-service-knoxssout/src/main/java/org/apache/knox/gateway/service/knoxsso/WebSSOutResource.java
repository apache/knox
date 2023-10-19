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
package org.apache.knox.gateway.service.knoxsso;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.session.control.ConcurrentSessionVerifier;
import org.apache.knox.gateway.util.Tokens;
import org.apache.knox.gateway.util.Urls;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Optional;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.Response.ok;

@Path( WebSSOutResource.RESOURCE_PATH )
public class WebSSOutResource {
  private static final KnoxSSOutMessages log = MessagesFactory.get( KnoxSSOutMessages.class );

  private static final String SSO_COOKIE_NAME = "knoxsso.cookie.name";
  private static final String DEFAULT_SSO_COOKIE_NAME = "hadoop-jwt";

  static final String RESOURCE_PATH = "knoxssout/api/v1/webssout";

  private String cookieName;

  @Context
  HttpServletRequest request;

  @Context
  HttpServletResponse response;

  @Context
  ServletContext context;

  @PostConstruct
  public void init() {
    cookieName = context.getInitParameter(SSO_COOKIE_NAME);
    if (cookieName == null) {
      cookieName = DEFAULT_SSO_COOKIE_NAME;
    }
  }

  @GET
  @Produces({APPLICATION_JSON, APPLICATION_XML})
  public Response doGet() {
    boolean rc = removeAuthenticationToken(response);
    if (rc) {
      return ok().entity("{ \"loggedOut\" : true }").build();
    } else {
      return ok().entity("{ \"loggedOut\" : false }").build();
    }
  }

  @POST
  @Produces({APPLICATION_JSON, APPLICATION_XML})
  public Response doPost() {
    boolean rc = removeAuthenticationToken(response);
    if (rc) {
      return ok().entity("{ \"loggedOut\" : true }").build();
    } else {
      return ok().entity("{ \"loggedOut\" : false }").build();
    }
  }

  private boolean removeAuthenticationToken(HttpServletResponse response) {
    boolean rc = true;
    Cookie c = new Cookie(cookieName, null);
    c.setMaxAge(0);
    c.setPath("/");
    try {
      String domainName = Urls.getDomainName(request.getRequestURL().toString(), null);
      if(domainName != null) {
        c.setDomain(domainName);
      }
    } catch (MalformedURLException e) {
      log.problemWithCookieDomainUsingDefault();
      // we are probably not going to be able to
      // remove the cookie due to this error but it
      // isn't necessarily not going to work.
      rc = false;
    }
    response.addCookie(c);

    Optional<Cookie> ssoCookie = findCookie(cookieName);
    if (ssoCookie.isPresent()) {
      GatewayServices gwServices =
              (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
      if (gwServices != null) {
        ConcurrentSessionVerifier verifier = gwServices.getService(ServiceType.CONCURRENT_SESSION_VERIFIER);
        verifier.sessionEndedForUser(request.getUserPrincipal().getName(), ssoCookie.get().getValue());
        removeKnoxSsoCookie(ssoCookie.get(), gwServices);
      }
    } else {
      log.couldNotFindCookieWithTokenToRemove(cookieName, request.getUserPrincipal().getName());
    }
    return rc;
  }

  private void removeKnoxSsoCookie(Cookie ssoCookie, GatewayServices gwServices) {
    final TokenStateService tokenStateService = gwServices.getService(ServiceType.TOKEN_STATE_SERVICE);
    if (tokenStateService!= null) {
      try {
        final JWTToken jwt = new JWTToken(ssoCookie.getValue());
        tokenStateService.revokeToken(jwt);
        final String revoker = SubjectUtils.getCurrentEffectivePrincipalName();
        log.revokedToken(getTopologyName(),
            Tokens.getTokenDisplayText(ssoCookie.getValue()),
            Tokens.getTokenIDDisplayText(TokenUtils.getTokenId(jwt)),
            revoker);
      } catch (ParseException | UnknownTokenException e) {
        // NOP: cookie maybe invalid or token management was disabled anyway
      }
    }
  }

  private String getTopologyName() {
    return (String) context.getAttribute("org.apache.knox.gateway.gateway.cluster");
  }

  private Optional<Cookie> findCookie(String cookieName) {
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      return Arrays.stream(cookies).filter(cookie -> cookie.getName().equals(cookieName)).findFirst();
    } else {
      return Optional.empty();
    }
  }
}
