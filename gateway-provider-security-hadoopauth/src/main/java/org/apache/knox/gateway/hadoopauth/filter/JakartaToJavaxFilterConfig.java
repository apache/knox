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

import java.util.Enumeration;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;

/**
 * A {@code javax.servlet.FilterConfig} view over a {@code jakarta.servlet.FilterConfig},
 * used to initialize Hadoop's {@code AuthenticationFilter} from Knox's EE10 filter chain.
 *
 * <p>The original jakarta {@link jakarta.servlet.FilterConfig} is retained and exposed via
 * {@link #getDelegate()} so the alias-resolving configuration path can operate on the real
 * jakarta object (this also keeps {@code HadoopAuthFilter.getConfiguration(String, FilterConfig)}
 * mockable in unit tests).</p>
 */
class JakartaToJavaxFilterConfig implements FilterConfig {

  private final jakarta.servlet.FilterConfig delegate;
  private final JakartaToJavaxServletContext servletContext;

  JakartaToJavaxFilterConfig(jakarta.servlet.FilterConfig delegate) {
    this.delegate = delegate;
    final jakarta.servlet.ServletContext ctx = delegate.getServletContext();
    this.servletContext = ctx == null ? null : new JakartaToJavaxServletContext(ctx);
  }

  jakarta.servlet.FilterConfig getDelegate() {
    return delegate;
  }

  @Override
  public String getFilterName() {
    return delegate.getFilterName();
  }

  @Override
  public ServletContext getServletContext() {
    return servletContext;
  }

  @Override
  public String getInitParameter(String name) {
    return delegate.getInitParameter(name);
  }

  @Override
  public Enumeration<String> getInitParameterNames() {
    return delegate.getInitParameterNames();
  }
}
