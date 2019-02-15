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
package org.apache.knox.gateway.services.security;

import java.util.List;

import org.apache.knox.gateway.services.Service;

public interface AliasService extends Service {

  List<String> getAliasesForCluster(String clusterName)
      throws AliasServiceException;

  void addAliasForCluster(String clusterName, String alias,
      String value) throws AliasServiceException;

  void removeAliasForCluster(String clusterName, String alias)
      throws AliasServiceException;

  char[] getPasswordFromAliasForCluster(String clusterName,
      String alias) throws AliasServiceException;

  char[] getPasswordFromAliasForCluster(String clusterName,
      String alias, boolean generate) throws AliasServiceException;

  void generateAliasForCluster(String clusterName, String alias)
      throws AliasServiceException;

  char[] getPasswordFromAliasForGateway(String alias)
      throws AliasServiceException;

  /**
   * Retrieves the password for the configured identity keystore.
   * <p>
   * The alias for this password is set in the Gateway configuration using
   * "gateway.tls.keystore.password.alias" property. If not set, the default value is
   * "gateway-identity-keystore-password".  This alias is used to look up the password.
   * <p>
   * If not found, the master password should be returned.
   *
   * @return an array of characters; or <code>null</code>, if the alias did not resolve to a password.
   * @throws AliasServiceException if an error occurs looking up the alias
   */
  char[] getGatewayIdentityKeystorePassword() throws AliasServiceException;

  /**
   * Retrieves the password/passphrase for the configured identity key.
   * <p>
   * The alias for this password is set in the Gateway configuration using
   * "gateway.tls.key.passphrase.alias" property. If not set, the default value is
   * "gateway-identity-passphrase".  This alias is used to look up the password.
   * <p>
   * If not found, the password for the keystore holding the Gateway identity key should be returned.
   *
   * @return an array of characters; or <code>null</code>, if the alias did not resolve to a password.
   * @throws AliasServiceException if an error occurs looking up the alias
   */
  char[] getGatewayIdentityPassphrase() throws AliasServiceException;

  /**
   * Retrieves the password for the configured signing keystore.
   * <p>
   * The alias for this password is set in the Gateway configuration using
   * "gateway.signing.keystore.password.alias" property. If not set, the default value is
   * "signing.keystore.password".  This alias is used to look up the password.
   * <p>
   * If not found, the master password should be returned.
   *
   * @return an array of characters; or <code>null</code>, if the alias did not resolve to a password.
   * @throws AliasServiceException if an error occurs looking up the alias
   */
  char[] getSigningKeystorePassword() throws AliasServiceException;

  /**
   * Retrieves the password/passphrase for the configured signing key.
   * <p>
   * The alias for this password is set in the Gateway configuration using
   * "gateway.signing.key.passphrase.alias" property. If not set, the default value is
   * "signing.key.passphrase".  This alias is used to look up the password.
   * <p>
   * If not found, the password for the keystore holding the signing key should be returned.
   *
   * @return an array of characters; or <code>null</code>, if the alias did not resolve to a password.
   * @throws AliasServiceException if an error occurs looking up the alias
   */
  char[] getSigningKeyPassphrase() throws AliasServiceException;

  void generateAliasForGateway(String alias)
      throws AliasServiceException;
}