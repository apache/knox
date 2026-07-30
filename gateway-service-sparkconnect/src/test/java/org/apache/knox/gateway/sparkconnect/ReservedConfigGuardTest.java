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
package org.apache.knox.gateway.sparkconnect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.apache.spark.connect.proto.ConfigRequest;
import org.apache.spark.connect.proto.KeyValue;

import org.junit.Test;

public class ReservedConfigGuardTest {

  private static final String PREFIX = "knox.";
  private static final String USER = "alice";

  private final ReservedConfigGuard guard = new ReservedConfigGuard(PREFIX);

  @Test
  public void deniesSettingAReservedKey() {
    // If a client could overwrite the key Knox publishes the identity into, it
    // could assume any identity it liked.
    assertDenied(configSet("knox.principal", "root"));
  }

  @Test
  public void deniesSettingAReservedKeyRegardlessOfCase() {
    assertDenied(configSet("KNOX.Principal", "root"));
  }

  @Test
  public void deniesUnsettingAReservedKey() {
    // Clearing the key is as good as overwriting it if downstream code then
    // falls back to something less trustworthy.
    assertDenied(ConfigRequest.newBuilder()
        .setOperation(ConfigRequest.Operation.newBuilder()
            .setUnset(ConfigRequest.Unset.newBuilder().addKeys("knox.principal")))
        .build());
  }

  @Test
  public void deniesWhenAReservedKeyIsBuriedAmongAllowedOnes() {
    assertDenied(ConfigRequest.newBuilder()
        .setOperation(ConfigRequest.Operation.newBuilder()
            .setSet(ConfigRequest.Set.newBuilder()
                .addPairs(KeyValue.newBuilder().setKey("spark.sql.shuffle.partitions").setValue("8"))
                .addPairs(KeyValue.newBuilder().setKey("knox.principal").setValue("root"))))
        .build());
  }

  @Test
  public void allowsOrdinarySparkSettings() {
    guard.check(configSet("spark.sql.shuffle.partitions", "8"), USER);
  }

  @Test
  public void allowsReadingAReservedKey() {
    // Reading is harmless; only writes can forge an identity.
    guard.check(ConfigRequest.newBuilder()
        .setOperation(ConfigRequest.Operation.newBuilder()
            .setGet(ConfigRequest.Get.newBuilder().addKeys("knox.principal")))
        .build(), USER);
  }

  @Test
  public void allowsRequestsWithNoConfigOperation() {
    guard.check(ConfigRequest.getDefaultInstance(), USER);
  }

  @Test
  public void doesNothingWhenNoPrefixIsReserved() {
    new ReservedConfigGuard("").check(configSet("knox.principal", "root"), USER);
  }

  private void assertDenied(ConfigRequest request) {
    try {
      guard.check(request, USER);
      fail("Expected the reserved key write to be denied");
    } catch (StatusRuntimeException e) {
      assertEquals(Status.Code.PERMISSION_DENIED, e.getStatus().getCode());
    }
  }

  private static ConfigRequest configSet(String key, String value) {
    return ConfigRequest.newBuilder()
        .setOperation(ConfigRequest.Operation.newBuilder()
            .setSet(ConfigRequest.Set.newBuilder()
                .addPairs(KeyValue.newBuilder().setKey(key).setValue(value))))
        .build();
  }
}
