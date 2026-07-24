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
package org.apache.knox.gateway.service.knoxidf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.TrustedOidcIssuer;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.TrustedOidcIssuerService;
import org.apache.knox.gateway.util.JsonUtils;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path(TrustedOidcIssuersResource.RESOURCE_PATH)
@Produces(MediaType.APPLICATION_JSON)
public class TrustedOidcIssuersResource {

  static final String RESOURCE_PATH = "knoxidf/issuers-admin/v1/trusted-oidc-issuers";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  // Non-final and package-private to allow test injection of a mock Auditor.
  static Auditor auditor = AuditServiceFactory.getAuditService()
      .getAuditor(AuditConstants.DEFAULT_AUDITOR_NAME,
          AuditConstants.KNOX_SERVICE_NAME, AuditConstants.KNOX_COMPONENT_NAME);

  @Context
  private ServletContext servletContext;

  @Context
  private HttpServletRequest request;

  private TrustedOidcIssuerService trustedIssuers;

  @PostConstruct
  public void init() {
    final GatewayServices services = (GatewayServices)
        servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    trustedIssuers = services.getService(ServiceType.TRUSTED_OIDC_ISSUER_SERVICE);
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response registerIssuer(String body) {
    String issuerUrl = "INVALID_REQUEST";
    final String operatorId = getOperatorId();
    String outcome = ActionOutcome.FAILURE;

    try {
      final Map<String, Object> parsed;
      try {
        parsed = MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {});
      } catch (IOException e) {
        return errorResponse(Response.Status.BAD_REQUEST, "invalid_request", "Malformed JSON body");
      }

      final String rawUrl = (String) parsed.get("issuerUrl");
      issuerUrl = (rawUrl != null && !rawUrl.isEmpty()) ? rawUrl : "UNKNOWN_ISSUER";

      if (rawUrl == null || rawUrl.isEmpty()) {
        return errorResponse(Response.Status.BAD_REQUEST, "invalid_request", "issuerUrl is required");
      }
      if (!isHttpsUrl(rawUrl)) {
        return errorResponse(Response.Status.BAD_REQUEST, "invalid_request",
            "issuerUrl must use HTTPS scheme");
      }
      if (trustedIssuers.isTrusted(rawUrl)) {
        return errorResponse(Response.Status.CONFLICT, "issuer_exists",
            "Issuer already registered: " + rawUrl);
      }

      final boolean dynamicJwks = Boolean.TRUE.equals(parsed.get("dynamicJwks"));
      final String clusterName = (String) parsed.get("clusterName");

      trustedIssuers.register(new TrustedOidcIssuer(rawUrl, dynamicJwks, clusterName,
          Instant.now(), operatorId));
      outcome = ActionOutcome.SUCCESS;
      return Response.status(Response.Status.CREATED).build();
    } catch (RuntimeException e) {
      return errorResponse(Response.Status.INTERNAL_SERVER_ERROR, "storage_error",
          "Failed to register issuer");
    } finally {
      auditor.audit(Action.DELEGATION_LIFECYCLE, issuerUrl, ResourceType.TRUSTED_ISSUER,
          outcome, "event_type=issuer_registered performed_by=" + auditLabel(operatorId));
    }
  }

  @DELETE
  public Response removeIssuer(@QueryParam("issuerUrl") String issuerUrl) {
    final String operatorId = getOperatorId();
    final String auditIssuerUrl = StringUtils.isBlank(issuerUrl) ? "UNKNOWN_ISSUER" : issuerUrl;
    String outcome = ActionOutcome.FAILURE;

    try {
      if (StringUtils.isBlank(issuerUrl)) {
        return errorResponse(Response.Status.BAD_REQUEST, "invalid_request",
            "issuerUrl query parameter is required");
      }

      // deregister is idempotent at the service layer: it returns silently if the issuer is
      // not registered. Admins deleting a non-existent issuer receive the same 204 and audit
      // event as a successful delete — there is no separate 404 path at this layer.
      trustedIssuers.deregister(issuerUrl);
      outcome = ActionOutcome.SUCCESS;
      return Response.noContent().build();
    } catch (RuntimeException e) {
      return errorResponse(Response.Status.INTERNAL_SERVER_ERROR, "storage_error",
          "Failed to remove issuer");
    } finally {
      auditor.audit(Action.DELEGATION_LIFECYCLE, auditIssuerUrl, ResourceType.TRUSTED_ISSUER,
          outcome, "event_type=issuer_removed performed_by=" + auditLabel(operatorId));
    }
  }

  @GET
  public Response listIssuers() {
    final List<Map<String, Object>> result = trustedIssuers.list().stream()
        .map(this::issuerToMap)
        .collect(Collectors.toList());
    return Response.ok(JsonUtils.renderAsJsonString(result)).build();
  }

  @POST
  @Path("/refresh-jwks")
  public Response refreshJwksUri(@QueryParam("issuerUrl") String issuerUrl) {
    final String operatorId = getOperatorId();
    final String auditIssuerUrl = StringUtils.isBlank(issuerUrl) ? "UNKNOWN_ISSUER" : issuerUrl;
    String outcome = ActionOutcome.FAILURE;

    try {
      if (StringUtils.isBlank(issuerUrl)) {
        return errorResponse(Response.Status.BAD_REQUEST, "invalid_request",
            "issuerUrl query parameter is required");
      }

      // No-op at the service layer if the issuer is not registered or not configured for
      // dynamic JWKS; still returns 204 so the caller does not need to check existence first.
      trustedIssuers.refreshJwksUri(issuerUrl);
      outcome = ActionOutcome.SUCCESS;
      return Response.noContent().build();
    } catch (RuntimeException e) {
      return errorResponse(Response.Status.INTERNAL_SERVER_ERROR, "storage_error",
          "Failed to refresh JWKS URI");
    } finally {
      auditor.audit(Action.DELEGATION_LIFECYCLE, auditIssuerUrl, ResourceType.TRUSTED_ISSUER,
          outcome, "event_type=issuer_jwks_refreshed performed_by=" + auditLabel(operatorId));
    }
  }

  private String getOperatorId() {
    final Principal principal = request.getUserPrincipal();
    return principal != null ? principal.getName() : null;
  }

  private static String auditLabel(String operatorId) {
    return operatorId != null ? operatorId : "ANONYMOUS";
  }

  private Map<String, Object> issuerToMap(TrustedOidcIssuer issuer) {
    final Map<String, Object> map = new LinkedHashMap<>();
    map.put("issuerUrl", issuer.getIssuerUrl());
    map.put("dynamicJwks", issuer.isDynamicJwks());
    map.put("clusterName", issuer.getClusterName());
    map.put("registeredAt",
        issuer.getRegisteredAt() != null ? issuer.getRegisteredAt().toString() : null);
    map.put("registeredBy", issuer.getRegisteredBy());
    return map;
  }

  private static boolean isHttpsUrl(String url) {
    try {
      return "https".equalsIgnoreCase(new URI(url).getScheme());
    } catch (URISyntaxException e) {
      return false;
    }
  }

  private static Response errorResponse(Response.Status status, String error, String description) {
    final Map<String, String> body = new LinkedHashMap<>();
    body.put("error", error);
    body.put("error_description", description);
    return Response.status(status).entity(JsonUtils.renderAsJsonString(body)).build();
  }
}
