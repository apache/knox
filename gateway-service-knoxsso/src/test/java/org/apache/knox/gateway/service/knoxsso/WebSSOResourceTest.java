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
package org.apache.knox.gateway.service.knoxsso;

import org.apache.http.HttpStatus;
import org.apache.knox.gateway.audit.log4j.audit.Log4jAuditor;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.util.RegExUtils;

import static org.apache.knox.gateway.services.GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Principal;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.security.auth.Subject;
import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;

/**
 * Some tests for the Knox SSO service.
 */
public class WebSSOResourceTest {

  private static RSAPublicKey gatewayPublicKey;
  private static RSAPrivateKey gatewayPrivateKey;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair keyPair = kpg.generateKeyPair();

    gatewayPublicKey = (RSAPublicKey) keyPair.getPublic();
    gatewayPrivateKey = (RSAPrivateKey) keyPair.getPrivate();
  }

  @Test
  public void testWhitelistMatching() {
    String whitelist = "^https?://.*example.com:8080/.*$;" +
        "^https?://.*example.com/.*$;" +
        "^https?://.*example2.com:\\d{0,9}/.*$;" +
        "^https://.*example3.com:\\d{0,9}/.*$;" +
        "^https?://localhost:\\d{0,9}/.*$;^/.*$";

    // match on explicit hostname/domain and port
    Assert.assertTrue("Failed to match whitelist", RegExUtils.checkWhitelist(whitelist,
        "http://host.example.com:8080/"));
    // match on non-required port
    Assert.assertTrue("Failed to match whitelist", RegExUtils.checkWhitelist(whitelist,
        "http://host.example.com/"));
    // match on required but any port
    Assert.assertTrue("Failed to match whitelist", RegExUtils.checkWhitelist(whitelist,
        "http://host.example2.com:1234/"));
    // fail on missing port
    assertFalse("Matched whitelist inappropriately", RegExUtils.checkWhitelist(whitelist,
        "http://host.example2.com/"));
    // fail on invalid port
    assertFalse("Matched whitelist inappropriately", RegExUtils.checkWhitelist(whitelist,
        "http://host.example.com:8081/"));
    // fail on alphanumeric port
    assertFalse("Matched whitelist inappropriately", RegExUtils.checkWhitelist(whitelist,
        "http://host.example.com:A080/"));
    // fail on invalid hostname/domain
    assertFalse("Matched whitelist inappropriately", RegExUtils.checkWhitelist(whitelist,
        "http://host.example.net:8080/"));
    // fail on required port
    assertFalse("Matched whitelist inappropriately", RegExUtils.checkWhitelist(whitelist,
        "http://host.example2.com/"));
    // fail on required https
    assertFalse("Matched whitelist inappropriately", RegExUtils.checkWhitelist(whitelist,
        "http://host.example3.com/"));
    // match on localhost and port
    Assert.assertTrue("Failed to match whitelist", RegExUtils.checkWhitelist(whitelist,
        "http://localhost:8080/"));
    // match on local/relative path
    Assert.assertTrue("Failed to match whitelist", RegExUtils.checkWhitelist(whitelist,
        "/local/resource/"));
  }

  @Test
  public void testGetToken() throws Exception {

    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(expectGatewayConfig()).anyTimes();
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.name")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.secure.only")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.max.age")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.domain.suffix")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.redirect.whitelist.regex")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.token.audiences")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.token.ttl")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.enable.session")).andReturn(null);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter("originalUrl")).andReturn("http://localhost:9080/service");
    EasyMock.expect(request.getParameterMap()).andReturn(Collections.emptyMap());
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();

    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    JWTokenAuthority authority = new TestJWTokenAuthority(gatewayPublicKey, gatewayPrivateKey);
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    ServletOutputStream outputStream = EasyMock.createNiceMock(ServletOutputStream.class);
    CookieResponseWrapper responseWrapper = new CookieResponseWrapper(response, outputStream);

    EasyMock.replay(principal, services, context, request);

    WebSSOResource webSSOResponse = new WebSSOResource();
    webSSOResponse.request = request;
    webSSOResponse.response = responseWrapper;
    webSSOResponse.context = context;
    webSSOResponse.init();

    // Issue a token
    webSSOResponse.doGet();

    // Check the cookie
    Cookie cookie = responseWrapper.getCookie("hadoop-jwt");
    assertNotNull(cookie);

    JWT parsedToken = new JWTToken(cookie.getValue());
    assertEquals("alice", parsedToken.getSubject());
    assertTrue(authority.verifyToken(parsedToken));
  }

  @Test
  public void testAudiences() throws Exception {

    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(expectGatewayConfig()).anyTimes();
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.name")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.secure.only")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.max.age")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.domain.suffix")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.redirect.whitelist.regex")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.token.audiences")).andReturn("recipient1,recipient2");
    EasyMock.expect(context.getInitParameter("knoxsso.token.ttl")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.enable.session")).andReturn(null);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter("originalUrl")).andReturn("http://localhost:9080/service");
    EasyMock.expect(request.getParameterMap()).andReturn(Collections.emptyMap());
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();

    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    JWTokenAuthority authority = new TestJWTokenAuthority(gatewayPublicKey, gatewayPrivateKey);
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    ServletOutputStream outputStream = EasyMock.createNiceMock(ServletOutputStream.class);
    CookieResponseWrapper responseWrapper = new CookieResponseWrapper(response, outputStream);

    EasyMock.replay(principal, services, context, request);

    WebSSOResource webSSOResponse = new WebSSOResource();
    webSSOResponse.request = request;
    webSSOResponse.response = responseWrapper;
    webSSOResponse.context = context;
    webSSOResponse.init();

    // Issue a token
    webSSOResponse.doGet();

    // Check the cookie
    Cookie cookie = responseWrapper.getCookie("hadoop-jwt");
    assertNotNull(cookie);

    JWT parsedToken = new JWTToken(cookie.getValue());
    assertEquals("alice", parsedToken.getSubject());
    assertTrue(authority.verifyToken(parsedToken));

    // Verify the audiences
    List<String> audiences = Arrays.asList(parsedToken.getAudienceClaims());
    assertEquals(2, audiences.size());
    assertTrue(audiences.contains("recipient1"));
    assertTrue(audiences.contains("recipient2"));
  }

  @Test
  public void testAudiencesWhitespace() throws Exception {

    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(expectGatewayConfig()).anyTimes();
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.name")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.secure.only")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.max.age")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.domain.suffix")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.redirect.whitelist.regex")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.token.audiences")).andReturn(" recipient1, recipient2 ");
    EasyMock.expect(context.getInitParameter("knoxsso.token.ttl")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.enable.session")).andReturn(null);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter("originalUrl")).andReturn("http://localhost:9080/service");
    EasyMock.expect(request.getParameterMap()).andReturn(Collections.emptyMap());
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();

    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    JWTokenAuthority authority = new TestJWTokenAuthority(gatewayPublicKey, gatewayPrivateKey);
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    ServletOutputStream outputStream = EasyMock.createNiceMock(ServletOutputStream.class);
    CookieResponseWrapper responseWrapper = new CookieResponseWrapper(response, outputStream);

    EasyMock.replay(principal, services, context, request);

    WebSSOResource webSSOResponse = new WebSSOResource();
    webSSOResponse.request = request;
    webSSOResponse.response = responseWrapper;
    webSSOResponse.context = context;
    webSSOResponse.init();

    // Issue a token
    webSSOResponse.doGet();

    // Check the cookie
    Cookie cookie = responseWrapper.getCookie("hadoop-jwt");
    assertNotNull(cookie);

    JWTToken parsedToken = new JWTToken(cookie.getValue());
    assertEquals("alice", parsedToken.getSubject());
    assertTrue(authority.verifyToken(parsedToken));

    // Verify the audiences
    List<String> audiences = Arrays.asList(parsedToken.getAudienceClaims());
    assertEquals(2, audiences.size());
    assertTrue(audiences.contains("recipient1"));
    assertTrue(audiences.contains("recipient2"));
  }

  @Test
  public void testSignatureAlgorithm() throws Exception {

    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(expectGatewayConfig()).anyTimes();
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.name")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.secure.only")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.max.age")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.domain.suffix")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.redirect.whitelist.regex")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.token.audiences")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.token.ttl")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.enable.session")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.token.sigalg")).andReturn("RS512");

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter("originalUrl")).andReturn("http://localhost:9080/service");
    EasyMock.expect(request.getParameterMap()).andReturn(Collections.emptyMap());
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();

    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    JWTokenAuthority authority = new TestJWTokenAuthority(gatewayPublicKey, gatewayPrivateKey);
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    ServletOutputStream outputStream = EasyMock.createNiceMock(ServletOutputStream.class);
    CookieResponseWrapper responseWrapper = new CookieResponseWrapper(response, outputStream);

    EasyMock.replay(principal, services, context, request);

    WebSSOResource webSSOResponse = new WebSSOResource();
    webSSOResponse.request = request;
    webSSOResponse.response = responseWrapper;
    webSSOResponse.context = context;
    webSSOResponse.init();

    // Issue a token
    webSSOResponse.doGet();

    // Check the cookie
    Cookie cookie = responseWrapper.getCookie("hadoop-jwt");
    assertNotNull(cookie);

    JWT parsedToken = new JWTToken(cookie.getValue());
    assertEquals("alice", parsedToken.getSubject());
    assertTrue(authority.verifyToken(parsedToken));
    assertTrue(parsedToken.getHeader().contains("RS512"));
  }

  @Test
  public void testDefaultTTL() throws Exception {

    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(expectGatewayConfig()).anyTimes();
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.name")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.secure.only")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.max.age")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.domain.suffix")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.redirect.whitelist.regex")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.token.audiences")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.token.ttl")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.enable.session")).andReturn(null);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter("originalUrl")).andReturn("http://localhost:9080/service");
    EasyMock.expect(request.getParameterMap()).andReturn(Collections.emptyMap());
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();

    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    JWTokenAuthority authority = new TestJWTokenAuthority(gatewayPublicKey, gatewayPrivateKey);
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    ServletOutputStream outputStream = EasyMock.createNiceMock(ServletOutputStream.class);
    CookieResponseWrapper responseWrapper = new CookieResponseWrapper(response, outputStream);

    EasyMock.replay(principal, services, context, request);

    WebSSOResource webSSOResponse = new WebSSOResource();
    webSSOResponse.request = request;
    webSSOResponse.response = responseWrapper;
    webSSOResponse.context = context;
    webSSOResponse.init();

    // Issue a token
    webSSOResponse.doGet();

    // Check the cookie
    Cookie cookie = responseWrapper.getCookie("hadoop-jwt");
    assertNotNull(cookie);

    JWT parsedToken = new JWTToken(cookie.getValue());
    assertEquals("alice", parsedToken.getSubject());
    assertTrue(authority.verifyToken(parsedToken));

    Date expiresDate = parsedToken.getExpiresDate();
    Date now = new Date();
    assertTrue(expiresDate.after(now));
    assertTrue((expiresDate.getTime() - now.getTime()) < 30000L);
  }

  @Test
  public void testCustomTTL() throws Exception {
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(expectGatewayConfig()).anyTimes();
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.name")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.secure.only")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.max.age")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.domain.suffix")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.redirect.whitelist.regex")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.token.audiences")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.token.ttl")).andReturn("60000");
    EasyMock.expect(context.getInitParameter("knoxsso.enable.session")).andReturn(null);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter("originalUrl")).andReturn("http://localhost:9080/service");
    EasyMock.expect(request.getParameterMap()).andReturn(Collections.emptyMap());
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();

    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    JWTokenAuthority authority = new TestJWTokenAuthority(gatewayPublicKey, gatewayPrivateKey);
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    ServletOutputStream outputStream = EasyMock.createNiceMock(ServletOutputStream.class);
    CookieResponseWrapper responseWrapper = new CookieResponseWrapper(response, outputStream);

    EasyMock.replay(principal, services, context, request);

    WebSSOResource webSSOResponse = new WebSSOResource();
    webSSOResponse.request = request;
    webSSOResponse.response = responseWrapper;
    webSSOResponse.context = context;
    webSSOResponse.init();

    // Issue a token
    webSSOResponse.doGet();

    // Check the cookie
    Cookie cookie = responseWrapper.getCookie("hadoop-jwt");
    assertNotNull(cookie);

    JWT parsedToken = new JWTToken(cookie.getValue());
    assertEquals("alice", parsedToken.getSubject());
    assertTrue(authority.verifyToken(parsedToken));

    Date expiresDate = parsedToken.getExpiresDate();
    Date now = new Date();
    assertTrue(expiresDate.after(now));
    long diff = expiresDate.getTime() - now.getTime();
    assertTrue(diff < 60000L && diff > 30000L);
  }

  @Test
  public void testNegativeTTL() throws Exception {

    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(expectGatewayConfig()).anyTimes();
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.name")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.secure.only")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.max.age")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.domain.suffix")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.redirect.whitelist.regex")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.token.audiences")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.token.ttl")).andReturn("-60000");
    EasyMock.expect(context.getInitParameter("knoxsso.enable.session")).andReturn(null);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter("originalUrl")).andReturn("http://localhost:9080/service");
    EasyMock.expect(request.getParameterMap()).andReturn(Collections.emptyMap());
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();

    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    JWTokenAuthority authority = new TestJWTokenAuthority(gatewayPublicKey, gatewayPrivateKey);
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    ServletOutputStream outputStream = EasyMock.createNiceMock(ServletOutputStream.class);
    CookieResponseWrapper responseWrapper = new CookieResponseWrapper(response, outputStream);

    EasyMock.replay(principal, services, context, request);

    WebSSOResource webSSOResponse = new WebSSOResource();
    webSSOResponse.request = request;
    webSSOResponse.response = responseWrapper;
    webSSOResponse.context = context;
    webSSOResponse.init();

    // Issue a token
    webSSOResponse.doGet();

    // Check the cookie
    Cookie cookie = responseWrapper.getCookie("hadoop-jwt");
    assertNotNull(cookie);

    JWT parsedToken = new JWTToken(cookie.getValue());
    assertEquals("alice", parsedToken.getSubject());
    assertTrue(authority.verifyToken(parsedToken));

    Date expiresDate = parsedToken.getExpiresDate();
    Date now = new Date();
    assertTrue(expiresDate.after(now));
    assertTrue((expiresDate.getTime() - now.getTime()) < 30000L);
  }

  @Test
  public void shouldMatchKnoxSsoSecureOnlyWithSslEnabledInCaseKnoxSsoSecureOnlyIsNotSet() throws Exception {
    testSecureOnly(false, null, false);
    testSecureOnly(true, null, true);
  }

  @Test
  public void shouldUseKnoxSsoSecureOnlyInCaseKnoxSsoSecureOnlyIsSet() throws Exception {
    testSecureOnly(false, Boolean.TRUE, true);
    testSecureOnly(true, Boolean.FALSE, false);
  }

  private void testSecureOnly(boolean sslEnabled, Boolean knoxSsoCookieSecureOnly, boolean expectedknoxSsoSecureOnly) throws Exception {
    final ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(expectGatewayConfig(sslEnabled)).anyTimes();
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.secure.only")).andReturn(knoxSsoCookieSecureOnly == null ? null : knoxSsoCookieSecureOnly.toString());

    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter("originalUrl")).andReturn("http://localhost:9080/service");
    EasyMock.expect(request.getParameterMap()).andReturn(Collections.emptyMap());
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();

    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    ServletOutputStream outputStream = EasyMock.createNiceMock(ServletOutputStream.class);
    CookieResponseWrapper responseWrapper = new CookieResponseWrapper(response, outputStream);

    final Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    final GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(new TestJWTokenAuthority(gatewayPublicKey, gatewayPrivateKey));
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    EasyMock.replay(context, request, services);

    final WebSSOResource webSSOResponse = new WebSSOResource();
    webSSOResponse.request = request;
    webSSOResponse.response = responseWrapper;
    webSSOResponse.context = context;
    webSSOResponse.init();

    // Issue a token
    webSSOResponse.doGet();

    // Check the cookie
    final Cookie cookie = responseWrapper.getCookie("hadoop-jwt");
    assertNotNull(cookie);
    assertEquals(expectedknoxSsoSecureOnly, cookie.getSecure());
  }

  @Test
  public void testOverflowTTL() throws Exception {

    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(expectGatewayConfig()).anyTimes();
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.name")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.secure.only")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.max.age")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.domain.suffix")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.redirect.whitelist.regex")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.token.audiences")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.token.ttl")).andReturn(String.valueOf(Long.MAX_VALUE));
    EasyMock.expect(context.getInitParameter("knoxsso.enable.session")).andReturn(null);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter("originalUrl")).andReturn("http://localhost:9080/service");
    EasyMock.expect(request.getParameterMap()).andReturn(Collections.emptyMap());
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();

    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    JWTokenAuthority authority = new TestJWTokenAuthority(gatewayPublicKey, gatewayPrivateKey);
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    ServletOutputStream outputStream = EasyMock.createNiceMock(ServletOutputStream.class);
    CookieResponseWrapper responseWrapper = new CookieResponseWrapper(response, outputStream);

    EasyMock.replay(principal, services, context, request);

    WebSSOResource webSSOResponse = new WebSSOResource();
    webSSOResponse.request = request;
    webSSOResponse.response = responseWrapper;
    webSSOResponse.context = context;
    webSSOResponse.init();

    // Issue a token
    webSSOResponse.doGet();

    // Check the cookie
    Cookie cookie = responseWrapper.getCookie("hadoop-jwt");
    assertNotNull(cookie);

    JWT parsedToken = new JWTToken(cookie.getValue());
    assertEquals("alice", parsedToken.getSubject());
    assertTrue(authority.verifyToken(parsedToken));

    Date expiresDate = parsedToken.getExpiresDate();
    Date now = new Date();
    assertTrue(expiresDate.after(now));
    assertTrue((expiresDate.getTime() - now.getTime()) < 30000L);
  }

  @Test
  public void testWhitelistValidationWithEncodedOriginalURL() throws Exception {
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(expectGatewayConfig()).anyTimes();
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.name")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.secure.only")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.max.age")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.domain.suffix")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.redirect.whitelist.regex")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.token.audiences")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.token.ttl")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.enable.session")).andReturn(null);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter("originalUrl")).andReturn(
        URLEncoder.encode("http://disallowedhost:9080/service", StandardCharsets.UTF_8.name()));
    EasyMock.expect(request.getAttribute("targetServiceRole")).andReturn("KNOXSSO").anyTimes();
    EasyMock.expect(request.getParameterMap()).andReturn(Collections.emptyMap());
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();
    EasyMock.expect(request.getServerName()).andReturn("localhost").anyTimes();

    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    JWTokenAuthority authority = new TestJWTokenAuthority(gatewayPublicKey, gatewayPrivateKey);
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    ServletOutputStream outputStream = EasyMock.createNiceMock(ServletOutputStream.class);
    CookieResponseWrapper responseWrapper = new CookieResponseWrapper(response, outputStream);

    EasyMock.replay(principal, services, context, request);

    WebSSOResource webSSOResponse = new WebSSOResource();
    webSSOResponse.request = request;
    webSSOResponse.response = responseWrapper;
    webSSOResponse.context = context;
    webSSOResponse.init();

    try {
      webSSOResponse.doGet();
    } catch (WebApplicationException e) {
      // Expected
      int status = e.getResponse().getStatus();
      assertEquals(HttpStatus.SC_BAD_REQUEST, status);
    }
  }

  private GatewayConfig expectGatewayConfig() {
    return expectGatewayConfig(true);
  }

  private GatewayConfig expectGatewayConfig(boolean sslEnabled) {
    final GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.getDispatchWhitelistServices()).andReturn(Collections.emptyList()).anyTimes();
    EasyMock.expect(config.getDispatchWhitelist()).andReturn(null).anyTimes();
    EasyMock.expect(config.isSSLEnabled()).andReturn(sslEnabled).anyTimes();
    EasyMock.replay(config);
    return config;
  }

  @Test
  public void testTopologyDefinedWhitelist() throws Exception {
    final String testServiceRole = "TEST";

    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(expectGatewayConfig()).anyTimes();
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.name")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.secure.only")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.max.age")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.domain.suffix")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.redirect.whitelist.regex")).andReturn("^.*$");
    EasyMock.expect(context.getInitParameter("knoxsso.token.audiences")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.token.ttl")).andReturn("60000");
    EasyMock.expect(context.getInitParameter("knoxsso.enable.session")).andReturn(null);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter("originalUrl")).andReturn("http://localhost:9080/service");
    EasyMock.expect(request.getParameterMap()).andReturn(Collections.emptyMap());
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();
    EasyMock.expect(request.getAttribute("targetServiceRole")).andReturn(testServiceRole).anyTimes();
    EasyMock.expect(request.getServerName()).andReturn("localhost").anyTimes();

    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    JWTokenAuthority authority = new TestJWTokenAuthority(gatewayPublicKey, gatewayPrivateKey);
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    ServletOutputStream outputStream = EasyMock.createNiceMock(ServletOutputStream.class);
    CookieResponseWrapper responseWrapper = new CookieResponseWrapper(response, outputStream);

    EasyMock.replay(principal, services, context, request);

    WebSSOResource webSSOResponse = new WebSSOResource();
    webSSOResponse.request = request;
    webSSOResponse.response = responseWrapper;
    webSSOResponse.context = context;
    webSSOResponse.init();

    Field whitelistField = webSSOResponse.getClass().getDeclaredField("whitelist");
    whitelistField.setAccessible(true);
    String whitelistValue = (String) whitelistField.get(webSSOResponse);
    assertNotNull(whitelistValue);
    assertEquals("^.*$", whitelistValue);
  }

  @Test
  public void testExpectedKnoxSSOParams() {

    final HashMap<String, String[]> paramMap = new HashMap<>();
    paramMap.put("knoxtoken", new String[]{"eyJhbGciOiJSUzI1NiJ9.eyJzdWIiO"
        + "iJhZG1pbjEiLCJpc3MiOiJLTk9YU1NPIiwiZXhwIjoxNTMwODk1NjUw"
        + "fQ.Sodks_BTwaijMM5eg9rY77ro1H4um12TCqmwL5eWn4IMWBBXZQOV"
        + "D4JRWNKG_OtITKvkn9EhowOZO6Qtb6tvZLUPyW8Bf9gfgKAHJNLSYyc"
        + "yWSPzBOc2kcPmwdYXkOXtPu6KWZaQcD-WRw-89aORbgqZVRKX2Zyk2MLb0Rnig_0"});

    final String originalUrl = "http://localhost:9080/service";


    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameter("knoxsso.expected.params")).andReturn("knoxtoken,originalUrl");
    EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(expectGatewayConfig()).anyTimes();

    ServletContext contextNoParam = EasyMock.createNiceMock(ServletContext.class);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter("originalUrl")).andReturn(originalUrl).anyTimes();
    EasyMock.expect(request.getParameterMap()).andReturn(paramMap).anyTimes();
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();

    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("tom").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services).anyTimes();

    JWTokenAuthority authority = new TestJWTokenAuthority(gatewayPublicKey, gatewayPrivateKey);
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority).anyTimes();

    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    ServletOutputStream outputStream = EasyMock.createNiceMock(ServletOutputStream.class);
    CookieResponseWrapper responseWrapper = new CookieResponseWrapper(response, outputStream);

    EasyMock.replay(principal, services, context, contextNoParam, request);

    /* declare knoxtoken as part of knoxsso param so it is stripped from the final url */
    WebSSOResource webSSOResponse = new WebSSOResource();
    webSSOResponse.request = request;
    webSSOResponse.response = responseWrapper;
    webSSOResponse.context = context;
    webSSOResponse.init();

    Response resp = webSSOResponse.doGet();
    assertEquals(originalUrl, resp.getLocation().toString());

    /* declare knoxtoken as NOT part of knoxsso param so it is stripped from the final url */
    webSSOResponse = new WebSSOResource();
    webSSOResponse.request = request;
    webSSOResponse.response = responseWrapper;
    webSSOResponse.context = contextNoParam;
    webSSOResponse.init();

    resp = webSSOResponse.doGet();
    assertEquals(originalUrl+"?&"+"knoxtoken="+"eyJhbGciOiJSUzI1NiJ9.eyJzdWIiO"
        + "iJhZG1pbjEiLCJpc3MiOiJLTk9YU1NPIiwiZXhwIjoxNTMwODk1NjUw"
        + "fQ.Sodks_BTwaijMM5eg9rY77ro1H4um12TCqmwL5eWn4IMWBBXZQOV"
        + "D4JRWNKG_OtITKvkn9EhowOZO6Qtb6tvZLUPyW8Bf9gfgKAHJNLSYyc"
        + "yWSPzBOc2kcPmwdYXkOXtPu6KWZaQcD-WRw-89aORbgqZVRKX2Zyk2MLb0Rnig_0",resp.getLocation().toString());

  }

  @Test
  public void testRedactToken() {

    final String token = "eyJhbGciOiJSUzI1NiJ9."
        + "eyJzdWIiOiJhZG1pbjEiLCJpc3MiOiJLTk9YU1NPIiwiZXhwIjoxNTMwNzkwMjkxfQ."
        + "BdWDAzGvXVvBH_TiEhAqowH-K24GfH8rgb8HJLromVpGwBTkbijIfOZWcvT3a5uZx6r"
        + "VEGAXqAKPEqrODBGTSV6l0uEJGeAfQj_7ulQD0YkXaPPJ_FYYntelSA814HzTU0X8UmY"
        + "Wo6awEaBwRC_xgNoZ_bMqnkjgFAfTtdHiiEQ";

    final String originalUrl1 = "http://loclahost:8080/?&knoxtoken="+token;
    final String originalUrl2 = "http://loclahost:8080/?&test=value&knoxtoken="+token;
    final String originalUrl3 = "http://loclahost:8080/?&knoxtoken="+token+"&test=value";

    final String fragment1 = "/gateway/knoxsso/api/v1/websso?knoxtoken="+token;
    final String fragment2 = "/gateway/knoxsso/api/v1/websso?knoxtoken="+token+
        "&originalUrl=http%3A%2F%2Fwww.local.com%3A8443%2F%3Fgateway%3Done%26knoxtoken";
    final String fragment3 = "/gateway/knoxsso/api/v1/websso?test=value"+
        "&originalUrl=http%3A%2F%2Fwww.local.com%3A8443%2F%3Fgateway%3Done%26knoxtoken";

    assertEquals("http://loclahost:8080/?&knoxtoken=***************", Log4jAuditor
        .maskTokenFromURL(originalUrl1));
    assertEquals("http://loclahost:8080/?&test=value&knoxtoken=***************", Log4jAuditor.maskTokenFromURL(originalUrl2));
    assertEquals("http://loclahost:8080/?&knoxtoken=***************&test=value", Log4jAuditor.maskTokenFromURL(originalUrl3));

    assertEquals("/gateway/knoxsso/api/v1/websso?knoxtoken=***************", Log4jAuditor.maskTokenFromURL(fragment1));
    assertEquals("/gateway/knoxsso/api/v1/websso?knoxtoken=***************"+
        "&originalUrl=http://www.local.com:8443/?gateway=one&knoxtoken", Log4jAuditor.maskTokenFromURL(fragment2));
    assertEquals("/gateway/knoxsso/api/v1/websso?test=value"+
        "&originalUrl=http://www.local.com:8443/?gateway=one&knoxtoken", Log4jAuditor.maskTokenFromURL(fragment3));
  }

  @Test
  public void testCustomSigningKey() throws Exception {

    String topologyName = "testCustomSigningKeyTopology";
    String customSigningKeyName = "testSigningKeyName";
    String customSigningKeyAlias = "testSigningKeyAlias";
    String customSigningKeyPassphraseAlias = "testSigningKeyPassphraseAlias";
    String customSigningKeyPassphrase = "testSigningKeyPassphrase";

    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair keyPair = kpg.generateKeyPair();
    RSAPublicKey customPublicKey = (RSAPublicKey) keyPair.getPublic();
    RSAPrivateKey customPrivateKey = (RSAPrivateKey) keyPair.getPrivate();

    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(expectGatewayConfig()).anyTimes();
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.name")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.secure.only")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.max.age")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.domain.suffix")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.redirect.whitelist.regex")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.token.audiences")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.token.ttl")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.enable.session")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knoxsso.signingkey.keystore.name"))
        .andReturn(customSigningKeyName);
    EasyMock.expect(context.getInitParameter("knoxsso.signingkey.keystore.alias"))
        .andReturn(customSigningKeyAlias);
    EasyMock.expect(context.getInitParameter("knoxsso.signingkey.keystore.passphrase.alias"))
        .andReturn(customSigningKeyPassphraseAlias);
    EasyMock.expect(context.getAttribute(GATEWAY_CLUSTER_ATTRIBUTE)).andReturn(topologyName);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter("originalUrl")).andReturn("http://localhost:9080/service");
    EasyMock.expect(request.getParameterMap()).andReturn(Collections.emptyMap());
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();

    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    TestJWTokenAuthority authority = new TestJWTokenAuthority(gatewayPublicKey, gatewayPrivateKey);
    authority.addCustomSigningKey(customSigningKeyName, customSigningKeyAlias, customSigningKeyPassphrase.toCharArray(),
        customPrivateKey);
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForCluster(topologyName, customSigningKeyPassphraseAlias))
        .andReturn(customSigningKeyPassphrase.toCharArray());
    EasyMock.expect(services.getService(ServiceType.ALIAS_SERVICE)).andReturn(aliasService);

    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    ServletOutputStream outputStream = EasyMock.createNiceMock(ServletOutputStream.class);
    CookieResponseWrapper responseWrapper = new CookieResponseWrapper(response, outputStream);

    EasyMock.replay(principal, services, context, request, aliasService);

    WebSSOResource webSSOResponse = new WebSSOResource();
    webSSOResponse.request = request;
    webSSOResponse.response = responseWrapper;
    webSSOResponse.context = context;
    webSSOResponse.init();

    // Issue a token
    webSSOResponse.doGet();

    // Check the cookie
    Cookie cookie = responseWrapper.getCookie("hadoop-jwt");
    assertNotNull(cookie);

    JWT parsedToken = new JWTToken(cookie.getValue());
    assertEquals("alice", parsedToken.getSubject());
    assertFalse(authority.verifyToken(parsedToken, gatewayPublicKey));
    assertTrue(authority.verifyToken(parsedToken, customPublicKey));
  }

  /**
   * A wrapper for HttpServletResponseWrapper to store the cookies
   */
  private static class CookieResponseWrapper extends HttpServletResponseWrapper {

    private ServletOutputStream outputStream;
    private Map<String, Cookie> cookies = new HashMap<>();

    CookieResponseWrapper(HttpServletResponse response, ServletOutputStream outputStream) {
        super(response);
        this.outputStream = outputStream;
    }

    @Override
    public ServletOutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public void addCookie(Cookie cookie) {
        super.addCookie(cookie);
        cookies.put(cookie.getName(), cookie);
    }

    Cookie getCookie(String name) {
        return cookies.get(name);
    }
  }

  private static class TestJWTokenAuthority implements JWTokenAuthority {
    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;
    private Map<String, Map<String,Object>> customSigningKeys = new HashMap<>();

    TestJWTokenAuthority(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
      this.publicKey = publicKey;
      this.privateKey = privateKey;
    }

    void addCustomSigningKey(String signingKeystoreName, String signingKeystoreAlias,
                             char[] signingKeystorePassphrase, RSAPrivateKey customPrivateKey) {

      Map<String, Object> signingKey = new HashMap<>();
      signingKey.put("alias", signingKeystoreAlias);
      signingKey.put("passphrase", signingKeystorePassphrase);
      signingKey.put("privateKey", customPrivateKey);
      customSigningKeys.put(signingKeystoreName, signingKey);
    }

    @Override
    public JWT issueToken(Subject subject, String algorithm)
      throws TokenServiceException {
      Principal p = (Principal) subject.getPrincipals().toArray()[0];
      return issueToken(p, algorithm);
    }

    @Override
    public JWT issueToken(Principal p, String algorithm)
      throws TokenServiceException {
      return issueToken(p, null, algorithm);
    }

    @Override
    public JWT issueToken(Principal p, String audience, String algorithm)
      throws TokenServiceException {
      return issueToken(p, audience, algorithm, -1);
    }

    @Override
    public boolean verifyToken(JWT token) {
      JWSVerifier verifier = new RSASSAVerifier(publicKey);
      return token.verify(verifier);
    }

    @Override
    public JWT issueToken(Principal p, String audience, String algorithm,
                               long expires) throws TokenServiceException {
      List<String> audiences = null;
      if (audience != null) {
        audiences = new ArrayList<>();
        audiences.add(audience);
      }
      return issueToken(p, audiences, algorithm, expires);
    }

    @Override
    public JWT issueToken(Principal p, List<String> audiences, String algorithm,
                          long expires) throws TokenServiceException {
      return issueToken(p, audiences, algorithm, expires, null, null, null);
    }

    @Override
    public JWT issueToken(Principal p, List<String> audiences, String algorithm, long expires,
                          String signingKeystoreName, String signingKeystoreAlias, char[] signingKeystorePassphrase)
        throws TokenServiceException {
      String[] claimArray = new String[4];
      claimArray[0] = "KNOXSSO";
      claimArray[1] = p.getName();
      claimArray[2] = null;
      if (expires == -1) {
        claimArray[3] = null;
      } else {
        claimArray[3] = String.valueOf(expires);
      }

      JWT token = new JWTToken(algorithm, claimArray, audiences);
      RSAPrivateKey privateKey = getPrivateKey(signingKeystoreName, signingKeystoreAlias, signingKeystorePassphrase);
      JWSSigner signer = new RSASSASigner(privateKey);
      token.sign(signer);

      return token;
    }

    private RSAPrivateKey getPrivateKey(String signingKeystoreName, String signingKeystoreAlias,
                                        char[] signingKeystorePassphrase) throws TokenServiceException {
      if(signingKeystoreName != null) {
        Map<String, Object> signingKey = customSigningKeys.get(signingKeystoreName);
        if(signingKey == null || !signingKey.get("alias").equals(signingKeystoreAlias) ||
               !Arrays.equals((char[])signingKey.get("passphrase"), signingKeystorePassphrase)) {
          throw new TokenServiceException("Invalid alias or passphrase");
        }
        return (RSAPrivateKey)signingKey.get("privateKey");
      }
      return privateKey;
    }

    @Override
    public JWT issueToken(Principal p, String algorithm, long expiry)
        throws TokenServiceException {
      return issueToken(p, Collections.emptyList(), algorithm, expiry);
    }

    @Override
    public boolean verifyToken(JWT token, RSAPublicKey publicKey) {
      JWSVerifier verifier = new RSASSAVerifier(publicKey);
      return token.verify(verifier);
    }
  }
}
