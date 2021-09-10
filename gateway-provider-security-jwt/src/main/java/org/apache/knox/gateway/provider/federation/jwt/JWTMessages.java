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
package org.apache.knox.gateway.provider.federation.jwt;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;
import org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter;

@Messages(logger="org.apache.knox.gateway.provider.federation.jwt")
public interface JWTMessages {
  @Message( level = MessageLevel.WARN, text = "Failed to validate the audience attribute for token {0} ({1})" )
  void failedToValidateAudience(String tokenDisplayText, String tokenId);

  @Message( level = MessageLevel.WARN, text = "Failed to verify the token signature of {0} ({1})" )
  void failedToVerifyTokenSignature(String tokenDisplayText, String tokenId);

  @Message( level = MessageLevel.INFO, text = "Access token {0} ({1}) has expired; a new one must be acquired." )
  void tokenHasExpired(String tokenDisplayText, String tokenId);

  @Message( level = MessageLevel.INFO, text = "Access token {0} has expired; a new one must be acquired." )
  void tokenHasExpired(String tokenId);

  @Message(level = MessageLevel.ERROR, text = "Received wrong passcode token for {0}")
  void wrongPasscodeToken(String tokenId);

  @Message( level = MessageLevel.INFO, text = "The NotBefore check failed." )
  void notBeforeCheckFailed();

  @Message( level = MessageLevel.WARN, text = "Expected Bearer token is missing." )
  void missingBearerToken();

  @Message( level = MessageLevel.INFO, text = "Unable to verify token: {0}" )
  void unableToVerifyToken(@StackTrace( level = MessageLevel.ERROR) Exception e);

  @Message( level = MessageLevel.WARN, text = "Unable to verify token expiration: {0}" )
  void unableToVerifyExpiration(@StackTrace( level = MessageLevel.DEBUG) Exception e);

  @Message( level = MessageLevel.ERROR, text = "Unable to issue token: {0}" )
  void unableToIssueToken(@StackTrace( level = MessageLevel.DEBUG) Exception e);

  @Message( level = MessageLevel.DEBUG, text = "Sending redirect to: {0}" )
  void sendRedirectToLoginURL(String loginURL);

  @Message( level = MessageLevel.WARN, text = "Configuration for authentication provider URL is missing - will derive default URL." )
  void missingAuthenticationProviderUrlConfiguration();

  @Message( level = MessageLevel.DEBUG, text = "Audience claim has been validated." )
  void jwtAudienceValidated();

  @Message( level = MessageLevel.INFO, text = "Request {0} matches unauthenticated path list {1}, letting it through" )
  void unauthenticatedPathBypass(String uri, String unauthPathList);

  @Message( level = MessageLevel.ERROR, text = "Error while checking whether path {0} should be allowed unauthenticated access : {1}" )
  void unauthenticatedPathError(String path, String error);

  @Message( level = MessageLevel.WARN, text = "Unable to derive authentication provider URL: {0}" )
  void failedToDeriveAuthenticationProviderUrl(@StackTrace( level = MessageLevel.ERROR) Exception e);

  @Message( level = MessageLevel.ERROR,
            text = "The configuration value ({0}) for maximum token verification cache is invalid; Using the default value." )
  void invalidVerificationCacheMaxConfiguration(String value);

  @Message( level = MessageLevel.ERROR,
            text = "Missing token passcode." )
  void missingTokenPasscode();

  @Message( level = MessageLevel.INFO, text = "Initialized token signature verification cache for the {0} topology." )
  void initializedSignatureVerificationCache(String topology);

  @Message( level = MessageLevel.ERROR, text = "Failed to parse passcode token: {0}" )
  void failedToParsePasscodeToken(@StackTrace( level = MessageLevel.ERROR) Exception e);

  @Message( level = MessageLevel.ERROR, text = "Token is disabled: {0}" )
  void disabledToken(String tokenId);

  @Message( level = MessageLevel.INFO, text = "Missing token: {0}")
  void missingTokenFromHeader(Pair<JWTFederationFilter.TokenType, String> wireToken);
}
