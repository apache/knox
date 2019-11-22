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
package org.apache.knox.gateway.filter;

import static org.junit.Assert.assertEquals;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;

import org.apache.knox.gateway.config.GatewayConfig;
import org.easymock.EasyMock;
import org.junit.Test;

public class RedirectToUrlFilterTest {

  private static final String OUT_OF_THE_BOX_REDIRECT_TO_URL = "/${GATEWAY_PATH}/knoxsso/knoxauth/login.html";
  private static final String CUSTOM_REDIRECT_URL = "/route/to/my/web/app/login/page";

  @Test
  public void shouldReplaceGatewayPathInRedirectUrl() throws Exception {
    final String customGatewayPath = "customGatewayPath";
    final RedirectToUrlFilter filter = new RedirectToUrlFilter();
    filter.init(buildFilterConfigAndSetOtherMockExpectations(OUT_OF_THE_BOX_REDIRECT_TO_URL, customGatewayPath));
    assertEquals("/" + customGatewayPath + "/knoxsso/knoxauth/login.html", filter.getRedirectUrl());
  }

  @Test
  public void shouldNotTouchAlreadyCustomizedRedirectUrl() throws Exception {
    final RedirectToUrlFilter filter = new RedirectToUrlFilter();
    filter.init(buildFilterConfigAndSetOtherMockExpectations(CUSTOM_REDIRECT_URL, ""));
    assertEquals(CUSTOM_REDIRECT_URL, filter.getRedirectUrl());
  }

  private FilterConfig buildFilterConfigAndSetOtherMockExpectations(String redirectUrl, String customGatewayPath) {
    final FilterConfig filterConfig = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(filterConfig.getInitParameter(RedirectToUrlFilter.REDIRECT_TO_URL)).andReturn(redirectUrl);
    final ServletContext servletContext = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(filterConfig.getServletContext()).andReturn(servletContext);
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(servletContext.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(gatewayConfig);
    EasyMock.expect(gatewayConfig.getGatewayPath()).andReturn(customGatewayPath);

    EasyMock.replay(filterConfig, servletContext, gatewayConfig);
    return filterConfig;
  }
}
