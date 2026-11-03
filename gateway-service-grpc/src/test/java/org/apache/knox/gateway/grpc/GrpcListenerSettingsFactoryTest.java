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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.knox.gateway.GatewayTestConfig;
import org.junit.Before;
import org.junit.Test;

/**
 * How gateway configuration becomes one set of settings per listener.
 * <p>
 * The behaviour worth pinning down is the inheritance: a deployment writes what
 * every listener shares once, and repeats only what differs. Getting that wrong
 * in either direction is quiet — an override that does not apply, or a shared
 * value that silently does not reach a listener.
 */
public class GrpcListenerSettingsFactoryTest {

  private GatewayTestConfig config;

  @Before
  public void setUp() {
    config = new GatewayTestConfig();
    config.setGrpcServiceRole("SPARKCONNECT");
    config.setGrpcProtoServices("spark.connect.SparkConnectService");
    config.setGrpcIdentityRules("2.1=principal,2.2=principal");
  }

  private void listeners(String... names) {
    config.setGrpcListenerNames(Arrays.asList(names));
  }

  private void set(String listener, String property, String value) {
    final Map<String, String> existing =
        new HashMap<>(config.getGrpcListenerConfig(listener));
    existing.put(property, value);
    config.setGrpcListenerConfig(listener, existing);
  }

  @Test
  public void namingNoListenersYieldsOneFromThePlainProperties() {
    final List<GrpcListenerSettings> settings = GrpcListenerSettingsFactory.create(config);

    assertEquals(1, settings.size());
    // Named after its service role, so a single-listener deployment reads in the
    // log for the thing being fronted rather than for the transport.
    assertEquals("SPARKCONNECT", settings.get(0).getName());
    assertEquals("SPARKCONNECT", settings.get(0).getServiceRole());
    assertEquals("2.1=principal,2.2=principal", settings.get(0).getIdentityRules());
    assertNull("no listener configures its own keystore by default",
        settings.get(0).getSslKeystorePath());
  }

  @Test
  public void eachListenerInheritsWhatItDoesNotSet() {
    listeners("analytics", "partner");
    set("analytics", "port", "15002");
    set("partner", "port", "15003");

    final List<GrpcListenerSettings> settings = GrpcListenerSettingsFactory.create(config);

    assertEquals(2, settings.size());
    for (GrpcListenerSettings listener : settings) {
      assertEquals("the shared service role did not reach " + listener.getName(),
          "SPARKCONNECT", listener.getServiceRole());
      assertEquals("the shared identity rules did not reach " + listener.getName(),
          "2.1=principal,2.2=principal", listener.getIdentityRules());
    }
    assertEquals(15002, settings.get(0).getPort());
    assertEquals(15003, settings.get(1).getPort());
  }

  @Test
  public void aListenerOverridesWhatItSets() {
    listeners("analytics", "partner");
    set("analytics", "port", "15002");
    set("partner", "port", "15003");
    set("partner", "identity.rules", "3.1=principal");
    set("partner", "service.role", "OTHERGRPC");
    set("partner", "methods.deny", "AddArtifacts");

    final List<GrpcListenerSettings> settings = GrpcListenerSettingsFactory.create(config);

    assertEquals("2.1=principal,2.2=principal", settings.get(0).getIdentityRules());
    assertEquals("SPARKCONNECT", settings.get(0).getServiceRole());
    assertNull(settings.get(0).getMethodsDeny());

    assertEquals("3.1=principal", settings.get(1).getIdentityRules());
    assertEquals("OTHERGRPC", settings.get(1).getServiceRole());
    assertEquals("AddArtifacts", settings.get(1).getMethodsDeny());
  }

  @Test
  public void eachListenerCanPresentItsOwnCertificate() {
    // The reason several listeners exist: TLS identity is per socket, so serving
    // several hostnames without a multi-name certificate needs a socket each.
    listeners("analytics", "partner");
    set("analytics", "port", "15002");
    set("analytics", "ssl.keystore.path", "/opt/pki/analytics.p12");
    set("analytics", "ssl.keystore.alias", "analytics");
    set("partner", "port", "15003");
    set("partner", "ssl.keystore.path", "/opt/pki/partner.p12");
    set("partner", "ssl.keystore.password.alias", "partner_keystore_password");

    final List<GrpcListenerSettings> settings = GrpcListenerSettingsFactory.create(config);

    assertEquals("/opt/pki/analytics.p12", settings.get(0).getSslKeystorePath());
    assertEquals("analytics", settings.get(0).getSslKeystoreAlias());
    assertNull(settings.get(0).getSslKeystorePasswordAlias());

    assertEquals("/opt/pki/partner.p12", settings.get(1).getSslKeystorePath());
    assertNull("an alias is optional where the keystore holds one key entry",
        settings.get(1).getSslKeystoreAlias());
    assertEquals("partner_keystore_password", settings.get(1).getSslKeystorePasswordAlias());
  }

  @Test
  public void keystorePathsAreNeverInherited() {
    // Sharing one keystore across listeners would defeat the point of having
    // several, so this is the one property that has no plain-property fallback.
    listeners("analytics", "partner");
    set("analytics", "port", "15002");
    set("analytics", "ssl.keystore.path", "/opt/pki/analytics.p12");
    set("partner", "port", "15003");

    final List<GrpcListenerSettings> settings = GrpcListenerSettingsFactory.create(config);

    assertEquals("/opt/pki/analytics.p12", settings.get(0).getSslKeystorePath());
    assertNull("partner should fall back to the gateway identity, not analytics' keystore",
        settings.get(1).getSslKeystorePath());
  }

  @Test
  public void refusesTwoListenersOnOnePort() {
    // Otherwise whichever lost the race would fail with an address-in-use error
    // naming neither listener.
    listeners("analytics", "partner");
    set("analytics", "port", "15002");
    set("partner", "port", "15002");

    assertRejected("both configured on port 15002");
  }

  @Test
  public void refusesANameThatCollidesWithAPlainProperty() {
    // gateway.grpc.identity.rules and a listener called 'identity' would occupy
    // the same namespace.
    listeners("identity");
    assertRejected("already a configuration property");
  }

  @Test
  public void refusesUnusableAndDuplicateNames() {
    listeners("Analytics");
    assertRejected("may contain only");

    listeners("analytics", "analytics");
    assertRejected("Duplicate");
  }

  @Test
  public void refusesANonNumericNumericProperty() {
    listeners("analytics");
    set("analytics", "port", "not-a-port");
    assertRejected("must be a number");
  }

  private void assertRejected(String expectedFragment) {
    try {
      GrpcListenerSettingsFactory.create(config);
      fail("Expected the configuration to be rejected");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage(), e.getMessage().contains(expectedFragment));
    }
  }
}
