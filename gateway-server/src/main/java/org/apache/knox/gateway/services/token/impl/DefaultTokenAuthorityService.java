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

import java.net.MalformedURLException;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;
import org.apache.knox.gateway.GatewayResources;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.i18n.resources.ResourcesFactory;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.security.token.JWTokenAttributes;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;

public class DefaultTokenAuthorityService implements JWTokenAuthority, Service {
  private static final GatewayResources RESOURCES = ResourcesFactory.get(GatewayResources.class);
  private static final TokenAuthorityServiceMessages LOG = MessagesFactory.get(TokenAuthorityServiceMessages.class);

  // Only standard RSA and HMAC signature algorithms are accepted
  // https://tools.ietf.org/html/rfc7518
  private static final Set<String> SUPPORTED_PKI_SIG_ALGS = new HashSet<>(Arrays.asList("RS256", "RS384", "RS512", "PS256", "PS384", "PS512"));
  private static final Set<String> SUPPORTED_HMAC_SIG_ALGS = new HashSet<>(Arrays.asList("HS256", "HS384", "HS512"));
  private AliasService aliasService;
  private KeystoreService keystoreService;
  private GatewayConfig config;

  private char[] cachedSigningKeyPassphrase;
  private byte[] cachedSigningHmacSecret;
  private RSAPrivateKey signingKey;

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
        key = keystoreService.getSigningKeystore().getCertificate(getSigningKeyAlias()).getPublicKey();
      }
      final JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) key);
      // TODO: interrogate the token for issuer claim in order to determine the public key to use for verification
      // consider jwk for specifying the key too
      return token.verify(verifier);
    } catch (KeyStoreException | KeystoreServiceException e) {
      throw new TokenServiceException("Cannot verify token.", e);
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
  public boolean verifyToken(JWT token, String jwksurl, String algorithm, Set<JOSEObjectType> allowedJwsTypes) throws TokenServiceException {
    boolean verified = false;
    try {
      if (algorithm != null && jwksurl != null) {
        JWSAlgorithm expectedJWSAlg = JWSAlgorithm.parse(algorithm);
        JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(new URL(jwksurl));
        JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(expectedJWSAlg, keySource);

        // Create a JWT processor for the access tokens
        ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
        jwtProcessor.setJWSKeySelector(keySelector);
        JWTClaimsSetVerifier<SecurityContext> claimsVerifier = new DefaultJWTClaimsVerifier<>();
        jwtProcessor.setJWTClaimsSetVerifier(claimsVerifier);
        final JOSEObjectTypeVerifier<SecurityContext> objectTypeVerifier = new DefaultJOSEObjectTypeVerifier<>(allowedJwsTypes);
        jwtProcessor.setJWSTypeVerifier(objectTypeVerifier);

        // Process the token
        SecurityContext ctx = null; // optional context parameter, not required here
        jwtProcessor.process(token.toString(), ctx);
        verified = true;
      }
    } catch (BadJOSEException | JOSEException | ParseException | MalformedURLException e) {
      throw new TokenServiceException("Cannot verify token.", e);
    }
    return verified;
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options)
      throws ServiceLifecycleException {
    if (aliasService == null || keystoreService == null) {
      throw new ServiceLifecycleException("Alias or Keystore service is not set");
    }
    this.config = config;
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
  }

  protected Optional<String> getCachedSigningKeyID() {
    return cachedSigningKeyID;
  }
}
