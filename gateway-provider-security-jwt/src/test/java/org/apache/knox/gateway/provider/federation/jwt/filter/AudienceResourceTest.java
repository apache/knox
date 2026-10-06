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
import static org.junit.Assert.assertTrue;

import java.util.Optional;
import org.junit.Test;

public class AudienceResourceTest {

  private static Optional<AudienceResource> parse(String audEntry) {
    return AudienceResource.parseCustomForm(audEntry, null);
  }

  private static Optional<AudienceResource> parseDns(String audEntry) {
    return AudienceResource.parseDnsForm(audEntry, "cluster.local");
  }

  private static Optional<AudienceResource> parseDns(String audEntry, String clusterDomain) {
    return AudienceResource.parseDnsForm(audEntry, clusterDomain);
  }

  @Test
  public void testParseBareServiceHasRootResourcePath() {
    final Optional<AudienceResource> parsed = parse("https://cluster.local/ns/svc");
    assertTrue(parsed.isPresent());
    final AudienceResource r = parsed.get();
    assertEquals("cluster.local", r.host());
    assertEquals(443, r.effectivePort());
    assertEquals("ns", r.namespace());
    assertEquals("svc", r.serviceName());
    assertEquals("/", r.resourcePathRaw());
    assertEquals("/", r.resourcePathEncoded());
  }

  @Test
  public void testParseWithResourcePath() {
    final Optional<AudienceResource> parsed = parse("https://cluster.local/ns/svc/a/b");
    assertTrue(parsed.isPresent());
    assertEquals("/a/b", parsed.get().resourcePathRaw());
    assertEquals("/a/b", parsed.get().resourcePathEncoded());
  }

  @Test
  public void testQueryStringStrippedFromResourcePath() {
    final Optional<AudienceResource> parsed = parse("https://cluster.local/ns/svc/a/b?x=1&y=2");
    assertTrue(parsed.isPresent());
    assertEquals("/a/b", parsed.get().resourcePathRaw());
  }

  @Test
  public void testFragmentStrippedFromResourcePath() {
    final Optional<AudienceResource> parsed = parse("https://cluster.local/ns/svc/a/b#section");
    assertTrue(parsed.isPresent());
    assertEquals("/a/b", parsed.get().resourcePathRaw());
  }

  @Test
  public void testQueryStringStrippedBeforePathPrefixSearch() {
    final Optional<AudienceResource> parsed = AudienceResource.parseCustomForm(
        "https://cluster.local/prefix/ns/svc/a?x=1", "prefix");
    assertTrue(parsed.isPresent());
    assertEquals("ns", parsed.get().namespace());
    assertEquals("/a", parsed.get().resourcePathRaw());
  }

  @Test
  public void testTrailingSlashTidiedNotMismatch() {
    final Optional<AudienceResource> withSlash = parse("https://cluster.local/ns/svc/a/b/");
    final Optional<AudienceResource> withoutSlash = parse("https://cluster.local/ns/svc/a/b");
    assertTrue(withSlash.isPresent());
    assertTrue(withoutSlash.isPresent());
    assertEquals(withoutSlash.get().resourcePathRaw(), withSlash.get().resourcePathRaw());
  }

  @Test
  public void testExplicitPort() {
    final Optional<AudienceResource> parsed = parse("https://cluster.local:8443/ns/svc/a");
    assertTrue(parsed.isPresent());
    assertEquals(8443, parsed.get().effectivePort());
  }

  @Test
  public void testExplicitDefaultPortNormalizesSameAsAbsent() {
    final Optional<AudienceResource> explicit = parse("https://cluster.local:443/ns/svc/a");
    final Optional<AudienceResource> absent = parse("https://cluster.local/ns/svc/a");
    assertTrue(explicit.isPresent());
    assertTrue(absent.isPresent());
    assertEquals(absent.get().effectivePort(), explicit.get().effectivePort());
  }

  @Test
  public void testTrailingColonWithNoPortDigitsDefaultsToStandardPort() {
    // java.net.URI accepts a bare trailing colon (no port digits) without throwing, and reports
    // getPort() == -1 for it, same as when the colon is absent entirely.
    final Optional<AudienceResource> parsed = parse("https://cluster.local:/ns/svc");
    assertTrue(parsed.isPresent());
    assertEquals(443, parsed.get().effectivePort());
  }

  @Test
  public void testRejectWrongScheme() {
    assertFalse(parse("http://cluster.local/ns/svc").isPresent());
  }

  @Test
  public void testRejectMissingSchemeSeparator() {
    assertFalse(parse("https:/cluster.local/ns/svc").isPresent());
  }

  @Test
  public void testRejectEmptyScheme() {
    assertFalse(parse("://cluster.local/ns/svc").isPresent());
  }

  @Test
  public void testRejectNoAuthority() {
    assertFalse(parse("https:///ns/svc").isPresent());
  }

  // The authority is handed to java.net.URI, so its own well-tested parsing -- not a hand-rolled
  // character check -- decides what counts as userinfo or an illegal character; these tests just
  // confirm this class rejects what java.net.URI flags, not reproduce URI's own test suite.
  @Test
  public void testRejectUserinfoPresent() {
    assertFalse(parse("https://user@cluster.local/ns/svc").isPresent());
  }

  @Test
  public void testRejectUserinfoWithPassword() {
    assertFalse(parse("https://user:pass@cluster.local/ns/svc").isPresent());
  }

  @Test
  public void testRejectAuthorityWithIllegalCharacter() {
    assertFalse(parse("https://clus ter.local/ns/svc").isPresent());
  }

  @Test
  public void testRejectTooFewPathSegments() {
    assertFalse(parse("https://cluster.local/ns").isPresent());
    assertFalse(parse("https://cluster.local/").isPresent());
    assertFalse(parse("https://cluster.local").isPresent());
  }

  @Test
  public void testRejectNonUrlLogicalName() {
    assertFalse(parse("service-a").isPresent());
  }

  @Test
  public void testRejectBarePath() {
    assertFalse(parse("/api/v1").isPresent());
  }

  @Test
  public void testRejectDotDotSegment() {
    assertFalse(parse("https://cluster.local/ns/svc/../a").isPresent());
    assertFalse(parse("https://cluster.local/ns/svc/a/..").isPresent());
  }

  @Test
  public void testRejectDotSegment() {
    assertFalse(parse("https://cluster.local/ns/svc/a/./b").isPresent());
  }

  @Test
  public void testRejectEmptySegment() {
    assertFalse(parse("https://cluster.local/ns/svc//").isPresent());
    assertFalse(parse("https://cluster.local/ns/svc/a//b").isPresent());
  }

  @Test
  public void testEmptyNamespaceOrServiceNameSegmentParses() {
    // Namespace and service-name are not validated as DNS labels here (see the class javadoc): an
    // empty segment parses successfully rather than being rejected, since it simply will not match
    // a real namespace or service name later.
    final Optional<AudienceResource> emptyNamespace = parse("https://cluster.local//svc/a");
    assertTrue(emptyNamespace.isPresent());
    assertEquals("", emptyNamespace.get().namespace());
    assertEquals("svc", emptyNamespace.get().serviceName());

    final Optional<AudienceResource> emptyServiceName = parse("https://cluster.local/ns//a");
    assertTrue(emptyServiceName.isPresent());
    assertEquals("ns", emptyServiceName.get().namespace());
    assertEquals("", emptyServiceName.get().serviceName());
  }

  @Test
  public void testUnencodedAudiencePathCarriesBothCandidateForms() {
    final Optional<AudienceResource> parsed = parse("https://cluster.local/ns/svc/a b");
    assertTrue(parsed.isPresent());
    assertEquals("/a b", parsed.get().resourcePathRaw());
    assertEquals("/a%20b", parsed.get().resourcePathEncoded());
  }

  @Test
  public void testAlreadyEscapedAudiencePathHasIdenticalCandidates() {
    final Optional<AudienceResource> parsed = parse("https://cluster.local/ns/svc/a%20b");
    assertTrue(parsed.isPresent());
    assertEquals("/a%20b", parsed.get().resourcePathRaw());
    assertEquals("/a%20b", parsed.get().resourcePathEncoded());
  }

  @Test
  public void testEscapedSlashNeverEqualsLiteralSlash() {
    final Optional<AudienceResource> escaped = parse("https://cluster.local/ns/svc/a%2Fb");
    final Optional<AudienceResource> literal = parse("https://cluster.local/ns/svc/a/b");
    assertTrue(escaped.isPresent());
    assertTrue(literal.isPresent());
    assertFalse(escaped.get().resourcePathRaw().equals(literal.get().resourcePathRaw()));
    assertFalse(escaped.get().resourcePathEncoded().equals(literal.get().resourcePathEncoded()));
  }

  @Test
  public void testRejectNullAndEmpty() {
    assertFalse(parse(null).isPresent());
    assertFalse(parse("").isPresent());
    assertFalse(parse("   ").isPresent());
  }

  @Test
  public void testWhitespacePaddedEntryTrimmed() {
    final Optional<AudienceResource> parsed = parse("  https://cluster.local/ns/svc  ");
    assertTrue(parsed.isPresent());
    assertEquals("cluster.local", parsed.get().host());
  }

  @Test
  public void testSchemeComparedCaseInsensitively() {
    assertTrue(parse("HTTPS://cluster.local/ns/svc").isPresent());
  }

  @Test
  public void testHostLowercased() {
    final Optional<AudienceResource> parsed = parse("https://Cluster.Local/ns/svc");
    assertTrue(parsed.isPresent());
    assertEquals("cluster.local", parsed.get().host());
  }

  @Test
  public void testNamespaceAndServiceNameLowercased() {
    final Optional<AudienceResource> parsed = parse("https://cluster.local/NS/SVC");
    assertTrue(parsed.isPresent());
    assertEquals("ns", parsed.get().namespace());
    assertEquals("svc", parsed.get().serviceName());
  }

  @Test
  public void testNullOrBlankPathPrefixDisablesSearch() {
    final Optional<AudienceResource> viaNull = AudienceResource.parseCustomForm("https://cluster.local/ns/svc", null);
    final Optional<AudienceResource> viaEmpty = AudienceResource.parseCustomForm("https://cluster.local/ns/svc", "");
    final Optional<AudienceResource> viaBlank = AudienceResource.parseCustomForm("https://cluster.local/ns/svc", "   ");
    assertTrue(viaNull.isPresent());
    assertTrue(viaEmpty.isPresent());
    assertTrue(viaBlank.isPresent());
    assertEquals("ns", viaNull.get().namespace());
    assertEquals("ns", viaEmpty.get().namespace());
    assertEquals("ns", viaBlank.get().namespace());
  }

  @Test
  public void testPathPrefixFoundImmediatelyAfterAuthority() {
    final Optional<AudienceResource> parsed =
        AudienceResource.parseCustomForm("https://cluster.local/prefix/ns/svc/a", "prefix");
    assertTrue(parsed.isPresent());
    assertEquals("ns", parsed.get().namespace());
    assertEquals("svc", parsed.get().serviceName());
    assertEquals("/a", parsed.get().resourcePathRaw());
  }

  @Test
  public void testPathPrefixFoundAfterSkippedLeadingSegments() {
    // Segments before the prefix (here, "id/cluster-1") are never parsed or validated -- just
    // skipped.
    final Optional<AudienceResource> parsed =
        AudienceResource.parseCustomForm("https://cluster.local/id/cluster-1/prefix/ns/svc/a", "prefix");
    assertTrue(parsed.isPresent());
    assertEquals("ns", parsed.get().namespace());
    assertEquals("svc", parsed.get().serviceName());
    assertEquals("/a", parsed.get().resourcePathRaw());
  }

  @Test
  public void testPathPrefixFirstOccurrenceWins() {
    // The prefix recurs later in the path (as a namespace segment); the first occurrence is the
    // one used to resume parsing, not the second.
    final Optional<AudienceResource> parsed =
        AudienceResource.parseCustomForm("https://cluster.local/prefix/prefix/svc", "prefix");
    assertTrue(parsed.isPresent());
    assertEquals("prefix", parsed.get().namespace());
    assertEquals("svc", parsed.get().serviceName());
  }

  @Test
  public void testPathPrefixNotFoundDoesNotParse() {
    assertFalse(AudienceResource.parseCustomForm("https://cluster.local/ns/svc", "prefix").isPresent());
    assertFalse(AudienceResource.parseCustomForm("https://cluster.local/other/ns/svc", "prefix").isPresent());
  }

  @Test
  public void testPathPrefixLeavingTooFewSegmentsDoesNotParse() {
    assertFalse(AudienceResource.parseCustomForm("https://cluster.local/prefix", "prefix").isPresent());
    assertFalse(AudienceResource.parseCustomForm("https://cluster.local/prefix/ns", "prefix").isPresent());
  }

  @Test
  public void testPathPrefixMatchedCaseSensitively() {
    assertFalse(AudienceResource.parseCustomForm("https://cluster.local/PREFIX/ns/svc", "prefix").isPresent());
  }

  @Test
  public void testPathPrefixDoesNotMatchPartialSegment() {
    // "prefix" must match a whole segment, not a substring straddling a segment boundary.
    assertFalse(AudienceResource.parseCustomForm("https://cluster.local/prefixed/ns/svc", "prefix").isPresent());
  }

  @Test
  public void testMultiSegmentPathPrefixMatchesAsOneToken() {
    final Optional<AudienceResource> parsed =
        AudienceResource.parseCustomForm("https://cluster.local/a/b/ns/svc", "a/b");
    assertTrue(parsed.isPresent());
    assertEquals("ns", parsed.get().namespace());
    assertEquals("svc", parsed.get().serviceName());
  }

  // ---- DNS-form parsing ----

  @Test
  public void testDnsFormOneLabelHostIsServiceOnly() {
    final Optional<AudienceResource> parsed = parseDns("https://svc");
    assertTrue(parsed.isPresent());
    assertEquals("svc", parsed.get().serviceName());
    assertEquals(null, parsed.get().namespace());
    assertEquals("/", parsed.get().resourcePathRaw());
  }

  @Test
  public void testDnsFormResultHasNoHostOrPort() {
    // host and effectivePort are meaningful only for a custom-form result; a DNS-form result sets
    // the sentinel values null/-1 since its cluster-domain host and port are not retained.
    final Optional<AudienceResource> parsed = parseDns("https://svc.ns.svc.cluster.local:8443/a");
    assertTrue(parsed.isPresent());
    assertEquals(null, parsed.get().host());
    assertEquals(-1, parsed.get().effectivePort());
  }

  @Test
  public void testDnsFormTwoLabelHostIsServiceAndNamespace() {
    final Optional<AudienceResource> parsed = parseDns("https://svc.ns");
    assertTrue(parsed.isPresent());
    assertEquals("svc", parsed.get().serviceName());
    assertEquals("ns", parsed.get().namespace());
  }

  @Test
  public void testDnsFormThreeLabelHostRequiresLiteralSvc() {
    final Optional<AudienceResource> parsed = parseDns("https://svc.ns.svc");
    assertTrue(parsed.isPresent());
    assertEquals("svc", parsed.get().serviceName());
    assertEquals("ns", parsed.get().namespace());
  }

  @Test
  public void testDnsFormThreeLabelHostWithWrongThirdLabelDoesNotParse() {
    // Third label is not literally "svc" -- an ordinary 3-label ingress hostname like
    // knox.example.com must fail DNS-form parsing cleanly, not swallow it.
    assertFalse(parseDns("https://knox.example.com").isPresent());
  }

  @Test
  public void testDnsFormFourLabelHostAcceptsOneClusterDomainLabel() {
    final Optional<AudienceResource> parsed = parseDns("https://svc.ns.svc.cluster");
    assertTrue(parsed.isPresent());
    assertEquals("svc", parsed.get().serviceName());
    assertEquals("ns", parsed.get().namespace());
  }

  @Test
  public void testDnsFormFiveLabelHostAcceptsFullClusterDomain() {
    final Optional<AudienceResource> parsed = parseDns("https://svc.ns.svc.cluster.local");
    assertTrue(parsed.isPresent());
    assertEquals("svc", parsed.get().serviceName());
    assertEquals("ns", parsed.get().namespace());
  }

  @Test
  public void testDnsFormRejectsNonPrefixClusterDomainSuffix() {
    // "local" alone is not a label-boundary prefix of "cluster.local" taken from the left.
    assertFalse(parseDns("https://svc.ns.svc.local").isPresent());
  }

  @Test
  public void testDnsFormRejectsClusterDomainLookalike() {
    // "cluster.locale" is not "cluster.local" -- not a prefix, even though it starts the same.
    assertFalse(parseDns("https://svc.ns.svc.cluster.locale").isPresent());
  }

  @Test
  public void testDnsFormRejectsMoreLabelsThanClusterDomainHas() {
    assertFalse(parseDns("https://svc.ns.svc.cluster.local.extra").isPresent());
  }

  @Test
  public void testDnsFormHonorsClusterDomainOverride() {
    final Optional<AudienceResource> parsed =
        parseDns("https://svc.ns.svc.example.org", "example.org");
    assertTrue(parsed.isPresent());
    assertEquals("svc", parsed.get().serviceName());
    assertEquals("ns", parsed.get().namespace());
    assertFalse(parseDns("https://svc.ns.svc.example.org", "cluster.local").isPresent());
  }

  @Test
  public void testDnsFormRejectsTrailingDot() {
    assertFalse(parseDns("https://svc.ns.svc.cluster.local.").isPresent());
    assertFalse(parseDns("https://svc.").isPresent());
  }

  @Test
  public void testDnsFormRejectsEmptyLabel() {
    assertFalse(parseDns("https://svc..ns").isPresent());
  }

  @Test
  public void testDnsFormIsCaseInsensitiveOnHost() {
    final Optional<AudienceResource> parsed = parseDns("https://SVC.NS.SVC.CLUSTER.LOCAL");
    assertTrue(parsed.isPresent());
    assertEquals("svc", parsed.get().serviceName());
    assertEquals("ns", parsed.get().namespace());
  }

  @Test
  public void testDnsFormPortIsParsedButIgnored() {
    final Optional<AudienceResource> withPort = parseDns("https://svc.ns:8443");
    assertTrue(withPort.isPresent());
    assertEquals("svc", withPort.get().serviceName());
    assertEquals("ns", withPort.get().namespace());
    // Non-numeric or otherwise malformed port still fails parsing like any invalid authority.
    assertFalse(parseDns("https://svc.ns:notaport").isPresent());
  }

  @Test
  public void testDnsFormWithNoPathIsWildcard() {
    final Optional<AudienceResource> parsed = parseDns("https://svc.ns");
    assertTrue(parsed.isPresent());
    assertEquals("/", parsed.get().resourcePathRaw());
  }

  @Test
  public void testDnsFormWithRootPathIsWildcard() {
    final Optional<AudienceResource> parsed = parseDns("https://svc.ns/");
    assertTrue(parsed.isPresent());
    assertEquals("/", parsed.get().resourcePathRaw());
  }

  @Test
  public void testDnsFormWholePathIsResourcePath() {
    // No namespace/service-name to skip over in the path -- the whole thing is the resource path.
    final Optional<AudienceResource> parsed = parseDns("https://svc.ns/a/b/c");
    assertTrue(parsed.isPresent());
    assertEquals("/a/b/c", parsed.get().resourcePathRaw());
  }

  @Test
  public void testDnsFormStripsQueryAndFragment() {
    final Optional<AudienceResource> parsed = parseDns("https://svc.ns/a?x=1#y");
    assertTrue(parsed.isPresent());
    assertEquals("/a", parsed.get().resourcePathRaw());
  }

  @Test
  public void testDnsFormRejectsDotSegmentsInPath() {
    assertFalse(parseDns("https://svc.ns/a/../b").isPresent());
    assertFalse(parseDns("https://svc.ns/a//b").isPresent());
  }

  @Test
  public void testDnsFormRejectsWrongSchemeOrUserinfo() {
    assertFalse(AudienceResource.parseDnsForm("spiffe://svc.ns", "cluster.local").isPresent());
    assertFalse(AudienceResource.parseDnsForm("https://user@svc.ns", "cluster.local").isPresent());
  }

  @Test
  public void testDnsFormRejectsNullAndBlank() {
    assertFalse(AudienceResource.parseDnsForm(null, "cluster.local").isPresent());
    assertFalse(AudienceResource.parseDnsForm("", "cluster.local").isPresent());
    assertFalse(AudienceResource.parseDnsForm("   ", "cluster.local").isPresent());
  }

  @Test
  public void testDnsFormBaseDomainWithTwoLabelsAlsoParsesAsDnsForm() {
    // The regression case behind D1: a custom-form aud with a 2-label base domain, e.g.
    // "https://knox.local/ns/svc/path", also parses cleanly as DNS form (service=knox,
    // namespace=local) -- resolving which interpretation is intended is the validator's job
    // (try DNS form first, fall back to custom form on parse OR match failure), not this
    // parser's.
    final Optional<AudienceResource> parsed = parseDns("https://knox.local/ns/svc/path");
    assertTrue(parsed.isPresent());
    assertEquals("knox", parsed.get().serviceName());
    assertEquals("local", parsed.get().namespace());
    assertEquals("/ns/svc/path", parsed.get().resourcePathRaw());
  }
}
