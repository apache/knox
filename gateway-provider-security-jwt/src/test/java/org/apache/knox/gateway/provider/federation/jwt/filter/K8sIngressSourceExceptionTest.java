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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.Test;

import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.actorChain;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.delegationToken;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.delegationTokenWithActClaim;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.filterConfig;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.nonDelegationToken;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.requestWithHeaders;

/**
 * Covers the ingress-source exception: the configuration it requires at initialization, which
 * tokens it applies to, the two forms the exemption can take (no audience validation at all, or
 * validation against the fixed configured allowed-audience list), how an allowed ingress identity
 * is matched against the request's source SPIFFE id, and the rejection of a token carrying an
 * {@code act} claim that arrives from an ingress source.
 */
public class K8sIngressSourceExceptionTest {

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
  private static final String MATCHING_AUD = "https://svc.ns";
  private static final String OTHER_NAMESPACE_AUD = "https://svc.other-ns";
  private static final String UNRELATED_AUD = "https://somewhere.else.example/nothing/to/do/with/this";
  private static final String WHITELIST_AUD = "configured-audience";
  private static final String MISSING_SOURCE_MESSAGE = "Missing or unparseable source SPIFFE id in header";

  private static K8sDestinationAudienceValidator init(final Map<String, String> params) throws Exception {
    final K8sDestinationAudienceValidator validator = new K8sDestinationAudienceValidator();
    validator.init(filterConfig(params));
    return validator;
  }

  private static Map<String, String> baseParams() {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    return params;
  }

  private static Map<String, String> exceptionParams() {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_ID_EXCEPTION_ENABLED_PARAM, "true");
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, INGRESS_SPIFFE_ID);
    return params;
  }

  private static Map<String, String> skipValidationParams() {
    final Map<String, String> params = exceptionParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_SKIP_VALIDATION_PARAM, "true");
    return params;
  }

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

  // ---- init() requirements ----

  @Test
  public void testInitThrowsWhenExceptionEnabledButSourceHeaderUnconfigured() {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_ID_EXCEPTION_ENABLED_PARAM, "true");
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, INGRESS_SPIFFE_ID);
    final ServletException e = assertThrows(ServletException.class, () -> init(params));
    assertTrue(e.getMessage().contains(
        K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_ID_EXCEPTION_ENABLED_PARAM));
    assertTrue(e.getMessage().contains(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM));
  }

  @Test
  public void testInitThrowsWhenExceptionEnabledButIngressIdListUnconfigured() {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_ID_EXCEPTION_ENABLED_PARAM, "true");
    final ServletException e = assertThrows(ServletException.class, () -> init(params));
    assertTrue(e.getMessage().contains(
        K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_ID_EXCEPTION_ENABLED_PARAM));
    assertTrue(e.getMessage().contains(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM));
  }

  @Test
  public void testInitThrowsWhenExceptionEnabledAndIngressIdListIsBlank() {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_ID_EXCEPTION_ENABLED_PARAM, "true");
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, "   ");
    final ServletException e = assertThrows(ServletException.class, () -> init(params));
    assertTrue(e.getMessage().contains(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM));
  }

  @Test
  public void testInitSucceedsWhenExceptionEnabledWithSourceHeaderAndIngressIdList() throws Exception {
    init(exceptionParams());
  }

  @Test
  public void testInitThrowsOnUnparseableIngressSpiffeIdEntry() {
    final Map<String, String> params = exceptionParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, "not-a-spiffe-id");
    final ServletException e = assertThrows(ServletException.class, () -> init(params));
    assertTrue(e.getMessage().contains(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM));
    assertTrue(e.getMessage().contains("not-a-spiffe-id"));
  }

  @Test
  public void testInitThrowsOnIngressSpiffeIdEntryWithoutNamespaceAndServiceAccountPath() {
    final Map<String, String> params = exceptionParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, "spiffe://trust-domain/ns/only");
    assertThrows(ServletException.class, () -> init(params));
  }

  @Test
  public void testInitThrowsOnIngressSpiffeIdsWithStrayInteriorComma() {
    final Map<String, String> params = exceptionParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM,
        INGRESS_SPIFFE_ID + ",," + SECOND_INGRESS_SPIFFE_ID);
    assertThrows(ServletException.class, () -> init(params));
  }

  @Test
  public void testInitThrowsOnIngressSpiffeIdsWithTrailingComma() {
    final Map<String, String> params = exceptionParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, INGRESS_SPIFFE_ID + ",");
    assertThrows(ServletException.class, () -> init(params));
  }

  @Test
  public void testInitThrowsOnIngressSpiffeIdsWithLeadingComma() {
    final Map<String, String> params = exceptionParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, "," + INGRESS_SPIFFE_ID);
    assertThrows(ServletException.class, () -> init(params));
  }

  @Test
  public void testInitSucceedsWithTwoIngressSpiffeIdEntries() throws Exception {
    final Map<String, String> params = exceptionParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM,
        "  " + INGRESS_SPIFFE_ID + "  ,  " + SECOND_INGRESS_SPIFFE_ID + "  ");
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_SKIP_VALIDATION_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    // Both entries were trimmed on the way in: a source naming either one is exempted.
    assertTrue(validator.validate(requestFrom(INGRESS_SPIFFE_ID), nonDelegationToken(OTHER_NAMESPACE_AUD), null)
        .isValid());
    assertTrue(validator.validate(requestFrom(SECOND_INGRESS_SPIFFE_ID),
        nonDelegationToken(OTHER_NAMESPACE_AUD), null).isValid());
  }

  @Test
  public void testInitSucceedsWithIngressIdListConfiguredWhileExceptionDisabled() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, INGRESS_SPIFFE_ID);
    init(params);
  }

  @Test
  public void testInitSucceedsWithSkipFlagsSetWhileExceptionDisabled() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_SKIP_VALIDATION_PARAM, "true");
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_SKIP_TRUST_DOMAIN_MATCH_PARAM, "true");
    init(params);
  }

  @Test
  public void testInitThrowsOnUnparseableIngressSpiffeIdEntryEvenWhenExceptionDisabled() {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, "not-a-spiffe-id");
    assertThrows(ServletException.class, () -> init(params));
  }

  // ---- exception on, skip.validation at its default of false ----

  @Test
  public void testIngressSourceWithoutActClaimIsValidatedAgainstConfiguredAudiences() throws Exception {
    final K8sDestinationAudienceValidator validator = init(exceptionParams());
    assertTrue(validator.validate(requestFrom(INGRESS_SPIFFE_ID), nonDelegationToken(WHITELIST_AUD),
        Collections.singletonList(WHITELIST_AUD)).isValid());
  }

  @Test
  public void testIngressSourceWithoutActClaimRejectedWhenAudienceNotInConfiguredList() throws Exception {
    final K8sDestinationAudienceValidator validator = init(exceptionParams());
    assertFalse(validator.validate(requestFrom(INGRESS_SPIFFE_ID), nonDelegationToken(WHITELIST_AUD),
        Collections.singletonList("some-other-audience")).isValid());
  }

  @Test
  public void testIngressSourceWithoutActClaimIsNotDestinationValidated() throws Exception {
    final K8sDestinationAudienceValidator validator = init(exceptionParams());
    // MATCHING_AUD would pass destination validation but is not in the configured list: the list
    // replaces destination matching rather than being tried in addition to it.
    assertFalse(validator.validate(requestFrom(INGRESS_SPIFFE_ID), nonDelegationToken(MATCHING_AUD),
        Collections.singletonList(WHITELIST_AUD)).isValid());
  }

  @Test
  public void testIngressSourceWithoutActClaimAcceptedWhenConfiguredAudiencesNull() throws Exception {
    final K8sDestinationAudienceValidator validator = init(exceptionParams());
    // The fixed-list check treats a null configured list as "any audience is acceptable".
    assertTrue(validator.validate(requestFrom(INGRESS_SPIFFE_ID), nonDelegationToken(WHITELIST_AUD), null)
        .isValid());
  }

  @Test
  public void testIngressSourceWithoutActClaimAndNoAudClaimReachesConfiguredAudiences() throws Exception {
    final K8sDestinationAudienceValidator validator = init(exceptionParams());
    // Reaching the "NONE" handling in the fixed-list check proves the ingress route runs before this
    // validator's own rejection of a token with no aud claim.
    assertTrue(validator.validate(requestFrom(INGRESS_SPIFFE_ID), nonDelegationToken((String[]) null),
        Collections.singletonList("NONE")).isValid());
  }

  @Test
  public void testIngressSourceWithoutActClaimAndNoAudClaimRejectedWithoutNoneSentinel() throws Exception {
    final K8sDestinationAudienceValidator validator = init(exceptionParams());
    final AudienceValidationResult result = validator.validate(requestFrom(INGRESS_SPIFFE_ID),
        nonDelegationToken((String[]) null), Collections.singletonList(WHITELIST_AUD));
    assertFalse(result.isValid());
    // The fixed-list check carries no message, which also shows this validator's own aud-presence
    // rejection did not run.
    assertNull(result.message());
  }

  // ---- exception on, skip.validation true ----

  @Test
  public void testSkipValidationAcceptsIngressSourceWithUnrelatedAudience() throws Exception {
    final K8sDestinationAudienceValidator validator = init(skipValidationParams());
    assertTrue(validator.validate(requestFrom(INGRESS_SPIFFE_ID), nonDelegationToken(UNRELATED_AUD),
        Collections.singletonList(WHITELIST_AUD)).isValid());
  }

  @Test
  public void testSkipValidationAcceptsIngressSourceWithNoAudClaimAtAll() throws Exception {
    final K8sDestinationAudienceValidator validator = init(skipValidationParams());
    assertTrue(validator.validate(requestFrom(INGRESS_SPIFFE_ID), nonDelegationToken((String[]) null), null)
        .isValid());
    assertTrue(validator.validate(requestFrom(INGRESS_SPIFFE_ID), nonDelegationToken((String[]) null),
        Collections.singletonList(WHITELIST_AUD)).isValid());
  }

  @Test
  public void testSkipValidationResultCarriesNoMessage() throws Exception {
    final K8sDestinationAudienceValidator validator = init(skipValidationParams());
    final AudienceValidationResult result = validator.validate(requestFrom(INGRESS_SPIFFE_ID),
        nonDelegationToken(UNRELATED_AUD), Collections.singletonList(WHITELIST_AUD));
    assertTrue(result.isValid());
    assertNull(result.message());
  }

  @Test
  public void testSkipValidationAcceptsIngressSourceWhenConfiguredPathHeaderIsAbsent() throws Exception {
    final Map<String, String> params = skipValidationParams();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    // The exemption precedes the rule that every configured header be present, so an exempted
    // request is accepted even though the configured path header is missing from it.
    assertTrue(validator.validate(requestFrom(INGRESS_SPIFFE_ID), nonDelegationToken((String[]) null), null)
        .isValid());
  }

  @Test
  public void testSkipValidationHasNoEffectWhenExceptionDisabled() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_SKIP_VALIDATION_PARAM, "true");
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, INGRESS_SPIFFE_ID);
    final K8sDestinationAudienceValidator validator = init(params);
    assertFalse(validator.validate(requestFrom(INGRESS_SPIFFE_ID), nonDelegationToken(OTHER_NAMESPACE_AUD), null)
        .isValid());
  }

  // ---- exception on, source is not an ingress identity ----

  @Test
  public void testNonIngressSourceWithoutActClaimIsDestinationValidatedAndAccepted() throws Exception {
    final K8sDestinationAudienceValidator validator = init(exceptionParams());
    assertTrue(validator.validate(requestFrom(NON_INGRESS_SPIFFE_ID), nonDelegationToken(MATCHING_AUD), null)
        .isValid());
  }

  @Test
  public void testNonIngressSourceWithoutActClaimIsDestinationValidatedAndRejected() throws Exception {
    final K8sDestinationAudienceValidator validator = init(exceptionParams());
    final AudienceValidationResult result =
        validator.validate(requestFrom(NON_INGRESS_SPIFFE_ID), nonDelegationToken(OTHER_NAMESPACE_AUD), null);
    assertFalse(result.isValid());
    assertTrue(result.message().contains("does not match the destination"));
  }

  @Test
  public void testNonIngressSourceWithoutActClaimFallsBackToWhitelistWhenConfigured() throws Exception {
    final Map<String, String> params = exceptionParams();
    params.put(K8sDestinationAudienceValidator.VALIDATE_AUDIENCES_WITHOUT_ACT_CLAIM_PARAM, "false");
    final K8sDestinationAudienceValidator validator = init(params);
    assertTrue(validator.validate(requestFrom(NON_INGRESS_SPIFFE_ID), nonDelegationToken(WHITELIST_AUD),
        Collections.singletonList(WHITELIST_AUD)).isValid());
  }

  @Test
  public void testExceptionEnabledWithMissingSourceHeaderIsRejected() throws Exception {
    final K8sDestinationAudienceValidator validator = init(exceptionParams());
    final AudienceValidationResult result =
        validator.validate(requestFrom(null), nonDelegationToken(MATCHING_AUD), null);
    assertFalse(result.isValid());
    assertTrue(result.message().contains(MISSING_SOURCE_MESSAGE));
  }

  @Test
  public void testExceptionEnabledWithUnparseableSourceHeaderIsRejected() throws Exception {
    final K8sDestinationAudienceValidator validator = init(exceptionParams());
    final AudienceValidationResult result =
        validator.validate(requestFrom("not-a-spiffe-id"), nonDelegationToken(MATCHING_AUD), null);
    assertFalse(result.isValid());
    assertTrue(result.message().contains(MISSING_SOURCE_MESSAGE));
  }

  @Test
  public void testMissingSourceHeaderFallsBackToWhitelistWhenValidateWithoutActClaimFalse() throws Exception {
    final Map<String, String> params = exceptionParams();
    params.put(K8sDestinationAudienceValidator.VALIDATE_AUDIENCES_WITHOUT_ACT_CLAIM_PARAM, "false");
    final K8sDestinationAudienceValidator validator = init(params);
    assertTrue(validator.validate(requestFrom(null), nonDelegationToken(WHITELIST_AUD),
        Collections.singletonList(WHITELIST_AUD)).isValid());
  }

  @Test
  public void testSkipValidationAcceptsIngressSourceWhenValidateWithoutActClaimFalse() throws Exception {
    final Map<String, String> params = skipValidationParams();
    params.put(K8sDestinationAudienceValidator.VALIDATE_AUDIENCES_WITHOUT_ACT_CLAIM_PARAM, "false");
    final K8sDestinationAudienceValidator validator = init(params);
    assertTrue(validator.validate(requestFrom(INGRESS_SPIFFE_ID), nonDelegationToken(UNRELATED_AUD),
        Collections.singletonList(WHITELIST_AUD)).isValid());
  }

  @Test
  public void testSourceMatchingNoneOfSeveralConfiguredIngressIdsIsNotExempted() throws Exception {
    final Map<String, String> params = skipValidationParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM,
        INGRESS_SPIFFE_ID + "," + SECOND_INGRESS_SPIFFE_ID);
    final K8sDestinationAudienceValidator validator = init(params);
    assertFalse(validator.validate(requestFrom(NON_INGRESS_SPIFFE_ID), nonDelegationToken(OTHER_NAMESPACE_AUD),
        null).isValid());
  }

  @Test
  public void testSourceMatchingTheSecondConfiguredIngressIdIsExempted() throws Exception {
    final Map<String, String> params = skipValidationParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM,
        INGRESS_SPIFFE_ID + "," + SECOND_INGRESS_SPIFFE_ID);
    final K8sDestinationAudienceValidator validator = init(params);
    assertTrue(validator.validate(requestFrom(SECOND_INGRESS_SPIFFE_ID), nonDelegationToken(OTHER_NAMESPACE_AUD),
        null).isValid());
  }

  // ---- trust-domain matching ----

  @Test
  public void testDifferentTrustDomainIsNotAnIngressSourceByDefault() throws Exception {
    final K8sDestinationAudienceValidator validator = init(skipValidationParams());
    assertFalse(validator.validate(requestFrom(INGRESS_SPIFFE_OTHER_TRUST_DOMAIN),
        nonDelegationToken(OTHER_NAMESPACE_AUD), null).isValid());
  }

  @Test
  public void testDifferentTrustDomainIsAnIngressSourceWhenTrustDomainMatchSkipped() throws Exception {
    final Map<String, String> params = skipValidationParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_SKIP_TRUST_DOMAIN_MATCH_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    assertTrue(validator.validate(requestFrom(INGRESS_SPIFFE_OTHER_TRUST_DOMAIN),
        nonDelegationToken(OTHER_NAMESPACE_AUD), null).isValid());
  }

  @Test
  public void testSkippingTrustDomainMatchStillRequiresNamespaceToMatch() throws Exception {
    final Map<String, String> params = skipValidationParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_SKIP_TRUST_DOMAIN_MATCH_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    assertFalse(validator.validate(requestFrom("spiffe://other-domain/ns/other-ns/sa/ingressgateway"),
        nonDelegationToken(OTHER_NAMESPACE_AUD), null).isValid());
  }

  @Test
  public void testSkippingTrustDomainMatchStillRequiresServiceAccountToMatch() throws Exception {
    final Map<String, String> params = skipValidationParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_SKIP_TRUST_DOMAIN_MATCH_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    assertFalse(validator.validate(requestFrom("spiffe://other-domain/ns/istio-system/sa/not-the-gateway"),
        nonDelegationToken(OTHER_NAMESPACE_AUD), null).isValid());
  }

  @Test
  public void testIngressSpiffeIdMatchingIsCaseInsensitive() throws Exception {
    final Map<String, String> mixedCaseConfigured = skipValidationParams();
    mixedCaseConfigured.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM,
        "spiffe://Trust-Domain/ns/Istio-System/sa/IngressGateway");
    assertTrue(init(mixedCaseConfigured).validate(requestFrom(INGRESS_SPIFFE_ID),
        nonDelegationToken(OTHER_NAMESPACE_AUD), null).isValid());

    assertTrue(init(skipValidationParams()).validate(
        requestFrom("spiffe://Trust-Domain/ns/Istio-System/sa/IngressGateway"),
        nonDelegationToken(OTHER_NAMESPACE_AUD), null).isValid());
  }

  @Test
  public void testSkipTrustDomainMatchHasNoEffectWhenExceptionDisabled() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, INGRESS_SPIFFE_ID);
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_SKIP_TRUST_DOMAIN_MATCH_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    assertFalse(validator.validate(requestFrom(INGRESS_SPIFFE_OTHER_TRUST_DOMAIN),
        nonDelegationToken(OTHER_NAMESPACE_AUD), null).isValid());
  }

  // ---- a token with an act claim from an ingress source ----

  @Test
  public void testDelegationTokenFromIngressSourceIsRejected() throws Exception {
    final K8sDestinationAudienceValidator validator = init(exceptionParams());
    final AudienceValidationResult result =
        validator.validate(requestFrom(INGRESS_SPIFFE_ID), delegationToken(MATCHING_AUD), null);
    assertFalse(result.isValid());
    assertTrue(result.message().contains("act claim"));
    assertTrue(result.message().contains("ingress"));
    assertTrue(result.message().contains(SOURCE_SPIFFE_HEADER));
  }

  @Test
  public void testDelegationTokenFromIngressSourceIsRejectedEvenWhenSkipValidationTrue() throws Exception {
    final K8sDestinationAudienceValidator validator = init(skipValidationParams());
    assertFalse(validator.validate(requestFrom(INGRESS_SPIFFE_ID), delegationToken(MATCHING_AUD), null)
        .isValid());
  }

  @Test
  public void testDelegationTokenFromIngressSourceIsRejectedWithNoAudClaim() throws Exception {
    final K8sDestinationAudienceValidator validator = init(exceptionParams());
    final AudienceValidationResult result =
        validator.validate(requestFrom(INGRESS_SPIFFE_ID), delegationToken((String[]) null), null);
    assertFalse(result.isValid());
    assertTrue(result.message().contains("ingress"));
    assertFalse(result.message().contains("Token has an act claim but no aud claim"));
  }

  @Test
  public void testDelegationTokenFromIngressSourceIsAcceptedWhenExceptionDisabled() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.INGRESS_SOURCE_SPIFFE_IDS_PARAM, INGRESS_SPIFFE_ID);
    final K8sDestinationAudienceValidator validator = init(params);
    assertTrue(validator.validate(requestFrom(INGRESS_SPIFFE_ID), delegationToken(MATCHING_AUD), null).isValid());
  }

  @Test
  public void testDelegationTokenFromNonIngressSourceIsDestinationValidated() throws Exception {
    final K8sDestinationAudienceValidator validator = init(exceptionParams());
    assertTrue(validator.validate(requestFrom(NON_INGRESS_SPIFFE_ID), delegationToken(MATCHING_AUD), null)
        .isValid());
  }

  @Test
  public void testMalformedActClaimFromIngressSourceIsRejected() throws Exception {
    final K8sDestinationAudienceValidator validator = init(exceptionParams());
    final AudienceValidationResult result = validator.validate(requestFrom(INGRESS_SPIFFE_ID),
        delegationTokenWithActClaim("not-a-map", MATCHING_AUD), null);
    assertFalse(result.isValid());
    assertTrue(result.message().contains("ingress"));
  }

  @Test
  public void testDelegationTokenFromIngressSourceRejectedBeforeActSubBindingRuns() throws Exception {
    final Map<String, String> params = exceptionParams();
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    // The act sub names the ingress service account, so the binding check on its own would pass.
    final AudienceValidationResult result = validator.validate(requestFrom(INGRESS_SPIFFE_ID),
        delegationTokenWithActClaim(actorChain("system:serviceaccount:istio-system:ingressgateway"),
            MATCHING_AUD), null);
    assertFalse(result.isValid());
    assertTrue(result.message().contains("ingress"));
  }

  // ---- the destination-validation checks that follow the routing ----

  @Test
  public void testRequireAllAudiencesMatchStillAppliesWhenExceptionEnabledButNotIngress() throws Exception {
    final Map<String, String> params = exceptionParams();
    params.put(K8sDestinationAudienceValidator.REQUIRE_ALL_AUDIENCES_MATCH_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    assertFalse(validator.validate(requestFrom(NON_INGRESS_SPIFFE_ID),
        delegationToken(MATCHING_AUD, OTHER_NAMESPACE_AUD), null).isValid());
  }

  @Test
  public void testRequireAllAudiencesMatchDoesNotApplyToTheWhitelistRoute() throws Exception {
    final Map<String, String> params = exceptionParams();
    params.put(K8sDestinationAudienceValidator.REQUIRE_ALL_AUDIENCES_MATCH_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    // The fixed-list check accepts a token when any one of its audiences is in the list.
    assertTrue(validator.validate(requestFrom(INGRESS_SPIFFE_ID),
        nonDelegationToken(WHITELIST_AUD, UNRELATED_AUD), Arrays.asList(WHITELIST_AUD, "another-audience"))
        .isValid());
  }

  @Test
  public void testBothNamespaceSourcesStillMustAgreeWhenExceptionEnabledButNotIngress() throws Exception {
    final Map<String, String> params = exceptionParams();
    params.put(K8sDestinationAudienceValidator.SERVER_NAME_HEADER_PARAM, SERVER_NAME_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    final Map<String, String> headers = headersFrom(NON_INGRESS_SPIFFE_ID);
    headers.put(SERVER_NAME_HEADER, "svc.other-ns" + CLUSTER_SUFFIX);
    final AudienceValidationResult result =
        validator.validate(requestWithHeaders(headers), nonDelegationToken(MATCHING_AUD), null);
    assertFalse(result.isValid());
    assertTrue(result.message().contains("disagrees with namespace"));
  }
}
