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

import com.nimbusds.jose.JWSHeader;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.provider.federation.jwt.JWTMessages;
import org.apache.knox.gateway.provider.federation.jwt.filter.SignatureVerificationCache;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.util.CertificateUtils;
import org.apache.knox.gateway.util.Tokens;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;

import javax.servlet.ServletException;
import java.net.HttpCookie;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JWTValidator {
    private static final String KNOXSSO_COOKIE_NAME = "knoxsso.cookie.name";
    private static final String DEFAULT_SSO_COOKIE_NAME = "hadoop-jwt";
    private static final String JWT_DEFAULT_ISSUER = "KNOXSSO";
    private static final String JWT_EXPECTED_ISSUER = "jwt.expected.issuer";
    private static final String JWT_DEFAULT_SIGALG = "RS256";
    private static final String JWT_EXPECTED_SIGALG = "jwt.expected.sigalg";
    public static final String SSO_VERIFICATION_PEM = "sso.token.verification.pem";
    private static final JWTMessages log = MessagesFactory.get(JWTMessages.class);
    private String cookieName;
    private String expectedIssuer;
    private String expectedSigAlg;
    private RSAPublicKey publicKey;
    private String providerParamValue;

    private TokenStateService tokenStateService;
    private JWTokenAuthority authorityService;
    private SignatureVerificationCache signatureVerificationCache;
    private JWT token;
    private String displayableTokenId;
    private String displayableToken;
    private final GatewayConfig gatewayConfig;
    private final GatewayServices gatewayServices;
    private static final WebsocketLogMessages websocketLog = MessagesFactory
            .get(WebsocketLogMessages.class);

    JWTValidator(ServletUpgradeRequest req, GatewayServices gatewayServices,
                 GatewayConfig gatewayConfig){
        this.gatewayConfig = gatewayConfig;
        this.gatewayServices = gatewayServices;
        configureParameters();
        extractToken(req);
    }

    private Map<String,String> getParams(){
        Map<String,String> params = new LinkedHashMap<>();
        TopologyService ts = gatewayServices.getService(ServiceType.TOPOLOGY_SERVICE);
        for (Topology topology : ts.getTopologies()) {
            if (topology.getName().equals("knoxsso")) {
                for (Service service : topology.getServices()) {
                    if (service.getRole().equals("KNOXSSO")) {
                        params = service.getParams();
                    }
                    break;
                }
                break;
            }
        }
        return params;
    }
    private void configureParameters() {
        Map<String,String> params = getParams();
        // token verification pem
        String verificationPEM = params.get(SSO_VERIFICATION_PEM);
        // setup the public key of the token issuer for verification
        if (verificationPEM != null) {
            try {
                publicKey = CertificateUtils.parseRSAPublicKey(verificationPEM);
            } catch (ServletException e){
                throw new RuntimeException("Failed to obtain public key: "+e.toString());
            }
        }

        //provider-level configuration
        providerParamValue = params.get(TokenStateService.CONFIG_SERVER_MANAGED);

        cookieName = params.get(KNOXSSO_COOKIE_NAME);
        if (cookieName == null) {
            cookieName = DEFAULT_SSO_COOKIE_NAME;
        }
        expectedIssuer =  params.get(JWT_EXPECTED_ISSUER);
        if (expectedIssuer == null) {
            expectedIssuer = JWT_DEFAULT_ISSUER;
        }
        expectedSigAlg =  params.get(JWT_EXPECTED_SIGALG);
        if (expectedSigAlg == null) {
            expectedSigAlg = JWT_DEFAULT_SIGALG;
        }
        authorityService = gatewayServices.getService(ServiceType.TOKEN_SERVICE);
        if (isServerManagedTokenStateEnabled()) {
            tokenStateService = gatewayServices.getService(ServiceType.TOKEN_STATE_SERVICE);
        }
        // Setup the verified tokens cache
        signatureVerificationCache = SignatureVerificationCache.getInstance(
                "knoxsso", new WebSocketFilterConfig(params));
    }

    private void extractToken(ServletUpgradeRequest req){
        List<HttpCookie> ssoCookies = req.getCookies();
        if (ssoCookies != null){
            for (HttpCookie ssoCookie : ssoCookies) {
                if (cookieName.equals(ssoCookie.getName())) {
                    try {
                        token = new JWTToken(ssoCookie.getValue());
                        displayableTokenId = Tokens.getTokenIDDisplayText(TokenUtils.getTokenId(token));
                        displayableToken = Tokens.getTokenDisplayText(token.toString());
                        websocketLog.debugLog("found token:"+displayableToken+" id:"+displayableTokenId);
                        return;
                    } catch (ParseException e) {
                        // Fall through to keep checking if there are more cookies
                    }
                }
            }
        }
        log.missingBearerToken();
        throw new RuntimeException("no Valid JWT found");
    }

    public JWT getToken(){
        return token;
    }

    public String getUsername(){
        return token.getSubject();
    }

    public boolean validate() {
        // confirm that issuer matches the intended target
        if (expectedIssuer.equals(token.getIssuer())) {
            // if there is no expiration data then the lifecycle is tied entirely to
            // the cookie validity - otherwise ensure that the current time is before
            // the designated expiration time
            try {
                if (tokenIsStillValid()) {
                    Date nbf = token.getNotBeforeDate();
                    if (nbf == null || new Date().after(nbf)) {
                        if (isTokenEnabled()){
                            if (verifyTokenSignature()) {
                                return true;
                            }
                        }
                    } else {
                        log.notBeforeCheckFailed();
                    }
                }
            } catch (UnknownTokenException e){
                return false;
            }
        }
        log.unexpectedTokenIssuer(displayableToken, displayableTokenId);
        return false;
    }

    // adapted from TokenUtils.isServerManagedTokenStateEnabled(FilterConfig)
    private boolean isServerManagedTokenStateEnabled() {
        boolean isServerManaged = false;
        // If there is no provider-level configuration
        if (providerParamValue == null || providerParamValue.isEmpty()) {
            // Fall back to the gateway-level default
            isServerManaged = (gatewayConfig != null) && gatewayConfig.isServerManagedTokenStateEnabled();
        } else {
            // Otherwise, apply the provider-level configuration
            isServerManaged = Boolean.parseBoolean(providerParamValue);
        }
        return isServerManaged;
    }

    public boolean tokenIsStillValid() throws UnknownTokenException {
        Date expires = getServerManagedStateExpiration();
        if (expires == null) {
            // if there is no expiration date then the lifecycle is tied entirely to
            // the cookie validity - otherwise ensure that the current time is before
            // the designated expiration time
            expires = token.getExpiresDate();
        }
        if (expires == null || new Date().before(expires)){
            return true;
        } else {
            log.tokenHasExpired(displayableToken, displayableTokenId);
            // Explicitly evict the record of this token's signature verification (if present).
            // There is no value in keeping this record for expired tokens, and explicitly
            // removing them may prevent records for other valid tokens from being prematurely
            // evicted from the cache.
            signatureVerificationCache.removeSignatureVerificationRecord(token.toString());
            return false;
        }
    }

    private Date getServerManagedStateExpiration() throws UnknownTokenException {
        Date expires = null;
        if (tokenStateService != null) {
            long value = tokenStateService.getTokenExpiration(TokenUtils.getTokenId(token));
            if (value > 0) {
                expires = new Date(value);
            }
        }
        return expires;
    }

    private boolean isTokenEnabled() throws UnknownTokenException {
        final TokenMetadata tokenMetadata = tokenStateService == null ? null :
                tokenStateService.getTokenMetadata(TokenUtils.getTokenId(token));
        if (tokenMetadata == null || tokenMetadata.isEnabled()){
            return true;
        } else {
            log.disabledToken(displayableTokenId);
            return false;
        }
    }

    private boolean verifyTokenSignature() {
        boolean verified;
        final String serializedJWT = token.toString();
        // Check if the token has already been verified
        verified = signatureVerificationCache.hasSignatureBeenVerified(serializedJWT);
        // If it has not yet been verified, then perform the verification now
        if (!verified) {
            try {
                if (publicKey != null) {
                    verified = authorityService.verifyToken(token, publicKey);
                } else {
                    verified = authorityService.verifyToken(token);
                }
            } catch (TokenServiceException e) {
                log.unableToVerifyToken(e);
            }
            // Check received signature algorithm if expectation is configured
            if (verified && expectedSigAlg != null) {
                try {
                    final String receivedSigAlg = JWSHeader.parse(token.getHeader()).getAlgorithm().getName();
                    if (!receivedSigAlg.equals(expectedSigAlg)) {
                        verified = false;
                    }
                } catch (ParseException e) {
                    log.unableToVerifyToken(e);
                    verified = false;
                }
            }
            if (verified) { // If successful, record the verification for future reference
                signatureVerificationCache.recordSignatureVerification(serializedJWT);
            }
        }
        if (!verified){
            log.failedToVerifyTokenSignature(displayableToken, displayableTokenId);
        }
        return verified;
    }
}
