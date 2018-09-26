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
package org.apache.knox.gateway.ha.provider.impl.i18n;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;

@Messages(logger = "org.apache.knox.gateway")
public interface HaMessages {

   @Message(level = MessageLevel.ERROR, text = "Failed to Write HA Descriptor: {0}")
   void failedToWriteHaDescriptor(Exception e);

   @Message(level = MessageLevel.ERROR, text = "Failed to load HA Descriptor: {0}")
   void failedToLoadHaDescriptor(Exception e);

   @Message(level = MessageLevel.INFO, text = "No Active URL was found for service: {0}")
   void noActiveUrlFound(String serviceName);

   @Message(level = MessageLevel.INFO, text = "No Service by this name was found: {0}")
   void noServiceFound(String serviceName);

   @Message(level = MessageLevel.DEBUG, text = "Moving failed URL to the bottom {0}, new top is {1}")
   void markedFailedUrl(String failedUrl, String top);

  @Message(level = MessageLevel.ERROR, text = "Failed to get Zookeeper URLs : {0}")
  void failedToGetZookeeperUrls(Exception e);

}
