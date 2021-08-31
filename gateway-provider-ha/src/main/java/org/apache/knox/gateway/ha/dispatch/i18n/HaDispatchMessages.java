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
package org.apache.knox.gateway.ha.dispatch.i18n;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger = "org.apache.knox.gateway")
public interface HaDispatchMessages {
  @Message(level = MessageLevel.INFO, text = "Initializing Ha Dispatch for: {0}")
  void initializingForResourceRole(String resourceRole);

  @Message(level = MessageLevel.INFO, text = "Could not connect to server: {0} {1}")
  void errorConnectingToServer(String uri, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.INFO, text = "Failing over request to a different server: {0}")
  void failingOverRequest(String uri);

  @Message(level = MessageLevel.INFO, text = "Failed to connect to: {0}")
  void failedToConnectTo(String uri);

  @Message(level = MessageLevel.INFO, text = "Maximum attempts {0} to failover reached for service: {1}")
  void maxFailoverAttemptsReached(int attempts, String service);

  @Message(level = MessageLevel.INFO, text = "Error occurred while trying to sleep for failover : {0} {1}")
  void failoverSleepFailed(String service, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR, text = "noFallback flag is turned on for sticky session so aborting request without retrying")
  void noFallbackError();

  @Message(level = MessageLevel.INFO, text = "Knox HA loadbalancing disabled, detected user-agent: {0}, configured user-agents to disable loadbalancing: {1}")
  void disableHALoadbalancinguserAgent(String userAgent, String configuration);

  @Message(level = MessageLevel.ERROR, text = "Error setting non-loadbalanced url to outbound request")
  void errorSettingActiveUrl();

  @Message(level = MessageLevel.ERROR, text = "Unsupported encoding, cause: {0}")
  void unsupportedEncodingException(String cause);
}
