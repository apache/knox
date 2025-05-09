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

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.apache.knox.gateway.services.GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
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
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.audit.log4j.audit.Log4jAuditor;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.token.JWTokenAttributes;
import org.apache.knox.gateway.services.security.token.JWTokenAttributesBuilder;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.session.control.ConcurrentSessionVerifier;
import org.apache.knox.gateway.util.CookieUtils;
import org.apache.knox.gateway.util.RegExUtils;
import org.apache.knox.gateway.util.SetCookieHeader;
import org.apache.knox.gateway.util.Tokens;
import org.apache.knox.gateway.util.Urls;
import org.apache.knox.gateway.util.WhitelistUtils;

@Path( WebSSOResource.RESOURCE_PATH )
public class WebSSOResource {
  private static final KnoxSSOMessages LOGGER = MessagesFactory.get( KnoxSSOMessages.class );

  private static final String SSO_COOKIE_NAME = "knoxsso.cookie.name";
  private static final String SSO_COOKIE_SECURE_ONLY_INIT_PARAM = "knoxsso.cookie.secure.only";
  private static final String SSO_COOKIE_MAX_AGE_INIT_PARAM = "knoxsso.cookie.max.age";
  private static final String SSO_COOKIE_DOMAIN_SUFFIX_PARAM = "knoxsso.cookie.domain.suffix";
  private static final String SSO_COOKIE_SAMESITE_PARAM = "knoxsso.cookie.samesite";
  private static final String SSO_COOKIE_TOKEN_TTL_PARAM = "knoxsso.token.ttl";
  private static final String SSO_COOKIE_TOKEN_AUDIENCES_PARAM = "knoxsso.token.audiences";
  private static final String SSO_COOKIE_TOKEN_SIG_ALG = "knoxsso.token.sigalg";
  private static final String SSO_COOKIE_TOKEN_WHITELIST_PARAM = "knoxsso.redirect.whitelist.regex";

  private static final String SSO_SIGNINGKEY_KEYSTORE_NAME = "knoxsso.signingkey.keystore.name";
  private static final String SSO_SIGNINGKEY_KEYSTORE_ALIAS = "knoxsso.signingkey.keystore.alias";
  private static final String SSO_SIGNINGKEY_KEYSTORE_PASSPHRASE_ALIAS = "knoxsso.signingkey.keystore.passphrase.alias";
  private static final String SSO_TOKEN_ISSUER = "knoxsso.token.issuer";

  /* parameters expected by knoxsso */
  private static final String SSO_EXPECTED_PARAM = "knoxsso.expected.params";

  private static final String SSO_ENABLE_SESSION_PARAM = "knoxsso.enable.session";
  private static final String ORIGINAL_URL_REQUEST_PARAM = "originalUrl";
  private static final String ORIGINAL_URL_COOKIE_NAME = "original-url";
  private static final String DEFAULT_SSO_COOKIE_NAME = "hadoop-jwt";
  private static final String SSO_COOKIE_SAMESITE_DEFAULT = "Strict";
  public static final long TOKEN_TTL_DEFAULT = 15000 * 60;
  static final String RESOURCE_PATH = "/api/v1/websso";
  private String cookieName;
  private boolean secureOnly = true;
  private int maxAge = -1;
  private long tokenTTL = TOKEN_TTL_DEFAULT;
  private String whitelist;
  private String domainSuffix;
  private List<String> targetAudiences = new ArrayList<>();
  private boolean enableSession;
  private String signatureAlgorithm;
  private List<String> ssoExpectedparams = new ArrayList<>();
  private String clusterName;
  private String tokenIssuer;
  private TokenStateService tokenStateService;

  private String sameSiteValue;

  @Context
  HttpServletRequest request;

  @Context
  HttpServletResponse response;

  @Context
  ServletContext context;

  @PostConstruct
  public void init() throws AliasServiceException {
    clusterName = String.valueOf(context.getAttribute(GATEWAY_CLUSTER_ATTRIBUTE));

    handleCookieSetup();

    String enableSessionStr = context.getInitParameter(SSO_ENABLE_SESSION_PARAM);
    this.enableSession = Boolean.parseBoolean(enableSessionStr);

    this.tokenIssuer = StringUtils.isBlank(context.getInitParameter(SSO_TOKEN_ISSUER))
            ? JWTokenAttributes.DEFAULT_ISSUER
            : context.getInitParameter(SSO_TOKEN_ISSUER);

    setSignatureAlogrithm();

    final String expectedParams = context.getInitParameter(SSO_EXPECTED_PARAM);
    if (expectedParams != null) {
      ssoExpectedparams = Arrays.asList(expectedParams.split(","));
    }

    this.sameSiteValue = StringUtils.isBlank(context.getInitParameter(SSO_COOKIE_SAMESITE_PARAM))
            ? SSO_COOKIE_SAMESITE_DEFAULT
            : context.getInitParameter(SSO_COOKIE_SAMESITE_PARAM);

    final GatewayServices services = (GatewayServices) context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    if (services != null) {
      if (TokenUtils.isServerManagedTokenStateEnabled(context)) {
        tokenStateService = services.getService(ServiceType.TOKEN_STATE_SERVICE);
      }
    }
  }

  private void setSignatureAlogrithm() throws AliasServiceException {
    final String configuredSigAlg = context.getInitParameter(SSO_COOKIE_TOKEN_SIG_ALG);
    final GatewayConfig config = (GatewayConfig) request.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
    final GatewayServices services = (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    signatureAlgorithm = TokenUtils.getSignatureAlgorithm(configuredSigAlg, (AliasService) services.getService(ServiceType.ALIAS_SERVICE), config.getSigningKeystoreName());
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
    if (!enableSession) {
      // invalidate the session to avoid autologin
      // Coverity CID 1352857
      HttpSession session = request.getSession(false);
      if (session != null) {
        session.invalidate();
      }
    }
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
        try {
          validRedirect = RegExUtils.checkBaseUrlAgainstWhitelist(whitelist, original);
        } catch (MalformedURLException e) {
          throw new WebApplicationException("Malformed original URL: " + original,
                  Response.Status.BAD_REQUEST);
        }
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

    Principal p = request.getUserPrincipal();
    ConcurrentSessionVerifier verifier = services.getService(ServiceType.CONCURRENT_SESSION_VERIFIER);
    if (!verifier.verifySessionForUser(p.getName())) {
      throw new WebApplicationException("Too many sessions for user: " + request.getUserPrincipal().getName(), Response.Status.FORBIDDEN);
    }

    AliasService as = services.getService(ServiceType.ALIAS_SERVICE);
    JWTokenAuthority tokenAuthority = services.getService(ServiceType.TOKEN_SERVICE);

    try {
      String signingKeystoreName = context.getInitParameter(SSO_SIGNINGKEY_KEYSTORE_NAME);
      String signingKeystoreAlias = context.getInitParameter(SSO_SIGNINGKEY_KEYSTORE_ALIAS);
      String signingKeystorePassphraseAlias = context.getInitParameter(SSO_SIGNINGKEY_KEYSTORE_PASSPHRASE_ALIAS);
      char[] signingKeystorePassphrase = null;
      if(signingKeystorePassphraseAlias != null) {
        signingKeystorePassphrase = as.getPasswordFromAliasForCluster(clusterName, signingKeystorePassphraseAlias);
      }

      final JWTokenAttributes jwtAttributes = new JWTokenAttributesBuilder()
              .setIssuer(tokenIssuer)
              .setUserName(p.getName())
              .setAudiences(targetAudiences)
              .setAlgorithm(signatureAlgorithm)
              .setExpires(getExpiry())
              .setSigningKeystoreName(signingKeystoreName)
              .setSigningKeystoreAlias(signingKeystoreAlias)
              .setSigningKeystorePassphrase(signingKeystorePassphrase)
              .setManaged(tokenStateService != null)
              .build();
      JWT token = tokenAuthority.issueToken(jwtAttributes);

      // Coverity CID 1327959
      if (token != null) {
        if (!verifier.registerToken(p.getName(), token)) {
          throw new WebApplicationException("Too many sessions for user: " + request.getUserPrincipal().getName(), Response.Status.FORBIDDEN);
        }
        saveToken(token);
        addJWTHadoopCookie(original, token);
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



    return Response.seeOther(location).entity("{ \"redirectTo\" : " + original + " }").build();
  }

  protected String getOriginalUrlFromQueryParams() {
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

        /* Only add ? if not already present. See KNOX-2973 */
        if(first && (buf.lastIndexOf("?") == -1) ) {
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
    final String logSafeToken = Tokens.getTokenDisplayText(token.toString());
    LOGGER.addingJWTCookie(logSafeToken);
    /*
     * In order to account for google chrome changing default value
     * of SameSite from None to Lax we need to craft Set-Cookie
     * header to prevent issues with hadoop-jwt cookie.
     * NOTE: this would have been easier if javax.servlet.http.Cookie supported
     * SameSite param. Change this back to Cookie impl. after
     * SameSite header is supported by javax.servlet.http.Cookie.
     */
    try {
      SetCookieHeader setCookieHeader = new SetCookieHeader(cookieName, token.toString());
      setCookieHeader.setPath("/");
      final String domain = Urls.getDomainName(original, domainSuffix);
      if (domain != null) {
        setCookieHeader.setDomain(domain);
      }
      setCookieHeader.setHttpOnly(true);
      if (secureOnly) {
        setCookieHeader.setSecure(true);
      }
      if (maxAge != -1) {
        setCookieHeader.setMaxAge(maxAge);
      }
      setCookieHeader.setSameSite(sameSiteValue);
      response.setHeader("Set-Cookie", setCookieHeader.toString());
      LOGGER.addedJWTCookie(logSafeToken);
    } catch (Exception e) {
      LOGGER.unableAddCookieToResponse(e.getMessage(),
          Arrays.toString(e.getStackTrace()));
      throw new WebApplicationException(
          "Unable to add JWT cookie to response.");
    }
  }

  private void removeOriginalUrlCookie(HttpServletResponse response) {
    Cookie c = new Cookie(ORIGINAL_URL_COOKIE_NAME, null);
    c.setMaxAge(0);
    c.setPath(RESOURCE_PATH);
    response.addCookie(c);
  }

  // Optional token state service persistence
  private void saveToken(JWT token) {
    if (tokenStateService != null) {
      final String tokenId = TokenUtils.getTokenId(token);
      final long issueTime = System.currentTimeMillis();
      tokenStateService.addToken(tokenId, issueTime, token.getExpiresDate().getTime(), tokenStateService.getDefaultMaxLifetimeDuration());
      final TokenMetadata tokenMetadata = new TokenMetadata(token.getSubject());
      tokenMetadata.markKnoxSsoCookie();
      tokenMetadata.useTokenNow();
      tokenStateService.addMetadata(tokenId, tokenMetadata);
      LOGGER.storedToken(getTopologyName(), Tokens.getTokenDisplayText(token.toString()), Tokens.getTokenIDDisplayText(tokenId));
    }
  }

  private String getTopologyName() {
    return (String) context.getAttribute("org.apache.knox.gateway.gateway.cluster");
  }
}
