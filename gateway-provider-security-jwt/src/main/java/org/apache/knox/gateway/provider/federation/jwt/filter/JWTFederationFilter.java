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
package org.apache.knox.gateway.provider.federation.jwt.filter;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.provider.federation.jwt.JWTMessages;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.TrustedOidcIssuerService;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.util.AuthFilterUtils;
import org.apache.knox.gateway.util.CertificateUtils;
import org.apache.knox.gateway.util.CookieUtils;
import org.apache.knox.gateway.util.ServletRequestUtils;
import org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants;
import org.apache.knox.gateway.util.knoxidf.KnoxIDFUtils;

import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.knox.gateway.security.CommonTokenConstants.AUTH_CODE;
import static org.apache.knox.gateway.security.CommonTokenConstants.CLIENT_CREDENTIALS;
import static org.apache.knox.gateway.security.CommonTokenConstants.CLIENT_ID;
import static org.apache.knox.gateway.security.CommonTokenConstants.CLIENT_SECRET;
import static org.apache.knox.gateway.security.CommonTokenConstants.GRANT_TYPE;
import static org.apache.knox.gateway.util.AuthFilterUtils.DEFAULT_AUTH_UNAUTHENTICATED_PATHS_PARAM;

public class JWTFederationFilter extends AbstractJWTFilter {
  private static final JWTMessages LOGGER = MessagesFactory.get( JWTMessages.class );
  /* A semicolon separated list of paths that need to bypass authentication */
  public static final String JWT_UNAUTHENTICATED_PATHS_PARAM = "jwt.unauthenticated.path.list";
  public static final String INVALID_CLIENT_SECRET = "Error while parsing the received client secret";
  public static final String MISMATCHING_CLIENT_ID_AND_CLIENT_SECRET = "Client credentials flow with mismatching client_id and client_secret";
  public static final String REFRESH_TOKEN = "refresh_token";
  public static final String REFRESH_TOKEN_PARAM = "refresh_token";
  public static final String CLIENT_ASSERTION_JWT_BEARER = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
  public static final String CLIENT_ASSERTION_TYPE = "client_assertion_type";
  public static final String CLIENT_ASSERTION = "client_assertion";
  // RFC 8693 constants
  public static final String TOKEN_EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange";
  public static final String SUBJECT_TOKEN = "subject_token";
  public static final String ACTOR_TOKEN = "actor_token";
  public static final String SUBJECT_TOKEN_TYPE = "subject_token_type";
  public static final String ACTOR_TOKEN_TYPE = "actor_token_type";

  // Set by doFilter only when it dispatches a genuine RFC 8693 token-exchange request (identified by
  // getWireToken from the body-only grant_type). resolveRegisteredIssuerJwks trusts a runtime-registered
  // external issuer's JWKS only when this attribute is present, binding that decision to the actual
  // dispatched code path rather than to request.getParameter(GRANT_TYPE) -- which the Servlet API also
  // populates from the URL query string, letting a plain Bearer request spoof it with ?grant_type=...
  static final String TOKEN_EXCHANGE_REQUEST_ATTR = "knox.jwt.token.exchange.request";
  // RFC 8693 section 3 token type identifiers. Only JWT-family types are supported for exchange;
  // Knox issues JWT access tokens, so the access_token URN is accepted as an alias for jwt.
  public static final String TOKEN_TYPE_JWT = "urn:ietf:params:oauth:token-type:jwt";
  public static final String TOKEN_TYPE_ACCESS_TOKEN = "urn:ietf:params:oauth:token-type:access_token";

  // Topology provider param. OOTB the JWKS URI resolved via dynamic OIDC discovery for a
  // runtime-registered external issuer MUST be HTTPS: fetching a token issuer's signing keys over
  // cleartext would let an on-path attacker substitute their own keys and forge subject tokens.
  // Set this to "true" on the provider to permit an http:// jwks_uri (e.g. an internal test OP).
  public static final String TOKEN_EXCHANGE_DYNAMIC_JWKS_ALLOW_HTTP = "knox.token.exchange.dynamic.jwks.allow.http";

  public enum TokenType {
    JWT, Passcode, TokenExchange, AuthCode;
  }

  public static final String KNOX_TOKEN_AUDIENCES = "knox.token.audiences";
  public static final String TOKEN_VERIFICATION_PEM = "knox.token.verification.pem";
  public static final String KNOX_TOKEN_QUERY_PARAM_NAME = "knox.token.query.param.name";
  public static final String TOKEN_PRINCIPAL_CLAIM = "knox.token.principal.claim";
  public static final String JWKS_URL = "knox.token.jwks.url";
  public static final String JWKS_URLS = "knox.token.jwks.urls";
  public static final String ALLOWED_JWS_TYPES = "knox.token.allowed.jws.types";
  public static final String BEARER   = "Bearer ";
  public static final String BASIC    = "Basic";
  public static final String TOKEN    = "Token";
  public static final String PASSCODE = "Passcode";

  //cookie verification support
  public static final String KNOX_TOKEN_USE_COOKIE = "knox.token.use.cookie";
  public static final String KNOX_TOKEN_COOKIE_NAME = "knox.token.cookie.name";
  private boolean useCookie; //defaults to false
  private String cookieName;

  private String paramName;
  // OOTB false: a non-HTTPS dynamic-discovery JWKS URI is rejected. Only an explicit
  // TOKEN_EXCHANGE_DYNAMIC_JWKS_ALLOW_HTTP="true" flips this, so a typo fails safe (secure).
  private boolean allowInsecureDynamicJwks;
  private Set<String> unAuthenticatedPaths = new HashSet<>(20);

  // Handles RFC 8693 token exchange requests (see doFilter).
  private TokenExchangeHandler tokenExchangeHandler = new TokenExchangeHandler(this);

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    super.init(filterConfig);

    // expected audiences or null
    String expectedAudiences = filterConfig.getInitParameter(KNOX_TOKEN_AUDIENCES);
    if (expectedAudiences != null) {
      audiences = parseExpectedAudiences(expectedAudiences);
    }

    // query param name for finding the provided knoxtoken
    String queryParamName = filterConfig.getInitParameter(KNOX_TOKEN_QUERY_PARAM_NAME);
    if (queryParamName != null) {
      paramName = queryParamName;
    }

    //  JWKSUrl(s) and allowed JWS types
    jwksUrls = parseJwksUrlsFromConfig(filterConfig.getInitParameter(JWKS_URL));
    jwksUrls.addAll(parseJwksUrlsFromConfig(filterConfig.getInitParameter(JWKS_URLS)));
    setJwsTypeVerifier(filterConfig, ALLOWED_JWS_TYPES);

    //cookie auth support
    final String useCookieParam = filterConfig.getInitParameter(KNOX_TOKEN_USE_COOKIE);
    useCookie = StringUtils.isBlank(useCookieParam) ? false : Boolean.parseBoolean(useCookieParam);

    final String cookieNameParam = filterConfig.getInitParameter(KNOX_TOKEN_COOKIE_NAME);
    cookieName = StringUtils.isBlank(cookieNameParam) ? SSOCookieFederationFilter.DEFAULT_SSO_COOKIE_NAME : cookieNameParam;

    // expected claim
    String oidcPrincipalclaim = filterConfig.getInitParameter(TOKEN_PRINCIPAL_CLAIM);
    if (oidcPrincipalclaim != null) {
      expectedPrincipalClaim = oidcPrincipalclaim;
    }

    // token verification pem
    String verificationPEM = filterConfig.getInitParameter(TOKEN_VERIFICATION_PEM);
    // setup the public key of the token issuer for verification
    if (verificationPEM != null) {
      publicKey = CertificateUtils.parseRSAPublicKey(verificationPEM);
    }

    // Topology toggle for permitting a non-HTTPS dynamic-discovery JWKS URI. Parsed with
    // Boolean.parseBoolean so anything other than an explicit "true" (including a typo) keeps
    // HTTPS enforcement on -- the fail-safe direction for a security control.
    allowInsecureDynamicJwks = Boolean.parseBoolean(
        filterConfig.getInitParameter(TOKEN_EXCHANGE_DYNAMIC_JWKS_ALLOW_HTTP));

    final String unAuthPathString = filterConfig
        .getInitParameter(JWT_UNAUTHENTICATED_PATHS_PARAM);
    /* prepare a list of allowed unauthenticated paths */
    AuthFilterUtils.addUnauthPaths(unAuthenticatedPaths, unAuthPathString, DEFAULT_AUTH_UNAUTHENTICATED_PATHS_PARAM);

    configureExpectedParameters(filterConfig);
  }

  @Override
  public void destroy() {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    /* check for unauthenticated paths to bypass */
    if(AuthFilterUtils
        .doesRequestContainUnauthPath(unAuthenticatedPaths, request)) {
      continueWithAnonymousSubject(request, response, chain);
      return;
    }

    if (useCookie) {
      try {
        if (authenticateWithCookies((HttpServletRequest) request, (HttpServletResponse) response, chain)) {
          // if there was a valid cookie authentication was handled, there is no point in
          // going forward to check the JWT path in the header
          return;
        }
      } catch (NoValidCookiesException e) {
        log.missingValidCookie();
        handleValidationError((HttpServletRequest) request, (HttpServletResponse) response, HttpServletResponse.SC_UNAUTHORIZED,
            "There is no valid cookie found");
        return;
      }
    }

    Pair<TokenType, String> wireToken = null;
    try {
      wireToken = getWireToken(request);
    } catch (SecurityException e) {
      handleValidationError((HttpServletRequest) request, (HttpServletResponse) response, HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
      throw e;
    }

    // RFC 8693 token exchange: getWireToken flags this via TokenType.TokenExchange when the
    // grant_type is in the request body. The subject_token/actor_token are read from the unwrapped
    // request by the handler. Reading the body only happens on this (header-less) grant-flow path,
    // so a proxied backend's body is never consumed by the header-authenticated path.
    if (wireToken != null && TokenType.TokenExchange.equals(wireToken.getLeft())) {
      // Bind the "this is token exchange" decision to the dispatched path so that only the
      // subject_token/actor_token validated inside the handler can unlock registered-issuer JWKS.
      request.setAttribute(TOKEN_EXCHANGE_REQUEST_ATTR, Boolean.TRUE);
      tokenExchangeHandler.handle((HttpServletRequest) request, (HttpServletResponse) response, chain);
      return;
    }

    // authorization_code grant: the KnoxIDF token endpoint (TokenResource) authenticates the client
    // itself -- a PKCE code_verifier for public clients, or a client_secret for confidential clients
    // -- and binds the code to its client_id and redirect_uri. getWireToken flags this via
    // TokenType.AuthCode when the grant_type is in the request body and no Bearer/Basic credentials
    // were presented. Forward to the service without a gateway-established token so that public PKCE
    // clients (no secret) are not rejected here.
    if (wireToken != null && TokenType.AuthCode.equals(wireToken.getLeft())) {
      continueWithAuthorizationCodeGrant(request, response, chain);
      return;
    }

    if (wireToken != null && wireToken.getLeft() != null && wireToken.getRight() != null) {
      TokenType tokenType  = wireToken.getLeft();
      String    tokenValue = wireToken.getRight();

      if (TokenType.JWT.equals(tokenType)) {
        try {
          JWT token = parseAndValidateJWT((HttpServletRequest) request, (HttpServletResponse) response, chain, tokenValue);
          if (token != null) {
            Subject subject = createSubjectFromToken(token);
            addKnoxIDFAttributes(request, token);
            continueWithEstablishedSecurityContext(subject, (HttpServletRequest) request, (HttpServletResponse) response, chain);
          }
        } catch (ParseException | UnknownTokenException ex) {
          ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
      } else if (TokenType.Passcode.equals(tokenType)) {
        // Validate the token based on the server-managed metadata
        // The received token value must be a Base64 encoded value of Base64(tokenId)::Base64(rawPasscode)
        String tokenId = null;
        String passcode = null;
        try {
          final String[] base64DecodedTokenIdAndPasscode = decodeBase64(tokenValue).split("::");
          tokenId = decodeBase64(base64DecodedTokenIdAndPasscode[0]);
          passcode = decodeBase64(base64DecodedTokenIdAndPasscode[1]);
        } catch (Exception e) {
          log.failedToParsePasscodeToken(e);
          handleValidationError((HttpServletRequest) request, (HttpServletResponse) response, HttpServletResponse.SC_UNAUTHORIZED,
              "Error while parsing the received passcode token");
        }
        if (validateToken((HttpServletRequest) request, (HttpServletResponse) response, chain, tokenId, passcode)) {
          try {
            final Subject subject = createSubjectFromTokenIdentifier(tokenId);
            request.setAttribute(KnoxIDFConstants.TOKEN_ID_ATTRIBUTE, tokenId);
            continueWithEstablishedSecurityContext(subject, (HttpServletRequest) request, (HttpServletResponse) response, chain);
          } catch (UnknownTokenException e) {
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
          }
        }
      }
    } else {
      // no token provided in header
      log.missingTokenFromHeader(wireToken);
      ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
  }

  private static void addKnoxIDFAttributes(ServletRequest request, JWT token) {
    request.setAttribute(KnoxIDFConstants.TOKEN_ID_ATTRIBUTE, TokenUtils.getTokenId(token));
    final String scope = token.getClaim(KnoxIDFConstants.SCOPE);
    if (scope != null) {
      request.setAttribute(KnoxIDFConstants.SCOPE_ATTRIBUTE, scope);
    }
    final String issuer = token.getIssuer();
    if (issuer != null) {
      request.setAttribute(KnoxIDFConstants.TOKEN_ISS_ATTRIBUTE, issuer);
    }
  }

  private void validateClientID(HttpServletRequest request, String tokenValue) {
    final String clientID = request.getParameter(CLIENT_ID);
    validateClientID(clientID, tokenValue);
  }

  private void validateClientID(String clientID, String tokenValue) {
    String tokenId;
    try {
      final String[] base64DecodedTokenIdAndPasscode = decodeBase64(tokenValue).split("::");
      tokenId = decodeBase64(base64DecodedTokenIdAndPasscode[0]);
    } catch (Exception e) {
      throw new SecurityException(INVALID_CLIENT_SECRET, e);
    }
    // if there is no client_id then this is not a client credentials flow
    if (!tokenId.equals(clientID)) {
     throw new SecurityException(MISMATCHING_CLIENT_ID_AND_CLIENT_SECRET);
    }
  }

  private String decodeBase64(String toBeDecoded) {
    return new String(Base64.getDecoder().decode(toBeDecoded.getBytes(UTF_8)), UTF_8);
  }

  public Pair<TokenType, String> getWireToken(final ServletRequest request) throws IOException {
      Pair<TokenType, String> parsed = null;
      String token = null;
      final String header = ((HttpServletRequest)request).getHeader("Authorization");
      if (header != null) {
          if (header.startsWith(BEARER)) {
              // what follows the bearer designator should be the JWT token being used
              // to request or as an access token
              token = header.substring(BEARER.length());

              // if this appears to be a JWT token then attempt to use it as such
              // otherwise assume it is a passcode token
              if (isJWT(token)) {
                parsed = Pair.of(TokenType.JWT, token);
              } else {
                parsed = Pair.of(TokenType.Passcode, token);
              }
          } else if (header.toLowerCase(Locale.ROOT).startsWith(BASIC.toLowerCase(Locale.ROOT))) {
              // what follows the Basic designator should be the JWT token or the unique token ID being used
              // to request or as an access token
              parsed = parseFromHTTPBasicCredentials(header, request);
          }
      }

      if (parsed == null) {
        parsed = parseFromGrantTypeFlow(request);
      }

      if (parsed == null) {
        token = request.getParameter(this.paramName);
        if (token != null) {
          parsed = Pair.of(TokenType.JWT, token);
        }
      }

      return parsed;
    }

    private Pair<TokenType, String> parseFromGrantTypeFlow(ServletRequest request) throws IOException {
      /*
        POST /{tenant}/oauth2/v2.0/token HTTP/1.1
        Host: login.microsoftonline.com:443
        Content-Type: application/x-www-form-urlencoded

        client_id=535fb089-9ff3-47b6-9bfb-4f1264799865
        &scope=https%3A%2F%2Fgraph.microsoft.com%2F.default
        &client_secret=sampleCredentials
        &grant_type=client_credentials

        or

        POST /token.oauth2 HTTP/1.1
        Content-Type: application/x-www-form-urlencoded

        grant_type=client_credentials&
        client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer&
        client_assertion=eyJhbGciOiJSUzI1NiJ9... <- K8s SA JWT
        scope=openid profile email
       */

      final HttpServletRequest httpRequest = (HttpServletRequest) request;
      final boolean clientSecretPresentAsQueryString = httpRequest.getQueryString() != null && httpRequest.getQueryString().contains("client_secret=");
      if (clientSecretPresentAsQueryString) {
        throw new SecurityException("client_secret must not be sent as a query parameter");
      }
      return getTokenFromRequestBody(request);
    }

    private Pair<TokenType, String> getTokenFromRequestBody(ServletRequest request) {
        // unwrap the servlet request so that we can get to the request body params since we are not passing this request
        // on to other services to handle like we do when proxying. The request wrapper is protecting the body from being
        // consumed before it gets to the proxied service that should handle it. We don't need that protection here.
        HttpServletRequest unwrappedRequest = ServletRequestUtils.unwrapHttpServletRequest(request);
        final String grantType = unwrappedRequest.getParameter(GRANT_TYPE);
        final String clientAssertionType = unwrappedRequest.getParameter(CLIENT_ASSERTION_TYPE);
        if (AUTH_CODE.equals(grantType)) {
          // no client_secret parsed here; the KnoxIDF token endpoint authenticates the client
          // (see the TokenType.AuthCode handling in doFilter)
          return Pair.of(TokenType.AuthCode, null);
        } else if (CLIENT_CREDENTIALS.equals(grantType)) {
          if (CLIENT_ASSERTION_JWT_BEARER.equals(clientAssertionType)) {
            // short lived client assertion token expected
            return getClientTokenFromParams(unwrappedRequest, CLIENT_ASSERTION);
          }
          // client credentials flow: client_id and client_secret are expected
          // the client_id will be in the token as the token_id
          final String clientSecret = unwrappedRequest.getParameter(CLIENT_SECRET);
          validateClientID((HttpServletRequest) unwrappedRequest, clientSecret);
          return Pair.of(TokenType.Passcode, clientSecret);
        } else if (REFRESH_TOKEN.equals(grantType)) {
          // refresh_token flow: the refresh_token parameter contains the actual token
          return getClientTokenFromParams(unwrappedRequest, REFRESH_TOKEN_PARAM);
        } else if (TOKEN_EXCHANGE.equals(grantType)) {
          // RFC 8693 token exchange: signal it via the token type. doFilter routes this to
          // TokenExchangeHandler, which reads subject_token/actor_token from the unwrapped request.
          return Pair.of(TokenType.TokenExchange, null);
        }
      return null;
    }

    private Pair<TokenType, String> getClientTokenFromParams(final ServletRequest request, final String requestParamName) {
      final String refreshOrSubjectToken = request.getParameter(requestParamName);
      if (refreshOrSubjectToken != null) {
        return isJWT(refreshOrSubjectToken) ? Pair.of(TokenType.JWT, refreshOrSubjectToken) : Pair.of(TokenType.Passcode,
                refreshOrSubjectToken);
      }
      return null;
    }

    private Pair<TokenType, String> parseFromHTTPBasicCredentials(final String header, final ServletRequest request) {
      Pair<TokenType, String> parsed = null;
      final String base64Credentials = header.substring(BASIC.length()).trim();
      final byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
      final String credentials = new String(credDecoded, UTF_8);
      final String[] values = credentials.split(":", 2);
      String username = values[0];
      String passcode = values[1].isEmpty() ? null : values[1];
      if (TOKEN.equalsIgnoreCase(username) || PASSCODE.equalsIgnoreCase(username)) {
          parsed = Pair.of(TOKEN.equalsIgnoreCase(username) ? TokenType.JWT : TokenType.Passcode, passcode);
      } else if (request != null) {
          HttpServletRequest unwrappedRequest = ServletRequestUtils.unwrapHttpServletRequest(request);
          if (CLIENT_CREDENTIALS.equals(unwrappedRequest.getParameter(GRANT_TYPE))) {
            // Allow client_credentials flow where client_id/client_secret are provided via HTTP Basic
            if (passcode != null) {
              validateClientID(username, passcode);
              parsed = Pair.of(TokenType.Passcode, passcode);
            }
            }
      }

      return parsed;
  }

  /*
   * Attempts to authenticate using session cookies.
   */
  private boolean authenticateWithCookies(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws NoValidCookiesException, ServletException, IOException {
    final List<Cookie> relevantCookies = CookieUtils.getCookiesForName(request, cookieName);
    for (Cookie ssoCookie : relevantCookies) {
      try {
        final JWT token = parseAndValidateJWT(request, response, chain, ssoCookie.getValue());
        if (token != null) {
          final Subject subject = createSubjectFromToken(token);
          continueWithEstablishedSecurityContext(subject, request, response, chain);
          // we found a valid cookie we don't need to keep checking anymore
          return true;
        }
      } catch (ParseException | UnknownTokenException ignore) {
        // Ignore the error since cookie was invalid
        // Fall through to keep checking if there are more cookies
      }
    }

    if (!relevantCookies.isEmpty()) {
      // No valid cookies found but cookie was present so reject this request and do
      // no further processing
      throw new NoValidCookiesException();
    }

    return false;
  }

  /**
   * Parse and validate a JWT token.
   *
   * @param request the HTTP request
   * @param response the HTTP response
   * @param chain the filter chain
   * @param tokenValue the JWT string to parse
   * @return the parsed and validated JWT, or null if validation failed
   * @throws ParseException if the JWT cannot be parsed
   * @throws IOException if an I/O error occurs during validation
   * @throws ServletException if a servlet error occurs during validation
   */
  // package-private: also invoked by TokenExchangeHandler
  JWT parseAndValidateJWT(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain, String tokenValue)
      throws ParseException, IOException, ServletException {
    JWT token = new JWTToken(tokenValue);
    if (validateToken(request, response, chain, token)) {
      return token;
    }
    // Validation failed - error response already sent by validateToken
    return null;
  }

  @Override
  protected Set<URI> resolveRegisteredIssuerJwks(String issuer, HttpServletRequest request) {
    // Only a genuine token-exchange dispatch (see doFilter) may trust a runtime-registered external
    // issuer's JWKS. Reading the request attribute -- not getParameter(GRANT_TYPE) -- prevents a
    // ?grant_type=<token-exchange> query param on a plain Bearer request from unlocking this path.
    if (!Boolean.TRUE.equals(request.getAttribute(TOKEN_EXCHANGE_REQUEST_ATTR))) {
      return Set.of();
    }
    final GatewayServices gws = (GatewayServices)
        request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    if (gws != null) {
      final TrustedOidcIssuerService issuerSvc = gws.getService(ServiceType.TRUSTED_OIDC_ISSUER_SERVICE);
      // isDynamicJwks() is the combined guard: true only if the issuer is both registered as
      // trusted AND configured for dynamic JWKS discovery. If the issuer is not registered, or
      // registered without dynamic JWKS, it is not actionable through this path.
      if (issuerSvc != null && issuerSvc.isDynamicJwks(issuer)) {
        // resolveJwksUri() performs OIDC discovery
        final Optional<String> jwksUri = issuerSvc.resolveJwksUri(issuer);
        if (jwksUri.isPresent()) {
          try {
            final URI uri = new URI(jwksUri.get());
            // OOTB the discovered JWKS URI must be HTTPS (see TOKEN_EXCHANGE_DYNAMIC_JWKS_ALLOW_HTTP):
            // signing keys fetched over cleartext could be swapped by an on-path attacker to forge
            // subject tokens. Reject anything non-HTTPS unless the operator opted in.
            if (!allowInsecureDynamicJwks && !"https".equalsIgnoreCase(uri.getScheme())) {
              LOGGER.rejectedInsecureDynamicJwksUri(jwksUri.get(), issuer);
              return Set.of();
            }
            return Set.of(uri);
          } catch (URISyntaxException e) {
            LOGGER.unableToVerifyToken(e);
          }
        }
      }
    }
    return Set.of();
  }

  @Override
  protected void handleValidationError(HttpServletRequest request, HttpServletResponse response, int status,
                                       String error) throws IOException {
    if (Boolean.TRUE.equals(request.getAttribute(TOKEN_EXCHANGE_REQUEST_ATTR))) {
      handleValidationError(request, response, status, deriveOAuthError(status), error);
      return;
    }
    if (error != null) {
      response.sendError(status, error);
    }
    else {
      response.sendError(status);
    }
  }

  /**
   * Emit an RFC 8693 / RFC 6749 §5.2 JSON error response ({@code {"error": ..., "error_description":
   * ...}}) for a token-exchange request. Called directly by {@link TokenExchangeHandler} when it has
   * an explicit OAuth error code (e.g. {@code invalid_request}, {@code unsupported_token_type}), and
   * indirectly by the four-argument {@link #handleValidationError} for shared-path errors.
   */
  void handleValidationError(HttpServletRequest request, HttpServletResponse response, int status,
                             String error, String description) throws IOException {
    KnoxIDFUtils.writeErrorResponse(response, status, error, description);
  }

  private static String deriveOAuthError(int status) {
    return status == HttpServletResponse.SC_BAD_REQUEST ? "invalid_request" : "invalid_grant";
  }

  /**
   * A function that let's configured unauthenticated path requests to
   * pass through without requiring authentication.
   * An anonymous subject is created and the request is audited.
   *
   * Fail gracefully by logging error message.
   * @param request
   * @param response
   * @param chain
   */
  private void continueWithAnonymousSubject(final ServletRequest request,
      final ServletResponse response, final FilterChain chain)
      throws ServletException, IOException {
    try {
      /* This path is configured as an unauthenticated path let the request through */
      final Subject sub = new Subject();
      sub.getPrincipals().add(new PrimaryPrincipal("anonymous"));
      LOGGER.unauthenticatedPathBypass(((HttpServletRequest) request).getRequestURI(), unAuthenticatedPaths.toString());
      continueWithEstablishedSecurityContext(sub, (HttpServletRequest) request,
          (HttpServletResponse) response, chain);

    } catch (final Exception e) {
      LOGGER.unauthenticatedPathError(
          ((HttpServletRequest) request).getRequestURI(), e.toString());
      throw e;
    }
  }

  /**
   * Forwards an {@code authorization_code} token request to the KnoxIDF token endpoint without a
   * gateway-established token. The token endpoint ({@code TokenResource.validateAuthCode})
   * independently authenticates the client -- a PKCE {@code code_verifier} for public clients, or a
   * {@code client_secret} for confidential clients -- and binds the code to its {@code client_id}
   * and {@code redirect_uri}, so this filter only needs to let the request through with an anonymous
   * subject. The principal of the issued token is derived from the authorization code's stored
   * metadata, not from this subject.
   */
  private void continueWithAuthorizationCodeGrant(final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws ServletException, IOException {
    final Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal("anonymous"));
    continueWithEstablishedSecurityContext(subject, (HttpServletRequest) request, (HttpServletResponse) response, chain);
  }

  /**
   * An exception indicating that cookies are present, but none of them contain a
   * valid JWT.
   */
  private static class NoValidCookiesException extends Exception {
    NoValidCookiesException() {
      super("None of the presented cookies are valid.");
    }
  }

  // Test seam: allows a mock/recording handler to be injected.
  void setTokenExchangeHandler(TokenExchangeHandler tokenExchangeHandler) {
    this.tokenExchangeHandler = tokenExchangeHandler;
  }
}
