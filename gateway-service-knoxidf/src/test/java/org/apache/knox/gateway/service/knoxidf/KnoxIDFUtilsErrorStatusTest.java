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

import javax.ws.rs.core.Response;

import org.apache.knox.gateway.util.knoxidf.KnoxIDFUtils;
import org.junit.Test;

/**
 * Verifies {@link KnoxIDFUtils#error(String, String)} maps each OAuth 2.0 error code to the HTTP
 * status RFC 6749 §5.2 prescribes, rather than the previous always-401 behavior.
 */
public class KnoxIDFUtilsErrorStatusTest {

  private static int statusOf(String error) {
    return KnoxIDFUtils.error(error, "desc").getStatus();
  }

  @Test
  public void testInvalidClientIsUnauthorized() {
    assertEquals(401, statusOf("invalid_client"));
  }

  @Test
  public void testAccessDeniedIsForbidden() {
    assertEquals(403, statusOf("access_denied"));
  }

  @Test
  public void testServerErrorIs500() {
    assertEquals(500, statusOf("server_error"));
  }

  @Test
  public void testTemporarilyUnavailableIs503() {
    assertEquals(503, statusOf("temporarily_unavailable"));
  }

  @Test
  public void testProtocolErrorsDefaultToBadRequest() {
    for (final String error : new String[]{"invalid_request", "invalid_grant", "invalid_scope",
        "unsupported_grant_type", "unsupported_response_type", "unauthorized_client"}) {
      assertEquals("Expected 400 for " + error, 400, statusOf(error));
    }
  }

  @Test
  public void testUnknownAndNullErrorDefaultToBadRequest() {
    assertEquals(400, statusOf("something_unexpected"));
    assertEquals(400, statusOf(null));
  }

  @Test
  public void testExplicitStatusOverloadWins() {
    final Response response = KnoxIDFUtils.error("invalid_request", "desc", Response.Status.CONFLICT);
    assertEquals(409, response.getStatus());
  }

  @Test
  public void testBodyCarriesErrorCodeAndDescription() {
    final String body = String.valueOf(KnoxIDFUtils.error("invalid_grant", "bad code").getEntity());
    assertTrue(body.contains("invalid_grant"));
    assertTrue(body.contains("bad code"));
  }
}
