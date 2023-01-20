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
package org.apache.knox.gateway.services.security.token.impl;

import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Date;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.security.token.JWTokenAttributes;

public class JWTToken implements JWT {
  private static JWTProviderMessages log = MessagesFactory.get( JWTProviderMessages.class );

  public static final String KNOX_ID_CLAIM = "knox.id";
  public static final String MANAGED_TOKEN_CLAIM = "managed.token";
  public static final String KNOX_KID_CLAIM = "kid";
  public static final String KNOX_JKU_CLAIM = "jku";
  public static final String KNOX_GROUPS_CLAIM = "knox.groups";

  SignedJWT jwt;

  private JWTToken(String header, String claims, String signature) throws ParseException {
    jwt = new SignedJWT(new Base64URL(header), new Base64URL(claims), new Base64URL(signature));
  }

  public JWTToken(String serializedJWT) throws ParseException {
    try {
      jwt = SignedJWT.parse(serializedJWT);
    } catch (ParseException e) {
      log.unableToParseToken(e);
      throw e;
    }
  }

  public JWTToken(JWTokenAttributes jwtAttributes) {
    JWSHeader header = null;
    try {
      header = new JWSHeader(new JWSAlgorithm(jwtAttributes.getAlgorithm()),
              jwtAttributes.getType() == null ? null : new JOSEObjectType(jwtAttributes.getType()),
      null,
      null,
      jwtAttributes.getJkuUri(),
      null,
      null,
      null,
      null,
      null,
      jwtAttributes.getKid(),
      null,
      null);
    } catch (URISyntaxException e) {
      /* in event of bad URI exception fall back to using just algo in header */
      header = new JWSHeader(new JWSAlgorithm(jwtAttributes.getAlgorithm()));
    }
    JWTClaimsSet claims;
    JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
      .issuer(jwtAttributes.getIssuer())
      .subject(jwtAttributes.getUserName())
      .audience(jwtAttributes.getAudiences());
    if(jwtAttributes.getExpiresDate() != null) {
      builder = builder.expirationTime(jwtAttributes.getExpiresDate());
    }
    if(jwtAttributes.getKid() != null) {
      builder.claim(KNOX_KID_CLAIM, jwtAttributes.getKid());
    }
    if(jwtAttributes.getJku() != null) {
      builder.claim(KNOX_JKU_CLAIM, jwtAttributes.getJku());
    }
    if (jwtAttributes.getGroups() != null) {
      builder.claim(KNOX_GROUPS_CLAIM, jwtAttributes.getGroups());
    }

    // Add a private UUID claim for uniqueness
    builder.claim(KNOX_ID_CLAIM, String.valueOf(UUID.randomUUID()));

    builder.claim(MANAGED_TOKEN_CLAIM, String.valueOf(jwtAttributes.isManaged()));
    claims = builder.build();

    jwt = new SignedJWT(header, claims);
  }

  @Override
  public String getHeader() {
    JWSHeader header = jwt.getHeader();
    return header.toString();
  }

  @Override
  public JWSAlgorithm getSignatureAlgorithm() {
    return jwt.getHeader().getAlgorithm();
  }

  @Override
  public JOSEObjectType getType() {
    return jwt.getHeader().getType();
  }

  @Override
  public String getClaims() {
    String c = null;
    JWTClaimsSet claims;
    try {
      claims = jwt.getJWTClaimsSet();
      c = claims.toJSONObject().toJSONString();
    } catch (ParseException e) {
      log.unableToParseToken(e);
    }
    return c;
  }

  @Override
  public String getPayload() {
    Payload payload = jwt.getPayload();
    return payload.toString();
  }

  @Override
  public String toString() {
    return jwt.serialize();
  }

  @Override
  public void setSignaturePayload(byte[] payload) {
//    this.payload = payload;
  }

  @Override
  public byte[] getSignaturePayload() {
    byte[] b = null;
    Base64URL b64 = jwt.getSignature();
    if (b64 != null) {
      b = b64.decode();
    }
    return b;
  }

  public static JWTToken parseToken(String wireToken) throws ParseException {
    log.parsingToken(wireToken);
    String[] parts = wireToken.split("\\.");
    return new JWTToken(parts[0], parts[1], parts[2]);
  }

  @Override
  public String getClaim(String claimName) {
    String claim = null;

    try {
      claim = jwt.getJWTClaimsSet().getStringClaim(claimName);
    } catch (ParseException e) {
      log.unableToParseToken(e);
    }

    return claim;
  }

  @Override
  public String getSubject() {
    return getClaim(JWT.SUBJECT);
  }

  @Override
  public String getIssuer() {
    return getClaim(JWT.ISSUER);
  }

  @Override
  public String getAudience() {
    String[] claim;
    String c = null;

    claim = getAudienceClaims();
    if (claim != null) {
      c = claim[0];
    }

    return c;
  }

  @Override
  public String[] getAudienceClaims() {
    String[] claims = null;

    try {
      claims = jwt.getJWTClaimsSet().getStringArrayClaim(JWT.AUDIENCE);
    } catch (ParseException e) {
      log.unableToParseToken(e);
    }

    return claims;
  }

  @Override
  public String getExpires() {
      Date expires = getExpiresDate();
    if (expires != null) {
      return String.valueOf(expires.getTime());
    }
    return null;
  }

  @Override
  public Date getExpiresDate() {
    Date date = null;
    try {
      date = jwt.getJWTClaimsSet().getExpirationTime();
    } catch (ParseException e) {
      log.unableToParseToken(e);
    }
    return date;
  }

  @Override
  public Date getNotBeforeDate() {
    Date date = null;
    try {
      date = jwt.getJWTClaimsSet().getNotBeforeTime();
    } catch (ParseException e) {
      log.unableToParseToken(e);
    }
    return date;
  }

  @Override
  public String getPrincipal() {
    return getClaim(JWT.PRINCIPAL);
  }

  @Override
  public void sign(JWSSigner signer) {
    try {
      jwt.sign(signer);
    } catch (JOSEException e) {
      log.unableToSignToken(e);
    }
  }

  @Override
  public boolean verify(JWSVerifier verifier) {
    boolean rc = false;

    try {
      rc = jwt.verify(verifier);
    } catch (JOSEException e) {
      log.unableToVerifyToken(e);
    }

    return rc;
  }
}
