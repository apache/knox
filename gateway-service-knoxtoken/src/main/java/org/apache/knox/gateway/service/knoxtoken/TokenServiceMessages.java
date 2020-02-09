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

@Messages(logger="org.apache.knox.gateway.service.knoxtoken")
public interface TokenServiceMessages {

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

  @Message( level = MessageLevel.WARN,
            text = "There are no token renewers white-listed in the \"{0}\" topology.")
  void noRenewersConfigured(String topologyName);

  @Message( level = MessageLevel.ERROR, text = "Knox Token service ({0}) rejected a bad token renewal request: {1}")
  void badRenewalRequest(String topologyName, String error);

  @Message( level = MessageLevel.ERROR, text = "Knox Token service ({0}) rejected a bad token revocation request: {1}")
  void badRevocationRequest(String topologyName, String error);


}
