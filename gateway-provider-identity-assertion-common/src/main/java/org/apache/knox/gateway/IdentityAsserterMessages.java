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
import org.apache.knox.gateway.plang.Ast;
import org.apache.knox.gateway.plang.SyntaxException;

@Messages(logger="org.apache.knox.gateway")
public interface IdentityAsserterMessages {
  @Message( level = MessageLevel.ERROR, text = "Required subject/identity not available.  Check authentication/federation provider for proper configuration." )
  void subjectNotAvailable();

  @Message( level = MessageLevel.WARN, text = "Virtual group name is missing after dot character.")
  void missingVirtualGroupName();

  @Message( level = MessageLevel.WARN, text = "Parse error: {2}. At {0}={1}")
  void parseError(String key, String script, SyntaxException e);

  @Message( level = MessageLevel.WARN, text = "Invalid result: {2}. Expected boolean when evaluating: {1}. For virtualGroup: {0}")
  void invalidResult(String virtualGroupName, Ast ast, Object result);

  @Message( level = MessageLevel.DEBUG, text = "Adding user: {0} to virtual group: {1} using predicate: {2}")
  void addingUserToVirtualGroup(String username, String virtualGroupName, Ast ast);

  @Message( level = MessageLevel.DEBUG, text = "Not adding user: {0} to virtual group: {1} using predicate: {2}")
  void notAddingUserToVirtualGroup(String username, String virtualGroupName, Ast ast);

  @Message( level = MessageLevel.DEBUG, text = "Checking user: {0} (with groups: {1}) whether to add virtualGroup: {2} using predicate: {3}")
  void checkingVirtualGroup(String userName, Set<String> userGroups, String virtualGroupName, Ast ast);

  @Message( level = MessageLevel.INFO, text = "User: {0} (with groups: {1}) added to virtual groups: {2}")
  void virtualGroups(String userName, Set<String> strings, Set<String> virtualGroups);
}
