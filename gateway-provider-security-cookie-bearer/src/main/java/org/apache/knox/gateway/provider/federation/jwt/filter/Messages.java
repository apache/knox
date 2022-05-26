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
package org.apache.knox.gateway.provider.federation.jwt.filter;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.StackTrace;
import org.apache.knox.gateway.provider.federation.jwt.JWTMessages;

@org.apache.knox.gateway.i18n.messages.Messages(
        logger = "org.apache.knox.gateway.provider.federation.jwt"
)
public interface Messages extends JWTMessages {

    @Message(
            level = MessageLevel.WARN,
            text = "Failed to validate the issuer attribute."
    )
    void failedToValidateIssuer();

    @Message(
            level = MessageLevel.WARN,
            text = "Unexpected signature algorithm."
    )
    void unexpectedSigAlg();

    @Message(
            level = MessageLevel.WARN,
            text = "Token is corrupt: {0}"
    )
    void corruptToken(@StackTrace(level = MessageLevel.WARN) Exception e);

    @Message(
            level = MessageLevel.WARN,
            text = "Token is unknown: {0}"
    )
    void unknownToken(@StackTrace(level = MessageLevel.WARN) Exception e);

    @Message(
            level = MessageLevel.WARN,
            text = "Unknown signing key."
    )
    void unknownSigningKey();
}
