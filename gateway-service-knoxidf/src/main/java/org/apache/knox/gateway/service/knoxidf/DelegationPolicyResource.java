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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.knoxidf.delegation.DelegationPolicy;
import org.apache.knox.gateway.services.knoxidf.delegation.DelegationPolicyAlreadyExistsException;
import org.apache.knox.gateway.services.knoxidf.delegation.DelegationPolicyList;
import org.apache.knox.gateway.services.knoxidf.delegation.DelegationPolicyNotFoundException;
import org.apache.knox.gateway.services.knoxidf.delegation.DelegationPolicyService;
import org.apache.knox.gateway.services.knoxidf.delegation.RegisterOrUpdateResult;
import org.apache.knox.gateway.util.JsonUtils;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.security.Principal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Path(DelegationPolicyResource.RESOURCE_PATH)
@Produces(MediaType.APPLICATION_JSON)
public class DelegationPolicyResource {

  static final String RESOURCE_PATH = "knoxidf/admin/v1/delegation-policies";

  static final String MIN_TOKEN_TTL_SEC_PARAM = "knox.delegation.min.token.ttl.sec";
  static final String MAX_TOKEN_TTL_SEC_PARAM = "knox.delegation.max.token.ttl.sec";
  static final int DEFAULT_MIN_TOKEN_TTL_SEC = 60;      // 1 minute
  static final int DEFAULT_MAX_TOKEN_TTL_SEC = 86400;   // 24 hours

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  // Non-final and package-private to allow test injection of a mock Auditor.
  static Auditor auditor = AuditServiceFactory.getAuditService()
      .getAuditor(AuditConstants.DEFAULT_AUDITOR_NAME,
          AuditConstants.KNOX_SERVICE_NAME, AuditConstants.KNOX_COMPONENT_NAME);

  @Context
  private ServletContext servletContext;

  @Context
  private HttpServletRequest request;

  private DelegationPolicyService policyService;

  private int minTokenTtlSec =  DEFAULT_MIN_TOKEN_TTL_SEC;
  private int maxTokenTtlSec =   DEFAULT_MAX_TOKEN_TTL_SEC;

  @PostConstruct
  public void init() {
    final GatewayServices services = (GatewayServices)
        servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    policyService = services.getService(ServiceType.DELEGATION_POLICY_SERVICE);
    configureTtlBounds();
  }

  private void configureTtlBounds() {
    minTokenTtlSec = readPositiveIntParam(MIN_TOKEN_TTL_SEC_PARAM, DEFAULT_MIN_TOKEN_TTL_SEC);
    maxTokenTtlSec = readPositiveIntParam(MAX_TOKEN_TTL_SEC_PARAM, DEFAULT_MAX_TOKEN_TTL_SEC);
    if (minTokenTtlSec > maxTokenTtlSec) {
      throw new IllegalStateException("Invalid delegation policy TTL bounds: "
              + MIN_TOKEN_TTL_SEC_PARAM + " (" + minTokenTtlSec + ") must not exceed "
              + MAX_TOKEN_TTL_SEC_PARAM + " (" + maxTokenTtlSec + ")");
    }

    final int configuredDefaultTtlSec = policyService.getConfiguredTokenTtlSec();
    if (configuredDefaultTtlSec < minTokenTtlSec || configuredDefaultTtlSec > maxTokenTtlSec) {
      throw new IllegalStateException("Configured default delegation token TTL ("
              + configuredDefaultTtlSec + "s) is outside the enforced bounds [" + minTokenTtlSec + ", "
              + maxTokenTtlSec + "]; policies without an explicit tokenTtlSec would receive an "
              + "out-of-range effective TTL");
    }
  }

  private int readPositiveIntParam(String paramName, int defaultValue) {
    final String raw = servletContext.getInitParameter(paramName);
    if (StringUtils.isBlank(raw)) {
      return defaultValue;
    }
    final int value;
    try {
      value = Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      throw new IllegalStateException("Invalid value for " + paramName + ": \"" + raw
          + "\" is not an integer");
    }
    if (value <= 0) {
      throw new IllegalStateException("Invalid value for " + paramName + ": " + value
          + " must be a positive number of seconds");
    }
    return value;
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response register(String body) {
    final String operatorId = getOperatorId();
    String auditId = "INVALID_REQUEST";
    String outcome = ActionOutcome.FAILURE;

    try {
      final DelegationPolicyRequest parsed;
      try {
        parsed = MAPPER.readValue(body, DelegationPolicyRequest.class);
      } catch (IOException e) {
        return errorResponse(Response.Status.BAD_REQUEST, "invalid_request", "Malformed or invalid JSON body");
      }
      auditId = bestEffortActorIdForAudit(parsed);

      final Response validationError = validateRequest(parsed);
      if (validationError != null) {
        return validationError;
      }

      final Instant now = Instant.now();
      final DelegationPolicy toStore = toDomain(null, parsed, operatorId, now, now);
      final DelegationPolicy stored = policyService.register(toStore);
      auditId = stored.getRegistrationId();
      outcome = ActionOutcome.SUCCESS;
      return Response.status(Response.Status.CREATED).entity(writeJson(toResponse(stored))).build();
    } catch (DelegationPolicyAlreadyExistsException e) {
      return errorResponse(Response.Status.CONFLICT, "actor_exists", e.getMessage());
    } catch (RuntimeException e) {
      return errorResponse(Response.Status.INTERNAL_SERVER_ERROR, "storage_error", "Failed to register delegation policy");
    } finally {
      auditor.audit(Action.DELEGATION_LIFECYCLE, auditId, ResourceType.DELEGATION_POLICY,
          outcome, "event_type=policy_registered performed_by=" + auditLabel(operatorId));
    }
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  public Response registerOrUpdate(String body) {
    final String operatorId = getOperatorId();
    String auditId = "INVALID_REQUEST";
    String outcome = ActionOutcome.FAILURE;
    String eventType = "policy_registered";

    try {
      final DelegationPolicyRequest parsed;
      try {
        parsed = MAPPER.readValue(body, DelegationPolicyRequest.class);
      } catch (IOException e) {
        return errorResponse(Response.Status.BAD_REQUEST, "invalid_request", "Malformed or invalid JSON body");
      }
      auditId = bestEffortActorIdForAudit(parsed);

      final Response validationError = validateRequest(parsed);
      if (validationError != null) {
        return validationError;
      }

      final Instant now = Instant.now();
      final DelegationPolicy toStore = toDomain(null, parsed, operatorId, now, now);
      final RegisterOrUpdateResult result = policyService.registerOrUpdate(toStore);
      auditId = result.getPolicy().getRegistrationId();
      eventType = result.isCreated() ? "policy_registered" : "policy_updated";
      outcome = ActionOutcome.SUCCESS;
      final Response.Status status = result.isCreated() ? Response.Status.CREATED : Response.Status.OK;
      return Response.status(status).entity(writeJson(toResponse(result.getPolicy()))).build();
    } catch (DelegationPolicyAlreadyExistsException e) {
      return errorResponse(Response.Status.CONFLICT, "actor_exists", e.getMessage());
    } catch (DelegationPolicyNotFoundException e) {
      return errorResponse(Response.Status.NOT_FOUND, "policy_not_found", e.getMessage());
    } catch (RuntimeException e) {
      return errorResponse(Response.Status.INTERNAL_SERVER_ERROR, "storage_error", "Failed to register or update delegation policy");
    } finally {
      auditor.audit(Action.DELEGATION_LIFECYCLE, auditId, ResourceType.DELEGATION_POLICY,
          outcome, "event_type=" + eventType + " performed_by=" + auditLabel(operatorId));
    }
  }

  @GET
  public Response list(@QueryParam("actorAuthority") String actorAuthority) {
    final String filter = StringUtils.isBlank(actorAuthority) ? null : actorAuthority;
    String outcome = ActionOutcome.FAILURE;
    try {
      final DelegationPolicyList result = policyService.list(filter);
      final DelegationPolicyListResponse body = new DelegationPolicyListResponse();
      body.setPolicies(result.getPolicies().stream().map(DelegationPolicyResource::toResponse).collect(Collectors.toList()));
      body.setHasMore(result.hasMore());
      outcome = ActionOutcome.SUCCESS;
      return Response.ok(writeJson(body)).build();
    } catch (RuntimeException e) {
      return errorResponse(Response.Status.INTERNAL_SERVER_ERROR, "storage_error", "Failed to list delegation policies");
    } finally {
      final String operatorId = getOperatorId();
      auditor.audit(Action.ACCESS, filter != null ? filter : "ALL", ResourceType.DELEGATION_POLICY,
          outcome, "event_type=policy_listed performed_by=" + auditLabel(operatorId));
    }
  }

  @GET
  @Path("/{registrationId}")
  public Response getOne(@PathParam("registrationId") String registrationId) {
    String outcome = ActionOutcome.FAILURE;
    try {
      final Optional<DelegationPolicy> found = policyService.get(registrationId);
      if (!found.isPresent()) {
        return errorResponse(Response.Status.NOT_FOUND, "policy_not_found", "Delegation policy not found: " + registrationId);
      }
      outcome = ActionOutcome.SUCCESS;
      return Response.ok(writeJson(toResponse(found.get()))).build();
    } catch (RuntimeException e) {
      return errorResponse(Response.Status.INTERNAL_SERVER_ERROR, "storage_error", "Failed to read delegation policy");
    } finally {
      final String operatorId = getOperatorId();
      auditor.audit(Action.ACCESS, registrationId, ResourceType.DELEGATION_POLICY,
          outcome, "event_type=policy_read performed_by=" + auditLabel(operatorId));
    }
  }

  @PUT
  @Path("/{registrationId}")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response update(@PathParam("registrationId") String registrationId, String body) {
    final String operatorId = getOperatorId();
    String outcome = ActionOutcome.FAILURE;

    try {
      final DelegationPolicyRequest parsed;
      try {
        parsed = MAPPER.readValue(body, DelegationPolicyRequest.class);
      } catch (IOException e) {
        return errorResponse(Response.Status.BAD_REQUEST, "invalid_request", "Malformed or invalid JSON body");
      }

      final Response validationError = validateRequest(parsed);
      if (validationError != null) {
        return validationError;
      }

      // PUT is full-replace, but actorAuthority/actorId/createdBy/createdAt are immutable after
      // registration and are never written by update() regardless of what toStore carries for
      // them; updatedAt is computed by the storage layer, not the caller. The values passed here
      // are inert placeholders -- the response is built from the policy update() actually
      // persisted and returned, not from toStore.
      final Instant now = Instant.now();
      final DelegationPolicy toStore = toDomain(registrationId, parsed, operatorId, now, now);
      final DelegationPolicy stored = policyService.update(registrationId, toStore);
      outcome = ActionOutcome.SUCCESS;
      return Response.ok(writeJson(toResponse(stored))).build();
    } catch (DelegationPolicyNotFoundException e) {
      // registrationId may not exist at all, or it may exist with a different
      // actorAuthority/actorId -- identity is immutable, so a mismatch is rejected the same way as
      // not-found (see DelegationPolicyService.update() javadoc). The two causes are deliberately
      // not distinguished here; GET the registrationId separately to tell them apart.
      return errorResponse(Response.Status.NOT_FOUND, "policy_not_found",
          "Delegation policy not found, or actorAuthority/actorId does not match the existing record: " + registrationId);
    } catch (RuntimeException e) {
      return errorResponse(Response.Status.INTERNAL_SERVER_ERROR, "storage_error", "Failed to update delegation policy");
    } finally {
      auditor.audit(Action.DELEGATION_LIFECYCLE, registrationId, ResourceType.DELEGATION_POLICY,
          outcome, "event_type=policy_updated performed_by=" + auditLabel(operatorId));
    }
  }

  @DELETE
  @Path("/{registrationId}")
  public Response delete(@PathParam("registrationId") String registrationId) {
    final String operatorId = getOperatorId();
    String outcome = ActionOutcome.FAILURE;

    try {
      policyService.delete(registrationId);
      outcome = ActionOutcome.SUCCESS;
      return Response.noContent().build();
    } catch (DelegationPolicyNotFoundException e) {
      return errorResponse(Response.Status.NOT_FOUND, "policy_not_found", e.getMessage());
    } catch (RuntimeException e) {
      return errorResponse(Response.Status.INTERNAL_SERVER_ERROR, "storage_error", "Failed to delete delegation policy");
    } finally {
      auditor.audit(Action.DELEGATION_LIFECYCLE, registrationId, ResourceType.DELEGATION_POLICY,
          outcome, "event_type=policy_deleted performed_by=" + auditLabel(operatorId));
    }
  }

  private Response validateRequest(DelegationPolicyRequest req) {
    if (StringUtils.isBlank(req.getActorAuthority())) {
      return errorResponse(Response.Status.BAD_REQUEST, "invalid_request", "actorAuthority is required");
    }
    if (StringUtils.isBlank(req.getActorId())) {
      return errorResponse(Response.Status.BAD_REQUEST, "invalid_request", "actorId is required");
    }
    final Integer tokenTtlSec = req.getTokenTtlSec();
    if (tokenTtlSec != null && (tokenTtlSec < minTokenTtlSec || tokenTtlSec > maxTokenTtlSec)) {
      return errorResponse(Response.Status.BAD_REQUEST, "invalid_request",
          "tokenTtlSec must be between " + minTokenTtlSec + " and " + maxTokenTtlSec + " seconds (inclusive)");
    }
    final String status = effectiveStatus(req.getStatus());
    if (!DelegationPolicyRequest.STATUS_ACTIVE.equals(status) && !DelegationPolicyRequest.STATUS_REVOKED.equals(status)) {
      return errorResponse(Response.Status.BAD_REQUEST, "invalid_request",
          "status must be \"" + DelegationPolicyRequest.STATUS_ACTIVE + "\" or \"" + DelegationPolicyRequest.STATUS_REVOKED + "\"");
    }
    if (req.getCanActForUsers().isEmpty() && req.getCanActForGroups().isEmpty()) {
      return errorResponse(Response.Status.BAD_REQUEST, "invalid_request",
          "At least one of canActForUsers or canActForGroups must be non-empty");
    }
    return null;
  }

  private static DelegationPolicy toDomain(String registrationId, DelegationPolicyRequest req,
      String createdBy, Instant createdAt, Instant updatedAt) {
    return new DelegationPolicy(registrationId, req.getActorAuthority(), req.getActorId(),
        normalize(req.getName()), effectiveStatus(req.getStatus()), req.getTokenTtlSec(),
        normalize(req.getDescription()), createdBy, createdAt, updatedAt,
        req.isAllowHeadlessExchange(), req.getCanActForUsers(), req.getCanActForGroups(),
        req.getResourcePolicy());
  }

  /** Null/empty status is accepted and defaults to {@link DelegationPolicyRequest#STATUS_ACTIVE}. */
  private static String effectiveStatus(String status) {
    return StringUtils.isBlank(status) ? DelegationPolicyRequest.STATUS_ACTIVE : status;
  }

  private static DelegationPolicyResponse toResponse(DelegationPolicy policy) {
    final DelegationPolicyResponse resp = new DelegationPolicyResponse();
    resp.setRegistrationId(policy.getRegistrationId());
    resp.setActorAuthority(policy.getActorAuthority());
    resp.setActorId(policy.getActorId());
    resp.setName(policy.getName());
    resp.setStatus(policy.getStatus());
    resp.setTokenTtlSec(policy.getTokenTtlSec());
    resp.setDescription(policy.getDescription());
    resp.setAllowHeadlessExchange(policy.isAllowHeadlessExchange());
    resp.setCanActForUsers(policy.getCanActForUsers());
    resp.setCanActForGroups(policy.getCanActForGroups());
    resp.setResourcePolicy(policy.getResourcePolicy());
    resp.setCreatedBy(policy.getCreatedBy());
    resp.setCreatedAt(policy.getCreatedAt());
    resp.setUpdatedAt(policy.getUpdatedAt());
    return resp;
  }

  private static String normalize(String value) {
    return (value != null && value.isEmpty()) ? null : value;
  }

  private static String bestEffortActorIdForAudit(DelegationPolicyRequest req) {
    final String authority = StringUtils.isBlank(req.getActorAuthority()) ? null : req.getActorAuthority();
    final String actorId = StringUtils.isBlank(req.getActorId()) ? null : req.getActorId();
    if (authority == null && actorId == null) {
      return "INVALID_REQUEST";
    }
    return (authority != null ? authority : "UNKNOWN") + "/" + (actorId != null ? actorId : "UNKNOWN");
  }

  private String getOperatorId() {
    final Principal principal = request.getUserPrincipal();
    return principal != null ? principal.getName() : null;
  }

  private static String auditLabel(String operatorId) {
    return operatorId != null ? operatorId : "ANONYMOUS";
  }

  private static String writeJson(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize delegation policy response", e);
    }
  }

  private static Response errorResponse(Response.Status status, String error, String description) {
    final Map<String, String> body = new LinkedHashMap<>();
    body.put("error", error);
    body.put("error_description", description);
    return Response.status(status).entity(JsonUtils.renderAsJsonString(body)).build();
  }
}
