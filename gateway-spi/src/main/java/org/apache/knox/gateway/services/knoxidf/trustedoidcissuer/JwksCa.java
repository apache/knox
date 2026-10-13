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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsing and validation helpers for the {@code jwksCa} value of a {@link TrustedOidcIssuer}.
 * <p>
 * A {@code jwksCa} value is either:
 * <ul>
 *   <li>an inline PEM bundle holding one or more {@code -----BEGIN CERTIFICATE-----} blocks, or</li>
 *   <li>a Kubernetes Secret reference of the form {@code secret:[<namespace>/]<name>#<key>}; the
 *       namespace is optional and defaults to {@value #DEFAULT_SECRET_NAMESPACE}, the name and key
 *       are required.</li>
 * </ul>
 */
public final class JwksCa {

  /** Upper bound on the stored value; keeps it within the MySQL/MariaDB {@code TEXT} limit. */
  public static final int MAX_LENGTH = 65_000;

  public static final String SECRET_PREFIX = "secret:";

  /** Namespace a Secret reference resolves in when it does not name one. */
  public static final String DEFAULT_SECRET_NAMESPACE = "knox";

  // namespace and name follow the Kubernetes DNS-1123 label/subdomain rules; the key follows the
  // Secret data key rules ([-._a-zA-Z0-9]+).
  private static final Pattern SECRET_REFERENCE = Pattern.compile(
      "^" + SECRET_PREFIX + "(?:([a-z0-9](?:[-a-z0-9]*[a-z0-9])?)/)?([a-z0-9](?:[-.a-z0-9]*[a-z0-9])?)#([-._A-Za-z0-9]+)$");

  private JwksCa() {
  }

  /**
   * @return {@code true} if the value uses the {@code secret:} prefix; it may still be malformed,
   *     see {@link #parseSecretReference(String)}
   */
  public static boolean isSecretReference(String jwksCa) {
    return jwksCa != null && jwksCa.startsWith(SECRET_PREFIX);
  }

  /**
   * @return the parsed reference; its namespace is {@value #DEFAULT_SECRET_NAMESPACE} when the
   *     value does not name one
   * @throws IllegalArgumentException if the value is not a well-formed
   *     {@code secret:[<namespace>/]<name>#<key>} reference
   */
  public static SecretReference parseSecretReference(String jwksCa) {
    final Matcher matcher = jwksCa == null ? null : SECRET_REFERENCE.matcher(jwksCa);
    if (matcher == null || !matcher.matches()) {
      throw new IllegalArgumentException("jwksCa secret reference must have the form secret:[<namespace>/]<name>#<key>");
    }
    final String namespace = matcher.group(1) == null ? DEFAULT_SECRET_NAMESPACE : matcher.group(1);
    return new SecretReference(namespace, matcher.group(2), matcher.group(3));
  }

  /**
   * Parses a PEM bundle into its X.509 certificates.
   *
   * @throws IllegalArgumentException if the bundle cannot be parsed or holds no certificate
   */
  public static List<X509Certificate> parsePemBundle(String pem) {
    if (pem == null || pem.trim().isEmpty()) {
      throw new IllegalArgumentException("CA bundle is empty");
    }
    final Collection<? extends Certificate> parsed;
    try {
      parsed = CertificateFactory.getInstance("X.509")
          .generateCertificates(new ByteArrayInputStream(pem.trim().getBytes(StandardCharsets.UTF_8)));
    } catch (CertificateException e) {
      throw new IllegalArgumentException("CA bundle is not a valid PEM certificate bundle: " + e.getMessage(), e);
    }
    final List<X509Certificate> certificates = new ArrayList<>();
    for (Certificate certificate : parsed) {
      if (certificate instanceof X509Certificate) {
        certificates.add((X509Certificate) certificate);
      }
    }
    if (certificates.isEmpty()) {
      throw new IllegalArgumentException("CA bundle contains no X.509 certificate");
    }
    return certificates;
  }

  /** A parsed {@code secret:[<namespace>/]<name>#<key>} reference. */
  public record SecretReference(String namespace, String name, String key) {
    @Override
    public String toString() {
      return namespace + "/" + name + "#" + key;
    }
  }
}
