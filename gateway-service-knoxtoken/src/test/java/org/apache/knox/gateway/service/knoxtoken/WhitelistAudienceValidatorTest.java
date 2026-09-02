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
package org.apache.knox.gateway.service.knoxtoken;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class WhitelistAudienceValidatorTest {

  private final WhitelistAudienceValidator validator = new WhitelistAudienceValidator();

  @Test
  public void noRequestedAudienceReturnsConfigured() throws Exception {
    final List<String> configured = Arrays.asList("a", "b");
    final List<String> result = validator.validateAndResolve(new AudienceValidationContext(Collections.emptyList(), configured));
    assertSame(configured, result);
  }

  @Test
  public void requestedSubsetIsHonored() throws Exception {
    final List<String> result = validator.validateAndResolve(
        new AudienceValidationContext(Collections.singletonList("a"), Arrays.asList("a", "b")));
    assertEquals(Collections.singletonList("a"), result);
  }

  @Test
  public void requestedValueNotInWhitelistIsRejected() {
    try {
      validator.validateAndResolve(new AudienceValidationContext(Arrays.asList("a", "intruder"), Arrays.asList("a", "b")));
      fail("Expected RequestedAudienceValidationException for a non-whitelisted audience");
    } catch (RequestedAudienceValidationException e) {
      assertEquals(TokenResource.ErrorCode.INVALID_AUDIENCE, e.getErrorCode());
    }
  }

  @Test
  public void requestedAudienceWithNoWhitelistIsRejected() {
    try {
      validator.validateAndResolve(new AudienceValidationContext(Collections.singletonList("a"), Collections.emptyList()));
      fail("Expected RequestedAudienceValidationException when no whitelist is configured");
    } catch (RequestedAudienceValidationException e) {
      assertEquals(TokenResource.ErrorCode.INVALID_AUDIENCE, e.getErrorCode());
    }
  }

  @Test
  public void requiresConfiguredAudiences() {
    assertTrue(validator.requiresConfiguredAudiences());
  }

  @Test
  public void forNameDefaultsToStatic() {
    assertTrue(AudienceValidator.forName(null) instanceof StaticAudienceValidator);
    assertTrue(AudienceValidator.forName("  ") instanceof StaticAudienceValidator);
    assertTrue(AudienceValidator.forName("static") instanceof StaticAudienceValidator);
    assertTrue(AudienceValidator.forName("whitelist") instanceof WhitelistAudienceValidator);
    assertTrue(AudienceValidator.forName("passthrough") instanceof PassthroughAudienceValidator);
  }

  @Test(expected = IllegalArgumentException.class)
  public void forNameRejectsUnknownValidator() {
    AudienceValidator.forName("nope");
  }
}
