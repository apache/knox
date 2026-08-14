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
package org.apache.knox.gateway.services.knoxidf.trustedoidcissuer;

import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EmptyTrustedOidcIssuerServiceTest {

  private final EmptyTrustedOidcIssuerService service = new EmptyTrustedOidcIssuerService();

  @Test
  public void testIsTrustedReturnsFalse() {
    assertFalse(service.isTrusted("https://any.issuer.com"));
  }

  @Test
  public void testIsDynamicJwksReturnsFalse() {
    assertFalse(service.isDynamicJwks("https://any.issuer.com"));
  }

  @Test
  public void testResolveJwksUriReturnsEmpty() {
    assertFalse(service.resolveJwksUri("https://any.issuer.com").isPresent());
  }

  @Test
  public void testRefreshJwksUriIsNoOp() {
    service.refreshJwksUri("https://any.issuer.com"); // must not throw
  }

  @Test
  public void testListReturnsEmpty() {
    assertTrue(service.list().isEmpty());
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testRegisterThrows() {
    service.register(new TrustedOidcIssuer("https://issuer.com", false, null, Instant.now(), null));
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testDeregisterThrows() {
    service.deregister("https://issuer.com");
  }
}
