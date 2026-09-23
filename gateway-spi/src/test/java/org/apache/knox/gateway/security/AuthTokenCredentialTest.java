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
package org.apache.knox.gateway.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AuthTokenCredentialTest {

  private static final String TOKEN = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJndWVzdCJ9.c2lnbmF0dXJlLXZhbHVl";

  @Test
  public void testGetTokenReturnsTheSerializedToken() {
    assertEquals(TOKEN, new AuthTokenCredential(TOKEN).getToken());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNullTokenIsRejected() {
    new AuthTokenCredential(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEmptyTokenIsRejected() {
    new AuthTokenCredential("");
  }

  @Test
  public void testEqualsAndHashCodeAreValueBased() {
    final AuthTokenCredential one = new AuthTokenCredential(TOKEN);
    final AuthTokenCredential same = new AuthTokenCredential(TOKEN);
    final AuthTokenCredential other = new AuthTokenCredential(TOKEN + "-different");

    assertEquals(one, same);
    assertEquals(one.hashCode(), same.hashCode());
    assertNotEquals(one, other);
    assertNotEquals(one, null);
    assertNotEquals(one, TOKEN);
  }

  /* The raw token must never reach a log line via toString(). */
  @Test
  public void testToStringDoesNotLeakTheToken() {
    final String rendered = new AuthTokenCredential(TOKEN).toString();
    assertFalse(rendered.contains(TOKEN));
    assertTrue(rendered.contains("AuthTokenCredential"));
  }

  /*
   * Tokens.getTokenDisplayText returns null below 7 characters; toString must still
   * redact rather than fall back to the raw value.
   */
  @Test
  public void testToStringRedactsShortTokenWithNoDisplayText() {
    final String shortToken = "abc";
    final String rendered = new AuthTokenCredential(shortToken).toString();
    assertFalse(rendered.contains(shortToken));
  }
}
