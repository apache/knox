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
package org.apache.knox.gateway.webappsec.filter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class XContentTypeOptionsFilter implements Filter {

  public static final String X_CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";

  public static final String CUSTOM_HEADER_PARAM = "xcontent-type.options";

  public static final String DEFAULT_OPTION_VALUE = "nosniff";

  private String option = DEFAULT_OPTION_VALUE;


  @Override
  public void init(FilterConfig config) throws ServletException {
    String customOption = config.getInitParameter(CUSTOM_HEADER_PARAM);
    if (customOption != null) {
      option = customOption;
    }
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    ((HttpServletResponse) response).setHeader(X_CONTENT_TYPE_OPTIONS_HEADER, option);
    chain.doFilter(request, new XContentTypeOptionsResponseWrapper((HttpServletResponse) response));
  }

  @Override
  public void destroy() {
  }


  class XContentTypeOptionsResponseWrapper extends HttpServletResponseWrapper {

    XContentTypeOptionsResponseWrapper(HttpServletResponse res) {
      super(res);
    }

    @Override
    public void addHeader(String name, String value) {
      // don't allow additional values to be added to
      // the configured options value in topology
      if (!name.equals(X_CONTENT_TYPE_OPTIONS_HEADER)) {
        super.addHeader(name, value);
      }
    }

    @Override
    public void setHeader(String name, String value) {
      // don't allow overwriting of configured value
      if (!name.equals(X_CONTENT_TYPE_OPTIONS_HEADER)) {
        super.setHeader(name, value);
      }
    }

    @Override
    public String getHeader(String name) {
      String headerValue = null;
      if (name.equals(X_CONTENT_TYPE_OPTIONS_HEADER)) {
        headerValue = option;
      }
      else {
        headerValue = super.getHeader(name);
      }
      return headerValue;
    }

    /**
     * get the Header names
     */
    @Override
    public Collection<String> getHeaderNames() {
      List<String> names = (List<String>) super.getHeaderNames();
      if (names == null) {
        names = new ArrayList<>();
      }
      names.add(X_CONTENT_TYPE_OPTIONS_HEADER);
      return names;
    }

    @Override
    public Collection<String> getHeaders(String name) {
      List<String> values = (List<String>) super.getHeaders(name);
      if (name.equals(X_CONTENT_TYPE_OPTIONS_HEADER)) {
        if (values == null) {
          values = new ArrayList<>();
        }
        values.add(option);
      }
      return values;
    }
  }
}
