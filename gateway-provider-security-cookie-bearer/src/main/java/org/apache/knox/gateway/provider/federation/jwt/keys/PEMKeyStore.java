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
package org.apache.knox.gateway.provider.federation.jwt.keys;

import org.apache.knox.gateway.util.CertificateUtils;

import javax.servlet.ServletException;
import java.security.interfaces.RSAPublicKey;

/**
 * A {@link KeyStore} that hosts one public key extracted from a PEM-encoded string.
 * <p>Expects just a PEM encoded string without a header and footer.
 */
public class PEMKeyStore implements KeyStore {
    private final RSAPublicKey publicKey;

    /**
     * @param keyAsPEM A string containing a PEM encoded RSA public key.
     */
    public PEMKeyStore(String keyAsPEM) throws ServletException {
        publicKey = CertificateUtils.parseRSAPublicKey(keyAsPEM);
    }

    @Override
    public RSAPublicKey getPublic(String keyId) {
        // this key store can only host a single key, so the keyId is ignored
        return publicKey;
    }
}
