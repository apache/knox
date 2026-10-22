/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.easymock.EasyMock;
import org.junit.Test;

/**
 * Regression coverage for {@link ActorIdentity#fromJwt(JWT)}.
 */
public class ActorIdentityTest {
  @Test
  public void testServiceAccountSubjectIsTaggedK8sSa() {
    final ActorIdentity identity = ActorIdentity.fromJwt(jwt("system:serviceaccount:demo:demo-app", "https://issuer.example.org"));
    assertEquals("K8S_SA", identity.actorAuthority);
    assertEquals("https://issuer.example.org:demo:demo-app", identity.actorId);
  }

  @Test
  public void testNonServiceAccountSubjectIsTaggedUser() {
    final ActorIdentity identity = ActorIdentity.fromJwt(jwt("alice", "https://issuer.example.org"));
    assertEquals("USER", identity.actorAuthority);
    assertEquals("alice", identity.actorId);
  }

  @Test
  public void testNullSubjectIsTaggedUser() {
    final ActorIdentity identity = ActorIdentity.fromJwt(jwt(null, "https://issuer.example.org"));
    assertEquals("USER", identity.actorAuthority);
    assertEquals(null, identity.actorId);
  }

  @Test
  public void testPrefixedSubjectWithNoSecondColonIsStillTaggedK8sSa() {
    // ServiceAccountSubject.parse rejects this shape, but ActorIdentity.fromJwt's own logic only
    // checks the prefix, not the full shape, and must keep doing exactly that.
    final ActorIdentity identity = ActorIdentity.fromJwt(jwt("system:serviceaccount:nsonly", "https://issuer.example.org"));
    assertEquals("K8S_SA", identity.actorAuthority);
    assertEquals("https://issuer.example.org:nsonly", identity.actorId);
  }

  @Test
  public void testBarePrefixIsStillTaggedK8sSa() {
    final ActorIdentity identity = ActorIdentity.fromJwt(jwt("system:serviceaccount:", "https://issuer.example.org"));
    assertEquals("K8S_SA", identity.actorAuthority);
    assertEquals("https://issuer.example.org:", identity.actorId);
  }

  @Test
  public void testResourceNameCombinesAuthorityAndId() {
    final ActorIdentity identity = ActorIdentity.fromJwt(jwt("system:serviceaccount:demo:demo-app", "https://issuer.example.org"));
    assertEquals("K8S_SA/https://issuer.example.org:demo:demo-app", identity.resourceName());
  }

  private static JWT jwt(String subject, String issuer) {
    final JWT jwt = EasyMock.createNiceMock(JWT.class);
    EasyMock.expect(jwt.getSubject()).andReturn(subject).anyTimes();
    EasyMock.expect(jwt.getIssuer()).andReturn(issuer).anyTimes();
    EasyMock.replay(jwt);
    return jwt;
  }
}
