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

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.util.TruststoreSSLContextUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Builds and caches the per-issuer TLS trust for trusted OIDC issuers registered with a
 * {@code jwksCa}. Each trust holds only the issuer's CA bundle; the gateway-wide truststore is
 * never consulted nor modified.
 * <p>
 * An inline PEM bundle is parsed once per registration. A Kubernetes Secret reference is read
 * lazily, never in the background:
 * <ul>
 *   <li>on first use;</li>
 *   <li>on access once the bundle is older than the configured max age, so that a CA removed from
 *       the Secret stops being trusted; a failed re-read keeps the previous bundle;</li>
 *   <li>after a TLS trust failure ({@link IssuerJwksTrust#reloadAfterTlsFailure()}), so a rotated
 *       CA is picked up at once;</li>
 *   <li>after {@link #invalidate(String)} (register, deregister, {@code refresh-jwks}).</li>
 * </ul>
 * Secret reads are attempted at most once per {@link #MIN_RELOAD_INTERVAL} per issuer, apart from
 * the first read after {@link #invalidate(String)}.
 */
class IssuerTrustProvider implements AutoCloseable {

  private static final TrustedOidcIssuerServiceMessages LOG =
      MessagesFactory.get(TrustedOidcIssuerServiceMessages.class);

  static final Duration MIN_RELOAD_INTERVAL = Duration.ofSeconds(30);

  /** Reads the PEM bundle a Secret reference points at. */
  @FunctionalInterface
  interface SecretReader {
    /**
     * @return the PEM bundle held by the referenced Secret key
     * @throws Exception if the Secret or the key does not exist or cannot be read
     */
    String read(JwksCa.SecretReference reference) throws Exception;
  }

  private final Function<String, TrustedOidcIssuer> issuerLookup;
  private final Duration secretMaxAge;
  private final SecretReader secretReader;
  private final Clock clock;
  private final Map<String, Trust> trusts = new ConcurrentHashMap<>();

  /**
   * @param issuerLookup returns the registered issuer for an issuer URL, or null
   * @param secretMaxAge how long a bundle read from a Secret is used before it is re-read
   * @param secretReader reads Secret-referenced bundles
   * @param clock the time source
   */
  IssuerTrustProvider(Function<String, TrustedOidcIssuer> issuerLookup, Duration secretMaxAge,
      SecretReader secretReader, Clock clock) {
    this.issuerLookup = issuerLookup;
    this.secretMaxAge = secretMaxAge;
    this.secretReader = secretReader;
    this.clock = clock;
  }

  /**
   * @return the trust for the issuer, or empty if the issuer is not registered or has no
   *     {@code jwksCa}
   * @throws IllegalStateException if the issuer has a {@code jwksCa} that cannot be loaded
   */
  Optional<IssuerJwksTrust> get(String issuerUrl) {
    final TrustedOidcIssuer issuer = issuerUrl == null ? null : issuerLookup.apply(issuerUrl);
    if (issuer == null || issuer.getJwksCa() == null) {
      return Optional.empty();
    }
    final Trust trust = trusts.compute(issuerUrl, (url, existing) ->
        existing != null && existing.jwksCa.equals(issuer.getJwksCa()) ? existing : new Trust(url, issuer.getJwksCa()));
    trust.current();
    return Optional.of(trust);
  }

  /**
   * Discards the issuer's trust so the next {@link #get(String)} parses the inline bundle or reads
   * the Secret again.
   */
  void invalidate(String issuerUrl) {
    if (issuerUrl != null) {
      trusts.remove(issuerUrl);
    }
  }

  /**
   * Discards the trust of every issuer, for when the registry is reloaded without a previous
   * snapshot to diff against.
   */
  void invalidateAll() {
    trusts.clear();
  }

  @Override
  public void close() throws Exception {
    trusts.clear();
    if (secretReader instanceof AutoCloseable) {
      ((AutoCloseable) secretReader).close();
    }
  }

  /**
   * The CA material loaded at one point in time. The socket factory is kept per material because
   * SSLContext.getSocketFactory() returns a new instance on each call.
   */
  private record CaMaterial(String fingerprint, SSLContext sslContext, SSLSocketFactory socketFactory, Instant loadedAt) {
    /* Same CA material re-read at the given instant; keeps the socket factory identity. */
    private CaMaterial reloadedAt(Instant instant) {
      return new CaMaterial(fingerprint, sslContext, socketFactory, instant);
    }
  }

  /** The trust of one issuer registration; its material may be re-read from a Secret. */
  private final class Trust implements IssuerJwksTrust {
    private final String issuerUrl;
    private final String jwksCa;
    private final JwksCa.SecretReference secretReference;
    private final String source;

    private final AtomicReference<CaMaterial> material = new AtomicReference<>();
    // guarded by this
    private Instant lastReadAttempt;
    private String lastReadError;

    private Trust(String issuerUrl, String jwksCa) {
      this.issuerUrl = issuerUrl;
      this.jwksCa = jwksCa;
      if (JwksCa.isSecretReference(jwksCa)) {
        JwksCa.SecretReference reference;
        try {
          reference = JwksCa.parseSecretReference(jwksCa);
        } catch (IllegalArgumentException e) {
          // rejected at registration; tolerate a malformed stored value by failing closed on use
          reference = null;
        }
        this.secretReference = reference;
        this.source = "secret " + (reference == null ? "<malformed reference>" : reference.toString());
      } else {
        this.secretReference = null;
        this.source = "inline PEM";
      }
    }

    @Override
    public SSLContext getSslContext() {
      return current().sslContext();
    }

    @Override
    public SSLSocketFactory getSslSocketFactory() {
      return current().socketFactory();
    }

    @Override
    public boolean reloadAfterTlsFailure() {
      if (!isFromSecret()) {
        return false;
      }
      synchronized (this) {
        if (!mayReadNow()) {
          return false;
        }
        final CaMaterial previous = material.get();
        try {
          final CaMaterial fresh = read();
          if (previous != null && previous.fingerprint().equals(fresh.fingerprint())) {
            material.set(previous.reloadedAt(fresh.loadedAt()));
            return false;
          }
          material.set(fresh);
          return true;
        } catch (IllegalStateException e) {
          return false;
        }
      }
    }

    private CaMaterial current() {
      CaMaterial current = material.get();
      if (current != null && !isStale(current)) {
        return current;
      }
      synchronized (this) {
        current = material.get();
        if (current == null) {
          if (!mayReadNow()) {
            throw new IllegalStateException(unavailableMessage(lastReadError));
          }
          current = read();
          material.set(current);
          return current;
        }
        if (isStale(current) && mayReadNow()) {
          try {
            final CaMaterial fresh = read();
            material.set(current.fingerprint().equals(fresh.fingerprint()) ? current.reloadedAt(fresh.loadedAt()) : fresh);
          } catch (IllegalStateException e) {
            LOG.keepingPreviousIssuerCa(issuerUrl, source, lastReadError);
          }
        }
        return material.get();
      }
    }

    private boolean isFromSecret() {
      return secretReference != null;
    }

    private boolean isStale(CaMaterial current) {
      return isFromSecret() && !current.loadedAt().plus(secretMaxAge).isAfter(clock.instant());
    }

    // must hold the monitor
    private boolean mayReadNow() {
      return lastReadAttempt == null || !lastReadAttempt.plus(MIN_RELOAD_INTERVAL).isAfter(clock.instant());
    }

    // must hold the monitor
    private CaMaterial read() {
      final Instant now = clock.instant();
      lastReadAttempt = now;
      try {
        final String pem;
        if (isFromSecret()) {
          pem = secretReader.read(secretReference);
        } else if (JwksCa.isSecretReference(jwksCa)) {
          throw new IllegalArgumentException("malformed secret reference " + jwksCa);
        } else {
          pem = jwksCa;
        }
        final CaMaterial loaded = build(pem, now);
        lastReadError = null;
        final CaMaterial previous = material.get();
        if (previous == null || !previous.fingerprint().equals(loaded.fingerprint())) {
          LOG.loadedIssuerCa(issuerUrl, source, loaded.fingerprint());
        }
        return loaded;
      } catch (Exception e) {
        lastReadError = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
        LOG.errorLoadingIssuerCa(issuerUrl, source, lastReadError, e);
        throw new IllegalStateException(unavailableMessage(lastReadError), e);
      }
    }

    private String unavailableMessage(String cause) {
      return "CA bundle for trusted OIDC issuer " + issuerUrl + " (" + source + ") is unavailable: " + cause;
    }
  }

  private static CaMaterial build(String pem, Instant loadedAt) throws GeneralSecurityException, IOException {
    final List<X509Certificate> certificates = JwksCa.parsePemBundle(pem);
    final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
    trustStore.load(null, null);
    final MessageDigest digest = MessageDigest.getInstance("SHA-256");
    for (int i = 0; i < certificates.size(); i++) {
      trustStore.setCertificateEntry("jwks-ca-" + i, certificates.get(i));
      digest.update(certificates.get(i).getEncoded());
    }
    final SSLContext sslContext = TruststoreSSLContextUtils.getTruststoreSSLContext(trustStore);
    if (sslContext == null) {
      throw new GeneralSecurityException("cannot build an SSL context from the CA bundle");
    }
    return new CaMaterial(HexFormat.of().formatHex(digest.digest()), sslContext, sslContext.getSocketFactory(), loadedAt);
  }

  /** Reads Secret-referenced bundles through the in-cluster Kubernetes API, built on first use. */
  static final class KubernetesSecretReader implements SecretReader, AutoCloseable {
    private final Supplier<KubernetesClient> clientFactory;
    private KubernetesClient client;

    KubernetesSecretReader() {
      this(() -> new KubernetesClientBuilder().build());
    }

    KubernetesSecretReader(Supplier<KubernetesClient> clientFactory) {
      this.clientFactory = clientFactory;
    }

    @Override
    public synchronized String read(JwksCa.SecretReference reference) {
      if (client == null) {
        client = clientFactory.get();
      }
      final Secret secret = client.secrets().inNamespace(reference.namespace()).withName(reference.name()).get();
      if (secret == null) {
        throw new IllegalStateException("Secret " + reference.namespace() + "/" + reference.name() + " not found");
      }
      if (secret.getData() != null && secret.getData().containsKey(reference.key())) {
        return new String(Base64.getDecoder().decode(secret.getData().get(reference.key())), StandardCharsets.UTF_8);
      }
      if (secret.getStringData() != null && secret.getStringData().containsKey(reference.key())) {
        return secret.getStringData().get(reference.key());
      }
      throw new IllegalStateException("Secret " + reference.namespace() + "/" + reference.name()
          + " has no key " + reference.key());
    }

    @Override
    public synchronized void close() {
      if (client != null) {
        client.close();
        client = null;
      }
    }
  }
}
