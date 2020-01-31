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
package org.apache.knox.gateway.services.token.impl;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger = "org.apache.knox.gateway.services.token.state")
public interface TokenStateServiceMessages {

  @Message(level = MessageLevel.DEBUG, text = "Added token {0}")
  void addedToken(String tokenDisplayText);

  @Message(level = MessageLevel.DEBUG, text = "Renewed token {0}")
  void renewedToken(String tokenDisplayText);

  @Message(level = MessageLevel.DEBUG, text = "Revoked token {0}")
  void revokedToken(String tokenDisplayText);

  @Message(level = MessageLevel.DEBUG, text = "Unknown token {0}")
  void unknownToken(String tokenDisplayText);

  @Message(level = MessageLevel.ERROR, text = "The renewal limit for the token ({0}) has been exceeded.")
  void renewalLimitExceeded(String tokenDisplayText);

  @Message(level = MessageLevel.ERROR, text = "Failed to save token state: {0}")
  void failedToSaveTokenState(@StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR, text = "Error accessing token state: {0}")
  void errorAccessingTokenState(@StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR, text = "Failed to update token expiration: {0}")
  void failedToUpdateTokenExpiration(@StackTrace(level = MessageLevel.DEBUG) Exception e);

}
