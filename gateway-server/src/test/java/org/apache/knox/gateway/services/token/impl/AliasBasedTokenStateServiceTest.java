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
package org.apache.knox.gateway.services.token.impl;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.token.TokenStateService;

import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AliasBasedTokenStateServiceTest extends DefaultTokenStateServiceTest {

  @Override
  protected TokenStateService createTokenStateService() {
    AliasBasedTokenStateService tss = new AliasBasedTokenStateService();
    tss.setAliasService(new TestAliasService());
    initTokenStateService(tss);
    return tss;
  }

  /**
   * A dumbed-down AliasService implementation for testing purposes only.
   */
  private static final class TestAliasService implements AliasService {

    private final Map<String, Map<String, String>> clusterAliases= new HashMap<>();


    @Override
    public List<String> getAliasesForCluster(String clusterName) throws AliasServiceException {
      List<String> aliases = new ArrayList<>();

      if (clusterAliases.containsKey(clusterName)) {
          aliases.addAll(clusterAliases.get(clusterName).keySet());
      }
      return aliases;
    }

    @Override
    public void addAliasForCluster(String clusterName, String alias, String value) throws AliasServiceException {
      Map<String, String> aliases = null;
      if (clusterAliases.containsKey(clusterName)) {
        aliases = clusterAliases.get(clusterName);
      } else {
        aliases = new HashMap<>();
        clusterAliases.put(clusterName, aliases);
      }
      aliases.put(alias, value);
    }

    @Override
    public void removeAliasForCluster(String clusterName, String alias) throws AliasServiceException {
      if (clusterAliases.containsKey(clusterName)) {
        clusterAliases.get(clusterName).remove(alias);
      }
    }

    @Override
    public char[] getPasswordFromAliasForCluster(String clusterName, String alias) throws AliasServiceException {
      char[] value = null;
      if (clusterAliases.containsKey(clusterName)) {
        String valString = clusterAliases.get(clusterName).get(alias);
        if (valString != null) {
          value = valString.toCharArray();
        }
      }
      return value;
    }

    @Override
    public char[] getPasswordFromAliasForCluster(String clusterName, String alias, boolean generate) throws AliasServiceException {
      return new char[0];
    }

    @Override
    public void generateAliasForCluster(String clusterName, String alias) throws AliasServiceException {
    }

    @Override
    public char[] getPasswordFromAliasForGateway(String alias) throws AliasServiceException {
      return getPasswordFromAliasForCluster(AliasService.NO_CLUSTER_NAME, alias);
    }

    @Override
    public char[] getGatewayIdentityPassphrase() throws AliasServiceException {
      return new char[0];
    }

    @Override
    public char[] getGatewayIdentityKeystorePassword() throws AliasServiceException {
      return new char[0];
    }

    @Override
    public char[] getSigningKeyPassphrase() throws AliasServiceException {
      return new char[0];
    }

    @Override
    public char[] getSigningKeystorePassword() throws AliasServiceException {
      return new char[0];
    }

    @Override
    public void generateAliasForGateway(String alias) throws AliasServiceException {
    }

    @Override
    public Certificate getCertificateForGateway(String alias) throws AliasServiceException {
      return null;
    }

    @Override
    public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
    }

    @Override
    public void start() throws ServiceLifecycleException {
    }

    @Override
    public void stop() throws ServiceLifecycleException {
    }
  }

}
