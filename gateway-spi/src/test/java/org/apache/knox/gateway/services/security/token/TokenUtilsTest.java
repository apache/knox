/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.services.security.token;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.junit.Test;

/**
 * Test class for TokenUtils, focusing on RFC 8693 actor chain functionality.
 */
public class TokenUtilsTest {

  @Test
  public void testExtractActorChain_NoActClaim() throws Exception {
    // Create a JWT without an 'act' claim
    JWTokenAttributesBuilder builder = new JWTokenAttributesBuilder();
    builder.setUserName("testuser")
        .setAlgorithm("RS256")
        .setExpires(System.currentTimeMillis() + 30000);

    JWTokenAttributes attributes = builder.build();
    JWTToken token = new JWTToken(attributes);

    List<Map<String, Object>> actorChain = TokenUtils.extractActorChain(token);
    assertNotNull(actorChain);
    assertTrue(actorChain.isEmpty());
  }

  @Test
  public void testExtractActorChain_SingleActor() throws Exception {
    // Create a JWT with a single actor in the chain
    List<Map<String, Object>> chain = new ArrayList<>();
    Map<String, Object> actor1 = new LinkedHashMap<>();
    actor1.put("sub", "actor1");
    chain.add(actor1);

    JWTokenAttributesBuilder builder = new JWTokenAttributesBuilder();
    builder.setUserName("testuser")
        .setAlgorithm("RS256")
        .setExpires(System.currentTimeMillis() + 30000)
        .setActorChain(chain);

    JWTokenAttributes attributes = builder.build();
    JWTToken token = new JWTToken(attributes);

    List<Map<String, Object>> actorChain = TokenUtils.extractActorChain(token);
    assertNotNull(actorChain);
    assertEquals(1, actorChain.size());
    assertEquals("actor1", actorChain.get(0).get("sub"));
  }

  @Test
  public void testExtractActorChain_MultipleActors() throws Exception {
    // Create an actor chain
    List<Map<String, Object>> originalChain = new ArrayList<>();
    Map<String, Object> actor1 = new LinkedHashMap<>();
    actor1.put("sub", "actor1");
    originalChain.add(actor1);

    Map<String, Object> actor2 = new LinkedHashMap<>();
    actor2.put("sub", "actor2");
    originalChain.add(actor2);

    Map<String, Object> actor3 = new LinkedHashMap<>();
    actor3.put("sub", "actor3");
    originalChain.add(actor3);

    // Create a JWT with the actor chain
    JWTokenAttributesBuilder builder = new JWTokenAttributesBuilder();
    builder.setUserName("testuser")
        .setAlgorithm("RS256")
        .setExpires(System.currentTimeMillis() + 30000)
        .setActorChain(originalChain);

    JWTokenAttributes attributes = builder.build();
    JWTToken token = new JWTToken(attributes);

    // Extract and verify the chain
    List<Map<String, Object>> extractedChain = TokenUtils.extractActorChain(token);
    assertNotNull(extractedChain);
    assertEquals(3, extractedChain.size());
    assertEquals("actor1", extractedChain.get(0).get("sub"));
    assertEquals("actor2", extractedChain.get(1).get("sub"));
    assertEquals("actor3", extractedChain.get(2).get("sub"));
  }

  @Test
  public void testAddActorToChain_EmptyChain() {
    List<Map<String, Object>> result = TokenUtils.addActorToChain(null, "newActor");
    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals("newActor", result.get(0).get("sub"));
  }

  @Test
  public void testAddActorToChain_ExistingChain() {
    List<Map<String, Object>> existingChain = new ArrayList<>();
    Map<String, Object> actor1 = new LinkedHashMap<>();
    actor1.put("sub", "actor1");
    existingChain.add(actor1);

    Map<String, Object> actor2 = new LinkedHashMap<>();
    actor2.put("sub", "actor2");
    existingChain.add(actor2);

    List<Map<String, Object>> result = TokenUtils.addActorToChain(existingChain, "newActor");
    assertNotNull(result);
    assertEquals(3, result.size());
    // New actor should be first (most recent)
    assertEquals("newActor", result.get(0).get("sub"));
    assertEquals("actor1", result.get(1).get("sub"));
    assertEquals("actor2", result.get(2).get("sub"));
  }

  @Test
  public void testAddActorToChain_NullActor() {
    List<Map<String, Object>> existingChain = new ArrayList<>();
    Map<String, Object> actor1 = new LinkedHashMap<>();
    actor1.put("sub", "actor1");
    existingChain.add(actor1);

    List<Map<String, Object>> result = TokenUtils.addActorToChain(existingChain, null);
    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals("actor1", result.get(0).get("sub"));
  }

  @Test
  public void testBuildNestedActClaim_EmptyChain() {
    Map<String, Object> result = TokenUtils.buildNestedActClaim(null);
    assertNull(result);

    result = TokenUtils.buildNestedActClaim(new ArrayList<>());
    assertNull(result);
  }

  @Test
  public void testBuildNestedActClaim_SingleActor() {
    List<Map<String, Object>> chain = new ArrayList<>();
    Map<String, Object> actor1 = new LinkedHashMap<>();
    actor1.put("sub", "actor1");
    chain.add(actor1);

    Map<String, Object> result = TokenUtils.buildNestedActClaim(chain);
    assertNotNull(result);
    assertEquals("actor1", result.get("sub"));
    assertNull(result.get("act")); // No nested act claim for single actor
  }

  @Test
  public void testBuildNestedActClaim_MultipleActors() {
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

    Map<String, Object> result = TokenUtils.buildNestedActClaim(chain);
    assertNotNull(result);

    // Verify the nested structure
    // Level 1: actor1
    assertEquals("actor1", result.get("sub"));
    Map<String, Object> level2 = (Map<String, Object>) result.get("act");
    assertNotNull(level2);

    // Level 2: actor2
    assertEquals("actor2", level2.get("sub"));
    Map<String, Object> level3 = (Map<String, Object>) level2.get("act");
    assertNotNull(level3);

    // Level 3: actor3 (deepest, no more nesting)
    assertEquals("actor3", level3.get("sub"));
    assertNull(level3.get("act"));
  }

  @Test
  public void testActorChainRoundTrip() throws Exception {
    // Create an actor chain
    List<Map<String, Object>> originalChain = new ArrayList<>();
    Map<String, Object> actor1 = new LinkedHashMap<>();
    actor1.put("sub", "actor1");
    actor1.put("iss", "issuer1");
    originalChain.add(actor1);

    Map<String, Object> actor2 = new LinkedHashMap<>();
    actor2.put("sub", "actor2");
    originalChain.add(actor2);

    Map<String, Object> actor3 = new LinkedHashMap<>();
    actor3.put("sub", "actor3");
    originalChain.add(actor3);

    // Create a JWT with the actor chain
    JWTokenAttributesBuilder builder = new JWTokenAttributesBuilder();
    builder.setUserName("testuser")
        .setAlgorithm("RS256")
        .setExpires(System.currentTimeMillis() + 30000)
        .setActorChain(originalChain);

    JWTokenAttributes attributes = builder.build();
    JWTToken token = new JWTToken(attributes);

    // Extract the chain and verify it matches the original
    List<Map<String, Object>> extractedChain = TokenUtils.extractActorChain(token);
    assertNotNull(extractedChain);
    assertEquals(3, extractedChain.size());
    assertEquals("actor1", extractedChain.get(0).get("sub"));
    assertEquals("issuer1", extractedChain.get(0).get("iss"));
    assertEquals("actor2", extractedChain.get(1).get("sub"));
    assertEquals("actor3", extractedChain.get(2).get("sub"));
  }
}
