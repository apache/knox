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

  @Test
  public void testParseBareServiceHasRootResourcePath() {
    final Optional<AudienceResource> parsed = AudienceResource.parse("https://cluster.local/ns/svc");
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
    final Optional<AudienceResource> parsed = AudienceResource.parse("https://cluster.local/ns/svc/a/b");
    assertTrue(parsed.isPresent());
    assertEquals("/a/b", parsed.get().resourcePathRaw());
    assertEquals("/a/b", parsed.get().resourcePathEncoded());
  }

  @Test
  public void testTrailingSlashTidiedNotMismatch() {
    final Optional<AudienceResource> withSlash = AudienceResource.parse("https://cluster.local/ns/svc/a/b/");
    final Optional<AudienceResource> withoutSlash = AudienceResource.parse("https://cluster.local/ns/svc/a/b");
    assertTrue(withSlash.isPresent());
    assertTrue(withoutSlash.isPresent());
    assertEquals(withoutSlash.get().resourcePathRaw(), withSlash.get().resourcePathRaw());
  }

  @Test
  public void testExplicitPort() {
    final Optional<AudienceResource> parsed = AudienceResource.parse("https://cluster.local:8443/ns/svc/a");
    assertTrue(parsed.isPresent());
    assertEquals(8443, parsed.get().effectivePort());
  }

  @Test
  public void testExplicitDefaultPortNormalizesSameAsAbsent() {
    final Optional<AudienceResource> explicit = AudienceResource.parse("https://cluster.local:443/ns/svc/a");
    final Optional<AudienceResource> absent = AudienceResource.parse("https://cluster.local/ns/svc/a");
    assertTrue(explicit.isPresent());
    assertTrue(absent.isPresent());
    assertEquals(absent.get().effectivePort(), explicit.get().effectivePort());
  }

  @Test
  public void testTrailingColonWithNoPortDigitsDefaultsToStandardPort() {
    // java.net.URI accepts a bare trailing colon (no port digits) without throwing, and reports
    // getPort() == -1 for it, same as when the colon is absent entirely.
    final Optional<AudienceResource> parsed = AudienceResource.parse("https://cluster.local:/ns/svc");
    assertTrue(parsed.isPresent());
    assertEquals(443, parsed.get().effectivePort());
  }

  @Test
  public void testRejectWrongScheme() {
    assertFalse(AudienceResource.parse("http://cluster.local/ns/svc").isPresent());
  }

  @Test
  public void testRejectMissingSchemeSeparator() {
    assertFalse(AudienceResource.parse("https:/cluster.local/ns/svc").isPresent());
  }

  @Test
  public void testRejectEmptyScheme() {
    assertFalse(AudienceResource.parse("://cluster.local/ns/svc").isPresent());
  }

  @Test
  public void testRejectNoAuthority() {
    assertFalse(AudienceResource.parse("https:///ns/svc").isPresent());
  }

  // The authority is handed to java.net.URI, so its own well-tested parsing -- not a hand-rolled
  // character check -- decides what counts as userinfo or an illegal character; these tests just
  // confirm this class rejects what java.net.URI flags, not reproduce URI's own test suite.
  @Test
  public void testRejectUserinfoPresent() {
    assertFalse(AudienceResource.parse("https://user@cluster.local/ns/svc").isPresent());
  }

  @Test
  public void testRejectUserinfoWithPassword() {
    assertFalse(AudienceResource.parse("https://user:pass@cluster.local/ns/svc").isPresent());
  }

  @Test
  public void testRejectAuthorityWithIllegalCharacter() {
    assertFalse(AudienceResource.parse("https://clus ter.local/ns/svc").isPresent());
  }

  @Test
  public void testRejectTooFewPathSegments() {
    assertFalse(AudienceResource.parse("https://cluster.local/ns").isPresent());
    assertFalse(AudienceResource.parse("https://cluster.local/").isPresent());
    assertFalse(AudienceResource.parse("https://cluster.local").isPresent());
  }

  @Test
  public void testRejectNonUrlLogicalName() {
    assertFalse(AudienceResource.parse("service-a").isPresent());
  }

  @Test
  public void testRejectBarePath() {
    assertFalse(AudienceResource.parse("/api/v1").isPresent());
  }

  @Test
  public void testRejectDotDotSegment() {
    assertFalse(AudienceResource.parse("https://cluster.local/ns/svc/../a").isPresent());
    assertFalse(AudienceResource.parse("https://cluster.local/ns/svc/a/..").isPresent());
  }

  @Test
  public void testRejectDotSegment() {
    assertFalse(AudienceResource.parse("https://cluster.local/ns/svc/a/./b").isPresent());
  }

  @Test
  public void testRejectEmptySegment() {
    assertFalse(AudienceResource.parse("https://cluster.local/ns/svc//").isPresent());
    assertFalse(AudienceResource.parse("https://cluster.local/ns/svc/a//b").isPresent());
  }

  @Test
  public void testEmptyNamespaceOrServiceNameSegmentParses() {
    // Namespace and service-name are not validated as DNS labels here (see the class javadoc): an
    // empty segment parses successfully rather than being rejected, since it simply will not match
    // a real namespace or service name later.
    final Optional<AudienceResource> emptyNamespace = AudienceResource.parse("https://cluster.local//svc/a");
    assertTrue(emptyNamespace.isPresent());
    assertEquals("", emptyNamespace.get().namespace());
    assertEquals("svc", emptyNamespace.get().serviceName());

    final Optional<AudienceResource> emptyServiceName = AudienceResource.parse("https://cluster.local/ns//a");
    assertTrue(emptyServiceName.isPresent());
    assertEquals("ns", emptyServiceName.get().namespace());
    assertEquals("", emptyServiceName.get().serviceName());
  }

  @Test
  public void testUnencodedAudiencePathCarriesBothCandidateForms() {
    final Optional<AudienceResource> parsed = AudienceResource.parse("https://cluster.local/ns/svc/a b");
    assertTrue(parsed.isPresent());
    assertEquals("/a b", parsed.get().resourcePathRaw());
    assertEquals("/a%20b", parsed.get().resourcePathEncoded());
  }

  @Test
  public void testAlreadyEscapedAudiencePathHasIdenticalCandidates() {
    final Optional<AudienceResource> parsed = AudienceResource.parse("https://cluster.local/ns/svc/a%20b");
    assertTrue(parsed.isPresent());
    assertEquals("/a%20b", parsed.get().resourcePathRaw());
    assertEquals("/a%20b", parsed.get().resourcePathEncoded());
  }

  @Test
  public void testEscapedSlashNeverEqualsLiteralSlash() {
    final Optional<AudienceResource> escaped = AudienceResource.parse("https://cluster.local/ns/svc/a%2Fb");
    final Optional<AudienceResource> literal = AudienceResource.parse("https://cluster.local/ns/svc/a/b");
    assertTrue(escaped.isPresent());
    assertTrue(literal.isPresent());
    assertFalse(escaped.get().resourcePathRaw().equals(literal.get().resourcePathRaw()));
    assertFalse(escaped.get().resourcePathEncoded().equals(literal.get().resourcePathEncoded()));
  }

  @Test
  public void testRejectNullAndEmpty() {
    assertFalse(AudienceResource.parse(null).isPresent());
    assertFalse(AudienceResource.parse("").isPresent());
    assertFalse(AudienceResource.parse("   ").isPresent());
  }

  @Test
  public void testWhitespacePaddedEntryTrimmed() {
    final Optional<AudienceResource> parsed = AudienceResource.parse("  https://cluster.local/ns/svc  ");
    assertTrue(parsed.isPresent());
    assertEquals("cluster.local", parsed.get().host());
  }

  @Test
  public void testSchemeComparedCaseInsensitively() {
    assertTrue(AudienceResource.parse("HTTPS://cluster.local/ns/svc").isPresent());
  }

  @Test
  public void testHostLowercased() {
    final Optional<AudienceResource> parsed = AudienceResource.parse("https://Cluster.Local/ns/svc");
    assertTrue(parsed.isPresent());
    assertEquals("cluster.local", parsed.get().host());
  }

  @Test
  public void testNamespaceAndServiceNameLowercased() {
    final Optional<AudienceResource> parsed = AudienceResource.parse("https://cluster.local/NS/SVC");
    assertTrue(parsed.isPresent());
    assertEquals("ns", parsed.get().namespace());
    assertEquals("svc", parsed.get().serviceName());
  }

  @Test
  public void testNullOrBlankPathPrefixDisablesSearch() {
    final Optional<AudienceResource> viaNull = AudienceResource.parse("https://cluster.local/ns/svc", null);
    final Optional<AudienceResource> viaEmpty = AudienceResource.parse("https://cluster.local/ns/svc", "");
    final Optional<AudienceResource> viaBlank = AudienceResource.parse("https://cluster.local/ns/svc", "   ");
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
        AudienceResource.parse("https://cluster.local/id/cluster-1/ns/svc/a", "id/cluster-1");
    assertTrue(parsed.isPresent());
    assertEquals("ns", parsed.get().namespace());
    assertEquals("svc", parsed.get().serviceName());
    assertEquals("/a", parsed.get().resourcePathRaw());
  }

  @Test
  public void testPathPrefixFoundAfterSkippedLeadingSegments() {
    // Segments before the prefix (here, "extra") are never parsed or validated -- just skipped.
    final Optional<AudienceResource> parsed =
        AudienceResource.parse("https://cluster.local/extra/id/cluster-1/ns/svc", "id/cluster-1");
    assertTrue(parsed.isPresent());
    assertEquals("ns", parsed.get().namespace());
    assertEquals("svc", parsed.get().serviceName());
  }

  @Test
  public void testPathPrefixFirstOccurrenceWins() {
    // The prefix's segments recur later in the path (as a namespace/service-name pair); the
    // first occurrence is the one used, not the second.
    final Optional<AudienceResource> parsed =
        AudienceResource.parse("https://cluster.local/id/cluster-1/id/cluster-1/svc", "id/cluster-1");
    assertTrue(parsed.isPresent());
    assertEquals("id", parsed.get().namespace());
    assertEquals("cluster-1", parsed.get().serviceName());
  }

  @Test
  public void testPathPrefixNotFoundDoesNotParse() {
    assertFalse(AudienceResource.parse("https://cluster.local/ns/svc", "id/cluster-1").isPresent());
    assertFalse(AudienceResource.parse("https://cluster.local/id/other-cluster/ns/svc", "id/cluster-1").isPresent());
  }

  @Test
  public void testPathPrefixLeavingTooFewSegmentsDoesNotParse() {
    assertFalse(AudienceResource.parse("https://cluster.local/id/cluster-1", "id/cluster-1").isPresent());
    assertFalse(AudienceResource.parse("https://cluster.local/id/cluster-1/ns", "id/cluster-1").isPresent());
  }

  @Test
  public void testPathPrefixMatchedCaseSensitively() {
    assertFalse(AudienceResource.parse("https://cluster.local/ID/CLUSTER-1/ns/svc", "id/cluster-1").isPresent());
  }

  @Test
  public void testPathPrefixLeadingAndTrailingSlashIgnored() {
    final String entry = "https://cluster.local/id/cluster-1/ns/svc";
    final Optional<AudienceResource> bare = AudienceResource.parse(entry, "id/cluster-1");
    final Optional<AudienceResource> leading = AudienceResource.parse(entry, "/id/cluster-1");
    final Optional<AudienceResource> trailing = AudienceResource.parse(entry, "id/cluster-1/");
    final Optional<AudienceResource> both = AudienceResource.parse(entry, "/id/cluster-1/");
    assertTrue(bare.isPresent());
    assertTrue(leading.isPresent());
    assertTrue(trailing.isPresent());
    assertTrue(both.isPresent());
    assertEquals("ns", leading.get().namespace());
    assertEquals("ns", trailing.get().namespace());
    assertEquals("ns", both.get().namespace());
  }

  @Test
  public void testPathPrefixDoesNotMatchPartialSegment() {
    // "id/cluster-1" must match whole segments, not a substring straddling segment boundaries.
    assertFalse(AudienceResource.parse("https://cluster.local/id/cluster-10/ns/svc", "id/cluster-1").isPresent());
  }
}
