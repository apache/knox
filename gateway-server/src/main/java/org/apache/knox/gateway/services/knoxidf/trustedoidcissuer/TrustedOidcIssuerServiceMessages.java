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
package org.apache.knox.gateway.services.knoxidf.trustedoidcissuer;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger = "org.apache.knox.gateway.knoxidf.trustedoidcissuer.service")
interface TrustedOidcIssuerServiceMessages {

  @Message(level = MessageLevel.ERROR,
      text = "Failed to fetch OIDC discovery document for issuer {0} from {1}: {2}")
  void errorFetchingDiscoveryDocument(String issuerUrl, String discoveryUrl, String cause,
      @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
      text = "Failed to parse OIDC discovery document for issuer {0}: {1}")
  void errorParsingDiscoveryDocument(String issuerUrl, String cause,
      @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
      text = "Error registering trusted OIDC issuer {0}: {1}")
  void errorRegisteringIssuer(String issuerUrl, String cause,
      @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
      text = "Error deregistering trusted OIDC issuer {0}: {1}")
  void errorDeregisteringIssuer(String issuerUrl, String cause,
      @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
      text = "Error reloading trusted OIDC issuer registry snapshot: {0}")
  void errorReloadingRegistrySnapshot(String cause,
      @StackTrace(level = MessageLevel.DEBUG) Exception e);
}
