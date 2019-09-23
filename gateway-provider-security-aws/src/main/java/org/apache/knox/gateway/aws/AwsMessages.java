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
package org.apache.knox.gateway.aws;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;

/**
 * Logging messages for the pac4j provider.
 *
 * @since 0.8.0
 */
@Messages(logger="org.apache.knox.gateway.aws")
public interface AwsMessages {

  @Message(level = MessageLevel.DEBUG, text = "Processing SAML Response")
  void processSamlResponse();

  @Message(level = MessageLevel.DEBUG, text = "Processing AWS credentials")
  void processAwsCredentials();

  @Message(level = MessageLevel.DEBUG, text = "Processed the AWS credentials - done with SAML handler")
  void samlHandlerDone();
}
