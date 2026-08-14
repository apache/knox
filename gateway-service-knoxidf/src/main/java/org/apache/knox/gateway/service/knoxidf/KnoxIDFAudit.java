/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.service.knoxidf;

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.util.Tokens;

/**
 * Centralized audit emission for the KnoxIDF OAuth2/OIDC endpoints
 * ({@link AuthorizeResource}, {@link TokenResource}, {@link RegistrationResource},
 * {@link UserInfoResource}).
 * <p>
 * Every security-relevant decision on these endpoints — an authorization request accepted or
 * rejected, consent shown/granted/denied, an authorization code issued, a code or refresh token
 * redeemed or replayed, a client registered, a federated callback validated, user info served — is
 * recorded through this class so the records share a single {@link Auditor} instance, a consistent
 * action/outcome/resource shape and, crucially, a single masking rule: credentials and full tokens
 * are NEVER written to the audit log. Token identifiers and JWTs are always passed through
 * {@link #mask(String)} first, and {@code client_secret}/{@code code_verifier}/raw refresh tokens
 * are never logged at all.
 * <p>
 * The {@link Auditor} field mirrors {@link TrustedOidcIssuersResource}: it is package-private and
 * non-final so a unit test can inject a capturing mock and assert the emitted record.
 */
final class KnoxIDFAudit {

  /** Placeholder used when a resource/subject identifier is absent or cannot be masked. */
  static final String UNKNOWN = "UNKNOWN";

  /** Placeholder for an unauthenticated caller. */
  static final String ANONYMOUS = "ANONYMOUS";

  // Non-final and package-private to allow test injection of a mock Auditor (see the sibling
  // TrustedOidcIssuersResource, which uses the same idiom).
  static Auditor auditor = AuditServiceFactory.getAuditService()
      .getAuditor(AuditConstants.DEFAULT_AUDITOR_NAME,
          AuditConstants.KNOX_SERVICE_NAME, AuditConstants.KNOX_COMPONENT_NAME);

  private KnoxIDFAudit() {
  }

  /**
   * Emits an audit record via the shared {@link Auditor}. The {@code resource} is used verbatim, so
   * callers that pass a token identifier or JWT MUST first mask it with {@link #mask(String)}. A
   * blank {@code resource} is normalized to {@link #UNKNOWN} because the underlying auditor rejects a
   * null resource name.
   */
  static void audit(final String action, final String resource, final String resourceType,
      final String outcome, final String message) {
    auditor.audit(action, StringUtils.isBlank(resource) ? UNKNOWN : resource, resourceType, outcome, message);
  }

  /**
   * Masks a token or token identifier for safe logging. A Knox token UUID is rendered via
   * {@link Tokens#getTokenIDDisplayText(String)} and a JWT via {@link Tokens#getTokenDisplayText(String)};
   * both keep only a short prefix/suffix so the full secret never reaches the log. Returns
   * {@link #UNKNOWN} for a blank or unmaskable value. This method never returns the raw input.
   */
  static String mask(final String tokenOrId) {
    if (StringUtils.isBlank(tokenOrId)) {
      return UNKNOWN;
    }
    String display = Tokens.getTokenIDDisplayText(tokenOrId);
    if (display == null) {
      display = Tokens.getTokenDisplayText(tokenOrId);
    }
    return display == null ? UNKNOWN : display;
  }

  /** Renders a subject/principal name for logging, mapping a blank/absent principal to {@link #ANONYMOUS}. */
  static String subjectLabel(final String subject) {
    return StringUtils.isBlank(subject) ? ANONYMOUS : subject;
  }
}
