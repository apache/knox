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

package org.apache.knox.gateway.websockets;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.commons.codec.binary.Base64;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.provider.federation.jwt.filter.SignatureVerificationCache;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.util.X509CertificateUtil;
import org.easymock.EasyMock;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.lang.reflect.Field;
import java.net.HttpCookie;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class JWTValidatorTest {
    private static final String dnTemplate = "CN={0},OU=Test,O=Hadoop,L=Test,ST=Test,C=US";
    private static final String PASSCODE_CLAIM = "passcode";
    public static final String SSO_VERIFICATION_PEM = "sso.token.verification.pem";
    private static final String JWT_EXPECTED_SIGALG = "jwt.expected.sigalg";
    private static final String JWT_EXPECTED_ISSUER = "jwt.expected.issuer";
    private static final String JWT_TEST_ISSUER = "jwt.test.issuer";

    private static RSAPublicKey publicKey;
    private static RSAPrivateKey privateKey;
    private static String pem;
    private static GatewayConfig gatewayConfig;
    private static GatewayServices gatewayServices;
    private static JWTokenAuthority authorityService;

    private JWTValidator jwtValidator;

    private static String buildDistinguishedName(String hostname) {
        final String cn = Character.isAlphabetic(hostname.charAt(0)) ? hostname : "localhost";
        String[] paramArray = new String[1];
        paramArray[0] = cn;
        return new MessageFormat(dnTemplate, Locale.ROOT).format(paramArray);
    }

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair KPair = kpg.generateKeyPair();
        String dn = buildDistinguishedName(InetAddress.getLocalHost().getHostName());
        Certificate cert = X509CertificateUtil.generateCertificate(dn, KPair, 365, "SHA1withRSA");
        byte[] data = cert.getEncoded();
        Base64 encoder = new Base64( 76, "\n".getBytes( StandardCharsets.US_ASCII ) );
        pem = new String(encoder.encodeToString( data ).getBytes( StandardCharsets.US_ASCII ), StandardCharsets.US_ASCII).trim();

        publicKey = (RSAPublicKey) KPair.getPublic();
        privateKey = (RSAPrivateKey) KPair.getPrivate();

    }

    @After
    public void tearDown() {
        try {
            Field f = jwtValidator.getClass().getDeclaredField("signatureVerificationCache");
            f.setAccessible(true);
            ((SignatureVerificationCache) f.get(jwtValidator)).clear();
        } catch (Exception e) {
            //
        }
    }

    private void setTokenOnRequest(ServletUpgradeRequest request, SignedJWT jwt){
        HttpCookie cookie1 = new HttpCookie("hadoop-jwt", "garbage");
        HttpCookie cookie2 = new HttpCookie("hadoop-jwt", "ljm" + jwt.serialize());// garbled jwt
        HttpCookie cookie3 = new HttpCookie("hadoop-jwt", jwt.serialize());
        EasyMock.expect(request.getCookies()).andReturn(Arrays.asList(cookie1, cookie2, cookie3)).anyTimes();
    }

    private static SignedJWT getJWT(final String issuer,
                       final String sub,
                       final Date expires,
                       final Date nbf,
                       final RSAPrivateKey privateKey,
                       final String signatureAlgorithm)
            throws Exception {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        builder.issuer(issuer)
                .subject(sub)
                .expirationTime(expires)
                .notBeforeTime(nbf)
                .claim("scope", "openid")
                .claim(PASSCODE_CLAIM, UUID.randomUUID().toString());
        JWTClaimsSet claims = builder.build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.parse(signatureAlgorithm)).build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        JWSSigner signer = new RSASSASigner(privateKey);

        signedJWT.sign(signer);

        return signedJWT;
    }

    private void setUpParams(Map<String,String> params){
        gatewayServices = EasyMock.createNiceMock(GatewayServices.class);
        TopologyService ts = EasyMock.createNiceMock(TopologyService.class);
        // insert params into gatewayServices
        EasyMock.expect(gatewayServices.getService(ServiceType.TOPOLOGY_SERVICE))
                .andReturn(ts).anyTimes();

        Topology topology = EasyMock.createNiceMock(Topology.class);
        EasyMock.expect(ts.getTopologies())
                .andReturn(Arrays.asList(topology)).anyTimes();
        EasyMock.expect(topology.getName())
                .andReturn("knoxsso").anyTimes();

        Service service = EasyMock.createNiceMock(Service.class);
        EasyMock.expect(topology.getServices())
                .andReturn(Arrays.asList(service)).anyTimes();
        EasyMock.expect(service.getRole())
                .andReturn("KNOXSSO").anyTimes();
        EasyMock.expect(service.getParams())
                .andReturn(params).anyTimes();

        authorityService = EasyMock.createNiceMock(JWTokenAuthority.class);
        EasyMock.expect(gatewayServices.getService(ServiceType.TOKEN_SERVICE))
                .andReturn(authorityService).anyTimes();

        gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(gatewayConfig.isServerManagedTokenStateEnabled())
                .andReturn(false).anyTimes();

        EasyMock.replay(gatewayConfig, gatewayServices, ts, topology, service);
    }

    @Test
    public void testValidToken() throws Exception{
        SignedJWT validJWT = getJWT(JWT_TEST_ISSUER,
                "alice",
                new Date(new Date().getTime() + TimeUnit.MINUTES.toMillis(10)),
                new Date(),
                privateKey,
                JWSAlgorithm.RS512.getName());
        Map<String, String> params = new HashMap<>();
        params.put(SSO_VERIFICATION_PEM, pem);
        params.put(JWT_EXPECTED_ISSUER, JWT_TEST_ISSUER);
        params.put(JWT_EXPECTED_SIGALG, validJWT.getHeader().getAlgorithm().getName());
        setUpParams(params);
        ServletUpgradeRequest request = EasyMock.createNiceMock(ServletUpgradeRequest.class);
        setTokenOnRequest(request, validJWT);
        EasyMock.replay(request);
        JWTValidator jwtValidator = JWTValidatorFactory.create(request, gatewayServices, gatewayConfig);
        // test get token
        Assert.assertEquals(validJWT.serialize(), jwtValidator.getToken().toString());
        // test get username
        Assert.assertEquals("alice",jwtValidator.getUsername());
        // test validate token
        EasyMock.expect(authorityService.verifyToken(jwtValidator.getToken(), publicKey)).andReturn(true).anyTimes();
        EasyMock.replay(authorityService);
        Assert.assertTrue(jwtValidator.validate());
    }
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testMissingToken() throws Exception {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("No Valid JWT found");
        setUpParams(new HashMap<>());
        ServletUpgradeRequest request = EasyMock.createNiceMock(ServletUpgradeRequest.class);
        EasyMock.expect(request.getCookies()).andReturn(null).anyTimes();
        EasyMock.replay(request);
        JWTValidator jwtValidator = JWTValidatorFactory.create(request, gatewayServices, gatewayConfig);
        EasyMock.expect(authorityService.verifyToken(jwtValidator.getToken(), publicKey)).andReturn(true).anyTimes();
        EasyMock.replay(authorityService);
    }


    @Test
    public void testUnexpectedTokenIssuer() throws Exception{
        SignedJWT unexpectedIssuerJWT = getJWT("unexpectedIssuer",
                "alice",
                new Date(new Date().getTime() + TimeUnit.MINUTES.toMillis(10)),
                new Date(),
                privateKey,
                JWSAlgorithm.RS512.getName());
        Map<String, String> params = new HashMap<>();
        params.put(SSO_VERIFICATION_PEM, pem);
        params.put(JWT_EXPECTED_ISSUER, JWT_TEST_ISSUER);
        params.put(JWT_EXPECTED_SIGALG, unexpectedIssuerJWT.getHeader().getAlgorithm().getName());
        setUpParams(params);
        ServletUpgradeRequest request = EasyMock.createNiceMock(ServletUpgradeRequest.class);
        setTokenOnRequest(request, unexpectedIssuerJWT);
        EasyMock.replay(request);
        JWTValidator jwtValidator = JWTValidatorFactory.create(request, gatewayServices, gatewayConfig);
        EasyMock.expect(authorityService.verifyToken(jwtValidator.getToken(), publicKey)).andReturn(true).anyTimes();
        EasyMock.replay(authorityService);
        Assert.assertFalse(jwtValidator.validate());
    }

    @Test
    public void testExpiredJWT() throws Exception{
        SignedJWT expiredJWT = getJWT(JWT_TEST_ISSUER,
                "alice",
                new Date(new Date().getTime() - TimeUnit.MINUTES.toMillis(10)),
                new Date(),
                privateKey,
                JWSAlgorithm.RS512.getName());
        Map<String, String> params = new HashMap<>();
        params.put(SSO_VERIFICATION_PEM, pem);
        params.put(JWT_EXPECTED_ISSUER, JWT_TEST_ISSUER);
        params.put(JWT_EXPECTED_SIGALG, expiredJWT.getHeader().getAlgorithm().getName());
        setUpParams(params);
        ServletUpgradeRequest request = EasyMock.createNiceMock(ServletUpgradeRequest.class);
        setTokenOnRequest(request, expiredJWT);
        EasyMock.replay(request);
        JWTValidator jwtValidator = JWTValidatorFactory.create(request, gatewayServices, gatewayConfig);
        EasyMock.expect(authorityService.verifyToken(jwtValidator.getToken(), publicKey)).andReturn(true).anyTimes();
        EasyMock.replay(authorityService);
        Assert.assertFalse(jwtValidator.validate());
    }

    @Test
    public void testInvalidNBFJWT() throws Exception{
        SignedJWT expiredJWT = getJWT(JWT_TEST_ISSUER,
                "alice",
                new Date(new Date().getTime() + TimeUnit.MINUTES.toMillis(10)),
                new Date(new Date().getTime() + TimeUnit.MINUTES.toMillis(5)),
                privateKey,
                JWSAlgorithm.RS512.getName());
        Map<String, String> params = new HashMap<>();
        params.put(SSO_VERIFICATION_PEM, pem);
        params.put(JWT_EXPECTED_ISSUER, JWT_TEST_ISSUER);
        params.put(JWT_EXPECTED_SIGALG, expiredJWT.getHeader().getAlgorithm().getName());
        setUpParams(params);
        ServletUpgradeRequest request = EasyMock.createNiceMock(ServletUpgradeRequest.class);
        setTokenOnRequest(request, expiredJWT);
        EasyMock.replay(request);
        JWTValidator jwtValidator = JWTValidatorFactory.create(request, gatewayServices, gatewayConfig);
        EasyMock.expect(authorityService.verifyToken(jwtValidator.getToken(), publicKey)).andReturn(true).anyTimes();
        EasyMock.replay(authorityService);
        Assert.assertFalse(jwtValidator.validate());
    }

    @Test
    public void testUnexpectedSigAlg() throws Exception{
        SignedJWT unexpectedSigAlgJWT = getJWT(JWT_TEST_ISSUER,
                "alice",
                new Date(new Date().getTime() + TimeUnit.MINUTES.toMillis(10)),
                new Date(),
                privateKey,
                JWSAlgorithm.RS256.getName());
        Map<String, String> params = new HashMap<>();
        params.put(SSO_VERIFICATION_PEM, pem);
        params.put(JWT_EXPECTED_ISSUER, JWT_TEST_ISSUER);
        params.put(JWT_EXPECTED_SIGALG, JWSAlgorithm.RS512.getName() );
        setUpParams(params);
        ServletUpgradeRequest request = EasyMock.createNiceMock(ServletUpgradeRequest.class);
        setTokenOnRequest(request, unexpectedSigAlgJWT);
        EasyMock.replay(request);
        JWTValidator jwtValidator = JWTValidatorFactory.create(request, gatewayServices, gatewayConfig);
        EasyMock.expect(authorityService.verifyToken(jwtValidator.getToken(), publicKey)).andReturn(true).anyTimes();
        EasyMock.replay(authorityService);
        Assert.assertFalse(jwtValidator.validate());
    }

    @Test
    public void testNoPublicKey() throws Exception{
        SignedJWT validJWT = getJWT(JWT_TEST_ISSUER,
                "alice",
                new Date(new Date().getTime() + TimeUnit.MINUTES.toMillis(10)),
                new Date(),
                privateKey,
                JWSAlgorithm.RS512.getName());
        Map<String, String> params = new HashMap<>();
        params.put(JWT_EXPECTED_ISSUER, JWT_TEST_ISSUER);
        params.put(JWT_EXPECTED_SIGALG, validJWT.getHeader().getAlgorithm().getName());
        setUpParams(params);
        ServletUpgradeRequest request = EasyMock.createNiceMock(ServletUpgradeRequest.class);
        setTokenOnRequest(request, validJWT);
        EasyMock.replay(request);
        JWTValidator jwtValidator = JWTValidatorFactory.create(request, gatewayServices, gatewayConfig);
        EasyMock.expect(authorityService.verifyToken(jwtValidator.getToken())).andReturn(true).anyTimes();
        EasyMock.replay(authorityService);
        Assert.assertTrue(jwtValidator.validate());
    }

    @Test
    public void testFailToVerifyToken() throws Exception{
        SignedJWT parsableJWT = getJWT(JWT_TEST_ISSUER,
                "alice",
                new Date(new Date().getTime() + TimeUnit.MINUTES.toMillis(10)),
                new Date(),
                privateKey,
                JWSAlgorithm.RS512.getName());
        Map<String, String> params = new HashMap<>();
        params.put(SSO_VERIFICATION_PEM, pem);
        params.put(JWT_EXPECTED_ISSUER, JWT_TEST_ISSUER);
        params.put(JWT_EXPECTED_SIGALG, parsableJWT.getHeader().getAlgorithm().getName());
        setUpParams(params);
        ServletUpgradeRequest request = EasyMock.createNiceMock(ServletUpgradeRequest.class);
        setTokenOnRequest(request, parsableJWT);
        EasyMock.replay(request);
        JWTValidator jwtValidator = JWTValidatorFactory.create(request, gatewayServices, gatewayConfig);
        EasyMock.expect(authorityService.verifyToken(jwtValidator.getToken(), publicKey)).andReturn(false).anyTimes();
        EasyMock.replay(authorityService);
        Assert.assertFalse(jwtValidator.validate());
    }

}
