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
package org.apache.knox.gateway.audit.log4j.appender;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.Test;

public class UuidV7Test {
  @Test
  public void testFormat() {
    for (int i = 0; i < 1000; i++) {
      UUID uuid = UuidV7.next();
      assertEquals(7, uuid.version());
      assertEquals(2, uuid.variant());
      assertTrue(uuid.toString().matches(AuditEventMapperTest.UUID_V7));
    }
  }

  @Test
  public void testTimestamp() {
    long before = System.currentTimeMillis();
    UUID uuid = UuidV7.next();
    long after = System.currentTimeMillis();
    long ts = UuidV7.timestamp(uuid);
    assertTrue(ts >= before && ts <= after);
    assertEquals(1_763_380_800_123L, UuidV7.timestamp(UuidV7.of(1_763_380_800_123L)));
  }

  @Test
  public void testUnique() {
    Set<UUID> seen = new HashSet<>();
    for (int i = 0; i < 10_000; i++) {
      assertTrue(seen.add(UuidV7.next()));
    }
  }
}
