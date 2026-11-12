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

import java.util.ArrayList;
import java.util.List;

import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.CorrelationContext;

/**
 * An {@link Auditor} that keeps every record it is handed, for asserting on the audit trail of a
 * request.
 */
final class RecordingAuditor implements Auditor {

  record Entry(String action, String resourceName, String resourceType, String outcome, String message) {
  }

  final List<Entry> entries = new ArrayList<>();

  @Override
  public void audit(final CorrelationContext correlationContext, final AuditContext auditContext,
      final String action, final String resourceName, final String resourceType, final String outcome,
      final String message) {
    audit(action, resourceName, resourceType, outcome, message);
  }

  @Override
  public void audit(final String action, final String resourceName, final String resourceType,
      final String outcome, final String message) {
    entries.add(new Entry(action, resourceName, resourceType, outcome, message));
  }

  @Override
  public void audit(final String action, final String resourceName, final String resourceType,
      final String outcome) {
    audit(action, resourceName, resourceType, outcome, null);
  }

  @Override
  public String getServiceName() {
    return "test";
  }

  @Override
  public String getComponentName() {
    return "test";
  }

  @Override
  public String getAuditorName() {
    return "test";
  }
}
