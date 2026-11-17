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
package org.apache.knox.gateway.audit.log4j.appender;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.CorrelationContext;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.audit.log4j.audit.Log4jAuditContext;
import org.apache.knox.gateway.audit.log4j.correlation.Log4jCorrelationContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

public final class AuditEventMapper {
  static final String SCHEMA_VERSION = "1.0";
  static final String ANONYMOUS = "anonymous";
  static final String DEFAULT_RESOURCE = "gateway";

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int MAX_ID = 256;
  private static final int MAX_TYPE = 64;
  private static final int MAX_NAME = 128;
  private static final int MAX_REASON = 1024;
  private static final int MAX_CORRELATION_ID = 128;
  private static final String RESPONSE_STATUS_PREFIX = "Response status";
  private static final String[] DEFAULT_ACTION = {"config", "execute"};
  private static final Map<String, String[]> ACTIONS = new HashMap<>();

  static {
    ACTIONS.put(Action.AUTHENTICATION, new String[]{"access", "authenticate"});
    ACTIONS.put(Action.AUTHORIZATION, new String[]{"access", "authorize"});
    ACTIONS.put(Action.AUDIENCE_VALIDATION, new String[]{"access", "authorize"});
    ACTIONS.put(Action.IDENTITY_MAPPING, new String[]{"access", "execute"});
    ACTIONS.put(Action.TOKEN_EXCHANGE, new String[]{"access", "create"});
    ACTIONS.put(Action.DELEGATION_LIFECYCLE, new String[]{"access", "update"});
    ACTIONS.put(Action.DISPATCH, new String[]{"data", "execute"});
    ACTIONS.put(Action.WEBSHELL, new String[]{"data", "execute"});
    ACTIONS.put(Action.ACCESS, new String[]{"data", "read"});
    ACTIONS.put(Action.DEPLOY, new String[]{"config", "create"});
    ACTIONS.put(Action.REDEPLOY, new String[]{"config", "update"});
    ACTIONS.put(Action.UNDEPLOY, new String[]{"config", "delete"});
    ACTIONS.put(Action.LOAD, new String[]{"config", "read"});
  }

  private AuditEventMapper() {
  }

  public static String toJson(LogEvent event, String sourceSystem, String podName) {
    ReadOnlyStringMap data = event.getContextData();
    AuditContext ac = Log4jAuditContext.of(event);
    CorrelationContext cc = Log4jCorrelationContext.of(event);

    String knoxAction = data.getValue(AuditConstants.MDC_ACTION_KEY);
    String knoxOutcome = data.getValue(AuditConstants.MDC_OUTCOME_KEY);
    String resourceType = data.getValue(AuditConstants.MDC_RESOURCE_TYPE_KEY);
    String resourceName = data.getValue(AuditConstants.MDC_RESOURCE_NAME_KEY);
    String[] categoryVerb = knoxAction == null ? DEFAULT_ACTION : ACTIONS.getOrDefault(knoxAction, DEFAULT_ACTION);
    String type = snake(resourceType);

    ObjectNode root = MAPPER.createObjectNode();
    root.put("schema_version", SCHEMA_VERSION);
    root.put("event_id", UuidV7.next().toString());
    root.put("event_time", DateTimeFormatter.ISO_INSTANT.format(
        Instant.ofEpochMilli(event.getInstant().getEpochMillisecond())));
    root.put("source_system", truncate(sourceSystem, MAX_ID));

    String username = ac == null ? null : blankToNull(ac.getUsername());
    String proxyUsername = ac == null ? null : blankToNull(ac.getProxyUsername());
    String systemUsername = ac == null ? null : blankToNull(ac.getSystemUsername());
    root.put("sub", truncate(firstNonNull(proxyUsername, username, systemUsername, ANONYMOUS), MAX_ID));
    if (proxyUsername != null && username != null && !proxyUsername.equals(username)) {
      root.putObject("act").put("sub", truncate(username, MAX_ID));
    }

    ObjectNode action = root.putObject("action");
    action.put("category", categoryVerb[0]);
    action.put("verb", categoryVerb[1]);
    String operation = snake(knoxAction);
    action.put("name", truncate((type == null ? DEFAULT_RESOURCE : type) + "."
        + (operation == null ? "unknown" : operation), MAX_NAME));

    String message = event.getMessage() == null ? null : blankToNull(event.getMessage().getFormattedMessage());
    if ("null".equals(message)) {
      message = null;
    }

    root.put("outcome", outcome(knoxAction, knoxOutcome, message));
    if ("authorize".equals(categoryVerb[1])) {
      root.put("verdict", verdict(knoxOutcome));
    }

    String id = blankToNull(resourceName);
    if (id != null && type != null) {
      ObjectNode target = root.putObject("target_resource");
      target.put("id", truncate(id, MAX_ID));
      target.put("type", truncate(type, MAX_TYPE));
    }

    if (message != null) {
      root.put("reason", truncate(message, MAX_REASON));
    }

    if (cc != null) {
      String correlationId = firstNonNull(blankToNull(cc.getRootRequestId()), blankToNull(cc.getRequestId()));
      if (correlationId != null) {
        root.put("correlation_id", truncate(correlationId, MAX_CORRELATION_ID));
      }
    }

    ObjectNode context = MAPPER.createObjectNode();
    putIfPresent(context, "knox.service", data.getValue(AuditConstants.MDC_SERVICE_KEY));
    putIfPresent(context, "knox.component", data.getValue(AuditConstants.MDC_COMPONENT_KEY));
    putIfPresent(context, "knox.target_service", ac == null ? null : ac.getTargetServiceName());
    putIfPresent(context, "knox.request_id", cc == null ? null : cc.getRequestId());
    putIfPresent(context, "k8s.pod.name", podName);
    if (!context.isEmpty()) {
      root.set("context", context);
    }

    return root.toString();
  }

  static String outcome(String knoxAction, String knoxOutcome, String message) {
    if (ActionOutcome.SUCCESS.equals(knoxOutcome)) {
      return "success";
    }
    if (ActionOutcome.FAILURE.equals(knoxOutcome) || ActionOutcome.WARNING.equals(knoxOutcome)) {
      return "failure";
    }
    if (ActionOutcome.UNAVAILABLE.equals(knoxOutcome)) {
      if (Action.TOKEN_EXCHANGE.equals(knoxAction)
          || (Action.ACCESS.equals(knoxAction) && message != null && message.startsWith(RESPONSE_STATUS_PREFIX))) {
        return "failure";
      }
      return "initiated";
    }
    return "unknown";
  }

  static String verdict(String knoxOutcome) {
    if (ActionOutcome.SUCCESS.equals(knoxOutcome)) {
      return "allowed";
    }
    if (ActionOutcome.FAILURE.equals(knoxOutcome)) {
      return "denied";
    }
    return "indeterminate";
  }

  static String snake(String value) {
    if (value == null) {
      return null;
    }
    String snake = value.toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "_")
        .replaceAll("^[^a-z]+", "")
        .replaceAll("_+$", "");
    return snake.isEmpty() ? null : snake;
  }

  private static void putIfPresent(ObjectNode node, String key, String value) {
    String v = blankToNull(value);
    if (v != null) {
      node.put(key, truncate(v, MAX_ID));
    }
  }

  private static String blankToNull(String value) {
    return StringUtils.isBlank(value) ? null : value;
  }

  private static String firstNonNull(String... values) {
    for (String value : values) {
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private static String truncate(String value, int max) {
    if (value == null || value.length() <= max) {
      return value;
    }
    return value.substring(0, max);
  }
}
