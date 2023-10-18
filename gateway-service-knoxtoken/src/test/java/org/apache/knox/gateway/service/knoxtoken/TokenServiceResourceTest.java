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

import static org.apache.knox.gateway.config.impl.GatewayConfigImpl.KNOX_TOKEN_USER_LIMIT;
import static org.apache.knox.gateway.config.impl.GatewayConfigImpl.KNOX_TOKEN_USER_LIMIT_DEFAULT;
import static org.apache.knox.gateway.service.knoxtoken.TokenResource.KNOX_TOKEN_ISSUER;
import static org.apache.knox.gateway.service.knoxtoken.TokenResource.KNOX_TOKEN_USER_LIMIT_EXCEEDED_ACTION;
import static org.apache.knox.gateway.service.knoxtoken.TokenResource.TOKEN_INCLUDE_GROUPS_IN_JWT_ALLOWED;
import static org.apache.knox.gateway.services.security.token.JWTokenAttributes.DEFAULT_ISSUER;
import static org.apache.knox.gateway.services.security.token.impl.JWTToken.KNOX_GROUPS_CLAIM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import javax.security.auth.Subject;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.context.ContextAttributes;
import org.apache.knox.gateway.security.ImpersonatedPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.token.JWTokenAttributes;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.KnoxToken;
import org.apache.knox.gateway.services.security.token.PersistentTokenStateService;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.services.security.token.impl.TokenMAC;
import org.apache.knox.gateway.services.token.impl.JDBCTokenStateService;
import org.apache.knox.gateway.util.AuthFilterUtils;
import org.apache.knox.gateway.util.JsonUtils;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Some tests for the token service
 */
public class TokenServiceResourceTest {

  private static RSAPublicKey publicKey;
  private static RSAPrivateKey privateKey;

  private static final String TOKEN_API_PATH = "https://gateway-host:8443/gateway/sandbox/knoxtoken/api/v1";
  private static final String TOKEN_PATH = "/token";
  private static final String JKWS_PATH = "/jwks.json";
  private static final String USER_NAME = "alice";

  private ServletContext context;
  private HttpServletRequest request;
  private JWTokenAuthority authority;
  private TestTokenStateService tss = new TestTokenStateService();
  private char[] hmacSecret;

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
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.enumeration(contextExpectations.keySet())).anyTimes();
    EasyMock.expect(context.getAttribute("org.apache.knox.gateway.gateway.cluster")).andReturn("topology1").anyTimes();
    if (contextExpectations.containsKey(ContextAttributes.IMPERSONATION_ENABLED_ATTRIBUTE)) {
       EasyMock.expect(context.getAttribute(ContextAttributes.IMPERSONATION_ENABLED_ATTRIBUTE)).andReturn(Boolean.parseBoolean(contextExpectations.get(ContextAttributes.IMPERSONATION_ENABLED_ATTRIBUTE))).anyTimes();
     }

    request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();
    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn(USER_NAME).anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal).anyTimes();
    EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(TOKEN_API_PATH+TOKEN_PATH)).anyTimes();
    if (contextExpectations.containsKey(TokenResource.LIFESPAN)) {
      EasyMock.expect(request.getParameter(TokenResource.LIFESPAN)).andReturn(contextExpectations.get(TokenResource.LIFESPAN)).anyTimes();
    }
    if (contextExpectations.containsKey(TokenResource.KNOX_TOKEN_INCLUDE_GROUPS)) {
      EasyMock.expect(request.getParameter(TokenResource.KNOX_TOKEN_INCLUDE_GROUPS)).andReturn(contextExpectations.get(TokenResource.KNOX_TOKEN_INCLUDE_GROUPS)).anyTimes();
    }
    if (contextExpectations.containsKey(TokenResource.QUERY_PARAMETER_DOAS)) {
      EasyMock.expect(request.getParameter(TokenResource.QUERY_PARAMETER_DOAS)).andReturn(contextExpectations.get(TokenResource.QUERY_PARAMETER_DOAS)).anyTimes();
    }
    EasyMock.expect(request.getParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();

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
    final String tokenStateServiceType = ServiceType.TOKEN_STATE_SERVICE.getShortName();
    if (contextExpectations.containsKey(tokenStateServiceType)) {
      EasyMock.expect(config.getServiceParameter(tokenStateServiceType, "impl")).andReturn(contextExpectations.get(tokenStateServiceType)).anyTimes();
    }
    EasyMock.expect(config.getKnoxTokenHashAlgorithm()).andReturn(HmacAlgorithms.HMAC_SHA_256.getName()).anyTimes();
    EasyMock.expect(config.getMaximumNumberOfTokensPerUser())
        .andReturn(contextExpectations.containsKey(KNOX_TOKEN_USER_LIMIT) ? Integer.parseInt(contextExpectations.get(KNOX_TOKEN_USER_LIMIT)) : -1).anyTimes();
    EasyMock.expect(services.getService(ServiceType.TOKEN_STATE_SERVICE)).andReturn(tss).anyTimes();

    AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(services.getService(ServiceType.ALIAS_SERVICE)).andReturn(aliasService).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(TokenUtils.SIGNING_HMAC_SECRET_ALIAS)).andReturn(hmacSecret).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(TokenMAC.KNOX_TOKEN_HASH_KEY_ALIAS_NAME)).andReturn("sPj8FCgQhCEi6G18kBfpswxYSki33plbelGLs0hMSbk".toCharArray()).anyTimes();

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

  @Test(expected = KeyLengthException.class)
  public void testInvalidHmacSecretThrowsException() throws Exception {
    final Map<String, String> contextExpectations = new HashMap<>();
    hmacSecret = "1234".toCharArray();
    contextExpectations.put("knox.token.sigalg", JWSAlgorithm.HS256.getName());
    configureCommonExpectations(contextExpectations);
    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.context = context;
    tr.init();
  }

  @Test
  public void testValidHmacSecretNoException() throws Exception {
    final Map<String, String> contextExpectations = new HashMap<>();
    hmacSecret = "12345678123456781234567812345678".toCharArray();
    contextExpectations.put("knox.token.sigalg", JWSAlgorithm.HS256.getName());
    configureCommonExpectations(contextExpectations);
    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.context = context;
    tr.init();
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
    final Map<String, String> contextExpectations = new HashMap<>();
    contextExpectations.put("org.apache.knox.gateway.gateway.cluster", "test");
    contextExpectations.put(TokenResource.TOKEN_CLIENT_DATA, "sampleClientData=param1=value1&param2=value2");
    configureCommonExpectations(contextExpectations, Boolean.TRUE);

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
    assertEquals(getTagValue(retString, "sampleClientData"), "param1=value1&param2=value2");

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
    Map<String, Object> json = parseJSONResponse(responseContent);
    assertTrue(Boolean.parseBoolean((String)json.get("renewed")));
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
    Map<String, Object> json = parseJSONResponse(responseContent);
    assertTrue(Boolean.parseBoolean((String)json.get("renewed")));
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
    Map<String, Object> json = parseJSONResponse(responseContent);
    assertTrue(Boolean.parseBoolean((String)json.get("renewed")));
    assertNotNull(json.get("expires")); // Should get back the original expiration from the token itself
  }

  @Test
  public void testTokenRenewal_Enabled_NoRenewersNoSubject() throws Exception {
    Response renewalResponse = doTestTokenRenewal(true, null, null);
    validateRenewalResponse(renewalResponse, 403, false, "Caller (null) not authorized to renew tokens.", TokenResource.ErrorCode.UNAUTHORIZED);
  }

  @Test
  public void testTokenRenewal_Enabled_NoRenewersWithSubject() throws Exception {
    final String caller = "yarn";
    Response renewalResponse = doTestTokenRenewal(true, null, createTestSubject(caller));
    validateRenewalResponse(renewalResponse,
                            403,
                            false,
                            "Caller (" + caller + ") not authorized to renew tokens.", TokenResource.ErrorCode.UNAUTHORIZED);
  }

  @Test
  public void testTokenRenewal_Enabled_WithRenewersNoSubject() throws Exception {
    Response renewalResponse = doTestTokenRenewal(true, "larry, moe,  curly ", null);
    validateRenewalResponse(renewalResponse,
                            403,
                            false,
                            "Caller (null) not authorized to renew tokens.", TokenResource.ErrorCode.UNAUTHORIZED);
  }

  @Test
  public void testTokenRenewal_Enabled_WithRenewersWithInvalidSubject() throws Exception {
    final String caller = "shemp";
    Response renewalResponse = doTestTokenRenewal(true, "larry, moe,  curly ", createTestSubject(caller));
    validateRenewalResponse(renewalResponse,
                            403,
                            false,
                            "Caller (" + caller + ") not authorized to renew tokens.", TokenResource.ErrorCode.UNAUTHORIZED);
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
                               "Token revocation support is not configured", TokenResource.ErrorCode.CONFIGURATION_ERROR);
  }

  @Test
  public void testTokenRevocation_Disabled() throws Exception {
    Response renewalResponse = doTestTokenRevocation(false, null, null);
    validateRevocationResponse(renewalResponse,
                               400,
                               false,
                               "Token revocation support is not configured", TokenResource.ErrorCode.CONFIGURATION_ERROR);
  }

  @Test
  public void testTokenRevocation_Enabled_NoRenewersNoSubject() throws Exception {
    Response renewalResponse = doTestTokenRevocation(true, null, null);
    validateRevocationResponse(renewalResponse,
                               403,
                               false,
                               "Caller (null) not authorized to revoke tokens.", TokenResource.ErrorCode.UNAUTHORIZED);
  }

  @Test
  public void testTokenRevocation_Enabled_NoRenewersWithSubject() throws Exception {
    final String caller = "yarn";
    Response renewalResponse = doTestTokenRevocation(true, null, createTestSubject(caller));
    validateRevocationResponse(renewalResponse,
                               403,
                               false,
                               "Caller (" + caller + ") not authorized to revoke tokens.", TokenResource.ErrorCode.UNAUTHORIZED);
  }

  @Test
  public void testTokenRevocation_Enabled_WithRenewersNoSubject() throws Exception {
    Response renewalResponse = doTestTokenRevocation(true, "larry, moe,  curly ", null);
    validateRevocationResponse(renewalResponse,
                               403,
                               false,
                               "Caller (null) not authorized to revoke tokens.", TokenResource.ErrorCode.UNAUTHORIZED);
  }

  @Test
  public void testTokenRevocation_Enabled_WithRenewersWithInvalidSubject() throws Exception {
    final String caller = "shemp";
    Response renewalResponse = doTestTokenRevocation(true, "larry, moe,  curly ", createTestSubject(caller));
    validateRevocationResponse(renewalResponse,
                               403,
                               false,
                               "Caller (" + caller + ") not authorized to revoke tokens.", TokenResource.ErrorCode.UNAUTHORIZED);
  }

  @Test
  public void testTokenRevocation_Enabled_WithRenewersWithValidSubject() throws Exception {
    final String caller = "shemp";
    Response renewalResponse =
        doTestTokenRevocation(true, ("larry, moe,  curly ," + caller), createTestSubject(caller));
    validateSuccessfulRevocationResponse(renewalResponse);
  }

  @Test
  public void testTokenRevocation_Enabled_RevokeOwnToken() throws Exception {
    final Response renewalResponse = doTestTokenRevocation(true, null, createTestSubject(USER_NAME));
    validateSuccessfulRevocationResponse(renewalResponse);
  }

  @Test
  public void testTokenRevocation_Enabled_RevokeImpersonatedToken() throws Exception {
    final Response renewalResponse = doTestTokenRevocation(true, null, createTestSubject(USER_NAME), "impersonatedUserName");
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

  @Test
  public void testGetTokenStateStatusTokenStateServiceNotEnabled() throws Exception {
    testGetTokenStateStatus(Collections.singletonMap(TokenStateService.CONFIG_SERVER_MANAGED, "false"), "false", null, null, null);
  }

  @Test
  public void testGetTokenStateStatusTokenStateServiceConfiguredProperly() throws Exception {
    final Map<String, String> expectations = new HashMap<>();
    expectations.put(TokenStateService.CONFIG_SERVER_MANAGED, "true");
    expectations.put(ServiceType.TOKEN_STATE_SERVICE.getShortName(), TestTokenStateService.class.getCanonicalName());
    testGetTokenStateStatus(expectations, "true", "TestTokenStateService", "TestTokenStateService", "false");
  }

  @Test
  public void testGetTokenStateStatusTokenStateServiceIsMisconfigured() throws Exception {
    final Map<String, String> expectations = new HashMap<>();
    expectations.put(TokenStateService.CONFIG_SERVER_MANAGED, "true");
    expectations.put(ServiceType.TOKEN_STATE_SERVICE.getShortName(), JDBCTokenStateService.class.getCanonicalName());
    testGetTokenStateStatus(expectations, "true", "JDBCTokenStateService", "TestTokenStateService", "false");
  }

  private void testGetTokenStateStatus(Map<String, String> expectations, String expectedTokenManagementFlag, String expectedConfiguredTssBackend, String expectedActualTssBackend,
      String expectedAllowedTssFlag) throws Exception {
    configureCommonExpectations(expectations);

    final TokenResource tr = new TokenResource();
    tr.request = request;
    tr.context = context;
    tr.init();

    final Response response = tr.getTokenStateServiceStatus();
    assertEquals(200, response.getStatus());
    final String statusJson = response.getEntity().toString();
    final Map<String, String> statusMap = JsonUtils.getMapFromJsonString(statusJson);
    if (expectedTokenManagementFlag != null) {
      assertEquals(statusMap.get("tokenManagementEnabled"), expectedTokenManagementFlag);
    }
    if (expectedConfiguredTssBackend != null) {
      assertEquals(statusMap.get("configuredTssBackend"), expectedConfiguredTssBackend);
    }
    if (expectedActualTssBackend != null) {
      assertEquals(statusMap.get("actualTssBackend"), expectedActualTssBackend);
    }
    if (expectedAllowedTssFlag != null) {
      assertEquals(statusMap.get("allowedTssForTokengen"), expectedAllowedTssFlag);
    }
    assertTrue(Boolean.parseBoolean(statusMap.get("lifespanInputEnabled")));
  }

  @Test
  public void testGettingTokenWithLifespanLessThanConfiguredTTL() throws Exception {
    final Map<String, String> contextExpectations = new HashMap<>();
    contextExpectations.put("knox.token.ttl", "172800000"); // 2 days
    contextExpectations.put(TokenResource.LIFESPAN, "P1DT0H0M"); // 1 day 0 hour 0 minute
    configureCommonExpectations(contextExpectations);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.context = context;
    tr.init();

    // Issue a token
    final Response retResponse = tr.doGet();
    final String retString = retResponse.getEntity().toString();
    String accessToken = getTagValue(retString, "access_token");
    assertNotNull(accessToken);

    // Verify the token
    final JWT parsedToken = new JWTToken(accessToken);
    assertTrue(authority.verifyToken(parsedToken));

    final Date expiresDate = parsedToken.getExpiresDate();

    final Calendar nowPlus23Hours = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault());
    nowPlus23Hours.add(Calendar.HOUR_OF_DAY, 23);

    final Calendar tomorrowPlus23Hours = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault());
    tomorrowPlus23Hours.add(Calendar.HOUR_OF_DAY, 47);

    final long oneHourInMills = 3600000L;
    assertTrue(expiresDate.after(nowPlus23Hours.getTime()));
    assertTrue(expiresDate.before(tomorrowPlus23Hours.getTime())); // make sure the supplied lifespan was used and not the configured TTL
    assertTrue((expiresDate.getTime() - nowPlus23Hours.getTime().getTime()) < oneHourInMills);
  }

  @Test
  public void testGettingTokenWithLifespanGreaterThanConfiguredTTL() throws Exception {
    testGettingTokenWithConfiguredTTL("P1D");
  }

  @Test
  public void testGettingTokenWithConfiguredTTLIfLifespanIsInvalid() throws Exception {
    testGettingTokenWithConfiguredTTL("InvalidLifespanPattern");
  }

  private void testGettingTokenWithConfiguredTTL(String lifespan) throws Exception {
    final Map<String, String> contextExpectations = new HashMap<>();
    final long oneMinute = 60000L;
    contextExpectations.put("knox.token.ttl", String.valueOf(oneMinute));
    contextExpectations.put(TokenResource.LIFESPAN, lifespan); // 1 day
    configureCommonExpectations(contextExpectations);

    final TokenResource tr = new TokenResource();
    tr.request = request;
    tr.context = context;
    tr.init();

    // Issue a token
    final Response retResponse = tr.doGet();
    final String retString = retResponse.getEntity().toString();
    String accessToken = getTagValue(retString, "access_token");
    assertNotNull(accessToken);

    // Verify the token
    final JWT parsedToken = new JWTToken(accessToken);
    assertTrue(authority.verifyToken(parsedToken));

    final Date expiresDate = parsedToken.getExpiresDate();
    final Date now = new Date();

    assertTrue(expiresDate.after(now));
    assertTrue((expiresDate.getTime() - now.getTime()) < oneMinute); // the configured TTL was used even if lifespan was supplied
  }

  @Test
  public void testConfiguredTokenLimitPerUser() throws Exception {
    testLimitingTokensPerUser(KNOX_TOKEN_USER_LIMIT_DEFAULT, KNOX_TOKEN_USER_LIMIT_DEFAULT);
  }

  @Test
  public void testUnlimitedTokensPerUser() throws Exception {
    testLimitingTokensPerUser(-1, 100);
  }

  @Test
  public void testTokenLimitChangeAfterAlreadyHavingTokens() throws Exception {
    Map<String, String> contextExpectations = new HashMap<>();
    contextExpectations.put(KNOX_TOKEN_USER_LIMIT, "-1");
    configureCommonExpectations(contextExpectations, Boolean.TRUE);
    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.context = context;
    tr.init();
    // already have N tokens
    int numberOfPreExistingTokens = 5;
    for (int i = 0; i < numberOfPreExistingTokens; i++) {
      tr.doGet();
    }
    Response getKnoxTokensResponse = getUserTokensResponse(tr);
    Collection<String> tokens = ((Map<String, Collection<String>>) JsonUtils.getObjectFromJsonString(getKnoxTokensResponse.getEntity().toString()))
            .get("tokens");
    assertEquals(tokens.size(), numberOfPreExistingTokens);
    // change the limit and try generate one more
    contextExpectations.put(KNOX_TOKEN_USER_LIMIT, Integer.toString(numberOfPreExistingTokens -1));
    configureCommonExpectations(contextExpectations, Boolean.TRUE);
    tr.request = request;
    tr.context = context;
    tr.init();
    Response response = tr.doGet();
    assertTrue(response.getEntity().toString().contains("Unable to get token - token limit exceeded."));
  }

  private Response getUserTokensResponse(TokenResource tokenResource) {
    return getUserTokensResponse(tokenResource, false);
  }

  private Response getUserTokensResponse(TokenResource tokenResource, boolean createdBy) {
    final MultivaluedMap<String, String> queryParameters = new MultivaluedHashMap<>();
    queryParameters.put(createdBy ? "createdBy" : "userName", Arrays.asList(USER_NAME));
    final UriInfo uriInfo = EasyMock.createNiceMock(UriInfo.class);
    EasyMock.expect(uriInfo.getQueryParameters()).andReturn(queryParameters).anyTimes();
    EasyMock.replay(uriInfo);
    return tokenResource.getUserTokens(uriInfo);
  }

  @Test
  public void testTokenLimitPerUserExceeded() throws Exception {
    try {
      testLimitingTokensPerUser(10, 11);
      fail("Exception should have been thrown");
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("Unable to get token - token limit exceeded."));
    }
  }

  @Test
  public void testTokenLimitPerUserExceededShouldRevokeOldestToken() throws Exception {
    try {
      testLimitingTokensPerUser(10, 11, true);
    } catch (Exception e) {
      fail("Exception should NOT have been thrown");
    }
  }

  private void testLimitingTokensPerUser(int configuredLimit, int numberOfTokens) throws Exception {
    testLimitingTokensPerUser(configuredLimit, numberOfTokens, false);
  }

  private void testLimitingTokensPerUser(int configuredLimit, int numberOfTokens, boolean revokeOldestToken) throws Exception {
    final Map<String, String> contextExpectations = new HashMap<>();
    contextExpectations.put(KNOX_TOKEN_USER_LIMIT, String.valueOf(configuredLimit));
    if (revokeOldestToken) {
      contextExpectations.put(KNOX_TOKEN_USER_LIMIT_EXCEEDED_ACTION, TokenResource.UserLimitExceededAction.REMOVE_OLDEST.name());
    }
    configureCommonExpectations(contextExpectations, Boolean.TRUE);

    final TokenResource tr = new TokenResource();
    tr.request = request;
    tr.context = context;
    tr.init();

    // add some KnoxSSO Cookie, they should not be considered during token limit
    // calculation
    final int numberOfKnoxSsoCookies = 5;
    for (int i = 0; i < numberOfKnoxSsoCookies; i++) {
      final Response tokenResponse = acquireToken(tr);

      final String tokenId = getTagValue(tokenResponse.getEntity().toString(), "token_id");
      assertNotNull(tokenId);
      final TokenMetadata tokenMetadata = new TokenMetadata(USER_NAME);
      tokenMetadata.setKnoxSsoCookie(true);
      tss.addMetadata(tokenId, tokenMetadata);
    }

    for (int i = 0; i < numberOfTokens; i++) {
      acquireToken(tr);
    }
    final Response getKnoxTokensResponse = getUserTokensResponse(tr);
    final Collection<String> tokens = ((Map<String, Collection<String>>) JsonUtils.getObjectFromJsonString(getKnoxTokensResponse.getEntity().toString()))
        .get("tokens");
    assertEquals(tokens.size(), revokeOldestToken ? configuredLimit + numberOfKnoxSsoCookies : numberOfTokens + numberOfKnoxSsoCookies);
  }

  private Response acquireToken(TokenResource tokenResource) throws Exception {
    final Response getTokenResponse = Subject.doAs(createTestSubject(USER_NAME), (PrivilegedAction<Response>) () -> tokenResource.doGet());
    if (getTokenResponse.getStatus() != Response.Status.OK.getStatusCode()) {
      throw new Exception(getTokenResponse.getEntity().toString());
    }
    return getTokenResponse;
  }

  @Test
  public void testCreateImpersonatedToken() throws Exception {
    testCreateImpersonatedToken(true);
  }

  @Test
  public void testImpersonationDisabled() throws Exception {
    testCreateImpersonatedToken(false);
  }

  private void testCreateImpersonatedToken(boolean enableImpersonation) throws Exception {
    final String impersonatedUser = "testUser";
    final Map<String, String> contextExpectations = new HashMap<>();
    contextExpectations.put(TokenResource.QUERY_PARAMETER_DOAS, impersonatedUser);
    contextExpectations.put(AuthFilterUtils.PROXYUSER_PREFIX + "." + USER_NAME + ".users", impersonatedUser);
    contextExpectations.put(AuthFilterUtils.PROXYUSER_PREFIX + "." + USER_NAME + ".hosts", "*");
    contextExpectations.put(ContextAttributes.IMPERSONATION_ENABLED_ATTRIBUTE, Boolean.toString(enableImpersonation));
    configureCommonExpectations(contextExpectations, Boolean.TRUE);

    final TokenResource tr = new TokenResource();
    tr.request = request;
    tr.context = context;
    tr.init();

    final Subject subject = createTestSubject(USER_NAME);
    if (enableImpersonation) {
      subject.getPrincipals().add(new ImpersonatedPrincipal(impersonatedUser));
    }
    Subject.doAs(subject,  (PrivilegedAction<Response>) () -> tr.doGet());

    final Response getKnoxTokensResponse = getUserTokensResponse(tr, enableImpersonation);
    final Collection<LinkedHashMap<String, Object>> tokens = ((Map<String, Collection<LinkedHashMap<String, Object>>>) JsonUtils
        .getObjectFromJsonString(getKnoxTokensResponse.getEntity().toString())).get("tokens");
    final LinkedHashMap<String, Object> knoxToken = tokens.iterator().next();
    final Map<String, String> metadata = (Map<String, String>) knoxToken.get("metadata");
    if (enableImpersonation) {
      assertEquals(metadata.get("createdBy"), USER_NAME);
      assertEquals(metadata.get("userName"), impersonatedUser);
    } else {
      assertNull(metadata.get("createdBy"));
      assertEquals(USER_NAME, metadata.get("userName"));
    }
  }

  @Test
  public void testDefaultIssuer() throws Exception {
    Map<String, String> contextExpectations = new HashMap<>();
    configureCommonExpectations(contextExpectations, Boolean.TRUE);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.context = context;
    tr.init();

    Response response = tr.doGet();
    assertEquals(200, response.getStatus());

    String accessToken = getTagValue(response.getEntity().toString(), "access_token");
    Map<String, Object> payload = parseJSONResponse(JWTToken.parseToken(accessToken).getPayload());
    assertEquals(DEFAULT_ISSUER, payload.get("iss"));
  }

  @Test
  public void testConfiguredIssuer() throws Exception {
    Map<String, String> contextExpectations = new HashMap<>();
    contextExpectations.put(KNOX_TOKEN_ISSUER, "test issuer");
    configureCommonExpectations(contextExpectations, Boolean.TRUE);

    TokenResource tr = new TokenResource();
    tr.request = request;
    tr.context = context;
    tr.init();

    Response response = tr.doGet();
    assertEquals(200, response.getStatus());

    String accessToken = getTagValue(response.getEntity().toString(), "access_token");
    Map<String, Object> payload = parseJSONResponse(JWTToken.parseToken(accessToken).getPayload());
    assertEquals("test issuer", payload.get("iss"));
  }

  @Test
  public void testGroupsAddedToToken() throws Exception {
    Set<String> groups = new HashSet<>(Arrays.asList("group1", "group2"));
    Map<String, String> contextExpectations = new HashMap<>();
    contextExpectations.put(TOKEN_INCLUDE_GROUPS_IN_JWT_ALLOWED, "true");
    contextExpectations.put(TokenResource.KNOX_TOKEN_INCLUDE_GROUPS, "true");
    configureCommonExpectations(contextExpectations, Boolean.TRUE);

    TokenResource tr = new TokenResource() {
      @Override
      protected Set<String> groups() {
        return groups;
      }
    };
    tr.request = request;
    tr.context = context;
    tr.init();

    Response response = tr.doGet();
    assertEquals(200, response.getStatus());

    String accessToken = getTagValue(response.getEntity().toString(), "access_token");
    Map<String, Object> payload = parseJSONResponse(JWTToken.parseToken(accessToken).getPayload());
    assertEquals(new ArrayList<>(groups), payload.get(KNOX_GROUPS_CLAIM));
  }

  @Test
  public void testNoGroupsAddedToTokenByDefault() throws Exception {
    Map<String, String> contextExpectations = new HashMap<>();
    contextExpectations.put(TOKEN_INCLUDE_GROUPS_IN_JWT_ALLOWED, "true");
    configureCommonExpectations(contextExpectations, Boolean.TRUE);

    TokenResource tr = new TokenResource() {
      @Override
      protected Set<String> groups() {
        return new HashSet<>(Arrays.asList("group1", "group2"));
      }
    };
    tr.request = request;
    tr.context = context;
    tr.init();

    Response response = tr.doGet();
    assertEquals(200, response.getStatus());

    String accessToken = getTagValue(response.getEntity().toString(), "access_token");
    Map<String, Object> payload = parseJSONResponse(JWTToken.parseToken(accessToken).getPayload());
    assertFalse(payload.containsKey(KNOX_GROUPS_CLAIM));
  }

  @Test
  public void testBadRequestWhenGroupsAreRequestedToBeIncludedInTokenButItIsDisabledByServer() throws Exception {
    Set<String> groups = new HashSet<>(Arrays.asList("group1", "group2"));
    Map<String, String> contextExpectations = new HashMap<>();
    contextExpectations.put(TOKEN_INCLUDE_GROUPS_IN_JWT_ALLOWED, "false");
    contextExpectations.put(TokenResource.KNOX_TOKEN_INCLUDE_GROUPS, "true");
    configureCommonExpectations(contextExpectations, Boolean.TRUE);

    TokenResource tr = new TokenResource() {
      @Override
      protected Set<String> groups() {
        return groups;
      }
    };
    tr.request = request;
    tr.context = context;
    tr.init();

    Response response = tr.doGet();
    assertEquals(400, response.getStatus());
  }

  @Test
  public void passcodeShouldNotBeInResponseIfTokenStateServiceIsDisabled() throws Exception {
    testPasscodeToken(false, false, false);
  }

  @Test
  public void passcodeShouldNotBeInResponseIfTokenStateServiceIsNotPersistent() throws Exception {
    testPasscodeToken(true, false, false);
  }

  @Test
  public void passcodeShouldBeInResponseIfTokenStateServiceIsEnabledAndPersistent() throws Exception {
    testPasscodeToken(true, true, true);
  }

  private void testPasscodeToken(boolean serverManagedTssEnabled, boolean usePersistentTokenStore, boolean expectPasscodeInResponse) throws Exception {
    try {
      if (usePersistentTokenStore) {
        tss = new PersistentTestTokenStateService();
      }
      configureCommonExpectations(new HashMap<>(), serverManagedTssEnabled);

      final TokenResource tr = new TokenResource();
      tr.context = context;
      tr.request = request;
      tr.init();

      // Issue a token
      final Response response = tr.doGet();
      assertEquals(200, response.getStatus());
      final String retString = response.getEntity().toString();
      final String passcode = getTagValue(retString, TokenResource.PASSCODE);
      if (expectPasscodeInResponse) {
        assertNotNull(passcode);
      } else {
        assertNull(passcode);
      }
    } finally {
      tss = new TestTokenStateService();
    }
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

  private Response doTestTokenRevocation(final Boolean isTokenStateServerManaged, final String renewers, final Subject caller, String impersonatedUser)
      throws Exception {
    return doTestTokenLifecyle(TokenLifecycleOperation.Revoke, isTokenStateServerManaged, null, renewers, null, caller, impersonatedUser).getValue();
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

  private Map.Entry<TestTokenStateService, Response> doTestTokenLifecyle(final TokenLifecycleOperation operation, final Boolean serviceLevelConfig,
      final Boolean gatewayLevelConfig, final String renewers, final Long maxTokenLifetime, final Subject caller) throws Exception {
    return doTestTokenLifecyle(operation, serviceLevelConfig, gatewayLevelConfig, renewers, maxTokenLifetime, caller, null);
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
                                                                         final Subject                 caller,
                                                                         final String impersonatedUser) throws Exception {

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

    if (StringUtils.isNotBlank(impersonatedUser)) {
      contextExpectations.put(ContextAttributes.IMPERSONATION_ENABLED_ATTRIBUTE, "true");
      contextExpectations.put(TokenResource.QUERY_PARAMETER_DOAS, impersonatedUser);
      contextExpectations.put(AuthFilterUtils.PROXYUSER_PREFIX + "." + USER_NAME + ".users", impersonatedUser);
      contextExpectations.put(AuthFilterUtils.PROXYUSER_PREFIX + "." + USER_NAME + ".hosts", "*");
    }

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
    validateRenewalResponse(response, 200, true, null, null);
  }

  private static void validateRenewalResponse(final Response response,
                                              final int      expectedStatusCode,
                                              final boolean  expectedResult,
                                              final String   expectedMessage,
                                              final TokenResource.ErrorCode expectedCode) throws IOException {
    assertEquals(expectedStatusCode, response.getStatus());
    assertTrue(response.hasEntity());
    String responseContent = (String) response.getEntity();
    assertNotNull(responseContent);
    assertFalse(responseContent.isEmpty());
    Map<String, Object> json = parseJSONResponse(responseContent);
    boolean result = Boolean.valueOf((String)json.get("renewed"));
    assertEquals(expectedResult, result);
    assertEquals(expectedMessage, json.get("error"));
    if (expectedCode != null) {
      assertEquals(expectedCode.toInt(), json.get("code"));
    }
  }

  private static void validateSuccessfulRevocationResponse(final Response response) throws IOException {
    validateRevocationResponse(response, 200, true, null, null);
  }

  private static void validateRevocationResponse(final Response response,
                                                 final int      expectedStatusCode,
                                                 final boolean  expectedResult,
                                                 final String   expectedMessage,
                                                 final TokenResource.ErrorCode expectedCode) throws IOException {
    assertEquals(expectedStatusCode, response.getStatus());
    assertTrue(response.hasEntity());
    String responseContent = (String) response.getEntity();
    assertNotNull(responseContent);
    assertFalse(responseContent.isEmpty());
    Map<String, Object> json = parseJSONResponse(responseContent);
    boolean result = Boolean.valueOf((String)json.get("revoked"));
    assertEquals(expectedResult, result);
    assertEquals(expectedMessage, json.get("error"));
    if (expectedCode != null) {
      assertEquals(expectedCode.toInt(), json.get("code"));
    }
  }


  private String getTagValue(String token, String tagName) {
    if (!token.contains(tagName)) {
      return null;
    }
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

  private static Map<String, Object> parseJSONResponse(final String response) throws IOException {
    return (new ObjectMapper()).readValue(response, new TypeReference<Map<String, Object>>(){});
  }


  private static class TestTokenStateService implements TokenStateService {

    private Map<String, Long> expirationData = new HashMap<>();
    private Map<String, Long> issueTimes = new HashMap<>();
    private Map<String, Long> maxLifetimes = new HashMap<>();
    private final Map<String, TokenMetadata> tokenMetadata = new ConcurrentHashMap<>();

    long getIssueTime(final String token) {
      return issueTimes.get(token);
    }

    long getMaxLifetime(final String token) {
      return maxLifetimes.get(token);
    }

    @Override
    public long getTokenIssueTime(String tokenId) throws UnknownTokenException {
      return issueTimes.getOrDefault(tokenId, 0L);
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
      issueTimes.remove(tokenId);
      expirationData.remove(tokenId);
      maxLifetimes.remove(tokenId);
      tokenMetadata.remove(tokenId);
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
      return expirationData.getOrDefault(tokenId, 0L);
    }

    @Override
    public long getTokenExpiration(String tokenId, boolean validate) throws UnknownTokenException {
      return 0;
    }

    @Override
    public void addMetadata(String tokenId, TokenMetadata metadata) {
      tokenMetadata.put(tokenId, metadata);
    }

    @Override
    public TokenMetadata getTokenMetadata(String tokenId) throws UnknownTokenException {
      return tokenMetadata.get(tokenId);
    }

    @Override
    public Collection<KnoxToken> getAllTokens() {
      return fetchTokens(null, false);
    }

    @Override
    public Collection<KnoxToken> getTokens(String userName) {
      return fetchTokens(userName, false);
    }

    @Override
    public Collection<KnoxToken> getDoAsTokens(String createdBy) {
      return fetchTokens(createdBy, true);
    }

    private Collection<KnoxToken> fetchTokens(String userName, boolean createdBy) {
      final Collection<KnoxToken> tokens = new TreeSet<>();
      final Predicate<Map.Entry<String, TokenMetadata>> filterPredicate;
      if (userName == null) {
        filterPredicate = entry -> true;
      } else {
        if (createdBy) {
          filterPredicate = entry -> userName.equals(entry.getValue().getCreatedBy());
        } else {
          filterPredicate = entry -> userName.equals(entry.getValue().getUserName());
        }
      }
      tokenMetadata.entrySet().stream().filter(filterPredicate).forEach(metadata -> {
        String tokenId = metadata.getKey();
        try {
          tokens.add(new KnoxToken(tokenId, getTokenIssueTime(tokenId), getTokenExpiration(tokenId), getMaxLifetime(tokenId), metadata.getValue()));
        } catch (UnknownTokenException e) {
          // NOP
        }
      });
      return tokens;
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

  private static class PersistentTestTokenStateService extends TestTokenStateService implements PersistentTokenStateService {
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
      claimArray[1] = jwtAttributes.getUserName();
      claimArray[2] = null;
      if (jwtAttributes.getExpires() == -1) {
        claimArray[3] = null;
      } else {
        claimArray[3] = String.valueOf(jwtAttributes.getExpires());
      }
      claimArray[4] = "E0LDZulQ0XE_otJ5aoQtQu-RnXv8hU-M9U4dD7vDioA";
      claimArray[5] = jwtAttributes.getJku();

      jwtAttributes.setKid("E0LDZulQ0XE_otJ5aoQtQu-RnXv8hU-M9U4dD7vDioA");
      JWT token = new JWTToken(jwtAttributes);
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
    public boolean verifyToken(JWT token, String jwksurl, String algorithm, Set<JOSEObjectType> allowedJwsTypes) {
     return false;
    }
  }
}
