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
package org.apache.knox.gateway.service.auth;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger = "org.apache.knox.gateway.service.auth")
public interface AuthMessages {

  @Message(level = MessageLevel.ERROR, text = "There was a problem extracting authenticated principal from request.")
  void noPrincipalFound();

  @Message(level = MessageLevel.INFO, text = "Serving request for path: {0}")
  void pathValue(String path);

  @Message(level = MessageLevel.ERROR, text = "Failed to lookup roles for user {0}: {1}")
  void ldapRolesLookupFailed(String user, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.WARN, text = "The authenticated user''s token ({0} bytes) exceeds the configured {1} of {2} bytes; "
      + "the {3} response header is omitted. Raise the limit, and the proxy''s response header buffer, if the token is needed downstream.")
  void authTokenTooLargeToForward(int tokenLength, String limitParamName, int limit, String headerName);

  @Message(level = MessageLevel.WARN, text = "The {0} service parameter is set to ''{1}'', which is not a usable HTTP response header name; "
      + "the authenticated user''s token is not forwarded.")
  void authTokenHeaderNameNotUsable(String paramName, String headerName);

  @Message(level = MessageLevel.WARN, text = "The {0} service parameter value ''{1}'' is not a number; falling back to the default of {2}.")
  void limitNotANumber(String paramName, String configuredValue, String defaultValue);

  @Message(level = MessageLevel.WARN, text = "The {0} service parameter is set to ''{1}'', which is not a usable HTTP response header name; "
      + "falling back to ''{2}''.")
  void headerNameNotUsableFallingBack(String paramName, String headerName, String defaultName);

  @Message(level = MessageLevel.WARN, text = "The {0} service parameter is set to ''{1}'', which is not a usable HTTP response header name; "
      + "the parameter is ignored.")
  void headerNameNotUsableDropped(String paramName, String headerName);

  @Message(level = MessageLevel.WARN, text = "The authenticated user''s token is not a well-formed JWS compact serialization, so the {0} "
      + "response header is omitted rather than written with an unvalidated value.")
  void authTokenNotWellFormed(String headerName);

  @Message(level = MessageLevel.WARN, text = "The {0} service parameter is set to ''{1}'', which collides with an identity header this "
      + "service already emits; the token header is omitted so the identity header is not overwritten.")
  void authTokenHeaderNameCollides(String paramName, String headerName);

}
