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
package org.apache.knox.gateway.provider.federation.jwt.filter;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;

import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.UUID;

public class JWTTestUtils {

    public static SignedJWT getJWT(String issuer, String sub, Date expires, RSAPrivateKey privateKey)
            throws Exception {
        return getJWT(issuer, sub, expires, new Date(), privateKey, JWSAlgorithm.RS256.getName());
    }

    public static SignedJWT getJWT(String issuer, String sub, Date expires, Date nbf, RSAPrivateKey privateKey,
                               String signatureAlgorithm)
            throws Exception {
        return getJWT(issuer, sub, "bar", expires, nbf, privateKey, signatureAlgorithm);
    }

    public static SignedJWT getJWT(String issuer, String sub, String aud, Date expires, Date nbf, RSAPrivateKey privateKey,
                               String signatureAlgorithm) throws Exception {
        return getJWT(issuer, sub, aud, expires, nbf, privateKey, signatureAlgorithm, String.valueOf(UUID.randomUUID()));
    }

    public static SignedJWT getJWT(final String issuer,
                                   final String sub,
                                   final String aud,
                                   final Date expires,
                                   final Date nbf,
                                   final RSAPrivateKey privateKey,
                                   final String signatureAlgorithm,
                                   final String knoxId)
            throws Exception {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        builder.issuer(issuer)
                .subject(sub)
                .audience(aud)
                .expirationTime(expires)
                .notBeforeTime(nbf)
                .claim("scope", "openid");
        if (knoxId != null) {
            builder.claim(JWTToken.KNOX_ID_CLAIM, knoxId);
        }
        JWTClaimsSet claims = builder.build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.parse(signatureAlgorithm)).build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        JWSSigner signer = new RSASSASigner(privateKey);

        signedJWT.sign(signer);

        return signedJWT;
    }

}
