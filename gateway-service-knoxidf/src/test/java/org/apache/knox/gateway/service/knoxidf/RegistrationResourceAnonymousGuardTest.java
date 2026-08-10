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

import javax.security.auth.Subject;

import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.junit.Test;

/**
 * Verifies finding 1.3: dynamic client registration refuses anonymous callers unless the
 * deployment explicitly opts in via {@code knoxidf.client.registration.anonymous.allowed}. The
 * endpoint is wired as {@code anon} in the sample topologies, so this resource-level check is what
 * keeps registration closed by default.
 */
public class RegistrationResourceAnonymousGuardTest {

  /** Exposes injection of the opt-in flag without running the full JAX-RS/servlet lifecycle. */
  static final class TestableRegistrationResource extends RegistrationResource {
    TestableRegistrationResource(final boolean anonymousRegistrationAllowed) {
      this.anonymousRegistrationAllowed = anonymousRegistrationAllowed;
    }
  }

  private static boolean deniedAs(final String principalName, final boolean anonymousAllowed) {
    final TestableRegistrationResource resource = new TestableRegistrationResource(anonymousAllowed);
    if (principalName == null) {
      // No security context at all.
      return resource.anonymousRegistrationDenied();
    }
    final Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal(principalName));
    return Subject.doAs(subject, (PrivilegedAction<Boolean>) resource::anonymousRegistrationDenied);
  }

  @Test
  public void testAnonymousCallerRejectedByDefault() {
    // Default (flag false): the AnonymousAuthFilter principal ("anonymous") must be turned away.
    assertTrue(deniedAs("anonymous", false));
  }

  @Test
  public void testAnonymousCallerAllowedWhenExplicitlyEnabled() {
    // The deliberate open-registration deployment mode: opt in and the anonymous caller is allowed.
    assertFalse(deniedAs("anonymous", true));
  }

  @Test
  public void testAuthenticatedCallerAlwaysAllowed() {
    // A real authenticated principal is never subject to the anonymous gate, regardless of the flag.
    assertFalse(deniedAs("alice", false));
    assertFalse(deniedAs("alice", true));
  }

  @Test
  public void testAnonymousMatchIsCaseInsensitive() {
    assertTrue(deniedAs("ANONYMOUS", false));
  }
}
