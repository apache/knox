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
package org.apache.knox.gateway.service.config.remote;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;


/**
 *
 */
@Messages(logger="org.apache.knox.gateway.service.config.remote")
public interface RemoteConfigurationMessages {

    @Message(level = MessageLevel.WARN,
             text = "Multiple remote configuration registries are not currently supported if any of them requires authentication.")
    void multipleRemoteRegistryConfigurations();

    @Message(level = MessageLevel.ERROR, text = "Failed to resolve the credential alias {0}")
    void unresolvedCredentialAlias(final String alias);

    @Message(level = MessageLevel.ERROR, text = "An error occurred interacting with the remote configuration registry : {0}")
    void errorInteractingWithRemoteConfigRegistry(@StackTrace(level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.ERROR, text = "An error occurred handling the ACL for remote configuration {0} : {1}")
    void errorHandlingRemoteConfigACL(final String path,
                                      @StackTrace(level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.ERROR, text = "An error occurred setting the ACL for remote configuration {0} : {1}")
    void errorSettingEntryACL(final String path,
                              @StackTrace(level = MessageLevel.DEBUG) Exception e);

}
