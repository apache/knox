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
package org.apache.knox.gateway.webappsec;

import org.apache.knox.gateway.webappsec.filter.SecurityHeaderFilter;
import org.easymock.EasyMock;
import org.junit.Assert;
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
import java.util.Collection;
import java.util.Enumeration;
import java.util.Properties;

import static org.junit.Assert.fail;

public class SecurityHeaderFilterTest {

  private static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";
  private String options;
  private Collection<String> headers;
  private Collection<String> headerNames;

  @Test
  public void testConfiguredSecurityHeaderOptionsValue() throws Exception {
    try {
      final String customOption = "default-src 'self'";

      SecurityHeaderFilter filter = new SecurityHeaderFilter();
      Properties props = new Properties();
      props.put("enabled", "true");
      props.put(CONTENT_SECURITY_POLICY, customOption);
      filter.init(new TestFilterConfig(props));

      HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
      HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
      EasyMock.replay(request);
      EasyMock.replay(response);

      TestFilterChain chain = new TestFilterChain();
      filter.doFilter(request, response, chain);
      Assert.assertTrue("doFilterCalled should not be false.", chain.doFilterCalled );
      Assert.assertEquals("SecurityHeaderFilter value incorrect", customOption, options);
      Assert.assertEquals("SecurityHeaderFilter header count incorrect.", 1, headers.size());
      Assert.assertEquals("SecurityHeaderFilter header value incorrect.", customOption, headers.toArray()[0]);
      Assert.assertEquals("SecurityHeaderFilter header name count incorrect.", 1, headerNames.size());
      Assert.assertEquals("SecurityHeaderFilter header value count incorrect.", CONTENT_SECURITY_POLICY,
              headerNames.toArray()[0]);
    } catch (ServletException se) {
      fail("Should NOT have thrown a ServletException.");
    }
  }

  private class TestFilterConfig implements FilterConfig {
    Properties props;

    TestFilterConfig(Properties props) {
      this.props = props;
    }

    @Override
    public String getFilterName() {
      return null;
    }

    @Override
    public ServletContext getServletContext() {
      return null;
    }

    @Override
    public String getInitParameter(String name) {
      return props.getProperty(name, null);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
      return (Enumeration<String>) props.propertyNames();
    }

  }

  class TestFilterChain implements FilterChain {
    boolean doFilterCalled;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
      doFilterCalled = true;
      options = ((HttpServletResponse)response).getHeader(CONTENT_SECURITY_POLICY);
      headers = ((HttpServletResponse)response).getHeaders(CONTENT_SECURITY_POLICY);
      headerNames = ((HttpServletResponse)response).getHeaderNames();
    }

  }


}
