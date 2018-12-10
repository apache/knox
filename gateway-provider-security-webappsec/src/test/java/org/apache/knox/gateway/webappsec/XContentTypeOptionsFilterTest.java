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

import org.apache.knox.gateway.webappsec.filter.XContentTypeOptionsFilter;

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
import java.util.Collection;
import java.util.Enumeration;
import java.util.Properties;

import static org.junit.Assert.fail;

public class XContentTypeOptionsFilterTest {

  private String options;
  private Collection<String> headers;

  @Test
  public void testDefaultOptionsValue() throws Exception {
    try {
      final String expectedDefaultValue = XContentTypeOptionsFilter.DEFAULT_OPTION_VALUE;

      XContentTypeOptionsFilter filter = new XContentTypeOptionsFilter();
      Properties props = new Properties();
      props.put("xcontent-type.options.enabled", "true");
      filter.init(new TestFilterConfig(props));

      HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
      HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
      EasyMock.replay(request);
      EasyMock.replay(response);

      TestFilterChain chain = new TestFilterChain();
      filter.doFilter(request, response, chain);
      Assert.assertTrue("doFilterCalled should not be false.", chain.doFilterCalled );
      Assert.assertEquals(XContentTypeOptionsFilter.X_CONTENT_TYPE_OPTIONS_HEADER + " value incorrect.",
                          expectedDefaultValue, options);
      Assert.assertEquals(XContentTypeOptionsFilter.X_CONTENT_TYPE_OPTIONS_HEADER + " count incorrect.",
                          1, headers.size());
    } catch (ServletException se) {
      fail("Should NOT have thrown a ServletException.");
    }
  }

  @Test
  public void testConfiguredOptionsValue() throws Exception {
    try {
      final String customOption = "myContentTypeOpts";

      XContentTypeOptionsFilter filter = new XContentTypeOptionsFilter();
      Properties props = new Properties();
      props.put("xcontent-type.options.enabled", "true");
      props.put("xcontent-type.options", customOption);
      filter.init(new TestFilterConfig(props));

      HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
      HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
      EasyMock.replay(request);
      EasyMock.replay(response);

      TestFilterChain chain = new TestFilterChain();
      filter.doFilter(request, response, chain);
      Assert.assertTrue("doFilterCalled should not be false.", chain.doFilterCalled );
      Assert.assertEquals(XContentTypeOptionsFilter.X_CONTENT_TYPE_OPTIONS_HEADER + " value incorrect",
                          customOption, options);
      Assert.assertEquals(XContentTypeOptionsFilter.X_CONTENT_TYPE_OPTIONS_HEADER + " count incorrect.",
                          1, headers.size());
    } catch (ServletException se) {
      fail("Should NOT have thrown a ServletException.");
    }
  }

  private static class TestFilterConfig implements FilterConfig {
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
      return null;
    }

  }

  class TestFilterChain implements FilterChain {
    boolean doFilterCalled;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response) {
      doFilterCalled = true;
      options = ((HttpServletResponse)response).getHeader(XContentTypeOptionsFilter.X_CONTENT_TYPE_OPTIONS_HEADER);
      headers = ((HttpServletResponse)response).getHeaders(XContentTypeOptionsFilter.X_CONTENT_TYPE_OPTIONS_HEADER);
    }

  }


}
