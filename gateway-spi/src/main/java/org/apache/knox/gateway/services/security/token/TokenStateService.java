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

import java.util.Collection;

import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;


/**
 * Service providing authentication token state management.
 */
public interface TokenStateService extends Service {

  String CONFIG_SERVER_MANAGED = "knox.token.exp.server-managed";

  /**
   * @return The default duration (in milliseconds) for which a token's life will be extended when it is renewed.
   */
  long getDefaultRenewInterval();

  /**
   * @return The default maximum lifetime duration (in milliseconds) of a token.
   */
  long getDefaultMaxLifetimeDuration();

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
   * @param tokenId    The token unique identifier.
   * @param issueTime  The time the token was issued.
   * @param expiration The token expiration time.
   */
  void addToken(String tokenId, long issueTime, long expiration);

  /**
   * Add state for the specified token.
   *
   * @param tokenId             The token unique identifier.
   * @param issueTime           The time the token was issued.
   * @param expiration          The token expiration time.
   * @param maxLifetimeDuration The maximum allowed lifetime for the token.
   */
  void addToken(String tokenId, long issueTime, long expiration, long maxLifetimeDuration);

  /**
   * @param tokenId
   *          The token unique identifier.
   * @return The time the token was issued.
   * @throws UnknownTokenException
   *           if token is not found.
   */
  long getTokenIssueTime(String tokenId) throws UnknownTokenException;

  /**
   * Checks if the token is expired.
   *
   * @param token The token.
   * @throws UnknownTokenException Exception if token is not found.
   *
   * @return true, if the token has expired; Otherwise, false.
   */
  boolean isExpired(JWTToken token) throws UnknownTokenException;

  /**
   * Disable any subsequent use of the specified token.
   *
   * @param token The token.
   * @throws UnknownTokenException Exception if token is not found.
   */
  void revokeToken(JWTToken token) throws UnknownTokenException;

  /**
   * Disable any subsequent use of the specified token.
   *
   * @param tokenId The token unique identifier.
   * @throws UnknownTokenException Exception if token is not found.
   */
  void revokeToken(String tokenId) throws UnknownTokenException;

  /**
   * Extend the lifetime of the specified token by the default amount of time.
   *
   * @param token The token.
   * @throws UnknownTokenException Exception if token is not found.
   *
   * @return The token's updated expiration time in milliseconds.
   */
  long renewToken(JWTToken token) throws UnknownTokenException;

  /**
   * Extend the lifetime of the specified token by the specified amount of time.
   *
   * @param token The token.
   * @param renewInterval The amount of time that should be added to the token's lifetime.
   * @throws UnknownTokenException Exception if token is not found.
   *
   * @return The token's updated expiration time in milliseconds.
   */
  long renewToken(JWTToken token, long renewInterval) throws UnknownTokenException;

  /**
   * Extend the lifetime of the specified token by the default amount of time.
   *
   * @param tokenId The token unique identifier.
   * @throws UnknownTokenException Exception if token is not found.
   *
   * @return The token's updated expiration time in milliseconds.
   */
  long renewToken(String tokenId) throws UnknownTokenException;

  /**
   * Extend the lifetime of the specified token by the specified amount of time.
   *
   * @param tokenId The token unique identifier.
   * @param renewInterval The amount of time that should be added to the token's lifetime.
   * @throws UnknownTokenException Exception if token is not found.
   *
   * @return The token's updated expiration time in milliseconds.
   */
  long renewToken(String tokenId, long renewInterval) throws UnknownTokenException;

  /**
   * Get the token expiration.
   *
   * @param token The token.
   * @throws UnknownTokenException Exception if token is not found.
   *
   * @return The token's expiration time in milliseconds.
   */
  long getTokenExpiration(JWT token) throws UnknownTokenException;

  /**
   * Get the token expiration.
   *
   * @param tokenId The token unique identifier.
   * @throws UnknownTokenException Exception if token is not found.
   *
   * @return The token's expiration time in milliseconds.
   */
  long getTokenExpiration(String tokenId) throws UnknownTokenException;

  /**
   * Get the expiration for the specified token, optionally validating the token prior to accessing its expiration.
   * In some cases, the token has already been validated, and skipping an additional unnecessary validation improves
   * performance.
   *
   * @param tokenId  The token unique identifier.
   * @param validate Flag indicating whether the token needs to be validated.
   * @throws UnknownTokenException Exception if token is not found.
   *
   * @return The token's expiration time in milliseconds.
   */
  long getTokenExpiration(String tokenId, boolean validate) throws UnknownTokenException;

  /**
   * Adds metadata to the token identified by the given ID
   *
   * @param tokenId
   *          The token's unique identifier.
   * @param metadata
   *          The metadata to be added
   */
  void addMetadata(String tokenId, TokenMetadata metadata);

  /**
   *
   * @param tokenId
   *          The token's unique identifier.
   * @return The associated token metadata
   */
  TokenMetadata getTokenMetadata(String tokenId) throws UnknownTokenException;

  /**
   * @return a collection of all the existing (not yet evicted) tokens
   */
  Collection<KnoxToken> getAllTokens();

  /**
   * @param userName The name of the user to get tokens for
   * @return a collection of tokens associated to the given user; it's an empty
   *         collection if there is no associated token found in the underlying
   *         token management backend
   */
  Collection<KnoxToken> getTokens(String userName);

  /**
   * @param createdBy the user name that identified the CREATED_BY metadata (the
   *                  person who created the token for the token's user as 'doAs'
   * @return a collection of tokens associated to the given 'created by' user;
   *         it's an empty collection if there is no associated token found in the
   *         underlying token management backend
   */
  Collection<KnoxToken> getDoAsTokens(String createdBy);

}
