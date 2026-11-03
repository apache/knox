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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.easymock.EasyMock;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class PolicyBoundSubjectAudienceValidatorTest {

  private static final String KNOX_ISSUER = "https://knox/gateway/knoxidf-tokenexchange";
  private static final List<String> CONFIGURED = Collections.singletonList("knoxidf");

  @Test
  public void testDelegatePassReturnsDelegateResultWithoutPolicyMatch() {
    final AudienceValidationResult pass = AudienceValidationResult.of(true);
    final PolicyBoundSubjectAudienceValidator validator =
        new PolicyBoundSubjectAudienceValidator((r, t, a) -> pass, Set.of(KNOX_ISSUER));

    assertSame(pass, validator.validate(null, token(KNOX_ISSUER), CONFIGURED));
    assertFalse(validator.isPolicyAudienceMatchRequired());
  }

  @Test
  public void testDelegateFailWithListedIssuerAcceptedProvisionally() {
    final PolicyBoundSubjectAudienceValidator validator = new PolicyBoundSubjectAudienceValidator(
        (r, t, a) -> new AudienceValidationResult(false, "missing aud"), Set.of(KNOX_ISSUER));

    assertTrue(validator.validate(null, token(KNOX_ISSUER), CONFIGURED).isValid());
    assertTrue(validator.isPolicyAudienceMatchRequired());
  }

  @Test
  public void testDelegateFailWithUnlistedIssuerReturnsDelegateFailure() {
    final AudienceValidationResult fail = new AudienceValidationResult(false, "missing aud");
    final PolicyBoundSubjectAudienceValidator validator =
        new PolicyBoundSubjectAudienceValidator((r, t, a) -> fail, Set.of(KNOX_ISSUER));

    assertSame(fail, validator.validate(null, token("https://kubernetes.default.svc"), CONFIGURED));
    assertFalse(validator.isPolicyAudienceMatchRequired());
  }

  @Test
  public void testDelegateFailWithNullIssuerReturnsDelegateFailure() {
    final AudienceValidationResult fail = AudienceValidationResult.of(false);
    final PolicyBoundSubjectAudienceValidator validator =
        new PolicyBoundSubjectAudienceValidator((r, t, a) -> fail, Set.of(KNOX_ISSUER));

    assertSame(fail, validator.validate(null, token(null), CONFIGURED));
    assertFalse(validator.isPolicyAudienceMatchRequired());
  }

  @Test
  public void testDelegateReceivesConfiguredAudiences() {
    final AtomicReference<List<String>> seen = new AtomicReference<>();
    final PolicyBoundSubjectAudienceValidator validator = new PolicyBoundSubjectAudienceValidator(
        (r, t, a) -> {
          seen.set(a);
          return AudienceValidationResult.of(true);
        }, Set.of(KNOX_ISSUER));

    validator.validate(null, token(KNOX_ISSUER), CONFIGURED);
    assertEquals(CONFIGURED, seen.get());
  }

  @Test
  public void testFlagResetOnEachValidation() {
    final boolean[] pass = {false};
    final PolicyBoundSubjectAudienceValidator validator = new PolicyBoundSubjectAudienceValidator(
        (r, t, a) -> AudienceValidationResult.of(pass[0]), Set.of(KNOX_ISSUER));

    validator.validate(null, token(KNOX_ISSUER), CONFIGURED);
    assertTrue(validator.isPolicyAudienceMatchRequired());

    pass[0] = true;
    validator.validate(null, token(KNOX_ISSUER), CONFIGURED);
    assertFalse(validator.isPolicyAudienceMatchRequired());
  }

  private static JWT token(String issuer) {
    final JWT token = EasyMock.createNiceMock(JWT.class);
    EasyMock.expect(token.getIssuer()).andReturn(issuer).anyTimes();
    EasyMock.replay(token);
    return token;
  }
}
