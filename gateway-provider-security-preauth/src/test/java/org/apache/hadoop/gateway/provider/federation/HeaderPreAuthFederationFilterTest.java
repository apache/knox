/**
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
package org.apache.hadoop.gateway.provider.federation;

import junit.framework.TestCase;
import org.apache.hadoop.gateway.preauth.filter.*;
import org.junit.Test;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HeaderPreAuthFederationFilterTest extends TestCase {

  @Test
  public void testDefaultValidator() throws ServletException, PreAuthValidationException {
    HeaderPreAuthFederationFilter hpaff = new HeaderPreAuthFederationFilter();
    final HttpServletRequest request = mock(HttpServletRequest.class);
    final FilterConfig filterConfig = mock(FilterConfig.class);
    when(filterConfig.getInitParameter(PreAuthService.VALIDATION_METHOD_PARAM)).thenReturn
        (DefaultValidator.DEFAULT_VALIDATION_METHOD_VALUE);
    hpaff.init(filterConfig);
    PreAuthValidator validator = hpaff.getValidator();
    assertEquals(validator.getName(), DefaultValidator.DEFAULT_VALIDATION_METHOD_VALUE);
    assertTrue(validator.validate(request, filterConfig));
  }

  @Test
  public void testIPValidator() throws ServletException, PreAuthValidationException {
    HeaderPreAuthFederationFilter hpaff = new HeaderPreAuthFederationFilter();
    final HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRemoteAddr()).thenReturn("10.1.23.42");
    final FilterConfig filterConfig = mock(FilterConfig.class);
    when(filterConfig.getInitParameter(IPValidator.IP_ADDRESSES_PARAM)).thenReturn("5.4.3.2,10.1.23.42");
    when(filterConfig.getInitParameter(PreAuthService.VALIDATION_METHOD_PARAM)).thenReturn(IPValidator
        .IP_VALIDATION_METHOD_VALUE);
    hpaff.init(filterConfig);
    PreAuthValidator validator = hpaff.getValidator();
    assertEquals(validator.getName(), IPValidator.IP_VALIDATION_METHOD_VALUE);
    assertTrue(validator.validate(request, filterConfig));
    //Negative testing
    when(request.getRemoteAddr()).thenReturn("10.10.22.33");
    assertFalse(validator.validate(request, filterConfig));
  }

  @Test
  public void testCustomValidatorPositive() throws ServletException, PreAuthValidationException {
    HeaderPreAuthFederationFilter hpaff = new HeaderPreAuthFederationFilter();
    final HttpServletRequest request = mock(HttpServletRequest.class);
    final FilterConfig filterConfig = mock(FilterConfig.class);
    when(filterConfig.getInitParameter(PreAuthService.VALIDATION_METHOD_PARAM)).thenReturn
        (DummyValidator.NAME);

    hpaff.init(filterConfig);
    PreAuthValidator validator = hpaff.getValidator();
    assertEquals(validator.getName(), DummyValidator.NAME);
    //Positive test
    when(request.getHeader("CUSTOM_TOKEN")).thenReturn("HelloWorld");
    assertTrue(validator.validate(request, filterConfig));

  }

  @Test
  public void testCustomValidatorNegative() throws ServletException, PreAuthValidationException {
    HeaderPreAuthFederationFilter hpaff = new HeaderPreAuthFederationFilter();
    final HttpServletRequest request = mock(HttpServletRequest.class);
    final FilterConfig filterConfig = mock(FilterConfig.class);
    when(filterConfig.getInitParameter(PreAuthService.VALIDATION_METHOD_PARAM)).thenReturn
        (DummyValidator.NAME);

    hpaff.init(filterConfig);
    PreAuthValidator validator = hpaff.getValidator();
    assertEquals(validator.getName(), DummyValidator.NAME);

    when(request.getHeader("CUSTOM_TOKEN")).thenReturn("NOTHelloWorld");
    assertFalse(validator.validate(request, filterConfig));

  }


  public static class DummyValidator implements PreAuthValidator {
    public static String NAME = "DummyValidator";

    public DummyValidator() {

    }

    /**
     * @param httpRequest
     * @param filterConfig
     * @return true if validated, otherwise false
     * @throws PreAuthValidationException
     */
    @Override
    public boolean validate(HttpServletRequest httpRequest, FilterConfig filterConfig) throws
        PreAuthValidationException {
      String token = httpRequest.getHeader("CUSTOM_TOKEN");
      if (token.equalsIgnoreCase("HelloWorld")) {
        return true;
      } else {
        return false;
      }
    }

    /**
     * Return unique validator name
     *
     * @return name of validator
     */
    @Override
    public String getName() {
      return NAME;
    }
  }

}
