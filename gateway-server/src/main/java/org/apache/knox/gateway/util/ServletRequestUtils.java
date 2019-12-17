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
package org.apache.knox.gateway.util;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

/**
 * Provides useful methods to fetch different parts from {@link ServletRequest} and {@link HttpServletRequest} interfaces.
 */
public class ServletRequestUtils {

  public static String getRequestPath(ServletRequest servletRequest) {
    return getRequestPath((HttpServletRequest) servletRequest);
  }

  public static String getRequestPath(HttpServletRequest httpServletRequest) {
    return emptyOrValue(httpServletRequest.getServletPath()) + emptyOrValue(httpServletRequest.getPathInfo());
  }

  public static String getRequestPathWithQuery(ServletRequest servletRequest) {
    return getRequestPathWithQuery((HttpServletRequest) servletRequest);
  }

  public static String getRequestPathWithQuery(HttpServletRequest httpServletRequest) {
    return getRequestPath(httpServletRequest) + emptyOrValue(httpServletRequest.getQueryString(), "?");
  }

  public static String getContextPathWithQuery(ServletRequest servletRequest) {
    return getContextPathWithQuery((HttpServletRequest) servletRequest);
  }

  public static String getContextPathWithQuery(HttpServletRequest httpServletRequest) {
    return httpServletRequest.getContextPath() + getRequestPathWithQuery(httpServletRequest);
  }

  private static String emptyOrValue(String toTest) {
    return emptyOrValue(toTest, null);
  }

  private static String emptyOrValue(String toTest, String prefix) {
    if (toTest == null) {
      return "";
    } else {
      return prefix == null ? toTest : prefix + toTest;
    }
  }

}
