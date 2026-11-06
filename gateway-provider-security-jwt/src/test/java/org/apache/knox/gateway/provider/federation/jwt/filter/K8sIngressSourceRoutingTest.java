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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.junit.Test;

import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.actorChain;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.delegationToken;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.delegationTokenWithActClaim;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.filterConfig;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.nonDelegationToken;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.requestWithHeaders;

/**
 * Covers ingress source routing: the configuration it requires at initialization, which
 * requests and tokens it applies to, the two audience checks a token with no {@code act} claim can
 * be sent to (none at all, or the fixed configured allowed-audience list), how a configured
 * ingress identity is matched against the request's source SPIFFE id, and the rejection of a token
 * carrying an {@code act} claim that arrives from an ingress source.
 *
 * <p>Every request in this class is for the same destination: the namespace
 * {@link #DEST_NAMESPACE}, taken from the destination SPIFFE id header ({@link #DEST_SPIFFE_VALUE}).
 * Audiences are written as full cluster DNS names so that the destination namespace is always
 * checked against the destination SPIFFE id header and never inferred from the source SPIFFE id
 * (that inference is for a namespace-less audience and is covered in
 * {@link K8sDestinationAudienceValidatorTest}). Only the source SPIFFE id varies between requests.
 *
 * <p><b>How a route is proven.</b> A token that reaches the validator is handled by one of three
 * routes, and a test that names a route must show a result that only that route produces. These
 * are the inputs used and what each route does with them, given {@link #CONFIGURED_AUDIENCES}
 * (always non-null, because a null list accepts every audience and would hide the difference):
 * <pre>
 *   token aud                          destination      configured list    skipped
 *   MATCHING_AUD                       accept           reject             accept
 *   LISTED_AUD                         reject           accept             accept
 *   UNLISTED_OTHER_NAMESPACE_AUD       reject           reject             accept
 *   no aud claim                       reject           reject             accept
 * </pre>
 * {@code assertDestinationValidated} uses the first two rows, {@code assertConfiguredAudiencesChecked}
 * the first two in the opposite sense, and {@code assertAudienceValidationSkipped} the last two,
 * which neither of the other routes accepts.
 *
 * <p><b>Assumptions.</b> The table, and so the helpers, rely on how the validator works today:
 * these three routes are the only ones, and the audience constants were chosen so the table holds.
 * {@link #testRouteProofInputsBehaveAsTheTableStates()} checks the table directly. If it fails,
 * or a new route is added to the validator (for example a second allowed-audience list), every
 * helper must be revisited and given inputs that the new route answers differently, and the table
 * above extended with a column for it.
 *
 * <p>How the configured-list check itself treats every combination of list and token audience is
 * covered by {@link MatchesConfiguredAudiencesTest}; here it is only shown to be reached.
 */
public class K8sIngressSourceRoutingTest {

  private static final String DEST_SPIFFE_HEADER = "x-dest-spiffe-id";
  private static final String SOURCE_SPIFFE_HEADER = "x-source-spiffe-id";
  private static final String PATH_HEADER = "x-dest-path";
  private static final String SERVER_NAME_HEADER = "x-dest-server-name";
  private static final String CLUSTER_DOMAIN = "cluster.local";
  private static final String CLUSTER_SUFFIX = ".svc." + CLUSTER_DOMAIN;

  private static final String DEST_NAMESPACE = "dest-namespace";
  private static final String OTHER_NAMESPACE = "other-namespace";
  private static final String DEST_SERVICE = "dest-service-name";
  private static final String DEST_SPIFFE_VALUE = "spiffe://trust-domain/ns/" + DEST_NAMESPACE + "/sa/dest-sa";

  private static final String INGRESS_SPIFFE_ID = "spiffe://trust-domain/ns/istio-system/sa/ingressgateway";
  private static final String SECOND_INGRESS_SPIFFE_ID = "spiffe://trust-domain/ns/edge/sa/edge-gateway";
  private static final String INGRESS_SPIFFE_OTHER_TRUST_DOMAIN =
      "spiffe://other-domain/ns/istio-system/sa/ingressgateway";
  private static final String NON_INGRESS_SPIFFE_ID = "spiffe://trust-domain/ns/caller-namespace/sa/caller-sa";

  /** Names the destination namespace: accepted by destination validation, not in the configured list. */
  private static final String MATCHING_AUD = "https://" + DEST_SERVICE + "." + DEST_NAMESPACE + CLUSTER_SUFFIX;
  /** Names another namespace, so destination validation rejects it; it is the only entry in the configured list. */
  private static final String LISTED_AUD = "https://" + DEST_SERVICE + "." + OTHER_NAMESPACE + CLUSTER_SUFFIX;
  /** Names yet another namespace: neither the destination nor in the configured list. */
  private static final String UNLISTED_OTHER_NAMESPACE_AUD =
      "https://" + DEST_SERVICE + ".unlisted-namespace" + CLUSTER_SUFFIX;
  private static final List<String> CONFIGURED_AUDIENCES = Collections.singletonList(LISTED_AUD);

  private static final String DESTINATION_MISMATCH_MESSAGE = "does not match the destination";
  /** The entry that makes a token with no aud claim acceptable to the configured list check. */
  private static final String NONE_ENTRY = "NONE";
  private static final String MISSING_SOURCE_MESSAGE = "Missing or unparseable source SPIFFE id in header";
  private static final String INGRESS_DELEGATION_MESSAGE = "is a configured ingress source";

  private static final Function<String[], JWT> NON_DELEGATION = K8sAudienceTestSupport::nonDelegationToken;
  private static final Function<String[], JWT> DELEGATION = K8sAudienceTestSupport::delegationToken;

  private static K8sDestinationAudienceValidator init(final Map<String, String> params) throws Exception {
    final K8sDestinationAudienceValidator validator = new K8sDestinationAudienceValidator();
    validator.init(filterConfig(params));
    return validator;
  }

  /**
   * Destination validation only: the destination namespace is read from the destination SPIFFE id
   * header {@link #DEST_SPIFFE_HEADER}, and {@link #CLUSTER_DOMAIN} is the cluster domain used to
   * parse DNS-form audiences. No source SPIFFE id header is configured and no ingress routing is set.
   */
  private static Map<String, String> destinationValidationParams() {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, DEST_SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    return params;
  }

  /**
   * {@link #destinationValidationParams()} plus the source SPIFFE id header name and ingress source
   * routing on, with {@link #INGRESS_SPIFFE_ID} the only ingress identity.
   */
  private static Map<String, String> ingressRoutingParams() {
    final Map<String, String> params = destinationValidationParams();
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_ID_ROUTING_ENABLED_PARAM, "true");
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, INGRESS_SPIFFE_ID);
    return params;
  }

  /** {@link #ingressRoutingParams()} plus skipping audience validation on the ingress route. */
  private static Map<String, String> ingressRoutingSkipValidationParams() {
    final Map<String, String> params = ingressRoutingParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_ROUTING_SKIP_VALIDATION_PARAM, "true");
    return params;
  }

  /**
   * {@link #ingressRoutingParams()} with routing explicitly off but the ingress list and both
   * routing skip flags set, so any effect of those settings with routing off would show.
   */
  private static Map<String, String> ingressRoutingDisabledParams() {
    final Map<String, String> params = ingressRoutingSkipValidationParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_ID_ROUTING_ENABLED_PARAM, "false");
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_ROUTING_SKIP_TRUST_DOMAIN_MATCH_PARAM,
        "true");
    return params;
  }

  /** Every request goes to the same destination; this is its destination SPIFFE id header. */
  private static Map<String, String> destinationHeaders() {
    final Map<String, String> headers = new HashMap<>();
    headers.put(DEST_SPIFFE_HEADER, DEST_SPIFFE_VALUE);
    return headers;
  }

  /** A request to the destination from {@code sourceSpiffeId}; with null, no source header is sent. */
  private static HttpServletRequest requestFromSource(final String sourceSpiffeId) {
    final Map<String, String> headers = destinationHeaders();
    if (sourceSpiffeId != null) {
      headers.put(SOURCE_SPIFFE_HEADER, sourceSpiffeId);
    }
    return requestWithHeaders(headers);
  }

  private static void assertRejected(final AudienceValidationResult result, final String messageFragment) {
    assertFalse(result.isValid());
    assertNotNull("a rejection from this validator carries a message", result.message());
    assertTrue(result.message(), result.message().contains(messageFragment));
  }

  private static void assertAccepted(final AudienceValidationResult result) {
    assertTrue(result.message(), result.isValid());
  }

  /** Destination validation: first two rows of the table, accepting {@link #MATCHING_AUD} only. */
  private static void assertDestinationValidated(final K8sDestinationAudienceValidator validator,
      final String sourceSpiffeId, final Function<String[], JWT> tokens) {
    assertAccepted(validator.validate(requestFromSource(sourceSpiffeId),
        tokens.apply(new String[]{MATCHING_AUD}), CONFIGURED_AUDIENCES));
    assertRejected(validator.validate(requestFromSource(sourceSpiffeId),
        tokens.apply(new String[]{LISTED_AUD}), CONFIGURED_AUDIENCES), DESTINATION_MISMATCH_MESSAGE);
  }

  /**
   * Configured list: first two rows, accepting {@link #LISTED_AUD} only. The list check's
   * rejection carries no message, unlike every rejection from this validator.
   */
  private static void assertConfiguredAudiencesChecked(final K8sDestinationAudienceValidator validator,
      final String sourceSpiffeId) {
    assertAccepted(validator.validate(requestFromSource(sourceSpiffeId),
        nonDelegationToken(LISTED_AUD), CONFIGURED_AUDIENCES));
    final AudienceValidationResult rejected = validator.validate(requestFromSource(sourceSpiffeId),
        nonDelegationToken(MATCHING_AUD), CONFIGURED_AUDIENCES);
    assertFalse(rejected.isValid());
    assertNull(rejected.message());
  }

  /** Skipped validation: last two rows, which neither of the other routes accepts. */
  private static void assertAudienceValidationSkipped(final K8sDestinationAudienceValidator validator,
      final String sourceSpiffeId) {
    assertAccepted(validator.validate(requestFromSource(sourceSpiffeId),
        nonDelegationToken(UNLISTED_OTHER_NAMESPACE_AUD), CONFIGURED_AUDIENCES));
    assertAccepted(validator.validate(requestFromSource(sourceSpiffeId),
        nonDelegationToken((String[]) null), CONFIGURED_AUDIENCES));
  }

  private static void assertIngressDelegationRejected(final AudienceValidationResult result) {
    assertRejected(result, INGRESS_DELEGATION_MESSAGE);
    assertTrue(result.message(), result.message().contains("act claim"));
    assertTrue(result.message(), result.message().contains(SOURCE_SPIFFE_HEADER));
  }

  private static void assertInitRejectsIngressIds(final Map<String, String> params) {
    final ServletException e = assertThrows(ServletException.class, () -> init(params));
    assertTrue(e.getMessage(),
        e.getMessage().contains(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM));
  }

  // ---- the table in the class javadoc ----

  @Test
  public void testRouteProofInputsBehaveAsTheTableStates() throws Exception {
    // Configured list on its own.
    assertFalse(AbstractJWTFilter.matchesConfiguredAudiences(nonDelegationToken(MATCHING_AUD), CONFIGURED_AUDIENCES));
    assertTrue(AbstractJWTFilter.matchesConfiguredAudiences(nonDelegationToken(LISTED_AUD), CONFIGURED_AUDIENCES));
    assertFalse(AbstractJWTFilter.matchesConfiguredAudiences(
        nonDelegationToken(UNLISTED_OTHER_NAMESPACE_AUD), CONFIGURED_AUDIENCES));
    assertFalse(AbstractJWTFilter.matchesConfiguredAudiences(
        nonDelegationToken((String[]) null), CONFIGURED_AUDIENCES));

    // Destination validation on its own: routing is not enabled, so there is no other route.
    final K8sDestinationAudienceValidator validator = init(destinationValidationParams());
    assertAccepted(validator.validate(requestFromSource(null), nonDelegationToken(MATCHING_AUD),
        CONFIGURED_AUDIENCES));
    assertRejected(validator.validate(requestFromSource(null), nonDelegationToken(LISTED_AUD),
        CONFIGURED_AUDIENCES), DESTINATION_MISMATCH_MESSAGE);
    assertRejected(validator.validate(requestFromSource(null), nonDelegationToken(UNLISTED_OTHER_NAMESPACE_AUD),
        CONFIGURED_AUDIENCES), DESTINATION_MISMATCH_MESSAGE);
    assertRejected(validator.validate(requestFromSource(null), nonDelegationToken((String[]) null),
        CONFIGURED_AUDIENCES), "no aud claim");
  }

  // ---- init() requirements ----

  @Test
  public void testInitThrowsWhenRoutingEnabledButSourceHeaderUnconfigured() {
    final Map<String, String> params = ingressRoutingParams();
    params.remove(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM);
    final ServletException e = assertThrows(ServletException.class, () -> init(params));
    assertTrue(e.getMessage().contains(
        K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_ID_ROUTING_ENABLED_PARAM));
    assertTrue(e.getMessage().contains(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM));
  }

  @Test
  public void testInitThrowsWhenRoutingEnabledButIngressIdListUnconfigured() {
    final Map<String, String> params = ingressRoutingParams();
    params.remove(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM);
    final ServletException e = assertThrows(ServletException.class, () -> init(params));
    assertTrue(e.getMessage().contains(
        K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_ID_ROUTING_ENABLED_PARAM));
    assertTrue(e.getMessage().contains(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM));
  }

  @Test
  public void testInitThrowsWhenRoutingEnabledAndIngressIdListIsBlank() {
    final Map<String, String> params = ingressRoutingParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, "   ");
    final ServletException e = assertThrows(ServletException.class, () -> init(params));
    assertTrue(e.getMessage().contains(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM));
  }

  @Test
  public void testInitThrowsOnUnparseableIngressSpiffeIdEntry() {
    final Map<String, String> params = ingressRoutingParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, "not-a-spiffe-id");
    final ServletException e = assertThrows(ServletException.class, () -> init(params));
    assertTrue(e.getMessage().contains(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM));
    assertTrue(e.getMessage().contains("not-a-spiffe-id"));
  }

  @Test
  public void testInitThrowsOnIngressSpiffeIdEntryWithoutNamespaceAndServiceAccountPath() {
    final Map<String, String> params = ingressRoutingParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, "spiffe://trust-domain/ns/only");
    assertInitRejectsIngressIds(params);
  }

  @Test
  public void testInitThrowsOnIngressSpiffeIdsWithStrayInteriorComma() {
    final Map<String, String> params = ingressRoutingParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM,
        INGRESS_SPIFFE_ID + ",," + SECOND_INGRESS_SPIFFE_ID);
    assertInitRejectsIngressIds(params);
  }

  @Test
  public void testInitThrowsOnIngressSpiffeIdsWithTrailingComma() {
    final Map<String, String> params = ingressRoutingParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, INGRESS_SPIFFE_ID + ",");
    assertInitRejectsIngressIds(params);
  }

  @Test
  public void testInitThrowsOnIngressSpiffeIdsWithLeadingComma() {
    final Map<String, String> params = ingressRoutingParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, "," + INGRESS_SPIFFE_ID);
    assertInitRejectsIngressIds(params);
  }

  @Test
  public void testIngressSpiffeIdListEntriesAreTrimmed() throws Exception {
    final Map<String, String> params = ingressRoutingSkipValidationParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM,
        "  " + INGRESS_SPIFFE_ID + "  ,  " + SECOND_INGRESS_SPIFFE_ID + "  ");
    final K8sDestinationAudienceValidator validator = init(params);
    assertAudienceValidationSkipped(validator, INGRESS_SPIFFE_ID);
    assertAudienceValidationSkipped(validator, SECOND_INGRESS_SPIFFE_ID);
  }

  @Test
  public void testInitSucceedsWithIngressIdListConfiguredWhileRoutingDisabled() throws Exception {
    final Map<String, String> params = destinationValidationParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, INGRESS_SPIFFE_ID);
    init(params);
  }

  @Test
  public void testInitSucceedsWithRoutingSkipFlagsSetWhileRoutingDisabled() throws Exception {
    final Map<String, String> params = destinationValidationParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_ROUTING_SKIP_VALIDATION_PARAM, "true");
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_ROUTING_SKIP_TRUST_DOMAIN_MATCH_PARAM,
        "true");
    init(params);
  }

  @Test
  public void testInitThrowsOnUnparseableIngressSpiffeIdEntryEvenWhenRoutingDisabled() {
    final Map<String, String> params = destinationValidationParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, "not-a-spiffe-id");
    assertInitRejectsIngressIds(params);
  }

  // ---- routing on, skip.validation at its default of false: ingress source, no act claim ----

  @Test
  public void testIngressSourceWithoutActClaimIsCheckedAgainstConfiguredAudiencesOnly() throws Exception {
    assertConfiguredAudiencesChecked(init(ingressRoutingParams()), INGRESS_SPIFFE_ID);
  }

  @Test
  public void testIngressSourceWithoutActClaimIsUnrestrictedWhenNoAudiencesAreConfigured() throws Exception {
    final K8sDestinationAudienceValidator validator = init(ingressRoutingParams());
    // A null configured list makes the list check accept any audience, so on this route the
    // ingress is only as strict as the deployment's configured audiences...
    assertAccepted(validator.validate(requestFromSource(INGRESS_SPIFFE_ID),
        nonDelegationToken(UNLISTED_OTHER_NAMESPACE_AUD), null));
    // ...whereas any other source with the same token is destination-validated and rejected.
    assertRejected(validator.validate(requestFromSource(NON_INGRESS_SPIFFE_ID),
        nonDelegationToken(UNLISTED_OTHER_NAMESPACE_AUD), null), DESTINATION_MISMATCH_MESSAGE);
  }

  @Test
  public void testIngressSourceWithoutActClaimAndNoAudClaimFollowsTheConfiguredListNoneRule() throws Exception {
    final K8sDestinationAudienceValidator validator = init(ingressRoutingParams());
    final JWT noAudience = nonDelegationToken((String[]) null);
    // No NONE in the list: rejected by the list check, which carries no message, so this
    // validator's own "token has no aud claim" rejection never ran.
    final AudienceValidationResult withoutNone = validator.validate(requestFromSource(INGRESS_SPIFFE_ID), noAudience,
        CONFIGURED_AUDIENCES);
    assertFalse(withoutNone.isValid());
    assertNull(withoutNone.message());
    // NONE in the list: accepted, which destination validation never does for a token with no aud.
    final List<String> listWithNone = Arrays.asList(LISTED_AUD, NONE_ENTRY);
    assertAccepted(validator.validate(requestFromSource(INGRESS_SPIFFE_ID), noAudience, listWithNone));
    // Contrast: any other source with the same token is rejected by this validator, with a message.
    assertRejected(validator.validate(requestFromSource(NON_INGRESS_SPIFFE_ID), noAudience, listWithNone),
        "no aud claim");
  }

  // ---- routing on, skip.validation true: ingress source, no act claim ----

  @Test
  public void testSkipValidationSkipsAudienceValidationForIngressSourceWithoutActClaim() throws Exception {
    assertAudienceValidationSkipped(init(ingressRoutingSkipValidationParams()), INGRESS_SPIFFE_ID);
  }

  @Test
  public void testSkipValidationPrecedesTheRequirementThatTheConfiguredPathHeaderBePresent() throws Exception {
    final Map<String, String> params = ingressRoutingSkipValidationParams();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    // The configured path header is missing from the request, yet the skipping route accepts it...
    assertAudienceValidationSkipped(validator, INGRESS_SPIFFE_ID);
    // ...while the same missing header is a rejection for any other source.
    assertRejected(validator.validate(requestFromSource(NON_INGRESS_SPIFFE_ID), nonDelegationToken(MATCHING_AUD), null),
        PATH_HEADER);
  }

  @Test
  public void testSkipValidationHasNoEffectWhenRoutingDisabled() throws Exception {
    // The ingress list and skip.validation are set, but routing is off: an ingress source is
    // destination-validated like any other.
    assertDestinationValidated(init(ingressRoutingDisabledParams()), INGRESS_SPIFFE_ID, NON_DELEGATION);
  }

  @Test
  public void testSkipValidationStillSkipsWhenValidateWithoutActClaimFalse() throws Exception {
    final Map<String, String> params = ingressRoutingSkipValidationParams();
    params.put(K8sDestinationAudienceValidator.VALIDATE_AUDIENCES_WITHOUT_ACT_CLAIM_PARAM, "false");
    assertAudienceValidationSkipped(init(params), INGRESS_SPIFFE_ID);
  }

  // ---- routing on, source is not an ingress identity ----

  @Test
  public void testNonIngressSourceWithoutActClaimIsDestinationValidated() throws Exception {
    assertDestinationValidated(init(ingressRoutingParams()), NON_INGRESS_SPIFFE_ID, NON_DELEGATION);
  }

  @Test
  public void testNonIngressSourceWithoutActClaimIsCheckedAgainstConfiguredAudiencesWhenValidateWithoutActClaimFalse()
      throws Exception {
    final Map<String, String> params = ingressRoutingParams();
    params.put(K8sDestinationAudienceValidator.VALIDATE_AUDIENCES_WITHOUT_ACT_CLAIM_PARAM, "false");
    assertConfiguredAudiencesChecked(init(params), NON_INGRESS_SPIFFE_ID);
  }

  @Test
  public void testMissingSourceHeaderIsNotAnIngressSourceAndIsRejectedAsMissing() throws Exception {
    final K8sDestinationAudienceValidator validator = init(ingressRoutingSkipValidationParams());
    assertRejected(validator.validate(requestFromSource(null), nonDelegationToken(MATCHING_AUD), CONFIGURED_AUDIENCES),
        MISSING_SOURCE_MESSAGE);
  }

  @Test
  public void testUnparseableSourceHeaderIsNotAnIngressSourceAndIsRejectedAsMissing() throws Exception {
    final K8sDestinationAudienceValidator validator = init(ingressRoutingSkipValidationParams());
    assertRejected(validator.validate(requestFromSource("not-a-spiffe-id"), nonDelegationToken(MATCHING_AUD),
        CONFIGURED_AUDIENCES), MISSING_SOURCE_MESSAGE);
  }

  @Test
  public void testMissingSourceHeaderIsCheckedAgainstConfiguredAudiencesWhenValidateWithoutActClaimFalse()
      throws Exception {
    final Map<String, String> params = ingressRoutingSkipValidationParams();
    params.put(K8sDestinationAudienceValidator.VALIDATE_AUDIENCES_WITHOUT_ACT_CLAIM_PARAM, "false");
    // Not skipped, although skip.validation is true: with no source header there is no ingress.
    assertConfiguredAudiencesChecked(init(params), null);
  }

  @Test
  public void testSourceMatchingNoneOfSeveralConfiguredIngressIdsIsDestinationValidated() throws Exception {
    final Map<String, String> params = ingressRoutingSkipValidationParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM,
        INGRESS_SPIFFE_ID + "," + SECOND_INGRESS_SPIFFE_ID);
    assertDestinationValidated(init(params), NON_INGRESS_SPIFFE_ID, NON_DELEGATION);
  }

  @Test
  public void testSourceMatchingTheSecondConfiguredIngressIdIsAnIngressSource() throws Exception {
    final Map<String, String> params = ingressRoutingSkipValidationParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM,
        INGRESS_SPIFFE_ID + "," + SECOND_INGRESS_SPIFFE_ID);
    assertAudienceValidationSkipped(init(params), SECOND_INGRESS_SPIFFE_ID);
  }

  // ---- trust-domain matching ----

  @Test
  public void testDifferentTrustDomainIsNotAnIngressSourceByDefault() throws Exception {
    assertDestinationValidated(init(ingressRoutingSkipValidationParams()),
        INGRESS_SPIFFE_OTHER_TRUST_DOMAIN, NON_DELEGATION);
  }

  @Test
  public void testDifferentTrustDomainIsAnIngressSourceWhenTrustDomainMatchSkipped() throws Exception {
    final Map<String, String> params = ingressRoutingSkipValidationParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_ROUTING_SKIP_TRUST_DOMAIN_MATCH_PARAM,
        "true");
    assertAudienceValidationSkipped(init(params), INGRESS_SPIFFE_OTHER_TRUST_DOMAIN);
  }

  @Test
  public void testSkippingTrustDomainMatchStillRequiresNamespaceToMatch() throws Exception {
    final Map<String, String> params = ingressRoutingSkipValidationParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_ROUTING_SKIP_TRUST_DOMAIN_MATCH_PARAM,
        "true");
    assertDestinationValidated(init(params),
        "spiffe://other-domain/ns/" + OTHER_NAMESPACE + "/sa/ingressgateway", NON_DELEGATION);
  }

  @Test
  public void testSkippingTrustDomainMatchStillRequiresServiceAccountToMatch() throws Exception {
    final Map<String, String> params = ingressRoutingSkipValidationParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_ROUTING_SKIP_TRUST_DOMAIN_MATCH_PARAM,
        "true");
    assertDestinationValidated(init(params), "spiffe://other-domain/ns/istio-system/sa/not-the-gateway",
        NON_DELEGATION);
  }

  @Test
  public void testIngressSpiffeIdMatchingIsCaseInsensitive() throws Exception {
    final Map<String, String> mixedCaseConfigured = ingressRoutingSkipValidationParams();
    mixedCaseConfigured.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM,
        "spiffe://Trust-Domain/ns/Istio-System/sa/IngressGateway");
    assertAudienceValidationSkipped(init(mixedCaseConfigured), INGRESS_SPIFFE_ID);

    assertAudienceValidationSkipped(init(ingressRoutingSkipValidationParams()),
        "spiffe://Trust-Domain/ns/Istio-System/sa/IngressGateway");
  }

  @Test
  public void testSkipTrustDomainMatchHasNoEffectWhenRoutingDisabled() throws Exception {
    assertDestinationValidated(init(ingressRoutingDisabledParams()), INGRESS_SPIFFE_OTHER_TRUST_DOMAIN, NON_DELEGATION);
  }

  // ---- a token with an act claim from an ingress source ----

  @Test
  public void testDelegationTokenFromIngressSourceIsRejected() throws Exception {
    final K8sDestinationAudienceValidator validator = init(ingressRoutingParams());
    assertIngressDelegationRejected(validator.validate(requestFromSource(INGRESS_SPIFFE_ID),
        delegationToken(MATCHING_AUD), null));
  }

  @Test
  public void testDelegationTokenFromIngressSourceIsNotCheckedAgainstConfiguredAudiences() throws Exception {
    final K8sDestinationAudienceValidator validator = init(ingressRoutingParams());
    // LISTED_AUD is in the configured list, which would accept a token with no act claim.
    assertIngressDelegationRejected(
        validator.validate(requestFromSource(INGRESS_SPIFFE_ID), delegationToken(LISTED_AUD), CONFIGURED_AUDIENCES));
  }

  @Test
  public void testDelegationTokenFromIngressSourceIsRejectedEvenWhenSkipValidationTrue() throws Exception {
    final K8sDestinationAudienceValidator validator = init(ingressRoutingSkipValidationParams());
    assertIngressDelegationRejected(validator.validate(requestFromSource(INGRESS_SPIFFE_ID),
        delegationToken(MATCHING_AUD), null));
  }

  @Test
  public void testDelegationTokenFromIngressSourceIsRejectedAsIngressWhenItHasNoAudClaim() throws Exception {
    final K8sDestinationAudienceValidator validator = init(ingressRoutingParams());
    // The rejection for a missing aud claim would otherwise have been reported first.
    assertIngressDelegationRejected(validator.validate(requestFromSource(INGRESS_SPIFFE_ID),
        delegationToken((String[]) null), null));
    assertRejected(validator.validate(requestFromSource(NON_INGRESS_SPIFFE_ID), delegationToken((String[]) null), null),
        "Token has an act claim but no aud claim");
  }

  @Test
  public void testDelegationTokenFromIngressSourceIsDestinationValidatedWhenRoutingDisabled() throws Exception {
    assertDestinationValidated(init(ingressRoutingDisabledParams()), INGRESS_SPIFFE_ID, DELEGATION);
  }

  @Test
  public void testDelegationTokenFromNonIngressSourceIsDestinationValidated() throws Exception {
    assertDestinationValidated(init(ingressRoutingParams()), NON_INGRESS_SPIFFE_ID, DELEGATION);
  }

  @Test
  public void testMalformedActClaimFromIngressSourceIsRejectedAsIngress() throws Exception {
    final K8sDestinationAudienceValidator validator = init(ingressRoutingParams());
    assertIngressDelegationRejected(validator.validate(requestFromSource(INGRESS_SPIFFE_ID),
        delegationTokenWithActClaim("not-a-map", MATCHING_AUD), null));
  }

  @Test
  public void testDelegationTokenFromIngressSourceRejectedBeforeActSubBindingRuns() throws Exception {
    final Map<String, String> params = ingressRoutingParams();
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    // The act sub names a different service account than the source, so if the binding check ran
    // first the rejection would be about the actor mismatch rather than about the ingress.
    final JWT otherActor = delegationTokenWithActClaim(
        actorChain("system:serviceaccount:" + OTHER_NAMESPACE + ":other-sa"), MATCHING_AUD);
    assertIngressDelegationRejected(validator.validate(requestFromSource(INGRESS_SPIFFE_ID), otherActor, null));
    // Contrast: the same token from a non-ingress source is rejected for the actor mismatch.
    assertRejected(validator.validate(requestFromSource(NON_INGRESS_SPIFFE_ID), otherActor, null),
        "does not match source SPIFFE id service account");
  }

  // ---- the destination-validation checks that follow the routing ----

  @Test
  public void testRequireAllAudiencesMatchStillAppliesWhenRoutingEnabledButNotIngress() throws Exception {
    final Map<String, String> params = ingressRoutingParams();
    final JWT matchingAndOtherNamespace = delegationToken(MATCHING_AUD, LISTED_AUD);
    // Without the flag one matching audience is enough...
    assertAccepted(init(params).validate(
        requestFromSource(NON_INGRESS_SPIFFE_ID), matchingAndOtherNamespace, null));
    // ...with it, the second audience, which names another namespace, causes a rejection.
    params.put(K8sDestinationAudienceValidator.REQUIRE_ALL_AUDIENCES_MATCH_PARAM, "true");
    assertRejected(init(params).validate(
        requestFromSource(NON_INGRESS_SPIFFE_ID), matchingAndOtherNamespace, null), DESTINATION_MISMATCH_MESSAGE);
  }

  @Test
  public void testRequireAllAudiencesMatchDoesNotApplyToTheConfiguredAudiencesCheck() throws Exception {
    final Map<String, String> params = ingressRoutingParams();
    params.put(K8sDestinationAudienceValidator.REQUIRE_ALL_AUDIENCES_MATCH_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    // UNLISTED_OTHER_NAMESPACE_AUD is in neither the list nor the destination, so a require-all
    // check would reject this token; the list check accepts it because one audience is in the list.
    assertAccepted(validator.validate(requestFromSource(INGRESS_SPIFFE_ID),
        nonDelegationToken(LISTED_AUD, UNLISTED_OTHER_NAMESPACE_AUD), CONFIGURED_AUDIENCES));
  }

  @Test
  public void testBothNamespaceSourcesStillMustAgreeWhenRoutingEnabledButNotIngress() throws Exception {
    final Map<String, String> params = ingressRoutingParams();
    params.put(K8sDestinationAudienceValidator.SERVER_NAME_HEADER_PARAM, SERVER_NAME_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    final Map<String, String> headers = destinationHeaders();
    headers.put(SOURCE_SPIFFE_HEADER, NON_INGRESS_SPIFFE_ID);
    headers.put(SERVER_NAME_HEADER, DEST_SERVICE + "." + OTHER_NAMESPACE + CLUSTER_SUFFIX);
    assertRejected(validator.validate(requestWithHeaders(headers), nonDelegationToken(MATCHING_AUD), null),
        "disagrees with namespace");
  }
}
