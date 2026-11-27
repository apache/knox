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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.Test;

import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.delegationToken;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.filterConfig;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.nonDelegationToken;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.requestWithHeaders;

/**
 * Pins how a token is routed by the presence or absence of its {@code act} claim, and pins the
 * order in which {@code validate()}'s early rejections fire relative to one another, since several
 * of those rejections are distinguishable only by their message.
 *
 * <p>Every test configures the source SPIFFE id header, because the order in which that header is
 * resolved and rejected relative to the other checks is part of what is pinned here.
 */
public class K8sNonDelegationTokenRoutingTest {

  private static final String SPIFFE_HEADER = "x-dest-spiffe-id";
  private static final String SOURCE_SPIFFE_HEADER = "x-source-spiffe-id";
  private static final String PATH_HEADER = "x-dest-path";
  private static final String CLUSTER_DOMAIN = "cluster.local";
  private static final String DEST_SPIFFE_VALUE = "spiffe://trust-domain/ns/ns/sa/dest-sa";
  private static final String SOURCE_SPIFFE_VALUE = "spiffe://trust-domain/ns/ns/sa/caller-sa";
  private static final String DEST_PATH_VALUE = "/some/path";
  private static final String MATCHING_AUD = "https://svc.ns";
  private static final String OTHER_NAMESPACE_AUD = "https://svc.other-ns";
  private static final String CONFIGURED_AUD = "configured-audience";
  private static final String MISSING_SOURCE_MESSAGE = "Missing or unparseable source SPIFFE id in header";
  private static final String MISSING_DESTINATION_MESSAGE = "Missing or unparseable destination SPIFFE id in header";

  private static K8sDestinationAudienceValidator init(final Map<String, String> params) throws Exception {
    final K8sDestinationAudienceValidator validator = new K8sDestinationAudienceValidator();
    validator.init(filterConfig(params));
    return validator;
  }

  private static Map<String, String> sourceHeaderParams() {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    return params;
  }

  private static Map<String, String> sourceHeaderAndPathParams() {
    final Map<String, String> params = sourceHeaderParams();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    return params;
  }

  private static Map<String, String> fixedListFallbackParams() {
    final Map<String, String> params = sourceHeaderParams();
    params.put(K8sDestinationAudienceValidator.VALIDATE_AUDIENCES_WITHOUT_ACT_CLAIM_PARAM, "false");
    return params;
  }

  private static Map<String, String> allHeaders() {
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, DEST_SPIFFE_VALUE);
    headers.put(SOURCE_SPIFFE_HEADER, SOURCE_SPIFFE_VALUE);
    headers.put(PATH_HEADER, DEST_PATH_VALUE);
    return headers;
  }

  private static Map<String, String> destinationHeaderOnly() {
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, DEST_SPIFFE_VALUE);
    return headers;
  }

  // ---- routing by act claim, source header configured ----

  @Test
  public void testNonDelegationTokenWithSourceHeaderConfiguredIsDestinationValidated() throws Exception {
    final K8sDestinationAudienceValidator validator = init(sourceHeaderAndPathParams());
    final HttpServletRequest request = requestWithHeaders(allHeaders());
    assertTrue(validator.validate(request, nonDelegationToken(MATCHING_AUD), null).isValid());
  }

  @Test
  public void testNonDelegationTokenWithSourceHeaderConfiguredRejectsNonMatchingAud() throws Exception {
    final K8sDestinationAudienceValidator validator = init(sourceHeaderAndPathParams());
    final HttpServletRequest request = requestWithHeaders(allHeaders());
    final AudienceValidationResult result =
        validator.validate(request, nonDelegationToken(OTHER_NAMESPACE_AUD), null);
    assertFalse(result.isValid());
    assertTrue(result.message().contains("does not match the destination"));
    assertTrue(result.message().contains("namespace other-ns does not match destination namespace ns"));
  }

  @Test
  public void testDelegationTokenWithSourceHeaderConfiguredIsDestinationValidated() throws Exception {
    final K8sDestinationAudienceValidator validator = init(sourceHeaderAndPathParams());
    final HttpServletRequest request = requestWithHeaders(allHeaders());
    assertTrue(validator.validate(request, delegationToken(MATCHING_AUD), null).isValid());
    assertFalse(validator.validate(request, delegationToken(OTHER_NAMESPACE_AUD), null).isValid());
  }

  // ---- fixed-list fallback, source header configured ----

  @Test
  public void testNonDelegationTokenFallsBackToWhitelistWhenValidateWithoutActClaimFalse() throws Exception {
    final K8sDestinationAudienceValidator validator = init(fixedListFallbackParams());
    final HttpServletRequest request = requestWithHeaders(allHeaders());
    assertTrue(validator.validate(request, nonDelegationToken(CONFIGURED_AUD),
        Collections.singletonList(CONFIGURED_AUD)).isValid());
  }

  @Test
  public void testNonDelegationTokenWhitelistFallbackRejectsAudienceNotInList() throws Exception {
    final K8sDestinationAudienceValidator validator = init(fixedListFallbackParams());
    final HttpServletRequest request = requestWithHeaders(allHeaders());
    final AudienceValidationResult result = validator.validate(request, nonDelegationToken("some-other-audience"),
        Collections.singletonList(CONFIGURED_AUD));
    assertFalse(result.isValid());
    // Destination validation always explains a rejection; the fixed-list check does not.
    assertNull(result.message());
  }

  @Test
  public void testNonDelegationTokenWhitelistFallbackAcceptsWhenConfiguredAudiencesNull() throws Exception {
    final K8sDestinationAudienceValidator validator = init(fixedListFallbackParams());
    final HttpServletRequest request = requestWithHeaders(allHeaders());
    assertTrue(validator.validate(request, nonDelegationToken("some-other-audience"), null).isValid());
  }

  @Test
  public void testNonDelegationTokenWhitelistFallbackRejectsNoneSentinelWithEmptyAudClaim() throws Exception {
    final K8sDestinationAudienceValidator validator = init(fixedListFallbackParams());
    final HttpServletRequest request = requestWithHeaders(allHeaders());
    // An empty aud array is not null, so the fixed-list check iterates zero entries and never
    // reaches its NONE branch.
    assertFalse(validator.validate(request, nonDelegationToken(), Collections.singletonList("NONE")).isValid());
  }

  @Test
  public void testNonDelegationTokenWhitelistFallbackAcceptsNoneSentinelWithNullAudClaim() throws Exception {
    final K8sDestinationAudienceValidator validator = init(fixedListFallbackParams());
    final HttpServletRequest request = requestWithHeaders(allHeaders());
    assertTrue(validator.validate(request, nonDelegationToken((String[]) null),
        Collections.singletonList("NONE")).isValid());
  }

  // ---- order of early rejections ----

  @Test
  public void testDelegationTokenWithNoAudClaimRejectedBeforeSourceHeaderIsChecked() throws Exception {
    final K8sDestinationAudienceValidator validator = init(sourceHeaderParams());
    final HttpServletRequest request = requestWithHeaders(destinationHeaderOnly());
    final AudienceValidationResult result = validator.validate(request, delegationToken((String[]) null), null);
    assertFalse(result.isValid());
    assertEquals("Token has an act claim but no aud claim", result.message());
  }

  @Test
  public void testNonDelegationTokenWithNoAudClaimRejectedBeforeSourceHeaderIsChecked() throws Exception {
    final K8sDestinationAudienceValidator validator = init(sourceHeaderParams());
    final HttpServletRequest request = requestWithHeaders(destinationHeaderOnly());
    final AudienceValidationResult result = validator.validate(request, nonDelegationToken((String[]) null), null);
    assertFalse(result.isValid());
    assertEquals("Token has no act claim and no aud claim, and "
        + K8sDestinationAudienceValidator.VALIDATE_AUDIENCES_WITHOUT_ACT_CLAIM_PARAM + " is true",
        result.message());
  }

  @Test
  public void testDestinationSpiffeHeaderRejectionPrecedesSourceSpiffeHeaderRejection() throws Exception {
    final K8sDestinationAudienceValidator validator = init(sourceHeaderParams());
    final HttpServletRequest request = requestWithHeaders(new HashMap<>());
    final AudienceValidationResult result = validator.validate(request, delegationToken(MATCHING_AUD), null);
    assertFalse(result.isValid());
    assertEquals(MISSING_DESTINATION_MESSAGE + " " + SPIFFE_HEADER + ": null", result.message());
  }

  @Test
  public void testNonDelegationTokenWithMissingSourceHeaderIsRejected() throws Exception {
    final K8sDestinationAudienceValidator validator = init(sourceHeaderParams());
    final HttpServletRequest request = requestWithHeaders(destinationHeaderOnly());
    final AudienceValidationResult result = validator.validate(request, nonDelegationToken(MATCHING_AUD), null);
    assertFalse(result.isValid());
    assertEquals(MISSING_SOURCE_MESSAGE + " " + SOURCE_SPIFFE_HEADER + ": null", result.message());
  }

  @Test
  public void testNonDelegationTokenWithUnparseableSourceHeaderIsRejected() throws Exception {
    final K8sDestinationAudienceValidator validator = init(sourceHeaderParams());
    final Map<String, String> headers = destinationHeaderOnly();
    headers.put(SOURCE_SPIFFE_HEADER, "not-a-spiffe-id");
    final HttpServletRequest request = requestWithHeaders(headers);
    final AudienceValidationResult result = validator.validate(request, nonDelegationToken(MATCHING_AUD), null);
    assertFalse(result.isValid());
    assertEquals(MISSING_SOURCE_MESSAGE + " " + SOURCE_SPIFFE_HEADER + ": not-a-spiffe-id", result.message());
  }
}
