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
package org.apache.knox.gateway.services.security.token;

import java.net.URI;
import java.security.interfaces.RSAPublicKey;
import java.util.Set;

import com.nimbusds.jose.proc.JOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.SecurityContext;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.IssuerJwksTrust;
import org.apache.knox.gateway.services.security.token.impl.JWT;

public interface JWTokenAuthority {

  JWT issueToken(JWTokenAttributes jwtAttributes) throws TokenServiceException;

  boolean verifyToken(JWT token) throws TokenServiceException;

  boolean verifyToken(JWT token, RSAPublicKey publicKey) throws TokenServiceException;

  boolean verifyToken(JWT token, String jwksUrl, String algorithm, JOSEObjectTypeVerifier<SecurityContext> typeVerifier) throws TokenServiceException;

  boolean verifyToken(JWT token, Set<URI> jwksUrls, String algorithm, JOSEObjectTypeVerifier<SecurityContext> typeVerifier) throws TokenServiceException;

  /**
   * Verifies the token against the given JWKS endpoints, fetching them over TLS connections that
   * trust only the given issuer CA bundle.
   *
   * @param jwksTrust the trust of the issuer the JWKS endpoints belong to; {@code null} means the
   *     default truststore applies, as for {@link #verifyToken(JWT, Set, String, JOSEObjectTypeVerifier)}
   */
  default boolean verifyToken(JWT token, Set<URI> jwksUrls, String algorithm, JOSEObjectTypeVerifier<SecurityContext> typeVerifier,
                              IssuerJwksTrust jwksTrust) throws TokenServiceException {
    if (jwksTrust == null) {
      return verifyToken(token, jwksUrls, algorithm, typeVerifier);
    }
    throw new UnsupportedOperationException("Verification with a per-issuer CA bundle is not supported");
  }

}
