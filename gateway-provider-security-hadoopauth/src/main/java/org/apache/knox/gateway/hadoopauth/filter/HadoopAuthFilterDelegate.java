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
package org.apache.knox.gateway.hadoopauth.filter;

import java.util.Properties;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;

/**
 * The {@code javax.servlet} half of the Hadoop auth bridge: a thin subclass of Hadoop's
 * {@code AuthenticationFilter} that {@link HadoopAuthFilter} composes and drives. It performs
 * the real token/Kerberos authentication using {@code javax.servlet} types (Hadoop has no
 * {@code jakarta} release), while all Knox-specific configuration and post-authentication
 * logic stays on {@link HadoopAuthFilter} in the {@code jakarta} world.
 *
 * <p>The only override needed is {@link #getConfiguration(String, FilterConfig)}, which routes
 * back to {@code HadoopAuthFilter}'s alias-resolving configuration (kept there so it remains
 * mockable in unit tests). The inherited protected
 * {@code doFilter(FilterChain, HttpServletRequest, HttpServletResponse)} runs unchanged and,
 * on successful authentication, invokes the {@link JavaxToJakartaFilterChain} that returns
 * control to {@code HadoopAuthFilter} for doAs impersonation.</p>
 */
class HadoopAuthFilterDelegate
    extends org.apache.hadoop.security.authentication.server.AuthenticationFilter {

  private final HadoopAuthFilter hadoopAuthFilter;

  HadoopAuthFilterDelegate(HadoopAuthFilter hadoopAuthFilter) {
    this.hadoopAuthFilter = hadoopAuthFilter;
  }

  @Override
  protected Properties getConfiguration(String configPrefix, FilterConfig filterConfig) throws ServletException {
    final jakarta.servlet.FilterConfig jakartaFilterConfig = (filterConfig instanceof JakartaToJavaxFilterConfig)
        ? ((JakartaToJavaxFilterConfig) filterConfig).getDelegate()
        : null;
    if (jakartaFilterConfig == null) {
      throw new ServletException("Unexpected FilterConfig type in Hadoop auth bridge: "
          + (filterConfig == null ? "null" : filterConfig.getClass().getName()));
    }
    try {
      return hadoopAuthFilter.getConfiguration(configPrefix, jakartaFilterConfig);
    } catch (jakarta.servlet.ServletException e) {
      throw new ServletException(e.getMessage(), e.getCause() == null ? e : e.getCause());
    }
  }
}
