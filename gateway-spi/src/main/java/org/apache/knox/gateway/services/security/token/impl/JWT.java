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
package org.apache.knox.gateway.services.security.token.impl;

import java.util.Date;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;

public interface JWT {

  String PRINCIPAL = "prn";
  String SUBJECT = "sub";
  String ISSUER = "iss";
  String AUDIENCE = "aud";
  String EXPIRES = "exp";
  String NOT_BEFORE = "nbf";

  String getPayload();

  void setSignaturePayload(byte[] payload);

  byte[] getSignaturePayload();

  String getClaim(String claimName);

  String getPrincipal();

  String getIssuer();

  String getAudience();

  String[] getAudienceClaims();

  String getExpires();

  Date getExpiresDate();

  Date getNotBeforeDate();

  String getSubject();

  String getHeader();

  String getClaims();

  JWSAlgorithm getSignatureAlgorithm();

  void sign(JWSSigner signer);

  boolean verify(JWSVerifier verifier);

}