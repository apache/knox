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
package org.apache.knox.gateway.provider.federation;

import org.apache.knox.gateway.preauth.filter.DefaultValidator;
import org.apache.knox.gateway.preauth.filter.IPValidator;
import org.apache.knox.gateway.preauth.filter.PreAuthService;
import org.apache.knox.gateway.preauth.filter.PreAuthValidator;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

public class PreAuthServiceTest extends org.junit.Assert {

  @Test
  public void testValidatorMap() {
    Map<String, PreAuthValidator> valMap = PreAuthService.getValidatorMap();
    assertNotNull(valMap.get(IPValidator.IP_VALIDATION_METHOD_VALUE));
    assertEquals(valMap.get(IPValidator.IP_VALIDATION_METHOD_VALUE).getName(), IPValidator.IP_VALIDATION_METHOD_VALUE);
    assertNotNull(valMap.get(DefaultValidator.DEFAULT_VALIDATION_METHOD_VALUE));
    assertEquals(valMap.get(DefaultValidator.DEFAULT_VALIDATION_METHOD_VALUE).getName(), DefaultValidator.DEFAULT_VALIDATION_METHOD_VALUE);

    //Negative test
    assertNull(valMap.get("NonExists"));
  }

  @Test
  public void testDefaultValidator() throws ServletException {
    final FilterConfig filterConfig = EasyMock.createMock(FilterConfig.class);
    EasyMock.expect(filterConfig.getInitParameter(PreAuthService.VALIDATION_METHOD_PARAM))
        .andReturn(DefaultValidator.DEFAULT_VALIDATION_METHOD_VALUE);
    EasyMock.replay(filterConfig);

    final HttpServletRequest request = EasyMock.createMock(HttpServletRequest.class);
    EasyMock.replay(request);

    List<PreAuthValidator> validators = PreAuthService.getValidators(filterConfig);
    assertEquals(validators.size(), 1);
    assertEquals(validators.get(0).getName(), DefaultValidator.DEFAULT_VALIDATION_METHOD_VALUE);
    assertTrue(PreAuthService.validate(request, filterConfig, validators));
  }

  @Test
  public void testIPValidator() throws ServletException {
    final HttpServletRequest request = EasyMock.createMock(HttpServletRequest.class);
    EasyMock.expect(request.getRemoteAddr()).andReturn("10.1.23.42");
    EasyMock.replay(request);

    final FilterConfig filterConfig = EasyMock.createMock(FilterConfig.class);
    EasyMock.expect(filterConfig.getInitParameter(IPValidator.IP_ADDRESSES_PARAM))
        .andReturn("5.4.3.2,10.1.23.42").anyTimes();
    EasyMock.expect(filterConfig.getInitParameter(PreAuthService.VALIDATION_METHOD_PARAM))
        .andReturn(IPValidator.IP_VALIDATION_METHOD_VALUE).anyTimes();
    EasyMock.replay(filterConfig);

    List<PreAuthValidator> validators = PreAuthService.getValidators(filterConfig);
    assertEquals(validators.size(), 1);
    assertEquals(validators.get(0).getName(), IPValidator.IP_VALIDATION_METHOD_VALUE);
    assertTrue(PreAuthService.validate(request, filterConfig, validators));

    //Negative testing
    EasyMock.reset(request);
    EasyMock.expect(request.getRemoteAddr()).andReturn("10.10.22.33");
    EasyMock.replay(request);
    assertFalse(PreAuthService.validate(request, filterConfig, validators));
  }

  @Test
  public void testMultipleValidatorsPositive() throws ServletException {
    final HttpServletRequest request = EasyMock.createMock(HttpServletRequest.class);
    EasyMock.expect(request.getRemoteAddr()).andReturn("10.1.23.42");
    EasyMock.replay(request);

    final FilterConfig filterConfig = EasyMock.createMock(FilterConfig.class);
    EasyMock.expect(filterConfig.getInitParameter(IPValidator.IP_ADDRESSES_PARAM))
        .andReturn("5.4.3.2,10.1.23.42").anyTimes();
    EasyMock.expect(filterConfig.getInitParameter(PreAuthService.VALIDATION_METHOD_PARAM))
        .andReturn(DefaultValidator.DEFAULT_VALIDATION_METHOD_VALUE + "," + IPValidator.IP_VALIDATION_METHOD_VALUE).anyTimes();
    EasyMock.replay(filterConfig);

    List<PreAuthValidator> validators = PreAuthService.getValidators(filterConfig);
    assertEquals(validators.size(), 2);
    assertEquals(validators.get(0).getName(), DefaultValidator.DEFAULT_VALIDATION_METHOD_VALUE);
    assertEquals(validators.get(1).getName(), IPValidator.IP_VALIDATION_METHOD_VALUE);

    assertTrue(PreAuthService.validate(request, filterConfig, validators));
    //Negative testing
    EasyMock.reset(request);
    EasyMock.expect(request.getRemoteAddr()).andReturn("10.10.22.33");
    EasyMock.replay(request);
    assertFalse(PreAuthService.validate(request, filterConfig, validators));

  }

  @Test
  public void testMultipleValidatorsNegative() {
    final FilterConfig filterConfig = EasyMock.createMock(FilterConfig.class);
    EasyMock.expect(filterConfig.getInitParameter(PreAuthService.VALIDATION_METHOD_PARAM)).andReturn
        (DefaultValidator.DEFAULT_VALIDATION_METHOD_VALUE + ",  NOT_EXISTED_VALIDATOR" );
    EasyMock.replay(filterConfig);
    try {
      PreAuthService.getValidators(filterConfig);
      fail("Should throw exception due to invalid validator");
    } catch (Exception e) {
      //Expected
    }
  }
}
