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
package org.apache.knox.gateway;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;


@Messages(logger="org.apache.knox.gateway.provider.federation.remote")
public interface RemoteAuthMessages {
  @Message( level = MessageLevel.WARN, text = "Missing required parameter named: {0}. Please check topology configuration.)" )
  void missingRequiredParameter(String paramName);

  @Message( level = MessageLevel.WARN, text = "Authentication of the user failed.)" )
  void failedToAuthenticateToRemoteAuthServer();

  @Message( level = MessageLevel.WARN, text = "Error received during authentication process: {0}.)" )
  void errorReceivedWhileAuthenticatingRequest(@StackTrace( level = MessageLevel.ERROR) Exception e);

  @Message( level = MessageLevel.WARN, text = "Error received during authentication process: {0} {1}.)" )
  void failedToLoadTruststore(String message, @StackTrace( level = MessageLevel.ERROR) Exception e);
}
