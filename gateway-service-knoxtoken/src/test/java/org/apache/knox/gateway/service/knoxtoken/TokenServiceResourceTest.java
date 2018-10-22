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
package org.apache.knox.gateway.service.knoxtoken;

import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.security.auth.Subject;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Some tests for the token service
 */
public class TokenServiceResourceTest {

  protected static RSAPublicKey publicKey;
  protected static RSAPrivateKey privateKey;

  @BeforeClass
  public static void setup() throws Exception, NoSuchAlgorithmException {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(1024);
    KeyPair KPair = kpg.generateKeyPair();

    publicKey = (RSAPublicKey) KPair.getPublic();
    privateKey = (RSAPrivateKey) KPair.getPrivate();
  }

  @Test
  public void testTokenService() throws Exception {
    Assert.assertTrue(true);
  }

  @Test
  public void testClientData() throws Exception {
    TokenResource tr = new TokenResource();

    Map<String,Object> clientDataMap = new HashMap<>();
    tr.addClientDataToMap("cookie.name=hadoop-jwt,test=value".split(","), clientDataMap);
    Assert.assertTrue(clientDataMap.size() == 2);

    clientDataMap = new HashMap<>();
    tr.addClientDataToMap("cookie.name=hadoop-jwt".split(","), clientDataMap);
    Assert.assertTrue(clientDataMap.size() == 1);

    clientDataMap = new HashMap<>();
    tr.addClientDataToMap("".split(","), clientDataMap);
    Assert.assertTrue(clientDataMap.size() == 0);
  }

  @Test
  public void testGetToken() throws Exception {

    ServletContext context = EasyMock.createNiceMock(ServletContext.class);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();
    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    JWTokenAuthority authority = new TestJWTokenAuthority(publicKey, privateKey);
    EasyMock.expect(services.getService(GatewayServices.TOKEN_SERVICE)).andReturn(authority);

    StringWriter writer = new StringWriter();
    PrintWriter printWriter = new PrintWriter(writer);
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.expect(response.getWriter()).andReturn(printWriter);

    EasyMock.replay(principal, services, context, request, response);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.response = response;

    // Issue a token
    Response retResponse = tr.doGet();

    assertEquals(200, retResponse.getStatus());

    // Parse the response
    String retString = writer.toString();
    String accessToken = getTagValue(retString, "access_token");
    assertNotNull(accessToken);
    String expiry = getTagValue(retString, "expires_in");
    assertNotNull(expiry);

    // Verify the token
    JWT parsedToken = new JWTToken(accessToken);
    assertEquals("alice", parsedToken.getSubject());
    assertTrue(authority.verifyToken(parsedToken));
  }

  @Test
  public void testAudiences() throws Exception {

    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameter("knox.token.audiences")).andReturn("recipient1,recipient2");
    EasyMock.expect(context.getInitParameter("knox.token.ttl")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knox.token.target.url")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knox.token.client.data")).andReturn(null);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();
    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    JWTokenAuthority authority = new TestJWTokenAuthority(publicKey, privateKey);
    EasyMock.expect(services.getService(GatewayServices.TOKEN_SERVICE)).andReturn(authority);

    StringWriter writer = new StringWriter();
    PrintWriter printWriter = new PrintWriter(writer);
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.expect(response.getWriter()).andReturn(printWriter);

    EasyMock.replay(principal, services, context, request, response);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.response = response;
    tr.context = context;
    tr.init();

    // Issue a token
    Response retResponse = tr.doGet();

    assertEquals(200, retResponse.getStatus());

    // Parse the response
    String retString = writer.toString();
    String accessToken = getTagValue(retString, "access_token");
    assertNotNull(accessToken);
    String expiry = getTagValue(retString, "expires_in");
    assertNotNull(expiry);

    // Verify the token
    JWT parsedToken = new JWTToken(accessToken);
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
    EasyMock.expect(context.getInitParameter("knox.token.audiences")).andReturn(" recipient1, recipient2 ");
    EasyMock.expect(context.getInitParameter("knox.token.ttl")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knox.token.target.url")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knox.token.client.data")).andReturn(null);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();
    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    JWTokenAuthority authority = new TestJWTokenAuthority(publicKey, privateKey);
    EasyMock.expect(services.getService(GatewayServices.TOKEN_SERVICE)).andReturn(authority);

    StringWriter writer = new StringWriter();
    PrintWriter printWriter = new PrintWriter(writer);
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.expect(response.getWriter()).andReturn(printWriter);

    EasyMock.replay(principal, services, context, request, response);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.response = response;
    tr.context = context;
    tr.init();

    // Issue a token
    Response retResponse = tr.doGet();

    assertEquals(200, retResponse.getStatus());

    // Parse the response
    String retString = writer.toString();
    String accessToken = getTagValue(retString, "access_token");
    assertNotNull(accessToken);
    String expiry = getTagValue(retString, "expires_in");
    assertNotNull(expiry);

    // Verify the token
    JWT parsedToken = new JWTToken(accessToken);
    assertEquals("alice", parsedToken.getSubject());
    assertTrue(authority.verifyToken(parsedToken));

    // Verify the audiences
    List<String> audiences = Arrays.asList(parsedToken.getAudienceClaims());
    assertEquals(2, audiences.size());
    assertTrue(audiences.contains("recipient1"));
    assertTrue(audiences.contains("recipient2"));
  }

  @Test
  public void testValidClientCert() throws Exception {

    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameter("knox.token.client.cert.required")).andReturn("true");
    EasyMock.expect(context.getInitParameter("knox.token.allowed.principals")).andReturn("CN=localhost, OU=Test, O=Hadoop, L=Test, ST=Test, C=US");

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();
    X509Certificate trustedCertMock = EasyMock.createMock(X509Certificate.class);
    EasyMock.expect(trustedCertMock.getSubjectDN()).andReturn(new PrimaryPrincipal("CN=localhost,OU=Test, O=Hadoop, L=Test, ST=Test, C=US")).anyTimes();
    ArrayList<X509Certificate> certArrayList = new ArrayList<>();
    certArrayList.add(trustedCertMock);
    X509Certificate[] certs = {};
    EasyMock.expect(request.getAttribute("javax.servlet.request.X509Certificate")).andReturn(certArrayList.toArray(certs)).anyTimes();

    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    JWTokenAuthority authority = new TestJWTokenAuthority(publicKey, privateKey);
    EasyMock.expect(services.getService(GatewayServices.TOKEN_SERVICE)).andReturn(authority);

    StringWriter writer = new StringWriter();
    PrintWriter printWriter = new PrintWriter(writer);
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.expect(response.getWriter()).andReturn(printWriter);

    EasyMock.replay(principal, services, context, request, response, trustedCertMock);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.response = response;
    tr.context = context;
    tr.init();

    // Issue a token
    Response retResponse = tr.doGet();

    assertEquals(200, retResponse.getStatus());

    // Parse the response
    String retString = writer.toString();
    String accessToken = getTagValue(retString, "access_token");
    assertNotNull(accessToken);
    String expiry = getTagValue(retString, "expires_in");
    assertNotNull(expiry);

    // Verify the token
    JWT parsedToken = new JWTToken(accessToken);
    assertEquals("alice", parsedToken.getSubject());
    assertTrue(authority.verifyToken(parsedToken));
  }

  @Test
  public void testValidClientCertWrongUser() throws Exception {

    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameter("knox.token.client.cert.required")).andReturn("true");
    EasyMock.expect(context.getInitParameter("knox.token.allowed.principals")).andReturn("CN=remotehost, OU=Test, O=Hadoop, L=Test, ST=Test, C=US");

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();
    X509Certificate trustedCertMock = EasyMock.createMock(X509Certificate.class);
    EasyMock.expect(trustedCertMock.getSubjectDN()).andReturn(new PrimaryPrincipal("CN=localhost, OU=Test, O=Hadoop, L=Test, ST=Test, C=US")).anyTimes();
    ArrayList<X509Certificate> certArrayList = new ArrayList<>();
    certArrayList.add(trustedCertMock);
    X509Certificate[] certs = {};
    EasyMock.expect(request.getAttribute("javax.servlet.request.X509Certificate")).andReturn(certArrayList.toArray(certs)).anyTimes();

    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    JWTokenAuthority authority = new TestJWTokenAuthority(publicKey, privateKey);
    EasyMock.expect(services.getService(GatewayServices.TOKEN_SERVICE)).andReturn(authority);

    StringWriter writer = new StringWriter();
    PrintWriter printWriter = new PrintWriter(writer);
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.expect(response.getWriter()).andReturn(printWriter);

    EasyMock.replay(principal, services, context, request, response, trustedCertMock);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.response = response;
    tr.context = context;
    tr.init();

    // Issue a token
    Response retResponse = tr.doGet();

    assertEquals(403, retResponse.getStatus());
  }

  @Test
  public void testMissingClientCert() throws Exception {

    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameter("knox.token.client.cert.required")).andReturn("true");
    EasyMock.expect(context.getInitParameter("knox.token.allowed.principals")).andReturn("CN=remotehost, OU=Test, O=Hadoop, L=Test, ST=Test, C=US");

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();
    EasyMock.expect(request.getAttribute("javax.servlet.request.X509Certificate")).andReturn(null).anyTimes();

    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    JWTokenAuthority authority = new TestJWTokenAuthority(publicKey, privateKey);
    EasyMock.expect(services.getService(GatewayServices.TOKEN_SERVICE)).andReturn(authority);

    StringWriter writer = new StringWriter();
    PrintWriter printWriter = new PrintWriter(writer);
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.expect(response.getWriter()).andReturn(printWriter);

    EasyMock.replay(principal, services, context, request, response);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.response = response;
    tr.context = context;
    tr.init();

    // Issue a token
    Response retResponse = tr.doGet();

    assertEquals(403, retResponse.getStatus());
  }

  @Test
  public void testSignatureAlgorithm() throws Exception {
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameter("knox.token.audiences")).andReturn("recipient1,recipient2");
    EasyMock.expect(context.getInitParameter("knox.token.ttl")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knox.token.target.url")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knox.token.client.data")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knox.token.sigalg")).andReturn("RS512");

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();
    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    JWTokenAuthority authority = new TestJWTokenAuthority(publicKey, privateKey);
    EasyMock.expect(services.getService(GatewayServices.TOKEN_SERVICE)).andReturn(authority);

    StringWriter writer = new StringWriter();
    PrintWriter printWriter = new PrintWriter(writer);
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.expect(response.getWriter()).andReturn(printWriter);

    EasyMock.replay(principal, services, context, request, response);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.response = response;
    tr.context = context;
    tr.init();

    // Issue a token
    Response retResponse = tr.doGet();

    assertEquals(200, retResponse.getStatus());

    // Parse the response
    String retString = writer.toString();
    String accessToken = getTagValue(retString, "access_token");
    assertNotNull(accessToken);
    String expiry = getTagValue(retString, "expires_in");
    assertNotNull(expiry);

    // Verify the token
    JWT parsedToken = new JWTToken(accessToken);
    assertEquals("alice", parsedToken.getSubject());
    assertTrue(authority.verifyToken(parsedToken));
    assertTrue(parsedToken.getHeader().contains("RS512"));
  }

  @Test
  public void testDefaultTTL() throws Exception {
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameter("knox.token.audiences")).andReturn("recipient1,recipient2");
    EasyMock.expect(context.getInitParameter("knox.token.ttl")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knox.token.target.url")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knox.token.client.data")).andReturn(null);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();
    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    JWTokenAuthority authority = new TestJWTokenAuthority(publicKey, privateKey);
    EasyMock.expect(services.getService(GatewayServices.TOKEN_SERVICE)).andReturn(authority);

    StringWriter writer = new StringWriter();
    PrintWriter printWriter = new PrintWriter(writer);
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.expect(response.getWriter()).andReturn(printWriter);

    EasyMock.replay(principal, services, context, request, response);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.response = response;
    tr.context = context;
    tr.init();

    // Issue a token
    Response retResponse = tr.doGet();

    assertEquals(200, retResponse.getStatus());

    // Parse the response
    String retString = writer.toString();
    String accessToken = getTagValue(retString, "access_token");
    assertNotNull(accessToken);
    String expiry = getTagValue(retString, "expires_in");
    assertNotNull(expiry);

    // Verify the token
    JWT parsedToken = new JWTToken(accessToken);
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
    EasyMock.expect(context.getInitParameter("knox.token.audiences")).andReturn("recipient1,recipient2");
    EasyMock.expect(context.getInitParameter("knox.token.ttl")).andReturn("60000");
    EasyMock.expect(context.getInitParameter("knox.token.target.url")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knox.token.client.data")).andReturn(null);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();
    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    JWTokenAuthority authority = new TestJWTokenAuthority(publicKey, privateKey);
    EasyMock.expect(services.getService(GatewayServices.TOKEN_SERVICE)).andReturn(authority);

    StringWriter writer = new StringWriter();
    PrintWriter printWriter = new PrintWriter(writer);
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.expect(response.getWriter()).andReturn(printWriter);

    EasyMock.replay(principal, services, context, request, response);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.response = response;
    tr.context = context;
    tr.init();

    // Issue a token
    Response retResponse = tr.doGet();

    assertEquals(200, retResponse.getStatus());

    // Parse the response
    String retString = writer.toString();
    String accessToken = getTagValue(retString, "access_token");
    assertNotNull(accessToken);
    String expiry = getTagValue(retString, "expires_in");
    assertNotNull(expiry);

    // Verify the token
    JWT parsedToken = new JWTToken(accessToken);
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
    EasyMock.expect(context.getInitParameter("knox.token.audiences")).andReturn("recipient1,recipient2");
    EasyMock.expect(context.getInitParameter("knox.token.ttl")).andReturn("-60000");
    EasyMock.expect(context.getInitParameter("knox.token.target.url")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knox.token.client.data")).andReturn(null);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();
    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    JWTokenAuthority authority = new TestJWTokenAuthority(publicKey, privateKey);
    EasyMock.expect(services.getService(GatewayServices.TOKEN_SERVICE)).andReturn(authority);

    StringWriter writer = new StringWriter();
    PrintWriter printWriter = new PrintWriter(writer);
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.expect(response.getWriter()).andReturn(printWriter);

    EasyMock.replay(principal, services, context, request, response);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.response = response;
    tr.context = context;
    tr.init();

    // Issue a token
    Response retResponse = tr.doGet();

    assertEquals(200, retResponse.getStatus());

    // Parse the response
    String retString = writer.toString();
    String accessToken = getTagValue(retString, "access_token");
    assertNotNull(accessToken);
    String expiry = getTagValue(retString, "expires_in");
    assertNotNull(expiry);

    // Verify the token
    JWT parsedToken = new JWTToken(accessToken);
    assertEquals("alice", parsedToken.getSubject());
    assertTrue(authority.verifyToken(parsedToken));

    Date expiresDate = parsedToken.getExpiresDate();
    Date now = new Date();
    assertTrue(expiresDate.after(now));
    assertTrue((expiresDate.getTime() - now.getTime()) < 30000L);
  }

  @Test
  public void testOverflowTTL() throws Exception {
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameter("knox.token.audiences")).andReturn("recipient1,recipient2");
    EasyMock.expect(context.getInitParameter("knox.token.ttl")).andReturn(String.valueOf(Long.MAX_VALUE));
    EasyMock.expect(context.getInitParameter("knox.token.target.url")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knox.token.client.data")).andReturn(null);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();
    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);

    JWTokenAuthority authority = new TestJWTokenAuthority(publicKey, privateKey);
    EasyMock.expect(services.getService(GatewayServices.TOKEN_SERVICE)).andReturn(authority);

    StringWriter writer = new StringWriter();
    PrintWriter printWriter = new PrintWriter(writer);
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.expect(response.getWriter()).andReturn(printWriter);

    EasyMock.replay(principal, services, context, request, response);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.response = response;
    tr.context = context;
    tr.init();

    // Issue a token
    Response retResponse = tr.doGet();

    assertEquals(200, retResponse.getStatus());

    // Parse the response
    String retString = writer.toString();
    String accessToken = getTagValue(retString, "access_token");
    assertNotNull(accessToken);
    String expiry = getTagValue(retString, "expires_in");
    assertNotNull(expiry);

    // Verify the token
    JWT parsedToken = new JWTToken(accessToken);
    assertEquals("alice", parsedToken.getSubject());
    assertTrue(authority.verifyToken(parsedToken));

    Date expiresDate = parsedToken.getExpiresDate();
    Date now = new Date();
    assertTrue(expiresDate.after(now));
    assertTrue((expiresDate.getTime() - now.getTime()) < 30000L);
  }

  private String getTagValue(String token, String tagName) {
    String searchString = tagName + "\":";
    String value = token.substring(token.indexOf(searchString) + searchString.length());
    if (value.startsWith("\"")) {
      value = value.substring(1);
    }
    if (value.contains("\"")) {
      return value.substring(0, value.indexOf("\""));
    } else if (value.contains(",")) {
      return value.substring(0, value.indexOf(","));
    } else {
      return value.substring(0, value.length() - 1);
    }
  }

  private static class TestJWTokenAuthority implements JWTokenAuthority {

    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;

    public TestJWTokenAuthority(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
      this.publicKey = publicKey;
      this.privateKey = privateKey;
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
    public boolean verifyToken(JWT token) throws TokenServiceException {
      JWSVerifier verifier = new RSASSAVerifier(publicKey);
      return token.verify(verifier);
    }

    @Override
    public JWT issueToken(Principal p, String audience, String algorithm,
                               long expires) throws TokenServiceException {
      ArrayList<String> audiences = null;
      if (audience != null) {
        audiences = new ArrayList<>();
        audiences.add(audience);
      }
      return issueToken(p, audiences, algorithm, expires);
    }

    @Override
    public JWT issueToken(Principal p, List<String> audiences, String algorithm,
                               long expires) throws TokenServiceException {
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
      JWSSigner signer = new RSASSASigner(privateKey);
      token.sign(signer);

      return token;
    }

    @Override
    public JWT issueToken(Principal p, String algorithm, long expiry)
        throws TokenServiceException {
      return issueToken(p, Collections.<String>emptyList(), algorithm, expiry);
    }

    @Override
    public boolean verifyToken(JWT token, RSAPublicKey publicKey) throws TokenServiceException {
      JWSVerifier verifier = new RSASSAVerifier(publicKey);
      return token.verify(verifier);
    }

  }


}
