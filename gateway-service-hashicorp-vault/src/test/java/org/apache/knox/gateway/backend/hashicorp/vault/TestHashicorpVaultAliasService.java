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
package org.apache.knox.gateway.backend.hashicorp.vault;

import org.apache.knox.gateway.backend.hashicorp.vault.authentication.HashicorpVaultClientAuthenticationProvider;
import org.apache.knox.gateway.backend.hashicorp.vault.authentication.TokenHashicorpVaultClientAuthenticationProvider;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.test.category.VerifyTest;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.vault.VaultContainer;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.apache.knox.gateway.backend.hashicorp.vault.HashicorpVaultAliasService.VAULT_SEPARATOR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;

@Category(VerifyTest.class)
public class TestHashicorpVaultAliasService {
  private static final Logger LOG = LoggerFactory.getLogger(TestHashicorpVaultAliasService.class);

  private static final String vaultVersion = "1.3.1";
  private static final String vaultImage = "vault:" + vaultVersion;
  private static final Integer vaultPort = 8200;
  private static final String vaultToken = "myroot";
  private String vaultSecretsEngine;

  private static GenericContainer vaultContainer;
  private static String vaultAddress;

  @BeforeClass
  public static void setUpClass() {
    try {
      vaultContainer = new VaultContainer(vaultImage)
                           .withVaultToken(vaultToken)
                           .waitingFor(Wait.forListeningPort());
      vaultContainer.addExposedPort(vaultPort);

      vaultContainer.start();

      vaultAddress = String.format(Locale.ROOT,
          "http://%s:%s",
          vaultContainer.getContainerIpAddress(),
          vaultContainer.getMappedPort(vaultPort));

      assertTrue(vaultContainer.isRunning());
    } catch (Exception e) {
      assumeNoException(e);
    }
  }

  @Before
  public void setUp() throws Exception {
    vaultSecretsEngine = "knox-secret-" + ThreadLocalRandom.current().nextInt(100);

    setupVaultSecretsEngine();
  }

  private void setupVaultSecretsEngine() throws Exception {
    Container.ExecResult execResult = vaultContainer.execInContainer("vault", "secrets",
        "enable", "-path=" + vaultSecretsEngine, "-version=2", "kv");
    assertEquals(0, execResult.getExitCode());
    LOG.debug("created KV secrets engine {}", vaultSecretsEngine);
  }

  @After
  public void tearDown() throws Exception {
    cleanupVaultPolicy();
    cleanupVaultSecretsEngine();

    vaultSecretsEngine = null;
  }

  private void cleanupVaultSecretsEngine() throws Exception {
    vaultContainer.execInContainer("vault", "secrets", "disable", vaultSecretsEngine);
    LOG.debug("deleted KV secrets engine {}", vaultSecretsEngine);
  }

  @AfterClass
  public static void tearDownClass() {
    if(vaultContainer != null) {
      vaultContainer.stop();
    }
  }

  private String getKnoxToken(boolean forceKnoxSpecifcToken) throws Exception {
    String token;
    if(forceKnoxSpecifcToken) {
      LOG.info("Using Knox specific token");
      Container.ExecResult tokenCreationExecResult = vaultContainer.execInContainer("vault", "token",
          "create", "-policy=" + getVaultPolicy(), "-field=token");
      token = tokenCreationExecResult.getStdout().replaceAll("\\s", "").trim();
    } else {
      LOG.info("Using root token");
      token = vaultToken;
    }
    return token;
  }

  private String getVaultPolicy() {
    return vaultSecretsEngine + "-policy";
  }

  @Test
  public void testVaultIntegration() throws Exception {
    String vaultPathPrefix = generatePathPrefix();
    setupVaultPolicy(VAULT_SEPARATOR);

    GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);

    Map<String, String> remoteAliasServiceConfiguration = new HashMap<>();
    remoteAliasServiceConfiguration.put(HashicorpVaultAliasService.VAULT_ADDRESS_KEY,
        vaultAddress);
    remoteAliasServiceConfiguration.put(HashicorpVaultAliasService.VAULT_SECRETS_ENGINE_KEY,
        vaultSecretsEngine);
    remoteAliasServiceConfiguration.put(HashicorpVaultAliasService.VAULT_PATH_PREFIX_KEY,
        vaultPathPrefix);
    remoteAliasServiceConfiguration.put(HashicorpVaultClientAuthenticationProvider.AUTHENTICATION_TYPE_KEY,
        TokenHashicorpVaultClientAuthenticationProvider.TYPE);
    remoteAliasServiceConfiguration.put(TokenHashicorpVaultClientAuthenticationProvider.TOKEN_KEY,
        getKnoxToken(ThreadLocalRandom.current().nextBoolean()));

    EasyMock.expect(gatewayConfig.getRemoteAliasServiceConfiguration())
        .andReturn(remoteAliasServiceConfiguration).anyTimes();
    EasyMock.replay(gatewayConfig);

    AliasService localAliasService = EasyMock.createNiceMock(AliasService.class);

    AliasService aliasService = new HashicorpVaultAliasService(localAliasService);
    aliasService.init(gatewayConfig, Collections.emptyMap());
    aliasService.start();

    String clusterName = "test-" + ThreadLocalRandom.current().nextInt(100);
    String alias = "abc-" + ThreadLocalRandom.current().nextInt(100);
    String aliasPassword = "def-" + ThreadLocalRandom.current().nextInt(100);

    assertEquals(0, aliasService.getAliasesForCluster(clusterName).size());

    aliasService.addAliasForCluster(clusterName, alias, aliasPassword);

    assertEquals(1, aliasService.getAliasesForCluster(clusterName).size());

    char[] vaultAliasPassword = aliasService.getPasswordFromAliasForCluster(clusterName, alias);
    assertEquals(aliasPassword, String.valueOf(vaultAliasPassword));

    aliasService.removeAliasForCluster(clusterName, alias);
    assertNull(aliasService.getPasswordFromAliasForCluster(clusterName, alias));
    assertEquals(0, aliasService.getAliasesForCluster(clusterName).size());

    char[] generatedPassword = aliasService.getPasswordFromAliasForCluster(clusterName, alias, true);
    assertNotNull(generatedPassword != null);
    assertNotEquals(generatedPassword, aliasPassword.toCharArray());

    aliasService.stop();
  }

  @Test
  public void testVaultIntegrationPermissions() throws Exception {
    String vaultPathPrefix = generatePathPrefix();
    setupVaultPolicy("/invalidPrefix/");

    GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);

    Map<String, String> remoteAliasServiceConfiguration = new HashMap<>();
    remoteAliasServiceConfiguration.put(HashicorpVaultAliasService.VAULT_ADDRESS_KEY, vaultAddress);
    remoteAliasServiceConfiguration.put(HashicorpVaultAliasService.VAULT_SECRETS_ENGINE_KEY,
        vaultSecretsEngine);
    remoteAliasServiceConfiguration.put(HashicorpVaultAliasService.VAULT_PATH_PREFIX_KEY,
        vaultPathPrefix);
    remoteAliasServiceConfiguration.put(HashicorpVaultClientAuthenticationProvider.AUTHENTICATION_TYPE_KEY,
        TokenHashicorpVaultClientAuthenticationProvider.TYPE);
    remoteAliasServiceConfiguration.put(TokenHashicorpVaultClientAuthenticationProvider.TOKEN_KEY,
        getKnoxToken(true));

    EasyMock.expect(gatewayConfig.getRemoteAliasServiceConfiguration())
        .andReturn(remoteAliasServiceConfiguration).anyTimes();
    EasyMock.replay(gatewayConfig);

    AliasService localAliasService = EasyMock.createNiceMock(AliasService.class);

    AliasService aliasService = new HashicorpVaultAliasService(localAliasService);
    aliasService.init(gatewayConfig, Collections.emptyMap());
    aliasService.start();

    String clusterName = "test-" + ThreadLocalRandom.current().nextInt(100);
    String alias = "abc-" + ThreadLocalRandom.current().nextInt(100);
    String aliasPassword = "def-" + ThreadLocalRandom.current().nextInt(100);

    try {
      aliasService.getAliasesForCluster(clusterName);
      fail("Should have gotten a 403");
    } catch (AliasServiceException e) {
      assertTrue(e.getMessage().contains("Status 403 Forbidden"));
    }

    try {
      aliasService.addAliasForCluster(clusterName, alias, aliasPassword);
      fail("Should have gotten a 403");
    } catch (AliasServiceException e) {
      assertTrue(e.getMessage().contains("Status 403 Forbidden"));
    }

    try {
      aliasService.getPasswordFromAliasForCluster(clusterName, alias);
      fail("Should have gotten a 403");
    } catch (AliasServiceException e) {
      assertTrue(e.getMessage().contains("Status 403 Forbidden"));
    }

    try {
      aliasService.removeAliasForCluster(clusterName, alias);
      fail("Should have gotten a 403");
    } catch (AliasServiceException e) {
      assertTrue(e.getMessage().contains("Status 403 Forbidden"));
    }

    aliasService.stop();
  }

  private String generatePathPrefix() {
    StringBuilder pathPrefix = new StringBuilder();
    int numParts = ThreadLocalRandom.current().nextInt(10);
    for(int i = 0; i < numParts; i++) {
      pathPrefix.append(VAULT_SEPARATOR).append(ThreadLocalRandom.current().nextInt(10));
    }
    pathPrefix.append(VAULT_SEPARATOR);
    String result = pathPrefix.toString();
    LOG.info("Using path prefix: '{}'", result);
    return result;
  }

  private void setupVaultPolicy(String pathPrefix) throws Exception {
    String policy = "path \"" + vaultSecretsEngine + pathPrefix + "*\" {\n" +
                        "  capabilities = [\"create\", \"read\", \"update\", \"delete\", \"list\"]\n" +
                        "}";
    LOG.info("policy: {}", policy);
    String policyFilePath = "/tmp/" + getVaultPolicy() + ".hcl";
    vaultContainer.copyFileToContainer(Transferable.of(policy.getBytes(StandardCharsets.UTF_8)),
        policyFilePath);
    vaultContainer.execInContainer("vault", "policy", "write", getVaultPolicy(), policyFilePath);
    LOG.debug("created policy {}", getVaultPolicy());
    vaultContainer.execInContainer("rm", "-f", policyFilePath);
  }

  private void cleanupVaultPolicy() {
    try {
      vaultContainer.execInContainer("vault", "policy", "delete", getVaultPolicy());
      LOG.debug("deleted policy {}", getVaultPolicy());
    } catch (Exception ignore) {
      // ignore
    }
  }
}
