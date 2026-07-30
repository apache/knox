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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;

import com.google.protobuf.Message;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.apache.spark.connect.proto.AddArtifactsRequest;

import org.junit.Test;

public class AddArtifactsGuardTest {

  private static final Message REQUEST = AddArtifactsRequest.getDefaultInstance();

  @Test
  public void allowModePermitsEveryone() {
    new AddArtifactsGuard(AddArtifactsGuard.MODE_ALLOW, Collections.emptyList()).check(REQUEST, "alice");
  }

  @Test
  public void defaultsToAllowWhenUnconfigured() {
    new AddArtifactsGuard(null, null).check(REQUEST, "alice");
  }

  @Test
  public void denyModeRejectsEveryone() {
    final AddArtifactsGuard guard =
        new AddArtifactsGuard(AddArtifactsGuard.MODE_DENY, Arrays.asList("alice"));
    assertTrue(guard.deniesEveryone());
    // Even a listed user is refused: DENY is not "deny except the list".
    assertDenied(guard, "alice");
  }

  @Test
  public void listedUsersModeAdmitsOnlyListedUsers() {
    final AddArtifactsGuard guard = new AddArtifactsGuard(
        AddArtifactsGuard.MODE_ALLOW_LISTED_USERS, Arrays.asList("alice", "bob"));
    assertFalse(guard.deniesEveryone());
    guard.check(REQUEST, "alice");
    guard.check(REQUEST, "bob");
    assertDenied(guard, "mallory");
  }

  @Test
  public void modeIsCaseAndWhitespaceInsensitive() {
    new AddArtifactsGuard("  allow  ", Collections.emptyList()).check(REQUEST, "alice");
  }

  @Test
  public void unrecognisedModeFailsClosed() {
    // A typo in configuration must not silently become "allow everyone".
    assertDenied(new AddArtifactsGuard("permissive", Collections.emptyList()), "alice");
  }

  private static void assertDenied(AddArtifactsGuard guard, String principal) {
    try {
      guard.check(REQUEST, principal);
      fail("Expected artifact upload to be denied for " + principal);
    } catch (StatusRuntimeException e) {
      assertEquals(Status.Code.PERMISSION_DENIED, e.getStatus().getCode());
    }
  }
}
