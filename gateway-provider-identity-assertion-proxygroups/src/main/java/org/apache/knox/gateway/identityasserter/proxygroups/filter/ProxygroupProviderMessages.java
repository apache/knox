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
package org.apache.knox.gateway.identityasserter.proxygroups.filter;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

/**
 * Messages for provider - HadoopGroupProvider
 *
 * @since 0.11
 */
@Messages(logger = "org.apache.knox.gateway")
public interface ProxygroupProviderMessages {

    @Message(level = MessageLevel.ERROR, text = "Error getting groups for principal {0}")
    void errorGettingUserGroups(String principal, @StackTrace(level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.INFO, text = "No groups for principal {0} found")
    void noGroupsFound(String principal);

    @Message(level = MessageLevel.DEBUG, text = "Found groups for principal {0} : {1}")
    void groupsFound(String principal, String groups);

    @Message(level = MessageLevel.DEBUG, text = "Found group mapping configuration in gateway-site")
    void groupMappingFound();

    @Message(level = MessageLevel.ERROR, text = "Proxy user Authentication failed: {0}")
    void hadoopAuthProxyUserFailed(@StackTrace Throwable t);

    @Message(level = MessageLevel.ERROR, text = "Proxy Group Authentication failed: {0}")
    void hadoopAuthProxyGroupFailed(@StackTrace Throwable t);

    @Message(level = MessageLevel.DEBUG, text = "doAsUser = {0}, RemoteUser = {1} , RemoteAddress = {2}")
    void hadoopAuthDoAsUser(String doAsUser, String remoteUser, String remoteAddr);

    @Message(level = MessageLevel.INFO, text = "Proxy Authentication successful: user {0} impersonating as principal: {1}, impersonation mode: {2}")
    void hadoopAuthProxyAccessSuccess(String principal, String doAsUser, String mode);

    @Message(level = MessageLevel.INFO, text = "Proxy group Authentication successful: user {0} impersonating as principal: {1}")
    void hadoopAuthProxyGroupSuccess(String principal, String doAsUser);

}
