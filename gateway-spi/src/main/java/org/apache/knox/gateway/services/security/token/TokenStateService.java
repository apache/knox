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

import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;


/**
 * Service providing authentication token state management.
 */
public interface TokenStateService extends Service {

  String CONFIG_SERVER_MANAGED = "knox.token.exp.server-managed";

  /**
   * Add state for the specified token.
   *
   * @param token     The token.
   * @param issueTime The time the token was issued.
   */
  void addToken(JWTToken token, long issueTime);

  /**
   * Add state for the specified token.
   *
   * @param token      The token.
   * @param issueTime  The time the token was issued.
   * @param expiration The token expiration time.
   */
  void addToken(String token, long issueTime, long expiration);

  /**
   *
   * @param token The token.
   *
   * @return true, if the token has expired; Otherwise, false.
   */
  boolean isExpired(JWTToken token);

  /**
   *
   * @param token The token.
   *
   * @return true, if the token has expired; Otherwise, false.
   */
  boolean isExpired(String token);

  /**
   * Disable any subsequent use of the specified token.
   *
   * @param token The token.
   */
  void revokeToken(JWTToken token);

  /**
   * Disable any subsequent use of the specified token.
   *
   * @param token The token.
   */
  void revokeToken(String token);

  /**
   * Extend the lifetime of the specified token by the default amount of time.
   *
   * @param token The token.
   *
   * @return The token's updated expiration time in milliseconds.
   */
  long renewToken(JWTToken token);

  /**
   * Extend the lifetime of the specified token by the specified amount of time.
   *
   * @param token The token.
   * @param renewInterval The amount of time that should be added to the token's lifetime.
   *
   * @return The token's updated expiration time in milliseconds.
   */
  long renewToken(JWTToken token, Long renewInterval);

  /**
   * Extend the lifetime of the specified token by the default amount of time.
   *
   * @param token The token.
   *
   * @return The token's updated expiration time in milliseconds.
   */
  long renewToken(String token);

  /**
   * Extend the lifetime of the specified token by the specified amount of time.
   *
   * @param token The token.
   * @param renewInterval The amount of time that should be added to the token's lifetime.
   *
   * @return The token's updated expiration time in milliseconds.
   */
  long renewToken(String token, Long renewInterval);

  /**
   *
   * @param token The token.
   *
   * @return The token's expiration time in milliseconds.
   */
  long getTokenExpiration(String token);

}
