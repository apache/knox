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

import org.junit.Test;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the dynamic-registration redirect-URI policy: HTTPS is required (RFC 8252), plain HTTP is
 * tolerated only for loopback dev, and wildcard hosts are rejected.
 */
public class RegistrationRedirectUriPolicyTest {

  private static Response verify(String... uris) {
    return RegistrationResource.verifyRedirectUris(Arrays.asList(uris));
  }

  @Test
  public void testHttpsAccepted() {
    assertNull("A plain https redirect must be accepted.", verify("https://app.example.com/cb"));
  }

  @Test
  public void testPlainHttpRejectedForNonLoopback() {
    final Response response = verify("http://app.example.com/cb");
    assertEquals(400, response.getStatus());
    assertTrue(String.valueOf(response.getEntity()).contains("HTTPS"));
  }

  @Test
  public void testPlainHttpAllowedForLoopback() {
    assertNull(verify("http://localhost:8080/cb"));
    assertNull(verify("http://127.0.0.1/cb"));
    assertNull(verify("http://[::1]:9000/cb"));
  }

  @Test
  public void testWildcardHostRejected() {
    final Response response = verify("https://*.example.com/cb");
    assertEquals(400, response.getStatus());
  }

  @Test
  public void testEmptyListRejected() {
    assertEquals(400, verify().getStatus());
    assertEquals(400, RegistrationResource.verifyRedirectUris(Collections.<String>emptyList()).getStatus());
    assertEquals(400, RegistrationResource.verifyRedirectUris((List<String>) null).getStatus());
  }

  @Test
  public void testOneBadUriAmongGoodOnesRejectsWhole() {
    assertEquals(400, verify("https://good.example.com/cb", "http://evil.example.com/cb").getStatus());
  }

  @Test
  public void testConfiguredLoopbackHostAllowsPlainHttp() {
    final Set<String> hosts = RegistrationResource.parseLoopbackHosts("host.docker.internal");
    assertNull("A configured loopback host must be allowed over plain HTTP.",
        RegistrationResource.verifyRedirectUris(Collections.singletonList("http://host.docker.internal:8443/cb"), hosts));
  }

  @Test
  public void testConfiguredLoopbackMatchIsCaseInsensitive() {
    final Set<String> hosts = RegistrationResource.parseLoopbackHosts("Host.Docker.Internal");
    assertNull(RegistrationResource.verifyRedirectUris(Collections.singletonList("http://HOST.docker.internal/cb"), hosts));
  }

  @Test
  public void testHostNotConfiguredStillRejected() {
    final Set<String> hosts = RegistrationResource.parseLoopbackHosts("host.docker.internal");
    assertEquals("A host outside the allowlist must still require HTTPS.", 400,
        RegistrationResource.verifyRedirectUris(Collections.singletonList("http://evil.docker.internal/cb"), hosts).getStatus());
  }

  @Test
  public void testConfiguredLoopbackDoesNotWidenToSubdomains() {
    final Set<String> hosts = RegistrationResource.parseLoopbackHosts("host.docker.internal");
    // Exact match only: a sub-domain of an allowlisted host is NOT itself allowlisted.
    assertEquals(400, RegistrationResource.verifyRedirectUris(Collections.singletonList("http://evil.host.docker.internal/cb"), hosts).getStatus());
  }

  @Test
  public void testDefaultsAlwaysPresentAlongsideConfiguredHosts() {
    final Set<String> hosts = RegistrationResource.parseLoopbackHosts("host.docker.internal");
    // The three hard-coded loopback hosts survive even when extras are configured.
    assertNull(RegistrationResource.verifyRedirectUris(Collections.singletonList("http://localhost:8080/cb"), hosts));
    assertNull(RegistrationResource.verifyRedirectUris(Collections.singletonList("http://127.0.0.1/cb"), hosts));
  }

  @Test
  public void testBlankConfigYieldsDefaultsOnly() {
    assertEquals(RegistrationResource.DEFAULT_LOOPBACK_HOSTS, RegistrationResource.parseLoopbackHosts(null));
    assertEquals(RegistrationResource.DEFAULT_LOOPBACK_HOSTS, RegistrationResource.parseLoopbackHosts("   "));
    assertEquals(RegistrationResource.DEFAULT_LOOPBACK_HOSTS, RegistrationResource.parseLoopbackHosts(" , ,"));
  }
}
