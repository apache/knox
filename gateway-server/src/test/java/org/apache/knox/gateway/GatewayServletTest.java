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
package org.apache.knox.gateway;

import org.apache.knox.gateway.config.GatewayConfig;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.servlet.FilterConfig;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(Parameterized.class)
public class GatewayServletTest {

    @Parameterized.Parameters(name = "{index}: SanitizationEnabled={2}, Exception={0}, ExpectedMessage={1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { new IOException("Connection to 192.168.1.1 failed"), "Connection to [hidden] failed", true },
                { new RuntimeException("Connection to 192.168.1.1 failed"), "Connection to [hidden] failed", true },
                { new NullPointerException(), null, true },
                { new IOException("General failure"), "General failure", true },
                { new IOException("Connection to 192.168.1.1 failed"), "Connection to 192.168.1.1 failed", false },
                { new RuntimeException("Connection to 192.168.1.1 failed"), "Connection to 192.168.1.1 failed", false },
                { new NullPointerException(), null, false },
                { new IOException("General failure"), "General failure", false }
        });
    }

    private final Exception exception;
    private final String expectedMessage;
    private final boolean isSanitizationEnabled;

    public GatewayServletTest(Exception exception, String expectedMessage, boolean isSanitizationEnabled) {
        this.exception = exception;
        this.expectedMessage = expectedMessage;
        this.isSanitizationEnabled = isSanitizationEnabled;
    }

    private IMocksControl mockControl;
    private ServletConfig servletConfig;
    private ServletContext servletContext;
    private GatewayConfig gatewayConfig;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private GatewayFilter filter;

    @Before
    public void setUp() throws ServletException {
        mockControl = EasyMock.createControl();
        servletConfig = mockControl.createMock(ServletConfig.class);
        servletContext = mockControl.createMock(ServletContext.class);
        gatewayConfig = mockControl.createMock(GatewayConfig.class);
        request = mockControl.createMock(HttpServletRequest.class);
        response = mockControl.createMock(HttpServletResponse.class);
        filter = mockControl.createMock(GatewayFilter.class);

        EasyMock.expect(servletConfig.getServletName()).andStubReturn("default");
        EasyMock.expect(servletConfig.getServletContext()).andStubReturn(servletContext);
        EasyMock.expect(servletContext.getAttribute("org.apache.knox.gateway.config")).andStubReturn(gatewayConfig);
    }

    @Test
    public void testExceptionSanitization() throws ServletException, IOException {
        GatewayServlet servlet = initializeServletWithSanitization(isSanitizationEnabled);

        try {
            servlet.service(request, response);
        } catch (Exception e) {
            if (expectedMessage != null) {
                assertEquals(expectedMessage, e.getMessage());
            } else {
                assertNull(e.getMessage());
            }
        }

        mockControl.verify();
    }

    private GatewayServlet initializeServletWithSanitization(boolean isErrorMessageSanitizationEnabled) throws ServletException, IOException {
        EasyMock.expect(gatewayConfig.isErrorMessageSanitizationEnabled()).andStubReturn(isErrorMessageSanitizationEnabled);

        filter.init(EasyMock.anyObject(FilterConfig.class));
        EasyMock.expectLastCall().once();
        filter.doFilter(EasyMock.eq(request), EasyMock.eq(response), EasyMock.isNull());
        EasyMock.expectLastCall().andThrow(exception).once();

        mockControl.replay();

        GatewayServlet servlet = new GatewayServlet(filter);
        servlet.init(servletConfig);
        return servlet;
    }
}
