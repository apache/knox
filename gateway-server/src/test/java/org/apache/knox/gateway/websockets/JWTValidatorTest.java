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

import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.gateway.deploy.DeploymentFactory;
import org.apache.knox.gateway.provider.federation.jwt.filter.AbstractJWTFilter;
import org.apache.knox.gateway.provider.federation.jwt.filter.SignatureVerificationCache;
import org.apache.knox.gateway.services.DefaultGatewayServices;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.util.X509CertificateUtil;
import org.apache.knox.test.TestUtils;
import org.easymock.EasyMock;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpCookie;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.apache.knox.gateway.config.GatewayConfig.DEFAULT_IDENTITY_KEYSTORE_PASSWORD_ALIAS;
import static org.apache.knox.gateway.config.GatewayConfig.DEFAULT_IDENTITY_KEYSTORE_TYPE;
import static org.apache.knox.gateway.config.GatewayConfig.DEFAULT_IDENTITY_KEY_PASSPHRASE_ALIAS;
import static org.apache.knox.gateway.config.GatewayConfig.DEFAULT_SIGNING_KEYSTORE_PASSWORD_ALIAS;
import static org.apache.knox.gateway.config.GatewayConfig.DEFAULT_SIGNING_KEYSTORE_TYPE;
import static org.apache.knox.gateway.config.GatewayConfig.DEFAULT_SIGNING_KEY_PASSPHRASE_ALIAS;

public class JWTValidatorTest {
    private static final String dnTemplate = "CN={0},OU=Test,O=Hadoop,L=Test,ST=Test,C=US";
    private static final String PASSCODE_CLAIM = "passcode";
    private static final String TEST_KEY_ALIAS = "test-identity";


    private static RSAPublicKey publicKey;
    private static RSAPrivateKey privateKey;
    private static String pem;
    private static GatewayConfig gatewayConfig;
    private static GatewayServices gatewayServices;

    private static File topoDir;
    private static Path dataDir;
    private static Path securityDir;
    private static Path keystoresDir;
    private static Path keystoreFile;

    private JWTValidator jwtValidator;



    private static String buildDistinguishedName(String hostname) {
        final String cn = Character.isAlphabetic(hostname.charAt(0)) ? hostname : "localhost";
        String[] paramArray = new String[1];
        paramArray[0] = cn;
        return new MessageFormat(dnTemplate, Locale.ROOT).format(paramArray);
    }

    private static XMLTag createKnoxTopology(final String backend) {
        return XMLDoc.newDocument(true).addRoot("topology").addTag("service")
                .addTag("role").addText("WEBSOCKET").addTag("url").addText(backend)
                .gotoParent().gotoRoot();
    }

    private static File createDir() throws IOException {
        return TestUtils
                .createTempDir(JWTValidatorTest.class.getSimpleName() + "-");
    }

    /**
     * Initialize the configs and components required for this test.
     * @param backend topology to use
     * @throws IOException exception on setting up the gateway
     */
    public static void setupGatewayConfig(final String backend) throws IOException {
        gatewayServices = new DefaultGatewayServices();

        URL serviceUrl = ClassLoader.getSystemResource("websocket-services");

        final File descriptor = new File(topoDir, "websocket.xml");
        try(OutputStream stream = Files.newOutputStream(descriptor.toPath())) {
            createKnoxTopology(backend).toStream(stream);
        }

        final Map<String, String> options = new HashMap<>();
        options.put("persist-master", "false");
        options.put("master", "password");

        gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(gatewayConfig.getGatewayTopologyDir())
                .andReturn(topoDir.toString()).anyTimes();

        EasyMock.expect(gatewayConfig.getGatewayProvidersConfigDir())
                .andReturn(topoDir.getAbsolutePath() + "/shared-providers").anyTimes();

        EasyMock.expect(gatewayConfig.getGatewayDescriptorsDir())
                .andReturn(topoDir.getAbsolutePath() + "/descriptors").anyTimes();

        EasyMock.expect(gatewayConfig.getGatewayServicesDir())
                .andReturn(serviceUrl.getFile()).anyTimes();

        EasyMock.expect(gatewayConfig.getEphemeralDHKeySize()).andReturn("2048")
                .anyTimes();

        /* Websocket configs */
        EasyMock.expect(gatewayConfig.isWebsocketEnabled()).andReturn(true)
                .anyTimes();

        EasyMock.expect(gatewayConfig.getWebsocketMaxTextMessageSize())
                .andReturn(GatewayConfigImpl.DEFAULT_WEBSOCKET_MAX_TEXT_MESSAGE_SIZE)
                .anyTimes();

        EasyMock.expect(gatewayConfig.getWebsocketMaxBinaryMessageSize())
                .andReturn(GatewayConfigImpl.DEFAULT_WEBSOCKET_MAX_BINARY_MESSAGE_SIZE)
                .anyTimes();

        EasyMock.expect(gatewayConfig.getWebsocketMaxTextMessageBufferSize())
                .andReturn(
                        GatewayConfigImpl.DEFAULT_WEBSOCKET_MAX_TEXT_MESSAGE_BUFFER_SIZE)
                .anyTimes();

        EasyMock.expect(gatewayConfig.getWebsocketMaxBinaryMessageBufferSize())
                .andReturn(
                        GatewayConfigImpl.DEFAULT_WEBSOCKET_MAX_BINARY_MESSAGE_BUFFER_SIZE)
                .anyTimes();

        EasyMock.expect(gatewayConfig.getWebsocketInputBufferSize())
                .andReturn(GatewayConfigImpl.DEFAULT_WEBSOCKET_INPUT_BUFFER_SIZE)
                .anyTimes();

        EasyMock.expect(gatewayConfig.getWebsocketAsyncWriteTimeout())
                .andReturn(GatewayConfigImpl.DEFAULT_WEBSOCKET_ASYNC_WRITE_TIMEOUT)
                .anyTimes();

        EasyMock.expect(gatewayConfig.getWebsocketIdleTimeout())
                .andReturn(GatewayConfigImpl.DEFAULT_WEBSOCKET_IDLE_TIMEOUT).anyTimes();

        EasyMock.expect(gatewayConfig.getWebsocketMaxWaitBufferCount())
                .andReturn(GatewayConfigImpl.DEFAULT_WEBSOCKET_MAX_WAIT_BUFFER_COUNT).anyTimes();

        EasyMock.expect(gatewayConfig.getRemoteRegistryConfigurationNames())
                .andReturn(Collections.emptyList())
                .anyTimes();

        EasyMock.expect(gatewayConfig.getGatewayDataDir())
                .andReturn(dataDir.toString())
                .anyTimes();

        EasyMock.expect(gatewayConfig.getGatewaySecurityDir())
                .andReturn(securityDir.toString())
                .anyTimes();

        EasyMock.expect(gatewayConfig.getGatewayKeystoreDir())
                .andReturn(keystoresDir.toString())
                .anyTimes();

        EasyMock.expect(gatewayConfig.getIdentityKeystorePath())
                .andReturn(keystoreFile.toString())
                .anyTimes();

        EasyMock.expect(gatewayConfig.getIdentityKeystoreType())
                .andReturn(DEFAULT_IDENTITY_KEYSTORE_TYPE)
                .anyTimes();

        EasyMock.expect(gatewayConfig.getIdentityKeystorePasswordAlias())
                .andReturn(DEFAULT_IDENTITY_KEYSTORE_PASSWORD_ALIAS)
                .anyTimes();

        EasyMock.expect(gatewayConfig.getIdentityKeyAlias())
                .andReturn(TEST_KEY_ALIAS)
                .anyTimes();

        EasyMock.expect(gatewayConfig.getIdentityKeyPassphraseAlias())
                .andReturn(DEFAULT_IDENTITY_KEY_PASSPHRASE_ALIAS)
                .anyTimes();

        EasyMock.expect(gatewayConfig.getSigningKeystorePasswordAlias())
                .andReturn(DEFAULT_SIGNING_KEYSTORE_PASSWORD_ALIAS)
                .anyTimes();

        EasyMock.expect(gatewayConfig.getSigningKeyPassphraseAlias())
                .andReturn(DEFAULT_SIGNING_KEY_PASSPHRASE_ALIAS)
                .anyTimes();

        EasyMock.expect(gatewayConfig.getSigningKeystorePath())
                .andReturn(keystoreFile.toString())
                .anyTimes();

        EasyMock.expect(gatewayConfig.getSigningKeystoreType())
                .andReturn(DEFAULT_SIGNING_KEYSTORE_TYPE)
                .anyTimes();

        EasyMock.expect(gatewayConfig.getSigningKeyAlias())
                .andReturn(TEST_KEY_ALIAS)
                .anyTimes();

        EasyMock.expect(gatewayConfig.getServiceParameter(EasyMock.anyString(), EasyMock.anyString())).andReturn("").anyTimes();

        EasyMock.expect(gatewayConfig.getCredentialStoreType()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_TYPE).anyTimes();
        EasyMock.expect(gatewayConfig.getCredentialStoreAlgorithm()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_ALG).anyTimes();

        EasyMock.replay(gatewayConfig);

        try {
            gatewayServices.init(gatewayConfig, options);
        } catch (ServiceLifecycleException e) {
            e.printStackTrace();
        }

        DeploymentFactory.setGatewayServices(gatewayServices);
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

        topoDir = createDir();
        dataDir = Paths.get(topoDir.getAbsolutePath(), "data").toAbsolutePath();
        securityDir = dataDir.resolve("security");
        keystoresDir = securityDir.resolve("keystores");
        keystoreFile = keystoresDir.resolve("tls.jks");
        setupGatewayConfig("JWTValidatorTestPlaceholderURL");
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

    @AfterClass
    public static void tearDownAfterClass() {
        /* Cleanup the created files */
        FileUtils.deleteQuietly(topoDir);
    }

    private void setTokenOnRequest(ServletUpgradeRequest request, SignedJWT jwt){
        HttpCookie cookie1 = new HttpCookie("hadoop-jwt", "garbage");
        HttpCookie cookie2 = new HttpCookie("hadoop-jwt", "ljm" + jwt.serialize());// garbled jwt
        HttpCookie cookie3 = new HttpCookie("hadoop-jwt", jwt.serialize());
        EasyMock.expect(request.getCookies()).andReturn(Arrays.asList(cookie1, cookie2, cookie3)).anyTimes();
    }

    protected SignedJWT getJWT(final String issuer,
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

    @Test
    public void testGetToken() throws Exception{
        SignedJWT jwt = getJWT(AbstractJWTFilter.JWT_DEFAULT_ISSUER,
                "alice",
                new Date(new Date().getTime() + TimeUnit.MINUTES.toMillis(10)),
                new Date(),
                privateKey,
                JWSAlgorithm.RS512.getName());
        ServletUpgradeRequest request = EasyMock.createNiceMock(ServletUpgradeRequest.class);
        setTokenOnRequest(request, jwt);
        EasyMock.replay(request);
        jwtValidator = new JWTValidator(request, gatewayServices, gatewayConfig);
        Assert.assertEquals(jwt.serialize(), jwtValidator.getToken().toString());
    }

    @Test
    public void testGetUsername() throws Exception{
        SignedJWT jwt = getJWT(AbstractJWTFilter.JWT_DEFAULT_ISSUER,
                "alice",
                new Date(new Date().getTime() + TimeUnit.MINUTES.toMillis(10)),
                new Date(),
                privateKey,
                JWSAlgorithm.RS512.getName());
        ServletUpgradeRequest request = EasyMock.createNiceMock(ServletUpgradeRequest.class);
        setTokenOnRequest(request, jwt);
        EasyMock.replay(request);
        jwtValidator = new JWTValidator(request, gatewayServices, gatewayConfig);
        Assert.assertEquals("alice",jwtValidator.getUsername());
    }
    /*
    @Test
    public void testValidToken() throws Exception{

    }
    @Test
    public void testInvalidTokenUnexpectedIssuer() throws Exception{

    }
    @Test
    public void testInvalidTokenExpired() throws Exception{

    }
    @Test
    public void testInvalidTokenDisabled() throws Exception{

    }
    @Test
    public void testTokenIsStillValid() throws Exception{

    }
    @Test
    public void testTokenIsNolongerValid() throws Exception{

    }

    */
}
