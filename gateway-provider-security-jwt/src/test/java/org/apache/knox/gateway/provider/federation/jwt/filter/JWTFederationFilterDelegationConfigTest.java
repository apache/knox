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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.apache.knox.gateway.provider.federation.TestFilterConfig;
import org.apache.knox.gateway.services.knoxidf.delegation.DelegationPolicyService;
import org.apache.knox.gateway.services.knoxidf.delegation.PolicyCheckRequest;
import org.apache.knox.gateway.services.knoxidf.delegation.PolicyDecision;
import org.easymock.EasyMock;
import org.junit.Test;

import jakarta.servlet.ServletException;

import java.util.Collections;
import java.util.Properties;

public class JWTFederationFilterDelegationConfigTest {

  @Test
  public void testDelegationFlagsDefaultToFalse() throws ServletException {
    final JWTFederationFilter filter = new JWTFederationFilter();
    filter.init(new TestFilterConfig(new Properties()));

    assertFalse(filter.isDelegationServerEnabled());
    assertFalse(filter.isDelegationRequestedSubjectEnabled());
    assertFalse(filter.isDelegationEnforceRequestedAudienceRequired());
    assertFalse(filter.isDelegationEnforceRequestedAudienceMaxOne());
    assertFalse(filter.isDelegationSameSubjectRequestedAudienceEnabled());
  }

  @Test
  public void testDelegationServerEnabledIsIndependentlyConfigurable() throws ServletException {
    final Properties props = new Properties();
    props.setProperty(JWTFederationFilter.DELEGATION_SERVER_ENABLED, "true");
    final JWTFederationFilter filter = new JWTFederationFilter();
    filter.init(new TestFilterConfig(props));

    assertTrue(filter.isDelegationServerEnabled());
    assertFalse(filter.isDelegationRequestedSubjectEnabled());
    assertFalse(filter.isDelegationEnforceRequestedAudienceRequired());
    assertFalse(filter.isDelegationEnforceRequestedAudienceMaxOne());
  }

  @Test
  public void testDelegationRequestedSubjectEnabledIsIndependentlyConfigurable() throws ServletException {
    final Properties props = new Properties();
    props.setProperty(JWTFederationFilter.DELEGATION_REQUESTED_SUBJECT_ENABLED, "true");
    final JWTFederationFilter filter = new JWTFederationFilter();
    filter.init(new TestFilterConfig(props));

    assertFalse(filter.isDelegationServerEnabled());
    assertTrue(filter.isDelegationRequestedSubjectEnabled());
    assertFalse(filter.isDelegationEnforceRequestedAudienceRequired());
    assertFalse(filter.isDelegationEnforceRequestedAudienceMaxOne());
  }

  @Test
  public void testDelegationEnforceRequestedAudienceRequiredIsIndependentlyConfigurable() throws ServletException {
    final Properties props = new Properties();
    props.setProperty(JWTFederationFilter.DELEGATION_ENFORCE_REQUESTED_AUDIENCE_REQUIRED, "true");
    final JWTFederationFilter filter = new JWTFederationFilter();
    filter.init(new TestFilterConfig(props));

    assertFalse(filter.isDelegationServerEnabled());
    assertFalse(filter.isDelegationRequestedSubjectEnabled());
    assertTrue(filter.isDelegationEnforceRequestedAudienceRequired());
    assertFalse(filter.isDelegationEnforceRequestedAudienceMaxOne());
  }

  @Test
  public void testDelegationEnforceRequestedAudienceMaxOneIsIndependentlyConfigurable() throws ServletException {
    final Properties props = new Properties();
    props.setProperty(JWTFederationFilter.DELEGATION_ENFORCE_REQUESTED_AUDIENCE_MAX_ONE, "true");
    final JWTFederationFilter filter = new JWTFederationFilter();
    filter.init(new TestFilterConfig(props));

    assertFalse(filter.isDelegationServerEnabled());
    assertFalse(filter.isDelegationRequestedSubjectEnabled());
    assertFalse(filter.isDelegationEnforceRequestedAudienceRequired());
    assertTrue(filter.isDelegationEnforceRequestedAudienceMaxOne());
    assertFalse(filter.isDelegationSameSubjectRequestedAudienceEnabled());
  }

  @Test
  public void testDelegationSameSubjectRequestedAudienceEnabledIsIndependentlyConfigurable() throws ServletException {
    final Properties props = new Properties();
    props.setProperty(JWTFederationFilter.DELEGATION_SAME_SUBJECT_REQUESTED_AUDIENCE_ENABLED, "true");
    final JWTFederationFilter filter = new JWTFederationFilter();
    filter.init(new TestFilterConfig(props));

    assertFalse(filter.isDelegationServerEnabled());
    assertFalse(filter.isDelegationRequestedSubjectEnabled());
    assertFalse(filter.isDelegationEnforceRequestedAudienceRequired());
    assertFalse(filter.isDelegationEnforceRequestedAudienceMaxOne());
    assertTrue(filter.isDelegationSameSubjectRequestedAudienceEnabled());
  }

  @Test
  public void testAllDelegationFlagsTrueWhenConfigured() throws ServletException {
    final Properties props = new Properties();
    props.setProperty(JWTFederationFilter.DELEGATION_SERVER_ENABLED, "true");
    props.setProperty(JWTFederationFilter.DELEGATION_REQUESTED_SUBJECT_ENABLED, "true");
    props.setProperty(JWTFederationFilter.DELEGATION_ENFORCE_REQUESTED_AUDIENCE_REQUIRED, "true");
    props.setProperty(JWTFederationFilter.DELEGATION_ENFORCE_REQUESTED_AUDIENCE_MAX_ONE, "true");
    props.setProperty(JWTFederationFilter.DELEGATION_SAME_SUBJECT_REQUESTED_AUDIENCE_ENABLED, "true");
    final JWTFederationFilter filter = new JWTFederationFilter();
    filter.init(new TestFilterConfig(props));

    assertTrue(filter.isDelegationServerEnabled());
    assertTrue(filter.isDelegationRequestedSubjectEnabled());
    assertTrue(filter.isDelegationEnforceRequestedAudienceRequired());
    assertTrue(filter.isDelegationEnforceRequestedAudienceMaxOne());
    assertTrue(filter.isDelegationSameSubjectRequestedAudienceEnabled());
  }

  @Test
  public void testEvaluateDelegationPolicyDelegatesToResolvedService() throws ServletException {
    final PolicyCheckRequest request = new PolicyCheckRequest("authority", "actorId", "subject",
        Collections.emptySet(), Collections.emptySet(), false);
    final PolicyDecision decision = new PolicyDecision(null, 3600);

    final DelegationPolicyService mockService = EasyMock.createMock(DelegationPolicyService.class);
    EasyMock.expect(mockService.evaluate(request)).andReturn(decision).once();
    EasyMock.replay(mockService);

    final JWTFederationFilter filter = new JWTFederationFilter();
    filter.init(new TestFilterConfig(new Properties(), null, mockService));

    final PolicyDecision result = filter.evaluateDelegationPolicy(request);

    assertSame(decision, result);
    EasyMock.verify(mockService);
  }
}
