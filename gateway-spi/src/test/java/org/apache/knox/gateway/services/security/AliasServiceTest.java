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
package org.apache.knox.gateway.services.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.security.cert.Certificate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.junit.Test;

/**
 * Tests the default {@link AliasService#isAlias(String)} and {@link AliasService#extractAlias(String)}
 * implementations, which must recognize both the canonical {@code ${ALIAS=...}} reference and the
 * Shiro-escaped {@code S{ALIAS=...}} variant produced by {@code ShiroConfig}.
 */
public class AliasServiceTest {

  private final AliasService aliasService = new NoOpAliasService();

  @Test
  public void testIsAliasAcceptsShiroEscapedPrefix() {
    assertTrue(aliasService.isAlias("S{ALIAS=my-password}"));
  }

  @Test
  public void testIsAliasAcceptsStandardPrefix() {
    assertTrue(aliasService.isAlias("${ALIAS=my-password}"));
  }

  @Test
  public void testIsAliasRejectsLiteralValue() {
    assertFalse(aliasService.isAlias("my-password"));
    assertFalse(aliasService.isAlias("ALIAS=my-password"));
    assertFalse(aliasService.isAlias(""));
  }

  @Test
  public void testExtractAliasFromShiroEscapedPrefix() {
    assertEquals("my-password", aliasService.extractAlias("S{ALIAS=my-password}"));
  }

  @Test
  public void testExtractAliasFromStandardPrefix() {
    assertEquals("my-password", aliasService.extractAlias("${ALIAS=my-password}"));
  }

  /**
   * Minimal {@link AliasService} that exercises only the interface default methods; all storage-backed
   * operations are unsupported.
   */
  private static class NoOpAliasService implements AliasService {
    @Override
    public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
    }

    @Override
    public void start() throws ServiceLifecycleException {
    }

    @Override
    public void stop() throws ServiceLifecycleException {
    }

    @Override
    public List<String> getAliasesForCluster(String clusterName) throws AliasServiceException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void addAliasForCluster(String clusterName, String alias, String value) throws AliasServiceException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void addAliasesForCluster(String clusterName, Map<String, String> credentials) throws AliasServiceException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void removeAliasForCluster(String clusterName, String alias) throws AliasServiceException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void removeAliasesForCluster(String clusterName, Set<String> aliases) throws AliasServiceException {
      throw new UnsupportedOperationException();
    }

    @Override
    public char[] getPasswordFromAliasForCluster(String clusterName, String alias) throws AliasServiceException {
      throw new UnsupportedOperationException();
    }

    @Override
    public char[] getPasswordFromAliasForCluster(String clusterName, String alias, boolean generate) throws AliasServiceException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void generateAliasForCluster(String clusterName, String alias) throws AliasServiceException {
      throw new UnsupportedOperationException();
    }

    @Override
    public char[] getPasswordFromAliasForGateway(String alias) throws AliasServiceException {
      throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, char[]> getPasswordsForGateway() throws AliasServiceException {
      throw new UnsupportedOperationException();
    }

    @Override
    public char[] getGatewayIdentityPassphrase() throws AliasServiceException {
      throw new UnsupportedOperationException();
    }

    @Override
    public char[] getGatewayIdentityKeystorePassword() throws AliasServiceException {
      throw new UnsupportedOperationException();
    }

    @Override
    public char[] getSigningKeyPassphrase() throws AliasServiceException {
      throw new UnsupportedOperationException();
    }

    @Override
    public char[] getSigningKeystorePassword() throws AliasServiceException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void generateAliasForGateway(String alias) throws AliasServiceException {
      throw new UnsupportedOperationException();
    }

    @Override
    public Certificate getCertificateForGateway(String alias) throws AliasServiceException {
      throw new UnsupportedOperationException();
    }
  }
}
