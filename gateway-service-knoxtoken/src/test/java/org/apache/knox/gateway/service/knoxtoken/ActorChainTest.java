/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.service.knoxtoken;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.knox.gateway.security.ActorChainPrincipal;
import org.apache.knox.gateway.security.ActorChainPrincipalImpl;
import org.apache.knox.gateway.services.security.token.JWTokenAttributesBuilder;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.junit.Test;

/**
 * Test class to verify RFC 8693 actor chain functionality end-to-end.
 */
public class ActorChainTest {

  @Test
  public void testActorChainPreservation() throws Exception {
    // Step 1: Create initial token with actor chain (simulating token from previous exchange)
    List<Map<String, Object>> initialChain = new ArrayList<>();

    Map<String, Object> actor1 = new LinkedHashMap<>();
    actor1.put("sub", "service-a");
    actor1.put("iss", "https://issuer.example.com");
    initialChain.add(actor1);

    Map<String, Object> actor2 = new LinkedHashMap<>();
    actor2.put("sub", "service-b");
    initialChain.add(actor2);

    JWTokenAttributesBuilder builder1 = new JWTokenAttributesBuilder();
    builder1.setUserName("end-user")
        .setAlgorithm("RS256")
        .setExpires(System.currentTimeMillis() + 30000)
        .setActorChain(initialChain);

    JWTToken token1 = new JWTToken(builder1.build());

    // Step 2: Extract actor chain from token (simulating JWT validation)
    List<Map<String, Object>> extractedChain = TokenUtils.extractActorChain(token1);
    assertNotNull("Extracted chain should not be null", extractedChain);
    assertEquals("Should have 2 actors", 2, extractedChain.size());
    assertEquals("First actor should be service-a", "service-a", extractedChain.get(0).get("sub"));
    assertEquals("Second actor should be service-b", "service-b", extractedChain.get(1).get("sub"));

    // Step 3: Create ActorChainPrincipal (simulating what AbstractJWTFilter does)
    ActorChainPrincipal actorChainPrincipal = new ActorChainPrincipalImpl(extractedChain);
    assertEquals("Current actor should be service-a", "service-a", actorChainPrincipal.getCurrentActor());
    assertEquals("Original delegator should be service-b", "service-b", actorChainPrincipal.getOriginalDelegator());

    // Step 4: Add new actor to chain (simulating what TokenResource does)
    String newActor = "service-c";
    List<Map<String, Object>> newChain = TokenUtils.addActorToChain(extractedChain, newActor);
    assertEquals("Should have 3 actors", 3, newChain.size());
    assertEquals("New actor should be first", newActor, newChain.get(0).get("sub"));
    assertEquals("Previous first actor should be second", "service-a", newChain.get(1).get("sub"));
    assertEquals("Previous second actor should be third", "service-b", newChain.get(2).get("sub"));

    // Step 5: Create new token with extended chain
    JWTokenAttributesBuilder builder2 = new JWTokenAttributesBuilder();
    builder2.setUserName("end-user")
        .setAlgorithm("RS256")
        .setExpires(System.currentTimeMillis() + 30000)
        .setActorChain(newChain);

    JWTToken token2 = new JWTToken(builder2.build());

    // Step 6: Verify the new token has the complete chain
    List<Map<String, Object>> finalChain = TokenUtils.extractActorChain(token2);
    assertNotNull("Final chain should not be null", finalChain);
    assertEquals("Should have 3 actors in final token", 3, finalChain.size());
    assertEquals("First actor should be service-c", "service-c", finalChain.get(0).get("sub"));
    assertEquals("Second actor should be service-a", "service-a", finalChain.get(1).get("sub"));
    assertEquals("Third actor should be service-b", "service-b", finalChain.get(2).get("sub"));

    // Verify issuer is preserved for actor1
    assertEquals("Issuer should be preserved", "https://issuer.example.com", finalChain.get(1).get("iss"));
  }

  @Test
  public void testActorChainInNestedStructure() throws ParseException {
    // Create a token with nested actor structure
    List<Map<String, Object>> chain = new ArrayList<>();

    Map<String, Object> actor1 = new LinkedHashMap<>();
    actor1.put("sub", "actor1");
    chain.add(actor1);

    Map<String, Object> actor2 = new LinkedHashMap<>();
    actor2.put("sub", "actor2");
    chain.add(actor2);

    Map<String, Object> actor3 = new LinkedHashMap<>();
    actor3.put("sub", "actor3");
    chain.add(actor3);

    JWTokenAttributesBuilder builder = new JWTokenAttributesBuilder();
    builder.setUserName("testuser")
        .setAlgorithm("RS256")
        .setExpires(System.currentTimeMillis() + 30000)
        .setActorChain(chain);

    JWT token = new JWTToken(builder.build());

    // Verify the act claim is present
    Object actClaim = token.getClaimAsObject(JWTToken.ACT_CLAIM);
    assertNotNull("Act claim should be present", actClaim);
    assertTrue("Act claim should be a Map", actClaim instanceof Map);

    // Verify nested structure
    Map<?, ?> level1 = (Map<?, ?>) actClaim;
    assertEquals("Level 1 sub should be actor1", "actor1", level1.get("sub"));

    Object level2Act = level1.get(JWTToken.ACT_CLAIM);
    assertNotNull("Level 2 act should be present", level2Act);
    assertTrue("Level 2 act should be a Map", level2Act instanceof Map);

    Map<?, ?> level2 = (Map<?, ?>) level2Act;
    assertEquals("Level 2 sub should be actor2", "actor2", level2.get("sub"));

    Object level3Act = level2.get(JWTToken.ACT_CLAIM);
    assertNotNull("Level 3 act should be present", level3Act);
    assertTrue("Level 3 act should be a Map", level3Act instanceof Map);

    Map<?, ?> level3 = (Map<?, ?>) level3Act;
    assertEquals("Level 3 sub should be actor3", "actor3", level3.get("sub"));
  }
}
