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
import static org.junit.Assert.fail;

import org.apache.knox.gateway.service.knoxidf.AuthorizeResource.ClientSecretResolutionException;
import org.junit.Test;

/**
 * Verifies the fail-closed handling of a configured federated-OP client-secret alias (review
 * finding M4). When an alias is declared but resolves to nothing, the token exchange must abort
 * with a clear error and never contact the OP -- previously an unresolvable alias yielded a null
 * secret that the form encoder serialized to a literal {@code client_secret=null} sent to the OP.
 */
public class AuthorizeResourceClientSecretResolutionTest {

  @Test
  public void testResolvedSecretIsReturned() {
    final String secret = AuthorizeResource.requireResolvedAliasSecret("op.secret.alias", "s3cr3t".toCharArray());
    assertEquals("A resolved alias must yield its secret value.", "s3cr3t", secret);
  }

  @Test
  public void testUnresolvableAliasFailsClosed() {
    try {
      AuthorizeResource.requireResolvedAliasSecret("op.secret.alias", null);
      fail("A configured-but-unresolvable alias must fail closed, not return null.");
    } catch (ClientSecretResolutionException e) {
      assertTrue("The error should name the offending alias.", e.getMessage().contains("op.secret.alias"));
    }
  }

  @Test
  public void testEmptyResolvedSecretFailsClosed() {
    try {
      AuthorizeResource.requireResolvedAliasSecret("op.secret.alias", new char[0]);
      fail("An alias resolving to an empty secret must fail closed.");
    } catch (ClientSecretResolutionException e) {
      // expected
    }
  }
}
