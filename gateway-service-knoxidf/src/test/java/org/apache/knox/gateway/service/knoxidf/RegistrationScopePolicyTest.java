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

import org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the server-side registerable-scope whitelist: when the operator leaves
 * {@code knoxidf.registration.allowed.scopes} unset the OIDC-standard scope set is the bound, and an
 * explicit config is parsed into the authoritative set (with {@code openid} always registerable).
 */
public class RegistrationScopePolicyTest {

  @Test
  public void testUnsetDefaultsToOidcStandardScopes() {
    // Only a genuinely blank (null / whitespace-only) config means "unset" -> OIDC-standard bound.
    assertEquals(KnoxIDFConstants.OIDC_STANDARD_SCOPES, RegistrationResource.parseRegisterableScopes(null));
    assertEquals(KnoxIDFConstants.OIDC_STANDARD_SCOPES, RegistrationResource.parseRegisterableScopes("   "));
  }

  @Test
  public void testExplicitEmptyListYieldsOpenidOnly() {
    // A non-blank config that resolves to no real scopes is an explicit (most restrictive) whitelist,
    // not "unset": openid is always registerable, nothing else is.
    assertEquals(Set.of("openid"), RegistrationResource.parseRegisterableScopes(" , ,"));
  }

  @Test
  public void testOidcStandardSetDoesNotIncludeArbitraryScopes() {
    // The default bound rejects a self-assigned privileged scope name like 'admin'.
    assertFalse(KnoxIDFConstants.OIDC_STANDARD_SCOPES.contains("admin"));
    assertTrue(KnoxIDFConstants.OIDC_STANDARD_SCOPES.contains("openid"));
    assertTrue(KnoxIDFConstants.OIDC_STANDARD_SCOPES.contains("offline_access"));
  }

  @Test
  public void testExplicitConfigIsAuthoritativeAndTrimmed() {
    final Set<String> scopes = RegistrationResource.parseRegisterableScopes("openid, profile , reports.read");
    assertTrue(scopes.contains("openid"));
    assertTrue(scopes.contains("profile"));
    assertTrue(scopes.contains("reports.read"));
    // A standard scope the operator omitted is NOT registerable under an explicit (narrower) config.
    assertFalse(scopes.contains("email"));
    assertFalse(scopes.contains("admin"));
  }

  @Test
  public void testOpenidAlwaysRegisterableEvenIfOmittedFromConfig() {
    final Set<String> scopes = RegistrationResource.parseRegisterableScopes("profile,email");
    assertTrue("'openid' must always be registerable regardless of the configured list.",
        scopes.contains("openid"));
  }
}
