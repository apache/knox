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
package org.apache.knox.gateway.services.token.impl;

import java.io.Closeable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jose.util.ResourceRetriever;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.knox.gateway.GatewayResources;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.fips.FipsUtils;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.i18n.resources.ResourcesFactory;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.IssuerJwksTrust;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.util.MetadataHttpClients;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.security.token.JWTokenAttributes;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.util.HttpUtils;
import org.apache.knox.gateway.util.TruststoreSSLContextUtils;

public class DefaultTokenAuthorityService implements JWTokenAuthority, Service {
  private static final GatewayResources RESOURCES = ResourcesFactory.get(GatewayResources.class);
  private static final TokenAuthorityServiceMessages LOG = MessagesFactory.get(TokenAuthorityServiceMessages.class);

  // Only standard RSA and HMAC signature algorithms are accepted
  // https://tools.ietf.org/html/rfc7518
  private static final Set<String> SUPPORTED_PKI_SIG_ALGS = new HashSet<>(Arrays.asList("RS256", "RS384", "RS512", "PS256", "PS384", "PS512"));
  private static final Set<String> SUPPORTED_HMAC_SIG_ALGS = new HashSet<>(Arrays.asList("HS256", "HS384", "HS512"));

  /* How long an issuer CA bundle JWK source is kept without use, e.g. after its issuer was re-registered */
  private static final Duration ISSUER_TRUST_JWK_SOURCE_IDLE_EXPIRY = Duration.ofHours(1);

  private AliasService aliasService;
  private KeystoreService keystoreService;
  private GatewayConfig config;

  /*
   * Built on first use and held for the service's lifetime; closed in stop(). Lazily, so a
   * gateway (or a test) that never fetches a JWKS does not start a connection-eviction thread.
   */
  /* Both guarded by this monitor; only reached on a JWK source cache miss, so plainly locked. */
  private CloseableHttpClient jwksHttpClient;
  private boolean stopped;

  private char[] cachedSigningKeyPassphrase;
  private byte[] cachedSigningHmacSecret;
  private RSAPrivateKey signingKey;

  /* Cache JWKS Key source which has its own cache for the jwk keys */
  /*
   * Concurrent and final: verifyToken runs per inbound request from a servlet filter, so this is
   * mutated by request threads while stop() walks it from the shutdown thread.
   */
  private final Map<String, JWKSource<SecurityContext>> cachedJwkSources = new ConcurrentHashMap<>();

  /* JWKS Key sources fetching over TLS trusting only an issuer CA bundle; closed when evicted */
  private final Cache<IssuerTrustJwksKey, JWKSource<SecurityContext>> cachedIssuerTrustJwkSources = Caffeine.newBuilder()
      .expireAfterAccess(ISSUER_TRUST_JWK_SOURCE_IDLE_EXPIRY)
      .removalListener((IssuerTrustJwksKey key, JWKSource<SecurityContext> source, RemovalCause cause) -> closeQuietly(source))
      .build();

  private Optional<String> cachedSigningKeyID = Optional.empty();

  public void setKeystoreService(KeystoreService ks) {
    this.keystoreService = ks;
  }

  public void setAliasService(AliasService as) {
    this.aliasService = as;
  }

  @Override
  public JWT issueToken(JWTokenAttributes jwtAttributes) throws TokenServiceException {
    final String algorithm = jwtAttributes.getAlgorithm();
    if(SUPPORTED_HMAC_SIG_ALGS.contains(algorithm)) {
      jwtAttributes.setKid(null);
      jwtAttributes.setJku(null);
    } else {
      jwtAttributes.setKid(cachedSigningKeyID.isPresent() ? cachedSigningKeyID.get() : null);
    }
    final JWT token = SUPPORTED_PKI_SIG_ALGS.contains(algorithm) || SUPPORTED_HMAC_SIG_ALGS.contains(algorithm)
        ? new JWTToken(jwtAttributes)
        : null;
    if (token != null) {
      if (SUPPORTED_HMAC_SIG_ALGS.contains(algorithm)) {
        signTokenWithHMAC(token);
      } else {
        signTokenWithRSA(token, jwtAttributes.getSigningKeystoreName(), jwtAttributes.getSigningKeystoreAlias(), jwtAttributes.getSigningKeystorePassphrase());
      }
      return token;
    } else {
      throw new TokenServiceException("Cannot issue token - Unsupported algorithm: " + algorithm);
    }
  }

  private void signTokenWithRSA(final JWT token, String signingKeystoreName, String signingKeystoreAlias, char[] signingKeystorePassphrase) throws TokenServiceException {
    try {
      final RSAPrivateKey key = getSigningKey(signingKeystoreName, signingKeystoreAlias, signingKeystorePassphrase);
      // allowWeakKey to not break existing 1024 bit certificates
      final JWSSigner signer = new RSASSASigner(key, true);
      token.sign(signer);
    } catch (KeystoreServiceException e) {
      throw new TokenServiceException(e);
    }
  }

  private RSAPrivateKey getSigningKey(final String signingKeystoreName, final String signingKeystoreAlias, final char[] signingKeystorePassphrase)
      throws KeystoreServiceException, TokenServiceException {

    if (signingKeystorePassphrase != null) {
      return (RSAPrivateKey) keystoreService.getSigningKey(signingKeystoreName, getSigningKeyAlias(signingKeystoreAlias), getSigningKeyPassphrase(signingKeystorePassphrase));
    }

    return signingKey;
  }

  private void signTokenWithHMAC(final JWT token) throws TokenServiceException {
    try {
      final JWSSigner signer = new MACSigner(getHmacSecret());
      token.sign(signer);
    } catch (KeyLengthException e) {
      throw new TokenServiceException(e);
    }
  }

  private byte[] getHmacSecret() throws TokenServiceException {
    if (cachedSigningHmacSecret == null) {
      try {
        final char[] hmacSecret = aliasService.getPasswordFromAliasForGateway(TokenUtils.SIGNING_HMAC_SECRET_ALIAS);
        this.cachedSigningHmacSecret = hmacSecret == null ? null : new String(hmacSecret).getBytes(StandardCharsets.UTF_8);
      } catch (AliasServiceException e) {
        throw new TokenServiceException(e);
      }
    }
    return cachedSigningHmacSecret;
  }

  private char[] getSigningKeyPassphrase(char[] signingKeyPassphrase) {
    return (signingKeyPassphrase != null) ? signingKeyPassphrase : cachedSigningKeyPassphrase;
  }

  private String getSigningKeyAlias() {
    String alias = config.getSigningKeyAlias();
    return (alias == null) ? GatewayConfig.DEFAULT_SIGNING_KEY_ALIAS : alias;
  }

  private String getSigningKeyAlias(String signingKeystoreAlias) {
    if(signingKeystoreAlias != null) {
     return signingKeystoreAlias;
    }

    // Fallback to defaults
    return getSigningKeyAlias();
  }

  @Override
  public boolean verifyToken(JWT token) throws TokenServiceException {
    return verifyToken(token, null);
  }

  @Override
  public boolean verifyToken(JWT token, RSAPublicKey publicKey) throws TokenServiceException {
    final String signatureAlgorithm = token.getSignatureAlgorithm().getName();
    return SUPPORTED_HMAC_SIG_ALGS.contains(signatureAlgorithm) ? verifyTokenUsingHMAC(token) : verifyTokenUsingRSA(token, publicKey);
  }

  private boolean verifyTokenUsingRSA(JWT token, RSAPublicKey publicKey) throws TokenServiceException {
    try {
      PublicKey key = publicKey;
      if (key == null) {
        key = selectVerificationKey(token);
      }
      final JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) key);
      return token.verify(verifier);
    } catch (KeyStoreException | KeystoreServiceException e) {
      throw new TokenServiceException("Cannot verify token.", e);
    }
  }

  /**
   * Selects the public key to verify a gateway-signed RSA token with. When more than one signing
   * key alias is configured, the token's {@code kid} header is matched against each configured
   * key's SHA-256 thumbprint so a token signed by a rotated-out key still verifies. When there is a
   * single configured key, or the token carries no matching {@code kid}, this falls back to the
   * current signing key — the historical single-key behavior.
   */
  private PublicKey selectVerificationKey(JWT token) throws KeyStoreException, KeystoreServiceException {
    final KeyStore keystore = keystoreService.getSigningKeystore();
    final List<String> aliases = getVerificationKeyAliases();
    // Fast path / backward compatibility: a single configured key means no kid selection is needed.
    if (aliases.size() > 1) {
      final String kid = extractKid(token);
      if (kid != null) {
        for (final String alias : aliases) {
          final Certificate cert = keystore.getCertificate(alias);
          if (cert == null || !(cert.getPublicKey() instanceof RSAPublicKey)) {
            continue;
          }
          try {
            if (kid.equals(TokenUtils.getThumbprint((RSAPublicKey) cert.getPublicKey(), "SHA-256"))) {
              return cert.getPublicKey();
            }
          } catch (JOSEException e) {
            // Cannot compute this key's thumbprint; skip it and try the next alias.
            LOG.errorGettingKid(e.toString());
          }
        }
      }
    }
    // No kid, no match, or a single key: verify with the current signing key.
    return keystore.getCertificate(getSigningKeyAlias()).getPublicKey();
  }

  private List<String> getVerificationKeyAliases() {
    final List<String> aliases = config == null ? null : config.getSigningKeyAliases();
    return (aliases == null || aliases.isEmpty()) ? Collections.singletonList(getSigningKeyAlias()) : aliases;
  }

  private static String extractKid(JWT token) {
    try {
      return JWSHeader.parse(token.getHeader()).getKeyID();
    } catch (ParseException e) {
      return null;
    }
  }

  private boolean verifyTokenUsingHMAC(JWT token) throws TokenServiceException {
    try {
      final JWSVerifier verifier = new MACVerifier(getHmacSecret());
      return token.verify(verifier);
    } catch (JOSEException e) {
      throw new TokenServiceException("Cannot verify token.", e);
    }
  }

  @Override
  public boolean verifyToken(JWT token, String jwksUrl, String algorithm, JOSEObjectTypeVerifier<SecurityContext> typeVerifier) throws TokenServiceException {
    return verifyToken(token, jwksUrl, algorithm, typeVerifier, null);
  }

  @Override
  public boolean verifyToken(JWT token, Set<URI> jwksUrls, String algorithm, JOSEObjectTypeVerifier<SecurityContext> typeVerifier) throws TokenServiceException {
    return verifyToken(token, jwksUrls, algorithm, typeVerifier, null);
  }

  @Override
  public boolean verifyToken(JWT token, Set<URI> jwksUrls, String algorithm, JOSEObjectTypeVerifier<SecurityContext> typeVerifier,
                             IssuerJwksTrust jwksTrust) throws TokenServiceException {
    boolean verified = false;
    for (final URI url : jwksUrls) {
      try {
        verified = verifyToken(token, url.toString(), algorithm, typeVerifier, jwksTrust);
        /* if token is verified no need to check further return result */
        if (verified) {
          return true;
        }
      } catch (TokenServiceException e) {
        LOG.jwksVerificationFailed(url.toString(),
                e.getCause() != null ? e.getCause().toString() : e.toString(), e);
      }
    }
    return verified;
  }

  private boolean verifyToken(JWT token, String jwksUrl, String algorithm, JOSEObjectTypeVerifier<SecurityContext> typeVerifier,
                              IssuerJwksTrust jwksTrust) throws TokenServiceException {
    boolean verified = false;
    try {
      if (algorithm != null && jwksUrl != null) {
        JWSAlgorithm expectedJWSAlg = JWSAlgorithm.parse(algorithm);
        final URL url = new URL(jwksUrl);
        final ResourceRetriever resourceRetriever = jwksTrust == null ? null
                : new IssuerTrustResourceRetriever(jwksTrust,
                JWKSourceBuilder.DEFAULT_HTTP_CONNECT_TIMEOUT, JWKSourceBuilder.DEFAULT_HTTP_READ_TIMEOUT,
                JWKSourceBuilder.DEFAULT_HTTP_SIZE_LIMIT);

        final JWKSource<SecurityContext> jwksSource = jwksTrust == null
            ? cachedJwkSources.computeIfAbsent(jwksUrl, key -> buildJwkSource(url, null))
            : cachedIssuerTrustJwkSources.get(new IssuerTrustJwksKey(jwksUrl, jwksTrust),
                key -> buildJwkSource(url, resourceRetriever));

        JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(expectedJWSAlg, jwksSource);

        // Create a JWT processor for the access tokens
        ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
        jwtProcessor.setJWSKeySelector(keySelector);
        jwtProcessor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(null, null));
        /* See if we have an issuer for which we want to ignore type validation */
        if (config.getIssuersWithIgnoredTypeHeader().contains(token.getIssuer())) {
          LOG.ignoreTypeHeaderVerification();
        } else {
          jwtProcessor.setJWSTypeVerifier(typeVerifier);
        }

        // Process the token
        SecurityContext ctx = null; // optional context parameter, not required here
        jwtProcessor.process(token.toString(), ctx);
        verified = true;
      }
    } catch (BadJOSEException | JOSEException | ParseException | MalformedURLException e) {
      if (isIpLiteralTlsFailure(jwksUrl, e)) {
        /* BC-FIPS refuses HTTPS endpoint identification against a bare IP */
        if (FipsUtils.isFipsEnabledWithBCProvider()) {
          LOG.jwksIpLiteralHostUnderFips(jwksUrl);
        } else {
          LOG.jwksIpLiteralHost(jwksUrl);
        }
      }
      throw new TokenServiceException("Cannot verify token.", e);
    }
    return verified;
  }

  /*
   * Builds a JWK source: retry one time in case of failure and cache JWKS in case there is outage,
   * TTL is OUTAGE_TTL. A null retriever means the default one, using the default truststore.
   */
  private JWKSource<SecurityContext> buildJwkSource(URL jwksUrl, ResourceRetriever retriever) {
    final long outageTTL = config.getJwksOutageCacheTTL();
    final long cacheTTL = config.getJwksCacheTimeToLive();
    final long cacheTimeOut = config.getJwksCacheRefreshTimeout();
    final JWKSourceBuilder<SecurityContext> builder =
        JWKSourceBuilder.create(jwksUrl, jwksResourceRetriever(retriever));
    return builder
        .cache(cacheTTL, cacheTimeOut)
        .refreshAheadCache(true)
        .retrying(true)
        .outageTolerant(outageTTL)
        .build();
  }

  /*
   * JWK sources built for an issuer CA bundle, per JWKS URL and issuer trust. A trust is replaced
   * when its issuer is re-registered or refreshed, so the key compares it by identity.
   */
  private record IssuerTrustJwksKey(String jwksUrl, IssuerJwksTrust jwksTrust) {
    @Override
    public boolean equals(Object o) {
      return o instanceof IssuerTrustJwksKey other && jwksUrl.equals(other.jwksUrl) && jwksTrust == other.jwksTrust;
    }

    @Override
    public int hashCode() {
      return 31 * jwksUrl.hashCode() + System.identityHashCode(jwksTrust);
    }
  }

  /**
   * Whether a JWKS failure is a TLS/trust failure against an IP literal host, the one case where
   * {@code certificate_unknown(46)} says nothing at all about the contents of the truststore.
   *
   * @param jwksUrl the JWKS endpoint that was being fetched
   * @param failure the failure to inspect
   * @return {@code true} when the host is an IP literal and the chain carries a TLS/trust failure
   */
  static boolean isIpLiteralTlsFailure(final String jwksUrl, final Throwable failure) {
    return HttpUtils.isIpLiteralHost(jwksUrl) && TruststoreSSLContextUtils.isTlsTrustFailure(failure);
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options)
      throws ServiceLifecycleException {
    if (aliasService == null || keystoreService == null) {
      throw new ServiceLifecycleException("Alias or Keystore service is not set");
    }
    this.config = config;
    synchronized (this) {
      /* A reused instance must be able to serve again after a stop. */
      stopped = false;
    }
  }

  @Override
  public void start() throws ServiceLifecycleException {
    // Ensure that the default signing keystore is available
    KeyStore keystore;
    try {
      keystore = keystoreService.getSigningKeystore();
      if (keystore == null) {
        throw new ServiceLifecycleException(RESOURCES.signingKeystoreNotAvailable(config.getSigningKeystorePath()));
      }
    } catch (KeystoreServiceException e) {
      throw new ServiceLifecycleException(RESOURCES.signingKeystoreNotAvailable(config.getSigningKeystorePath()), e);
    }

    // Ensure that the password for the signing key is available
    try {
      cachedSigningKeyPassphrase = aliasService.getSigningKeyPassphrase();
      if (cachedSigningKeyPassphrase == null) {
        throw new ServiceLifecycleException(RESOURCES.signingKeyPassphraseNotAvailable(config.getSigningKeyPassphraseAlias()));
      }
    } catch (AliasServiceException e) {
      throw new ServiceLifecycleException(RESOURCES.signingKeyPassphraseNotAvailable(config.getSigningKeyPassphraseAlias()), e);
    }

    String signingKeyAlias = getSigningKeyAlias();

    // Ensure that the public signing keys is available
    try {
      Certificate certificate = keystore.getCertificate(signingKeyAlias);
      if(certificate == null) {
        throw new ServiceLifecycleException(RESOURCES.publicSigningKeyNotFound(signingKeyAlias));
      }
      PublicKey publicKey = certificate.getPublicKey();
      if (publicKey == null) {
        throw new ServiceLifecycleException(RESOURCES.publicSigningKeyNotFound(signingKeyAlias));
      }
      else if (! (publicKey instanceof  RSAPublicKey)) {
        throw new ServiceLifecycleException(RESOURCES.publicSigningKeyWrongType(signingKeyAlias));
      }
      cachedSigningKeyID = Optional.of(TokenUtils.getThumbprint((RSAPublicKey) publicKey, "SHA-256"));
    } catch (KeyStoreException e) {
      throw new ServiceLifecycleException(RESOURCES.publicSigningKeyNotFound(signingKeyAlias), e);
    } catch (final JOSEException e) {
      /* in case there is an error getting KID log and move one */
      LOG.errorGettingKid(e.toString());
      cachedSigningKeyID = Optional.empty();
    }

    // Ensure that the private signing keys is available
    try {
      Key key = keystore.getKey(signingKeyAlias, cachedSigningKeyPassphrase);
      if (key == null) {
        throw new ServiceLifecycleException(RESOURCES.privateSigningKeyNotFound(signingKeyAlias));
      }
      else if (! (key instanceof RSAPrivateKey)) {
        throw new ServiceLifecycleException(RESOURCES.privateSigningKeyWrongType(signingKeyAlias));
      }
      signingKey = (RSAPrivateKey) key;
    } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
      throw new ServiceLifecycleException(RESOURCES.privateSigningKeyNotFound(signingKeyAlias), e);
    }
  }

  @Override
  public void stop() throws ServiceLifecycleException {
    /*
     * Every source owns refresh ahead executors that only its own close() shuts down, so both
     * caches are closed explicitly. Caffeine dispatches its removal listener asynchronously on the
     * common pool, so invalidateAll() alone may return before or without anything being closed.
     */
    closeAll(cachedIssuerTrustJwkSources.asMap().values());
    cachedIssuerTrustJwkSources.invalidateAll();
    closeAll(cachedJwkSources.values());
    cachedJwkSources.clear();
    /*
     * Inside the same monitor jwksHttpClient() builds under, and latching stopped so a request
     * racing this cannot resurrect the pool after it has been closed.
     */
    final CloseableHttpClient client;
    synchronized (this) {
      stopped = true;
      client = jwksHttpClient;
      jwksHttpClient = null;
    }
    if (client != null) {
      try {
        client.close();
      } catch (IOException e) {
        /* Shutting down; the connection pool is going away regardless. */
      }
    }
  }

  /**
   * The retriever used for JWKS endpoints trusted through the gateway truststore.
   * <p>
   * Apache HttpClient rather than Nimbus's default, which goes through
   * {@link java.net.HttpURLConnection} and leaves the socket without a host - see
   * {@link HttpClientResourceRetriever}. Timeouts and size limit keep the Nimbus defaults the
   * previous retriever used.
   */
  ResourceRetriever jwksResourceRetriever(ResourceRetriever configured) {
    return configured == null
        ? new HttpClientResourceRetriever(this::jwksHttpClient, JWKSourceBuilder.DEFAULT_HTTP_SIZE_LIMIT)
        : configured;
  }

  private synchronized CloseableHttpClient jwksHttpClient() {
    if (stopped) {
      throw new IllegalStateException("The token authority service has been stopped");
    }
    if (jwksHttpClient == null) {
      /*
       * No in-client retries: Nimbus's retrying JWK source and, on the issuer-CA path,
       * IssuerTrustResourceRetriever already retry around this.
       */
      jwksHttpClient = MetadataHttpClients.pooled(JWKSourceBuilder.DEFAULT_HTTP_CONNECT_TIMEOUT,
          JWKSourceBuilder.DEFAULT_HTTP_READ_TIMEOUT, MetadataHttpClients.NO_RETRIES);
    }
    return jwksHttpClient;
  }


  /* Snapshots first: the caches stay live for in-flight requests while this walks them. */
  private static void closeAll(Collection<JWKSource<SecurityContext>> sources) {
    new ArrayList<>(sources).forEach(DefaultTokenAuthorityService::closeQuietly);
  }

  private static void closeQuietly(JWKSource<SecurityContext> source) {
    if (source instanceof Closeable) {
      try {
        ((Closeable) source).close();
      } catch (IOException e) {
        // nothing to recover; the source is no longer used
      }
    }
  }

  protected Optional<String> getCachedSigningKeyID() {
    return cachedSigningKeyID;
  }
}
