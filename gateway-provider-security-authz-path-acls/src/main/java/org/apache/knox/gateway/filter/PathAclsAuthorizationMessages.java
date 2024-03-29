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
package org.apache.knox.gateway.filter;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;

@Messages(logger="org.apache.knox.gateway")
public interface PathAclsAuthorizationMessages {

  @Message( level = MessageLevel.INFO, text = "Initializing PathAclsAuthz Provider for: {0}" )
  void initializingForResourceRole(String resourceRole);

  @Message( level = MessageLevel.DEBUG, text = "Path ACL Processing Mode is: {0}" )
  void aclProcessingMode(String aclProcessingMode);

  @Message( level = MessageLevel.WARN, text = "Invalid Path ACLs found for rule: {0}" )
  void invalidAclsFoundForResource(String resourceRole);

  @Message( level = MessageLevel.WARN, text = "Invalid URL pattern for rule: {0}" )
  void invalidURLPattern(String rule);

  @Message( level = MessageLevel.INFO, text = "Path ACLs found for: {0}" )
  void aclsFoundForResource(String resourceRole);

  @Message( level = MessageLevel.DEBUG, text = "No Path ACLs found for: {0}" )
  void noAclsFoundForResource(String resourceRole);

  @Message( level = MessageLevel.DEBUG, text = "Path ACLs Access Granted: {0}" )
  void accessGranted(boolean accessGranted);

  @Message( level = MessageLevel.DEBUG, text = "Path ACLs Effective principal: {0}" )
  void effectivePrincipal(String name);

  @Message( level = MessageLevel.DEBUG, text = "Path ACLs Effective principal has access: {0}" )
  void effectivePrincipalHasAccess(boolean userAccess);

  @Message( level = MessageLevel.DEBUG, text = "Path ACLs GroupPrincipal has access: {0}" )
  void groupPrincipalHasAccess(boolean groupAccess);

  @Message( level = MessageLevel.DEBUG, text = "Path ACLs Remote IP Address: {0}" )
  void remoteIPAddress(String remoteAddr);

  @Message( level = MessageLevel.DEBUG, text = "Path ACLs Remote IP Address has access: {0}" )
  void remoteIPAddressHasAccess(boolean remoteIpAccess);

  @Message( level = MessageLevel.ERROR, text = "Path ACLs, error parsing URL: {0}" )
  void errorParsingUrl(String reason);

  @Message( level = MessageLevel.ERROR, text = "Path ACLs, error sending error code: {0}" )
  void errorSendCode(String reason);
}
