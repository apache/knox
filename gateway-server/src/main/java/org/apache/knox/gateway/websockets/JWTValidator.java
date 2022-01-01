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
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.provider.federation.jwt.JWTMessages;
import org.apache.knox.gateway.provider.federation.jwt.filter.SignatureVerificationCache;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.util.Tokens;

import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Date;

public class JWTValidator {
    private static final String JWT_DEFAULT_ISSUER = "KNOXSSO";
    private static final String JWT_DEFAULT_SIGALG = "RS256";
    private static final JWTMessages jwtMessagesLog = MessagesFactory.get(JWTMessages.class);
    // required parameters
    private final JWTokenAuthority authorityService;
    private final SignatureVerificationCache signatureVerificationCache;
    private final JWT token;
    // optional parameters
    private String expectedIssuer = JWT_DEFAULT_ISSUER;
    private String expectedSigAlg = JWT_DEFAULT_SIGALG;
    private RSAPublicKey publicKey;
    private TokenStateService tokenStateService;

    private final String displayableTokenId;
    private final String displayableToken;

    public JWTValidator(JWT token, JWTokenAuthority authorityService, SignatureVerificationCache signatureVerificationCache) {
        // required parameters
        this.authorityService = authorityService;
        this.signatureVerificationCache = signatureVerificationCache;
        this.token = token;
        this.displayableTokenId = Tokens.getTokenIDDisplayText(TokenUtils.getTokenId(token));
        this.displayableToken = Tokens.getTokenDisplayText(token.toString());
    }

    // set optional parameters
    public void setPublicKey(RSAPublicKey val){
        publicKey = val;
    }

    public void setExpectedSigAlg(String val){
        expectedSigAlg = val;
    }
    public void setExpectedIssuer(String val){
        expectedIssuer = val;
    }
    public void setTokenStateService(TokenStateService val){
        tokenStateService = val;
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
                        jwtMessagesLog.notBeforeCheckFailed();
                    }
                }
            } catch (UnknownTokenException e){
                return false;
            }
        }
        jwtMessagesLog.unexpectedTokenIssuer(displayableToken, displayableTokenId);
        return false;
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
            jwtMessagesLog.tokenHasExpired(displayableToken, displayableTokenId);
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
            jwtMessagesLog.disabledToken(displayableTokenId);
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
                jwtMessagesLog.unableToVerifyToken(e);
            }
            // Check received signature algorithm if expectation is configured
            if (verified && expectedSigAlg != null) {
                try {
                    final String receivedSigAlg = JWSHeader.parse(token.getHeader()).getAlgorithm().getName();
                    if (!receivedSigAlg.equals(expectedSigAlg)) {
                        verified = false;
                    }
                } catch (ParseException e) {
                    jwtMessagesLog.unableToVerifyToken(e);
                    verified = false;
                }
            }
            if (verified) { // If successful, record the verification for future reference
                signatureVerificationCache.recordSignatureVerification(serializedJWT);
            }
        }
        if (!verified){
            jwtMessagesLog.failedToVerifyTokenSignature(displayableToken, displayableTokenId);
        }
        return verified;
    }
}
