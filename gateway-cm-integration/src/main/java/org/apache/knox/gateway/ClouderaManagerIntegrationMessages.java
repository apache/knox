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

@Messages(logger="org.apache.knox.gateway")
public interface ClouderaManagerIntegrationMessages {

  @Message(level = MessageLevel.INFO, text = "Parsing XML descriptor {0}")
  void parseXmlDescriptor(String path);

  @Message(level = MessageLevel.INFO, text = "Found descriptors {0} in {1}")
  void parsedXmlDescriptor(String descriptorList, String path);

  @Message(level = MessageLevel.ERROR, text = "Parsing descriptor {0} failed: {1}")
  void failedToParseDescriptor(String name, String errorMessage, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR, text = "Parsing XML configuration {0} failed: {1}")
  void failedToParseXmlConfiguration(String path, String errorMessage, @StackTrace(level = MessageLevel.DEBUG) Exception e);
}
