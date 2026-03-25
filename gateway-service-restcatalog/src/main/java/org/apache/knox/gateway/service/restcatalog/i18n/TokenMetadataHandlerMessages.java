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
package org.apache.knox.gateway.service.restcatalog.i18n;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger = "org.apache.knox.gateway")
public interface TokenMetadataHandlerMessages {

    @Message(level = MessageLevel.INFO, text = "Configured metadata header prefix: {0}")
    void configuredMetatadataHeaderPrefix(String prefix);

    @Message(level = MessageLevel.WARN, text = "There is no metadata associated with client ID: {0}")
    void noMetadataForClientId(String clientId);

    @Message(level = MessageLevel.ERROR, text = "Invalid client secret: {0}")
    void invalidClientSecret(@StackTrace(level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.ERROR, text = "Invalid client ID: {0} ; {1}")
    void invalidClientId(String clientId, @StackTrace(level = MessageLevel.DEBUG) Exception e);

}
