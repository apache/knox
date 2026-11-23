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
package org.apache.knox.gateway.filter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PathAclCanonicalizerTest {
  private static final String BASE = "https://localhost:8443";

  @Test
  public void testPlainPathIsUnchanged() {
    assertEquals(BASE + "/gateway/z/svc/admin/op", PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/admin/op"));
  }

  @Test
  public void testPercentEncodedCharactersAreDecoded() {
    assertEquals(BASE + "/gateway/z/svc/admin/op", PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/%61dmin/op"));
    assertEquals(BASE + "/gateway/z/svc/admin/op", PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/%61%64%6D%69%6E/op"));
  }

  @Test
  public void testMatrixParametersAreStripped() {
    assertEquals(BASE + "/gateway/z/svc/admin/op", PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/admin;x=1/op"));
    assertEquals(BASE + "/gateway/z/svc/admin/op", PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/x/..;y/admin/op"));
  }

  @Test
  public void testDotSegmentsAreRemoved() {
    assertEquals(BASE + "/gateway/z/svc/admin/op", PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/./admin/op"));
    assertEquals(BASE + "/gateway/z/svc/admin/op", PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/public/../admin/op"));
    assertEquals(BASE + "/gateway/z/svc/admin/op", PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/public/%2e%2e/admin/op"));
    assertEquals(BASE + "/gateway/z/svc/admin/op", PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/public/%2E%2E/admin/op"));
  }

  @Test
  public void testEncodedSeparatorsBecomeSegments() {
    assertEquals(BASE + "/gateway/z/svc/admin/op", PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/public%2F..%2Fadmin/op"));
    assertEquals(BASE + "/gateway/z/svc/admin/op", PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/public%5C..%5Cadmin/op"));
  }

  @Test
  public void testEmptySegmentsAreCollapsed() {
    assertEquals(BASE + "/gateway/z/svc/admin/op", PathAclCanonicalizer.canonicalize(BASE + "/gateway//z/svc//admin/op"));
  }

  @Test
  public void testTrailingSlashIsKept() {
    assertEquals(BASE + "/gateway/z/svc/admin/", PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/admin/"));
    assertEquals(BASE + "/", PathAclCanonicalizer.canonicalize(BASE + "/"));
  }

  @Test
  public void testDecodingHappensOnlyOnce() {
    assertEquals(BASE + "/gateway/z/svc/%2561dmin/op", PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/%2561dmin/op"));
  }

  @Test
  public void testSpecialCharactersAreReEncoded() {
    assertEquals(BASE + "/gateway/z/svc/a%3Fb/a%23b/a;b/a%20b/a+b",
        PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/a%3Fb/a%23b/a%3Bb/a%20b/a+b"));
  }

  @Test
  public void testUtf8IsDecoded() {
    assertEquals(BASE + "/gateway/z/svc/été", PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/%C3%A9t%C3%A9"));
  }

  @Test
  public void testTraversalAboveRootIsRejected() {
    assertNull(PathAclCanonicalizer.canonicalize(BASE + "/gateway/../../admin"));
    assertNull(PathAclCanonicalizer.canonicalize(BASE + "/%2e%2e/admin"));
  }

  @Test
  public void testMalformedEncodingIsRejected() {
    assertNull(PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/%zzdmin/op"));
    assertNull(PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/admin%2"));
  }

  @Test
  public void testInvalidUtf8IsRejected() {
    assertNull(PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/%C3/op"));
    assertNull(PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/%C0%AFadmin/op"));
    assertNull(PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/%FF/op"));
    assertNull(PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/%ED%A0%80/op"));
  }

  @Test
  public void testDotSegmentWithEncodedParameterIsRejected() {
    assertNull(PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/x/%2e%2e%3b/admin/op"));
    assertNull(PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/x/..%3Bx=1/admin/op"));
    assertNull(PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/x/.%3b/admin/op"));
  }

  @Test
  public void testEncodedSemicolonInNameIsKept() {
    assertEquals(BASE + "/gateway/z/svc/a;b/op", PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/a%3Bb/op"));
    assertEquals(BASE + "/gateway/z/svc/..a;b/op", PathAclCanonicalizer.canonicalize(BASE + "/gateway/z/svc/..a%3Bb/op"));
  }

  @Test
  public void testUrlWithoutPath() {
    assertEquals(BASE, PathAclCanonicalizer.canonicalize(BASE));
    assertNull(PathAclCanonicalizer.canonicalize(null));
  }
}
