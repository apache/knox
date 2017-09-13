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
package org.apache.knox.gateway.services.security.token.impl;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;

import org.junit.Test;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;

public class JWTTokenTest extends org.junit.Assert {
  private static final String JWT_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpYXQiOjE0MTY5MjkxMDksImp0aSI6ImFhN2Y4ZDBhOTVjIiwic2NvcGVzIjpbInJlcG8iLCJwdWJsaWNfcmVwbyJdfQ.XCEwpBGvOLma4TCoh36FU7XhUbcskygS81HE1uHLf0E";
  private static final String HEADER = "{\"alg\":\"RS256\", \"type\":\"JWT\"}";
  private static final String CLAIMS = "{\"iss\": \"gateway\", \"prn\": \"john.doe@example.com\", \"aud\": \"https://login.example.com\", \"exp\": \"1363360913\"}";

  private RSAPublicKey publicKey;
  private RSAPrivateKey privateKey;

  public JWTTokenTest() throws Exception, NoSuchAlgorithmException {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);

    KeyPair kp = kpg.genKeyPair();
    publicKey = (RSAPublicKey) kp.getPublic();
    privateKey = (RSAPrivateKey) kp.getPrivate();
  }

  public void testTokenParsing() throws Exception {
    JWTToken token = JWTToken.parseToken(JWT_TOKEN);
    assertEquals(token.getHeader(), HEADER);
    assertEquals(token.getClaims(), CLAIMS);

    assertEquals(token.getIssuer(), "gateway");
    assertEquals(token.getPrincipal(), "john.doe@example.com");
    assertEquals(token.getAudience(), "https://login.example.com");
    assertEquals(token.getExpires(), "1363360913");
  }

  @Test
  public void testTokenCreation() throws Exception {
    String[] claims = new String[4];
    claims[0] = "KNOXSSO";
    claims[1] = "john.doe@example.com";
    claims[2] = "https://login.example.com";
    claims[3] = Long.toString( ( System.currentTimeMillis()/1000 ) + 300);
    JWTToken token = new JWTToken("RS256", claims);

    assertEquals("KNOXSSO", token.getIssuer());
    assertEquals("john.doe@example.com", token.getSubject());
    assertEquals("https://login.example.com", token.getAudience());
  }

  @Test
  public void testTokenCreationWithAudienceListSingle() throws Exception {
    String[] claims = new String[4];
    claims[0] = "KNOXSSO";
    claims[1] = "john.doe@example.com";
    claims[2] = null;
    claims[3] = Long.toString( ( System.currentTimeMillis()/1000 ) + 300);
    ArrayList<String> audiences = new ArrayList<String>();
    audiences.add("https://login.example.com");

    JWTToken token = new JWTToken("RS256", claims, audiences);

    assertEquals("KNOXSSO", token.getIssuer());
    assertEquals("john.doe@example.com", token.getSubject());
    assertEquals("https://login.example.com", token.getAudience());
    assertEquals(1, token.getAudienceClaims().length);
  }

  @Test
  public void testTokenCreationWithAudienceListMultiple() throws Exception {
    String[] claims = new String[4];
    claims[0] = "KNOXSSO";
    claims[1] = "john.doe@example.com";
    claims[2] = null;
    claims[3] = Long.toString( ( System.currentTimeMillis()/1000 ) + 300);
    ArrayList<String> audiences = new ArrayList<String>();
    audiences.add("https://login.example.com");
    audiences.add("KNOXSSO");

    JWTToken token = new JWTToken("RS256", claims, audiences);

    assertEquals("KNOXSSO", token.getIssuer());
    assertEquals("john.doe@example.com", token.getSubject());
    assertEquals("https://login.example.com", token.getAudience());
    assertEquals(2, token.getAudienceClaims().length);
  }

  @Test
  public void testTokenCreationWithAudienceListCombined() throws Exception {
    String[] claims = new String[4];
    claims[0] = "KNOXSSO";
    claims[1] = "john.doe@example.com";
    claims[2] = "LJM";
    claims[3] = Long.toString( ( System.currentTimeMillis()/1000 ) + 300);
    ArrayList<String> audiences = new ArrayList<String>();
    audiences.add("https://login.example.com");
    audiences.add("KNOXSSO");

    JWTToken token = new JWTToken("RS256", claims, audiences);

    assertEquals("KNOXSSO", token.getIssuer());
    assertEquals("john.doe@example.com", token.getSubject());
    assertEquals("https://login.example.com", token.getAudience());
    assertEquals(3, token.getAudienceClaims().length);
  }

  @Test
  public void testTokenCreationWithNullAudienceList() throws Exception {
    String[] claims = new String[4];
    claims[0] = "KNOXSSO";
    claims[1] = "john.doe@example.com";
    claims[2] = null;
    claims[3] = Long.toString( ( System.currentTimeMillis()/1000 ) + 300);
    ArrayList<String> audiences = null;

    JWTToken token = new JWTToken("RS256", claims, audiences);

    assertEquals("KNOXSSO", token.getIssuer());
    assertEquals("john.doe@example.com", token.getSubject());
    assertEquals(null, token.getAudience());
    assertArrayEquals(null, token.getAudienceClaims());
  }

  @Test
  public void testTokenCreationRS512() throws Exception {
    String[] claims = new String[4];
    claims[0] = "KNOXSSO";
    claims[1] = "john.doe@example.com";
    claims[2] = "https://login.example.com";
    claims[3] = Long.toString( ( System.currentTimeMillis()/1000 ) + 300);
    JWTToken token = new JWTToken(JWSAlgorithm.RS512.getName(), claims);

    assertEquals("KNOXSSO", token.getIssuer());
    assertEquals("john.doe@example.com", token.getSubject());
    assertEquals("https://login.example.com", token.getAudience());
    assertTrue(token.getHeader().contains(JWSAlgorithm.RS512.getName()));
  }

  @Test
  public void testTokenSignature() throws Exception {
    String[] claims = new String[4];
    claims[0] = "KNOXSSO";
    claims[1] = "john.doe@example.com";
    claims[2] = "https://login.example.com";
    claims[3] = Long.toString( ( System.currentTimeMillis()/1000 ) + 300);
    JWTToken token = new JWTToken("RS256", claims);


    assertEquals("KNOXSSO", token.getIssuer());
    assertEquals("john.doe@example.com", token.getSubject());
    assertEquals("https://login.example.com", token.getAudience());

    // Sign the token
    JWSSigner signer = new RSASSASigner(privateKey);
    token.sign(signer);
    assertTrue(token.getSignaturePayload().length > 0);

    // Verify the signature
    JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) publicKey);
    assertTrue(token.verify(verifier));
  }

  @Test
  public void testTokenSignatureRS512() throws Exception {
    String[] claims = new String[4];
    claims[0] = "KNOXSSO";
    claims[1] = "john.doe@example.com";
    claims[2] = "https://login.example.com";
    claims[3] = Long.toString( ( System.currentTimeMillis()/1000 ) + 300);
    JWTToken token = new JWTToken(JWSAlgorithm.RS512.getName(), claims);

    assertEquals("KNOXSSO", token.getIssuer());
    assertEquals("john.doe@example.com", token.getSubject());
    assertEquals("https://login.example.com", token.getAudience());
    assertTrue(token.getHeader().contains(JWSAlgorithm.RS512.getName()));

    // Sign the token
    JWSSigner signer = new RSASSASigner(privateKey);
    token.sign(signer);
    assertTrue(token.getSignaturePayload().length > 0);

    // Verify the signature
    JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) publicKey);
    assertTrue(token.verify(verifier));
  }

}
