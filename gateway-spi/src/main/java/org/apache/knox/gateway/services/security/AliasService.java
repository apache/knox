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

import java.security.cert.Certificate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.knox.gateway.services.Service;

public interface AliasService extends Service {
  String NO_CLUSTER_NAME = "__gateway";

  List<String> getAliasesForCluster(String clusterName)
      throws AliasServiceException;

  void addAliasForCluster(String clusterName, String alias,
      String value) throws AliasServiceException;

  void addAliasesForCluster(String clusterName,
      Map<String, String> credentials) throws AliasServiceException;

  void removeAliasForCluster(String clusterName, String alias)
      throws AliasServiceException;

  void removeAliasesForCluster(String clusterName, Set<String> aliases)
      throws AliasServiceException;

  char[] getPasswordFromAliasForCluster(String clusterName,
      String alias) throws AliasServiceException;

  char[] getPasswordFromAliasForCluster(String clusterName,
      String alias, boolean generate) throws AliasServiceException;

  void generateAliasForCluster(String clusterName, String alias)
      throws AliasServiceException;

  char[] getPasswordFromAliasForGateway(String alias)
      throws AliasServiceException;

  Map<String, char[]> getPasswordsForGateway() throws AliasServiceException;

  char[] getGatewayIdentityPassphrase() throws AliasServiceException;

  char[] getGatewayIdentityKeystorePassword() throws AliasServiceException;

  char[] getSigningKeyPassphrase() throws AliasServiceException;

  char[] getSigningKeystorePassword() throws AliasServiceException;

  void generateAliasForGateway(String alias)
      throws AliasServiceException;

  Certificate getCertificateForGateway(String alias)
      throws AliasServiceException;
}
