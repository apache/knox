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
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.junit.Test;

import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.delegationToken;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.filterConfig;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.requestWithHeaders;

/**
 * Covers the k8s service DNS name aud format at the validator level: every host shape allowed by
 * {@link K8sDestinationAudienceValidator#CLUSTER_DOMAIN_PARAM}, its independence from {@link
 * K8sDestinationAudienceValidator#CLUSTER_DOMAINS_PARAM} and from {@link
 * K8sDestinationAudienceValidator#SERVER_NAME_CLUSTER_SUFFIX_PARAM}, and the dual-format fallback
 * between it and the custom destination-URL format.
 */
public class K8sDnsAudienceFormatTest {

  private static final String SPIFFE_HEADER = "x-dest-spiffe-id";
  private static final String SERVER_NAME_HEADER = "x-dest-server-name";
  private static final String PATH_HEADER = "x-dest-path";
  private static final String SOURCE_SPIFFE_HEADER = "x-source-spiffe-id";
  private static final String CLUSTER_SUFFIX = ".svc.cluster.local";

  private static K8sDestinationAudienceValidator init(final Map<String, String> params) throws Exception {
    final K8sDestinationAudienceValidator validator = new K8sDestinationAudienceValidator();
    validator.init(filterConfig(params));
    return validator;
  }

  // ---- host shapes, default cluster.domain ----

  @Test
  public void testSingleLabelHostMatchesWithSourceSpiffeIdSupplyingNamespace() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://svc");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/myns/sa/sa");
    headers.put(SOURCE_SPIFFE_HEADER, "spiffe://trust-domain/ns/myns/sa/caller-sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testSingleLabelHostRejectedWhenNoDestinationNamespaceConfigured() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://svc");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SOURCE_SPIFFE_HEADER, "spiffe://trust-domain/ns/myns/sa/caller-sa");
    headers.put(PATH_HEADER, "/x");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testSingleLabelHostRejectedWhenSourceSpiffeIdHeaderNotConfigured() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://svc");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/myns/sa/sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testSingleLabelHostRejectedWhenSourceNamespaceDisagrees() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.SOURCE_SPIFFE_ID_HEADER_PARAM, SOURCE_SPIFFE_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://svc");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/myns/sa/sa");
    headers.put(SOURCE_SPIFFE_HEADER, "spiffe://trust-domain/ns/otherns/sa/caller-sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testTwoLabelHostTakesNamespaceFromHostItself() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://svc.myns");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/myns/sa/sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testTwoLabelHostRejectedWhenHostNamespaceDisagreesWithDestination() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://svc.otherns");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/myns/sa/sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testThreeLabelHostWithLiteralSvcAndNoClusterDomainRemainderMatches() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://svc.myns.svc");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/myns/sa/sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testThreeLabelHostWhoseThirdLabelIsNotSvcFallsThroughAndFails() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://svc.myns.notsvc");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/myns/sa/sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testFourLabelHostAcceptsPartialClusterDomainPrefix() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://svc.myns.svc.cluster");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/myns/sa/sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testFourLabelHostRejectsLabelNotMatchingClusterDomainPrefix() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://svc.myns.svc.locale");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/myns/sa/sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testFourLabelHostCannotSkipClusterDomainsFirstLabel() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    // cluster.domain defaults to "cluster.local"; "local" alone is not a label-boundary prefix of it.
    final JWT token = delegationToken("https://svc.myns.svc.local");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/myns/sa/sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testFiveLabelHostWithFullClusterDomainMatches() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://svc.myns.svc.cluster.local");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/myns/sa/sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testTrailingDotOnHostIsRejectedAndCustomFormFallbackAlsoFails() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, "cluster.local");
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://svc.myns.svc.cluster.local.");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/myns/sa/sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  // ---- cluster-domains allow-list and port are irrelevant to the DNS form ----

  @Test
  public void testDnsFormIgnoresCustomFormClusterDomainsAllowList() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, "totally-different.example");
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://svc.myns.svc.cluster.local");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/myns/sa/sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testDnsFormPortIsParsedThenIgnored() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://svc.myns.svc.cluster.local:9999");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/myns/sa/sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  // ---- cluster.domain is independent of server.name.cluster-suffix ----

  @Test
  public void testClusterDomainParamIsIndependentOfServerNameClusterSuffix() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.SERVER_NAME_HEADER_PARAM, SERVER_NAME_HEADER);
    params.put(K8sDestinationAudienceValidator.SERVER_NAME_CLUSTER_SUFFIX_PARAM, ".internal");
    final K8sDestinationAudienceValidator validator = init(params);
    // Destination header uses the customized suffix; the aud entry still uses the default
    // cluster.domain ("cluster.local"), and the two configs do not interact.
    final JWT token = delegationToken("https://svc.myns.svc.cluster.local");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SERVER_NAME_HEADER, "svc.myns.internal");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  // ---- cluster.domain override ----

  @Test
  public void testClusterDomainParamOverrideHonoredForFullMatch() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAIN_PARAM, "mycompany.io");
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://svc.myns.svc.mycompany.io");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/myns/sa/sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testClusterDomainParamOverridePartialPrefixAccepted() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAIN_PARAM, "mycompany.io");
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://svc.myns.svc.mycompany");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/myns/sa/sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testHostValidAgainstOverriddenClusterDomainDoesNotMatchDefaultClusterDomain() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    // cluster.domain left at its default of "cluster.local".
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://svc.myns.svc.mycompany.io");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/myns/sa/sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }

  // ---- dual-format resolution ----

  @Test
  public void testTwoLabelBaseDomainFallsBackToCustomFormAndMatches() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.SERVER_NAME_HEADER_PARAM, SERVER_NAME_HEADER);
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, "knox.local");
    final K8sDestinationAudienceValidator validator = init(params);
    // As a DNS-form host, "knox.local" is service="knox", namespace="local", which does not
    // match the destination namespace "ns" below -- so this falls through and is evaluated as
    // the custom form instead, where "knox.local" is the cluster domain and "/ns/svc/path" is
    // the namespace, service name and resource path.
    final JWT token = delegationToken("https://knox.local/ns/svc/path");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/ns/sa/sa");
    headers.put(SERVER_NAME_HEADER, "svc.ns" + CLUSTER_SUFFIX);
    headers.put(PATH_HEADER, "/path");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testDnsShapedHostMatchesViaDnsFormEvenWhenCustomFormClusterDomainsWouldNeverMatch() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    // Default cluster-domains allow-list ("service.local") would never match this host as a
    // custom-form entry; the DNS form is tried first and succeeds on its own.
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://svc.ns.svc.cluster.local/a/b");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/ns/sa/sa");
    headers.put(PATH_HEADER, "/a/b");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testFailureMessageMentionsBothFormatsWhenNeitherParses() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("not-a-url-at-all");
    final Map<String, String> headers = new HashMap<>();
    headers.put(PATH_HEADER, "/a");
    final HttpServletRequest request = requestWithHeaders(headers);
    final AudienceValidationResult result = validator.validate(request, token, null);
    assertFalse(result.isValid());
    assertTrue(result.message().contains("k8s service DNS name"));
    assertTrue(result.message().contains("custom form destination URL"));
  }

  @Test
  public void testFailureMessageMentionsBothFormatsWhenBothParseButNeitherMatches() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, "service.local");
    final K8sDestinationAudienceValidator validator = init(params);
    // Parses as DNS form (namespace "local" from the two-label host) and also as custom form
    // (authority "service.local" is in the allow-list, namespace "otherns" from the path), but
    // neither one's namespace matches the destination namespace "ns".
    final JWT token = delegationToken("https://service.local/otherns/svc");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/ns/sa/sa");
    final HttpServletRequest request = requestWithHeaders(headers);
    final AudienceValidationResult result = validator.validate(request, token, null);
    assertFalse(result.isValid());
    assertTrue(result.message().contains("namespace local does not match destination namespace ns"));
    assertTrue(result.message().contains("namespace otherns does not match destination namespace ns"));
  }

  // ---- require-all crossed with mixed DNS-form and custom-form interpretations ----

  @Test
  public void testRequireAllAudiencesMatchAcceptsMixOfDnsAndCustomFormEntries() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.SERVER_NAME_HEADER_PARAM, SERVER_NAME_HEADER);
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, "custom.local");
    params.put(K8sDestinationAudienceValidator.REQUIRE_ALL_AUDIENCES_MATCH_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://svc.ns.svc.cluster.local/a", "https://custom.local/ns/svc/a");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/ns/sa/sa");
    headers.put(SERVER_NAME_HEADER, "svc.ns" + CLUSTER_SUFFIX);
    headers.put(PATH_HEADER, "/a");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testRequireAllAudiencesMatchRejectsWhenOneEntryFailsBothInterpretations() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.SERVER_NAME_HEADER_PARAM, SERVER_NAME_HEADER);
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    params.put(K8sDestinationAudienceValidator.CLUSTER_DOMAINS_PARAM, "custom.local");
    params.put(K8sDestinationAudienceValidator.REQUIRE_ALL_AUDIENCES_MATCH_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://svc.ns.svc.cluster.local/a", "https://custom.local/ns/svc/a",
        "not-a-url-at-all");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, "spiffe://trust-domain/ns/ns/sa/sa");
    headers.put(SERVER_NAME_HEADER, "svc.ns" + CLUSTER_SUFFIX);
    headers.put(PATH_HEADER, "/a");
    final HttpServletRequest request = requestWithHeaders(headers);
    assertFalse(validator.validate(request, token, null).isValid());
  }
}
