/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServiceAccountSubjectTest {
  @Test
  public void testParseValidSubject() {
    Optional<ServiceAccountSubject> parsed = ServiceAccountSubject.parse("system:serviceaccount:demo:demo-app");
    assertTrue(parsed.isPresent());
    assertEquals("demo", parsed.get().namespace());
    assertEquals("demo-app", parsed.get().name());
  }

  @Test
  public void testRejectNull() {
    assertFalse(ServiceAccountSubject.parse(null).isPresent());
  }

  @Test
  public void testRejectWrongPrefix() {
    assertFalse(ServiceAccountSubject.parse("user:demo").isPresent());
    assertFalse(ServiceAccountSubject.parse("").isPresent());
  }

  @Test
  public void testRejectMissingSeparator() {
    assertFalse(ServiceAccountSubject.parse("system:serviceaccount:demo-app").isPresent());
  }

  @Test
  public void testRejectEmptyNamespaceOrName() {
    assertFalse(ServiceAccountSubject.parse("system:serviceaccount::demo-app").isPresent());
    assertFalse(ServiceAccountSubject.parse("system:serviceaccount:demo:").isPresent());
  }

  @Test
  public void testRejectExtraColonInName() {
    assertFalse(ServiceAccountSubject.parse("system:serviceaccount:demo:demo-app:extra").isPresent());
  }

  @Test
  public void testPrefixOnlyIsRejected() {
    assertFalse(ServiceAccountSubject.parse("system:serviceaccount:").isPresent());
  }
}
