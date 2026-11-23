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
package org.apache.knox.gateway.services.knoxidf.flowstate;

import org.apache.knox.gateway.config.GatewayConfig;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InMemoryAuthzFlowStateServiceTest {

  private static final int TTL_SECS = 60;
  private static final int CLEANUP_INTERVAL_SECS = 10;

  private MutableClock clock;
  private InMemoryAuthzFlowStateService service;

  @Before
  public void setUp() {
    clock = new MutableClock(1_000_000L);
    service = new InMemoryAuthzFlowStateService(clock);
    final GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.getKnoxIDFAuthzFlowStateTtlSecs()).andReturn(TTL_SECS).anyTimes();
    EasyMock.expect(config.getKnoxIDFAuthzFlowStateCleanupIntervalSecs()).andReturn(CLEANUP_INTERVAL_SECS).anyTimes();
    EasyMock.replay(config);
    service.init(config, Collections.emptyMap());
  }

  @Test
  public void testPutAndGet() {
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id", "payload");
    assertEquals(Optional.of("payload"), service.get(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id"));
    assertEquals(Optional.of("payload"), service.get(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id"));
  }

  @Test
  public void testKindsAreSeparate() {
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id", "request");
    service.put(AuthzFlowStateKind.FEDERATED_OP_SELECTION, "id", "selection");
    assertEquals(Optional.of("request"), service.get(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id"));
    assertEquals(Optional.of("selection"), service.get(AuthzFlowStateKind.FEDERATED_OP_SELECTION, "id"));
    assertFalse(service.get(AuthzFlowStateKind.FEDERATED_OP_OPTIONS, "id").isPresent());
  }

  @Test
  public void testPutReplacesAndRestartsTtl() {
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id", "first");
    clock.advanceMillis(TimeUnit.SECONDS.toMillis(TTL_SECS - 1));
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id", "second");
    clock.advanceMillis(TimeUnit.SECONDS.toMillis(TTL_SECS - 1));
    assertEquals(Optional.of("second"), service.get(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id"));
  }

  @Test
  public void testExpiredEntryIsAbsent() {
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id", "payload");
    clock.advanceMillis(TimeUnit.SECONDS.toMillis(TTL_SECS));
    assertFalse(service.get(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id").isPresent());
    assertFalse(service.consume(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id").isPresent());
  }

  @Test
  public void testConsumeReturnsPayloadOnce() {
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id", "payload");
    assertEquals(Optional.of("payload"), service.consume(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id"));
    assertFalse(service.consume(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id").isPresent());
    assertFalse(service.get(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id").isPresent());
  }

  @Test
  public void testConcurrentConsumeSucceedsOnce() throws Exception {
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id", "payload");
    final int threads = 8;
    final ExecutorService executor = Executors.newFixedThreadPool(threads);
    try {
      final List<Callable<Optional<String>>> tasks = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        tasks.add(() -> service.consume(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id"));
      }
      int winners = 0;
      for (Future<Optional<String>> result : executor.invokeAll(tasks)) {
        if (result.get().isPresent()) {
          winners++;
        }
      }
      assertEquals(1, winners);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void testRemove() {
    service.put(AuthzFlowStateKind.FEDERATED_OP_OPTIONS, "id", "payload");
    service.remove(AuthzFlowStateKind.FEDERATED_OP_OPTIONS, "id");
    assertFalse(service.get(AuthzFlowStateKind.FEDERATED_OP_OPTIONS, "id").isPresent());
    service.remove(AuthzFlowStateKind.FEDERATED_OP_OPTIONS, "missing");
  }

  @Test
  public void testNullIdIsAbsent() {
    assertFalse(service.get(AuthzFlowStateKind.AUTHORIZE_REQUEST, null).isPresent());
    assertFalse(service.consume(AuthzFlowStateKind.AUTHORIZE_REQUEST, null).isPresent());
    service.remove(AuthzFlowStateKind.AUTHORIZE_REQUEST, null);
  }

  @Test
  public void testRemoveExpired() {
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "old", "payload");
    clock.advanceMillis(TimeUnit.SECONDS.toMillis(TTL_SECS / 2));
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "new", "payload");
    clock.advanceMillis(TimeUnit.SECONDS.toMillis(TTL_SECS / 2));
    assertEquals(1, service.removeExpired());
    assertEquals(1, service.size());
    assertTrue(service.get(AuthzFlowStateKind.AUTHORIZE_REQUEST, "new").isPresent());
  }

  @Test
  public void testPutSweepsExpiredEntriesAfterCleanupInterval() {
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "old", "payload");
    clock.advanceMillis(TimeUnit.SECONDS.toMillis(TTL_SECS));
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "new", "payload");
    assertEquals(1, service.size());
  }

  @Test
  public void testStopClearsState() {
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id", "payload");
    service.stop();
    assertEquals(0, service.size());
  }
}
