/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.service.knoxidf;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Set;

import org.junit.Test;

/**
 * Verifies wildcard redirect_uri matching rejects path traversal (review finding M3). A wildcard
 * registration such as {@code https://app.example/callback/*} must not match
 * {@code https://app.example/callback/../admin}: a raw startsWith on the un-normalized path would
 * let the traversal escape the registered prefix and deliver the authorization code to /admin
 * (a same-host open redirect). The path is normalized before the prefix compare.
 */
public class AuthorizeResourceRedirectUriMatchTest {

  private final AuthorizeResource resource = new AuthorizeResource();

  private boolean matches(final String requested, final String registered) {
    final Set<String> registeredUris = Collections.singleton(registered);
    return resource.matchesRedirectUri(requested, registeredUris);
  }

  @Test
  public void testTraversalEscapingWildcardPrefixIsRejected() {
    assertFalse("A traversal that resolves outside the registered prefix must be rejected.",
        matches("https://app.example/callback/../admin", "https://app.example/callback/*"));
  }

  @Test
  public void testEncodedPrefixSuffixStillMatches() {
    assertTrue("A genuine path under the wildcard prefix must still match.",
        matches("https://app.example/callback/oauth", "https://app.example/callback/*"));
  }

  @Test
  public void testExactPrefixMatchesWildcard() {
    assertTrue("The wildcard base path itself must match.",
        matches("https://app.example/callback/", "https://app.example/callback/*"));
  }

  @Test
  public void testDifferentOriginIsRejected() {
    assertFalse("A same-prefix path on a different host must be rejected.",
        matches("https://app.example.evil.com/callback/x", "https://app.example/callback/*"));
  }

  @Test
  public void testExact(){
    assertTrue("An exact non-wildcard registration must match verbatim.",
        matches("https://app.example/cb", "https://app.example/cb"));
    assertFalse("An exact registration must not match a different path.",
        matches("https://app.example/cb2", "https://app.example/cb"));
  }
}
