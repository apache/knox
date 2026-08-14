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
package org.apache.knox.gateway.util.knoxidf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Verifies the {@link KnoxIDFArtifactStore#remove(String)} single-use invalidation added so an
 * artifact (e.g. an authorize/consent state) cannot be replayed within its TTL grace window.
 */
public class KnoxIDFArtifactStoreTest {

  /** Minimal concrete store; the base class is abstract. TTL is large so nothing expires mid-test. */
  private static final class TestStore extends KnoxIDFArtifactStore<String> {
    TestStore() {
      super(60_000L);
    }
  }

  @Test
  public void testPutThenGetReturnsValue() {
    final TestStore store = new TestStore();
    store.put("k", "v");
    assertEquals("v", store.get("k"));
  }

  @Test
  public void testRemoveInvalidatesEntry() {
    final TestStore store = new TestStore();
    store.put("state", "payload");
    store.remove("state");
    assertNull("A removed entry must not be retrievable (single-use replay guard).", store.get("state"));
  }

  @Test
  public void testRemoveIsIdempotentAndSafeForUnknownKey() {
    final TestStore store = new TestStore();
    store.remove("never-put"); // must not throw
    assertNull(store.get("never-put"));
  }

  @Test
  public void testGetUnknownKeyReturnsNull() {
    assertNull(new TestStore().get("missing"));
  }
}
