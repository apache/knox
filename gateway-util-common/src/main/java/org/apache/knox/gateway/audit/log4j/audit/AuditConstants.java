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
package org.apache.knox.gateway.audit.log4j.audit;

public class AuditConstants {
  //resource
  public static final String MDC_RESOURCE_TYPE_KEY = "resource_type";
  public static final String MDC_RESOURCE_NAME_KEY = "resource_name";

  //Action details
  public static final String MDC_ACTION_KEY = "action";
  public static final String MDC_OUTCOME_KEY = "outcome";

  public static final String MDC_SERVICE_KEY = "service_name";
  public static final String MDC_COMPONENT_KEY = "component_name";

  public static final String DEFAULT_AUDITOR_NAME = "audit";
  public static final String KNOX_SERVICE_NAME = "knox";
  public static final String KNOX_COMPONENT_NAME = "knox";
}
