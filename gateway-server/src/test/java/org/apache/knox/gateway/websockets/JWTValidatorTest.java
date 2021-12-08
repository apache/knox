package org.apache.knox.gateway.websockets;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.commons.codec.binary.Base64;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.provider.federation.jwt.filter.AbstractJWTFilter;
import org.apache.knox.gateway.provider.federation.jwt.filter.SignatureVerificationCache;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.util.X509CertificateUtil;
import org.easymock.EasyMock;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

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
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class JWTValidatorTest {
    private static final String dnTemplate = "CN={0},OU=Test,O=Hadoop,L=Test,ST=Test,C=US";
    private static final String PASSCODE_CLAIM = "passcode";

    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;
    private String pem;
    private GatewayConfig gatewayConfig;
    private GatewayServices gatewayServices;
    private JWTValidator jwtValidator;
    private String buildDistinguishedName(String hostname) {
        final String cn = Character.isAlphabetic(hostname.charAt(0)) ? hostname : "localhost";
        String[] paramArray = new String[1];
        paramArray[0] = cn;
        return new MessageFormat(dnTemplate, Locale.ROOT).format(paramArray);
    }

    @BeforeClass
    public void setUpBeforeClass() throws Exception {
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
        HttpCookie cookie2 = new HttpCookie("hadoop-jwt", "ljm" + jwt.serialize());   // garbled jwt
        HttpCookie cookie3 = new HttpCookie("hadoop-jwt", jwt.serialize());
        EasyMock.expect(request.getCookies()).andReturn(Arrays.asList(cookie1, cookie2, cookie3));
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

    // test configure parameters and extract token
    @Test
    public void testGetUsername() throws Exception{

        SignedJWT jwt = getJWT(AbstractJWTFilter.JWT_DEFAULT_ISSUER,
                "alice",
                new Date(new Date().getTime() + TimeUnit.MINUTES.toMillis(10)),
                new Date(),
                privateKey,
                JWSAlgorithm.RS512.getName()); // null knox ID so the claim will be omitted from the token
        ServletUpgradeRequest request = EasyMock.createNiceMock(ServletUpgradeRequest.class);
        setTokenOnRequest(request, jwt);
        //todo: create a real instance?
        gatewayServices = EasyMock.createNiceMock(GatewayServices.class);
        gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);

        jwtValidator = new JWTValidator(request, gatewayServices, gatewayConfig);



    }


}
