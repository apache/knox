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
package org.apache.knox.gateway;

import java.util.Set;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.plang.AbstractSyntaxTree;
import org.apache.knox.gateway.plang.SyntaxException;

@Messages(logger="org.apache.knox.gateway")
public interface IdentityAsserterMessages {
  @Message( level = MessageLevel.ERROR, text = "Required subject/identity not available.  Check authentication/federation provider for proper configuration." )
  void subjectNotAvailable();

  @Message( level = MessageLevel.WARN, text = "Invalid mapping parameter name: Missing required group name.")
  void missingVirtualGroupName();

  @Message( level = MessageLevel.WARN, text = "Invalid mapping {0}={1}, Parse error: {2}")
  void parseError(String key, String script, SyntaxException e);

  @Message( level = MessageLevel.WARN, text = "Invalid result: {2}. Expected boolean when evaluating group {0} mapping value {1}.")
  void invalidResult(String virtualGroupName, AbstractSyntaxTree ast, Object result);

  @Message( level = MessageLevel.DEBUG, text = "Adding user {0} to group {1} based on predicate {2}")
  void addingUserToVirtualGroup(String username, String virtualGroupName, AbstractSyntaxTree ast);

  @Message( level = MessageLevel.DEBUG, text = "Checking whether user {0} (with group(s) {1}) should be added to group {2} based on predicate {3}")
  void checkingVirtualGroup(String userName, Set<String> userGroups, String virtualGroupName, AbstractSyntaxTree ast);

  @Message( level = MessageLevel.DEBUG, text = "User {0} (with group(s) {1}) added to group(s) {2}")
  void virtualGroups(String userName, Set<String> userGroups, Set<String> virtualGroups);

  @Message( level = MessageLevel.INFO, text = "Using configured impersonation parameters: {0}")
  void impersonationConfig(String config);
}
