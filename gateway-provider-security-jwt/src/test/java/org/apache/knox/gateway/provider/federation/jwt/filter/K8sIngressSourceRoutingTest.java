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
 * be routed to (none at all, or the fixed configured allowed-audience list), how a configured
 * ingress identity is matched against the request's source SPIFFE id, and the rejection of a token
 * carrying an {@code act} claim that arrives from an ingress source.
 *
 * <p>Every test that names a route proves it, by showing a result that only that route can
 * produce. Three routes are possible for a token that passes through here, and each helper
 * below checks one of them with inputs chosen so the other two would give a different answer:
 * <ul>
 *   <li>destination validation: accepts {@link #MATCHING_AUD}, rejects {@link #LISTED_AUD} with a
 *       message naming the destination;</li>
 *   <li>the fixed configured allowed-audience list: accepts {@link #LISTED_AUD}, rejects
 *       {@link #MATCHING_AUD} with no message;</li>
 *   <li>skipping audience validation: accepts {@link #UNRELATED_AUD} and a token with no audience,
 *       which neither of the other two accepts.</li>
 * </ul>
 * The configured list is always non-null where a route is being proven, because a null list
 * accepts every audience and would make the list route indistinguishable from the others. How the
 * list check itself treats every combination of list and token audience is covered by
 * {@link MatchesConfiguredAudiencesTest}; here it is only shown to be reached.
 *
 * <p>The destination in every request is the namespace {@code ns}, which {@link #MATCHING_AUD}
 * names. It comes from the destination SPIFFE id header configured in {@link #baseParams()},
 * which {@link #headersFrom(String)} always sets to {@link #DEST_SPIFFE_VALUE}.
 */
public class K8sIngressSourceRoutingTest {

  private static final String SPIFFE_HEADER = "x-dest-spiffe-id";
  private static final String SOURCE_SPIFFE_HEADER = "x-source-spiffe-id";
  private static final String PATH_HEADER = "x-dest-path";
  private static final String SERVER_NAME_HEADER = "x-dest-server-name";
  private static final String CLUSTER_DOMAIN = "cluster.local";
  private static final String CLUSTER_SUFFIX = ".svc.cluster.local";
  private static final String DEST_SPIFFE_VALUE = "spiffe://trust-domain/ns/ns/sa/dest-sa";
  private static final String INGRESS_SPIFFE_ID = "spiffe://trust-domain/ns/istio-system/sa/ingressgateway";
  private static final String SECOND_INGRESS_SPIFFE_ID = "spiffe://trust-domain/ns/edge/sa/edge-gateway";
  private static final String INGRESS_SPIFFE_OTHER_TRUST_DOMAIN =
      "spiffe://other-domain/ns/istio-system/sa/ingressgateway";
  private static final String NON_INGRESS_SPIFFE_ID = "spiffe://trust-domain/ns/ns/sa/caller-sa";

  /** Names the destination namespace {@code ns}: accepted by destination validation. */
  private static final String MATCHING_AUD = "https://svc.ns";
  /** Names another namespace, so destination validation rejects it; it is the configured list. */
  private static final String LISTED_AUD = "https://svc.other-ns";
  /** Neither the destination nor in the configured list. */
  private static final String UNRELATED_AUD = "https://somewhere.else.example/nothing/to/do/with/this";
  private static final List<String> CONFIGURED_AUDIENCES = Collections.singletonList(LISTED_AUD);

  private static final String DESTINATION_MISMATCH_MESSAGE = "does not match the destination";
  private static final String MISSING_SOURCE_MESSAGE = "Missing or unparseable source SPIFFE id in header";
  private static final String INGRESS_DELEGATION_MESSAGE = "is a configured ingress source";

  private static final Function<String[], JWT> NON_DELEGATION = audiences -> nonDelegationToken(audiences);
  private static final Function<String[], JWT> DELEGATION = audiences -> delegationToken(audiences);

  private static K8sDestinationAudienceValidator init(final Map<String, String> params) throws Exception {
    final K8sDestinationAudienceValidator validator = new K8sDestinationAudienceValidator();
    validator.init(filterConfig(params));
    return validator;
  }

  /**
   * Destination validation set up: the destination namespace is read from the SPIFFE id header
   * {@link #SPIFFE_HEADER}, and {@link #CLUSTER_DOMAIN} is the cluster domain. Also names the
   * header that carries the source SPIFFE id. No ingress routing is configured.
   */
  private static Map<String, String> baseParams() {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    return params;
  }

  /** {@link #baseParams()} plus ingress source routing on, with {@link #INGRESS_SPIFFE_ID} the ingress. */
  private static Map<String, String> ingressRoutingParams() {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_ID_ROUTING_ENABLED_PARAM, "true");
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, INGRESS_SPIFFE_ID);
    return params;
  }

  /** {@link #ingressRoutingParams()} plus skipping audience validation for the ingress route. */
  private static Map<String, String> skipValidationParams() {
    final Map<String, String> params = ingressRoutingParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_ROUTING_SKIP_VALIDATION_PARAM, "true");
    return params;
  }

  /** {@link #baseParams()} with the ingress list and both skip flags set but routing left off. */
  private static Map<String, String> routingDisabledParams() {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, INGRESS_SPIFFE_ID);
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_ROUTING_SKIP_VALIDATION_PARAM, "true");
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_ROUTING_SKIP_TRUST_DOMAIN_MATCH_PARAM,
        "true");
    return params;
  }

  /** Request headers: destination is always {@link #DEST_SPIFFE_VALUE}; the source is omitted when null. */
  private static Map<String, String> headersFrom(final String sourceSpiffeId) {
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, DEST_SPIFFE_VALUE);
    if (sourceSpiffeId != null) {
      headers.put(SOURCE_SPIFFE_HEADER, sourceSpiffeId);
    }
    return headers;
  }

  private static HttpServletRequest requestFrom(final String sourceSpiffeId) {
    return requestWithHeaders(headersFrom(sourceSpiffeId));
  }

  private static AudienceValidationResult validate(final K8sDestinationAudienceValidator validator,
      final String sourceSpiffeId, final JWT token, final List<String> configuredAudiences) {
    return validator.validate(requestFrom(sourceSpiffeId), token, configuredAudiences);
  }

  private static void assertRejected(final AudienceValidationResult result, final String messageFragment) {
    assertFalse(result.isValid());
    assertNotNull("a rejection from this validator carries a message", result.message());
    assertTrue(result.message(), result.message().contains(messageFragment));
  }

  private static void assertAccepted(final AudienceValidationResult result) {
    assertTrue(result.message(), result.isValid());
  }

  /**
   * Asserts the request is destination-validated: {@link #MATCHING_AUD} is accepted although it
   * is not in the configured list, and {@link #LISTED_AUD} is rejected although it is in it.
   */
  private static void assertDestinationValidated(final K8sDestinationAudienceValidator validator,
      final String sourceSpiffeId, final Function<String[], JWT> tokens) {
    assertAccepted(validate(validator, sourceSpiffeId, tokens.apply(new String[]{MATCHING_AUD}),
        CONFIGURED_AUDIENCES));
    assertRejected(validate(validator, sourceSpiffeId, tokens.apply(new String[]{LISTED_AUD}),
        CONFIGURED_AUDIENCES), DESTINATION_MISMATCH_MESSAGE);
  }

  /**
   * Asserts the token is checked against the fixed configured list and nothing else:
   * {@link #LISTED_AUD} is accepted although the destination does not match it, and
   * {@link #MATCHING_AUD} is rejected although the destination does match it. The list check's
   * rejection carries no message, unlike any rejection from this validator.
   */
  private static void assertConfiguredAudiencesChecked(final K8sDestinationAudienceValidator validator,
      final String sourceSpiffeId) {
    assertAccepted(validate(validator, sourceSpiffeId, nonDelegationToken(LISTED_AUD), CONFIGURED_AUDIENCES));
    final AudienceValidationResult rejected =
        validate(validator, sourceSpiffeId, nonDelegationToken(MATCHING_AUD), CONFIGURED_AUDIENCES);
    assertFalse(rejected.isValid());
    assertNull(rejected.message());
  }

  /**
   * Asserts audience validation is skipped: {@link #UNRELATED_AUD}, which is neither the
   * destination nor in the configured list, is accepted, and so is a token with no audience at
   * all, which the configured list (it has no {@code NONE}) and destination validation both reject.
   */
  private static void assertAudienceValidationSkipped(final K8sDestinationAudienceValidator validator,
      final String sourceSpiffeId) {
    assertAccepted(validate(validator, sourceSpiffeId, nonDelegationToken(UNRELATED_AUD), CONFIGURED_AUDIENCES));
    assertAccepted(validate(validator, sourceSpiffeId, nonDelegationToken((String[]) null),
        CONFIGURED_AUDIENCES));
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

  // ---- init() requirements ----

  @Test
  public void testInitThrowsWhenRoutingEnabledButSourceHeaderUnconfigured() {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_ID_ROUTING_ENABLED_PARAM, "true");
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, INGRESS_SPIFFE_ID);
    final ServletException e = assertThrows(ServletException.class, () -> init(params));
    assertTrue(e.getMessage().contains(
        K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_ID_ROUTING_ENABLED_PARAM));
    assertTrue(e.getMessage().contains(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM));
  }

  @Test
  public void testInitThrowsWhenRoutingEnabledButIngressIdListUnconfigured() {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_ID_ROUTING_ENABLED_PARAM, "true");
    final ServletException e = assertThrows(ServletException.class, () -> init(params));
    assertTrue(e.getMessage().contains(
        K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_ID_ROUTING_ENABLED_PARAM));
    assertTrue(e.getMessage().contains(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM));
  }

  @Test
  public void testInitThrowsWhenRoutingEnabledAndIngressIdListIsBlank() {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_ID_ROUTING_ENABLED_PARAM, "true");
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
  public void testInitTrimsEachOfTwoIngressSpiffeIdEntries() throws Exception {
    final Map<String, String> params = skipValidationParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM,
        "  " + INGRESS_SPIFFE_ID + "  ,  " + SECOND_INGRESS_SPIFFE_ID + "  ");
    final K8sDestinationAudienceValidator validator = init(params);
    assertAudienceValidationSkipped(validator, INGRESS_SPIFFE_ID);
    assertAudienceValidationSkipped(validator, SECOND_INGRESS_SPIFFE_ID);
  }

  @Test
  public void testInitSucceedsWithIngressIdListConfiguredWhileRoutingDisabled() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, INGRESS_SPIFFE_ID);
    init(params);
  }

  @Test
  public void testInitSucceedsWithRoutingSkipFlagsSetWhileRoutingDisabled() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_ROUTING_SKIP_VALIDATION_PARAM, "true");
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_ROUTING_SKIP_TRUST_DOMAIN_MATCH_PARAM,
        "true");
    init(params);
  }

  @Test
  public void testInitThrowsOnUnparseableIngressSpiffeIdEntryEvenWhenRoutingDisabled() {
    final Map<String, String> params = baseParams();
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
    assertAccepted(validate(validator, INGRESS_SPIFFE_ID, nonDelegationToken(UNRELATED_AUD), null));
    // ...whereas any other source with the same token is destination-validated and rejected.
    assertRejected(validate(validator, NON_INGRESS_SPIFFE_ID, nonDelegationToken(UNRELATED_AUD), null),
        DESTINATION_MISMATCH_MESSAGE);
  }

  @Test
  public void testIngressSourceWithoutActClaimAndNoAudClaimFollowsTheConfiguredListNoneRule() throws Exception {
    final K8sDestinationAudienceValidator validator = init(ingressRoutingParams());
    final JWT noAudience = nonDelegationToken((String[]) null);
    // No NONE in the list: rejected by the list check, which carries no message, so this
    // validator's own "token has no aud claim" rejection never ran.
    final AudienceValidationResult withoutNone = validate(validator, INGRESS_SPIFFE_ID, noAudience,
        CONFIGURED_AUDIENCES);
    assertFalse(withoutNone.isValid());
    assertNull(withoutNone.message());
    // NONE in the list: accepted, which destination validation never does for a token with no aud.
    assertAccepted(validate(validator, INGRESS_SPIFFE_ID, noAudience, Arrays.asList(LISTED_AUD, "NONE")));
    // Contrast: any other source with the same token is rejected by this validator, with a message.
    assertRejected(validate(validator, NON_INGRESS_SPIFFE_ID, noAudience, Arrays.asList(LISTED_AUD, "NONE")),
        "no aud claim");
  }

  // ---- routing on, skip.validation true: ingress source, no act claim ----

  @Test
  public void testSkipValidationSkipsAudienceValidationForIngressSourceWithoutActClaim() throws Exception {
    assertAudienceValidationSkipped(init(skipValidationParams()), INGRESS_SPIFFE_ID);
  }

  @Test
  public void testSkipValidationPrecedesTheRequirementThatConfiguredHeadersBePresent() throws Exception {
    final Map<String, String> params = skipValidationParams();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    // The configured path header is missing from the request, yet the skipping route accepts it...
    assertAudienceValidationSkipped(validator, INGRESS_SPIFFE_ID);
    // ...while the same missing header is a rejection for any other source.
    assertRejected(validate(validator, NON_INGRESS_SPIFFE_ID, nonDelegationToken(MATCHING_AUD), null),
        PATH_HEADER);
  }

  @Test
  public void testSkipValidationHasNoEffectWhenRoutingDisabled() throws Exception {
    // The ingress list and skip.validation are set, but routing is off: an ingress source is
    // destination-validated like any other.
    assertDestinationValidated(init(routingDisabledParams()), INGRESS_SPIFFE_ID, NON_DELEGATION);
  }

  @Test
  public void testSkipValidationStillSkipsWhenValidateWithoutActClaimFalse() throws Exception {
    final Map<String, String> params = skipValidationParams();
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
  public void testMissingSourceHeaderIsNotRoutedAndIsRejectedAsMissing() throws Exception {
    final K8sDestinationAudienceValidator validator = init(skipValidationParams());
    assertRejected(validate(validator, null, nonDelegationToken(MATCHING_AUD), CONFIGURED_AUDIENCES),
        MISSING_SOURCE_MESSAGE);
  }

  @Test
  public void testUnparseableSourceHeaderIsNotRoutedAndIsRejectedAsMissing() throws Exception {
    final K8sDestinationAudienceValidator validator = init(skipValidationParams());
    assertRejected(validate(validator, "not-a-spiffe-id", nonDelegationToken(MATCHING_AUD), CONFIGURED_AUDIENCES),
        MISSING_SOURCE_MESSAGE);
  }

  @Test
  public void testMissingSourceHeaderIsCheckedAgainstConfiguredAudiencesWhenValidateWithoutActClaimFalse()
      throws Exception {
    final Map<String, String> params = skipValidationParams();
    params.put(K8sDestinationAudienceValidator.VALIDATE_AUDIENCES_WITHOUT_ACT_CLAIM_PARAM, "false");
    // Not skipped, although skip.validation is true: with no source header there is no ingress.
    assertConfiguredAudiencesChecked(init(params), null);
  }

  @Test
  public void testSourceMatchingNoneOfSeveralConfiguredIngressIdsIsDestinationValidated() throws Exception {
    final Map<String, String> params = skipValidationParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM,
        INGRESS_SPIFFE_ID + "," + SECOND_INGRESS_SPIFFE_ID);
    assertDestinationValidated(init(params), NON_INGRESS_SPIFFE_ID, NON_DELEGATION);
  }

  @Test
  public void testSourceMatchingTheSecondConfiguredIngressIdIsRouted() throws Exception {
    final Map<String, String> params = skipValidationParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM,
        INGRESS_SPIFFE_ID + "," + SECOND_INGRESS_SPIFFE_ID);
    assertAudienceValidationSkipped(init(params), SECOND_INGRESS_SPIFFE_ID);
  }

  // ---- trust-domain matching ----

  @Test
  public void testDifferentTrustDomainIsNotAnIngressSourceByDefault() throws Exception {
    assertDestinationValidated(init(skipValidationParams()), INGRESS_SPIFFE_OTHER_TRUST_DOMAIN, NON_DELEGATION);
  }

  @Test
  public void testDifferentTrustDomainIsAnIngressSourceWhenTrustDomainMatchSkipped() throws Exception {
    final Map<String, String> params = skipValidationParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_ROUTING_SKIP_TRUST_DOMAIN_MATCH_PARAM,
        "true");
    assertAudienceValidationSkipped(init(params), INGRESS_SPIFFE_OTHER_TRUST_DOMAIN);
  }

  @Test
  public void testSkippingTrustDomainMatchStillRequiresNamespaceToMatch() throws Exception {
    final Map<String, String> params = skipValidationParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_ROUTING_SKIP_TRUST_DOMAIN_MATCH_PARAM,
        "true");
    assertDestinationValidated(init(params), "spiffe://other-domain/ns/other-ns/sa/ingressgateway", NON_DELEGATION);
  }

  @Test
  public void testSkippingTrustDomainMatchStillRequiresServiceAccountToMatch() throws Exception {
    final Map<String, String> params = skipValidationParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_ROUTING_SKIP_TRUST_DOMAIN_MATCH_PARAM,
        "true");
    assertDestinationValidated(init(params), "spiffe://other-domain/ns/istio-system/sa/not-the-gateway",
        NON_DELEGATION);
  }

  @Test
  public void testIngressSpiffeIdMatchingIsCaseInsensitive() throws Exception {
    final Map<String, String> mixedCaseConfigured = skipValidationParams();
    mixedCaseConfigured.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM,
        "spiffe://Trust-Domain/ns/Istio-System/sa/IngressGateway");
    assertAudienceValidationSkipped(init(mixedCaseConfigured), INGRESS_SPIFFE_ID);

    assertAudienceValidationSkipped(init(skipValidationParams()),
        "spiffe://Trust-Domain/ns/Istio-System/sa/IngressGateway");
  }

  @Test
  public void testSkipTrustDomainMatchHasNoEffectWhenRoutingDisabled() throws Exception {
    assertDestinationValidated(init(routingDisabledParams()), INGRESS_SPIFFE_OTHER_TRUST_DOMAIN, NON_DELEGATION);
  }

  // ---- a token with an act claim from an ingress source ----

  @Test
  public void testDelegationTokenFromIngressSourceIsRejected() throws Exception {
    final K8sDestinationAudienceValidator validator = init(ingressRoutingParams());
    assertIngressDelegationRejected(validate(validator, INGRESS_SPIFFE_ID, delegationToken(MATCHING_AUD), null));
  }

  @Test
  public void testDelegationTokenFromIngressSourceIsNotRoutedToConfiguredAudiences() throws Exception {
    final K8sDestinationAudienceValidator validator = init(ingressRoutingParams());
    // LISTED_AUD is in the configured list, which would accept a token with no act claim.
    assertIngressDelegationRejected(
        validate(validator, INGRESS_SPIFFE_ID, delegationToken(LISTED_AUD), CONFIGURED_AUDIENCES));
  }

  @Test
  public void testDelegationTokenFromIngressSourceIsRejectedEvenWhenSkipValidationTrue() throws Exception {
    final K8sDestinationAudienceValidator validator = init(skipValidationParams());
    assertIngressDelegationRejected(validate(validator, INGRESS_SPIFFE_ID, delegationToken(MATCHING_AUD), null));
  }

  @Test
  public void testDelegationTokenFromIngressSourceIsRejectedAsIngressWhenItHasNoAudClaim() throws Exception {
    final K8sDestinationAudienceValidator validator = init(ingressRoutingParams());
    // The rejection for a missing aud claim would otherwise have been reported first.
    assertIngressDelegationRejected(validate(validator, INGRESS_SPIFFE_ID, delegationToken((String[]) null), null));
    assertRejected(validate(validator, NON_INGRESS_SPIFFE_ID, delegationToken((String[]) null), null),
        "Token has an act claim but no aud claim");
  }

  @Test
  public void testDelegationTokenFromIngressSourceIsDestinationValidatedWhenRoutingDisabled() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, INGRESS_SPIFFE_ID);
    assertDestinationValidated(init(params), INGRESS_SPIFFE_ID, DELEGATION);
  }

  @Test
  public void testDelegationTokenFromNonIngressSourceIsDestinationValidated() throws Exception {
    assertDestinationValidated(init(ingressRoutingParams()), NON_INGRESS_SPIFFE_ID, DELEGATION);
  }

  @Test
  public void testMalformedActClaimFromIngressSourceIsRejectedAsIngress() throws Exception {
    final K8sDestinationAudienceValidator validator = init(ingressRoutingParams());
    assertIngressDelegationRejected(validate(validator, INGRESS_SPIFFE_ID,
        delegationTokenWithActClaim("not-a-map", MATCHING_AUD), null));
  }

  @Test
  public void testDelegationTokenFromIngressSourceRejectedBeforeActSubBindingRuns() throws Exception {
    final Map<String, String> params = ingressRoutingParams();
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    // The act sub names a different service account than the source, so if the binding check ran
    // first the rejection would be about the actor mismatch rather than about the ingress.
    assertIngressDelegationRejected(validate(validator, INGRESS_SPIFFE_ID,
        delegationTokenWithActClaim(actorChain("system:serviceaccount:other-ns:other-sa"),
            MATCHING_AUD), null));
    // Contrast: the same token from a non-ingress source is rejected for the actor mismatch.
    assertRejected(validate(validator, NON_INGRESS_SPIFFE_ID,
        delegationTokenWithActClaim(actorChain("system:serviceaccount:other-ns:other-sa"),
            MATCHING_AUD), null), "does not match source SPIFFE id service account");
  }

  // ---- the destination-validation checks that follow the routing ----

  @Test
  public void testRequireAllAudiencesMatchStillAppliesWhenRoutingEnabledButNotIngress() throws Exception {
    final Map<String, String> params = ingressRoutingParams();
    final JWT matchingAndOtherNamespace = delegationToken(MATCHING_AUD, LISTED_AUD);
    // Without the flag one matching audience is enough...
    assertAccepted(validate(init(params), NON_INGRESS_SPIFFE_ID, matchingAndOtherNamespace, null));
    // ...with it, the second audience, which names another namespace, causes a rejection.
    params.put(K8sDestinationAudienceValidator.REQUIRE_ALL_AUDIENCES_MATCH_PARAM, "true");
    assertRejected(validate(init(params), NON_INGRESS_SPIFFE_ID, matchingAndOtherNamespace, null),
        DESTINATION_MISMATCH_MESSAGE);
  }

  @Test
  public void testRequireAllAudiencesMatchDoesNotApplyToTheConfiguredAudiencesCheck() throws Exception {
    final Map<String, String> params = ingressRoutingParams();
    params.put(K8sDestinationAudienceValidator.REQUIRE_ALL_AUDIENCES_MATCH_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    // UNRELATED_AUD is in neither the list nor the destination, so a require-all check would
    // reject this token; the list check accepts it because one audience is in the list.
    assertAccepted(validate(validator, INGRESS_SPIFFE_ID, nonDelegationToken(LISTED_AUD, UNRELATED_AUD),
        CONFIGURED_AUDIENCES));
  }

  @Test
  public void testBothNamespaceSourcesStillMustAgreeWhenRoutingEnabledButNotIngress() throws Exception {
    final Map<String, String> params = ingressRoutingParams();
    params.put(K8sDestinationAudienceValidator.SERVER_NAME_HEADER_PARAM, SERVER_NAME_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    final Map<String, String> headers = headersFrom(NON_INGRESS_SPIFFE_ID);
    headers.put(SERVER_NAME_HEADER, "svc.other-ns" + CLUSTER_SUFFIX);
    assertRejected(validator.validate(requestWithHeaders(headers), nonDelegationToken(MATCHING_AUD), null),
        "disagrees with namespace");
  }
}
