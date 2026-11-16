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
import org.apache.knox.gateway.util.knoxidf.AllowedGrantTypes;

/**
 * Audits a token request rejected by {@link JWTFederationFilter#ALLOWED_GRANT_TYPES}.
 */
final class GrantTypeAuditing {

  // Non-final to allow test injection of a mock Auditor.
  static Auditor auditor = AuditServiceFactory.getAuditService().getAuditor(AuditConstants.DEFAULT_AUDITOR_NAME,
          AuditConstants.KNOX_SERVICE_NAME, AuditConstants.KNOX_COMPONENT_NAME);

  private GrantTypeAuditing() {
  }

  static void rejected(final HttpServletRequest request, final String grantType) {
    auditor.audit(Action.AUTHENTICATION, DelegatedRequestAuditing.resourceName(request), ResourceType.URI,
        ActionOutcome.FAILURE,
        "event=token_grant grant_type=" + AllowedGrantTypes.auditName(grantType) + " reason=grant_type_not_allowed");
  }
}
