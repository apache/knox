/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.provider.federation.jwt.filter;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.SignedJWT;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.provider.federation.jwt.keys.JWKSKeyStore;
import org.apache.knox.gateway.provider.federation.jwt.keys.KeyNotFoundException;
import org.apache.knox.gateway.provider.federation.jwt.keys.KeyStore;
import org.apache.knox.gateway.provider.federation.jwt.keys.KeyStoreException;
import org.apache.knox.gateway.provider.federation.jwt.keys.PEMKeyStore;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.util.CookieUtils;
import org.apache.knox.gateway.util.Tokens;

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
import java.net.MalformedURLException;
import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class JWTCookieOrBearerFederationFilter extends AbstractJWTFilter {
    public static final String JWT_EXPECTED_AUDIENCE = "jwt.expected.audience";
    public static final String TOKEN_VERIFICATION_PEM = "knox.token.verification.pem";
    public static final String TOKEN_VERIFICATION_JWKS_URL = "knox.token.verification.jwks";
    public static final String KNOX_TOKEN_QUERY_PARAM_NAME = "knox.token.query.param.name";
    public static final String KNOX_TOKEN_COOKIE_NAME = "knox.token.cookie.name";
    public static final String BAD_REQUESTS_UNAUTHORIZED = "knox.token.bad.requests.unauthorized";
    public static final String KNOX_TOKEN_USE_COOKIES = "knox.token.use.cookie";
    public static final String KNOX_TOKEN_USE_BEARER = "knox.token.use.bearer";
    private static final String BEARER = "Bearer ";
    private static final String DEFAULT_SSO_COOKIE_NAME = "hadoop-jwt";
    private static final String DEFAULT_TOKEN_PARAM_NAME = "knoxtoken";
    private static final boolean DEFAULT_BAD_REQUESTS_UNAUTHORIZED = false;

    private static Messages log = MessagesFactory.get(Messages.class);
    private String paramName = DEFAULT_TOKEN_PARAM_NAME;
    private String cookieName = DEFAULT_SSO_COOKIE_NAME;
    private boolean badRequestsUnauthorized = DEFAULT_BAD_REQUESTS_UNAUTHORIZED;
    private String expectedIssuer = JWT_DEFAULT_ISSUER;
    private String expectedSigAlg = JWT_DEFAULT_SIGALG;
    private boolean useCookies = true;
    private boolean useBearer = true;
    private KeyStore keyStore;


    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        super.init(filterConfig);

        // expected audiences or null
        String expectedAudiences = filterConfig.getInitParameter(JWT_EXPECTED_AUDIENCE);
        if (expectedAudiences != null) {
            audiences = parseExpectedAudiences(expectedAudiences);
        }

        // query param name for finding the provided knoxtoken
        String queryParamName = filterConfig.getInitParameter(KNOX_TOKEN_QUERY_PARAM_NAME);
        if (queryParamName != null) {
            paramName = queryParamName;
        }

        // configured cookieName
        String cookieNameString = filterConfig.getInitParameter(KNOX_TOKEN_COOKIE_NAME);
        if (cookieNameString != null) {
            cookieName = cookieNameString;
        }

        // configure whether 400s should be returned as 401s
        String badRequestsUnauthorizedString = filterConfig.getInitParameter(BAD_REQUESTS_UNAUTHORIZED);
        if (badRequestsUnauthorizedString != null) {
            badRequestsUnauthorized = Boolean.parseBoolean(badRequestsUnauthorizedString);
        }

        // should authentication be attempted using a cookie?
        String useCookiesValue = filterConfig.getInitParameter(KNOX_TOKEN_USE_COOKIES);
        if (useCookiesValue != null) {
            useCookies = Boolean.parseBoolean(useCookiesValue);
        }

        // should authentication be attempted using a bearer token?
        String useBearerValue = filterConfig.getInitParameter(KNOX_TOKEN_USE_BEARER);
        if (useBearerValue != null) {
            useBearer = Boolean.parseBoolean(useBearerValue);
        }

        if (!useBearer && !useCookies) {
            String msg = format(Locale.ROOT, "Either '%s', '%s', or both must be true.", KNOX_TOKEN_USE_COOKIES, KNOX_TOKEN_USE_BEARER);
            throw new ServletException(msg);
        }

        // the expected issuer 'iss' field
        String expectedIssuerValue = filterConfig.getInitParameter(JWT_EXPECTED_ISSUER);
        if (expectedIssuerValue != null) {
            expectedIssuer = expectedIssuerValue;
        }

        // the expected signature algorithm
        String expectedSigAlgValue = filterConfig.getInitParameter(JWT_EXPECTED_SIGALG);
        if (expectedSigAlgValue != null) {
            expectedSigAlg = expectedSigAlgValue;
        }

        // the public signing key is passed as a config parameter
        String verificationPEM = filterConfig.getInitParameter(TOKEN_VERIFICATION_PEM);
        if (!isBlank(verificationPEM)) {
            keyStore = new PEMKeyStore(verificationPEM);
        }

        // the public signing keys are retrieved from a JWKS provider
        String jwksURL = filterConfig.getInitParameter(TOKEN_VERIFICATION_JWKS_URL);
        if (!isBlank(jwksURL)) {
            keyStore = new JWKSKeyStore(toURL(jwksURL));
        }

        if (isBlank(verificationPEM) == isBlank(jwksURL)) {
            throw new ServletException(String.format(Locale.ROOT,"Expected one of either '%s' or '%s' to be defined.",
                    TOKEN_VERIFICATION_PEM, TOKEN_VERIFICATION_JWKS_URL));
        }
    }

    private URL toURL(String url) throws ServletException {
        try {
            return new URL(url);
        } catch(MalformedURLException e) {
            throw new ServletException(e);
        }
    }

    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        try {
            if (useCookies) {
                Subject subject = authenticateWithCookies(req, res);
                if (subject != null) {
                    acceptToken(req, res, chain, subject);
                    return;
                }
            }
            if (useBearer) {
                String token = getWireToken(req);
                Subject subject = getAuthenticatedSubject(req, res, token);
                if (subject != null) {
                    acceptToken(req, res, chain, subject);
                    return;
                }
            }
        } catch (NoValidCookiesException e) {
            noValidCookies(req, res);
        } catch (KeyNotFoundException e) {
            signingKeyNotFound(req, res, e);
        } catch (KeyStoreException e) {
            keyStoreError(req, res, e);
        } catch (ParseException e) {
            corruptToken(req, res, e);
        } catch (UnknownTokenException e) {
            unknownToken(req, res, e);
        } catch (NoBearerTokenException e) {
            missingBearerToken(req, res);
        }
    }

    /**
     * Attempts to authenticate using session cookies.
     * @param req The request.
     * @param res The response.
     * @return The subject if authentication successful. Otherwise, null
     * @throws KeyStoreException if the key store is not able to return the signing keys.
     * @throws KeyNotFoundException if the token uses a signing key that we do not have.
     */
    private Subject authenticateWithCookies(HttpServletRequest req, HttpServletResponse res)
            throws NoValidCookiesException, KeyStoreException, IOException {
        // If the cookie is present we make a decision based on this alone
        // and do not redirect to the SSO login page in this implementation
        List<Cookie> ssoCookies = CookieUtils.getCookiesForName(req, cookieName);
        for (Cookie ssoCookie : ssoCookies) {
            try {
                Subject subject = getAuthenticatedSubject(req, res, ssoCookie.getValue());
                if (subject != null) {
                    return subject;
                }
            } catch (KeyNotFoundException ignore) {
                // Ignore the error so that we keep checking more cookies
                log.unknownSigningKey();
            } catch (ParseException ignore) {
                // Ignore the error so that we keep checking more cookies
                log.corruptToken(ignore);
            } catch (UnknownTokenException ignore) {
                // Ignore the error so that we keep checking more cookies
                log.unknownToken(ignore);
            }
        }
        if (!ssoCookies.isEmpty()) {
            // No valid cookies found but cookie was present so reject this request and do no further processing
            throw new NoValidCookiesException();
        }
        return null;
    }

    /**
     * Returns the {@link Subject} from a JWT. If the JWT cannot be validated, null is returned.
     * @param request The request.
     * @param response The response.
     * @param wireToken The encoded JWT to authenticate.
     * @return If the JWT is valid, returns the subject. Otherwise, null.
     * @throws ParseException if the JWT is corrupt and cannot be used to authenticate.
     * @throws KeyStoreException if the {@link KeyStore} is unable to retrieve keys.
     * @throws KeyNotFoundException If the {@link KeyStore} is not able to retrieve the public signing key.
     */
    private Subject getAuthenticatedSubject(HttpServletRequest request,
                                            HttpServletResponse response,
                                            String wireToken)
            throws ParseException, KeyStoreException, KeyNotFoundException, IOException, UnknownTokenException {
        Subject subject = null;

        // retrieve the signing key from the token's 'kid' field. if the field is missing, becomes an empty string.
        SignedJWT jwt = SignedJWT.parse(wireToken);
        String kid = defaultString(jwt.getHeader().getKeyID());
        RSAPublicKey signingKey = keyStore.getPublic(kid);

        // validate the token
        JWT token = new JWTToken(wireToken);
        if (validateToken(request, response, token, signingKey)) {
            subject = createSubjectFromToken(token);
        }
        return subject;
    }

    /**
     * Validates a JWT token.
     * <p>Forked from {@link AbstractJWTFilter#validateToken(HttpServletRequest, HttpServletResponse, FilterChain, JWT)}.
     * @param request The request.
     * @param response The response.
     * @param token The token to validate.
     * @param signingKey The public key used to sign the token.
     * @return If the token is authenticate, returns true. Otherwise, false.
     * @throws IOException
     * @throws ParseException if the JWT is corrupt and cannot be used to authenticate.
     * @throws KeyStoreException if the {@link KeyStore} is unable to retrieve keys.
     * @throws KeyNotFoundException If the {@link KeyStore} is not able to retrieve the public signing key.
     */
    protected boolean validateToken(HttpServletRequest request,
                                    HttpServletResponse response,
                                    JWT token,
                                    RSAPublicKey signingKey) throws IOException {
        final String tokenId = TokenUtils.getTokenId(token);
        final String displayableToken = Tokens.getTokenDisplayText(token.toString());
        boolean verified = false;
        try {
            verified = this.authority.verifyToken(token, signingKey);
        } catch (TokenServiceException e) {
            log.corruptToken(e);
        }

        if (verified) {
            try {
                String receivedSigAlg = JWSHeader.parse(token.getHeader()).getAlgorithm().getName();
                if (!receivedSigAlg.equals(this.expectedSigAlg)) {
                    log.unexpectedSigAlg();
                    verified = false;
                }
            } catch (ParseException e) {
                log.corruptToken(e);
                verified = false;
            }
        }

        if (verified) {
            if (this.expectedIssuer.equals(token.getIssuer())) {
                try {
                    if (this.tokenIsStillValid(token)) {
                        boolean audValid = this.validateAudiences(token);
                        if (audValid) {
                            Date nbf = token.getNotBeforeDate();
                            if (nbf == null || (new Date()).after(nbf)) {
                                return true;
                            }
                            log.notBeforeCheckFailed();
                            this.handleValidationError(request, response, 400, "Bad request: the NotBefore check failed");
                        } else {
                            log.failedToValidateAudience(tokenId, displayableToken);
                            this.handleValidationError(request, response, 400, "Bad request: missing required token audience");
                        }
                    } else {
                        log.tokenHasExpired(tokenId, displayableToken);
                        this.handleValidationError(request, response, 400, "Bad request: token has expired");
                    }
                } catch (UnknownTokenException e) {
                    log.unableToVerifyExpiration(e);
                    this.handleValidationError(request, response, 401, e.getMessage());
                }
            } else {
                log.failedToValidateIssuer();
                this.handleValidationError(request, response, 401, (String)null);
            }
        } else {
            log.failedToVerifyTokenSignature(tokenId, displayableToken);
            this.handleValidationError(request, response, 401, (String)null);
        }

        return false;
    }

    protected String getWireToken(HttpServletRequest request) throws NoBearerTokenException {
        final String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            // what follows the bearer designator should be the JWT token being used to
            // request or as an access token
            return header.substring(BEARER.length());
        }

        // check for query param
        String token = request.getParameter(paramName);
        if (token != null) {
            return token;
        }

        throw new NoBearerTokenException();
    }

    @Override
    protected void handleValidationError(HttpServletRequest request, HttpServletResponse response, int status,
            String error) throws IOException {
        if (badRequestsUnauthorized && status == HttpServletResponse.SC_BAD_REQUEST) {
            status = HttpServletResponse.SC_UNAUTHORIZED;
        }
        if (error != null) {
            response.sendError(status, error);
        } else {
            response.sendError(status);
        }
    }

    private void acceptToken(HttpServletRequest req, HttpServletResponse res, FilterChain chain, Subject subject)
            throws IOException, ServletException {
        continueWithEstablishedSecurityContext(subject, req, res, chain);
    }

    private void corruptToken(HttpServletRequest req, HttpServletResponse res, ParseException e) throws IOException {
        log.unableToVerifyToken(e);
        handleValidationError(req, res, HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
    }

    private void unknownToken(HttpServletRequest req, HttpServletResponse res, UnknownTokenException e) throws IOException {
        log.unableToVerifyToken(e);
        handleValidationError(req, res, HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
    }

    private void signingKeyNotFound(HttpServletRequest req, HttpServletResponse res, KeyNotFoundException e) throws IOException {
        log.unableToVerifyToken(e);
        handleValidationError(req, res, HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
    }

    private void keyStoreError(HttpServletRequest req, HttpServletResponse res, KeyStoreException e) throws IOException {
        log.unableToVerifyToken(e);
        handleValidationError(req, res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }

    private void missingBearerToken(HttpServletRequest req, HttpServletResponse res) throws IOException {
        log.missingBearerToken();
        handleValidationError(req, res, HttpServletResponse.SC_UNAUTHORIZED, "missing bearer token");
    }

    private void noValidCookies(HttpServletRequest req, HttpServletResponse res) throws IOException {
        log.missingBearerToken();
        handleValidationError(req, res, HttpServletResponse.SC_UNAUTHORIZED, "no valid cookies found");
    }

    protected KeyStore getKeyStore() {
        return keyStore;
    }
}

/**
 * An exception indicating that cookies are present, but none of them contain
 * a valid JWT.
 *
 * <p>This is not the same as there being no cookies. If there are no cookies present,
 * authentication may fall-back to check for a bearer token.
 */
class NoValidCookiesException extends Exception {
    NoValidCookiesException() {
        super("None of the presented cookies are valid.");
    }
}

/**
 * An exception indicating that a bearer token was not presented in cases
 * where it is required.
 */
class NoBearerTokenException extends Exception {
    NoBearerTokenException() {
        super("No bearer token was presented.");
    }
}