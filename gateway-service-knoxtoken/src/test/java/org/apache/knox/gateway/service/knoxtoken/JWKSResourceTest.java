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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreSpi;
import java.security.Provider;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Collections;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.token.JWTokenAttributesBuilder;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;

/**
 * Unit tests for JWKS Resource
 */
public class JWKSResourceTest {

  private static RSAPublicKey publicKey;
  private static RSAPrivateKey privateKey;
  private ServletContext context;
  private HttpServletRequest request;
  private GatewayServices services;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair KPair = kpg.generateKeyPair();

    publicKey = (RSAPublicKey) KPair.getPublic();
    privateKey = (RSAPrivateKey) KPair.getPrivate();
  }

  @Before
  public void init() throws Exception {
    services = EasyMock.createNiceMock(GatewayServices.class);
    context = EasyMock.createNiceMock(ServletContext.class);
    request = EasyMock.createNiceMock(HttpServletRequest.class);

    final KeystoreService ks = EasyMock.createNiceMock(KeystoreService.class);
    final KeyStoreSpi keyStoreSpi = EasyMock.createNiceMock(KeyStoreSpi.class);
    final KeyStore keystore = new KeyStoreMock(keyStoreSpi, null, "test");
    keystore.load(null);
    EasyMock.expect(ks.getSigningKeystore(null)).andReturn(keystore).anyTimes();
    final Certificate cert = EasyMock.createNiceMock(Certificate.class);
    EasyMock.expect(keyStoreSpi.engineGetCertificate(EasyMock.anyString())).andReturn(cert).anyTimes();
    EasyMock.expect(cert.getPublicKey()).andReturn(publicKey).anyTimes();
    EasyMock.expect(services.getService(ServiceType.KEYSTORE_SERVICE)).andReturn(ks).anyTimes();
    EasyMock.expect(services.getService(ServiceType.ALIAS_SERVICE)).andReturn(EasyMock.createNiceMock(AliasService.class)).anyTimes();
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services).anyTimes();
    final GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.getSigningKeyAlias()).andReturn(null).anyTimes();
    EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(config).anyTimes();
    EasyMock.replay(context, request, services, ks, keyStoreSpi, cert, config);
  }

  @Test
  public void testJWKSrequest() throws Exception {
    final JWKSResource jwksResource = new JWKSResource();
    jwksResource.context = context;
    jwksResource.request = request;
    jwksResource.init();
    final Response retResponse = jwksResource.getJwksResponse();
    Assert.assertEquals(Response.Status.OK.getStatusCode(), retResponse.getStatus());
  }

  /**
   * End to End test that verifies the token acquired from JWKS endpoint.
   */
  @Test
  public void testE2E() throws Exception {
    /* get a signed JWT token */
    final JWT testToken = getTestToken("RS256");

    final JWKSResource jwksResource = new JWKSResource();
    jwksResource.context = context;
    jwksResource.request = request;
    jwksResource.init();
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
    String[] claimArray = new String[6];
    claimArray[0] = "KNOXSSO";
    claimArray[1] = "joe@example.com";
    claimArray[2] = null;
    claimArray[3] = null;
    claimArray[4] = "E0LDZulQ0XE_otJ5aoQtQu-RnXv8hU-M9U4dD7vDioA";
    claimArray[5] = null;

    final JWT token = new JWTToken(new JWTokenAttributesBuilder().setAlgorithm(algorithm).setAudiences(Collections.singletonList("aud")).setManaged(false).build());
    final JWSSigner signer = new RSASSASigner(privateKey, true);
    token.sign(signer);
    return token;
  }

  private static final class KeyStoreMock extends KeyStore {

    protected KeyStoreMock(KeyStoreSpi keyStoreSpi, Provider provider, String type) {
      super(keyStoreSpi, provider, type);
    }
  }

}