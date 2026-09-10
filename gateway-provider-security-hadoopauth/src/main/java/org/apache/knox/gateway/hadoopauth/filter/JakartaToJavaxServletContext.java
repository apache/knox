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

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Map;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;

/**
 * A {@code javax.servlet.ServletContext} view over a {@code jakarta.servlet.ServletContext}.
 *
 * <p>This is part of the javax&harr;jakarta bridge that lets Knox keep delegating to
 * Hadoop's {@code AuthenticationFilter} (compiled against {@code javax.servlet}) while the
 * gateway itself runs on Jetty EE10 / {@code jakarta.servlet}. Only the surface Hadoop's
 * filter and its {@code SignerSecretProvider} bootstrap actually touch (attributes and
 * init-parameters) is forwarded; the servlet-3.0 programmatic registration / listener /
 * session-tracking APIs are never used from this path and throw
 * {@link UnsupportedOperationException} if reached.</p>
 */
class JakartaToJavaxServletContext implements ServletContext {

  private final jakarta.servlet.ServletContext delegate;

  JakartaToJavaxServletContext(jakarta.servlet.ServletContext delegate) {
    this.delegate = delegate;
  }

  @Override
  public Object getAttribute(String name) {
    return delegate.getAttribute(name);
  }

  @Override
  public Enumeration<String> getAttributeNames() {
    return delegate.getAttributeNames();
  }

  @Override
  public void setAttribute(String name, Object object) {
    delegate.setAttribute(name, object);
  }

  @Override
  public void removeAttribute(String name) {
    delegate.removeAttribute(name);
  }

  @Override
  public String getInitParameter(String name) {
    return delegate.getInitParameter(name);
  }

  @Override
  public Enumeration<String> getInitParameterNames() {
    return delegate.getInitParameterNames();
  }

  @Override
  public boolean setInitParameter(String name, String value) {
    return delegate.setInitParameter(name, value);
  }

  @Override
  public String getContextPath() {
    return delegate.getContextPath();
  }

  @Override
  public int getMajorVersion() {
    return delegate.getMajorVersion();
  }

  @Override
  public int getMinorVersion() {
    return delegate.getMinorVersion();
  }

  @Override
  public int getEffectiveMajorVersion() {
    return delegate.getEffectiveMajorVersion();
  }

  @Override
  public int getEffectiveMinorVersion() {
    return delegate.getEffectiveMinorVersion();
  }

  @Override
  public String getMimeType(String file) {
    return delegate.getMimeType(file);
  }

  @Override
  public Set<String> getResourcePaths(String path) {
    return delegate.getResourcePaths(path);
  }

  @Override
  public URL getResource(String path) throws MalformedURLException {
    return delegate.getResource(path);
  }

  @Override
  public InputStream getResourceAsStream(String path) {
    return delegate.getResourceAsStream(path);
  }

  @Override
  public String getRealPath(String path) {
    return delegate.getRealPath(path);
  }

  @Override
  public String getServerInfo() {
    return delegate.getServerInfo();
  }

  @Override
  public String getServletContextName() {
    return delegate.getServletContextName();
  }

  @Override
  public String getVirtualServerName() {
    return delegate.getVirtualServerName();
  }

  @Override
  public ClassLoader getClassLoader() {
    return delegate.getClassLoader();
  }

  @Override
  public void log(String msg) {
    delegate.log(msg);
  }

  @Override
  @SuppressWarnings("deprecation")
  public void log(Exception exception, String msg) {
    delegate.log(msg, exception);
  }

  @Override
  public void log(String message, Throwable throwable) {
    delegate.log(message, throwable);
  }

  // --- Unsupported: servlet-container programmatic APIs never used on the Hadoop auth path ---

  @Override
  public ServletContext getContext(String uripath) {
    throw unsupported("getContext");
  }

  @Override
  public RequestDispatcher getRequestDispatcher(String path) {
    throw unsupported("getRequestDispatcher");
  }

  @Override
  public RequestDispatcher getNamedDispatcher(String name) {
    throw unsupported("getNamedDispatcher");
  }

  @Override
  @SuppressWarnings("deprecation")
  public Servlet getServlet(String name) throws ServletException {
    throw unsupported("getServlet");
  }

  @Override
  @SuppressWarnings("deprecation")
  public Enumeration<Servlet> getServlets() {
    throw unsupported("getServlets");
  }

  @Override
  @SuppressWarnings("deprecation")
  public Enumeration<String> getServletNames() {
    throw unsupported("getServletNames");
  }

  @Override
  public ServletRegistration.Dynamic addServlet(String servletName, String className) {
    throw unsupported("addServlet");
  }

  @Override
  public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet) {
    throw unsupported("addServlet");
  }

  @Override
  public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
    throw unsupported("addServlet");
  }

  @Override
  public <T extends Servlet> T createServlet(Class<T> clazz) throws ServletException {
    throw unsupported("createServlet");
  }

  @Override
  public ServletRegistration getServletRegistration(String servletName) {
    throw unsupported("getServletRegistration");
  }

  @Override
  public Map<String, ? extends ServletRegistration> getServletRegistrations() {
    throw unsupported("getServletRegistrations");
  }

  @Override
  public FilterRegistration.Dynamic addFilter(String filterName, String className) {
    throw unsupported("addFilter");
  }

  @Override
  public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
    throw unsupported("addFilter");
  }

  @Override
  public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) {
    throw unsupported("addFilter");
  }

  @Override
  public <T extends Filter> T createFilter(Class<T> clazz) throws ServletException {
    throw unsupported("createFilter");
  }

  @Override
  public FilterRegistration getFilterRegistration(String filterName) {
    throw unsupported("getFilterRegistration");
  }

  @Override
  public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
    throw unsupported("getFilterRegistrations");
  }

  @Override
  public SessionCookieConfig getSessionCookieConfig() {
    throw unsupported("getSessionCookieConfig");
  }

  @Override
  public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
    throw unsupported("setSessionTrackingModes");
  }

  @Override
  public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
    throw unsupported("getDefaultSessionTrackingModes");
  }

  @Override
  public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
    throw unsupported("getEffectiveSessionTrackingModes");
  }

  @Override
  public void addListener(String className) {
    throw unsupported("addListener");
  }

  @Override
  public <T extends EventListener> void addListener(T t) {
    throw unsupported("addListener");
  }

  @Override
  public void addListener(Class<? extends EventListener> listenerClass) {
    throw unsupported("addListener");
  }

  @Override
  public <T extends EventListener> T createListener(Class<T> clazz) throws ServletException {
    throw unsupported("createListener");
  }

  @Override
  public JspConfigDescriptor getJspConfigDescriptor() {
    throw unsupported("getJspConfigDescriptor");
  }

  @Override
  public void declareRoles(String... roleNames) {
    throw unsupported("declareRoles");
  }

  private static UnsupportedOperationException unsupported(String method) {
    return new UnsupportedOperationException(
        "ServletContext." + method + " is not supported by the Hadoop auth javax bridge");
  }
}
