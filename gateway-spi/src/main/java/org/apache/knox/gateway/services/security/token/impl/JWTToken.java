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

import java.text.ParseException;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

public class JWTToken implements JWT {
  private static JWTProviderMessages log = MessagesFactory.get( JWTProviderMessages.class );

  SignedJWT jwt = null;

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

  public JWTToken(String alg, String[] claimsArray) {
    this(alg, claimsArray, null);
  }

  public JWTToken(String alg, String[] claimsArray, List<String> audiences) {
    JWSHeader header = new JWSHeader(new JWSAlgorithm(alg));

    if (claimsArray[2] != null) {
      if (audiences == null) {
        audiences = new ArrayList<>();
      }
      audiences.add(claimsArray[2]);
    }
    JWTClaimsSet claims = null;
    JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
    .issuer(claimsArray[0])
    .subject(claimsArray[1])
    .audience(audiences);
    if(claimsArray[3] != null) {
      builder = builder.expirationTime(new Date(Long.parseLong(claimsArray[3])));
    }

    claims = builder.build();

    jwt = new SignedJWT(header, claims);
  }

  /* (non-Javadoc)
   * @see org.apache.knox.gateway.services.security.token.impl.JWT#getPayloadToSign()
   */
  @Override
  public String getHeader() {
    JWSHeader header = jwt.getHeader();
    return header.toString();
  }

  /* (non-Javadoc)
   * @see org.apache.knox.gateway.services.security.token.impl.JWT#getPayloadToSign()
   */
  @Override
  public String getClaims() {
    String c = null;
    JWTClaimsSet claims = null;
    try {
      claims = (JWTClaimsSet) jwt.getJWTClaimsSet();
      c = claims.toJSONObject().toJSONString();
    } catch (ParseException e) {
      log.unableToParseToken(e);
    }
    return c;
  }

  /* (non-Javadoc)
   * @see org.apache.knox.gateway.services.security.token.impl.JWT#getPayloadToSign()
   */
  @Override
  public String getPayload() {
    Payload payload = jwt.getPayload();
    return payload.toString();
  }

  public String toString() {
    return jwt.serialize();
  }

  /* (non-Javadoc)
   * @see org.apache.knox.gateway.services.security.token.impl.JWT#setSignaturePayload(byte[])
   */
  @Override
  public void setSignaturePayload(byte[] payload) {
//    this.payload = payload;
  }

  /* (non-Javadoc)
   * @see org.apache.knox.gateway.services.security.token.impl.JWT#getSignaturePayload()
   */
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

  /* (non-Javadoc)
   * @see org.apache.knox.gateway.services.security.token.impl.JWT#getClaim(java.lang.String)
   */
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

  /* (non-Javadoc)
   * @see org.apache.knox.gateway.services.security.token.impl.JWT#getSubject()
   */
  @Override
  public String getSubject() {
    return getClaim(JWT.SUBJECT);
  }

  /* (non-Javadoc)
   * @see org.apache.knox.gateway.services.security.token.impl.JWT#getIssuer()
   */
  @Override
  public String getIssuer() {
    return getClaim(JWT.ISSUER);
  }

  /* (non-Javadoc)
   * @see org.apache.knox.gateway.services.security.token.impl.JWT#getAudience()
   */
  @Override
  public String getAudience() {
    String[] claim = null;
    String c = null;

    claim = getAudienceClaims();
    if (claim != null) {
      c = claim[0];
    }

    return c;
  }

  /* (non-Javadoc)
   * @see org.apache.knox.gateway.services.security.token.impl.JWT#getAudienceClaims()
   */
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

  /* (non-Javadoc)
   * @see org.apache.knox.gateway.services.security.token.impl.JWT#getExpires()
   */
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

  /* (non-Javadoc)
   * @see org.apache.knox.gateway.services.security.token.impl.JWT#getPrincipal()
   */
  @Override
  public String getPrincipal() {
    return getClaim(JWT.PRINCIPAL);
  }


  /* (non-Javadoc)
   * @see org.apache.knox.gateway.services.security.token.impl.JWT#sign(JWSSigner)
   */
  @Override
  public void sign(JWSSigner signer) {
    try {
      jwt.sign(signer);
    } catch (JOSEException e) {
      log.unableToSignToken(e);
    }
  }

  /* (non-Javadoc)
   * @see org.apache.knox.gateway.services.security.token.impl.JWT#verify(JWSVerifier)
   */
  public boolean verify(JWSVerifier verifier) {
    boolean rc = false;

    try {
      rc = jwt.verify(verifier);
    } catch (JOSEException e) {
      // TODO Auto-generated catch block
      log.unableToVerifyToken(e);
    }

    return rc;
  }
}
