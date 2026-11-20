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

import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.security.auth.Subject;

import org.apache.knox.gateway.security.ActorChainPrincipalImpl;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.junit.Test;

/**
 * Verifies that dynamic client registration refuses a caller whose token carries an RFC 8693 actor chain
 * unless the deployment explicitly opts out via
 * {@code knoxidf.client.registration.delegated.callers.denied=false}. Otherwise a token delegated for one
 * audience could create a client owned by the user it acts for.
 */
public class RegistrationResourceDelegatedCallerGuardTest {

  private static final String USER = "alice";

  /** Exposes injection of the flag without running the full JAX-RS/servlet lifecycle. */
  static final class TestableRegistrationResource extends RegistrationResource {
    TestableRegistrationResource(final boolean delegatedCallersDenied) {
      this.delegatedCallersDenied = delegatedCallersDenied;
    }
  }

  private static boolean deniedAs(final Subject subject, final boolean delegatedCallersDenied) {
    final TestableRegistrationResource resource = new TestableRegistrationResource(delegatedCallersDenied);
    if (subject == null) {
      // No security context at all.
      return resource.delegatedCallerDenied();
    }
    return Subject.doAs(subject, (PrivilegedAction<Boolean>) resource::delegatedCallerDenied);
  }

  private static Subject userSubject() {
    final Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal(USER));
    return subject;
  }

  private static Subject delegatedSubject() {
    final Subject subject = userSubject();
    final List<Map<String, Object>> actorChain = Collections.singletonList(Collections.singletonMap("sub", "engine-sa"));
    subject.getPrincipals().add(new ActorChainPrincipalImpl(actorChain));
    return subject;
  }

  @Test
  public void testDelegatedCallerRejectedByDefault() {
    assertTrue(deniedAs(delegatedSubject(), true));
  }

  @Test
  public void testDelegatedCallerAllowedWhenExplicitlyEnabled() {
    assertFalse(deniedAs(delegatedSubject(), false));
  }

  @Test
  public void testNonDelegatedCallerAllowed() {
    assertFalse(deniedAs(userSubject(), true));
  }

  @Test
  public void testNoSecurityContextAllowed() {
    // Without a subject there is no actor chain; the anonymous guard handles unauthenticated callers.
    assertFalse(deniedAs(null, true));
  }
}
