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
package org.apache.knox.gateway.pac4j;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;

/**
 * Logging messages for the pac4j provider.
 *
 * @since 0.8.0
 */
@Messages(logger="org.apache.knox.gateway.pac4j")
public interface Pac4jMessages {

  @Message( level = MessageLevel.ERROR, text = "pac4j callback URL required")
  void ssoAuthenticationProviderUrlRequired();

  @Message( level = MessageLevel.ERROR, text = "At least one pac4j client must be defined")
  void atLeastOnePac4jClientMustBeDefined();

  @Message( level = MessageLevel.ERROR, text = "Crypto service, alias service and cluster name required")
  void cryptoServiceAndAliasServiceAndClusterNameRequired();

  @Message( level = MessageLevel.ERROR, text = "Unable to generate a password for encryption")
  void unableToGenerateAPasswordForEncryption(Exception e);

  @Message( level = MessageLevel.INFO, text =
      "No private key passphrase alias found. Defaulting to master secret. Exception encountered: {0}")
  void noPrivateKeyPasshraseProvisioned(Exception e);

  @Message( level = MessageLevel.ERROR, text =
      "No keystore password alias found. Defaulting to master secret. Exception encountered: {0}")
  void noKeystorePasswordProvisioned(Exception e);

  @Message( level = MessageLevel.ERROR, text =
      "There was an error fetching keystore type. Exception encountered: {0}")
  void errorFetchingKeystoreType(Exception e);

  @Message( level = MessageLevel.DEBUG, text = "Pac4j keystore path used : {0}")
  void pac4jSamlKeystorePath(String path);

  @Message( level = MessageLevel.DEBUG, text = "Pac4j keystore type : {0}")
  void pac4jSamlKeystoreType(String type);
}
