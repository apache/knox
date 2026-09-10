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

import java.io.IOException;
import java.security.Principal;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * The {@code javax.servlet.FilterChain} handed to Hadoop's {@code AuthenticationFilter}. When
 * Hadoop finishes authenticating it invokes this chain; at that point we hop back to the
 * {@code jakarta} world: the principal Hadoop established (exposed on its own javax request
 * wrapper) is carried onto the original jakarta request, and control returns to
 * {@link HadoopAuthFilter} to apply doAs impersonation and continue the real gateway chain.
 */
class JavaxToJakartaFilterChain implements FilterChain {

  private final jakarta.servlet.FilterChain jakartaChain;
  private final jakarta.servlet.http.HttpServletRequest originalRequest;
  private final jakarta.servlet.http.HttpServletResponse originalResponse;
  private final HadoopAuthFilter hadoopAuthFilter;

  JavaxToJakartaFilterChain(jakarta.servlet.FilterChain jakartaChain,
      jakarta.servlet.http.HttpServletRequest originalRequest,
      jakarta.servlet.http.HttpServletResponse originalResponse,
      HadoopAuthFilter hadoopAuthFilter) {
    this.jakartaChain = jakartaChain;
    this.originalRequest = originalRequest;
    this.originalResponse = originalResponse;
    this.hadoopAuthFilter = hadoopAuthFilter;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
    final javax.servlet.http.HttpServletRequest authenticated = (javax.servlet.http.HttpServletRequest) request;
    final String remoteUser = authenticated.getRemoteUser();
    final Principal userPrincipal = authenticated.getUserPrincipal();
    final String authType = authenticated.getAuthType();

    final jakarta.servlet.http.HttpServletRequest jakartaRequest =
        new AuthenticatedRequest(originalRequest, remoteUser, userPrincipal, authType);

    try {
      hadoopAuthFilter.continueChainAfterHadoopAuth(jakartaRequest, originalResponse, jakartaChain);
    } catch (jakarta.servlet.ServletException e) {
      // Translate back to the javax exception type expected by Hadoop's doFilter contract.
      throw new ServletException(e.getMessage(), e.getCause() == null ? e : e.getCause());
    }
  }

  /**
   * Wraps the original jakarta request so that downstream gateway filters observe the identity
   * Hadoop authenticated (mirrors the {@code HttpServletRequestWrapper} Hadoop's
   * {@code AuthenticationFilter} installs on the javax side).
   */
  private static final class AuthenticatedRequest extends jakarta.servlet.http.HttpServletRequestWrapper {
    private final String remoteUser;
    private final Principal userPrincipal;
    private final String authType;

    AuthenticatedRequest(jakarta.servlet.http.HttpServletRequest request, String remoteUser,
        Principal userPrincipal, String authType) {
      super(request);
      this.remoteUser = remoteUser;
      this.userPrincipal = userPrincipal;
      this.authType = authType;
    }

    @Override
    public String getRemoteUser() {
      return remoteUser;
    }

    @Override
    public Principal getUserPrincipal() {
      return userPrincipal;
    }

    @Override
    public String getAuthType() {
      return authType;
    }
  }
}
