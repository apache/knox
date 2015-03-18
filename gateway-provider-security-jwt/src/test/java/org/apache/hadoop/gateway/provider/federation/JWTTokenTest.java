/**
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
package org.apache.hadoop.gateway.provider.federation;

import junit.framework.TestCase;

import org.apache.hadoop.gateway.services.security.token.impl.JWTToken;
import org.junit.Test;

public class JWTTokenTest extends TestCase {
  private static final String JWT_TOKEN = "eyJhbGciOiJSUzI1NiJ9.eyJleHAiOjE0MjU2NTA1MzksInN1YiI6Imd1ZXN0IiwiYXVkIjoiSFNTTyIsImlzcyI6IkhTU08ifQ.lNLCcDeVpnsksbD_oE4YFKE_0HPLg0Qh0ToQHhzGSUCUNOm_dDRg3mtAHUz4qchwuAnGmZrnOAvY9a1832WMC1qJNjUHv1mxmox3yAneUIyVzZjazZsekdaQWgl7tha1OiE1iUhupXQtCFf7F3J10SsUImiE1F9XAMVIl5ut38c";
  private static final String HEADER = "{\"alg\":\"RS256\", \"type\":\"JWT\"}";
  private static final String CLAIMS = "{\"iss\": \"gateway\", \"prn\": \"john.doe@example.com\", \"aud\": \"https://login.example.com\", \"exp\": \"1363360913\"}";
  
  @Test
  public void testTokenParsing() throws Exception {
    JWTToken token = JWTToken.parseToken(JWT_TOKEN);
    
    //assertEquals(token.getHeader(), HEADER);
    //assertEquals(token.getClaims(), CLAIMS);
    
//    assertEquals(token.getIssuer(), "gateway");
//    assertEquals(token.getPrincipal(), "john.doe@example.com");
//    assertEquals(token.getAudience(), "https://login.example.com");
//    assertEquals(token.getExpires(), "1363360913");
  }
  
  @Test 
  public void testTokenCreation() throws Exception {
    String[] claims = new String[4];
    claims[0] = "3MVG99OxTyEMCQ3gNp2PjkqeZKxnmAiG1xV4oHh9AKL_rSK.BoSVPGZHQukXnVjzRgSuQqGn75NL7yfkQcyy7";
    claims[1] = "john.doe@example.com";
    claims[2] = "https://login.example.com";
    claims[3] = Long.toString( ( System.currentTimeMillis()/1000 ) + 300);
    JWTToken token = new JWTToken("RS256", claims);

    assertEquals(token.getIssuer(), "3MVG99OxTyEMCQ3gNp2PjkqeZKxnmAiG1xV4oHh9AKL_rSK.BoSVPGZHQukXnVjzRgSuQqGn75NL7yfkQcyy7");
    assertEquals(token.getSubject(), "john.doe@example.com");
    assertEquals(token.getAudience(), "https://login.example.com");
  }
}
