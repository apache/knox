/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.services.knoxidf.trustedoidcissuer;

import org.apache.knox.gateway.util.X509CertificateUtil;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Base64;

/** Self-signed CA certificates and PEM bundles for jwksCa tests. */
public final class TestCaBundles {

  private TestCaBundles() {
  }

  public static KeyPair generateKeyPair() throws GeneralSecurityException {
    final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  /**
   * @param dn the subject DN; a {@code CN=localhost} certificate also carries a localhost SAN, so it
   *     can serve as the TLS certificate of a local test server
   */
  public static X509Certificate selfSigned(String dn, KeyPair keyPair) {
    return X509CertificateUtil.generateCertificate(dn, keyPair, 1, "SHA256withRSA");
  }

  public static X509Certificate selfSigned(String dn) throws GeneralSecurityException {
    return selfSigned(dn, generateKeyPair());
  }

  public static String toPem(X509Certificate... certificates) throws GeneralSecurityException {
    final Base64.Encoder encoder = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII));
    final StringBuilder pem = new StringBuilder();
    for (X509Certificate certificate : certificates) {
      pem.append("-----BEGIN CERTIFICATE-----\n")
          .append(encoder.encodeToString(certificate.getEncoded()))
          .append("\n-----END CERTIFICATE-----\n");
    }
    return pem.toString();
  }
}
