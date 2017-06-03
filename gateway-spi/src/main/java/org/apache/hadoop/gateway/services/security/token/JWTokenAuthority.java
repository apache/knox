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
package org.apache.hadoop.gateway.services.security.token;

import java.security.Principal;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

import javax.security.auth.Subject;

import org.apache.hadoop.gateway.services.security.token.impl.JWT;
import org.apache.hadoop.gateway.services.security.token.impl.JWTToken;

public interface JWTokenAuthority {

  JWTToken issueToken(Subject subject, String algorithm)
      throws TokenServiceException;

  JWTToken issueToken(Principal p, String algorithm)
      throws TokenServiceException;

  JWTToken issueToken(Principal p, String audience,
      String algorithm) throws TokenServiceException;

  boolean verifyToken(JWTToken token) throws TokenServiceException;

  boolean verifyToken(JWTToken token, RSAPublicKey publicKey)
      throws TokenServiceException;

  JWTToken issueToken(Principal p, String audience, String algorithm,
      long expires) throws TokenServiceException;

  JWT issueToken(Principal p, String audience, long l) throws TokenServiceException;

  JWTToken issueToken(Principal p, List<String> audience, String algorithm,
      long expires) throws TokenServiceException;
}