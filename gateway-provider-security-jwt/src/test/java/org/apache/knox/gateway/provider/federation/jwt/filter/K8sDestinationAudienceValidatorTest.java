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
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.easymock.EasyMock;
import org.junit.Test;

public class K8sDestinationAudienceValidatorTest {

  private static final String SPIFFE_HEADER = "x-dest-spiffe-id";
  private static final String SERVER_NAME_HEADER = "x-dest-server-name";
  private static final String PATH_HEADER = "x-dest-path";
  private static final String CLUSTER_SUFFIX = ".svc.cluster.local";
  private static final String CLUSTER_DOMAIN = "cluster.local";

  private static K8sDestinationAudienceValidator init(final Map<String, String> params) throws Exception {
    final K8sDestinationAudienceValidator validator = new K8sDestinationAudienceValidator();
    validator.init(filterConfig(params));
    return validator;
  }

  private static FilterConfig filterConfig(final Map<String, String> params) {
    final FilterConfig filterConfig = EasyMock.createNiceMock(FilterConfig.class);
    for (final Map.Entry<String, String> entry : params.entrySet()) {
      EasyMock.expect(filterConfig.getInitParameter(entry.getKey())).andReturn(entry.getValue()).anyTimes();
    }
    EasyMock.replay(filterConfig);
    return filterConfig;
  }

  private static HttpServletRequest requestWithHeaders(final Map<String, String> headers) {
    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    for (final Map.Entry<String, String> entry : headers.entrySet()) {
      EasyMock.expect(request.getHeader(entry.getKey())).andReturn(entry.getValue()).anyTimes();
    }
    EasyMock.replay(request);
    return request;
  }

  private static JWT delegationToken(final String... audiences) {
    final JWT token = EasyMock.createNiceMock(JWT.class);
    EasyMock.expect(token.getClaimAsObject(JWTToken.ACT_CLAIM)).andReturn("delegate").anyTimes();
    EasyMock.expect(token.getAudienceClaims()).andReturn(audiences).anyTimes();
    EasyMock.replay(token);
    return token;
  }

  private static JWT nonDelegationToken(final String... audiences) {
    final JWT token = EasyMock.createNiceMock(JWT.class);
    EasyMock.expect(token.getClaimAsObject(JWTToken.ACT_CLAIM)).andReturn(null).anyTimes();
    EasyMock.expect(token.getAudienceClaims()).andReturn(audiences).anyTimes();
    EasyMock.replay(token);
    return token;
  }

  private static Map<String, String> baseParamsWithAllThreeHeaders() {
    final Map<String, String> params = new LinkedHashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.SERVER_NAME_HEADER_PARAM, SERVER_NAME_HEADER);
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    return params;
  }

  // ---- init() fail-fast ----

  @Test
  public void testInitThrowsWhenNoHeaderParamConfigured() {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    try {
      init(params);
      fail("Expected ServletException");
    } catch (final ServletException e) {
      assertTrue(e.getMessage().contains(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM));
      assertTrue(e.getMessage().contains(K8sDestinationAudienceValidator.SERVER_NAME_HEADER_PARAM));
      assertTrue(e.getMessage().contains(K8sDestinationAudienceValidator.PATH_HEADER_PARAM));
    } catch (final Exception e) {
      fail("Expected ServletException, got " + e);
    }
  }

  @Test
  public void testInitSucceedsWithOnlySpiffeHeaderConfigured() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    init(params);
  }

  @Test
  public void testInitSucceedsWithOnlyServerNameHeaderConfigured() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.SERVER_NAME_HEADER_PARAM, SERVER_NAME_HEADER);
    init(params);
  }

  @Test
  public void testInitSucceedsWithOnlyPathHeaderConfigured() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    init(params);
  }

  // ---- cluster-domains allow-list validation at init() ----

  @Test
  public void testInitThrowsOnClusterDomainEntryWithPath() {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, "cluster.local/extra");
    assertInitThrows(params);
  }

  @Test
  public void testInitThrowsOnClusterDomainEntryWithUserinfo() {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, "user@cluster.local");
    assertInitThrows(params);
  }

  @Test
  public void testInitThrowsOnClusterDomainEntryWithScheme() {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, "https://cluster.local");
    assertInitThrows(params);
  }

  @Test
  public void testInitThrowsOnClusterDomainEntryWithUnparseablePort() {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, "cluster.local:abc");
    assertInitThrows(params);
  }

  private static void assertInitThrows(final Map<String, String> params) {
    try {
      init(params);
      fail("Expected ServletException");
    } catch (final ServletException e) {
      // expected
    } catch (final Exception e) {
      fail("Expected ServletException, got " + e);
    }
  }

  // ---- no-act-claim fallthrough ----

  @Test
  public void testNoActClaimFallsThroughToMatchesConfiguredAudiences() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParamsWithAllThreeHeaders());
    final JWT token = nonDelegationToken("configured-audience");
    final HttpServletRequest request = requestWithHeaders(new HashMap<>());
    final AudienceValidationResult result =
        validator.validate(request, token, java.util.Collections.singletonList("configured-audience"));
    assertTrue(result.isValid());
  }

  @Test
  public void testNoActClaimFallsThroughAndFailsWhenNotInConfiguredList() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParamsWithAllThreeHeaders());
    final JWT token = nonDelegationToken("some-other-audience");
    final HttpServletRequest request = requestWithHeaders(new HashMap<>());
    final AudienceValidationResult result =
        validator.validate(request, token, java.util.Collections.singletonList("configured-audience"));
    assertFalse(result.isValid());
  }

  // ---- absent/empty aud claim on an act-bearing token (rejected) ----

  @Test
  public void testActClaimWithNullAudienceClaimsRejected() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParamsWithAllThreeHeaders());
    final JWT token = delegationToken((String[]) null);
    final HttpServletRequest request = requestWithHeaders(new HashMap<>());
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testActClaimWithEmptyAudienceClaimsRejected() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParamsWithAllThreeHeaders());
    final JWT token = delegationToken();
    final HttpServletRequest request = requestWithHeaders(new HashMap<>());
    assertFalse(validator.validate(request, token, null).isValid());
  }

  // ---- missing configured header is a rejection, one test per header ----

  @Test
  public void testMissingSpiffeHeaderRejectsRequest() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/svc");
    final HttpServletRequest request = requestWithHeaders(new HashMap<>());
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testMissingServerNameHeaderRejectsRequest() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.SERVER_NAME_HEADER_PARAM, SERVER_NAME_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/svc");
    final HttpServletRequest request = requestWithHeaders(new HashMap<>());
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testMissingPathHeaderRejectsRequest() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/svc/a");
    final HttpServletRequest request = requestWithHeaders(new HashMap<>());
    assertFalse(validator.validate(request, token, null).isValid());
  }

  // ---- each segment check independently, others unconfigured ----

  @Test
  public void testNamespaceOnlyCheckPassesOnMatch() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/anything-goes-here");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/ns/sa/sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testNamespaceOnlyCheckFailsOnMismatch() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/other-ns/svc");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/ns/sa/sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testServiceNameAndNamespaceCheckPassesOnMatch() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.SERVER_NAME_HEADER_PARAM, SERVER_NAME_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/svc");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SERVER_NAME_HEADER, "svc.ns" + CLUSTER_SUFFIX);
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testServiceNameCheckFailsOnMismatch() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.SERVER_NAME_HEADER_PARAM, SERVER_NAME_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/other-svc");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SERVER_NAME_HEADER, "svc.ns" + CLUSTER_SUFFIX);
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testPathOnlyCheckPassesOnMatch() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/anything/goes/a/b");
    final Map<String, String> headers = new HashMap<>();
    headers.put(PATH_HEADER, "/a/b");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testPathOnlyCheckFailsOnMismatch() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/svc/a/b");
    final Map<String, String> headers = new HashMap<>();
    headers.put(PATH_HEADER, "/a/c");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  // ---- aud matching every enabled segment but differing in an unconfigured segment: ACCEPTED ----

  @Test
  public void testUnconfiguredServiceNameSegmentGapIsAccepted() throws Exception {
    // Only namespace (via SPIFFE header) and path are configured; service-name is not -- an aud
    // entry for a different service in the same namespace and path must still be accepted.
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/some-other-service/a");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/ns/sa/sa");
    headers.put(PATH_HEADER, "/a");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  // ---- namespace from two sources: agree / disagree ----

  @Test
  public void testBothNamespaceSourcesAgreeingIsAccepted() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParamsWithAllThreeHeaders());
    final JWT token = delegationToken("https://cluster.local/ns/svc/a");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/ns/sa/sa");
    headers.put(SERVER_NAME_HEADER, "svc.ns" + CLUSTER_SUFFIX);
    headers.put(PATH_HEADER, "/a");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testDisagreeingNamespaceSourcesRejectsFailClosed() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParamsWithAllThreeHeaders());
    final JWT token = delegationToken("https://cluster.local/ns/svc/a");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/ns/sa/sa");
    headers.put(SERVER_NAME_HEADER, "svc.other-ns" + CLUSTER_SUFFIX);
    headers.put(PATH_HEADER, "/a");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  // ---- strict aud entry shape rejections ----

  @Test
  public void testWrongSchemeAudEntryNeverMatches() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParamsWithAllThreeHeaders());
    final JWT token = delegationToken("http://cluster.local/ns/svc/a");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/ns/sa/sa");
    headers.put(SERVER_NAME_HEADER, "svc.ns" + CLUSTER_SUFFIX);
    headers.put(PATH_HEADER, "/a");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testNoAuthorityAudEntryNeverMatches() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParamsWithAllThreeHeaders());
    final JWT token = delegationToken("https:///ns/svc/a");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/ns/sa/sa");
    headers.put(SERVER_NAME_HEADER, "svc.ns" + CLUSTER_SUFFIX);
    headers.put(PATH_HEADER, "/a");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testUserinfoPresentAudEntryNeverMatches() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParamsWithAllThreeHeaders());
    final JWT token = delegationToken("https://user@cluster.local/ns/svc/a");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/ns/sa/sa");
    headers.put(SERVER_NAME_HEADER, "svc.ns" + CLUSTER_SUFFIX);
    headers.put(PATH_HEADER, "/a");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testTooFewPathSegmentsAudEntryNeverMatches() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParamsWithAllThreeHeaders());
    final JWT token = delegationToken("https://cluster.local/ns");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/ns/sa/sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testNonUrlLogicalNameAudEntryNeverMatches() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParamsWithAllThreeHeaders());
    final JWT token = delegationToken("service-a");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/ns/sa/sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testBarePathAudEntryNeverMatches() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParamsWithAllThreeHeaders());
    final JWT token = delegationToken("/api/v1");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/ns/sa/sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  // ---- strict server-name header FQDN rejections ----

  @Test
  public void testServerNameHeaderMissingSuffixRejects() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.SERVER_NAME_HEADER_PARAM, SERVER_NAME_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/svc");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SERVER_NAME_HEADER, "svc.ns.other.suffix");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testServerNameHeaderSuffixNotAtEndRejects() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.SERVER_NAME_HEADER_PARAM, SERVER_NAME_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/svc");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SERVER_NAME_HEADER, "svc.ns" + CLUSTER_SUFFIX + ".extra");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testServerNameHeaderWrongLabelCountRejects() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.SERVER_NAME_HEADER_PARAM, SERVER_NAME_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/svc");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SERVER_NAME_HEADER, "a.b.svc.ns" + CLUSTER_SUFFIX);
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testServerNameHeaderTrailingPortAccepted() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.SERVER_NAME_HEADER_PARAM, SERVER_NAME_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/svc");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SERVER_NAME_HEADER, "svc.ns" + CLUSTER_SUFFIX + ":8443");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testCustomClusterSuffixIsHonored() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.SERVER_NAME_HEADER_PARAM, SERVER_NAME_HEADER);
    params.put(K8sDestinationAudienceValidator.SERVER_NAME_CLUSTER_SUFFIX_PARAM, ".internal");
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/svc");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SERVER_NAME_HEADER, "svc.ns.internal");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  // ---- cluster-domain port equivalences ----

  @Test
  public void testNoPortAudMatchesDefaultPortAllowListEntry() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, "cluster.local:443");
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/svc/a");
    final Map<String, String> headers = new HashMap<>();
    headers.put(PATH_HEADER, "/a");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testExplicitDefaultPortAudMatchesNoPortAllowListEntry() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local:443/ns/svc/a");
    final Map<String, String> headers = new HashMap<>();
    headers.put(PATH_HEADER, "/a");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testNonDefaultPortMatchesOnlyIdenticalPort() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, "cluster.local:8443");
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT matching = delegationToken("https://cluster.local:8443/ns/svc/a");
    final JWT mismatching = delegationToken("https://cluster.local:9443/ns/svc/a");
    final Map<String, String> headers = new HashMap<>();
    headers.put(PATH_HEADER, "/a");
    assertTrue(validator.validate(requestWithHeaders(headers), matching, null).isValid());
    assertFalse(validator.validate(requestWithHeaders(headers), mismatching, null).isValid());
  }

  @Test
  public void testNoPortAudNeverMatchesNonDefaultPortAllowListEntry() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, "cluster.local:8443");
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/svc/a");
    final Map<String, String> headers = new HashMap<>();
    headers.put(PATH_HEADER, "/a");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  // ---- dot-segment / empty-segment rejections and trailing-slash tidying ----

  @Test
  public void testRequestPathWithDotDotSegmentRejected() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/svc/a");
    final Map<String, String> headers = new HashMap<>();
    headers.put(PATH_HEADER, "/a/../a");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testRequestPathWithDotSegmentRejected() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/svc/a/b");
    final Map<String, String> headers = new HashMap<>();
    headers.put(PATH_HEADER, "/a/./b");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testRequestPathWithEmptySegmentRejected() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/svc/a/b");
    final Map<String, String> headers = new HashMap<>();
    headers.put(PATH_HEADER, "/a//b");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testTrailingSlashDifferenceIsNotAMismatch() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/svc/a/b/");
    final Map<String, String> headers = new HashMap<>();
    headers.put(PATH_HEADER, "/a/b");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  // ---- two-candidate percent-encoding comparison ----

  @Test
  public void testUnencodedAudPathMatchesEncodedRequestPath() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/svc/a b");
    final Map<String, String> headers = new HashMap<>();
    headers.put(PATH_HEADER, "/a%20b");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testAudPathWithExistingEscapeMatchesRawRequestCandidate() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/svc/a%20b");
    final Map<String, String> headers = new HashMap<>();
    headers.put(PATH_HEADER, "/a%20b");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testEscapedSlashInRequestPathNeverMatchesLiteralSlashInAud() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/svc/a/b");
    final Map<String, String> headers = new HashMap<>();
    headers.put(PATH_HEADER, "/a%2Fb");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  // ---- path.header.from.url ----

  @Test
  public void testPathHeaderFromUrlExtractsPathComponent() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_FROM_URL_PARAM, "true");
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/svc/a/b");
    final Map<String, String> headers = new HashMap<>();
    headers.put(PATH_HEADER, "https://internal-router.example/a/b?query=1");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  // ---- require-all-audiences-match ----

  @Test
  public void testRequireAllFalseAcceptsWhenOnlyOneOfManyMatches() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    params.put(K8sDestinationAudienceValidator.REQUIRE_ALL_AUDIENCES_MATCH_PARAM, "false");
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://other.local/ns/svc/a", "https://cluster.local/ns/svc/a");
    final Map<String, String> headers = new HashMap<>();
    headers.put(PATH_HEADER, "/a");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testRequireAllTrueRejectsWhenOnlyOneOfManyMatches() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAIN);
    params.put(K8sDestinationAudienceValidator.REQUIRE_ALL_AUDIENCES_MATCH_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://other.local/ns/svc/a", "https://cluster.local/ns/svc/a");
    final Map<String, String> headers = new HashMap<>();
    headers.put(PATH_HEADER, "/a");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testRequireAllTrueAcceptsWhenEveryEntryMatches() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, "cluster.local,cluster2.local");
    params.put(K8sDestinationAudienceValidator.REQUIRE_ALL_AUDIENCES_MATCH_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://cluster.local/ns/svc/a", "https://cluster2.local/ns/svc/a");
    final Map<String, String> headers = new HashMap<>();
    headers.put(PATH_HEADER, "/a");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  // ---- getName() ----

  @Test
  public void testGetNameReturnsFixedValidationMethodValue() {
    final K8sDestinationAudienceValidator validator = new K8sDestinationAudienceValidator();
    assertTrue(K8sDestinationAudienceValidator.VALIDATION_METHOD_VALUE.equals(validator.getName()));
  }
}
