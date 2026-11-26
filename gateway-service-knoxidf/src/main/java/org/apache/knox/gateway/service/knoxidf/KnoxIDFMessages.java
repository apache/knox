/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.service.knoxidf;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger = "org.apache.knox.gateway.service.knoxidf")
public interface KnoxIDFMessages {

    @Message(level = MessageLevel.WARN, text = "Failed to fetch user params for {0}; issuing the token without them: {1}")
    void failedToFetchUserParamsForToken(String userName, String cause, @StackTrace(level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.WARN, text = "Failed to fetch user params for {0}; /userinfo is temporarily unavailable: {1}")
    void failedToFetchUserParamsForUserInfo(String userName, String cause, @StackTrace(level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.ERROR, text = "Invalid user params provider configuration: {0}")
    void invalidUserParamsProviderConfig(String reason);
}
