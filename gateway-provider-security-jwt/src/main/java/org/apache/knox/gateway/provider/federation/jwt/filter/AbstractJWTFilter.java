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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
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

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.provider.federation.jwt.JWTMessages;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.services.security.token.impl.TokenMAC;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSHeader;
import org.apache.knox.gateway.util.Tokens;

public abstract class AbstractJWTFilter implements Filter {

  public static final String TOKEN_STATE_SERVICE_DISABLED_ERROR = "Error in token provider config: passcode use with knox.token.exp.server-managed set to false.";

  /**
   * If specified, this configuration property refers to a value which the issuer of a received
   * token must match. Otherwise, the default value "KNOXSSO" is used
   */
  public static final String JWT_EXPECTED_ISSUER = "jwt.expected.issuer";
  public static final String JWT_DEFAULT_ISSUER = "KNOXSSO";

  public static final String TOKEN_PREFIX = "Token ";
  public static final String DISABLED_POSTFIX = " is disabled";
  public static final String IDLE_TIMEOUT_POSTFIX = " exceeded idle timeout";

  /**
   * If specified, this configuration property refers to the signature algorithm which a received
   * token must match. Otherwise, the default value "RS256" is used
   */
  public static final String JWT_EXPECTED_SIGALG = "jwt.expected.sigalg";
  public static final String JWT_DEFAULT_SIGALG = "RS256";

  public static final String JWT_INSTANCE_KEY_FALLBACK = "jwt.instance.key.fallback";
  public static final boolean JWT_INSTANCE_KEY_FALLBACK_DEFAULT = false;

  static JWTMessages log = MessagesFactory.get( JWTMessages.class );

  private static AuditService auditService = AuditServiceFactory.getAuditService();
  private static Auditor auditor = auditService.getAuditor(
      AuditConstants.DEFAULT_AUDITOR_NAME, AuditConstants.KNOX_SERVICE_NAME,
      AuditConstants.KNOX_COMPONENT_NAME );

  protected List<String> audiences;
  protected JWTokenAuthority authority;
  protected RSAPublicKey publicKey;
  protected SignatureVerificationCache signatureVerificationCache;
  private String expectedIssuer;
  private String expectedSigAlg;
  protected String expectedPrincipalClaim;
  protected Set<URI> expectedJWKSUrls = new LinkedHashSet();
  protected Set<JOSEObjectType> allowedJwsTypes;

  private TokenStateService tokenStateService;
  private TokenMAC tokenMAC;
  protected long idleTimeoutSeconds = -1;
  protected String topologyName;
  protected boolean isJwtInstanceKeyFallback = JWT_INSTANCE_KEY_FALLBACK_DEFAULT;

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
          try {
            final GatewayConfig config = (GatewayConfig) context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
            final AliasService aliasService =  services.getService(ServiceType.ALIAS_SERVICE);
            tokenMAC = new TokenMAC(config.getKnoxTokenHashAlgorithm(), aliasService.getPasswordFromAliasForGateway(TokenMAC.KNOX_TOKEN_HASH_KEY_ALIAS_NAME));
          } catch (ServiceLifecycleException | AliasServiceException e) {
            throw new ServletException("Error while initializing Knox token MAC generator", e);
          }
        }
      }
    }

    // Setup the verified tokens cache
    topologyName = context != null ? (String) context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE) : null;
    signatureVerificationCache = SignatureVerificationCache.getInstance(topologyName, filterConfig);

    String fallbackConfig = filterConfig.getInitParameter(JWT_INSTANCE_KEY_FALLBACK);
    isJwtInstanceKeyFallback = fallbackConfig != null ? Boolean.parseBoolean(fallbackConfig) : JWT_INSTANCE_KEY_FALLBACK_DEFAULT;
  }

  protected void configureExpectedParameters(FilterConfig filterConfig) {
    expectedIssuer = filterConfig.getInitParameter(JWT_EXPECTED_ISSUER);
    if (expectedIssuer == null) {
      expectedIssuer = JWT_DEFAULT_ISSUER;
    }

    expectedSigAlg = filterConfig.getInitParameter(JWT_EXPECTED_SIGALG);
    if(StringUtils.isBlank(expectedSigAlg)) {
      expectedSigAlg = JWT_DEFAULT_SIGALG;
    }
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

  protected boolean tokenIsStillValid(final JWT jwtToken) throws UnknownTokenException {
    Date expires = getServerManagedStateExpiration(TokenUtils.getTokenId(jwtToken));
    if (expires == null) {
      // if there is no expiration date then the lifecycle is tied entirely to
      // the cookie validity - otherwise ensure that the current time is before
      // the designated expiration time
      expires = jwtToken.getExpiresDate();
    }
    return expires == null || new Date().before(expires);
  }

  protected boolean tokenIsStillValid(final String tokenId) throws UnknownTokenException {
    Date expires = getServerManagedStateExpiration(tokenId);
    return expires == null || (new Date().before(expires));
  }

  private Date getServerManagedStateExpiration(final String tokenId) throws UnknownTokenException {
    Date expires = null;
    if (tokenStateService != null) {
      long value = tokenStateService.getTokenExpiration(tokenId);
      if (value > 0) {
        expires = new Date(value);
      }
    }
    return expires;
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
  protected boolean validateAudiences(final JWT jwtToken) {
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

  protected void continueWithEstablishedSecurityContext(final Subject subject,
                                                        final HttpServletRequest request,
                                                        final HttpServletResponse response,
                                                        final FilterChain chain) throws IOException, ServletException {
    AuditContext context = auditService.getContext();
    if (context != null) {
      context.setUsername( SubjectUtils.getPrimaryPrincipalName(subject) );
      auditService.attachContext(context);
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

  public Subject createSubjectFromToken(final String token) throws ParseException, UnknownTokenException {
    return createSubjectFromToken(new JWTToken(token));
  }

  protected Subject createSubjectFromToken(final JWT token) throws UnknownTokenException {
    String principal = token.getSubject();
    String claimvalue = null;
    if (expectedPrincipalClaim != null) {
      claimvalue = token.getClaim(expectedPrincipalClaim);
    }
    // The newly constructed Sets check whether this Subject has been set read-only
    // before permitting subsequent modifications. The newly created Sets also prevent
    // illegal modifications by ensuring that callers have sufficient permissions.
    //
    // To modify the Principals Set, the caller must have AuthPermission("modifyPrincipals").
    // To modify the public credential Set, the caller must have AuthPermission("modifyPublicCredentials").
    // To modify the private credential Set, the caller must have AuthPermission("modifyPrivateCredentials").
    return createSubjectFromTokenData(principal, claimvalue);
  }

  public Subject createSubjectFromTokenIdentifier(final String tokenId) throws UnknownTokenException {
    TokenMetadata metadata = tokenStateService.getTokenMetadata(tokenId);
    if (metadata != null) {
      // using tokenID and passcode as CLIENT_ID and CLIENT_SECRET will
      // result in a metadata item called "type". If the value is set
      // to CLIENT_ID then it will be assumed to be a CLIENT_ID and we
      // will use the token id as the username. Since we don't know the
      // token id until it is created, the username is always the same
      // in the record. Using the token id makes it a unique username for
      // audit and the like.
      final String username = metadata.isClientId() ? tokenId : metadata.getUserName();

      return createSubjectFromTokenData(username, null);
    }
    return null;
  }

  protected Subject createSubjectFromTokenData(final String principal, final String expectedPrincipalClaimValue) {
    String claimValue =
              (expectedPrincipalClaimValue != null) ? expectedPrincipalClaimValue.toLowerCase(Locale.ROOT) : null;

    @SuppressWarnings("rawtypes")
    HashSet emptySet = new HashSet();
    Set<Principal> principals = new HashSet<>();
    Principal p = new PrimaryPrincipal(claimValue != null ? claimValue : principal);
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


  protected boolean validateToken(final HttpServletRequest request, final HttpServletResponse response,
      final FilterChain chain, final JWT token)
      throws IOException, ServletException {
    final String tokenId = TokenUtils.getTokenId(token);
    final String displayableTokenId = Tokens.getTokenIDDisplayText(tokenId);
    final String displayableToken = Tokens.getTokenDisplayText(token.toString());
    // confirm that issuer matches the intended target
    if (expectedIssuer.equals(token.getIssuer())) {
      // if there is no expiration data then the lifecycle is tied entirely to
      // the cookie validity - otherwise ensure that the current time is before
      // the designated expiration time
      try {
        if (tokenIsStillValid(token)) {
          boolean audValid = validateAudiences(token);
          if (audValid) {
            Date nbf = token.getNotBeforeDate();
            if (nbf == null || new Date().after(nbf)) {
              final TokenMetadata tokenMetadata = tokenStateService == null ? null : tokenStateService.getTokenMetadata(tokenId);
              if (isTokenEnabled(tokenMetadata)) {
                if (isIdleTimeoutLimitNotExceeded(tokenMetadata)) {
                  if (verifyTokenSignature(token)) {
                    markLastUsedAt(tokenId, tokenMetadata);
                    return true;
                  } else {
                    log.failedToVerifyTokenSignature(displayableToken, displayableTokenId);
                    handleValidationError(request, response, HttpServletResponse.SC_UNAUTHORIZED, null);
                  }
                } else {
                  log.idleTimoutExceeded(token.getSubject(), displayableTokenId, idleTimeoutSeconds);
                  handleValidationError(request, response, HttpServletResponse.SC_UNAUTHORIZED, TOKEN_PREFIX + displayableTokenId + IDLE_TIMEOUT_POSTFIX);
                }
              } else {
                log.disabledToken(displayableTokenId);
                handleValidationError(request, response, HttpServletResponse.SC_UNAUTHORIZED, TOKEN_PREFIX + displayableTokenId + DISABLED_POSTFIX);
              }
            } else {
              log.notBeforeCheckFailed();
              handleValidationError(request, response, HttpServletResponse.SC_BAD_REQUEST,
                      "Bad request: the NotBefore check failed");
            }
          } else {
            log.failedToValidateAudience(displayableToken, displayableTokenId);
            handleValidationError(request, response, HttpServletResponse.SC_BAD_REQUEST,
                    "Bad request: missing required token audience");
          }
        } else {
          log.tokenHasExpired(displayableToken, displayableTokenId);

          // Explicitly evict the record of this token's signature verification (if present).
          // There is no value in keeping this record for expired tokens, and explicitly removing them may prevent
          // records for other valid tokens from being prematurely evicted from the cache.
          removeSignatureVerificationRecord(token.toString());

          handleValidationError(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Token has expired");

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

  private boolean isTokenEnabled(TokenMetadata tokenMetadata) throws UnknownTokenException {
    return tokenMetadata == null ? true : tokenMetadata.isEnabled();
  }

  private boolean isIdleTimeoutLimitNotExceeded(TokenMetadata tokenMetadata) throws UnknownTokenException {
    if (idleTimeoutSeconds > 0) {
      final Instant lastUsedAt = tokenMetadata == null ? null : tokenMetadata.getLastUsedAt();
      final Instant idleTimeoutLimit = lastUsedAt == null ? null : lastUsedAt.plusSeconds(idleTimeoutSeconds);
      return idleTimeoutLimit == null ? true : (tokenMetadata.isKnoxSsoCookie() && idleTimeoutLimit.isAfter(Instant.now()));
    }
    return true; // no idle timeout is configured -> ignore idleness check
  }

  private void markLastUsedAt(String tokenId, TokenMetadata tokenMetadata) throws UnknownTokenException {
    if (tokenMetadata != null && tokenMetadata.isKnoxSsoCookie()) {
      // to avoid updating every single metadata value, we create a new token metadata
      // instance only with the updated "LAST_USED_AT" information
      final TokenMetadata updatedTokenMetadata = new TokenMetadata();
      updatedTokenMetadata.useTokenNow();
      tokenStateService.addMetadata(tokenId, updatedTokenMetadata);
    }
  }

  protected boolean validateToken(final HttpServletRequest request,
                                  final HttpServletResponse response,
                                  final FilterChain chain,
                                  final String tokenId,
                                  final String passcode)
          throws IOException, ServletException {

    final String displayableTokenId = tokenId == null ? "N/A" : Tokens.getTokenIDDisplayText(tokenId);
    if (tokenStateService != null) {
      try {
        if (tokenId != null) {
          if (tokenIsStillValid(tokenId)) {
            final TokenMetadata tokenMetadata = tokenStateService == null ? null : tokenStateService.getTokenMetadata(tokenId);
            if (isTokenEnabled(tokenMetadata)) {
              if (isIdleTimeoutLimitNotExceeded(tokenMetadata)) {
                if (hasSignatureBeenVerified(passcode) || validatePasscode(tokenId, passcode)) {
                  markLastUsedAt(tokenId, tokenMetadata);
                  return true;
                } else {
                  log.wrongPasscodeToken(tokenId);
                  handleValidationError(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid passcode");
                }
              } else {
                // tokenMetadata at this point cannot be null (see isIdleTimeoutLimitNotExceeded(...))
                log.idleTimoutExceeded(tokenMetadata.getUserName(), displayableTokenId, idleTimeoutSeconds);
                handleValidationError(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Token " + displayableTokenId + " exceeded idle timeout");
              }
            } else {
              log.disabledToken(displayableTokenId);
              handleValidationError(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Token " + displayableTokenId + " is disabled");
            }
          } else {
            log.tokenHasExpired(displayableTokenId);
            // Explicitly evict the record of this token's signature verification (if present).
            // There is no value in keeping this record for expired tokens, and explicitly removing them may prevent
            // records for other valid tokens from being prematurely evicted from the cache.
            removeSignatureVerificationRecord(passcode);
            handleValidationError(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Token has expired");
          }
        } else {
          log.missingTokenPasscode();
          handleValidationError(request, response, HttpServletResponse.SC_BAD_REQUEST, "Bad request: missing token passcode.");
        }
      } catch (UnknownTokenException e) {
        log.unableToVerifyExpiration(e);
        handleValidationError(request, response, HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
      }
    } else {
      log.unableToVerifyPasscodeToken(displayableTokenId);
      handleValidationError(request, response, HttpServletResponse.SC_UNAUTHORIZED, TOKEN_STATE_SERVICE_DISABLED_ERROR);
    }

    return false;
  }

  private boolean validatePasscode(String tokenId, String passcode) throws UnknownTokenException {
    final long issueTime = tokenStateService.getTokenIssueTime(tokenId);
    final TokenMetadata tokenMetadata = tokenStateService.getTokenMetadata(tokenId);
    final String userName = tokenMetadata == null ? "" : tokenMetadata.getUserName();
    final byte[] storedPasscode = tokenMetadata == null ? null : tokenMetadata.getPasscode().getBytes(UTF_8);
    final boolean validPasscode = Arrays.equals(tokenMAC.hash(tokenId, issueTime, userName, passcode).getBytes(UTF_8), storedPasscode);
    if (validPasscode) {
      recordSignatureVerification(passcode);
    }
    return validPasscode;
  }

  protected boolean verifyTokenSignature(final JWT token) {
    boolean verified;

    final String serializedJWT = token.toString();

    // Check if the token has already been verified
    verified = hasSignatureBeenVerified(serializedJWT);

    // If it has not yet been verified, then perform the verification now
    if (!verified) {
      try {
        boolean attemptedPEMVerification  = false;
        boolean attemptedJWKSVerification = false;

        if (publicKey != null) {
          attemptedPEMVerification = true;
          verified = authority.verifyToken(token, publicKey);
          log.pemVerificationResultMessage(verified);
        }

        if (!verified && expectedJWKSUrls != null && !expectedJWKSUrls.isEmpty()) {
          attemptedJWKSVerification = true;
          verified = authority.verifyToken(token, expectedJWKSUrls, expectedSigAlg, allowedJwsTypes);
          log.jwksVerificationResultMessage(verified);
        }

        if(!verified && ((!attemptedPEMVerification && !attemptedJWKSVerification) || isJwtInstanceKeyFallback)) {
          verified = authority.verifyToken(token);
          log.signingKeyVerificationResultMessage(verified);
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
        recordSignatureVerification(serializedJWT);
      }
    }

    return verified;
  }

  /**
   * Determine if the specified JWT or Passcode token signature has previously been successfully verified.
   *
   * @param token A serialized JWT String or Passcode token.
   *
   * @return true, if the specified token has been previously verified; Otherwise, false.
   */
  protected boolean hasSignatureBeenVerified(final String token) {
    return signatureVerificationCache.hasSignatureBeenVerified(token);
  }

  /**
   * Record a successful JWT or Passcode token signature verification.
   *
   * @param token The serialized String for a JWT or Passcode token which has been successfully verified.
   */
  protected void recordSignatureVerification(final String token) {
    signatureVerificationCache.recordSignatureVerification(token);
  }

  /**
   * Explicitly evict the signature verification record for the specified JWT from the cache if it exists.
   *
   * @param token The serialized String for a JWT or Passcode token whose signature verification record should be evicted.
   */
  protected void removeSignatureVerificationRecord(final String token) {
    signatureVerificationCache.removeSignatureVerificationRecord(token);
  }

  protected abstract void handleValidationError(HttpServletRequest request, HttpServletResponse response, int status,
                                                String error) throws IOException;

}
