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

import java.io.IOException;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.security.auth.Subject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.provider.federation.jwt.JWTMessages;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;

import com.nimbusds.jose.JWSHeader;
import org.apache.knox.gateway.util.Tokens;

public abstract class AbstractJWTFilter implements Filter {
  /**
   * If specified, this configuration property refers to a value which the issuer of a received
   * token must match. Otherwise, the default value "KNOXSSO" is used
   */
  public static final String JWT_EXPECTED_ISSUER = "jwt.expected.issuer";
  public static final String JWT_DEFAULT_ISSUER = "KNOXSSO";

  /**
   * If specified, this configuration property refers to the signature algorithm which a received
   * token must match. Otherwise, the default value "RS256" is used
   */
  public static final String JWT_EXPECTED_SIGALG = "jwt.expected.sigalg";
  public static final String JWT_DEFAULT_SIGALG = "RS256";

  public static final String JWT_VERIFIED_CACHE_MAX = "jwt.verified.cache.max";
  public static final int    JWT_VERIFIED_CACHE_MAX_DEFAULT = 100;

  static JWTMessages log = MessagesFactory.get( JWTMessages.class );
  private static AuditService auditService = AuditServiceFactory.getAuditService();
  private static Auditor auditor = auditService.getAuditor(
      AuditConstants.DEFAULT_AUDITOR_NAME, AuditConstants.KNOX_SERVICE_NAME,
      AuditConstants.KNOX_COMPONENT_NAME );

  protected List<String> audiences;
  protected JWTokenAuthority authority;
  protected RSAPublicKey publicKey;
  protected Cache<String, Boolean> verifiedTokens;
  private String expectedIssuer;
  private String expectedSigAlg;
  protected String expectedPrincipalClaim;
  protected String expectedJWKSUrl;

  private TokenStateService tokenStateService;

  @Override
  public abstract void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException;

  /**
   *
   */
  public AbstractJWTFilter() {
    super();
  }

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    ServletContext context = filterConfig.getServletContext();
    if (context != null) {
      GatewayServices services = (GatewayServices) context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
      if (services != null) {
        authority = services.getService(ServiceType.TOKEN_SERVICE);
        if (TokenUtils.isServerManagedTokenStateEnabled(filterConfig)) {
          tokenStateService = services.getService(ServiceType.TOKEN_STATE_SERVICE);
        }
      }
    }

    // Setup the verified tokens cache
    initializeVerifiedTokensCache(filterConfig);
  }

  /**
   * Initialize the cache for token verifications records.
   *
   * @param config The filter configuration
   */
  private void initializeVerifiedTokensCache(final FilterConfig config) {
    int maxCacheSize = JWT_VERIFIED_CACHE_MAX_DEFAULT;

    String configValue = config.getInitParameter(JWT_VERIFIED_CACHE_MAX);
    if (configValue != null && !configValue.isEmpty()) {
      try {
        maxCacheSize = Integer.parseInt(configValue);
      } catch (NumberFormatException e) {
        log.invalidVerificationCacheMaxConfiguration(configValue);
      }
    }

    verifiedTokens = Caffeine.newBuilder().maximumSize(maxCacheSize).build();
  }

  protected void configureExpectedParameters(FilterConfig filterConfig) {
    expectedIssuer = filterConfig.getInitParameter(JWT_EXPECTED_ISSUER);
    if (expectedIssuer == null) {
      expectedIssuer = JWT_DEFAULT_ISSUER;
    }

    expectedSigAlg = filterConfig.getInitParameter(JWT_EXPECTED_SIGALG);
  }

  protected List<String> parseExpectedAudiences(String expectedAudiences) {
    List<String> audList = null;
    // setup the list of valid audiences for token validation
    if (expectedAudiences != null && !expectedAudiences.isEmpty()) {
      // parse into the list
      String[] audArray = expectedAudiences.split(",");
      audList = new ArrayList<>();
      for (String a : audArray) {
        audList.add(a.trim());
      }
    }
    return audList;
  }

  protected boolean tokenIsStillValid(JWT jwtToken) throws UnknownTokenException {
    Date expires;
    if (tokenStateService != null) {
      expires = new Date(tokenStateService.getTokenExpiration(jwtToken));
    } else {
      // if there is no expiration date then the lifecycle is tied entirely to
      // the cookie validity - otherwise ensure that the current time is before
      // the designated expiration time
      expires = jwtToken.getExpiresDate();
    }
    return expires == null || new Date().before(expires);
  }

  /**
   * Validate whether any of the accepted audience claims is present in the
   * issued token claims list for audience. Override this method in subclasses
   * in order to customize the audience validation behavior.
   *
   * @param jwtToken
   *          the JWT token where the allowed audiences will be found
   * @return true if an expected audience is present, otherwise false
   */
  protected boolean validateAudiences(JWT jwtToken) {
    boolean valid = false;

    String[] tokenAudienceList = jwtToken.getAudienceClaims();
    // if there were no expected audiences configured then just
    // consider any audience acceptable
    if (audiences == null) {
      valid = true;
    } else {
      // if any of the configured audiences is found then consider it
      // acceptable
      if (tokenAudienceList != null) {
        for (String aud : tokenAudienceList) {
          if (audiences.contains(aud)) {
            log.jwtAudienceValidated();
            valid = true;
            break;
          }
        }
      }
    }
    return valid;
  }

  protected void continueWithEstablishedSecurityContext(Subject subject, final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain) throws IOException, ServletException {
    Principal principal = (Principal) subject.getPrincipals(PrimaryPrincipal.class).toArray()[0];
    AuditContext context = auditService.getContext();
    if (context != null) {
      context.setUsername( principal.getName() );
      String sourceUri = (String)request.getAttribute( AbstractGatewayFilter.SOURCE_REQUEST_CONTEXT_URL_ATTRIBUTE_NAME );
      if (sourceUri != null) {
        auditor.audit( Action.AUTHENTICATION , sourceUri, ResourceType.URI, ActionOutcome.SUCCESS );
      }
    }

    try {
      Subject.doAs(
        subject,
        new PrivilegedExceptionAction<Object>() {
          @Override
          public Object run() throws Exception {
            chain.doFilter(request, response);
            return null;
          }
        }
        );
    }
    catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      }
      else if (t instanceof ServletException) {
        throw (ServletException) t;
      }
      else {
        throw new ServletException(t);
      }
    }
  }

  public Subject createSubjectFromToken(String token) throws ParseException {
    return createSubjectFromToken(new JWTToken(token));
  }

  protected Subject createSubjectFromToken(JWT token) {
    String principal = token.getSubject();
    String claimvalue = null;
    if (expectedPrincipalClaim != null) {
      claimvalue = token.getClaim(expectedPrincipalClaim);
    }

    if (claimvalue != null) {
      principal = claimvalue.toLowerCase(Locale.ROOT);
    }
    @SuppressWarnings("rawtypes")
    HashSet emptySet = new HashSet();
    Set<Principal> principals = new HashSet<>();
    Principal p = new PrimaryPrincipal(principal);
    principals.add(p);

    // The newly constructed Sets check whether this Subject has been set read-only
    // before permitting subsequent modifications. The newly created Sets also prevent
    // illegal modifications by ensuring that callers have sufficient permissions.
    //
    // To modify the Principals Set, the caller must have AuthPermission("modifyPrincipals").
    // To modify the public credential Set, the caller must have AuthPermission("modifyPublicCredentials").
    // To modify the private credential Set, the caller must have AuthPermission("modifyPrivateCredentials").
    return new Subject(true, principals, emptySet, emptySet);
  }

  protected boolean validateToken(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain, JWT token)
      throws IOException, ServletException {
    final String tokenId = TokenUtils.getTokenId(token);
    final String displayableToken = Tokens.getTokenDisplayText(token.toString());
    // confirm that issuer matches the intended target
    if (expectedIssuer.equals(token.getIssuer())) {
      // if there is no expiration data then the lifecycle is tied entirely to
      // the cookie validity - otherwise ensure that the current time is before
      // the designated expiration time
      try {
        if (tokenIsStillValid(token)) {
          // Verify the token signature
          if (verifyToken(token)) {
            boolean audValid = validateAudiences(token);
            if (audValid) {
              Date nbf = token.getNotBeforeDate();
              if (nbf == null || new Date().after(nbf)) {
                return true;
              } else {
                log.notBeforeCheckFailed();
                handleValidationError(request, response, HttpServletResponse.SC_BAD_REQUEST,
                        "Bad request: the NotBefore check failed");
              }
            } else {
              log.failedToValidateAudience(tokenId, displayableToken);
              handleValidationError(request, response, HttpServletResponse.SC_BAD_REQUEST,
                      "Bad request: missing required token audience");
            }
          } else {
            log.failedToVerifyTokenSignature(tokenId, displayableToken);
            handleValidationError(request, response, HttpServletResponse.SC_UNAUTHORIZED, null);
          }
        } else {
          log.tokenHasExpired(tokenId, displayableToken);
          handleValidationError(request, response, HttpServletResponse.SC_BAD_REQUEST,
                                "Bad request: token has expired");
        }
      } catch (UnknownTokenException e) {
        log.unableToVerifyExpiration(e);
        handleValidationError(request, response, HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
      }
    } else {
      handleValidationError(request, response, HttpServletResponse.SC_UNAUTHORIZED, null);
    }

    return false;
  }

  protected boolean verifyToken(final JWT token) {
    boolean verified;

    String tokenId = TokenUtils.getTokenId(token);

    // Check if the token has already been verified
    verified = hasTokenBeenVerified(tokenId);

    // If it has not yet been verified, then perform the verification now
    if (!verified) {
      try {
        if (publicKey != null) {
          verified = authority.verifyToken(token, publicKey);
        } else if (expectedJWKSUrl != null) {
          verified = authority.verifyToken(token, expectedJWKSUrl, expectedSigAlg);
        } else {
          verified = authority.verifyToken(token);
        }
      } catch (TokenServiceException e) {
        log.unableToVerifyToken(e);
      }

      // Check received signature algorithm if expectation is configured
      if (verified && expectedSigAlg != null) {
        try {
          final String receivedSigAlg = JWSHeader.parse(token.getHeader()).getAlgorithm().getName();
          if (!receivedSigAlg.equals(expectedSigAlg)) {
            verified = false;
          }
        } catch (ParseException e) {
          log.unableToVerifyToken(e);
          verified = false;
        }
      }

      if (verified) { // If successful, record the verification for future reference
        recordTokenVerification(tokenId);
      }
    }

    return verified;
  }

  /**
   * Determine if the specified token has previously been successfully verified.
   *
   * @param tokenId The unique identifier for a token.
   *
   * @return true, if the specified token has been previously verified; Otherwise, false.
   */
  protected boolean hasTokenBeenVerified(final String tokenId) {
    return (verifiedTokens.getIfPresent(tokenId) != null);
  }

  /**
   * Record a successful token verification.
   *
   * @param tokenId The unique identifier for the token which has been successfully verified.
   */
  protected void recordTokenVerification(final String tokenId) {
    verifiedTokens.put(tokenId, true);
  }

  protected abstract void handleValidationError(HttpServletRequest request, HttpServletResponse response, int status,
                                                String error) throws IOException;

}
