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
package org.apache.knox.gateway.services.token.impl;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;

@Messages(logger = "org.apache.knox.gateway.services.token.state")
public interface TokenAuthorityServiceMessages {
  @Message(level = MessageLevel.ERROR, text = "There was an error getting kid, cause: {0}")
  void errorGettingKid(String message);

  @Message(level = MessageLevel.ERROR, text = "Failed to verify token using JWKS endpoint {0}, reason: {1}")
  void jwksVerificationFailed(String jwksUrl, String reason);

  @Message(level = MessageLevel.WARN, text = "Ignoring typ header verification for token")
  void ignoreTypeHeaderVerification();
}
