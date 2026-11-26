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
package org.apache.knox.gateway.services.knoxidf.flowstate;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger = "org.apache.knox.gateway.knoxidf.authz.flow.state.service")
public interface AuthzFlowStateServiceMessages {

  @Message(level = MessageLevel.ERROR, text = "An error occurred while saving KnoxIDF authorization flow state ({0}) in the database : {1}")
  void errorSavingAuthzFlowState(String kind, String errorMessage, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR, text = "An error occurred while reading KnoxIDF authorization flow state ({0}) from the database : {1}")
  void errorReadingAuthzFlowState(String kind, String errorMessage, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR, text = "An error occurred while removing KnoxIDF authorization flow state ({0}) from the database : {1}")
  void errorRemovingAuthzFlowState(String kind, String errorMessage, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR, text = "An error occurred while removing expired KnoxIDF authorization flow state from the database : {0}")
  void errorRemovingExpiredAuthzFlowState(String errorMessage, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.DEBUG, text = "Removed {0} expired KnoxIDF authorization flow state entries")
  void removedExpiredAuthzFlowState(int count);

  @Message(level = MessageLevel.ERROR, text = "Could not use the database for KnoxIDF authorization flow state, using in-memory state instead; "
      + "KnoxIDF authorize flows need load balancer session affinity : {0}")
  void fallingBackToInMemoryAuthzFlowState(String errorMessage, @StackTrace(level = MessageLevel.DEBUG) Exception e);
}
