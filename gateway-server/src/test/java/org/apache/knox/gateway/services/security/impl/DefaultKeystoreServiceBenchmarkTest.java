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
package org.apache.knox.gateway.services.security.impl;

import static org.apache.knox.gateway.services.security.AliasService.NO_CLUSTER_NAME;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.gateway.services.security.MasterService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Measures the keystore work on the hot paths of KNOX-3579 with BCFKS stores, as used in FIPS
 * deployments. Skipped unless run with <code>-Dknox.keystore.benchmark=true</code>:
 * <pre>
 * mvn -pl gateway-server test -Dtest=DefaultKeystoreServiceBenchmarkTest -Dknox.keystore.benchmark=true
 * </pre>
 * Optional: <code>-Dknox.keystore.benchmark.threads=1,16</code>,
 * <code>-Dknox.keystore.benchmark.seconds=5</code>, <code>-Dknox.keystore.benchmark.warmup.seconds=10</code> and
 * <code>-Dknox.keystore.benchmark.cache=false,true</code>
 * (the values of <code>gateway.keystore.file.cache.enabled</code> to measure).
 */
public class DefaultKeystoreServiceBenchmarkTest {
  private static final String SIGNING_KEY_ALIAS = "gateway-identity";
  private static final String TOPOLOGY = "sandbox";
  private static final String LDAP_PASSWORD_ALIAS = "ldap-system-password";

  @Rule
  public final TemporaryFolder testFolder = new TemporaryFolder();

  private interface Operation {
    void run(DefaultKeystoreService keystoreService) throws Exception;
  }

  @Test
  public void benchmark() throws Exception {
    assumeTrue(Boolean.getBoolean("knox.keystore.benchmark"));
    Security.addProvider(new BouncyCastleProvider());

    final long seconds = Long.getLong("knox.keystore.benchmark.seconds", 5L);
    final long warmUpSeconds = Long.getLong("knox.keystore.benchmark.warmup.seconds", 10L);
    final List<Integer> threadCounts = new ArrayList<>();
    for (String threadCount : System.getProperty("knox.keystore.benchmark.threads", "1,16").split(",")) {
      threadCounts.add(Integer.valueOf(threadCount.trim()));
    }

    // SSO cookie verification: DefaultTokenAuthorityService.selectVerificationKey
    final Operation cookieVerification = keystoreService ->
        assertNotNull(keystoreService.getSigningKeystore().getCertificate(SIGNING_KEY_ALIAS));
    // WebSSO login: the per-request WebSSOResource.init looks up the (normally absent) HMAC secret,
    // the LDAP bind looks up the system password in the topology, then the gateway credential store
    final Operation login = keystoreService -> {
      keystoreService.getCredentialForCluster(NO_CLUSTER_NAME, "gateway.signing.hmac.secret");
      if (keystoreService.getCredentialForCluster(TOPOLOGY, LDAP_PASSWORD_ALIAS) == null) {
        assertNotNull(keystoreService.getCredentialForCluster(NO_CLUSTER_NAME, LDAP_PASSWORD_ALIAS));
      }
    };

    for (String cache : System.getProperty("knox.keystore.benchmark.cache", "false,true").split(",")) {
      final boolean fileCacheEnabled = Boolean.parseBoolean(cache.trim());
      final DefaultKeystoreService keystoreService = createKeystoreService(fileCacheEnabled);
      // Without the cache, an operation takes long enough for a short warm-up to leave it interpreted
      final int maxThreadCount = Collections.max(threadCounts);
      run(keystoreService, cookieVerification, maxThreadCount, warmUpSeconds);
      run(keystoreService, login, maxThreadCount, warmUpSeconds);
      for (int threadCount : threadCounts) {
        report("SSO cookie verification", fileCacheEnabled, threadCount,
            measure(keystoreService, cookieVerification, threadCount, seconds));
        report("WebSSO login", fileCacheEnabled, threadCount,
            measure(keystoreService, login, threadCount, seconds));
      }
    }
  }

  private DefaultKeystoreService createKeystoreService(boolean fileCacheEnabled) throws Exception {
    final char[] masterPassword = "master_password".toCharArray();
    // Not a mock: EasyMock serializes the calls to a mock, which would limit the throughput measured
    final MasterService masterService = () -> masterPassword;

    final Path baseDir = testFolder.newFolder().toPath();
    final GatewayConfigImpl config = new GatewayConfigImpl();
    config.set("gateway.data.dir", baseDir.toString());
    config.set("gateway.security.dir", baseDir.resolve("security").toString());
    config.set("gateway.tls.keystore.type", "BCFKS");
    config.set("gateway.credential.store.type", "BCFKS");
    config.set("gateway.keystore.file.cache.enabled", String.valueOf(fileCacheEnabled));

    final DefaultKeystoreService keystoreService = new DefaultKeystoreService();
    keystoreService.setMasterService(masterService);
    keystoreService.init(config, Collections.emptyMap());
    keystoreService.createKeystoreForGateway();
    keystoreService.addSelfSignedCertForGateway(SIGNING_KEY_ALIAS, masterPassword);
    keystoreService.createCredentialStoreForCluster(NO_CLUSTER_NAME);
    keystoreService.addCredentialForCluster(NO_CLUSTER_NAME, LDAP_PASSWORD_ALIAS, "secret");
    keystoreService.createCredentialStoreForCluster(TOPOLOGY);
    keystoreService.addCredentialForCluster(TOPOLOGY, "unrelated-alias", "value");
    return keystoreService;
  }

  private static double measure(DefaultKeystoreService keystoreService, Operation operation,
                                int threadCount, long seconds) throws Exception {
    run(keystoreService, operation, threadCount, 1L); // warm-up
    return (double) run(keystoreService, operation, threadCount, seconds) / seconds;
  }

  private static long run(DefaultKeystoreService keystoreService, Operation operation,
                          int threadCount, long seconds) throws Exception {
    final AtomicBoolean running = new AtomicBoolean(true);
    final AtomicLong count = new AtomicLong();
    final long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
    final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    try {
      final List<Future<Void>> futures = new ArrayList<>();
      for (int i = 0; i < threadCount; i++) {
        futures.add(executor.submit(() -> {
          while (running.get()) {
            operation.run(keystoreService);
            // Operations still waiting for a lock when the time is up do not count
            if (System.nanoTime() < end) {
              count.incrementAndGet();
            }
          }
          return null;
        }));
      }
      TimeUnit.NANOSECONDS.sleep(end - System.nanoTime());
      running.set(false);
      for (Future<Void> future : futures) {
        future.get();
      }
    } finally {
      executor.shutdownNow();
    }
    assertTrue(count.get() > 0);
    return count.get();
  }

  private static void report(String operation, boolean fileCacheEnabled, int threadCount, double perSecond) {
    System.out.println(String.format(Locale.ROOT, "BENCHMARK|%s|cache=%s|threads=%d|%.1f/s",
        operation, fileCacheEnabled, threadCount, perSecond));
  }
}
