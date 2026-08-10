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
package org.apache.knox.gateway.util.knoxidf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import javax.servlet.ServletContext;

import org.easymock.EasyMock;
import org.junit.Test;

public class FederatedOpConfigurationTest {

  private static final String OP = "keycloak";
  private static final String PREFIX = KnoxIDFConstants.FEDERATED_OP_CONFIG_PREFIX + OP + ".";

  @Test
  public void testIdTokenVerificationParamsAreRead() {
    final ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameter(PREFIX + "jwks.endpoint")).andReturn("https://op.example/jwks").anyTimes();
    EasyMock.expect(context.getInitParameter(PREFIX + "issuer")).andReturn("https://op.example/realms/knox").anyTimes();
    EasyMock.expect(context.getInitParameter(PREFIX + "signature.algorithm")).andReturn("RS512").anyTimes();
    EasyMock.expect(context.getInitParameter(PREFIX + "clientId")).andReturn("knox-client").anyTimes();
    EasyMock.replay(context);

    final FederatedOpConfiguration config = new FederatedOpConfiguration(context, OP);

    assertEquals("https://op.example/jwks", config.getJwksEndpoint());
    assertEquals("https://op.example/realms/knox", config.getIssuer());
    assertEquals("RS512", config.getSignatureAlgorithm());
    assertEquals("knox-client", config.getClientId());
  }

  @Test
  public void testSignatureAlgorithmDefaultsToRS256() {
    final ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    // No signature.algorithm configured -> the default (RS256) must be used.
    EasyMock.expect(context.getInitParameter(PREFIX + "signature.algorithm")).andReturn(null).anyTimes();
    EasyMock.replay(context);

    final FederatedOpConfiguration config = new FederatedOpConfiguration(context, OP);

    assertEquals(FederatedOpConfiguration.DEFAULT_SIGNATURE_ALGORITHM, config.getSignatureAlgorithm());
    assertEquals("RS256", config.getSignatureAlgorithm());
  }

  @Test
  public void testVerificationParamsAbsentByDefault() {
    final ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.replay(context);

    final FederatedOpConfiguration config = new FederatedOpConfiguration(context, OP);

    // When nothing is configured the id_token verification inputs are null, which makes the
    // federated login fail closed in AuthorizeResource#validateFederatedIdToken.
    assertNull(config.getJwksEndpoint());
    assertNull(config.getIssuer());
  }
}
