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
package org.apache.knox.gateway.services.knoxidf.delegation;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger = "org.apache.knox.gateway.knoxidf.delegation.service")
interface DelegationPolicyServiceMessages {

  @Message(level = MessageLevel.ERROR,
      text = "Error registering delegation policy for actor ({0}, {1}): {2}")
  void errorRegisteringPolicy(String actorAuthority, String actorId, String cause,
      @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
      text = "Error updating delegation policy {0}: {1}")
  void errorUpdatingPolicy(String registrationId, String cause,
      @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
      text = "Error deleting delegation policy {0}: {1}")
  void errorDeletingPolicy(String registrationId, String cause,
      @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
      text = "Error reading delegation policy {0}: {1}")
  void errorReadingPolicy(String registrationId, String cause,
      @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
      text = "Error listing delegation policies: {0}")
  void errorListingPolicies(String cause,
      @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
      text = "Cannot evaluate canActFor.groups for subject {0}: the LDAP service is disabled or "
          + "unavailable; failing the exchange with a server error. Enable LDAP on the gateway to "
          + "evaluate group-based delegation policies")
  void groupLookupUnavailable(String subjectName);

  @Message(level = MessageLevel.ERROR,
      text = "Error resolving group membership for subject {0} during delegation policy evaluation: "
          + "{1}; failing the exchange with a server error")
  void errorEvaluatingGroupMembership(String subjectName, String cause,
      @StackTrace(level = MessageLevel.ERROR) Exception e);
}
