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
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.junit.Test;

import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.actorChain;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.delegationTokenWithActClaim;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.filterConfig;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.nonDelegationToken;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.requestWithHeaders;

/**
 * Covers the actor-sub binding check: {@link
 * K8sDestinationAudienceValidator#ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM} nesting {@link
 * K8sDestinationAudienceValidator#ENFORCE_ACT_SUB_IS_SERVICE_ACCOUNT_PARAM}, the unconditional
 * source-header presence/parseability requirement, act-chain ordering (only the most recent actor
 * is ever compared), and malformed-chain/missing-sub handling.
 *
 * <p>Every test here uses the same destination and {@code aud}: destination namespace {@code ns}
 * from {@link #SPIFFE_HEADER}, and a two-label DNS-form {@code aud} of {@code https://svc.ns},
 * which matches that destination regardless of service name or path (both left unconfigured, so
 * neither is compared). This keeps the act-sub check the only variable under test.
 */
public class K8sActorSubjectBindingTest {

  private static final String SPIFFE_HEADER = "x-dest-spiffe-id";
  private static final String SOURCE_SPIFFE_HEADER = "x-source-spiffe-id";
  private static final String DEST_SPIFFE_VALUE = "spiffe://trust-domain/ns/ns/sa/dest-sa";
  private static final String SOURCE_SPIFFE_VALUE = "spiffe://trust-domain/ns/ns/sa/caller-sa";
  private static final String MATCHING_ACTOR_SUB = "system:serviceaccount:ns:caller-sa";
  private static final String AUD = "https://svc.ns";

  private static K8sDestinationAudienceValidator init(final Map<String, String> params) throws Exception {
    final K8sDestinationAudienceValidator validator = new K8sDestinationAudienceValidator();
    validator.init(filterConfig(params));
    return validator;
  }

  private static Map<String, String> baseParams() {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    return params;
  }

  private static Map<String, String> baseHeaders() {
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, DEST_SPIFFE_VALUE);
    return headers;
  }

  // ---- matches=false disables the whole check, including is.service.account ----

  @Test
  public void testMatchesFalseDisablesCheckEvenWhenActorMismatchesSource() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_IS_SERVICE_ACCOUNT_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final Object actClaim = actorChain("system:serviceaccount:other-ns:other-sa");
    final HttpServletRequest request = requestWithHeaders(baseHeaders());
    assertTrue(validator.validate(request, delegationTokenWithActClaim(actClaim, AUD), null).isValid());
  }

  @Test
  public void testMatchesFalseDisablesCheckEvenWhenActorNotServiceAccountForm() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_IS_SERVICE_ACCOUNT_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final Object actClaim = actorChain("plain-user");
    final HttpServletRequest request = requestWithHeaders(baseHeaders());
    assertTrue(validator.validate(request, delegationTokenWithActClaim(actClaim, AUD), null).isValid());
  }

  // ---- matches=true, actor sub is a service account ----

  @Test
  public void testMatchesTrueActorMatchesSourcePasses() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final Object actClaim = actorChain(MATCHING_ACTOR_SUB);
    final Map<String, String> headers = baseHeaders();
    headers.put(SOURCE_SPIFFE_HEADER, SOURCE_SPIFFE_VALUE);
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, delegationTokenWithActClaim(actClaim, AUD), null).isValid());
  }

  @Test
  public void testMatchesTrueNamespaceMismatchFails() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final Object actClaim = actorChain("system:serviceaccount:other-ns:caller-sa");
    final Map<String, String> headers = baseHeaders();
    headers.put(SOURCE_SPIFFE_HEADER, SOURCE_SPIFFE_VALUE);
    final HttpServletRequest request = requestWithHeaders(headers);
    final AudienceValidationResult result = validator.validate(request, delegationTokenWithActClaim(actClaim, AUD), null);
    assertFalse(result.isValid());
    assertTrue(result.message().contains("other-ns:caller-sa"));
    assertTrue(result.message().contains("ns:caller-sa"));
  }

  @Test
  public void testMatchesTrueServiceAccountNameMismatchFails() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final Object actClaim = actorChain("system:serviceaccount:ns:other-sa");
    final Map<String, String> headers = baseHeaders();
    headers.put(SOURCE_SPIFFE_HEADER, SOURCE_SPIFFE_VALUE);
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, delegationTokenWithActClaim(actClaim, AUD), null).isValid());
  }

  @Test
  public void testActSubMatchIsCaseInsensitiveOnNamespaceAndServiceAccountName() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final Object actClaim = actorChain("system:serviceaccount:NS:Caller-SA");
    final Map<String, String> headers = baseHeaders();
    headers.put(SOURCE_SPIFFE_HEADER, SOURCE_SPIFFE_VALUE);
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, delegationTokenWithActClaim(actClaim, AUD), null).isValid());
  }

  // ---- is.service.account, consulted only under matches=true ----

  @Test
  public void testMatchesTrueIsServiceAccountFalseSkipsNonServiceAccountActorSub() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final Object actClaim = actorChain("plain-user");
    final Map<String, String> headers = baseHeaders();
    headers.put(SOURCE_SPIFFE_HEADER, SOURCE_SPIFFE_VALUE);
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, delegationTokenWithActClaim(actClaim, AUD), null).isValid());
  }

  @Test
  public void testMatchesTrueIsServiceAccountTrueFailsNonServiceAccountActorSub() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM, "true");
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_IS_SERVICE_ACCOUNT_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final Object actClaim = actorChain("plain-user");
    final Map<String, String> headers = baseHeaders();
    headers.put(SOURCE_SPIFFE_HEADER, SOURCE_SPIFFE_VALUE);
    final HttpServletRequest request = requestWithHeaders(headers);
    final AudienceValidationResult result = validator.validate(request, delegationTokenWithActClaim(actClaim, AUD), null);
    assertFalse(result.isValid());
    assertTrue(result.message().contains("not a k8s service account subject"));
    assertTrue(result.message().contains("plain-user"));
  }

  @Test
  public void testIsServiceAccountTrueAloneWithoutMatchesHasNoEffect() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_IS_SERVICE_ACCOUNT_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final Object actClaim = actorChain("plain-user");
    final HttpServletRequest request = requestWithHeaders(baseHeaders());
    assertTrue(validator.validate(request, delegationTokenWithActClaim(actClaim, AUD), null).isValid());
  }

  // ---- a configured source header must be present and parseable, unconditionally ----

  @Test
  public void testSourceHeaderMissingFailsEvenWhenActSubCheckDisabled() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    final HttpServletRequest request = requestWithHeaders(baseHeaders());
    final AudienceValidationResult result =
        validator.validate(request, delegationTokenWithActClaim(actorChain(MATCHING_ACTOR_SUB), AUD), null);
    assertFalse(result.isValid());
    assertTrue(result.message().contains("source SPIFFE id"));
    assertTrue(result.message().contains(SOURCE_SPIFFE_HEADER));
  }

  @Test
  public void testSourceHeaderUnparseableFailsEvenWhenActSubCheckDisabled() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    final Map<String, String> headers = baseHeaders();
    headers.put(SOURCE_SPIFFE_HEADER, "not-a-spiffe-id");
    final HttpServletRequest request = requestWithHeaders(headers);
    final AudienceValidationResult result =
        validator.validate(request, delegationTokenWithActClaim(actorChain(MATCHING_ACTOR_SUB), AUD), null);
    assertFalse(result.isValid());
    assertTrue(result.message().contains("source SPIFFE id"));
  }

  @Test
  public void testSourceHeaderPresentAndValidButActSubCheckDisabledPasses() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    final Object actClaim = actorChain("system:serviceaccount:other-ns:other-sa");
    final Map<String, String> headers = baseHeaders();
    headers.put(SOURCE_SPIFFE_HEADER, SOURCE_SPIFFE_VALUE);
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, delegationTokenWithActClaim(actClaim, AUD), null).isValid());
  }

  // ---- act chain ordering: only the most recent (index 0) actor is ever compared ----

  @Test
  public void testOnlyMostRecentActorIsCheckedWhenItMatches() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM, "true");
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_IS_SERVICE_ACCOUNT_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final Object actClaim = actorChain(MATCHING_ACTOR_SUB, "not-a-service-account", "also-not-one");
    final Map<String, String> headers = baseHeaders();
    headers.put(SOURCE_SPIFFE_HEADER, SOURCE_SPIFFE_VALUE);
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, delegationTokenWithActClaim(actClaim, AUD), null).isValid());
  }

  @Test
  public void testOnlyMostRecentActorIsCheckedWhenItMismatchesEvenIfOlderActorWouldMatch() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final Object actClaim = actorChain("system:serviceaccount:other-ns:other-sa", MATCHING_ACTOR_SUB);
    final Map<String, String> headers = baseHeaders();
    headers.put(SOURCE_SPIFFE_HEADER, SOURCE_SPIFFE_VALUE);
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, delegationTokenWithActClaim(actClaim, AUD), null).isValid());
  }

  // ---- malformed chain / missing-sub cases ----

  @Test
  public void testActClaimNotAMapSkipsCheckEntirely() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM, "true");
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_IS_SERVICE_ACCOUNT_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final Map<String, String> headers = baseHeaders();
    headers.put(SOURCE_SPIFFE_HEADER, SOURCE_SPIFFE_VALUE);
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, delegationTokenWithActClaim("opaque-string", AUD), null).isValid());
  }

  @Test
  public void testNonDelegationTokenNeverRunsActSubCheckRegardlessOfEnforceFlags() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM, "true");
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_IS_SERVICE_ACCOUNT_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final Map<String, String> headers = baseHeaders();
    headers.put(SOURCE_SPIFFE_HEADER, SOURCE_SPIFFE_VALUE);
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, nonDelegationToken(AUD), null).isValid());
  }

  @Test
  public void testMostRecentActorMapMissingSubSkipsWhenIsServiceAccountFalse() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final Map<String, Object> mostRecentActor = new LinkedHashMap<>();
    final Map<String, String> headers = baseHeaders();
    headers.put(SOURCE_SPIFFE_HEADER, SOURCE_SPIFFE_VALUE);
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, delegationTokenWithActClaim(mostRecentActor, AUD), null).isValid());
  }

  @Test
  public void testMostRecentActorMapMissingSubFailsWhenIsServiceAccountTrue() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM, "true");
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_IS_SERVICE_ACCOUNT_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final Map<String, Object> mostRecentActor = new LinkedHashMap<>();
    final Map<String, String> headers = baseHeaders();
    headers.put(SOURCE_SPIFFE_HEADER, SOURCE_SPIFFE_VALUE);
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, delegationTokenWithActClaim(mostRecentActor, AUD), null).isValid());
  }

  @Test
  public void testMostRecentActorSubNotAStringSkipsWhenIsServiceAccountFalse() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final Map<String, Object> mostRecentActor = new LinkedHashMap<>();
    mostRecentActor.put(JWT.SUBJECT, Integer.valueOf(42));
    final Map<String, String> headers = baseHeaders();
    headers.put(SOURCE_SPIFFE_HEADER, SOURCE_SPIFFE_VALUE);
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, delegationTokenWithActClaim(mostRecentActor, AUD), null).isValid());
  }

  @Test
  public void testMalformedServiceAccountSubMissingColonSeparatorFailsWhenIsServiceAccountTrue() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM, "true");
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_IS_SERVICE_ACCOUNT_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final Object actClaim = actorChain("system:serviceaccount:onlynamespace");
    final Map<String, String> headers = baseHeaders();
    headers.put(SOURCE_SPIFFE_HEADER, SOURCE_SPIFFE_VALUE);
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, delegationTokenWithActClaim(actClaim, AUD), null).isValid());
  }

  @Test
  public void testMalformedServiceAccountSubWrongPrefixSkipsWhenIsServiceAccountFalse() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final Object actClaim = actorChain("system:user:ns:name");
    final Map<String, String> headers = baseHeaders();
    headers.put(SOURCE_SPIFFE_HEADER, SOURCE_SPIFFE_VALUE);
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, delegationTokenWithActClaim(actClaim, AUD), null).isValid());
  }
}
