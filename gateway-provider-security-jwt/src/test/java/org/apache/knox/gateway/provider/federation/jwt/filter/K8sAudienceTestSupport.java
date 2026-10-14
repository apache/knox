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

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.easymock.EasyMock;

/**
 * EasyMock fixtures shared by the K8sDestinationAudienceValidator test classes.
 */
final class K8sAudienceTestSupport {

  private K8sAudienceTestSupport() {
  }

  static FilterConfig filterConfig(final Map<String, String> params) {
    final FilterConfig filterConfig = EasyMock.createNiceMock(FilterConfig.class);
    for (final Map.Entry<String, String> entry : params.entrySet()) {
      EasyMock.expect(filterConfig.getInitParameter(entry.getKey())).andReturn(entry.getValue()).anyTimes();
    }
    EasyMock.replay(filterConfig);
    return filterConfig;
  }

  static HttpServletRequest requestWithHeaders(final Map<String, String> headers) {
    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    for (final Map.Entry<String, String> entry : headers.entrySet()) {
      EasyMock.expect(request.getHeader(entry.getKey())).andReturn(entry.getValue()).anyTimes();
    }
    EasyMock.replay(request);
    return request;
  }

  static JWT delegationToken(final String... audiences) {
    final JWT token = EasyMock.createNiceMock(JWT.class);
    EasyMock.expect(token.getClaimAsObject(JWTToken.ACT_CLAIM)).andReturn("delegate").anyTimes();
    EasyMock.expect(token.getAudienceClaims()).andReturn(audiences).anyTimes();
    EasyMock.replay(token);
    return token;
  }

  static JWT nonDelegationToken(final String... audiences) {
    final JWT token = EasyMock.createNiceMock(JWT.class);
    EasyMock.expect(token.getClaimAsObject(JWTToken.ACT_CLAIM)).andReturn(null).anyTimes();
    EasyMock.expect(token.getAudienceClaims()).andReturn(audiences).anyTimes();
    EasyMock.replay(token);
    return token;
  }

  /**
   * A delegation token whose {@code act} claim is {@code actClaim} verbatim, for exercising
   * {@code TokenUtils.extractActorChain} with chain shapes {@link #delegationToken} cannot
   * produce, including malformed ones (e.g. an actor map with no {@code sub}, or an {@code act}
   * claim that is not a map at all).
   */
  static JWT delegationTokenWithActClaim(final Object actClaim, final String... audiences) {
    final JWT token = EasyMock.createNiceMock(JWT.class);
    EasyMock.expect(token.getClaimAsObject(JWTToken.ACT_CLAIM)).andReturn(actClaim).anyTimes();
    EasyMock.expect(token.getAudienceClaims()).andReturn(audiences).anyTimes();
    EasyMock.replay(token);
    return token;
  }

  /**
   * Builds the nested {@code act} claim map {@code TokenUtils.extractActorChain} walks, from a
   * list of actor {@code sub} values ordered most-recent-first (index 0 becomes the token's own
   * {@code act} claim, i.e. the most recent actor).
   */
  static Map<String, Object> actorChain(final String... actorSubsMostRecentFirst) {
    Map<String, Object> chain = null;
    for (int i = actorSubsMostRecentFirst.length - 1; i >= 0; i--) {
      final Map<String, Object> actor = new LinkedHashMap<>();
      actor.put(JWT.SUBJECT, actorSubsMostRecentFirst[i]);
      if (chain != null) {
        actor.put(JWTToken.ACT_CLAIM, chain);
      }
      chain = actor;
    }
    return chain;
  }
}
