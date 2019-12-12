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
import java.text.ParseException;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Iterator;

import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.util.CertificateUtils;

import com.okta.jwt.AccessTokenVerifier;
import com.okta.jwt.JwtVerificationException;
import com.okta.jwt.JwtVerifiers;

public class JWTFederationFilter extends AbstractJWTFilter {

	public static final String JWKS_TOKEN_AUDIENCE = "jwks.thirdparty.token.audience";
	public static final String JWKS_TOKEN_PROVIDER = "jwks.thirdparty.token.provider";
	public static final String JWKS_TOKEN_PRINCIPAL_CLAIM = "jwks.thirdparty.token.principal.claim";
	public static final String TOKEN_VERIFICATION_JWKS_URL = "jwks.thirdparty.token.verification.url";
	public static final String KNOX_TOKEN_AUDIENCES = "knox.token.audiences";
	public static final String TOKEN_VERIFICATION_PEM = "knox.token.verification.pem";
	private static final String KNOX_TOKEN_QUERY_PARAM_NAME = "knox.token.query.param.name";
	private static final String BEARER = "Bearer ";
	private String paramName = "knoxtoken";

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
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

		// token verification pem
		verificationPEM = filterConfig.getInitParameter(TOKEN_VERIFICATION_PEM);
		// setup the public key of the token issuer for verification
		if (verificationPEM != null) {
			publicKey = CertificateUtils.parseRSAPublicKey(verificationPEM);
		}
		// Thirdparty token verification JWKS URL
		String thirdpartyjwksurl = filterConfig.getInitParameter(TOKEN_VERIFICATION_JWKS_URL);

		if (thirdpartyjwksurl != null) {
			thirdpartyPEMVIAJWKS = thirdpartyjwksurl;
		}
		// Thirdparty jws Audiences
		String thirdjwkdAud = filterConfig.getInitParameter(JWKS_TOKEN_AUDIENCE);
		if (thirdjwkdAud != null) {
			thirdpartyjwkdAud = thirdjwkdAud;
		}
		// Thirdparty provider like Okta, Auth0
		String thirdpartyTkpdr = filterConfig.getInitParameter(JWKS_TOKEN_PROVIDER);
		if (thirdpartyTkpdr != null) {
			thirdpartyTokenProvider = thirdpartyTkpdr;
		}
		// Thirdparty provider Principal claim like email , empId, lanId ...
		String thirdPartyPclaim = filterConfig.getInitParameter(JWKS_TOKEN_PRINCIPAL_CLAIM);

		if (thirdPartyPclaim != null) {
			thirdPartyPrincipalClaim = thirdPartyPclaim;
		}

		configureExpectedParameters(filterConfig);
	}

	@Override
	public void destroy() {
	}

	/**
	 * validateJWTtoken
	 * @param wireToken
	 * @param request
	 * @param response
	 * @param chain
	 * @throws IOException
	 */
	private void validateJWTtoken(String wireToken, ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException {
		try {
			Subject subject = null;
			JWT token = new JWTToken(wireToken);
			boolean validate = false;
			if (thirdpartyPEMVIAJWKS != null)

			{
				if (thirdpartyTokenProvider != null && thirdpartyTokenProvider.equalsIgnoreCase("okta")) {
					// Please refer https://github.com/okta/okta-jwt-verifier-java for Okta JWT
					// token verification
					if (thirdpartyjwkdAud == null) {
						throw new JwtVerificationException(
								"JWKS Audience is Null for Provider. Please provide Audience ");
					}
					AccessTokenVerifier jwtVerifier = JwtVerifiers.accessTokenVerifierBuilder()
							.setIssuer(thirdpartyPEMVIAJWKS).setConnectionTimeout(Duration.ofSeconds(3))
							.setAudience(thirdpartyjwkdAud)// defaults to 1s
							.setReadTimeout(Duration.ofSeconds(3)) // defaults to 1s
							.build();
					jwtVerifier.decode(wireToken);
					validate = true;
				}

			}

			if (verificationPEM != null && !validate) {

				boolean validateToken = validateToken((HttpServletRequest) request, (HttpServletResponse) response,
						chain, token);
				if (!validateToken) {
					throw new JwtVerificationException(" Token is not Valid");
				}
			}

			subject = createSubjectFromToken(token, thirdPartyPrincipalClaim);

			continueWithEstablishedSecurityContext(subject, (HttpServletRequest) request,
					(HttpServletResponse) response, chain);

		} catch (ParseException | JwtVerificationException | IOException | ServletException ex) {
			((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
		}
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		Enumeration<String> headerNames = ((HttpServletRequest) request).getHeaderNames();
		System.out.println(headerNames);
		String header = ((HttpServletRequest) request).getHeader("Authorization");
		String header_hive = ((HttpServletRequest) request).getHeader("HiveAuthToken");
		String wireToken;
		if (header != null && header.startsWith(BEARER)) {
			// what follows the bearer designator should be the JWT token being used to
			// request or as an access token
			wireToken = header.substring(BEARER.length());
		} else if(header_hive != null) {
			// check for query param
			wireToken = header_hive;
		}
		else {
			// check for query param
			wireToken = request.getParameter(paramName);
		}

		if (wireToken != null && !wireToken.isEmpty()) {
			// validate JWT token with JWT Issuer
			validateJWTtoken(wireToken, request, response, chain);
		} else {
			// no token provided in header
			((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
		}
	}

	@Override
	protected void handleValidationError(HttpServletRequest request, HttpServletResponse response, int status,
			String error) throws IOException {
		if (error != null) {
			response.sendError(status, error);
		} else {
			response.sendError(status);
		}
	}
}
