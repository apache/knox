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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.knox.gateway.provider.federation.JWTFederationFilterTest;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.easymock.EasyMock;
import org.junit.Test;

public class RequestAudienceValidatorServiceTest {

  private static final String SERVICE_RESOURCE_NAME =
      "META-INF/services/org.apache.knox.gateway.provider.federation.jwt.filter.RequestAudienceValidator";

  @Test
  public void testGetValidatorWithParamUnsetReturnsEmpty() throws Exception {
    final FilterConfig filterConfig = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(filterConfig.getInitParameter(RequestAudienceValidatorService.REQUEST_AUDIENCE_VALIDATOR_PARAM))
        .andReturn(null).anyTimes();
    EasyMock.replay(filterConfig);

    final RequestAudienceValidatorService service = new RequestAudienceValidatorService();
    final Optional<RequestAudienceValidator> result = service.getValidator(filterConfig);
    assertFalse(result.isPresent());
  }

  @Test
  public void testGetValidatorWithKnownNameReturnsDummy() throws Exception {
    final FilterConfig filterConfig = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(filterConfig.getInitParameter(RequestAudienceValidatorService.REQUEST_AUDIENCE_VALIDATOR_PARAM))
        .andReturn(DummyValidator.NAME).anyTimes();
    EasyMock.replay(filterConfig);

    final RequestAudienceValidatorService service = new RequestAudienceValidatorService();
    final Optional<RequestAudienceValidator> result = service.getValidator(filterConfig);
    assertTrue(result.isPresent());
    assertEquals(DummyValidator.NAME, result.get().getName());
  }

  @Test
  public void testGetValidatorWithUnknownNameThrows() throws Exception {
    final FilterConfig filterConfig = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(filterConfig.getInitParameter(RequestAudienceValidatorService.REQUEST_AUDIENCE_VALIDATOR_PARAM))
        .andReturn("unknown-validator").anyTimes();
    EasyMock.replay(filterConfig);

    final RequestAudienceValidatorService service = new RequestAudienceValidatorService();
    try {
      service.getValidator(filterConfig);
      fail("Expected ServletException");
    } catch (ServletException e) {
      assertTrue(e.getMessage().contains("unknown-validator"));
    }
  }

  @Test
  public void testGetValidatorMapContainsExactlyTheRegisteredValidators() {
    final RequestAudienceValidatorService service = new RequestAudienceValidatorService();
    final Map<String, RequestAudienceValidator> validatorMap = service.getValidatorMap();
    assertEquals(3, validatorMap.size());
    assertTrue(validatorMap.containsKey(DummyValidator.NAME));
    assertTrue(validatorMap.containsKey(JWTFederationFilterTest.RecordingRequestAudienceValidator.NAME));
    assertTrue(validatorMap.containsKey(K8sDestinationAudienceValidator.VALIDATION_METHOD_VALUE));
  }

  @Test
  public void testProductionServiceRegistrationFileListsOnlyTheK8sValidator() throws IOException {
    final Enumeration<URL> resources =
        Thread.currentThread().getContextClassLoader().getResources(SERVICE_RESOURCE_NAME);
    URL productionResource = null;
    while (resources.hasMoreElements()) {
      final URL candidate = resources.nextElement();
      if (!candidate.getPath().contains("test-classes")) {
        productionResource = candidate;
      }
    }
    assertTrue("Production " + SERVICE_RESOURCE_NAME + " resource not found on the classpath",
        productionResource != null);

    final List<String> entries = new ArrayList<>();
    try (InputStream in = productionResource.openStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        final String trimmed = line.trim();
        if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
          entries.add(trimmed);
        }
      }
    }
    assertEquals(1, entries.size());
    assertEquals(K8sDestinationAudienceValidator.class.getName(), entries.get(0));
  }

  public static class DummyValidator implements RequestAudienceValidator {
    static final String NAME = "DummyValidator";

    public DummyValidator() {
    }

    @Override
    public AudienceValidationResult validate(HttpServletRequest request, JWT token, List<String> configuredAudiences) {
      return AudienceValidationResult.of(true);
    }

    @Override
    public String getName() {
      return NAME;
    }
  }
}
