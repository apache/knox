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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import jakarta.ws.rs.core.Response;

import org.junit.Test;

/**
 * Verifies that a consent POST without a {@code state} form field is rejected as a 400
 * {@code invalid_request} instead of reaching the state store with a null key (which failed with
 * an NPE inside the cache and surfaced as a 500).
 */
public class AuthorizeResourceConsentStateParamTest {

  private static void assertMissingStateRejected(final String state) throws Exception {
    final Response result = new AuthorizeResource().consentAccepted(state);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), result.getStatus());
    assertTrue(String.valueOf(result.getEntity()).contains("invalid_request"));
  }

  @Test
  public void testNullStateIsRejectedWith400() throws Exception {
    assertMissingStateRejected(null);
  }

  @Test
  public void testBlankStateIsRejectedWith400() throws Exception {
    assertMissingStateRejected("  ");
  }
}
