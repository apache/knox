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
package org.apache.knox.gateway.audit.api;

/**
 * Used to record audit events.
 */
public interface Auditor {

  /**
   * Records a single audit event.
   *
   * @param correlationContext The explicit correlation context to use when recording this audit event.  May not be null.
   * @param auditContext The explicit audit context to use when recording this audit event.  May not be null.
   * @param action The action being recorded for this audit event.  May not be null.
   * @param resourceName The resource identifier to record for this audit event.  May not be null.
   * @param resourceType The resource type to record for this audit event.  May not be null.
   * @param outcome The outcome to record for this audit event.  Typically the result of a authorization check.  May not be null.
   * @param message An arbitrary message to record with the audit event.  May be null.
   */
  void audit( CorrelationContext correlationContext, AuditContext auditContext, String action,
              String resourceName, String resourceType, String outcome, String message );

  /**
   * Records a single audit event using context information associated with the current thread.
   *
   * @param action The action being recorded for this audit event.  May not be null.
   * @param resourceName The resource identifier to record for this audit event.  May not be null.
   * @param resourceType The resource type to record for this audit event.  May not be null.
   * @param outcome The outcome to record for this audit event.  Typically the result of a authorization check.  May not be null.
   * @param message An arbitrary message to record with the audit event.  May be null.
   */
  void audit( String action, String resourceName, String resourceType, String outcome, String message );

  /**
   * Records a single audit event using context information associated with the current thread.
   *
   * @param action The action being recorded for this audit event.  May not be null.
   * @param resourceName The resource identifier to record for this audit event.  May not be null.
   * @param resourceType The resource type to record for this audit event.  May not be null.
   * @param outcome The outcome to record for this audit event.  Typically the result of a authorization check.  May not be null.
   */
  void audit( String action, String resourceName, String resourceType, String outcome );

  /**
   * The service name established when the Auditor was acquired.
   * Every event logged by auditor instance will contain data about service that generated event.
   *
   * @return The service name established when the Auditor was acquired.
   */
  String getServiceName();

  /**
   * The component name established when the Auditor was acquired.
   *
   * @return The component name established when the Auditor was acquired.
   */
  String getComponentName();

  /**
   * The auditor name established when the Auditor was acquired.
   * As an example, authentication/authorization operations may be logged to separate security log.
   * Or actions on some resources shouldn't be logged into central storage.
   * Auditor name provide an ability to logically group audit events, configure theirs filtration and  persistence
   *
   * @return The auditor name established when the Auditor was acquired.
   */
  String getAuditorName();

}