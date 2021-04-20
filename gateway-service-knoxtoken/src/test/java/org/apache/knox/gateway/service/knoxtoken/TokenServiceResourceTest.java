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

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.token.JWTokenAttributes;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Some tests for the token service
 */
public class TokenServiceResourceTest {

  private static RSAPublicKey publicKey;
  private static RSAPrivateKey privateKey;

  private static String TOKEN_API_PATH = "https://gateway-host:8443/gateway/sandbox/knoxtoken/api/v1";
  private static String TOKEN_PATH = "/token";
  private static String JKWS_PATH = "/jwks.json";

  private ServletContext context;
  private HttpServletRequest request;
  private JWTokenAuthority authority;
  private TestTokenStateService tss;

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

  private void configureCommonExpectations(Map<String, String> contextExpectations) throws Exception {
    configureCommonExpectations(contextExpectations, null, null);
  }

  private void configureCommonExpectations(Map<String, String> contextExpectations, String expectedSubjectDN) throws Exception {
    configureCommonExpectations(contextExpectations, expectedSubjectDN, null);
  }

  private void configureCommonExpectations(Map<String, String> contextExpectations, Boolean serverManagedTssEnabled) throws Exception {
    configureCommonExpectations(contextExpectations, null, serverManagedTssEnabled);
  }

  private void configureCommonExpectations(Map<String, String> contextExpectations, String expectedSubjectDN, Boolean serverManagedTssEnabled) throws Exception {
    context = EasyMock.createNiceMock(ServletContext.class);
    contextExpectations.forEach((key, value) -> EasyMock.expect(context.getInitParameter(key)).andReturn(value).anyTimes());
    request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();
    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("alice").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();
    EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(TOKEN_API_PATH+TOKEN_PATH)).anyTimes();

    GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services).anyTimes();

    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(config).anyTimes();
    EasyMock.expect(config.getSigningKeystoreName()).andReturn(null).anyTimes();
    if (serverManagedTssEnabled != null) {
      if (serverManagedTssEnabled) {
        EasyMock.expect(config.isServerManagedTokenStateEnabled()).andReturn(true).anyTimes();
      }
    }
    tss = new TestTokenStateService();
    EasyMock.expect(services.getService(ServiceType.TOKEN_STATE_SERVICE)).andReturn(tss).anyTimes();

    AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(services.getService(ServiceType.ALIAS_SERVICE)).andReturn(aliasService).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(TokenUtils.SIGNING_HMAC_SECRET_ALIAS)).andReturn(null).anyTimes();

    authority = new TestJWTokenAuthority(publicKey, privateKey);
    EasyMock.expect(services.getService(ServiceType.TOKEN_SERVICE)).andReturn(authority).anyTimes();

    if (StringUtils.isNotBlank(expectedSubjectDN)) {
      X509Certificate trustedCertMock = EasyMock.createMock(X509Certificate.class);
      EasyMock.expect(trustedCertMock.getSubjectDN()).andReturn(new PrimaryPrincipal(expectedSubjectDN)).anyTimes();
      ArrayList<X509Certificate> certArrayList = new ArrayList<>();
      certArrayList.add(trustedCertMock);
      X509Certificate[] certs = {};
      EasyMock.expect(request.getAttribute("javax.servlet.request.X509Certificate")).andReturn(certArrayList.toArray(certs)).anyTimes();
      EasyMock.replay(trustedCertMock);
    }
    else {
      EasyMock.expect(request.getAttribute("javax.servlet.request.X509Certificate")).andReturn(null).anyTimes();
    }

    EasyMock.replay(principal, services, context, request, aliasService, config);
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
    configureCommonExpectations(Collections.singletonMap("org.apache.knox.gateway.gateway.cluster", "test"), Boolean.TRUE);

    TokenResource tr = new TokenResource();
    tr.context = context;
    tr.request = request;
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

    assertNotNull(getTagValue(retString, "token_id"));
    assertTrue(Boolean.parseBoolean(getTagValue(retString, "managed")));

    // Verify the token
    JWT parsedToken = new JWTToken(accessToken);
    assertEquals("alice", parsedToken.getSubject());
    assertTrue(authority.verifyToken(parsedToken));
  }

  /*
   * KNOX-2266
   */
  @Test
  public void testConcurrentGetToken() throws Exception {

    configureCommonExpectations(Collections.singletonMap("org.apache.knox.gateway.gateway.cluster", "test"));

    final TokenResource tr = new TokenResource();
    tr.context = context;
    tr.request = request;
    tr.init();

    // Request two tokens concurrently
    Callable<Response> task = tr::doGet;
    List<Callable<Response>> tasks = Collections.nCopies(2, task);
    ExecutorService executorService = Executors.newFixedThreadPool(2);
    List<Future<Response>> futures = executorService.invokeAll(tasks);
    List<Response> responses = new ArrayList<>(futures.size());
    for (Future<Response> f : futures) {
      responses.add(f.get());
    }

    // Parse the responses
    String accessToken1 = getTagValue(responses.get(0).getEntity().toString(), "access_token");
    assertNotNull(accessToken1);
    JWT jwt1 = new JWTToken(accessToken1);

    String accessToken2 = getTagValue(responses.get(1).getEntity().toString(), "access_token");
    assertNotNull(accessToken1);
    JWT jwt2 = new JWTToken(accessToken2);

    // Verify the tokens
    assertNotEquals("Access tokens should be different.", accessToken1, accessToken2);
    assertEquals("The token expirations should be the same.", jwt1.getExpires(), jwt2.getExpires());
    assertNotEquals("Tokens should have unique IDs.", TokenUtils.getTokenId(jwt1), TokenUtils.getTokenId(jwt2));
  }

  @Test
  public void testAudiences() throws Exception {
    final Map<String, String> contextExpectations = new HashMap<>();
    contextExpectations.put("knox.token.audiences", "recipient1,recipient2");
    configureCommonExpectations(contextExpectations);

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
    final Map<String, String> contextExpectations = new HashMap<>();
    contextExpectations.put("knox.token.audiences", " recipient1,recipient2 ");
    configureCommonExpectations(contextExpectations);

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
    final Map<String, String> contextExpectations = new HashMap<>();
    contextExpectations.put("knox.token.client.cert.required", "true");
    contextExpectations.put("knox.token.allowed.principals", "CN=localhost, OU=Test, O=Hadoop, L=Test, ST=Test, C=US");
    configureCommonExpectations(contextExpectations, "CN=localhost,OU=Test, O=Hadoop, L=Test, ST=Test, C=US");

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
    final Map<String, String> contextExpectations = new HashMap<>();
    contextExpectations.put("knox.token.client.cert.required", "true");
    contextExpectations.put("knox.token.allowed.principals", "CN=remotehost, OU=Test, O=Hadoop, L=Test, ST=Test, C=US");
    configureCommonExpectations(contextExpectations, "CN=localhost,OU=Test, O=Hadoop, L=Test, ST=Test, C=US");

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
    final Map<String, String> contextExpectations = new HashMap<>();
    contextExpectations.put("knox.token.client.cert.required", "true");
    contextExpectations.put("knox.token.allowed.principals", "CN=remotehost, OU=Test, O=Hadoop, L=Test, ST=Test, C=US");
    configureCommonExpectations(contextExpectations, (String) null);

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
    final Map<String, String> contextExpectations = new HashMap<>();
    contextExpectations.put("knox.token.sigalg", "RS512");
    configureCommonExpectations(contextExpectations);

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
    final Map<String, String> contextExpectations = new HashMap<>();
    contextExpectations.put("knox.token.ttl", null);
    configureCommonExpectations(contextExpectations);

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
    final Map<String, String> contextExpectations = new HashMap<>();
    contextExpectations.put("knox.token.ttl", "60000");
    configureCommonExpectations(contextExpectations);

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
    final Map<String, String> contextExpectations = new HashMap<>();
    contextExpectations.put("knox.token.ttl", "-60000");
    configureCommonExpectations(contextExpectations);

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
    final Map<String, String> contextExpectations = new HashMap<>();
    contextExpectations.put("knox.token.ttl", String.valueOf(Long.MAX_VALUE));
    configureCommonExpectations(contextExpectations);

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
  public void testTokenRenewal_ServerManagedStateConfiguredAtGatewayOnly() throws Exception {
    final String caller = "yarn";
    Response renewalResponse = doTestTokenRenewal(null, true, caller, null, createTestSubject(caller)).getValue();
    validateSuccessfulRenewalResponse(renewalResponse);
  }

  @Test
  public void testTokenRenewal_ServerManagedStateDisabledAtGatewayWithServiceOverride() throws Exception {
    final String caller = "yarn";
    Response renewalResponse = doTestTokenRenewal(true, false, caller, null, createTestSubject(caller)).getValue();
    validateSuccessfulRenewalResponse(renewalResponse);
  }

  @Test
  public void testTokenRenewal_ServerManagedStateEnabledAtGatewayWithServiceOverride() throws Exception {
    final String caller = "yarn";
    Map.Entry<TestTokenStateService, Response> result =
            doTestTokenRenewal(false, true, caller, null, createTestSubject(caller));

    // Make sure the expiration was not recorded by the TokenStateService, since it is disabled for this test
    TestTokenStateService tss = result.getKey();
    assertEquals("TokenStateService should be disabled for this test.", 0, tss.expirationData.size());

    Response renewalResponse = result.getValue();
    validateSuccessfulRenewalResponse(renewalResponse);
    String responseContent = (String) renewalResponse.getEntity();
    assertNotNull(responseContent);
    Map<String, String> json = parseJSONResponse(responseContent);
    assertTrue(Boolean.parseBoolean(json.get("renewed")));
    assertNotNull(json.get("expires")); // Should get back the original expiration from the token itself
  }

  @Test
  public void testTokenRenewal_ServerManagedStateNotConfiguredAtAll() throws Exception {
    Map.Entry<TestTokenStateService, Response> result = doTestTokenRenewal(null, null, null, null, null);

    // Make sure the expiration was not recorded by the TokenStateService, since it is disabled for this test
    TestTokenStateService tss = result.getKey();
    assertEquals("TokenStateService should be disabled for this test.", 0, tss.expirationData.size());

    Response renewalResponse = result.getValue();
    validateSuccessfulRenewalResponse(renewalResponse);
    String responseContent = (String) renewalResponse.getEntity();
    assertNotNull(responseContent);
    Map<String, String> json = parseJSONResponse(responseContent);
    assertTrue(Boolean.parseBoolean(json.get("renewed")));
    assertNotNull(json.get("expires")); // Should get back the original expiration from the token itself
  }

  @Test
  public void testTokenRenewal_Disabled() throws Exception {
    Map.Entry<TestTokenStateService, Response> result = doTestTokenRenewal(false, null, null, null);

    // Make sure the expiration was not recorded by the TokenStateService, since it is disabled for this test
    TestTokenStateService tss = result.getKey();
    assertEquals("TokenStateService should be disabled for this test.", 0, tss.expirationData.size());

    Response renewalResponse = result.getValue();
    validateSuccessfulRenewalResponse(renewalResponse);
    String responseContent = (String) renewalResponse.getEntity();
    assertNotNull(responseContent);
    Map<String, String> json = parseJSONResponse(responseContent);
    assertTrue(Boolean.parseBoolean(json.get("renewed")));
    assertNotNull(json.get("expires")); // Should get back the original expiration from the token itself
  }

  @Test
  public void testTokenRenewal_Enabled_NoRenewersNoSubject() throws Exception {
    Response renewalResponse = doTestTokenRenewal(true, null, null);
    validateRenewalResponse(renewalResponse, 403, false, "Caller (null) not authorized to renew tokens.");
  }

  @Test
  public void testTokenRenewal_Enabled_NoRenewersWithSubject() throws Exception {
    final String caller = "yarn";
    Response renewalResponse = doTestTokenRenewal(true, null, createTestSubject(caller));
    validateRenewalResponse(renewalResponse,
                            403,
                            false,
                            "Caller (" + caller + ") not authorized to renew tokens.");
  }

  @Test
  public void testTokenRenewal_Enabled_WithRenewersNoSubject() throws Exception {
    Response renewalResponse = doTestTokenRenewal(true, "larry, moe,  curly ", null);
    validateRenewalResponse(renewalResponse,
                            403,
                            false,
                            "Caller (null) not authorized to renew tokens.");
  }

  @Test
  public void testTokenRenewal_Enabled_WithRenewersWithInvalidSubject() throws Exception {
    final String caller = "shemp";
    Response renewalResponse = doTestTokenRenewal(true, "larry, moe,  curly ", createTestSubject(caller));
    validateRenewalResponse(renewalResponse,
                            403,
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
                               403,
                               false,
                               "Caller (null) not authorized to revoke tokens.");
  }

  @Test
  public void testTokenRevocation_Enabled_NoRenewersWithSubject() throws Exception {
    final String caller = "yarn";
    Response renewalResponse = doTestTokenRevocation(true, null, createTestSubject(caller));
    validateRevocationResponse(renewalResponse,
                               403,
                               false,
                               "Caller (" + caller + ") not authorized to revoke tokens.");
  }

  @Test
  public void testTokenRevocation_Enabled_WithRenewersNoSubject() throws Exception {
    Response renewalResponse = doTestTokenRevocation(true, "larry, moe,  curly ", null);
    validateRevocationResponse(renewalResponse,
                               403,
                               false,
                               "Caller (null) not authorized to revoke tokens.");
  }

  @Test
  public void testTokenRevocation_Enabled_WithRenewersWithInvalidSubject() throws Exception {
    final String caller = "shemp";
    Response renewalResponse = doTestTokenRevocation(true, "larry, moe,  curly ", createTestSubject(caller));
    validateRevocationResponse(renewalResponse,
                               403,
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

  @Test
  public void testKidJkuClaims() throws Exception {
    final Map<String, String> contextExpectations = new HashMap<>();
    contextExpectations.put("knox.token.ttl", "60000");
    configureCommonExpectations(contextExpectations);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.context = context;
    tr.init();

    // Issue a token
    Response retResponse = tr.doGet();

    assertEquals(200, retResponse.getStatus());

    // Parse the response
    final String retString = retResponse.getEntity().toString();
    final String accessToken = getTagValue(retString, "access_token");
    assertNotNull(accessToken);

    // Verify the token
    final JWT parsedToken = new JWTToken(accessToken);
    assertEquals("alice", parsedToken.getSubject());
    assertTrue(authority.verifyToken(parsedToken));

    assertNotNull(parsedToken.getClaim("kid"));
    assertEquals(TOKEN_API_PATH+JKWS_PATH, parsedToken.getClaim("jku"));
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
    return doTestTokenRenewal(isTokenStateServerManaged,
                              null,
                              renewers,
                              maxTokenLifetime,
                              caller);
  }

  /**
   *
   * @param serviceLevelConfig true, if server-side token state management should be enabled; Otherwise, false or null.
   * @param gatewayLevelConfig true, if server-side token state management should be enabled; Otherwise, false or null.
   * @param renewers           A comma-delimited list of permitted renewer user names
   * @param maxTokenLifetime   The maximum duration (milliseconds) for a token's lifetime
   * @param caller             The user name making the request
   *
   * @return The Response from the token renewal request
   *
   * @throws Exception
   */
  private Map.Entry<TestTokenStateService, Response> doTestTokenRenewal(final Boolean serviceLevelConfig,
                                                                        final Boolean gatewayLevelConfig,
                                                                        final String  renewers,
                                                                        final Long    maxTokenLifetime,
                                                                        final Subject caller) throws Exception {
    return doTestTokenLifecyle(TokenLifecycleOperation.Renew,
                               serviceLevelConfig,
                               gatewayLevelConfig,
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
   * @param operation          A TokenLifecycleOperation
   * @param serviceLevelConfig true, if server-side token state management should be enabled at the service level;
   *                           Otherwise, false or null.
   * @param renewers           A comma-delimited list of permitted renewer user names
   * @param maxTokenLifetime   The maximum lifetime duration for a token.
   * @param caller             The user name making the request
   *
   * @return The Response from the token revocation request
   *
   * @throws Exception
   */
  private Map.Entry<TestTokenStateService, Response> doTestTokenLifecyle(final TokenLifecycleOperation operation,
                                                                         final Boolean                 serviceLevelConfig,
                                                                         final String                  renewers,
                                                                         final Long                    maxTokenLifetime,
                                                                         final Subject                 caller) throws Exception {
    return doTestTokenLifecyle(operation, serviceLevelConfig, null, renewers, maxTokenLifetime, caller);
  }

  /**
   * @param operation          A TokenLifecycleOperation
   * @param serviceLevelConfig true, if server-side token state management should be enabled at the service level;
   *                           Otherwise, false or null.
   * @param gatewayLevelConfig true, if server-side token state management should be enabled at the gateway level;
   *                           Otherwise, false or null.
   * @param renewers           A comma-delimited list of permitted renewer user names
   * @param maxTokenLifetime   The maximum lifetime duration for a token.
   * @param caller             The user name making the request
   *
   * @return The Response from the token revocation request
   *
   * @throws Exception
   */
  private Map.Entry<TestTokenStateService, Response> doTestTokenLifecyle(final TokenLifecycleOperation operation,
                                                                         final Boolean                 serviceLevelConfig,
                                                                         final Boolean                 gatewayLevelConfig,
                                                                         final String                  renewers,
                                                                         final Long                    maxTokenLifetime,
                                                                         final Subject                 caller) throws Exception {

    final Map<String, String> contextExpectations = new HashMap<>();
    contextExpectations.put("knox.token.audiences", "recipient1,recipient2");
    contextExpectations.put("knox.token.ttl", String.valueOf(Long.MAX_VALUE));
    if (serviceLevelConfig != null) {
      contextExpectations.put("knox.token.exp.server-managed", String.valueOf(serviceLevelConfig));
      if (maxTokenLifetime != null) {
        contextExpectations.put("knox.token.exp.renew-interval", String.valueOf(maxTokenLifetime / 2));
        contextExpectations.put("knox.token.exp.max-lifetime", maxTokenLifetime.toString());
      }
    }
    contextExpectations.put("knox.token.renewer.whitelist", renewers);

    configureCommonExpectations(contextExpectations, gatewayLevelConfig);

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
      addToken(TokenUtils.getTokenId(token), issueTime, token.getExpiresDate().getTime());
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
    public void addToken(String tokenId, long issueTime, long expiration) {
      addToken(tokenId, issueTime, expiration, getDefaultMaxLifetimeDuration());
    }

    @Override
    public void addToken(String tokenId, long issueTime, long expiration, long maxLifetimeDuration) {
      issueTimes.put(tokenId, issueTime);
      expirationData.put(tokenId, expiration);
      maxLifetimes.put(tokenId, issueTime + maxLifetimeDuration);
    }

    @Override
    public boolean isExpired(JWTToken token) {
      return false;
    }

    @Override
    public void revokeToken(JWTToken token) {
      revokeToken(TokenUtils.getTokenId(token));
    }

    @Override
    public void revokeToken(String tokenId) {
    }

    @Override
    public long renewToken(JWTToken token) {
      return renewToken(TokenUtils.getTokenId(token));
    }

    @Override
    public long renewToken(String tokenId) {
      return renewToken(tokenId, 0L);
    }

    @Override
    public long renewToken(JWTToken token, long renewInterval) {
      return renewToken(TokenUtils.getTokenId(token), renewInterval);
    }

    @Override
    public long renewToken(String tokenId, long renewInterval) {
      return 0;
    }

    @Override
    public long getTokenExpiration(JWT token) throws UnknownTokenException {
      return 0;
    }

    @Override
    public long getTokenExpiration(String tokenId) {
      return 0;
    }

    @Override
    public long getTokenExpiration(String tokenId, boolean validate) throws UnknownTokenException {
      return 0;
    }

    @Override
    public void addMetadata(String tokenId, TokenMetadata metadata) {
    }

    @Override
    public TokenMetadata getTokenMetadata(String tokenId) throws UnknownTokenException {
      return null;
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
    public boolean verifyToken(JWT token) {
      JWSVerifier verifier = new RSASSAVerifier(publicKey);
      return token.verify(verifier);
    }

    @Override
    public JWT issueToken(JWTokenAttributes jwtAttributes) {
      String[] claimArray = new String[6];
      claimArray[0] = "KNOXSSO";
      claimArray[1] = jwtAttributes.getPrincipal().getName();
      claimArray[2] = null;
      if (jwtAttributes.getExpires() == -1) {
        claimArray[3] = null;
      } else {
        claimArray[3] = String.valueOf(jwtAttributes.getExpires());
      }
      claimArray[4] = "E0LDZulQ0XE_otJ5aoQtQu-RnXv8hU-M9U4dD7vDioA";
      claimArray[5] = jwtAttributes.getJku();

      JWT token = new JWTToken(jwtAttributes.getAlgorithm(), claimArray, jwtAttributes.getAudiences());
      JWSSigner signer = new RSASSASigner(privateKey);
      token.sign(signer);

      return token;
    }


    @Override
    public boolean verifyToken(JWT token, RSAPublicKey publicKey) {
      JWSVerifier verifier = new RSASSAVerifier(publicKey);
      return token.verify(verifier);
    }

    @Override
    public boolean verifyToken(JWT token, String jwksurl, String algorithm) {
     return false;
    }
  }
}
