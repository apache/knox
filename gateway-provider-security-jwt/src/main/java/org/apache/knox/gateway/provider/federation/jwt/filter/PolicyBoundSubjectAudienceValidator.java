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

import org.apache.knox.gateway.services.security.token.impl.JWT;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;

/**
 * Validates the {@code aud} claim of the {@code subject_token} of a delegated token exchange (one
 * with an {@code actor_token}) when {@link JWTFederationFilter#TOKEN_EXCHANGE_SUBJECT_TOKEN_POLICY_AUDIENCES_ISSUERS}
 * is configured.
 *
 * <p>The configured audience check is applied first, unchanged. If it fails, the token is still
 * accepted when its {@code iss} is one of the configured issuers, but only provisionally: the
 * validator records that, and {@link TokenExchangeHandler} then requires the token's {@code aud} to
 * intersect the actor's delegation policy {@code subjectAudiences}. This lets a service re-exchange
 * a Knox-minted token addressed to itself (multi-hop delegation) without relaxing the check for
 * actor tokens, headless or same-subject exchanges. Signature, issuer trust and expiry are still
 * validated as usual; only the audience decision is deferred.</p>
 *
 * <p>Holds per-request state, so a new instance must be used for every subject_token.</p>
 */
class PolicyBoundSubjectAudienceValidator implements RequestAudienceValidator {

  private final RequestAudienceValidator delegate;
  private final Set<String> policyAudiencesIssuers;
  private boolean policyAudienceMatchRequired;

  PolicyBoundSubjectAudienceValidator(RequestAudienceValidator delegate, Set<String> policyAudiencesIssuers) {
    this.delegate = delegate;
    this.policyAudiencesIssuers = Set.copyOf(policyAudiencesIssuers);
  }

  @Override
  public AudienceValidationResult validate(HttpServletRequest request, JWT token, List<String> configuredAudiences) {
    policyAudienceMatchRequired = false;
    final AudienceValidationResult result = delegate.validate(request, token, configuredAudiences);
    if (result.isValid()) {
      return result;
    }
    final String issuer = token.getIssuer();
    if (issuer != null && policyAudiencesIssuers.contains(issuer)) {
      policyAudienceMatchRequired = true;
      return AudienceValidationResult.of(true);
    }
    return result;
  }

  /**
   * @return true if the subject_token was accepted without passing the configured audience check,
   *     so its {@code aud} must match the actor policy's {@code subjectAudiences}
   */
  boolean isPolicyAudienceMatchRequired() {
    return policyAudienceMatchRequired;
  }
}
