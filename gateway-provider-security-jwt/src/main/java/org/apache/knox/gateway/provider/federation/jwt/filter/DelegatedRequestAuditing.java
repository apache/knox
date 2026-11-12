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

import jakarta.servlet.http.HttpServletRequest;

import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.util.Tokens;

/**
 * Audit records for requests presenting a delegated token, i.e. one carrying an RFC 8693 {@code act}
 * claim. The claims are read before the signature is verified ({@code claims_verified=false}), so
 * every written value is sanitized and quoted where needed. No token material is written.
 */
final class DelegatedRequestAuditing {

  static final String DESTINATION_ATTRIBUTE = DelegatedRequestAuditing.class.getName() + ".destination";

  static final int MAX_VALUE_LENGTH = 512;

  // Non-final to allow test injection of a mock Auditor.
  static Auditor auditor = AuditServiceFactory.getAuditService()
      .getAuditor(AuditConstants.DEFAULT_AUDITOR_NAME,
          AuditConstants.KNOX_SERVICE_NAME, AuditConstants.KNOX_COMPONENT_NAME);

  private DelegatedRequestAuditing() {
  }

  /**
   * Raw destination header values: {@code ""} if the header is configured but absent, {@code null}
   * if it is not configured.
   */
  record Destination(String service, String spiffeId, String path) {
  }

  static boolean isDelegated(final JWT token) {
    return token != null && token.getClaimAsObject(JWTToken.ACT_CLAIM) != null;
  }

  static void audienceChecked(final HttpServletRequest request, final JWT token, final Destination destination,
      final AudienceValidationResult result) {
    request.setAttribute(DESTINATION_ATTRIBUTE, destination);
    final StringBuilder message = commonFields("delegated_audience_check", token);
    appendDestination(message, destination);
    if (!result.isValid()) {
      appendReason(message, "audience_invalid", result.message());
    }
    auditor.audit(Action.AUDIENCE_VALIDATION, resourceName(request), ResourceType.URI,
        result.isValid() ? ActionOutcome.SUCCESS : ActionOutcome.FAILURE, message.toString());
  }

  static void rejected(final HttpServletRequest request, final JWT token, final String reason, final String detail) {
    if (!isDelegated(token)) {
      return;
    }
    final StringBuilder message = commonFields("delegated_request_rejected", token);
    final Destination destination = recordedDestination(request);
    appendDestination(message, destination != null ? destination
        : new Destination(null, null, request.getRequestURI()));
    appendReason(message, reason, detail);
    auditor.audit(Action.AUTHENTICATION, resourceName(request), ResourceType.URI, ActionOutcome.FAILURE,
        message.toString());
  }

  static String destinationFields(final HttpServletRequest request) {
    final Destination destination = recordedDestination(request);
    if (destination == null) {
      return "";
    }
    final StringBuilder fields = new StringBuilder();
    appendDestination(fields, destination);
    return fields.toString();
  }

  static String field(final String name, final String value) {
    return name + '=' + quoteIfNeeded(displayText(value));
  }

  /** Caps the length and replaces line-breaking characters, so a value cannot flood or forge log lines. */
  static String displayText(final String value) {
    if (value == null) {
      return "";
    }
    int end = Math.min(value.length(), MAX_VALUE_LENGTH);
    if (end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))) {
      end--;
    }
    final StringBuilder displayText = new StringBuilder(end + 3);
    value.substring(0, end).codePoints()
        .forEach(c -> displayText.appendCodePoint(isLineBreaking(c) ? '?' : c));
    if (end < value.length()) {
      displayText.append("...");
    }
    return displayText.toString();
  }

  private static boolean isLineBreaking(final int codePoint) {
    final int type = Character.getType(codePoint);
    return Character.isISOControl(codePoint)
        || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
  }

  // So that, e.g., a sub of "alice result=accepted" cannot be read as two fields.
  private static String quoteIfNeeded(final String value) {
    if (value.chars().noneMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '"')) {
      return value;
    }
    return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
  }

  private static StringBuilder commonFields(final String eventType, final JWT token) {
    final StringBuilder message = new StringBuilder();
    message.append("event_type=").append(eventType).append(" claims_verified=false ")
        .append(field("token_id", Tokens.getTokenIDDisplayText(TokenUtils.getTokenId(token))));
    message.append(' ').append(field("sub", token.getSubject()));
    message.append(' ').append(field("act_chain", SubjectUtils.renderActorChain(TokenUtils.extractActorChain(token))));
    return message;
  }

  private static void appendDestination(final StringBuilder message, final Destination destination) {
    if (destination.service() != null) {
      message.append(' ').append(field("dest_service", destination.service()));
    }
    if (destination.spiffeId() != null) {
      message.append(' ').append(field("dest_spiffe_id", destination.spiffeId()));
    }
    message.append(' ').append(field("path", destination.path()));
  }

  private static void appendReason(final StringBuilder message, final String reason, final String detail) {
    message.append(' ').append(field("reason", reason));
    if (detail != null) {
      message.append(' ').append(field("detail", detail));
    }
  }

  private static Destination recordedDestination(final HttpServletRequest request) {
    final Object destination = request.getAttribute(DESTINATION_ATTRIBUTE);
    return destination instanceof Destination recorded ? recorded : null;
  }

  // The query string is dropped as it may carry credentials.
  private static String resourceName(final HttpServletRequest request) {
    final Object sourceUri = request.getAttribute(AbstractGatewayFilter.SOURCE_REQUEST_CONTEXT_URL_ATTRIBUTE_NAME);
    return displayText(withoutQuery(sourceUri instanceof String uri ? uri : request.getRequestURI()));
  }

  static String withoutQuery(final String uri) {
    if (uri == null) {
      return null;
    }
    final int queryOrFragment = AudienceResource.indexOfFirst(uri, 0, '?', '#');
    return queryOrFragment < 0 ? uri : uri.substring(0, queryOrFragment);
  }
}
