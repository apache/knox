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

import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.captureInt;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createMockBuilder;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.knox.gateway.GatewayFilter;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.provider.federation.jwt.filter.AbstractJWTFilter;
import org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.topology.Topology;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.AccessController;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class HadoopAuthFilterTest {
  private static final String SERVICE_URL = "https://localhost:8888/gateway/sandbox";
  private static final String JWKS_PATH = "/knoxtoken/api/v1/jwks.json";

  @Test
  public void testHadoopAuthFilterAliases() throws Exception {
    String aliasKey = "signature.secret";
    String aliasConfigKey = "${ALIAS=" + aliasKey + "}";
    String aliasValue = "password";
    String clusterName = "Sample";

    Topology topology = new Topology();
    topology.setName(clusterName);

    AliasService as = createMock(AliasService.class);
    expect(as.getPasswordFromAliasForCluster(clusterName, aliasKey))
        .andReturn(aliasValue.toCharArray()).atLeastOnce();

    String configPrefix = "hadoop.auth.config.";

    Map<String, String> props = new HashMap<>();
    props.put("clusterName", clusterName);
    props.put(configPrefix + "signature.secret", aliasConfigKey);
    props.put(configPrefix + "test", "abc");

    FilterConfig filterConfig = createMock(FilterConfig.class);
    expect(filterConfig.getInitParameter(anyString()))
        .andAnswer(() -> props.get(getCurrentArguments()[0].toString()))
        .atLeastOnce();
    expect(filterConfig.getInitParameterNames()).andReturn(Collections.enumeration(props.keySet())).atLeastOnce();

    replay(filterConfig, as);

    HadoopAuthFilter hadoopAuthFilter = new HadoopAuthFilter();

    Properties configuration = hadoopAuthFilter.getConfiguration(as, configPrefix, filterConfig);
    assertEquals(aliasValue, configuration.getProperty(aliasKey));
    assertEquals("abc", configuration.getProperty("test"));

    verify(filterConfig, as);
  }

  @Test
  public void testHadoopAuthFilterIgnoreDoAs() throws Exception {
    Topology topology = new Topology();
    topology.setName("Sample");

    ServletContext servletContext = createMock(ServletContext.class);
    expect(servletContext.getAttribute("signer.secret.provider.object")).andReturn(null).atLeastOnce();

    FilterConfig filterConfig = createMock(FilterConfig.class);
    expect(filterConfig.getInitParameter("config.prefix"))
        .andReturn("some.prefix")
        .atLeastOnce();
    expect(filterConfig.getInitParameterNames())
        .andReturn(Collections.enumeration(Collections.singleton(GatewayConfig.PROXYUSER_SERVICES_IGNORE_DOAS)))
        .atLeastOnce();
    expect(filterConfig.getInitParameter(GatewayConfig.PROXYUSER_SERVICES_IGNORE_DOAS))
        .andReturn("Knox, hdfs,TesT") // Spacing and case set on purpose
        .atLeastOnce();
    expect(filterConfig.getInitParameter("support.jwt")).andReturn("false").anyTimes();
    expect(filterConfig.getServletContext()).andReturn(servletContext).atLeastOnce();
    expect(filterConfig.getInitParameter("hadoop.auth.unauthenticated.path.list")).andReturn(null).anyTimes();

    Properties configProperties = createMock(Properties.class);
    expect(configProperties.getProperty("signature.secret.file")).andReturn("signature.secret.file").atLeastOnce();
    expect(configProperties.getProperty(anyString(), anyString())).andAnswer(() -> {
      Object[] args = getCurrentArguments();

      if ("type".equals(args[0])) {
        return "simple"; // This is "simple", rather than "kerberos" to avoid the super class' init logic
      } else {
        return (String) args[1];
      }
    }).atLeastOnce();

    HadoopAuthFilter hadoopAuthFilter = createMockBuilder(HadoopAuthFilter.class)
        .addMockedMethod("getConfiguration", String.class, FilterConfig.class)
        .withConstructor()
        .createMock();
    expect(hadoopAuthFilter.getConfiguration(eq("some.prefix."), eq(filterConfig)))
        .andReturn(configProperties)
        .atLeastOnce();

    replay(filterConfig, configProperties, hadoopAuthFilter, servletContext);

    hadoopAuthFilter.init(filterConfig);

    assertTrue(hadoopAuthFilter.ignoreDoAs("knox"));
    assertTrue(hadoopAuthFilter.ignoreDoAs("hdfs"));
    assertTrue(hadoopAuthFilter.ignoreDoAs("test"));
    assertTrue(hadoopAuthFilter.ignoreDoAs("TEST"));
    assertTrue(hadoopAuthFilter.ignoreDoAs(null));
    assertTrue(hadoopAuthFilter.ignoreDoAs(""));
    assertFalse(hadoopAuthFilter.ignoreDoAs("hive"));
    assertFalse(hadoopAuthFilter.ignoreDoAs("HivE"));

    verify(filterConfig, configProperties, hadoopAuthFilter, servletContext);
  }

  @Test
  public void shouldNotUseJwtFilterIfProviderParamIsNotSet() throws Exception {
    testIfJwtSupported(null);
  }

  @Test
  public void shouldNotUseJwtFilterIfProviderParamIsFalse() throws Exception {
    testIfJwtSupported("false");
  }

  @Test
  public void shouldUseJwtFilterIfProviderParamIsTrue() throws Exception {
    testIfJwtSupported("true");
  }

  @Test
  public void testUnauthenticatedList() throws Exception {
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);

    GatewayFilter.Holder filterConfig = createMock(GatewayFilter.Holder.class);
    expect(filterConfig.getInitParameterNames()).andReturn(Collections.enumeration(Collections.emptyList()));
    expect(filterConfig.getInitParameter(GatewayConfig.PROXYUSER_SERVICES_IGNORE_DOAS)).andReturn("service").atLeastOnce();
    expect(filterConfig.getInitParameter("config.prefix")).andReturn("some.prefix").atLeastOnce();
    expect(filterConfig.getInitParameter("support.jwt")).andReturn("false").anyTimes();
    expect(filterConfig.getInitParameter("hadoop.auth.unauthenticated.path.list")).andReturn(null).anyTimes();


    EasyMock.expect(response.encodeRedirectURL(SERVICE_URL)).andReturn(SERVICE_URL);
    EasyMock.expect(response.getOutputStream()).andAnswer(
        DummyServletOutputStream::new).anyTimes();
    EasyMock.expect(request.getPathInfo()).andReturn(JWKS_PATH).anyTimes();
    EasyMock.expect(request.getQueryString()).andReturn(null);

    final ServletContext servletContext = createMock(ServletContext.class);
    expect(servletContext.getAttribute("signer.secret.provider.object")).andReturn(null).atLeastOnce();
    expect(filterConfig.getServletContext()).andReturn(servletContext).atLeastOnce();

    final HadoopAuthFilter hadoopAuthFilter = createMockBuilder(HadoopAuthFilter.class).addMockedMethod("getConfiguration", String.class, FilterConfig.class).withConstructor()
        .createMock();
    final Properties config = new Properties();
    config.put("type", "simple");
    expect(hadoopAuthFilter.getConfiguration(eq("some.prefix."), eq(filterConfig))).andReturn(config).atLeastOnce();

    replay(servletContext, filterConfig, hadoopAuthFilter, request, response);

    hadoopAuthFilter.init(filterConfig);
    DummyFilterChain chain = new DummyFilterChain();
    hadoopAuthFilter.doFilter(request, response, chain);
    Assert.assertTrue("doFilterCalled should be true.", chain.doFilterCalled );
    /* make sure the principal is anonymous */
    Assert.assertEquals("anonymous", chain.subject.getPrincipals().stream().findFirst().get().getName());
  }

  /**
   * Test to check if this can be used to bypass authentication
   * @throws Exception
   */
  @Test
  public void testNegativeUnauthenticatedListSemicolon() throws Exception {
    final String request_semicolon_path = "/knoxtoken/api/v1/jwks.json;favicon.ico";
    final Capture<Integer> capturedError = EasyMock.newCapture();
    final Capture<String> capturedErrorMessage = EasyMock.newCapture();

    HttpServletRequest request_semicolon = EasyMock.createNiceMock(HttpServletRequest.class);

    GatewayFilter.Holder filterConfig = createMock(GatewayFilter.Holder.class);
    expect(filterConfig.getInitParameterNames()).andReturn(Collections.enumeration(Collections.emptyList()));
    expect(filterConfig.getInitParameter(GatewayConfig.PROXYUSER_SERVICES_IGNORE_DOAS)).andReturn("service").atLeastOnce();
    expect(filterConfig.getInitParameter("config.prefix")).andReturn("some.prefix").atLeastOnce();
    expect(filterConfig.getInitParameter("support.jwt")).andReturn("false").anyTimes();
    /* update the default list to use favicon.ico */
    expect(filterConfig.getInitParameter("hadoop.auth.unauthenticated.path.list")).andReturn(request_semicolon_path).anyTimes();

    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    /* capture errors */
    response.sendError(captureInt(capturedError), capture(capturedErrorMessage));
    expectLastCall().anyTimes();
    EasyMock.expect(response.encodeRedirectURL(SERVICE_URL)).andReturn(SERVICE_URL);
    EasyMock.expect(response.getOutputStream()).andAnswer(
        DummyServletOutputStream::new).anyTimes();


    replay(response);

    EasyMock.expect(request_semicolon.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL+request_semicolon_path)).anyTimes();
    /* try attaching favicon.ico in path */
    EasyMock.expect(request_semicolon.getPathInfo()).andReturn("/knoxtoken/api/v1/token;favicon.ico").anyTimes();
    EasyMock.expect(request_semicolon.getQueryString()).andReturn(null);


    final ServletContext servletContext = createMock(ServletContext.class);
    expect(servletContext.getAttribute("signer.secret.provider.object")).andReturn(null).atLeastOnce();
    expect(filterConfig.getServletContext()).andReturn(servletContext).atLeastOnce();

    final HadoopAuthFilter hadoopAuthFilter = createMockBuilder(HadoopAuthFilter.class).addMockedMethod("getConfiguration", String.class, FilterConfig.class).withConstructor()
        .createMock();
    final Properties config = new Properties();
    config.put("type", "simple");
    expect(hadoopAuthFilter.getConfiguration(eq("some.prefix."), eq(filterConfig))).andReturn(config).atLeastOnce();

    replay(servletContext, filterConfig, hadoopAuthFilter, request_semicolon);

    hadoopAuthFilter.init(filterConfig);
    DummyFilterChain chain = new DummyFilterChain();
    hadoopAuthFilter.doFilter(request_semicolon, response, chain);
    Assert.assertFalse("doFilterCalled should be false.", chain.doFilterCalled );
    /* make sure we get 403 */
    Assert.assertEquals(403, capturedError.getValue().intValue());
    Assert.assertEquals("Authentication required", capturedErrorMessage.getValue());
  }

  /**
   * Test to check if this can be used to bypass authentication
   * @throws Exception
   */
  @Test
  public void testNegativeUnauthenticatedListQuery() throws Exception {
    final String request_semicolon_path = "/knoxtoken/api/v1/jwks.json?favicon.ico";
    final Capture<Integer> capturedError = EasyMock.newCapture();
    final Capture<String> capturedErrorMessage = EasyMock.newCapture();

    HttpServletRequest request_query = EasyMock.createNiceMock(HttpServletRequest.class);

    GatewayFilter.Holder filterConfig = createMock(GatewayFilter.Holder.class);
    expect(filterConfig.getInitParameterNames()).andReturn(Collections.enumeration(Collections.emptyList()));
    expect(filterConfig.getInitParameter(GatewayConfig.PROXYUSER_SERVICES_IGNORE_DOAS)).andReturn("service").atLeastOnce();
    expect(filterConfig.getInitParameter("config.prefix")).andReturn("some.prefix").atLeastOnce();
    expect(filterConfig.getInitParameter("support.jwt")).andReturn("false").anyTimes();
    /* update the default list to use favicon.ico */
    expect(filterConfig.getInitParameter("hadoop.auth.unauthenticated.path.list")).andReturn(request_semicolon_path).anyTimes();

    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    /* capture errors */
    response.sendError(captureInt(capturedError), capture(capturedErrorMessage));
    expectLastCall().anyTimes();
    EasyMock.expect(response.encodeRedirectURL(SERVICE_URL)).andReturn(SERVICE_URL);
    EasyMock.expect(response.getOutputStream()).andAnswer(
        DummyServletOutputStream::new).anyTimes();


    replay(response);

    EasyMock.expect(request_query.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL+request_semicolon_path)).anyTimes();
    /* try attaching favicon.ico in path */
    EasyMock.expect(request_query.getPathInfo()).andReturn("/knoxtoken/api/v1/token;favicon.ico").anyTimes();
    EasyMock.expect(request_query.getQueryString()).andReturn(null);


    final ServletContext servletContext = createMock(ServletContext.class);
    expect(servletContext.getAttribute("signer.secret.provider.object")).andReturn(null).atLeastOnce();
    expect(filterConfig.getServletContext()).andReturn(servletContext).atLeastOnce();

    final HadoopAuthFilter hadoopAuthFilter = createMockBuilder(HadoopAuthFilter.class).addMockedMethod("getConfiguration", String.class, FilterConfig.class).withConstructor()
        .createMock();
    final Properties config = new Properties();
    config.put("type", "simple");
    expect(hadoopAuthFilter.getConfiguration(eq("some.prefix."), eq(filterConfig))).andReturn(config).atLeastOnce();

    replay(servletContext, filterConfig, hadoopAuthFilter, request_query);

    hadoopAuthFilter.init(filterConfig);
    DummyFilterChain chain = new DummyFilterChain();
    hadoopAuthFilter.doFilter(request_query, response, chain);
    Assert.assertFalse("doFilterCalled should be false.", chain.doFilterCalled );
    /* make sure we get 403 */
    Assert.assertEquals(403, capturedError.getValue().intValue());
    Assert.assertEquals("Authentication required", capturedErrorMessage.getValue());
  }

  /**
   * Test to check if this can be used to bypass authentication
   * @throws Exception
   */
  @Test
  public void testNegativeUnauthenticatedListAmpersand() throws Exception {
    final String request_semicolon_path = "/knoxtoken/api/v1/jwks.json&favicon.ico";
    final Capture<Integer> capturedError = EasyMock.newCapture();
    final Capture<String> capturedErrorMessage = EasyMock.newCapture();

    HttpServletRequest request_ampersand = EasyMock.createNiceMock(HttpServletRequest.class);

    GatewayFilter.Holder filterConfig = createMock(GatewayFilter.Holder.class);
    expect(filterConfig.getInitParameterNames()).andReturn(Collections.enumeration(Collections.emptyList()));
    expect(filterConfig.getInitParameter(GatewayConfig.PROXYUSER_SERVICES_IGNORE_DOAS)).andReturn("service").atLeastOnce();
    expect(filterConfig.getInitParameter("config.prefix")).andReturn("some.prefix").atLeastOnce();
    expect(filterConfig.getInitParameter("support.jwt")).andReturn("false").anyTimes();
    /* update the default list to use favicon.ico */
    expect(filterConfig.getInitParameter("hadoop.auth.unauthenticated.path.list")).andReturn(request_semicolon_path).anyTimes();

    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    /* capture errors */
    response.sendError(captureInt(capturedError), capture(capturedErrorMessage));
    expectLastCall().anyTimes();
    EasyMock.expect(response.encodeRedirectURL(SERVICE_URL)).andReturn(SERVICE_URL);
    EasyMock.expect(response.getOutputStream()).andAnswer(
        DummyServletOutputStream::new).anyTimes();


    replay(response);

    EasyMock.expect(request_ampersand.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL+request_semicolon_path)).anyTimes();
    /* try attaching favicon.ico in path */
    EasyMock.expect(request_ampersand.getPathInfo()).andReturn("/knoxtoken/api/v1/token;favicon.ico").anyTimes();
    EasyMock.expect(request_ampersand.getQueryString()).andReturn(null);


    final ServletContext servletContext = createMock(ServletContext.class);
    expect(servletContext.getAttribute("signer.secret.provider.object")).andReturn(null).atLeastOnce();
    expect(filterConfig.getServletContext()).andReturn(servletContext).atLeastOnce();

    final HadoopAuthFilter hadoopAuthFilter = createMockBuilder(HadoopAuthFilter.class).addMockedMethod("getConfiguration", String.class, FilterConfig.class).withConstructor()
        .createMock();
    final Properties config = new Properties();
    config.put("type", "simple");
    expect(hadoopAuthFilter.getConfiguration(eq("some.prefix."), eq(filterConfig))).andReturn(config).atLeastOnce();

    replay(servletContext, filterConfig, hadoopAuthFilter, request_ampersand);

    hadoopAuthFilter.init(filterConfig);
    DummyFilterChain chain = new DummyFilterChain();
    hadoopAuthFilter.doFilter(request_ampersand, response, chain);
    Assert.assertFalse("doFilterCalled should be false.", chain.doFilterCalled );
    /* make sure we get 403 */
    Assert.assertEquals(403, capturedError.getValue().intValue());
    Assert.assertEquals("Authentication required", capturedErrorMessage.getValue());
  }

  /**
   * Test to check if this can be used to bypass authentication
   * @throws Exception
   */
  @Test
  public void testNegativeUnauthenticatedListDash() throws Exception {
    final String request_semicolon_path = "/knoxtoken/api/v1/jwks.json-favicon.ico";
    final Capture<Integer> capturedError = EasyMock.newCapture();
    final Capture<String> capturedErrorMessage = EasyMock.newCapture();

    HttpServletRequest request_dash = EasyMock.createNiceMock(HttpServletRequest.class);

    GatewayFilter.Holder filterConfig = createMock(GatewayFilter.Holder.class);
    expect(filterConfig.getInitParameterNames()).andReturn(Collections.enumeration(Collections.emptyList()));
    expect(filterConfig.getInitParameter(GatewayConfig.PROXYUSER_SERVICES_IGNORE_DOAS)).andReturn("service").atLeastOnce();
    expect(filterConfig.getInitParameter("config.prefix")).andReturn("some.prefix").atLeastOnce();
    expect(filterConfig.getInitParameter("support.jwt")).andReturn("false").anyTimes();
    /* update the default list to use favicon.ico */
    expect(filterConfig.getInitParameter("hadoop.auth.unauthenticated.path.list")).andReturn(request_semicolon_path).anyTimes();

    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    /* capture errors */
    response.sendError(captureInt(capturedError), capture(capturedErrorMessage));
    expectLastCall().anyTimes();
    EasyMock.expect(response.encodeRedirectURL(SERVICE_URL)).andReturn(SERVICE_URL);
    EasyMock.expect(response.getOutputStream()).andAnswer(
        DummyServletOutputStream::new).anyTimes();


    replay(response);

    EasyMock.expect(request_dash.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL+request_semicolon_path)).anyTimes();
    /* try attaching favicon.ico in path */
    EasyMock.expect(request_dash.getPathInfo()).andReturn("/knoxtoken/api/v1/token;favicon.ico").anyTimes();
    EasyMock.expect(request_dash.getQueryString()).andReturn(null);


    final ServletContext servletContext = createMock(ServletContext.class);
    expect(servletContext.getAttribute("signer.secret.provider.object")).andReturn(null).atLeastOnce();
    expect(filterConfig.getServletContext()).andReturn(servletContext).atLeastOnce();

    final HadoopAuthFilter hadoopAuthFilter = createMockBuilder(HadoopAuthFilter.class).addMockedMethod("getConfiguration", String.class, FilterConfig.class).withConstructor()
        .createMock();
    final Properties config = new Properties();
    config.put("type", "simple");
    expect(hadoopAuthFilter.getConfiguration(eq("some.prefix."), eq(filterConfig))).andReturn(config).atLeastOnce();

    replay(servletContext, filterConfig, hadoopAuthFilter, request_dash);

    hadoopAuthFilter.init(filterConfig);
    DummyFilterChain chain = new DummyFilterChain();
    hadoopAuthFilter.doFilter(request_dash, response, chain);
    Assert.assertFalse("doFilterCalled should be false.", chain.doFilterCalled );
    /* make sure we get 403 */
    Assert.assertEquals(403, capturedError.getValue().intValue());
    Assert.assertEquals("Authentication required", capturedErrorMessage.getValue());
  }

  /**
   * Test to check if this can be used to bypass authentication
   * @throws Exception
   */
  @Test
  public void testNegativeUnauthenticatedListSpace() throws Exception {
    final String request_semicolon_path = "/knoxtoken/api/v1/jwks.json favicon.ico";
    final Capture<Integer> capturedError = EasyMock.newCapture();
    final Capture<String> capturedErrorMessage = EasyMock.newCapture();

    HttpServletRequest request_space = EasyMock.createNiceMock(HttpServletRequest.class);


    GatewayFilter.Holder filterConfig = createMock(GatewayFilter.Holder.class);
    expect(filterConfig.getInitParameterNames()).andReturn(Collections.enumeration(Collections.emptyList()));
    expect(filterConfig.getInitParameter(GatewayConfig.PROXYUSER_SERVICES_IGNORE_DOAS)).andReturn("service").atLeastOnce();
    expect(filterConfig.getInitParameter("config.prefix")).andReturn("some.prefix").atLeastOnce();
    expect(filterConfig.getInitParameter("support.jwt")).andReturn("false").anyTimes();
    /* update the default list to use favicon.ico */
    expect(filterConfig.getInitParameter("hadoop.auth.unauthenticated.path.list")).andReturn(request_semicolon_path).anyTimes();

    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    /* capture errors */
    response.sendError(captureInt(capturedError), capture(capturedErrorMessage));
    expectLastCall().anyTimes();
    EasyMock.expect(response.encodeRedirectURL(SERVICE_URL)).andReturn(SERVICE_URL);
    EasyMock.expect(response.getOutputStream()).andAnswer(
        DummyServletOutputStream::new).anyTimes();


    replay(response);

    EasyMock.expect(request_space.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL+request_semicolon_path)).anyTimes();
    /* try attaching favicon.ico in path */
    EasyMock.expect(request_space.getPathInfo()).andReturn("/knoxtoken/api/v1/token;favicon.ico").anyTimes();
    EasyMock.expect(request_space.getQueryString()).andReturn(null);


    final ServletContext servletContext = createMock(ServletContext.class);
    expect(servletContext.getAttribute("signer.secret.provider.object")).andReturn(null).atLeastOnce();
    expect(filterConfig.getServletContext()).andReturn(servletContext).atLeastOnce();

    final HadoopAuthFilter hadoopAuthFilter = createMockBuilder(HadoopAuthFilter.class).addMockedMethod("getConfiguration", String.class, FilterConfig.class).withConstructor()
        .createMock();
    final Properties config = new Properties();
    config.put("type", "simple");
    expect(hadoopAuthFilter.getConfiguration(eq("some.prefix."), eq(filterConfig))).andReturn(config).atLeastOnce();

    replay(servletContext, filterConfig, hadoopAuthFilter, request_space);

    hadoopAuthFilter.init(filterConfig);
    DummyFilterChain chain = new DummyFilterChain();
    hadoopAuthFilter.doFilter(request_space, response, chain);
    Assert.assertFalse("doFilterCalled should be false.", chain.doFilterCalled );
    /* make sure we get 403 */
    Assert.assertEquals(403, capturedError.getValue().intValue());
    Assert.assertEquals("Authentication required", capturedErrorMessage.getValue());
  }

  public static class DummyFilterChain implements FilterChain {
    boolean doFilterCalled;
    Subject subject;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response)
        throws IOException {
      doFilterCalled = true;
      subject = Subject.getSubject( AccessController.getContext() );
    }
  }

  static class DummyServletOutputStream extends ServletOutputStream {
    @Override
    public void write(int b) {
    }

    @Override
    public void setWriteListener(WriteListener arg0) {
    }

    @Override
    public boolean isReady() {
      return false;
    }
  }

  private HadoopAuthFilter testIfJwtSupported(String supportJwt) throws Exception {
    final GatewayFilter.Holder filterConfig = createMock(GatewayFilter.Holder.class);
    expect(filterConfig.getInitParameterNames()).andReturn(Collections.enumeration(Collections.emptyList()));
    expect(filterConfig.getInitParameter(GatewayConfig.PROXYUSER_SERVICES_IGNORE_DOAS)).andReturn("service").atLeastOnce();
    expect(filterConfig.getInitParameter("config.prefix")).andReturn("some.prefix").atLeastOnce();
    expect(filterConfig.getInitParameter("support.jwt")).andReturn(supportJwt).anyTimes();
    expect(filterConfig.getInitParameter("hadoop.auth.unauthenticated.path.list")).andReturn(null).anyTimes();
    final boolean isJwtSupported = Boolean.parseBoolean(supportJwt);
    if (isJwtSupported) {
      expect(filterConfig.getInitParameter(JWTFederationFilter.KNOX_TOKEN_AUDIENCES)).andReturn(null).anyTimes();
      expect(filterConfig.getInitParameter(JWTFederationFilter.KNOX_TOKEN_QUERY_PARAM_NAME)).andReturn(null).anyTimes();
      expect(filterConfig.getInitParameter(JWTFederationFilter.JWKS_URL)).andReturn(null).anyTimes();
      expect(filterConfig.getInitParameter(JWTFederationFilter.TOKEN_PRINCIPAL_CLAIM)).andReturn(null).anyTimes();
      expect(filterConfig.getInitParameter(JWTFederationFilter.TOKEN_VERIFICATION_PEM)).andReturn(null).anyTimes();
      expect(filterConfig.getInitParameter(JWTFederationFilter.JWT_UNAUTHENTICATED_PATHS_PARAM)).andReturn(null).anyTimes();
      expect(filterConfig.getInitParameter(AbstractJWTFilter.JWT_EXPECTED_ISSUER)).andReturn(null).anyTimes();
      expect(filterConfig.getInitParameter(AbstractJWTFilter.JWT_EXPECTED_SIGALG)).andReturn(null).anyTimes();
      expect(filterConfig.getInitParameter(AbstractJWTFilter.JWT_VERIFIED_CACHE_MAX)).andReturn(null).anyTimes();
    }

    final ServletContext servletContext = createMock(ServletContext.class);
    expect(servletContext.getAttribute("signer.secret.provider.object")).andReturn(null).atLeastOnce();
    if (isJwtSupported) {
      expect(servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(null).anyTimes();
    }
    expect(filterConfig.getServletContext()).andReturn(servletContext).atLeastOnce();

    final HadoopAuthFilter hadoopAuthFilter = createMockBuilder(HadoopAuthFilter.class).addMockedMethod("getConfiguration", String.class, FilterConfig.class).withConstructor()
        .createMock();
    final Properties config = new Properties();
    config.put("type", "simple");
    expect(hadoopAuthFilter.getConfiguration(eq("some.prefix."), eq(filterConfig))).andReturn(config).atLeastOnce();

    replay(servletContext, filterConfig, hadoopAuthFilter);

    hadoopAuthFilter.init(filterConfig);

    assertEquals(isJwtSupported, hadoopAuthFilter.isJwtSupported());

    return hadoopAuthFilter;
  }
}
