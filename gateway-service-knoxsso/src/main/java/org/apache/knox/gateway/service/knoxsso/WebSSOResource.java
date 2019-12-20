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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.audit.log4j.audit.Log4jAuditor;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.util.CookieUtils;
import org.apache.knox.gateway.util.RegExUtils;
import org.apache.knox.gateway.util.Urls;
import org.apache.knox.gateway.util.WhitelistUtils;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.apache.knox.gateway.services.GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE;

@Path( WebSSOResource.RESOURCE_PATH )
public class WebSSOResource {
  private static final KnoxSSOMessages LOGGER = MessagesFactory.get( KnoxSSOMessages.class );

  private static final String SSO_COOKIE_NAME = "knoxsso.cookie.name";
  private static final String SSO_COOKIE_SECURE_ONLY_INIT_PARAM = "knoxsso.cookie.secure.only";
  private static final String SSO_COOKIE_MAX_AGE_INIT_PARAM = "knoxsso.cookie.max.age";
  private static final String SSO_COOKIE_DOMAIN_SUFFIX_PARAM = "knoxsso.cookie.domain.suffix";
  private static final String SSO_COOKIE_TOKEN_TTL_PARAM = "knoxsso.token.ttl";
  private static final String SSO_COOKIE_TOKEN_AUDIENCES_PARAM = "knoxsso.token.audiences";
  private static final String SSO_COOKIE_TOKEN_SIG_ALG = "knoxsso.token.sigalg";
  private static final String SSO_COOKIE_TOKEN_WHITELIST_PARAM = "knoxsso.redirect.whitelist.regex";

  private static final String SSO_SIGNINGKEY_KEYSTORE_NAME = "knoxsso.signingkey.keystore.name";
  private static final String SSO_SIGNINGKEY_KEYSTORE_ALIAS = "knoxsso.signingkey.keystore.alias";
  private static final String SSO_SIGNINGKEY_KEYSTORE_PASSPHRASE_ALIAS = "knoxsso.signingkey.keystore.passphrase.alias";

  /* parameters expected by knoxsso */
  private static final String SSO_EXPECTED_PARAM = "knoxsso.expected.params";

  private static final String SSO_ENABLE_SESSION_PARAM = "knoxsso.enable.session";
  private static final String ORIGINAL_URL_REQUEST_PARAM = "originalUrl";
  private static final String ORIGINAL_URL_COOKIE_NAME = "original-url";
  private static final String DEFAULT_SSO_COOKIE_NAME = "hadoop-jwt";
  private static final long TOKEN_TTL_DEFAULT = 30000L;
  static final String RESOURCE_PATH = "/api/v1/websso";
  private String cookieName;
  private boolean secureOnly = true;
  private int maxAge = -1;
  private long tokenTTL = TOKEN_TTL_DEFAULT;
  private String whitelist;
  private String domainSuffix;
  private List<String> targetAudiences = new ArrayList<>();
  private boolean enableSession;
  private String signatureAlgorithm = "RS256";
  private List<String> ssoExpectedparams = new ArrayList<>();
  private String clusterName;

  @Context
  HttpServletRequest request;

  @Context
  HttpServletResponse response;

  @Context
  ServletContext context;

  @PostConstruct
  public void init() {
    clusterName = String.valueOf(context.getAttribute(GATEWAY_CLUSTER_ATTRIBUTE));

    handleCookieSetup();

    String enableSessionStr = context.getInitParameter(SSO_ENABLE_SESSION_PARAM);
    this.enableSession = Boolean.parseBoolean(enableSessionStr);

    String sigAlg = context.getInitParameter(SSO_COOKIE_TOKEN_SIG_ALG);
    if (sigAlg != null) {
      signatureAlgorithm = sigAlg;
    }

    final String expectedParams = context.getInitParameter(SSO_EXPECTED_PARAM);
    if (expectedParams != null) {
      ssoExpectedparams = Arrays.asList(expectedParams.split(","));
    }
  }

  private void handleCookieSetup() {
    cookieName = context.getInitParameter(SSO_COOKIE_NAME);
    if (cookieName == null) {
      cookieName = DEFAULT_SSO_COOKIE_NAME;
    }

    final String secure = context.getInitParameter(SSO_COOKIE_SECURE_ONLY_INIT_PARAM);
    if (StringUtils.isBlank(secure)) {
      final GatewayConfig config = (GatewayConfig) request.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
      secureOnly = config.isSSLEnabled();
    } else {
      secureOnly = Boolean.parseBoolean(secure);
    }
    if (!secureOnly) {
      LOGGER.cookieSecureOnly(secureOnly);
    }

    String age = context.getInitParameter(SSO_COOKIE_MAX_AGE_INIT_PARAM);
    if (age != null) {
      try {
        LOGGER.setMaxAge(age);
        maxAge = Integer.parseInt(age);
      }
      catch (NumberFormatException nfe) {
        LOGGER.invalidMaxAgeEncountered(age);
      }
    }

    domainSuffix = context.getInitParameter(SSO_COOKIE_DOMAIN_SUFFIX_PARAM);

    whitelist = context.getInitParameter(SSO_COOKIE_TOKEN_WHITELIST_PARAM);
    if (whitelist == null) {
      whitelist = WhitelistUtils.getDispatchWhitelist(request);
    }

    String audiences = context.getInitParameter(SSO_COOKIE_TOKEN_AUDIENCES_PARAM);
    if (audiences != null) {
      String[] auds = audiences.split(",");
      for (String aud : auds) {
        targetAudiences.add(aud.trim());
      }
    }

    String ttl = context.getInitParameter(SSO_COOKIE_TOKEN_TTL_PARAM);
    if (ttl != null) {
      try {
        tokenTTL = Long.parseLong(ttl);
        if (tokenTTL < -1 || (tokenTTL + System.currentTimeMillis() < 0)) {
          LOGGER.invalidTokenTTLEncountered(ttl);
          tokenTTL = TOKEN_TTL_DEFAULT;
        }
      }
      catch (NumberFormatException nfe) {
        LOGGER.invalidTokenTTLEncountered(ttl);
      }
    }
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
    GatewayServices services =
                (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    boolean removeOriginalUrlCookie = true;
    List<Cookie> originalUrlCookies = CookieUtils.getCookiesForName(request, ORIGINAL_URL_COOKIE_NAME);
    String original;
    if (originalUrlCookies.isEmpty()) {
      // in the case where there are no SAML redirects done before here
      // we need to get it from the request parameters
      removeOriginalUrlCookie = false;
      original = getOriginalUrlFromQueryParams();
      if (original.isEmpty()) {
        LOGGER.originalURLNotFound();
        throw new WebApplicationException("Original URL not found in the request.", Response.Status.BAD_REQUEST);
      }

      boolean validRedirect = true;

      // If there is a whitelist defined, then the original URL must be validated against it.
      // If there is no whitelist, then everything is valid.
      if (whitelist != null) {
        String decodedOriginal = null;
        try {
          decodedOriginal = URLDecoder.decode(original, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
          //
        }

        validRedirect = RegExUtils.checkWhitelist(whitelist, (decodedOriginal != null ? decodedOriginal : original));
      }

      if (!validRedirect) {
        LOGGER.whiteListMatchFail(Log4jAuditor.maskTokenFromURL(original), whitelist);
        throw new WebApplicationException("Original URL not valid according to the configured whitelist.",
                                          Response.Status.BAD_REQUEST);
      }
    } else {
      // There should only be one original url cookie for the given path
      original = originalUrlCookies.get(0).getValue();
    }

    AliasService as = services.getService(ServiceType.ALIAS_SERVICE);
    JWTokenAuthority ts = services.getService(ServiceType.TOKEN_SERVICE);
    Principal p = request.getUserPrincipal();

    try {
      String signingKeystoreName = context.getInitParameter(SSO_SIGNINGKEY_KEYSTORE_NAME);
      String signingKeystoreAlias = context.getInitParameter(SSO_SIGNINGKEY_KEYSTORE_ALIAS);
      String signingKeystorePassphraseAlias = context.getInitParameter(SSO_SIGNINGKEY_KEYSTORE_PASSPHRASE_ALIAS);
      char[] signingKeystorePassphrase = null;
      if(signingKeystorePassphraseAlias != null) {
        signingKeystorePassphrase = as.getPasswordFromAliasForCluster(clusterName, signingKeystorePassphraseAlias);
      }

      JWT token = ts.issueToken(p, targetAudiences, signatureAlgorithm, getExpiry(),
          signingKeystoreName,  signingKeystoreAlias, signingKeystorePassphrase);

      // Coverity CID 1327959
      if( token != null ) {
        addJWTHadoopCookie( original, token );
      }

      if (removeOriginalUrlCookie) {
        removeOriginalUrlCookie(response);
      }

      LOGGER.aboutToRedirectToOriginal(Log4jAuditor.maskTokenFromURL(original));
      response.setStatus(statusCode);
      response.setHeader("Location", original);
      try {
        response.getOutputStream().close();
      } catch (IOException e) {
        LOGGER.unableToCloseOutputStream(e.getMessage(), Arrays.toString(e.getStackTrace()));
      }
    } catch (TokenServiceException| AliasServiceException e) {
      LOGGER.unableToIssueToken(e);
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
    StringBuilder buf = new StringBuilder(original);

    boolean first = true;

    // Add any other query params.
    // Probably not ideal but will not break existing integrations by requiring
    // some encoding.
    Map<String, String[]> params = request.getParameterMap();
    for (Entry<String, String[]> entry : params.entrySet()) {
      if (!ORIGINAL_URL_REQUEST_PARAM.equals(entry.getKey())
          && !original.contains(entry.getKey() + "=")
          && !ssoExpectedparams.contains(entry.getKey())) {

        if(first) {
          buf.append('?');
          first = false;
        }

        buf.append('&').append(entry.getKey());
        String[] values = entry.getValue();
        if (values.length > 0 && values[0] != null) {
          buf.append('=');
        }
        for (int i = 0; i < values.length; i++) {
          if (values[0] != null) {
            buf.append(values[i]);
            if (i < values.length-1) {
              buf.append('&').append(entry.getKey()).append('=');
            }
          }
        }
      }
    }

    return buf.toString();
  }

  private long getExpiry() {
    long expiry;
    if (tokenTTL == -1) {
      expiry = -1;
    }
    else {
      expiry = System.currentTimeMillis() + tokenTTL;
    }
    return expiry;
  }

  private void addJWTHadoopCookie(String original, JWT token) {
    LOGGER.addingJWTCookie(token.toString());
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
      LOGGER.addedJWTCookie();
    }
    catch(Exception e) {
      LOGGER.unableAddCookieToResponse(e.getMessage(), Arrays.toString(e.getStackTrace()));
      throw new WebApplicationException("Unable to add JWT cookie to response.");
    }
  }

  private void removeOriginalUrlCookie(HttpServletResponse response) {
    Cookie c = new Cookie(ORIGINAL_URL_COOKIE_NAME, null);
    c.setMaxAge(0);
    c.setPath(RESOURCE_PATH);
    response.addCookie(c);
  }
}
