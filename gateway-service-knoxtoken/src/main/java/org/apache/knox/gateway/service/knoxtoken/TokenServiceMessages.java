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
package org.apache.knox.gateway.service.knoxtoken;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

import java.text.ParseException;

@Messages(logger="org.apache.knox.gateway.service.knoxtoken")
public interface TokenServiceMessages {

  @Message( level = MessageLevel.INFO, text = "Knox Token service ({0}) issued token {1} ({2})")
  void issuedToken(String topologyName, String tokenDisplayText, String tokenId);

  @Message( level = MessageLevel.INFO, text = "Knox Token service ({0}) renewed the expiration for token {1} ({2}) (renewer={3})")
  void renewedToken(String topologyName, String tokenDisplayText, String tokenId, String renewer);

  @Message( level = MessageLevel.INFO, text = "Knox Token service ({0}) revoked token {1} ({2}) (renewer={3})")
  void revokedToken(String topologyName, String tokenDisplayText, String tokenId, String renewer);

  @Message( level = MessageLevel.INFO, text = "Knox Token service ({0}) set enabled flag to {1} on token {2}")
  void setEnabledFlag(String topologyName, boolean enabled, String tokenId);

  @Message( level = MessageLevel.ERROR, text = "Unable to issue token.")
  void unableToIssueToken(@StackTrace( level = MessageLevel.DEBUG) Exception e);

  @Message( level = MessageLevel.WARN, text = "The SSO token time to live - ttl is invalid: {0} - using default.")
  void invalidTokenTTLEncountered(String ttl);

  @Message( level = MessageLevel.WARN,
            text = "Unable to acquire cert for endpoint clients - assume trust will be provisioned separately: {0}.")
  void unableToAcquireCertForEndpointClients(@StackTrace( level = MessageLevel.DEBUG ) Exception e);

  @Message( level = MessageLevel.ERROR,
            text = "The specified value for the {1} configuration property is not valid for the \"{0}\" topology: {2}")
  void invalidConfigValue(String topologyName,
                          String name,
                          String value,
                          @StackTrace( level = MessageLevel.DEBUG ) Exception e);

  @Message( level = MessageLevel.INFO, text = "Server management of token state is enabled for the \"{0}\" topology.")
  void serverManagedTokenStateEnabled(String topologyName);

  @Message( level = MessageLevel.ERROR, text = "Knox Token service ({0}) could not parse token {1}: {2}")
  void invalidToken(String topologyName,
                    String tokenDisplayText,
                    @StackTrace( level = MessageLevel.DEBUG ) ParseException e);

  @Message( level = MessageLevel.WARN,
            text = "There are no token renewers white-listed in the \"{0}\" topology.")
  void noRenewersConfigured(String topologyName);

  @Message( level = MessageLevel.ERROR, text = "Knox Token service ({0}) rejected a bad renewal request for token {1}: {2}")
  void badRenewalRequest(String topologyName, String tokenDisplayText, String error);

  @Message( level = MessageLevel.ERROR, text = "Knox Token service ({0}) rejected a bad revocation request for token {1}: {2}")
  void badRevocationRequest(String topologyName, String tokenDisplayText, String error);

  @Message( level = MessageLevel.ERROR, text = "Knox Token service ({0}) rejected a bad set enabled flag request for token {1}: {2}")
  void badSetEnabledFlagRequest(String topologyName, String tokenId, String error);

  @Message( level = MessageLevel.DEBUG, text = "Knox Token service ({0}) stored state for token {1} ({2})")
  void storedToken(String topologyName, String tokenDisplayText, String tokenId);

  @Message( level = MessageLevel.WARN,
          text = "Renewal is disabled for the Knox Token service ({0}). Responding with the expiration from the token {1} ({2})")
  void renewalDisabled(String topologyName, String tokenDisplayText, String tokenId);

  @Message( level = MessageLevel.WARN, text = "Invalid duration used for JWT token lifespan ({0}) using the configured TTL for KnoxToken service")
  void invalidLifetimeValue(String lifetimeStr);

  @Message( level = MessageLevel.ERROR, text = "Unable to get token for user {0}: token limit exceeded")
  void tokenLimitExceeded(String userName);

  @Message( level = MessageLevel.INFO, text = "{0}")
  void generalInfoMessage(String message);

  @Message( level = MessageLevel.DEBUG, text = "Token impersonation successful: {0}/{1}" )
  void tokenImpersonationSuccess(String userName, String doAs);

  @Message( level = MessageLevel.DEBUG, text = "Token impersonation failed: {0}" )
  void tokenImpersonationFailed(@StackTrace Throwable t);

}
