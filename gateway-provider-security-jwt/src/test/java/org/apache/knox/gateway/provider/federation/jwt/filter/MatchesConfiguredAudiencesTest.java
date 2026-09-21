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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.easymock.EasyMock;
import org.junit.Test;

public class MatchesConfiguredAudiencesTest {

  @Test
  public void testNoConfiguredAudiencesAlwaysValid() {
    final JWT jwtToken = EasyMock.createNiceMock(JWT.class);
    EasyMock.expect(jwtToken.getAudienceClaims()).andReturn(new String[]{"anything"}).anyTimes();
    EasyMock.replay(jwtToken);

    assertTrue(AbstractJWTFilter.matchesConfiguredAudiences(jwtToken, null));
  }

  @Test
  public void testMatchingTokenAudienceIsValid() {
    final JWT jwtToken = EasyMock.createNiceMock(JWT.class);
    EasyMock.expect(jwtToken.getAudienceClaims()).andReturn(new String[]{"foo", "bar"}).anyTimes();
    EasyMock.replay(jwtToken);

    final List<String> configuredAudiences = Arrays.asList("bar", "baz");
    assertTrue(AbstractJWTFilter.matchesConfiguredAudiences(jwtToken, configuredAudiences));
  }

  @Test
  public void testNonMatchingTokenAudienceAndNoNoneEscapeIsInvalid() {
    final JWT jwtToken = EasyMock.createNiceMock(JWT.class);
    EasyMock.expect(jwtToken.getAudienceClaims()).andReturn(new String[]{"foo"}).anyTimes();
    EasyMock.replay(jwtToken);

    final List<String> configuredAudiences = Collections.singletonList("bar");
    assertFalse(AbstractJWTFilter.matchesConfiguredAudiences(jwtToken, configuredAudiences));
  }

  @Test
  public void testNoTokenAudienceWithNoneConfiguredIsValid() {
    final JWT jwtToken = EasyMock.createNiceMock(JWT.class);
    EasyMock.expect(jwtToken.getAudienceClaims()).andReturn(null).anyTimes();
    EasyMock.replay(jwtToken);

    final List<String> configuredAudiences = Collections.singletonList("NONE");
    assertTrue(AbstractJWTFilter.matchesConfiguredAudiences(jwtToken, configuredAudiences));
  }

  @Test
  public void testNoTokenAudienceWithoutNoneConfiguredIsInvalid() {
    final JWT jwtToken = EasyMock.createNiceMock(JWT.class);
    EasyMock.expect(jwtToken.getAudienceClaims()).andReturn(null).anyTimes();
    EasyMock.replay(jwtToken);

    final List<String> configuredAudiences = Collections.singletonList("bar");
    assertFalse(AbstractJWTFilter.matchesConfiguredAudiences(jwtToken, configuredAudiences));
  }
}
