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
package org.apache.knox.gateway.webappsec.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * This filter protects proxied webapps from clickjacking attacks that
 * are possible through use of Frames to contain the proxied resources.
 */
public class XFrameOptionsFilter implements Filter {
  private static final String X_FRAME_OPTIONS = "X-Frame-Options";
  private static final String CUSTOM_HEADER_PARAM = "xframe.options";

  private String option = "DENY";

  /* (non-Javadoc)
   * @see javax.servlet.Filter#destroy()
   */
  @Override
  public void destroy() {
  }

  /* (non-Javadoc)
   * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
   */
  @Override
  public void doFilter(ServletRequest req, ServletResponse res,
      FilterChain chain) throws IOException, ServletException {
    ((HttpServletResponse) res).setHeader(X_FRAME_OPTIONS, option);
    chain.doFilter(req, new XFrameOptionsResponseWrapper((HttpServletResponse) res));
  }

  /* (non-Javadoc)
   * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
   */
  @Override
  public void init(FilterConfig config) throws ServletException {
    String customOption = config.getInitParameter(CUSTOM_HEADER_PARAM);
    if (customOption != null) {
      option = customOption;
    }
  }

  public class XFrameOptionsResponseWrapper extends HttpServletResponseWrapper {
    @Override
    public void addHeader(String name, String value) {
      // don't allow additional values to be added to
      // the configured options value in topology
      if (!name.equals(X_FRAME_OPTIONS)) {
        super.addHeader(name, value);
      }
    }

    @Override
    public void setHeader(String name, String value) {
      // don't allow overwriting of configured value
      if (!name.equals(X_FRAME_OPTIONS)) {
        super.setHeader(name, value);
      }
    }

    /**
     * construct a wrapper for this response
     * 
     * @param response
     */
    public XFrameOptionsResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public String getHeader(String name) {
        String headerValue = null;
        if (name.equals(X_FRAME_OPTIONS)) {
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
        names.add(X_FRAME_OPTIONS);
        return names;
    }

    @Override
    public Collection<String> getHeaders(String name) {
        List<String> values = (List<String>) super.getHeaders(name);
        if (name.equals(X_FRAME_OPTIONS)) {
          if (values == null) {
            values = new ArrayList<>();
          }
          values.add(option);
        }
        return values;
    }
  }

}
