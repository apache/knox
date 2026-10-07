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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AudienceValidationResultTest {

  @Test
  public void testConstructorWithMessage() {
    final AudienceValidationResult result = new AudienceValidationResult(false, "reason");
    assertFalse(result.isValid());
    assertEquals("reason", result.message());
  }

  @Test
  public void testOfValid() {
    final AudienceValidationResult result = AudienceValidationResult.of(true);
    assertTrue(result.isValid());
    assertNull(result.message());
  }

  @Test
  public void testOfInvalid() {
    final AudienceValidationResult result = AudienceValidationResult.of(false);
    assertFalse(result.isValid());
    assertNull(result.message());
  }
}
