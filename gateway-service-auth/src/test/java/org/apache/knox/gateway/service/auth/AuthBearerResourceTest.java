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
package org.apache.knox.gateway.service.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;

import org.easymock.EasyMock;
import org.junit.Rule;
import org.junit.Test;

import uk.org.webcompere.systemstubs.rules.EnvironmentVariablesRule;

public class AuthBearerResourceTest {

  @Rule
  public EnvironmentVariablesRule environmentVariablesRule = new EnvironmentVariablesRule();

  private static final String CUSTOM_TOKEN_ENV_VARIABLE = "MY_BEARER_TOKEN_ENV";
  private static final String TOKEN = "TestBearerToken";

  private ServletContext context;
  private HttpServletResponse response;

  private void configureCommonExpectations(String bearerTokenEnvVariableName, boolean expectHeader) {
    context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameter(AuthBearerResource.BEARER_AUTH_TOKEN_ENV_CONFIG)).andReturn(bearerTokenEnvVariableName).anyTimes();
    response = EasyMock.createNiceMock(HttpServletResponse.class);
    if (expectHeader) {
      response.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
      EasyMock.expectLastCall();
    }
    EasyMock.replay(context, response);
  }

  @Test
  public void testBearerTokenWithDefaultEnvVariableName() throws Exception {
    testAuthBearerResource(null, true);
  }

  @Test
  public void testBearerTokenWithCustomEnvVariableName() throws Exception {
    testAuthBearerResource(CUSTOM_TOKEN_ENV_VARIABLE, true);
  }

  @Test
  public void testNoBearerTokenWithDefaultEnvVariableName() throws Exception {
    testAuthBearerResource(null, false);
  }

  @Test
  public void testNoBearerTokenWithCustomEnvVariableName() throws Exception {
    testAuthBearerResource(CUSTOM_TOKEN_ENV_VARIABLE, false);
  }

  private void testAuthBearerResource(String envVariableName, boolean setEnv) {
    final String expectedEnvVariableName = envVariableName == null ? AuthBearerResource.DEFAULT_BEARER_AUTH_TOKEN_ENV : envVariableName;
    boolean exceptionThrown = false;
    try {
      if (setEnv) {
        environmentVariablesRule.set(expectedEnvVariableName, TOKEN);
      }
      configureCommonExpectations(envVariableName, setEnv);
      final AuthBearerResource authBearerResource = new AuthBearerResource();
      authBearerResource.context = context;
      authBearerResource.response = response;
      authBearerResource.init();
      authBearerResource.doGet();
      EasyMock.verify(response);
    } catch (ServletException e) {
      exceptionThrown = true;
      assertEquals("Token environment variable '" + expectedEnvVariableName + "' is not set", e.getMessage());
    }
    if (setEnv) {
      assertFalse(exceptionThrown);
    } else {
      assertTrue(exceptionThrown);
    }
  }

}
