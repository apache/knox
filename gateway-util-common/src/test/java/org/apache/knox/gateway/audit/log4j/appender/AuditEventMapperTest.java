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

import static org.apache.knox.gateway.audit.log4j.appender.AuditEvents.event;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.junit.Test;

public class AuditEventMapperTest {
  static final String UUID_V7 = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";
  private static final String NAME_PATTERN = "^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$";
  private static final String SOURCE = "system:serviceaccount:knox:knox";
  private static final Set<String> TOP_LEVEL_KEYS = new HashSet<>(Arrays.asList(
      "schema_version", "event_id", "event_time", "source_system", "sub", "act", "action", "outcome",
      "verdict", "target_resource", "reason", "correlation_id", "context"));
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static JsonNode map(AuditEvents events, String podName) throws Exception {
    return MAPPER.readTree(AuditEventMapper.toJson(events.build(), SOURCE, podName));
  }

  @Test
  public void testFullDispatchEvent() throws Exception {
    JsonNode json = map(event(Action.DISPATCH, ResourceType.URI, "https://nn:9871/webhdfs/v1/tmp", ActionOutcome.SUCCESS)
        .users("alice", "bob", "knox")
        .remote("WEBHDFS", "10.0.0.1")
        .correlation("req-3", "req-2", "req-1")
        .message("Request method: GET"), "knox-0");

    assertEquals("1.0", json.get("schema_version").asText());
    assertTrue(json.get("event_id").asText().matches(UUID_V7));
    assertEquals("2025-11-17T12:00:00.123Z", json.get("event_time").asText());
    assertEquals(SOURCE, json.get("source_system").asText());
    assertEquals("bob", json.get("sub").asText());
    assertEquals("alice", json.get("act").get("sub").asText());
    assertEquals("data", json.at("/action/category").asText());
    assertEquals("execute", json.at("/action/verb").asText());
    assertEquals("uri.dispatch", json.at("/action/name").asText());
    assertEquals("success", json.get("outcome").asText());
    assertFalse(json.has("verdict"));
    assertEquals("https://nn:9871/webhdfs/v1/tmp", json.at("/target_resource/id").asText());
    assertEquals("uri", json.at("/target_resource/type").asText());
    assertEquals("Request method: GET", json.get("reason").asText());
    assertEquals("req-1", json.get("correlation_id").asText());
    assertEquals("knox", json.at("/context/knox.service").asText());
    assertEquals("knox", json.at("/context/knox.component").asText());
    assertEquals("WEBHDFS", json.at("/context/knox.target_service").asText());
    assertEquals("req-3", json.at("/context/knox.request_id").asText());
    assertEquals("knox-0", json.at("/context/k8s.pod.name").asText());
    assertFalse(json.toString().contains("10.0.0.1"));
    for (Iterator<String> it = json.fieldNames(); it.hasNext();) {
      String key = it.next();
      assertTrue("unexpected key " + key, TOP_LEVEL_KEYS.contains(key));
    }
  }

  @Test
  public void testAuthorizationDeniedHasVerdict() throws Exception {
    JsonNode json = map(event(Action.AUTHORIZATION, ResourceType.URI, "/gateway/sandbox/webhdfs", ActionOutcome.FAILURE)
        .users("alice", null, null), null);
    assertEquals("access", json.at("/action/category").asText());
    assertEquals("authorize", json.at("/action/verb").asText());
    assertEquals("failure", json.get("outcome").asText());
    assertEquals("denied", json.get("verdict").asText());
    assertEquals("alice", json.get("sub").asText());
    assertFalse(json.has("act"));
    assertFalse(json.has("reason"));
    assertNull(json.get("context").get("k8s.pod.name"));
  }

  @Test
  public void testVerdictAndOutcomeMapping() {
    assertEquals("allowed", AuditEventMapper.verdict(ActionOutcome.SUCCESS));
    assertEquals("denied", AuditEventMapper.verdict(ActionOutcome.FAILURE));
    assertEquals("indeterminate", AuditEventMapper.verdict(ActionOutcome.UNAVAILABLE));
    assertEquals("success", AuditEventMapper.outcome(Action.ACCESS, ActionOutcome.SUCCESS, null));
    assertEquals("failure", AuditEventMapper.outcome(Action.AUTHENTICATION, ActionOutcome.FAILURE, null));
    assertEquals("failure", AuditEventMapper.outcome("deserialize headers", ActionOutcome.WARNING, "Extra Headers are skipped"));
    assertEquals("unknown", AuditEventMapper.outcome(null, null, null));
  }

  @Test
  public void testUnavailableMapping() {
    assertEquals("initiated", AuditEventMapper.outcome(Action.ACCESS, ActionOutcome.UNAVAILABLE, "Request method: POST"));
    assertEquals("initiated", AuditEventMapper.outcome(Action.ACCESS, ActionOutcome.UNAVAILABLE, null));
    assertEquals("failure", AuditEventMapper.outcome(Action.ACCESS, ActionOutcome.UNAVAILABLE, "Response status: 503"));
    assertEquals("initiated", AuditEventMapper.outcome(Action.DISPATCH, ActionOutcome.UNAVAILABLE, "Request method: GET"));
    assertEquals("initiated", AuditEventMapper.outcome(Action.DEPLOY, ActionOutcome.UNAVAILABLE, null));
    assertEquals("initiated", AuditEventMapper.outcome(Action.REDEPLOY, ActionOutcome.UNAVAILABLE, null));
    assertEquals("initiated", AuditEventMapper.outcome(Action.UNDEPLOY, ActionOutcome.UNAVAILABLE, null));
    assertEquals("failure", AuditEventMapper.outcome(Action.TOKEN_EXCHANGE, ActionOutcome.UNAVAILABLE, "token_exchange_unavailable"));
  }

  @Test
  public void testRequestStartIsInitiated() throws Exception {
    JsonNode json = map(event(Action.ACCESS, ResourceType.URI, "/gateway/tokenissuer/knoxidf/api/v1/token", ActionOutcome.UNAVAILABLE)
        .message("Request method: POST"), null);
    assertEquals("initiated", json.get("outcome").asText());
    assertEquals("/gateway/tokenissuer/knoxidf/api/v1/token", json.at("/target_resource/id").asText());
  }

  @Test
  public void testActionTable() throws Exception {
    String[][] cases = {
        {Action.AUTHENTICATION, "access", "authenticate"},
        {Action.AUDIENCE_VALIDATION, "access", "authorize"},
        {Action.IDENTITY_MAPPING, "access", "execute"},
        {Action.TOKEN_EXCHANGE, "access", "create"},
        {Action.DELEGATION_LIFECYCLE, "access", "update"},
        {Action.WEBSHELL, "data", "execute"},
        {Action.ACCESS, "data", "read"},
        {Action.DEPLOY, "config", "create"},
        {Action.REDEPLOY, "config", "update"},
        {Action.UNDEPLOY, "config", "delete"},
        {Action.LOAD, "config", "read"},
        {"something-new", "config", "execute"}
    };
    for (String[] c : cases) {
      JsonNode json = map(event(c[0], ResourceType.TRUSTED_ISSUER, "issuer-1", ActionOutcome.SUCCESS), null);
      assertEquals(c[0], c[1], json.at("/action/category").asText());
      assertEquals(c[0], c[2], json.at("/action/verb").asText());
      assertTrue(c[0], json.at("/action/name").asText().matches(NAME_PATTERN));
      assertTrue(c[0], json.at("/action/name").asText().startsWith("trusted_issuer."));
    }
  }

  @Test
  public void testMissingContextsAndResource() throws Exception {
    JsonNode json = map(event(Action.AUTHENTICATION, null, null, ActionOutcome.SUCCESS), null);
    assertEquals(AuditEventMapper.ANONYMOUS, json.get("sub").asText());
    assertEquals("gateway.authentication", json.at("/action/name").asText());
    assertFalse(json.has("target_resource"));
    assertFalse(json.has("correlation_id"));
    assertFalse(json.has("act"));
  }

  @Test
  public void testSystemUsernameFallbackAndTruncation() throws Exception {
    StringBuilder longId = new StringBuilder();
    for (int i = 0; i < 300; i++) {
      longId.append('x');
    }
    StringBuilder longReason = new StringBuilder();
    for (int i = 0; i < 1100; i++) {
      longReason.append('r');
    }
    JsonNode json = map(event(Action.DEPLOY, ResourceType.TOPOLOGY, longId.toString(), ActionOutcome.SUCCESS)
        .users(null, null, "knox")
        .message(longReason.toString()), null);
    assertEquals("knox", json.get("sub").asText());
    assertEquals(256, json.at("/target_resource/id").asText().length());
    assertEquals(1024, json.get("reason").asText().length());
  }

  @Test
  public void testSnake() {
    assertEquals("identity_mapping", AuditEventMapper.snake("identity-mapping"));
    assertEquals("delegation_policy", AuditEventMapper.snake("Delegation-Policy"));
    assertEquals("a_b", AuditEventMapper.snake("9a b--"));
    assertNull(AuditEventMapper.snake("--"));
    assertNull(AuditEventMapper.snake(null));
  }
}
