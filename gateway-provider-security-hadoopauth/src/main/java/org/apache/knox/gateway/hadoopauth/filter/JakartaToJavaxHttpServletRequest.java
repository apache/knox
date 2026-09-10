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

import java.io.BufferedReader;
import java.io.IOException;
import java.security.Principal;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;

/**
 * A {@code javax.servlet.http.HttpServletRequest} view over a
 * {@code jakarta.servlet.http.HttpServletRequest}, so Hadoop's {@code AuthenticationFilter}
 * (and its authentication handlers) can read the incoming request.
 *
 * <p>All the read accessors Hadoop auth relies on (method, URI/URL, headers, cookies, query
 * string, parameters, remote/local address, scheme, principal) are forwarded. Session,
 * async, upgrade, multipart and dispatch APIs are not part of the stateless Hadoop auth path
 * and throw {@link UnsupportedOperationException} (or return an inert value where the token
 * validation code tolerates it).</p>
 */
class JakartaToJavaxHttpServletRequest implements HttpServletRequest {

  private final jakarta.servlet.http.HttpServletRequest delegate;
  private ServletContext servletContext;

  JakartaToJavaxHttpServletRequest(jakarta.servlet.http.HttpServletRequest delegate) {
    this.delegate = delegate;
  }

  jakarta.servlet.http.HttpServletRequest getDelegate() {
    return delegate;
  }

  // --- HttpServletRequest ---

  @Override
  public String getAuthType() {
    return delegate.getAuthType();
  }

  @Override
  public Cookie[] getCookies() {
    final jakarta.servlet.http.Cookie[] cookies = delegate.getCookies();
    if (cookies == null) {
      return null;
    }
    final Cookie[] result = new Cookie[cookies.length];
    for (int i = 0; i < cookies.length; i++) {
      result[i] = toJavaxCookie(cookies[i]);
    }
    return result;
  }

  static Cookie toJavaxCookie(jakarta.servlet.http.Cookie source) {
    final Cookie target = new Cookie(source.getName(), source.getValue());
    if (source.getDomain() != null) {
      target.setDomain(source.getDomain());
    }
    if (source.getPath() != null) {
      target.setPath(source.getPath());
    }
    target.setMaxAge(source.getMaxAge());
    target.setSecure(source.getSecure());
    target.setHttpOnly(source.isHttpOnly());
    target.setVersion(source.getVersion());
    return target;
  }

  @Override
  public long getDateHeader(String name) {
    return delegate.getDateHeader(name);
  }

  @Override
  public String getHeader(String name) {
    return delegate.getHeader(name);
  }

  @Override
  public Enumeration<String> getHeaders(String name) {
    return delegate.getHeaders(name);
  }

  @Override
  public Enumeration<String> getHeaderNames() {
    return delegate.getHeaderNames();
  }

  @Override
  public int getIntHeader(String name) {
    return delegate.getIntHeader(name);
  }

  @Override
  public String getMethod() {
    return delegate.getMethod();
  }

  @Override
  public String getPathInfo() {
    return delegate.getPathInfo();
  }

  @Override
  public String getPathTranslated() {
    return delegate.getPathTranslated();
  }

  @Override
  public String getContextPath() {
    return delegate.getContextPath();
  }

  @Override
  public String getQueryString() {
    return delegate.getQueryString();
  }

  @Override
  public String getRemoteUser() {
    return delegate.getRemoteUser();
  }

  @Override
  public boolean isUserInRole(String role) {
    return delegate.isUserInRole(role);
  }

  @Override
  public Principal getUserPrincipal() {
    return delegate.getUserPrincipal();
  }

  @Override
  public String getRequestedSessionId() {
    return delegate.getRequestedSessionId();
  }

  @Override
  public String getRequestURI() {
    return delegate.getRequestURI();
  }

  @Override
  public StringBuffer getRequestURL() {
    return delegate.getRequestURL();
  }

  @Override
  public String getServletPath() {
    return delegate.getServletPath();
  }

  @Override
  public String changeSessionId() {
    return delegate.changeSessionId();
  }

  @Override
  public boolean isRequestedSessionIdValid() {
    return delegate.isRequestedSessionIdValid();
  }

  @Override
  public boolean isRequestedSessionIdFromCookie() {
    return delegate.isRequestedSessionIdFromCookie();
  }

  @Override
  public boolean isRequestedSessionIdFromURL() {
    return delegate.isRequestedSessionIdFromURL();
  }

  @Override
  @SuppressWarnings("deprecation")
  public boolean isRequestedSessionIdFromUrl() {
    return delegate.isRequestedSessionIdFromURL();
  }

  @Override
  public HttpSession getSession(boolean create) {
    throw unsupported("getSession");
  }

  @Override
  public HttpSession getSession() {
    throw unsupported("getSession");
  }

  @Override
  public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
    throw unsupported("authenticate");
  }

  @Override
  public void login(String username, String password) throws ServletException {
    throw unsupported("login");
  }

  @Override
  public void logout() throws ServletException {
    throw unsupported("logout");
  }

  @Override
  public java.util.Collection<Part> getParts() throws IOException, ServletException {
    throw unsupported("getParts");
  }

  @Override
  public Part getPart(String name) throws IOException, ServletException {
    throw unsupported("getPart");
  }

  @Override
  public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
    throw unsupported("upgrade");
  }

  // --- ServletRequest ---

  @Override
  public Object getAttribute(String name) {
    return delegate.getAttribute(name);
  }

  @Override
  public Enumeration<String> getAttributeNames() {
    return delegate.getAttributeNames();
  }

  @Override
  public String getCharacterEncoding() {
    return delegate.getCharacterEncoding();
  }

  @Override
  public void setCharacterEncoding(String env) throws java.io.UnsupportedEncodingException {
    delegate.setCharacterEncoding(env);
  }

  @Override
  public int getContentLength() {
    return delegate.getContentLength();
  }

  @Override
  public long getContentLengthLong() {
    return delegate.getContentLengthLong();
  }

  @Override
  public String getContentType() {
    return delegate.getContentType();
  }

  @Override
  public ServletInputStream getInputStream() throws IOException {
    final jakarta.servlet.ServletInputStream in = delegate.getInputStream();
    return in == null ? null : new JakartaToJavaxServletInputStream(in);
  }

  @Override
  public String getParameter(String name) {
    return delegate.getParameter(name);
  }

  @Override
  public Enumeration<String> getParameterNames() {
    return delegate.getParameterNames();
  }

  @Override
  public String[] getParameterValues(String name) {
    return delegate.getParameterValues(name);
  }

  @Override
  public Map<String, String[]> getParameterMap() {
    return delegate.getParameterMap();
  }

  @Override
  public String getProtocol() {
    return delegate.getProtocol();
  }

  @Override
  public String getScheme() {
    return delegate.getScheme();
  }

  @Override
  public String getServerName() {
    return delegate.getServerName();
  }

  @Override
  public int getServerPort() {
    return delegate.getServerPort();
  }

  @Override
  public BufferedReader getReader() throws IOException {
    return delegate.getReader();
  }

  @Override
  public String getRemoteAddr() {
    return delegate.getRemoteAddr();
  }

  @Override
  public String getRemoteHost() {
    return delegate.getRemoteHost();
  }

  @Override
  public void setAttribute(String name, Object o) {
    delegate.setAttribute(name, o);
  }

  @Override
  public void removeAttribute(String name) {
    delegate.removeAttribute(name);
  }

  @Override
  public Locale getLocale() {
    return delegate.getLocale();
  }

  @Override
  public Enumeration<Locale> getLocales() {
    return delegate.getLocales();
  }

  @Override
  public boolean isSecure() {
    return delegate.isSecure();
  }

  @Override
  @SuppressWarnings("deprecation")
  public String getRealPath(String path) {
    throw unsupported("getRealPath");
  }

  @Override
  public int getRemotePort() {
    return delegate.getRemotePort();
  }

  @Override
  public String getLocalName() {
    return delegate.getLocalName();
  }

  @Override
  public String getLocalAddr() {
    return delegate.getLocalAddr();
  }

  @Override
  public int getLocalPort() {
    return delegate.getLocalPort();
  }

  @Override
  public ServletContext getServletContext() {
    if (servletContext == null) {
      final jakarta.servlet.ServletContext ctx = delegate.getServletContext();
      servletContext = ctx == null ? null : new JakartaToJavaxServletContext(ctx);
    }
    return servletContext;
  }

  @Override
  public RequestDispatcher getRequestDispatcher(String path) {
    throw unsupported("getRequestDispatcher");
  }

  @Override
  public AsyncContext startAsync() throws IllegalStateException {
    throw unsupported("startAsync");
  }

  @Override
  public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
    throw unsupported("startAsync");
  }

  @Override
  public boolean isAsyncStarted() {
    return false;
  }

  @Override
  public boolean isAsyncSupported() {
    return false;
  }

  @Override
  public AsyncContext getAsyncContext() {
    throw unsupported("getAsyncContext");
  }

  @Override
  public DispatcherType getDispatcherType() {
    final jakarta.servlet.DispatcherType type = delegate.getDispatcherType();
    return type == null ? null : DispatcherType.valueOf(type.name());
  }

  private static UnsupportedOperationException unsupported(String method) {
    return new UnsupportedOperationException(
        "HttpServletRequest." + method + " is not supported by the Hadoop auth javax bridge");
  }
}
