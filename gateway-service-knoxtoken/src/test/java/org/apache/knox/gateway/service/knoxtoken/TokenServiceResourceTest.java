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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.security.auth.Subject;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Some tests for the token service
 */
public class TokenServiceResourceTest {

  private static RSAPublicKey publicKey;
  private static RSAPrivateKey privateKey;

  private enum TokenLifecycleOperation {
    Renew,
    Revoke
  }

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair KPair = kpg.generateKeyPair();

    publicKey = (RSAPublicKey) KPair.getPublic();
    privateKey = (RSAPrivateKey) KPair.getPrivate();
  }

  @Test
  public void testTokenService() {
    Assert.assertTrue(true);
  }

  @Test
  public void testClientData() {
    TokenResource tr = new TokenResource();

    Map<String,Object> clientDataMap = new HashMap<>();
    tr.addClientDataToMap("cookie.name=hadoop-jwt,test=value".split(","), clientDataMap);
    Assert.assertEquals(2, clientDataMap.size());

    clientDataMap = new HashMap<>();
    tr.addClientDataToMap("cookie.name=hadoop-jwt".split(","), clientDataMap);
    Assert.assertEquals(1, clientDataMap.size());

    clientDataMap = new HashMap<>();
    tr.addClientDataToMap("".split(","), clientDataMap);
    Assert.assertEquals(0, clientDataMap.size());
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
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    EasyMock.replay(principal, services, context, request);

    TokenResource tr = new TokenResource();
    tr.request = request;

    // Issue a token
    Response retResponse = tr.doGet();

    assertEquals(200, retResponse.getStatus());

    // Parse the response
    String retString = retResponse.getEntity().toString();
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
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    EasyMock.replay(principal, services, context, request);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.context = context;
    tr.init();

    // Issue a token
    Response retResponse = tr.doGet();

    assertEquals(200, retResponse.getStatus());

    // Parse the response
    String retString = retResponse.getEntity().toString();
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
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    EasyMock.replay(principal, services, context, request);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.context = context;
    tr.init();

    // Issue a token
    Response retResponse = tr.doGet();

    assertEquals(200, retResponse.getStatus());

    // Parse the response
    String retString = retResponse.getEntity().toString();
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
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    EasyMock.replay(principal, services, context, request, trustedCertMock);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.context = context;
    tr.init();

    // Issue a token
    Response retResponse = tr.doGet();

    assertEquals(200, retResponse.getStatus());

    // Parse the response
    String retString = retResponse.getEntity().toString();
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
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    EasyMock.replay(principal, services, context, request, trustedCertMock);

    TokenResource tr = new TokenResource();
    tr.request = request;
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
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    EasyMock.replay(principal, services, context, request);

    TokenResource tr = new TokenResource();
    tr.request = request;
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
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    EasyMock.replay(principal, services, context, request);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.context = context;
    tr.init();

    // Issue a token
    Response retResponse = tr.doGet();

    assertEquals(200, retResponse.getStatus());

    // Parse the response
    String retString = retResponse.getEntity().toString();
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
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    EasyMock.replay(principal, services, context, request);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.context = context;
    tr.init();

    // Issue a token
    Response retResponse = tr.doGet();

    assertEquals(200, retResponse.getStatus());

    // Parse the response
    String retString = retResponse.getEntity().toString();
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
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    EasyMock.replay(principal, services, context, request);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.context = context;
    tr.init();

    // Issue a token
    Response retResponse = tr.doGet();

    assertEquals(200, retResponse.getStatus());

    // Parse the response
    String retString = retResponse.getEntity().toString();
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
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    EasyMock.replay(principal, services, context, request);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.context = context;
    tr.init();

    // Issue a token
    Response retResponse = tr.doGet();

    assertEquals(200, retResponse.getStatus());

    // Parse the response
    String retString = retResponse.getEntity().toString();
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
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority);

    EasyMock.replay(principal, services, context, request);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.context = context;
    tr.init();

    // Issue a token
    Response retResponse = tr.doGet();

    assertEquals(200, retResponse.getStatus());

    // Parse the response
    String retString = retResponse.getEntity().toString();
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
  public void testTokenRenewal_ServerManagedStateNotConfigured() throws Exception {
    Response renewalResponse = doTestTokenRenewal(null, null, null);
    validateRenewalResponse(renewalResponse, 400, false, "Token renewal support is not configured");
  }

  @Test
  public void testTokenRenewal_Disabled() throws Exception {
    Response renewalResponse = doTestTokenRenewal(false, null, null);
    validateRenewalResponse(renewalResponse, 400, false, "Token renewal support is not configured");
  }

  @Test
  public void testTokenRenewal_Enabled_NoRenewersNoSubject() throws Exception {
    Response renewalResponse = doTestTokenRenewal(true, null, null);
    validateRenewalResponse(renewalResponse, 400, false, "Caller (null) not authorized to renew tokens.");
  }

  @Test
  public void testTokenRenewal_Enabled_NoRenewersWithSubject() throws Exception {
    final String caller = "yarn";
    Response renewalResponse = doTestTokenRenewal(true, null, createTestSubject(caller));
    validateRenewalResponse(renewalResponse,
                            400,
                            false,
                            "Caller (" + caller + ") not authorized to renew tokens.");
  }

  @Test
  public void testTokenRenewal_Enabled_WithRenewersNoSubject() throws Exception {
    Response renewalResponse = doTestTokenRenewal(true, "larry, moe,  curly ", null);
    validateRenewalResponse(renewalResponse,
                            400,
                            false,
                            "Caller (null) not authorized to renew tokens.");
  }

  @Test
  public void testTokenRenewal_Enabled_WithRenewersWithInvalidSubject() throws Exception {
    final String caller = "shemp";
    Response renewalResponse = doTestTokenRenewal(true, "larry, moe,  curly ", createTestSubject(caller));
    validateRenewalResponse(renewalResponse,
                            400,
                            false,
                            "Caller (" + caller + ") not authorized to renew tokens.");
  }

  @Test
  public void testTokenRenewal_Enabled_WithRenewersWithValidSubject() throws Exception {
    final String caller = "shemp";
    Response renewalResponse =
                      doTestTokenRenewal(true, ("larry, moe,  curly ," + caller), createTestSubject(caller));
    validateSuccessfulRenewalResponse(renewalResponse);
  }

  @Test
  public void testTokenRenewal_Enabled_WithDefaultMaxTokenLifetime() throws Exception {
    final String caller = "yarn";

    // Max lifetime duration is 10ms
    Map.Entry<TestTokenStateService, Response> testResult =
                  doTestTokenRenewal(true, caller, null, createTestSubject(caller));

    TestTokenStateService tss = testResult.getKey();
    assertEquals(1, tss.issueTimes.size());
    String token = tss.issueTimes.keySet().iterator().next();

    // Verify that the configured max lifetime was honored
    assertEquals(tss.getDefaultMaxLifetimeDuration(), tss.getMaxLifetime(token) - tss.getIssueTime(token));
  }


  @Test
  public void testTokenRenewal_Enabled_WithConfigurableMaxTokenLifetime() throws Exception {
    final String caller = "yarn";

    // Max lifetime duration is 10ms
    Map.Entry<TestTokenStateService, Response> testResult =
                                              doTestTokenRenewal(true, caller, 10L, createTestSubject(caller));

    TestTokenStateService tss = testResult.getKey();
    assertEquals(1, tss.issueTimes.size());
    String token = tss.issueTimes.keySet().iterator().next();

    // Verify that the configured max lifetime was honored
    assertEquals(10L, tss.getMaxLifetime(token) - tss.getIssueTime(token));
  }


  @Test
  public void testTokenRevocation_ServerManagedStateNotConfigured() throws Exception {
    Response renewalResponse = doTestTokenRevocation(null, null, null);
    validateRevocationResponse(renewalResponse,
                               400,
                               false,
                               "Token revocation support is not configured");
  }

  @Test
  public void testTokenRevocation_Disabled() throws Exception {
    Response renewalResponse = doTestTokenRevocation(false, null, null);
    validateRevocationResponse(renewalResponse,
                               400,
                               false,
                               "Token revocation support is not configured");
  }

  @Test
  public void testTokenRevocation_Enabled_NoRenewersNoSubject() throws Exception {
    Response renewalResponse = doTestTokenRevocation(true, null, null);
    validateRevocationResponse(renewalResponse,
                               400,
                               false,
                               "Caller (null) not authorized to revoke tokens.");
  }

  @Test
  public void testTokenRevocation_Enabled_NoRenewersWithSubject() throws Exception {
    final String caller = "yarn";
    Response renewalResponse = doTestTokenRevocation(true, null, createTestSubject(caller));
    validateRevocationResponse(renewalResponse,
                               400,
                               false,
                               "Caller (" + caller + ") not authorized to revoke tokens.");
  }

  @Test
  public void testTokenRevocation_Enabled_WithRenewersNoSubject() throws Exception {
    Response renewalResponse = doTestTokenRevocation(true, "larry, moe,  curly ", null);
    validateRevocationResponse(renewalResponse,
                               400,
                               false,
                               "Caller (null) not authorized to revoke tokens.");
  }

  @Test
  public void testTokenRevocation_Enabled_WithRenewersWithInvalidSubject() throws Exception {
    final String caller = "shemp";
    Response renewalResponse = doTestTokenRevocation(true, "larry, moe,  curly ", createTestSubject(caller));
    validateRevocationResponse(renewalResponse,
                               400,
                               false,
                               "Caller (" + caller + ") not authorized to revoke tokens.");
  }

  @Test
  public void testTokenRevocation_Enabled_WithRenewersWithValidSubject() throws Exception {
    final String caller = "shemp";
    Response renewalResponse =
        doTestTokenRevocation(true, ("larry, moe,  curly ," + caller), createTestSubject(caller));
    validateSuccessfulRevocationResponse(renewalResponse);
  }


  /**
   *
   * @param isTokenStateServerManaged true, if server-side token state management should be enabled; Otherwise, false or null.
   * @param renewers A comma-delimited list of permitted renewer user names
   * @param caller The user name making the request
   *
   * @return The Response from the token renewal request
   *
   * @throws Exception
   */
  private Response doTestTokenRenewal(final Boolean isTokenStateServerManaged,
                                      final String  renewers,
                                      final Subject caller) throws Exception {
    return doTestTokenRenewal(isTokenStateServerManaged, renewers, null, caller).getValue();
  }

  /**
   *
   * @param isTokenStateServerManaged true, if server-side token state management should be enabled; Otherwise, false or null.
   * @param renewers                  A comma-delimited list of permitted renewer user names
   * @param maxTokenLifetime          The maximum duration (milliseconds) for a token's lifetime
   * @param caller                    The user name making the request
   *
   * @return The Response from the token renewal request
   *
   * @throws Exception
   */
  private Map.Entry<TestTokenStateService, Response> doTestTokenRenewal(final Boolean isTokenStateServerManaged,
                                                                        final String  renewers,
                                                                        final Long    maxTokenLifetime,
                                                                        final Subject caller) throws Exception {
    return doTestTokenLifecyle(TokenLifecycleOperation.Renew,
                               isTokenStateServerManaged,
                               renewers,
                               maxTokenLifetime,
                               caller);
  }

  /**
   *
   * @param isTokenStateServerManaged true, if server-side token state management should be enabled; Otherwise, false or null.
   * @param renewers A comma-delimited list of permitted renewer user names
   * @param caller The user name making the request
   *
   * @return The Response from the token revocation request
   *
   * @throws Exception
   */
  private Response doTestTokenRevocation(final Boolean isTokenStateServerManaged,
                                         final String  renewers,
                                         final Subject caller) throws Exception {
    return doTestTokenLifecyle(TokenLifecycleOperation.Revoke, isTokenStateServerManaged, renewers, caller);
  }

  /**
   * @param operation     A TokenLifecycleOperation
   * @param serverManaged true, if server-side token state management should be enabled; Otherwise, false or null.
   * @param renewers      A comma-delimited list of permitted renewer user names
   * @param caller        The user name making the request
   *
   * @return The Response from the token revocation request
   *
   * @throws Exception
   */
  private Response doTestTokenLifecyle(final TokenLifecycleOperation operation,
                                       final Boolean                 serverManaged,
                                       final String                  renewers,
                                       final Subject                 caller) throws Exception {
    return doTestTokenLifecyle(operation, serverManaged, renewers, null, caller).getValue();
  }

  /**
   * @param operation                 A TokenLifecycleOperation
   * @param isTokenStateServerManaged true, if server-side token state management should be enabled; Otherwise, false or null.
   * @param renewers                  A comma-delimited list of permitted renewer user names
   * @param maxTokenLifetime          The maximum lifetime duration for a token.
   * @param caller                    The user name making the request
   *
   * @return The Response from the token revocation request
   *
   * @throws Exception
   */
  private Map.Entry<TestTokenStateService, Response> doTestTokenLifecyle(final TokenLifecycleOperation operation,
                                                                         final Boolean                 isTokenStateServerManaged,
                                                                         final String                  renewers,
                                                                         final Long                    maxTokenLifetime,
                                                                         final Subject                 caller) throws Exception {
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameter("knox.token.audiences")).andReturn("recipient1,recipient2");
    EasyMock.expect(context.getInitParameter("knox.token.ttl")).andReturn(String.valueOf(Long.MAX_VALUE));
    EasyMock.expect(context.getInitParameter("knox.token.target.url")).andReturn(null);
    EasyMock.expect(context.getInitParameter("knox.token.client.data")).andReturn(null);
    if (isTokenStateServerManaged != null) {
      EasyMock.expect(context.getInitParameter("knox.token.exp.server-managed"))
              .andReturn(String.valueOf(isTokenStateServerManaged));
      if (maxTokenLifetime != null) {
        EasyMock.expect(context.getInitParameter("knox.token.exp.renew-interval")).andReturn(String.valueOf(maxTokenLifetime / 2));
        EasyMock.expect(context.getInitParameter("knox.token.exp.max-lifetime")).andReturn(maxTokenLifetime.toString());
      }
    }
    EasyMock.expect(context.getInitParameter("knox.token.renewer.whitelist")).andReturn(renewers);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();
    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services).anyTimes();

    JWTokenAuthority authority = new TestJWTokenAuthority(publicKey, privateKey);
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority).anyTimes();

    TestTokenStateService tss = new TestTokenStateService();
    EasyMock.expect(services.getService(ServiceType.TOKEN_STATE_SERVICE)).andReturn(tss).anyTimes();

    EasyMock.replay(principal, services, context, request);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.context = context;
    tr.init();

    // Request a token
    Response retResponse = tr.doGet();
    assertEquals(200, retResponse.getStatus());

    // Parse the response
    String retString = retResponse.getEntity().toString();
    String accessToken = getTagValue(retString, "access_token");
    assertNotNull(accessToken);

    Response response;
    switch (operation) {
      case Renew:
        response = requestTokenRenewal(tr, accessToken, caller);
        break;
      case Revoke:
        response = requestTokenRevocation(tr, accessToken, caller);
        break;
      default:
        throw new Exception("Invalid operation: " + operation);
    }

    return new AbstractMap.SimpleEntry<>(tss, response);
  }

  private static Response requestTokenRenewal(final TokenResource tr, final String tokenData, final Subject caller) {
    Response response;
    if (caller != null) {
      response = Subject.doAs(caller, (PrivilegedAction<Response>) () -> tr.renew(tokenData));
    } else {
      response = tr.renew(tokenData);
    }
    return response;
  }

  private static Response requestTokenRevocation(final TokenResource tr, final String tokenData, final Subject caller) {
    Response response;
    if (caller != null) {
      response = Subject.doAs(caller, (PrivilegedAction<Response>) () -> tr.revoke(tokenData));
    } else {
      response = tr.revoke(tokenData);
    }
    return response;
  }

  private static void validateSuccessfulRenewalResponse(final Response response) throws IOException {
    validateRenewalResponse(response, 200, true, null);
  }

  private static void validateRenewalResponse(final Response response,
                                              final int      expectedStatusCode,
                                              final boolean  expectedResult,
                                              final String   expectedMessage) throws IOException {
    assertEquals(expectedStatusCode, response.getStatus());
    assertTrue(response.hasEntity());
    String responseContent = (String) response.getEntity();
    assertNotNull(responseContent);
    assertFalse(responseContent.isEmpty());
    Map<String, String> json = parseJSONResponse(responseContent);
    boolean result = Boolean.valueOf(json.get("renewed"));
    assertEquals(expectedResult, result);
    assertEquals(expectedMessage, json.get("error"));
  }

  private static void validateSuccessfulRevocationResponse(final Response response) throws IOException {
    validateRevocationResponse(response, 200, true, null);
  }

  private static void validateRevocationResponse(final Response response,
                                                 final int      expectedStatusCode,
                                                 final boolean  expectedResult,
                                                 final String   expectedMessage) throws IOException {
    assertEquals(expectedStatusCode, response.getStatus());
    assertTrue(response.hasEntity());
    String responseContent = (String) response.getEntity();
    assertNotNull(responseContent);
    assertFalse(responseContent.isEmpty());
    Map<String, String> json = parseJSONResponse(responseContent);
    boolean result = Boolean.valueOf(json.get("revoked"));
    assertEquals(expectedResult, result);
    assertEquals(expectedMessage, json.get("error"));
  }


  private String getTagValue(String token, String tagName) {
    String searchString = tagName + "\":";
    String value = token.substring(token.indexOf(searchString) + searchString.length());
    if (value.startsWith("\"")) {
      value = value.substring(1);
    }
    if (value.contains("\"")) {
      return value.substring(0, value.indexOf('\"'));
    } else if (value.contains(",")) {
      return value.substring(0, value.indexOf(','));
    } else {
      return value.substring(0, value.length() - 1);
    }
  }

  /**
   * Create a Subject for testing.
   *
   * @param username The user identifier
   *
   * @return A Subject
   */
  private Subject createTestSubject(final String username) {
    Subject s = new Subject();

    Set<Principal> principals = s.getPrincipals();
    principals.add(new PrimaryPrincipal(username));

    return s;
  }

  private static Map<String, String> parseJSONResponse(final String response) throws IOException {
    return (new ObjectMapper()).readValue(response, new TypeReference<Map<String, String>>(){});
  }


  private static class TestTokenStateService implements TokenStateService {

    private Map<String, Long> expirationData = new HashMap<>();
    private Map<String, Long> issueTimes = new HashMap<>();
    private Map<String, Long> maxLifetimes = new HashMap<>();

    long getIssueTime(final String token) {
      return issueTimes.get(token);
    }

    long getMaxLifetime(final String token) {
      return maxLifetimes.get(token);
    }

    long getExpiration(final String token) {
      return expirationData.get(token);
    }

    @Override
    public void addToken(JWTToken token, long issueTime) {
      addToken(token.getPayload(), issueTime, token.getExpiresDate().getTime());
    }

    @Override
    public long getDefaultRenewInterval() {
      return 250;
    }

    @Override
    public long getDefaultMaxLifetimeDuration() {
      return 500;
    }

    @Override
    public void addToken(String token, long issueTime, long expiration) {
      addToken(token, issueTime, expiration, getDefaultMaxLifetimeDuration());
    }

    @Override
    public void addToken(String token, long issueTime, long expiration, long maxLifetimeDuration) {
      issueTimes.put(token, issueTime);
      expirationData.put(token, expiration);
      maxLifetimes.put(token, issueTime + maxLifetimeDuration);
    }

    @Override
    public boolean isExpired(JWTToken token) {
      return isExpired(token.getPayload());
    }

    @Override
    public boolean isExpired(String token) {
      return false;
    }

    @Override
    public void revokeToken(JWTToken token) {
      revokeToken(token.getPayload());
    }

    @Override
    public void revokeToken(String token) {
    }

    @Override
    public long renewToken(JWTToken token) {
      return renewToken(token.getPayload());
    }

    @Override
    public long renewToken(String token) {
      return renewToken(token, 0L);
    }

    @Override
    public long renewToken(JWTToken token, long renewInterval) {
      return renewToken(token.getPayload());
    }

    @Override
    public long renewToken(String token, long renewInterval) {
      return 0;
    }

    @Override
    public long getTokenExpiration(String token) {
      return 0;
    }

    @Override
    public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
    }

    @Override
    public void start() throws ServiceLifecycleException {
    }

    @Override
    public void stop() throws ServiceLifecycleException {
    }
  }

  private static class TestJWTokenAuthority implements JWTokenAuthority {

    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;

    TestJWTokenAuthority(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
      this.publicKey = publicKey;
      this.privateKey = privateKey;
    }

    @Override
    public JWT issueToken(Subject subject, String algorithm) {
      Principal p = (Principal) subject.getPrincipals().toArray()[0];
      return issueToken(p, algorithm);
    }

    @Override
    public JWT issueToken(Principal p, String algorithm) {
      return issueToken(p, null, algorithm);
    }

    @Override
    public JWT issueToken(Principal p, String audience, String algorithm) {
      return issueToken(p, audience, algorithm, -1);
    }

    @Override
    public boolean verifyToken(JWT token) {
      JWSVerifier verifier = new RSASSAVerifier(publicKey);
      return token.verify(verifier);
    }

    @Override
    public JWT issueToken(Principal p, String audience, String algorithm,
                               long expires) {
      ArrayList<String> audiences = null;
      if (audience != null) {
        audiences = new ArrayList<>();
        audiences.add(audience);
      }
      return issueToken(p, audiences, algorithm, expires);
    }

    @Override
    public JWT issueToken(Principal p, List<String> audiences, String algorithm, long expires,
                          String signingkeyName, String signingkeyAlias, char[] signingkeyPassphrase) {
      return issueToken(p, audiences, algorithm, expires);
    }

    @Override
    public JWT issueToken(Principal p, List<String> audiences, String algorithm, long expires) {
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
    public JWT issueToken(Principal p, String algorithm, long expiry) {
      return issueToken(p, Collections.emptyList(), algorithm, expiry);
    }

    @Override
    public boolean verifyToken(JWT token, RSAPublicKey publicKey) {
      JWSVerifier verifier = new RSASSAVerifier(publicKey);
      return token.verify(verifier);
    }
  }
}
