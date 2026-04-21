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

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TokenMetadataTest {

  @Test
  public void testIsThirdPartyAppDefault() {
    assertTrue("Should default to true for backward compatibility", new TokenMetadata().isThirdPartyApp());
  }

  @Test
  public void testIsThirdPartyAppExplicit() {
    final TokenMetadata metadata = new TokenMetadata();
    metadata.add(TokenMetadata.THIRD_PARTY_APP, "true");
    assertTrue(metadata.isThirdPartyApp());
    metadata.add(TokenMetadata.THIRD_PARTY_APP, "false");
    assertFalse(metadata.isThirdPartyApp());
  }

  @Test
  public void testIsThirdPartyAppInvalidValue() {
    final TokenMetadata metadata = new TokenMetadata();
    metadata.add(TokenMetadata.THIRD_PARTY_APP, "not-a-boolean");
    assertFalse("Boolean.parseBoolean should return false for invalid strings", metadata.isThirdPartyApp());
  }
}
