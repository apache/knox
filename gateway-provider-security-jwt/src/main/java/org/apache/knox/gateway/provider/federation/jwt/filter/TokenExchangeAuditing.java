/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.services.security.token.impl.JWT;

import java.util.Set;

/**
 * Emits the RFC 8693 token-exchange {@link Action#TOKEN_EXCHANGE} audit records for
 * {@link TokenExchangeHandler}. Centralizes the auditor, the resourceName, and the pre-mint message
 * format so the handler expresses only the outcome ({@link #allowed}, {@link #denied},
 * {@link #unavailable}) and never touches audit primitives or string formatting.
 *
 * <p>Every record carries the fields known before minting: the actor, the subject_token's issuer
 * and subject, the requested_subject (for delegation), the requested resources and whether they
 * were honored, and the incoming delegation chain depth. The fields only known at minting time
 * (issued_token_jti, issued_token_expiry, issued_subject) are emitted by the KNOXTOKEN service in a
 * complementary {@code token_exchange_minted} record; the two records correlate via the request's
 * audit correlation id.</p>
 */
final class TokenExchangeAuditing {

  // Non-final and package-private to allow test injection of a mock Auditor (see
  // DelegationPolicyResource.auditor for the identical, already-established pattern).
  static Auditor auditor = AuditServiceFactory.getAuditService()
      .getAuditor(AuditConstants.DEFAULT_AUDITOR_NAME,
          AuditConstants.KNOX_SERVICE_NAME, AuditConstants.KNOX_COMPONENT_NAME);

  /**
   * Audit an authorized exchange (same-subject or delegation).
   *
   * @param audiencesHonored whether the requested resources will be conveyed to the mint (false
   *                         when honoring is disabled and they were dropped, so a reader never
   *                         mistakes a listed-but-dropped {@code requested_resources} for honored)
   */
  void allowed(ActorIdentity actor, JWT subjectToken, String requestedSubject,
               Set<String> requestedResources, boolean audiencesHonored, int actChainDepth) {
    auditor.audit(Action.TOKEN_EXCHANGE, actor.resourceName(), ResourceType.PRINCIPAL,
        ActionOutcome.SUCCESS, message("token_exchange_allowed", null, actor, subjectToken,
            requestedSubject, requestedResources, audiencesHonored, actChainDepth));
  }

  /** Audit a policy/validation denial. Nothing is minted, so no audiences are honored. */
  void denied(ActorIdentity actor, JWT subjectToken, String requestedSubject, String denyReason,
              Set<String> requestedResources, int actChainDepth) {
    auditor.audit(Action.TOKEN_EXCHANGE, actor.resourceName(), ResourceType.PRINCIPAL,
        ActionOutcome.FAILURE, message("token_exchange_denied", "deny_reason=" + denyReason, actor,
            subjectToken, requestedSubject, requestedResources, false, actChainDepth));
  }

  /**
   * Audit a server-side inability to reach a decision (e.g. the LDAP group lookup a group-based
   * delegation policy needs is unavailable). Nothing is minted, so no audiences are honored.
   */
  void unavailable(ActorIdentity actor, JWT subjectToken, String requestedSubject, String reason,
                   Set<String> requestedResources, int actChainDepth) {
    auditor.audit(Action.TOKEN_EXCHANGE, actor.resourceName(), ResourceType.PRINCIPAL,
        ActionOutcome.UNAVAILABLE, message("token_exchange_unavailable", "reason=" + reason, actor,
            subjectToken, requestedSubject, requestedResources, false, actChainDepth));
  }

  /**
   * Builds the audit message for one exchange outcome. {@code eventType} is the {@code event_type}
   * value (token_exchange_allowed / token_exchange_denied / token_exchange_unavailable) and
   * {@code reasonField} is a fully-formed, pre-labeled reason token (e.g. {@code deny_reason=...})
   * appended verbatim, or null when there is none. {@code actChainDepth} is the depth of the
   * delegation history arriving on the subject_token (0 for a headless exchange, whose incoming
   * chain is not propagated).
   */
  private static String message(String eventType, String reasonField, ActorIdentity actor,
                                JWT subjectToken, String requestedSubject,
                                Set<String> requestedResources, boolean audiencesHonored,
                                int actChainDepth) {
    final StringBuilder message = new StringBuilder();
    message.append("event_type=").append(eventType);
    if (reasonField != null) {
      message.append(' ').append(reasonField);
    }
    message.append(" actor_authority=").append(actor.actorAuthority);
    message.append(" actor_id=").append(actor.actorId);
    message.append(" subject_token_iss=").append(label(subjectToken.getIssuer()));
    message.append(" subject_token_sub=").append(label(subjectToken.getSubject()));
    message.append(" requested_subject=").append(label(requestedSubject));
    message.append(" requested_resources=").append(requestedResources);
    message.append(" audiences_honored=").append(audiencesHonored);
    message.append(" act_chain_depth=").append(actChainDepth);
    return message.toString();
  }

  private static String label(String value) {
    return value != null ? value : "";
  }
}
