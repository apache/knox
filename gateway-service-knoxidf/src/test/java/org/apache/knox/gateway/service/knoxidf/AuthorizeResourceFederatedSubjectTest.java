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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.ws.rs.core.Response;

import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.easymock.EasyMock;
import org.junit.Test;

/**
 * Verifies that a federated id_token missing the required {@code sub} claim is rejected as a 4xx
 * client/OP error rather than surfacing as an HTTP 500 (review finding M2). Knox derives the Knox
 * subject and the federated-identity primary key from {@code sub}, and the identity tables declare
 * {@code external_subject NOT NULL}; without this guard a broken or hostile OP omitting {@code sub}
 * drives a NOT NULL insert failure and a 500 on every callback through that OP.
 */
public class AuthorizeResourceFederatedSubjectTest {

  private static JWT idTokenWithSubject(final String subject) {
    final JWT idToken = EasyMock.createNiceMock(JWT.class);
    EasyMock.expect(idToken.getSubject()).andReturn(subject).anyTimes();
    EasyMock.replay(idToken);
    return idToken;
  }

  @Test
  public void testPresentSubjectPasses() {
    final Response result = new AuthorizeResource().requireFederatedSubject(idTokenWithSubject("user-123"));
    assertNull("An id_token carrying a sub claim must pass (null == no error).", result);
  }

  @Test
  public void testMissingSubjectIsRejectedWith4xx() {
    final Response result = new AuthorizeResource().requireFederatedSubject(idTokenWithSubject(null));
    assertNotNull("An id_token without a sub claim must be rejected.", result);
    assertEquals("A missing sub must be a client/OP error, not a 500.",
        Response.Status.BAD_REQUEST.getStatusCode(), result.getStatus());
    assertTrue("The error body should identify the invalid_request condition.",
        String.valueOf(result.getEntity()).contains("invalid_request"));
  }

  @Test
  public void testBlankSubjectIsRejectedWith4xx() {
    final Response result = new AuthorizeResource().requireFederatedSubject(idTokenWithSubject("   "));
    assertNotNull("An id_token with a blank sub claim must be rejected.", result);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), result.getStatus());
  }
}
