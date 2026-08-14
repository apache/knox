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
package org.apache.knox.gateway.grpc;

import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.provider.federation.jwt.JWTValidator;
import org.apache.knox.gateway.provider.federation.jwt.filter.SignatureVerificationCache;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.util.CertificateUtils;

/**
 * Validates the bearer token a gRPC client presents, using the same machinery as
 * the WebSocket listener.
 * <p>
 * Bearer tokens are the whole of the credential vocabulary here, because that is
 * all an unmodified gRPC client can generally carry: a bearer token, static
 * metadata and TLS. gRPC has no challenge-response step for SPNEGO to hook into.
 * In a Kerberos deployment the user still authenticates with Kerberos — to the
 * {@code knoxtoken} API, over HTTPS — and the resulting JWT acts as the
 * delegation credential on the data path, exactly as delegation tokens do for
 * HDFS.
 * <p>
 * Validation covers issuer, expiry, not-before, signature and — when server
 * managed token state is on — revocation, so an administrator can kill one
 * long-running client's access without touching the principal.
 */
public class TokenAuthenticator {

  private static final String KNOXSSO_TOPOLOGY = "knoxsso";
  private static final String KNOXSSO_ROLE = "KNOXSSO";
  private static final String KNOXTOKEN_ROLE = "KNOXTOKEN";
  private static final String JWT_EXPECTED_ISSUER = "jwt.expected.issuer";
  private static final String JWT_EXPECTED_SIGALG = "jwt.expected.sigalg";
  private static final String SSO_VERIFICATION_PEM = "sso.token.verification.pem";
  /** Names the signature-verification cache; not a topology lookup. */
  private static final String CACHE_NAME = "grpc";

  private final GatewayConfig config;
  private final GatewayServices services;

  public TokenAuthenticator(GatewayConfig config, GatewayServices services) {
    this.config = config;
    this.services = services;
  }

  /**
   * Validates a serialized JWT and returns the identity it establishes.
   *
   * @param serializedToken the bearer token from the call metadata
   * @return the authenticated user
   * @throws AuthenticationException if the token is malformed, expired, revoked,
   *         from an unexpected issuer, or fails signature verification
   */
  public AuthenticatedUser authenticate(String serializedToken) throws AuthenticationException {
    final JWT token;
    try {
      token = new JWTToken(serializedToken);
    } catch (ParseException e) {
      throw new AuthenticationException("Bearer token is not a well-formed JWT", e);
    }

    final Map<String, String> params = tokenProviderParams();
    final JWTValidator validator = new JWTValidator(
        token,
        services.getService(ServiceType.TOKEN_SERVICE),
        SignatureVerificationCache.getInstance(CACHE_NAME, new MapFilterConfig(CACHE_NAME, params)));

    if (params.containsKey(SSO_VERIFICATION_PEM)) {
      try {
        final RSAPublicKey publicKey = CertificateUtils.parseRSAPublicKey(params.get(SSO_VERIFICATION_PEM));
        validator.setPublicKey(publicKey);
      } catch (ServletException e) {
        throw new AuthenticationException("Cannot parse the configured token verification key", e);
      }
    }
    if (params.containsKey(JWT_EXPECTED_ISSUER)) {
      validator.setExpectedIssuer(params.get(JWT_EXPECTED_ISSUER));
    }
    if (params.containsKey(JWT_EXPECTED_SIGALG)) {
      validator.setExpectedSigAlg(params.get(JWT_EXPECTED_SIGALG));
    }
    if (isServerManagedTokenStateEnabled(params.get(TokenStateService.CONFIG_SERVER_MANAGED))) {
      validator.setTokenStateService(services.getService(ServiceType.TOKEN_STATE_SERVICE));
    }

    final boolean valid;
    try {
      valid = validator.validate();
    } catch (RuntimeException e) {
      // Validation reaches third-party JOSE code, which signals some malformed
      // credentials by throwing rather than returning false — a token signed with
      // an unexpected algorithm family, for instance. Treat any such failure as a
      // failed authentication: a credential that provokes an error is emphatically
      // not one that passed, and letting the exception escape would answer an
      // attacker probing for it differently from an ordinary rejection.
      throw new AuthenticationException("Bearer token could not be validated", e);
    }
    if (!valid) {
      throw new AuthenticationException("Bearer token failed validation");
    }

    final String principal = validator.getUsername();
    if (principal == null || principal.isEmpty()) {
      throw new AuthenticationException("Bearer token carries no subject");
    }
    return new AuthenticatedUser(principal, groupsFrom(token));
  }

  /**
   * Reads the group claim the token issuer embedded, if any. A token minted
   * without groups simply yields none, which the ACL check treats as "member of
   * nothing" rather than an error.
   */
  private static Set<String> groupsFrom(JWT token) {
    final Object claim = token.getClaimAsObject(JWTToken.KNOX_GROUPS_CLAIM);
    if (claim == null) {
      return Collections.emptySet();
    }
    final Set<String> groups = new LinkedHashSet<>();
    if (claim instanceof Collection) {
      for (Object group : (Collection<?>) claim) {
        if (group != null) {
          groups.add(String.valueOf(group));
        }
      }
    } else {
      groups.add(String.valueOf(claim));
    }
    return groups;
  }

  /**
   * Collects the token provider's parameters so this listener validates tokens
   * on the same terms the servlet path does. The topology that issues tokens is
   * preferred; a deployment that only runs KnoxSSO falls back to that.
   */
  private Map<String, String> tokenProviderParams() {
    final TopologyService topologyService = services.getService(ServiceType.TOPOLOGY_SERVICE);
    if (topologyService == null) {
      return Collections.emptyMap();
    }

    Map<String, String> ssoParams = null;
    for (Topology topology : topologyService.getTopologies()) {
      for (Service service : topology.getServices()) {
        if (KNOXTOKEN_ROLE.equals(service.getRole())) {
          return service.getParams();
        }
        if (KNOXSSO_ROLE.equals(service.getRole()) && KNOXSSO_TOPOLOGY.equals(topology.getName())) {
          ssoParams = service.getParams();
        }
      }
    }
    return ssoParams == null ? Collections.emptyMap() : ssoParams;
  }

  private boolean isServerManagedTokenStateEnabled(String providerParamValue) {
    if (providerParamValue == null || providerParamValue.isEmpty()) {
      return config != null && config.isServerManagedTokenStateEnabled();
    }
    return Boolean.parseBoolean(providerParamValue);
  }

  /** Signals that a call's credential did not establish an identity. */
  public static class AuthenticationException extends Exception {
    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
      super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
