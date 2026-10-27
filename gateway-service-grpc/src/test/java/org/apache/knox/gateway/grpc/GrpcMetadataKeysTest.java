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
package org.apache.knox.gateway.grpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;

import org.junit.Test;

/**
 * The topology metadata key is named by configuration, because it appears
 * verbatim in the connection strings users write and need not advertise which
 * gateway is reading it.
 * <p>
 * gRPC is strict about header names, so a bad one is rejected at startup with an
 * explanation rather than surfacing later from deep inside the transport.
 */
public class GrpcMetadataKeysTest {

  @Test
  public void acceptsTheDefault() {
    assertEquals(GrpcMetadataKeys.DEFAULT_TOPOLOGY_KEY,
        GrpcMetadataKeys.topologyKey(GrpcMetadataKeys.DEFAULT_TOPOLOGY_KEY).name());
  }

  @Test
  public void acceptsANeutralNameThatDoesNotMentionTheGateway() {
    // The point of making this configurable.
    assertEquals("cluster", GrpcMetadataKeys.topologyKey("cluster").name());
    assertEquals("workspace", GrpcMetadataKeys.topologyKey("workspace").name());
    assertEquals("x-compute-target", GrpcMetadataKeys.topologyKey("x-compute-target").name());
  }

  @Test
  public void trimsSurroundingWhitespace() {
    assertEquals("cluster", GrpcMetadataKeys.topologyKey("  cluster  ").name());
  }

  @Test
  public void rejectsAnEmptyName() {
    assertRejected(null, "must not be empty");
    assertRejected("", "must not be empty");
    assertRejected("   ", "must not be empty");
  }

  @Test
  public void rejectsUpperCase() {
    // gRPC lower-cases header names, so an upper-case configuration value would
    // never match what arrives; say so rather than silently never matching.
    assertRejected("Knox-Topology", "lower case");
  }

  @Test
  public void rejectsCharactersGrpcDoesNotAllowInHeaderNames() {
    assertRejected("knox topology", "may contain only");
    assertRejected("knox:topology", "may contain only");
    assertRejected("knox/topology", "may contain only");
  }

  @Test
  public void rejectsTheBinarySuffixReservedByGrpc() {
    assertRejected("topology-bin", "-bin");
  }

  @Test
  public void rejectsCollidingWithTheBearerTokenHeader() {
    assertRejected("authorization", "authorization");
  }

  @Test
  public void theMissingTopologyNoticeIsDebugNotAWarning() throws Exception {
    // A listener enabled ahead of any backend is a legitimate steady state, and a
    // deployment that provisions clusters on demand would otherwise carry a
    // warning forever. Pinned so it cannot drift back to WARN unnoticed.
    final Message message = GrpcGatewayMessages.class
        .getMethod("noTopologyDeclaresService", String.class, String.class)
        .getAnnotation(Message.class);
    assertEquals(MessageLevel.DEBUG, message.level());
  }

  private static void assertRejected(String name, String expectedFragment) {
    try {
      GrpcMetadataKeys.topologyKey(name);
      fail("Expected " + name + " to be rejected");
    } catch (IllegalArgumentException e) {
      assertTrue("message should explain the problem, was: " + e.getMessage(),
          e.getMessage().contains(expectedFragment));
    }
  }
}
