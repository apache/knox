/**
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
package org.apache.hadoop.gateway.service.knoxsso;

import org.apache.hadoop.gateway.i18n.messages.Message;
import org.apache.hadoop.gateway.i18n.messages.MessageLevel;
import org.apache.hadoop.gateway.i18n.messages.Messages;
import org.apache.hadoop.gateway.i18n.messages.StackTrace;

@Messages(logger="org.apache.hadoop.gateway.service.knoxsso")
public interface KnoxSSOMessages {
  @Message( level = MessageLevel.INFO, text = "About to redirect to original URL: {0}")
  void aboutToRedirectToOriginal(String original);

  @Message( level = MessageLevel.DEBUG, text = "Adding the following JWT token as a cookie: {0}")
  void addingJWTCookie(String token);

  @Message( level = MessageLevel.INFO, text = "Unable to find cookie with name: {0}")
  void cookieNotFound(String name);

  @Message( level = MessageLevel.ERROR, text = "Unable to properly send needed HTTP status code: {0}, {1}")
  void unableToCloseOutputStream(String message, String string);

  @Message( level = MessageLevel.ERROR, text = "Unable to add cookie to response. {0}: {1}")
  void unableAddCookieToResponse(String message, String stackTrace);

  @Message( level = MessageLevel.ERROR, text = "Original URL not found in request.")
  void originalURLNotFound();

  @Message( level = MessageLevel.INFO, text = "JWT cookie successfully added.")
  void addedJWTCookie();

  @Message( level = MessageLevel.ERROR, text = "Unable to issue token.")
  void unableToIssueToken(@StackTrace( level = MessageLevel.DEBUG) Exception e);
}