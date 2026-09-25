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
package org.apache.knox.gateway.provider.federation.jwt.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;
import org.junit.Test;

public class DestinationServiceNameTest {

  private static final String SUFFIX = ".svc.cluster.local";

  @Test
  public void testParseValid() {
    final Optional<DestinationServiceName> parsed = DestinationServiceName.parse("svc.ns.svc.cluster.local", SUFFIX);
    assertTrue(parsed.isPresent());
    assertEquals("svc", parsed.get().serviceName());
    assertEquals("ns", parsed.get().namespace());
  }

  @Test
  public void testParseValidWithTrailingPort() {
    final Optional<DestinationServiceName> parsed = DestinationServiceName.parse("svc.ns.svc.cluster.local:8443", SUFFIX);
    assertTrue(parsed.isPresent());
    assertEquals("svc", parsed.get().serviceName());
    assertEquals("ns", parsed.get().namespace());
  }

  @Test
  public void testServiceNameAndNamespaceLowercased() {
    final Optional<DestinationServiceName> parsed = DestinationServiceName.parse("SVC.NS.svc.cluster.local", SUFFIX);
    assertTrue(parsed.isPresent());
    assertEquals("svc", parsed.get().serviceName());
    assertEquals("ns", parsed.get().namespace());
  }

  @Test
  public void testWhitespacePaddedHeaderValueTrimmed() {
    final Optional<DestinationServiceName> parsed = DestinationServiceName.parse("  svc.ns.svc.cluster.local  ", SUFFIX);
    assertTrue(parsed.isPresent());
    assertEquals("svc", parsed.get().serviceName());
    assertEquals("ns", parsed.get().namespace());
  }

  @Test
  public void testRejectNonNumericPort() {
    assertFalse(DestinationServiceName.parse("svc.ns.svc.cluster.local:abc", SUFFIX).isPresent());
  }

  @Test
  public void testRejectEmptyPort() {
    assertFalse(DestinationServiceName.parse("svc.ns.svc.cluster.local:", SUFFIX).isPresent());
  }

  @Test
  public void testRejectNoSuffixAtAll() {
    assertFalse(DestinationServiceName.parse("svc.ns", SUFFIX).isPresent());
  }

  @Test
  public void testRejectMismatchedSuffix() {
    assertFalse(DestinationServiceName.parse("svc.ns.other.suffix", SUFFIX).isPresent());
  }

  @Test
  public void testRejectSuffixNotAtEnd() {
    assertFalse(DestinationServiceName.parse("svc.ns.svc.cluster.local.extra", SUFFIX).isPresent());
  }

  @Test
  public void testRejectTooManyLabelsBeforeSuffix() {
    assertFalse(DestinationServiceName.parse("a.b.svc.ns.svc.cluster.local", SUFFIX).isPresent());
  }

  @Test
  public void testRejectTooFewLabelsBeforeSuffix() {
    assertFalse(DestinationServiceName.parse("svc.svc.cluster.local", SUFFIX).isPresent());
  }

  @Test
  public void testRejectNullAndEmpty() {
    assertFalse(DestinationServiceName.parse(null, SUFFIX).isPresent());
    assertFalse(DestinationServiceName.parse("", SUFFIX).isPresent());
    assertFalse(DestinationServiceName.parse("svc.ns.svc.cluster.local", null).isPresent());
    assertFalse(DestinationServiceName.parse("svc.ns.svc.cluster.local", "").isPresent());
  }

  @Test
  public void testRejectEmptyLabel() {
    assertFalse(DestinationServiceName.parse(".ns.svc.cluster.local", SUFFIX).isPresent());
    assertFalse(DestinationServiceName.parse("svc..svc.cluster.local", SUFFIX).isPresent());
  }

  @Test
  public void testRejectInternalWhitespaceInLabel() {
    assertFalse(DestinationServiceName.parse("svc .ns.svc.cluster.local", SUFFIX).isPresent());
    assertFalse(DestinationServiceName.parse("svc.n s.svc.cluster.local", SUFFIX).isPresent());
  }
}
