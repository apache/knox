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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TrustedOidcIssuerTest {

  @Test
  public void testGetters() {
    Instant now = Instant.now();
    TrustedOidcIssuer issuer = new TrustedOidcIssuer(
        "https://issuer.example.com", true, "cluster-a", now, "admin@example.com");

    assertEquals("https://issuer.example.com", issuer.getIssuerUrl());
    assertTrue(issuer.isDynamicJwks());
    assertEquals("cluster-a", issuer.getClusterName());
    assertEquals(now, issuer.getRegisteredAt());
    assertEquals("admin@example.com", issuer.getRegisteredBy());
  }

  @Test
  public void testNullableOptionalFields() {
    TrustedOidcIssuer issuer = new TrustedOidcIssuer(
        "https://issuer.example.com", false, null, Instant.now(), null);

    assertNull("clusterName should be nullable", issuer.getClusterName());
    assertNull("registeredBy should be nullable", issuer.getRegisteredBy());
    assertFalse(issuer.isDynamicJwks());
  }

  @Test
  public void testAllFieldsAreFinal() {
    for (Field field : TrustedOidcIssuer.class.getDeclaredFields()) {
      assertTrue("Field '" + field.getName() + "' must be final for immutability",
          Modifier.isFinal(field.getModifiers()));
    }
  }

  @Test
  public void testNoSetterMethods() {
    for (Method method : TrustedOidcIssuer.class.getDeclaredMethods()) {
      assertFalse("Setter found in immutable POJO: " + method.getName(),
          method.getName().startsWith("set"));
    }
  }
}
