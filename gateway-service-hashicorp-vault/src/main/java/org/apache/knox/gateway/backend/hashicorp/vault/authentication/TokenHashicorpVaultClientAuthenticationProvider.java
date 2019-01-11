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
package org.apache.knox.gateway.backend.hashicorp.vault.authentication;

import org.apache.knox.gateway.services.security.AliasService;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;

import java.util.Map;

public class TokenHashicorpVaultClientAuthenticationProvider
    implements HashicorpVaultClientAuthenticationProvider {
  public static final String TYPE = "token";
  public static final String TOKEN_KEY = AUTHENTICATION_CONFIG_PREFIX + "token";

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public ClientAuthentication newInstance(AliasService localAliasService,
                                          Map<String, String> properties) throws Exception {
    String vaultToken = getVaultToken(localAliasService, properties);
    return new TokenAuthentication(vaultToken);
  }

  /**
   * Returns the Vault token from the properties and looks it up in the AliasService
   * if it is an alias.
   *
   * @param localAliasService alias service to use to look up the token
   * @param properties properties for the Hashicorp Vault remote alias service
   * @return string of the Vault token
   * @throws Exception exception if there is an error retrieving the Vault token
   */
  private String getVaultToken(AliasService localAliasService, Map<String, String> properties)
      throws Exception {
    String vaultToken = properties.get(TOKEN_KEY);
    if(vaultToken.startsWith("${ALIAS=") && vaultToken.endsWith("}")) {
      // Strip off ${ALIAS= and } from the value before looking it up
      String vaultTokenAlias = vaultToken.substring(8, vaultToken.length()-1);
      return new String(localAliasService.getPasswordFromAliasForGateway(vaultTokenAlias));
    }
    return vaultToken;
  }
}
