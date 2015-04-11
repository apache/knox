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
package org.apache.hadoop.gateway.picketlink.filter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.picketlink.PicketlinkMessages;

import java.io.IOException;

public class CaptureOriginalURLFilter implements Filter {
  private static PicketlinkMessages log = MessagesFactory.get( PicketlinkMessages.class );
  private static final String COOKIE_PATH = "cookie.path"; 
  private String cookiePath = null;

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    cookiePath = filterConfig.getInitParameter(COOKIE_PATH);
    if (cookiePath == null) {
      cookiePath = "/gateway/idp/knoxsso/websso";
    }
  }

  @Override
  public void doFilter( ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain ) throws IOException, ServletException {
    String original = null;
    HttpServletRequest request = (HttpServletRequest)servletRequest;
    String url = request.getParameter("originalUrl");
    if (url != null) {
      log.foundOriginalURLInRequest(url);
      original = request.getParameter("originalUrl");
      log.settingCookieForOriginalURL();
      addCookie(servletResponse, original);
    }
    filterChain.doFilter(request, servletResponse);
  }

  @Override
  public void destroy() {

  }
  
  private void addCookie(ServletResponse servletResponse, String original) {
    Cookie c = new Cookie("original-url", original);
    c.setPath(cookiePath);
    c.setMaxAge(60);
    ((HttpServletResponse)servletResponse).addCookie(c);
  }

}