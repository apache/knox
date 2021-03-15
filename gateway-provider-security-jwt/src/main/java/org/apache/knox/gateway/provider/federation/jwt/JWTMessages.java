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

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger="org.apache.knox.gateway.provider.federation.jwt")
public interface JWTMessages {
  @Message( level = MessageLevel.WARN, text = "Failed to validate the audience attribute for token {0} ({1})" )
  void failedToValidateAudience(String tokenDisplayText, String tokenId);

  @Message( level = MessageLevel.WARN, text = "Failed to verify the token signature of {0} ({1})" )
  void failedToVerifyTokenSignature(String tokenDisplayText, String tokenId);

  @Message( level = MessageLevel.INFO, text = "Access token {0} ({1}) has expired; a new one must be acquired." )
  void tokenHasExpired(String tokenDisplayText, String tokenId);

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

  @Message( level = MessageLevel.INFO, text = "Path {0} is configured as unauthenticated path, letting the request {1} through" )
  void unauthenticatedPathBypass(String path, String uri);

  @Message( level = MessageLevel.WARN, text = "Unable to derive authentication provider URL: {0}" )
  void failedToDeriveAuthenticationProviderUrl(@StackTrace( level = MessageLevel.ERROR) Exception e);

  @Message( level = MessageLevel.ERROR,
            text = "The configuration value ({0}) for maximum token verification cache is invalid; Using the default value." )
  void invalidVerificationCacheMaxConfiguration(String value);

}
