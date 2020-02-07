/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.provider.federation;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.provider.federation.jwt.filter.AbstractJWTFilter;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CommonJWTFilterTest {

  private AbstractJWTFilter handler;

  @Before
  public void setUp() {
    handler = new TestHandler();
  }

  @After
  public void tearDown() {
    handler = null;
  }

  @Test
  public void testServerManagedTokenStateEnabledByProviderOverride() throws Exception {
    // Provider param should override gateway config
    assertTrue(doTestServerManagedTokenState(false, "true"));
  }

  @Test
  public void testServerManagedTokenStateDisabledByProviderOverride() throws Exception {
    // Provider param should override gateway config
    assertFalse(doTestServerManagedTokenState(true, "false"));
  }

  @Test
  public void testServerManagedTokenStateDisabledWithoutProviderOverride() throws Exception {
    // Missing provider param override should apply gateway config
    assertFalse(doTestServerManagedTokenState(false, null));
  }

  @Test
  public void testServerManagedTokenStateEnabledWithoutProviderOverride() throws Exception {
    // Missing provider param override should apply gateway config
    assertTrue(doTestServerManagedTokenState(true, null));
  }

  @Test
  public void testServerManagedTokenStateDisabledWitEmptyProviderOverride() throws Exception {
    // Empty provider param override should apply gateway config
    assertFalse(doTestServerManagedTokenState(false, ""));
  }

  @Test
  public void testServerManagedTokenStateEnabledWithEmptyProviderOverride() throws Exception {
    // Empty provider param override should apply gateway config
    assertTrue(doTestServerManagedTokenState(true, ""));
  }

  private boolean doTestServerManagedTokenState(final Boolean isEnabledAtGateway, final String providerParamValue)
      throws Exception {

    GatewayConfig gwConf = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gwConf.isServerManagedTokenStateEnabled()).andReturn(isEnabledAtGateway).anyTimes();
    EasyMock.replay(gwConf);

    ServletContext sc = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(sc.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(gwConf).anyTimes();
    EasyMock.replay(sc);

    FilterConfig fc = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(fc.getInitParameter(TokenStateService.CONFIG_SERVER_MANAGED)).andReturn(providerParamValue).anyTimes();
    EasyMock.expect(fc.getServletContext()).andReturn(sc).anyTimes();
    EasyMock.replay(fc);

    Method m = AbstractJWTFilter.class.getDeclaredMethod("isServerManagedTokenStateEnabled", FilterConfig.class);
    m.setAccessible(true);
    return (Boolean) m.invoke(handler, fc);
  }


  static final class TestHandler extends AbstractJWTFilter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

    }

    @Override
    protected void handleValidationError(HttpServletRequest request, HttpServletResponse response, int status, String error) throws IOException {

    }

    @Override
    public void destroy() {

    }
  }

}
