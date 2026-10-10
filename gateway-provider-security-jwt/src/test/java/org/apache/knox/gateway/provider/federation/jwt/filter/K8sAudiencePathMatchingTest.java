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
 * Covers resource-path matching at the validator level: the no-path/root-path wildcard rule for
 * both {@code aud} formats, exact matching against the raw and percent-encoded candidate forms,
 * {@link K8sDestinationAudienceValidator#AUDIENCE_PATH_PREFIX_PARAM} applying only to the custom
 * form, the unconditional requirement that a configured {@link
 * K8sDestinationAudienceValidator#PATH_HEADER_PARAM} header be present, and the {@link
 * K8sDestinationAudienceValidator#PATH_HEADER_FROM_URL_PARAM} true/false branches.
 *
 * <p>Every test here uses the same destination namespace, {@code ns}, taken from {@link
 * #SPIFFE_HEADER}; namespace is therefore never the reason an entry matches or fails to, keeping
 * the resource path the only variable under test.
 */
public class K8sAudiencePathMatchingTest {

  private static final String SPIFFE_HEADER = "x-dest-spiffe-id";
  private static final String PATH_HEADER = "x-dest-path";
  private static final String DEST_SPIFFE_VALUE = "spiffe://trust-domain/ns/ns/sa/sa";

  private static K8sDestinationAudienceValidator init(final Map<String, String> params) throws Exception {
    final K8sDestinationAudienceValidator validator = new K8sDestinationAudienceValidator();
    validator.init(filterConfig(params));
    return validator;
  }

  private static Map<String, String> baseParams() {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    return params;
  }

  private static Map<String, String> headersWithPath(final String path) {
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, DEST_SPIFFE_VALUE);
    headers.put(PATH_HEADER, path);
    return headers;
  }

  // ---- no path, or exactly "/", matches any request path, for either aud format ----

  @Test
  public void testCustomFormWithNoPathSegmentMatchesAnyRequestPath() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParams());
    final JWT token = delegationToken("https://service.local/ns/svc");
    final HttpServletRequest request = requestWithHeaders(headersWithPath("/anything/goes"));
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testCustomFormWithExplicitRootPathMatchesAnyRequestPath() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParams());
    final JWT token = delegationToken("https://service.local/ns/svc/");
    final HttpServletRequest request = requestWithHeaders(headersWithPath("/anything/goes"));
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testDnsFormWithNoPathMatchesAnyRequestPath() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParams());
    final JWT token = delegationToken("https://svc.ns");
    final HttpServletRequest request = requestWithHeaders(headersWithPath("/anything/goes"));
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testDnsFormWithExplicitRootPathMatchesAnyRequestPath() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParams());
    final JWT token = delegationToken("https://svc.ns/");
    final HttpServletRequest request = requestWithHeaders(headersWithPath("/anything/goes"));
    assertTrue(validator.validate(request, token, null).isValid());
  }

  // ---- exact matching against the raw and percent-encoded candidate forms ----

  @Test
  public void testExactPathMatchPasses() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParams());
    final JWT token = delegationToken("https://service.local/ns/svc/orders/42");
    final HttpServletRequest request = requestWithHeaders(headersWithPath("/orders/42"));
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testPathMismatchFails() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParams());
    final JWT token = delegationToken("https://service.local/ns/svc/orders/42");
    final HttpServletRequest request = requestWithHeaders(headersWithPath("/orders/43"));
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testPathMatchesAgainstRawFormWhenRequestPathIsUnencoded() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParams());
    // "a b" is unencoded in the aud entry, so resourcePathRaw is "/orders/a b" verbatim.
    final JWT token = delegationToken("https://service.local/ns/svc/orders/a b");
    final HttpServletRequest request = requestWithHeaders(headersWithPath("/orders/a b"));
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testPathMatchesAgainstEncodedFormWhenRequestPathIsPercentEncoded() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParams());
    // Same aud entry as above; resourcePathEncoded is "/orders/a%20b". A request path header
    // carrying the already-escaped form matches that candidate instead of the raw one.
    final JWT token = delegationToken("https://service.local/ns/svc/orders/a b");
    final HttpServletRequest request = requestWithHeaders(headersWithPath("/orders/a%20b"));
    assertTrue(validator.validate(request, token, null).isValid());
  }

  // ---- audience.path.prefix applies only to the custom form ----

  @Test
  public void testAudiencePathPrefixAppliesToCustomFormAndParsingResumesAfterIt() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.AUDIENCE_PATH_PREFIX_PARAM, "internal");
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://service.local/internal/ns/svc/orders/42");
    final HttpServletRequest request = requestWithHeaders(headersWithPath("/orders/42"));
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testCustomFormFailsToParseWhenConfiguredPrefixIsAbsentFromPath() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.AUDIENCE_PATH_PREFIX_PARAM, "internal");
    final K8sDestinationAudienceValidator validator = init(params);
    // No "/internal/" segment anywhere in the path, so the prefix search never finds where
    // namespace and service-name parsing should resume, and the custom form does not parse.
    final JWT token = delegationToken("https://service.local/ns/svc/orders/42");
    final HttpServletRequest request = requestWithHeaders(headersWithPath("/orders/42"));
    assertFalse(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testDnsFormIgnoresAudiencePathPrefixEvenWhenPathLiterallyContainsIt() throws Exception {
    final Map<String, String> params = baseParams();
    // If the DNS form searched for and stripped this prefix the way the custom form does, the
    // leading "/orders/" would be consumed and the remaining resource path would be "/42" --
    // which would not match the request path below. Since the DNS form never applies this
    // parameter, the whole path "/orders/42" is kept as the resource path, and it matches.
    params.put(K8sDestinationAudienceValidator.AUDIENCE_PATH_PREFIX_PARAM, "orders");
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://svc.ns/orders/42");
    final HttpServletRequest request = requestWithHeaders(headersWithPath("/orders/42"));
    assertTrue(validator.validate(request, token, null).isValid());
  }

  // ---- a configured path header must be present and parseable, unconditionally ----

  @Test
  public void testPathHeaderMissingFailsEvenWithAWildcardAudienceEntry() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParams());
    final JWT token = delegationToken("https://service.local/ns/svc");
    final Map<String, String> headers = new HashMap<>();
    headers.put(SPIFFE_HEADER, DEST_SPIFFE_VALUE);
    final HttpServletRequest request = requestWithHeaders(headers);
    final AudienceValidationResult result = validator.validate(request, token, null);
    assertFalse(result.isValid());
    assertTrue(result.message().contains("destination path"));
    assertTrue(result.message().contains(PATH_HEADER));
  }

  @Test
  public void testPathHeaderEmptyValueFails() throws Exception {
    final K8sDestinationAudienceValidator validator = init(baseParams());
    final JWT token = delegationToken("https://service.local/ns/svc");
    final HttpServletRequest request = requestWithHeaders(headersWithPath(""));
    final AudienceValidationResult result = validator.validate(request, token, null);
    assertFalse(result.isValid());
    assertTrue(result.message().contains("destination path"));
  }

  // ---- path.header.from.url ----

  @Test
  public void testPathHeaderFromUrlTrueExtractsPathAndStripsQueryAndFragment() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_FROM_URL_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://service.local/ns/svc/orders/42");
    final HttpServletRequest request =
        requestWithHeaders(headersWithPath("https://gateway.example.com/orders/42?foo=bar#frag"));
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testPathHeaderFromUrlTrueFailsWhenHeaderValueHasNoSchemeSeparator() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_FROM_URL_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    // No "://" in the header value, so there is no authority to skip past, and the full-URL
    // parse fails even though this would be a perfectly good bare path.
    final JWT token = delegationToken("https://service.local/ns/svc/orders/42");
    final HttpServletRequest request = requestWithHeaders(headersWithPath("/orders/42"));
    final AudienceValidationResult result = validator.validate(request, token, null);
    assertFalse(result.isValid());
    assertTrue(result.message().contains("destination path"));
  }

  @Test
  public void testPathHeaderFromUrlTrueFailsWhenNoPathFollowsTheAuthority() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_FROM_URL_PARAM, "true");
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://service.local/ns/svc");
    final HttpServletRequest request = requestWithHeaders(headersWithPath("https://gateway.example.com"));
    final AudienceValidationResult result = validator.validate(request, token, null);
    assertFalse(result.isValid());
    assertTrue(result.message().contains("destination path"));
  }

  @Test
  public void testPathHeaderFromUrlDefaultFalseTreatsHeaderValueAsTheBarePathAndStripsQuery() throws Exception {
    // PATH_HEADER_FROM_URL_PARAM left unconfigured; defaults to false.
    final K8sDestinationAudienceValidator validator = init(baseParams());
    final JWT token = delegationToken("https://service.local/ns/svc/orders/42");
    final HttpServletRequest request = requestWithHeaders(headersWithPath("/orders/42?foo=bar"));
    assertTrue(validator.validate(request, token, null).isValid());
  }

  @Test
  public void testPathHeaderFromUrlFalseStripsFragmentFromTheBarePath() throws Exception {
    final Map<String, String> params = baseParams();
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_FROM_URL_PARAM, "false");
    final K8sDestinationAudienceValidator validator = init(params);
    final JWT token = delegationToken("https://service.local/ns/svc/orders/42");
    final HttpServletRequest request = requestWithHeaders(headersWithPath("/orders/42#section"));
    assertTrue(validator.validate(request, token, null).isValid());
  }
}
