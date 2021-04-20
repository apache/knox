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
package org.apache.knox.gateway.services.token.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.UUID;

import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.knox.gateway.services.security.token.impl.TokenMAC;
import org.junit.Test;

public class TokenMACTest {

  @Test
  public void testHash() throws Exception {
    final TokenMAC tokenMAC = new TokenMAC(HmacAlgorithms.HMAC_SHA_256.getName(), "sPj8FCgQhCEi6G18kBfpswxYSki33plbelGLs0hMSbk".toCharArray());
    final String toBeHashed = "sampleTokenContent";
    final String tokenId = UUID.randomUUID().toString();
    final long issueTime = 123L;
    final String userName = "smolnar";
    final String hashed = tokenMAC.hash(tokenId, issueTime, userName, toBeHashed);
    assertNotEquals(toBeHashed, hashed);
    assertEquals(hashed, tokenMAC.hash(tokenId, issueTime, userName, toBeHashed));
  }

}
