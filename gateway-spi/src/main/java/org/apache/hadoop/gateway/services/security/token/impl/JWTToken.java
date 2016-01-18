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
package org.apache.hadoop.gateway.services.security.token.impl;

import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.Date;
import org.apache.commons.codec.binary.Base64;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;

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
  
  private JWTToken(byte[] header, byte[] claims, byte[] signature) throws ParseException {
    try {
      jwt = new SignedJWT(new Base64URL(new String(header, "UTF8")), new Base64URL(new String(claims, "UTF8")), 
          new Base64URL(new String(signature, "UTF8")));
    } catch (UnsupportedEncodingException e) {
      log.unsupportedEncoding(e);
    }
  }

  public JWTToken(String serializedJWT) {
    try {
      jwt = SignedJWT.parse(serializedJWT);
    } catch (ParseException e) {
      e.printStackTrace();
    }
  }

  public JWTToken(String alg, String[] claimsArray) {
    JWSHeader header = new JWSHeader(new JWSAlgorithm(alg));

    JWTClaimsSet claims = new JWTClaimsSet.Builder()
    .issuer(claimsArray[0])
    .subject(claimsArray[1])
    .audience(claimsArray[2])
    .expirationTime(new Date(Long.parseLong(claimsArray[3])))
    .build();

    jwt = new SignedJWT(header, claims);
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.services.security.token.impl.JWT#getPayloadToSign()
   */
  @Override
  public String getHeader() {
    JWSHeader header = jwt.getHeader();
    return header.toString();
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.services.security.token.impl.JWT#getPayloadToSign()
   */
  @Override
  public String getClaims() {
    String c = null;
    JWTClaimsSet claims = null;
    try {
      claims = (JWTClaimsSet) jwt.getJWTClaimsSet();
      c = claims.toJSONObject().toJSONString();
    } catch (ParseException e) {
      e.printStackTrace();
    }
    return c;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.services.security.token.impl.JWT#getPayloadToSign()
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
   * @see org.apache.hadoop.gateway.services.security.token.impl.JWT#setSignaturePayload(byte[])
   */
  @Override
  public void setSignaturePayload(byte[] payload) {
//    this.payload = payload;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.services.security.token.impl.JWT#getSignaturePayload()
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
    JWTToken jwt = new JWTToken(Base64.decodeBase64(parts[0]), Base64.decodeBase64(parts[1]), Base64.decodeBase64(parts[2]));
//    System.out.println("header: " + token.header);
//    System.out.println("claims: " + token.claims);
//    System.out.println("payload: " + new String(token.payload));
    
    return jwt;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.services.security.token.impl.JWT#getClaim(java.lang.String)
   */
  @Override
  public String getClaim(String claimName) {
    String claim = null;
    
    try {
      claim = jwt.getJWTClaimsSet().getStringClaim(claimName);
    } catch (ParseException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    return claim;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.services.security.token.impl.JWT#getSubject()
   */
  @Override
  public String getSubject() {
    return getClaim(JWT.SUBJECT);
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.services.security.token.impl.JWT#getIssuer()
   */
  @Override
  public String getIssuer() {
    return getClaim(JWT.ISSUER);
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.services.security.token.impl.JWT#getAudience()
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
   * @see org.apache.hadoop.gateway.services.security.token.impl.JWT#getAudienceClaims()
   */
  @Override
  public String[] getAudienceClaims() {
    String[] claims = null;

    try {
      claims = jwt.getJWTClaimsSet().getStringArrayClaim(JWT.AUDIENCE);
    } catch (ParseException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    return claims;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.services.security.token.impl.JWT#getExpires()
   */
  @Override
  public String getExpires() {
    return getClaim(JWT.EXPIRES);
  }

  @Override
  public Date getExpiresDate() {
    Date date = null;
    try {
      date = jwt.getJWTClaimsSet().getExpirationTime();
    } catch (ParseException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return date;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.services.security.token.impl.JWT#getPrincipal()
   */
  @Override
  public String getPrincipal() {
    return getClaim(JWT.PRINCIPAL);
  }

  
  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.services.security.token.impl.JWT#getPrincipal()
   */
  @Override
  public void sign(JWSSigner signer) {
    try {
      jwt.sign(signer);
    } catch (JOSEException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  /**
   * @param verifier
   * @return
   */
  public boolean verify(JWSVerifier verifier) {
    boolean rc = false;
    
    try {
      rc = jwt.verify(verifier);
    } catch (JOSEException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    return rc;
  }  
}
