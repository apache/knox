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
package org.apache.knox.gateway.services.knoxidf.flowstate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.ServletContext;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.util.knoxidf.AuthorizeRequestMetadata;
import org.apache.knox.gateway.util.knoxidf.FederatedOpLoginOption;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Typed, JSON-serialized access to the {@link AuthzFlowStateService}. A payload that cannot be
 * parsed is treated as absent, so the flow fails closed.
 */
public class AuthzFlowStateStore {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final AuthzFlowStateService service;

  public AuthzFlowStateStore(AuthzFlowStateService service) {
    this.service = service;
  }

  /**
   * Returns the store backed by the gateway's {@link AuthzFlowStateService}, or by a JVM-wide
   * in-memory one when there is none (e.g. in tests), so the KnoxIDF and KnoxSSO topologies still
   * share state.
   */
  public static AuthzFlowStateStore get(ServletContext servletContext) {
    AuthzFlowStateService service = null;
    final Object services = servletContext == null ? null : servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    if (services instanceof GatewayServices) {
      service = ((GatewayServices) services).getService(ServiceType.KNOXIDF_AUTHZ_FLOW_STATE_SERVICE);
    }
    return new AuthzFlowStateStore(service == null ? FallbackHolder.SERVICE : service);
  }

  public void putAuthorizeRequest(String id, AuthorizeRequestMetadata metadata) {
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, id, toJson(metadata));
  }

  public Optional<AuthorizeRequestMetadata> consumeAuthorizeRequest(String id) {
    return service.consume(AuthzFlowStateKind.AUTHORIZE_REQUEST, id).flatMap(AuthzFlowStateStore::authorizeRequestFromJson);
  }

  public void putFederatedOpOptions(String id, List<FederatedOpLoginOption> options) {
    service.put(AuthzFlowStateKind.FEDERATED_OP_OPTIONS, id, toJson(options));
  }

  public Optional<List<FederatedOpLoginOption>> getFederatedOpOptions(String id) {
    return service.get(AuthzFlowStateKind.FEDERATED_OP_OPTIONS, id).flatMap(AuthzFlowStateStore::federatedOpOptionsFromJson);
  }

  public void removeFederatedOpOptions(String id) {
    service.remove(AuthzFlowStateKind.FEDERATED_OP_OPTIONS, id);
  }

  public void putFederatedOpSelection(String id, FederatedOpSelection selection) {
    service.put(AuthzFlowStateKind.FEDERATED_OP_SELECTION, id, toJson(selection));
  }

  public Optional<FederatedOpSelection> consumeFederatedOpSelection(String id) {
    return service.consume(AuthzFlowStateKind.FEDERATED_OP_SELECTION, id).flatMap(AuthzFlowStateStore::federatedOpSelectionFromJson);
  }

  static String toJson(AuthorizeRequestMetadata metadata) {
    final ObjectNode node = MAPPER.createObjectNode();
    node.put("clientId", metadata.getClientId());
    node.put("subject", metadata.getSubject());
    node.put("responseType", metadata.getResponseType());
    node.put("redirectUri", metadata.getRedirectUri());
    final ArrayNode scopes = node.putArray("scopes");
    if (metadata.getRequestedScopes() != null) {
      metadata.getRequestedScopes().forEach(scopes::add);
    }
    node.put("state", metadata.getState());
    node.put("nonce", metadata.getNonce());
    node.put("codeChallenge", metadata.getCodeChallenge());
    node.put("codeChallengeMethod", metadata.getCodeChallengeMethod());
    return node.toString();
  }

  static Optional<AuthorizeRequestMetadata> authorizeRequestFromJson(String json) {
    try {
      final JsonNode node = MAPPER.readTree(json);
      if (node == null || !node.isObject()) {
        return Optional.empty();
      }
      final Set<String> scopes = new LinkedHashSet<>();
      node.path("scopes").forEach(scope -> scopes.add(scope.asText()));
      return Optional.of(new AuthorizeRequestMetadata(text(node, "clientId"), text(node, "subject"),
          text(node, "responseType"), text(node, "redirectUri"), scopes, text(node, "state"), text(node, "nonce"),
          text(node, "codeChallenge"), text(node, "codeChallengeMethod")));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  static String toJson(List<FederatedOpLoginOption> options) {
    final ArrayNode array = MAPPER.createArrayNode();
    for (FederatedOpLoginOption option : options) {
      array.addObject()
          .put("name", option.getName())
          .put("clientId", option.getClientId())
          .put("authorizeEndpoint", option.getAuthorizeEndpoint())
          .put("authorizeCallback", option.getAuthorizeCallback());
    }
    return array.toString();
  }

  static Optional<List<FederatedOpLoginOption>> federatedOpOptionsFromJson(String json) {
    try {
      final JsonNode node = MAPPER.readTree(json);
      if (node == null || !node.isArray()) {
        return Optional.empty();
      }
      final List<FederatedOpLoginOption> options = new ArrayList<>();
      for (JsonNode option : node) {
        options.add(new FederatedOpLoginOption(text(option, "name"), text(option, "clientId"),
            text(option, "authorizeEndpoint"), text(option, "authorizeCallback")));
      }
      return Optional.of(Collections.unmodifiableList(options));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  static String toJson(FederatedOpSelection selection) {
    return MAPPER.createObjectNode()
        .put("opName", selection.opName())
        .put("nonce", selection.nonce())
        .toString();
  }

  static Optional<FederatedOpSelection> federatedOpSelectionFromJson(String json) {
    try {
      final JsonNode node = MAPPER.readTree(json);
      if (node == null || !node.isObject() || text(node, "opName") == null) {
        return Optional.empty();
      }
      return Optional.of(new FederatedOpSelection(text(node, "opName"), text(node, "nonce")));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private static String text(JsonNode node, String field) {
    final JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  private static final class FallbackHolder {
    private static final AuthzFlowStateService SERVICE = new InMemoryAuthzFlowStateService();
  }
}
