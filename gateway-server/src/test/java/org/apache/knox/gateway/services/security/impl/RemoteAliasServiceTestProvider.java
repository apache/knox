/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.services.security.impl;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.security.RemoteAliasServiceProvider;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.AbstractAliasService;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.util.PasswordUtils;

import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RemoteAliasServiceTestProvider implements RemoteAliasServiceProvider {
  @Override
  public String getType() {
    return "test";
  }

  @Override
  public AliasService newInstance(AliasService localAliasService, MasterService masterService) {
    return new TestAliasService();
  }

  @Override
  public AliasService newInstance(GatewayServices gatewayService, AliasService localAliasService, MasterService masterService) {
    return new TestAliasService();
  }

  private class TestAliasService extends AbstractAliasService {
    private final Map<String, Map<String, String>> aliases = new HashMap<>();
    private GatewayConfig config;

    @Override
    public List<String> getAliasesForCluster(String clusterName) {
      return new ArrayList<>(aliases.getOrDefault(clusterName, Collections.emptyMap()).keySet());
    }

    @Override
    public void addAliasForCluster(String clusterName, String alias, String value) {
      if(!aliases.containsKey(clusterName)) {
        aliases.put(clusterName, new HashMap<>());
      }
      aliases.get(clusterName).put(alias, value);
    }

    @Override
    public void addAliasesForCluster(String clusterName, Map<String, String> credentials) throws AliasServiceException {
      for (Map.Entry<String, String> credential : credentials.entrySet()) {
        addAliasForCluster(clusterName, credential.getKey(), credential.getValue());
      }
    }

    @Override
    public void removeAliasForCluster(String clusterName, String alias) {
      aliases.getOrDefault(clusterName, new HashMap<>()).remove(alias);
    }

    @Override
    public void removeAliasesForCluster(String clusterName, Set<String> aliases) throws AliasServiceException {
      for (String alias : aliases) {
        removeAliasForCluster(clusterName, alias);
      }
    }

    @Override
    public char[] getPasswordFromAliasForCluster(String clusterName, String alias) {
      return aliases.getOrDefault(clusterName, new HashMap<>()).get(alias).toCharArray();
    }

    @Override
    public char[] getPasswordFromAliasForCluster(String clusterName, String alias, boolean generate) {
      if(generate) {
        generateAliasForCluster(clusterName, alias);
      }
      return getPasswordFromAliasForCluster(clusterName, alias);
    }

    @Override
    public void generateAliasForCluster(String clusterName, String alias) {
      addAliasForCluster(clusterName, alias, PasswordUtils.generatePassword(16));
    }

    @Override
    public char[] getPasswordFromAliasForGateway(String alias) {
      return getPasswordFromAliasForCluster(NO_CLUSTER_NAME, alias);
    }

    @Override
    public char[] getGatewayIdentityPassphrase() {
      return getPasswordFromAliasForGateway(config.getIdentityKeyPassphraseAlias());
    }

    @Override
    public char[] getGatewayIdentityKeystorePassword() {
      return getPasswordFromAliasForGateway(config.getIdentityKeystorePasswordAlias());
    }

    @Override
    public char[] getSigningKeyPassphrase() {
      return getPasswordFromAliasForGateway(config.getSigningKeyPassphraseAlias());
    }

    @Override
    public char[] getSigningKeystorePassword() {
      return getPasswordFromAliasForGateway(config.getSigningKeystorePasswordAlias());
    }

    @Override
    public void generateAliasForGateway(String alias) {
      generateAliasForCluster(NO_CLUSTER_NAME, alias);
    }

    @Override
    public Certificate getCertificateForGateway(String alias) throws AliasServiceException {
      throw new AliasServiceException(new UnsupportedOperationException());
    }

    @Override
    public void init(GatewayConfig config, Map<String, String> options) {
      this.config = config;
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }
  }
}
