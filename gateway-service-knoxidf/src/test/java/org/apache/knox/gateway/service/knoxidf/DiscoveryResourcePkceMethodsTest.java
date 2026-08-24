/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.service.knoxidf;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URI;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import org.easymock.EasyMock;
import org.junit.Test;

/**
 * Verifies discovery advertises only the PKCE methods it actually honors (review finding M8).
 * AuthorizeResource rejects any code_challenge_method other than S256, so the discovery document
 * must not list "plain" -- a client that trusts discovery and sends plain would be rejected at
 * /authorize.
 */
public class DiscoveryResourcePkceMethodsTest {

  @Test
  public void testCodeChallengeMethodsAdvertisesOnlyS256() {
    final UriInfo uriInfo = EasyMock.createNiceMock(UriInfo.class);
    EasyMock.expect(uriInfo.getBaseUri()).andReturn(URI.create("https://knox:8443/gateway/knoxidf/")).anyTimes();
    EasyMock.replay(uriInfo);

    final Response response = new DiscoveryResource().getConfig(uriInfo);
    final String body = String.valueOf(response.getEntity());

    assertTrue("Discovery must advertise S256 PKCE support.",
        body.contains("code_challenge_methods_supported") && body.contains("S256"));
    assertFalse("Discovery must not advertise 'plain' PKCE, which /authorize rejects.",
        body.contains("plain"));
  }
}
