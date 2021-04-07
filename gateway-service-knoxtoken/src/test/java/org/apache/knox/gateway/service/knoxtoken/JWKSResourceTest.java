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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStoreException;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Collections;

/**
 * Unit tests for JWKS Resource
 */
public class JWKSResourceTest {

  private static RSAPublicKey publicKey;
  private static RSAPrivateKey privateKey;
  private ServletContext context;
  private HttpServletRequest request;
  private GatewayServices services;
  private JWKSResource jwksResource;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair KPair = kpg.generateKeyPair();

    publicKey = (RSAPublicKey) KPair.getPublic();
    privateKey = (RSAPrivateKey) KPair.getPrivate();
  }

  private void init() throws KeystoreServiceException, KeyStoreException {
    final KeystoreService ks = EasyMock.createNiceMock(KeystoreService.class);
    services = EasyMock.createNiceMock(GatewayServices.class);
    context = EasyMock.createNiceMock(ServletContext.class);
    request = EasyMock.createNiceMock(HttpServletRequest.class);

    jwksResource = EasyMock.partialMockBuilder(JWKSResource.class)
        .addMockedMethod("getPublicKey", String.class).createMock();

    EasyMock.expect(services.getService(ServiceType.KEYSTORE_SERVICE))
        .andReturn(ks).anyTimes();
    EasyMock.expect(
        context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE))
        .andReturn(services).anyTimes();
    EasyMock.expect(jwksResource.getPublicKey(null)).andReturn(publicKey)
        .anyTimes();
    EasyMock.replay(jwksResource, context, request, services, ks);
  }

  @Test
  public void testJWKSrequest()
      throws KeystoreServiceException, KeyStoreException {
    init();
    Response retResponse = jwksResource.getJwksResponse();
    Assert.assertEquals(Response.Status.OK.getStatusCode(),
        retResponse.getStatus());
  }

  /**
   * End to End test that verifies the token acquired from JWKS endpoint.
   *
   * @throws KeystoreServiceException
   * @throws KeyStoreException
   * @throws ParseException
   * @throws JOSEException
   */
  @Test
  public void testE2E()
      throws KeystoreServiceException, KeyStoreException, ParseException,
      JOSEException {
    init();
    /* get a signed JWT token */
    final JWT testToken = getTestToken("RS256");
    /* get JWKS keyset */
    final Response retResponse = jwksResource.getJwksResponse();

    /* following lines just verifies the token */
    final JWKSet jwks = JWKSet.parse(retResponse.getEntity().toString());
    Assert.assertTrue("No keys found", jwks.getKeys().size() > 0);
    final JWK jwk = jwks.getKeys().get(0);
    Assert.assertNotNull("No private key found", jwk.toRSAKey().toPublicKey());
    final PublicKey pk = jwk.toRSAKey().toPublicKey();
    final JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) pk);
    Assert.assertTrue("Cannot verify the token, wrong certificate",
        testToken.verify(verifier));
  }

  private JWT getTestToken(final String algorithm) {
    String[] claimArray = new String[4];
    claimArray[0] = "KNOXSSO";
    claimArray[1] = "joe@example.com";
    claimArray[2] = null;
    claimArray[3] = null;

    final JWT token = new JWTToken(algorithm, claimArray,
        Collections.singletonList("aud"), false);
    final JWSSigner signer = new RSASSASigner(privateKey, true);
    token.sign(signer);
    return token;
  }

}