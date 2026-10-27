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
package org.apache.knox.gateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.protocol.ProtocolListener;
import org.apache.knox.gateway.services.GatewayServices;

import org.junit.Test;

/**
 * Whether a protocol listener runs is decided once, at startup, and cannot change
 * without a restart. Toggling the property in a running gateway therefore does
 * nothing — so it has to say so, or an operator has no way to tell the edit was
 * not applied.
 */
public class ProtocolListenerEnablementTest {

  @Test
  public void reportsAListenerSwitchedOffWhileRunning() {
    final StubListener running = new StubListener("SparkConnect", false);

    final List<String> reported = GatewayServer.warnAboutEnablementChanges(
        config(), Collections.singletonList(running), Collections.emptyList());

    assertEquals(Collections.singletonList("SparkConnect"), reported);
  }

  @Test
  public void reportsAListenerSwitchedOnWhileStopped() {
    // The likelier mistake: an operator sets enabled=true, expects a listener,
    // and gets silence. Nothing else in the gateway would mention it.
    final StubListener inactive = new StubListener("SparkConnect", true);

    final List<String> reported = GatewayServer.warnAboutEnablementChanges(
        config(), Collections.emptyList(), Collections.singletonList(inactive));

    assertEquals(Collections.singletonList("SparkConnect"), reported);
  }

  @Test
  public void staysQuietWhenEnablementIsUnchanged() {
    final StubListener running = new StubListener("SparkConnect", true);
    final StubListener inactive = new StubListener("Other", false);

    final List<String> reported = GatewayServer.warnAboutEnablementChanges(
        config(), Collections.singletonList(running), Collections.singletonList(inactive));

    assertTrue("no warning is due when nothing changed", reported.isEmpty());
  }

  @Test
  public void reportsEachChangedListenerSeparately() {
    final List<ProtocolListener> running =
        Arrays.asList(new StubListener("A", false), new StubListener("B", true));
    final List<ProtocolListener> inactive =
        Arrays.asList(new StubListener("C", true), new StubListener("D", false));

    final List<String> reported =
        GatewayServer.warnAboutEnablementChanges(config(), running, inactive);

    // A was switched off, C was switched on; B and D are unchanged.
    assertEquals(Arrays.asList("A", "C"), reported);
  }

  private static GatewayConfig config() {
    return new GatewayTestConfig();
  }

  /** A listener that reports a fixed enablement, standing in for the config read. */
  private static final class StubListener implements ProtocolListener {

    private final String name;
    private final boolean enabled;

    StubListener(String name, boolean enabled) {
      this.name = name;
      this.enabled = enabled;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public boolean isEnabled(GatewayConfig config) {
      return enabled;
    }

    @Override
    public void start(GatewayConfig config, GatewayServices services) {
    }

    @Override
    public void stop() {
    }

    @Override
    public int getPort() {
      return -1;
    }
  }
}
