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

import java.security.SecureRandom;
import java.util.UUID;

public final class UuidV7 {
  private static final SecureRandom RANDOM = new SecureRandom();

  private UuidV7() {
  }

  public static UUID next() {
    return of(System.currentTimeMillis());
  }

  static UUID of(long epochMillis) {
    long randA = RANDOM.nextInt(1 << 12);
    long randB = RANDOM.nextLong();
    long msb = ((epochMillis & 0xFFFFFFFFFFFFL) << 16) | 0x7000L | randA;
    long lsb = (randB & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
    return new UUID(msb, lsb);
  }

  static long timestamp(UUID uuid) {
    return uuid.getMostSignificantBits() >>> 16;
  }
}
