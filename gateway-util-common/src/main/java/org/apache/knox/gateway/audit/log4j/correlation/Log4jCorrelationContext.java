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
package org.apache.knox.gateway.audit.log4j.correlation;

import static org.apache.knox.gateway.audit.log4j.correlation.Log4jCorrelationService.KEY_PARENT_REQUEST_ID;
import static org.apache.knox.gateway.audit.log4j.correlation.Log4jCorrelationService.KEY_REQUEST_ID;
import static org.apache.knox.gateway.audit.log4j.correlation.Log4jCorrelationService.KEY_ROOT_REQUEST_ID;

import java.util.Map;
import java.util.UUID;

import org.apache.knox.gateway.audit.api.CorrelationContext;
import org.apache.logging.log4j.core.LogEvent;

public class Log4jCorrelationContext implements CorrelationContext {
  private final String requestId;
  private final String parentRequestId;
  private final String rootRequestId;

  public static Log4jCorrelationContext of(LogEvent event) {
    if (event == null) {
      return null;
    }
    Map<String, String> data = event.getContextData().toMap();
    return data.containsKey(KEY_REQUEST_ID)
        ? new Log4jCorrelationContext(data.get(KEY_REQUEST_ID), data.get(KEY_PARENT_REQUEST_ID), data.get(KEY_ROOT_REQUEST_ID))
        : null;
  }

  public static Log4jCorrelationContext random() {
    return new Log4jCorrelationContext(
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString());
  }

  public Log4jCorrelationContext( String requestId, String parentRequestId,
      String rootRequestId ) {
    this.requestId = requestId;
    this.parentRequestId = parentRequestId;
    this.rootRequestId = rootRequestId;
  }

  @Override
  public String getRequestId() {
    return requestId;
  }

  @Override
  public String getParentRequestId() {
    return parentRequestId;
  }

  @Override
  public String getRootRequestId() {
    return rootRequestId;
  }

  @Override
  public String toString() {
    return "[" +
               "request_id=" + requestId +
               ", parent_request_id=" + parentRequestId +
               ", root_request_id=" + rootRequestId +
               "]";
  }
}
