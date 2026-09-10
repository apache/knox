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
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

/**
 * A {@code javax.servlet.http.HttpServletResponse} view over a
 * {@code jakarta.servlet.http.HttpServletResponse}, so Hadoop's {@code AuthenticationFilter}
 * can set the authentication cookie, headers and error/redirect status on the outgoing
 * response. Every setter is forwarded to the jakarta response.
 *
 * <p>{@code setStatus(int, String)} — removed in Servlet 6 / jakarta — is mapped to
 * {@link jakarta.servlet.http.HttpServletResponse#setStatus(int)} (the reason phrase is
 * dropped, matching the container's own behavior). The deprecated {@code encodeUrl} /
 * {@code encodeRedirectUrl} aliases forward to their non-deprecated counterparts.</p>
 */
class JakartaToJavaxHttpServletResponse implements HttpServletResponse {

  private final jakarta.servlet.http.HttpServletResponse delegate;

  JakartaToJavaxHttpServletResponse(jakarta.servlet.http.HttpServletResponse delegate) {
    this.delegate = delegate;
  }

  // --- HttpServletResponse ---

  @Override
  public void addCookie(Cookie cookie) {
    delegate.addCookie(toJakartaCookie(cookie));
  }

  static jakarta.servlet.http.Cookie toJakartaCookie(Cookie source) {
    final jakarta.servlet.http.Cookie target =
        new jakarta.servlet.http.Cookie(source.getName(), source.getValue());
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
  public boolean containsHeader(String name) {
    return delegate.containsHeader(name);
  }

  @Override
  public String encodeURL(String url) {
    return delegate.encodeURL(url);
  }

  @Override
  public String encodeRedirectURL(String url) {
    return delegate.encodeRedirectURL(url);
  }

  @Override
  @SuppressWarnings("deprecation")
  public String encodeUrl(String url) {
    return delegate.encodeURL(url);
  }

  @Override
  @SuppressWarnings("deprecation")
  public String encodeRedirectUrl(String url) {
    return delegate.encodeRedirectURL(url);
  }

  @Override
  public void sendError(int sc, String msg) throws IOException {
    delegate.sendError(sc, msg);
  }

  @Override
  public void sendError(int sc) throws IOException {
    delegate.sendError(sc);
  }

  @Override
  public void sendRedirect(String location) throws IOException {
    delegate.sendRedirect(location);
  }

  @Override
  public void setDateHeader(String name, long date) {
    delegate.setDateHeader(name, date);
  }

  @Override
  public void addDateHeader(String name, long date) {
    delegate.addDateHeader(name, date);
  }

  @Override
  public void setHeader(String name, String value) {
    delegate.setHeader(name, value);
  }

  @Override
  public void addHeader(String name, String value) {
    delegate.addHeader(name, value);
  }

  @Override
  public void setIntHeader(String name, int value) {
    delegate.setIntHeader(name, value);
  }

  @Override
  public void addIntHeader(String name, int value) {
    delegate.addIntHeader(name, value);
  }

  @Override
  public void setStatus(int sc) {
    delegate.setStatus(sc);
  }

  @Override
  @SuppressWarnings("deprecation")
  public void setStatus(int sc, String sm) {
    // Servlet 6 / jakarta removed setStatus(int, String); the reason phrase is no longer
    // settable, so forward the status code only.
    delegate.setStatus(sc);
  }

  @Override
  public int getStatus() {
    return delegate.getStatus();
  }

  @Override
  public String getHeader(String name) {
    return delegate.getHeader(name);
  }

  @Override
  public Collection<String> getHeaders(String name) {
    return delegate.getHeaders(name);
  }

  @Override
  public Collection<String> getHeaderNames() {
    return delegate.getHeaderNames();
  }

  // --- ServletResponse ---

  @Override
  public String getCharacterEncoding() {
    return delegate.getCharacterEncoding();
  }

  @Override
  public String getContentType() {
    return delegate.getContentType();
  }

  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    final jakarta.servlet.ServletOutputStream out = delegate.getOutputStream();
    return out == null ? null : new JakartaToJavaxServletOutputStream(out);
  }

  @Override
  public PrintWriter getWriter() throws IOException {
    return delegate.getWriter();
  }

  @Override
  public void setCharacterEncoding(String charset) {
    delegate.setCharacterEncoding(charset);
  }

  @Override
  public void setContentLength(int len) {
    delegate.setContentLength(len);
  }

  @Override
  public void setContentLengthLong(long len) {
    delegate.setContentLengthLong(len);
  }

  @Override
  public void setContentType(String type) {
    delegate.setContentType(type);
  }

  @Override
  public void setBufferSize(int size) {
    delegate.setBufferSize(size);
  }

  @Override
  public int getBufferSize() {
    return delegate.getBufferSize();
  }

  @Override
  public void flushBuffer() throws IOException {
    delegate.flushBuffer();
  }

  @Override
  public void resetBuffer() {
    delegate.resetBuffer();
  }

  @Override
  public boolean isCommitted() {
    return delegate.isCommitted();
  }

  @Override
  public void reset() {
    delegate.reset();
  }

  @Override
  public void setLocale(Locale loc) {
    delegate.setLocale(loc);
  }

  @Override
  public Locale getLocale() {
    return delegate.getLocale();
  }
}
