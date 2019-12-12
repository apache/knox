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
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.security.auth.Subject;

import org.apache.knox.gateway.GatewayResources;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.resources.ResourcesFactory;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;

public class DefaultTokenAuthorityService implements JWTokenAuthority, Service {
  private static final GatewayResources RESOURCES = ResourcesFactory.get(GatewayResources.class);

  private static final Set<String> SUPPORTED_SIG_ALGS = new HashSet<>();
  private AliasService as;
  private KeystoreService ks;
  private GatewayConfig config;

  static {
      // Only standard RSA signature algorithms are accepted
      // https://tools.ietf.org/html/rfc7518
      SUPPORTED_SIG_ALGS.add("RS256");
      SUPPORTED_SIG_ALGS.add("RS384");
      SUPPORTED_SIG_ALGS.add("RS512");
      SUPPORTED_SIG_ALGS.add("PS256");
      SUPPORTED_SIG_ALGS.add("PS384");
      SUPPORTED_SIG_ALGS.add("PS512");
  }

  public void setKeystoreService(KeystoreService ks) {
    this.ks = ks;
  }

  public void setAliasService(AliasService as) {
    this.as = as;
  }

  @Override
  public JWT issueToken(Subject subject, String algorithm) throws TokenServiceException {
    Principal p = (Principal) subject.getPrincipals().toArray()[0];
    return issueToken(p, algorithm);
  }

  @Override
  public JWT issueToken(Principal p, String algorithm) throws TokenServiceException {
    return issueToken(p, null, algorithm);
  }

  @Override
  public JWT issueToken(Principal p, String algorithm, long expires) throws TokenServiceException {
    return issueToken(p, (String)null, algorithm, expires);
  }

  @Override
  public JWT issueToken(Principal p, String audience, String algorithm)
      throws TokenServiceException {
    return issueToken(p, audience, algorithm, -1);
  }

  @Override
  public JWT issueToken(Principal p, String audience, String algorithm, long expires)
      throws TokenServiceException {
    List<String> audiences = null;
    if (audience != null) {
      audiences = new ArrayList<>();
      audiences.add(audience);
    }
    return issueToken(p, audiences, algorithm, expires);
  }

  @Override
  public JWT issueToken(Principal p, List<String> audiences, String algorithm, long expires)
      throws TokenServiceException {
    return issueToken(p, audiences, algorithm, expires, null, null, null);
  }

  @Override
  public JWT issueToken(Principal p, List<String> audiences, String algorithm, long expires,
                        String signingKeystoreName, String signingKeystoreAlias, char[] signingKeystorePassphrase)
      throws TokenServiceException {
    String[] claimArray = new String[4];
    claimArray[0] = "KNOXSSO";
    claimArray[1] = p.getName();
    claimArray[2] = null;
    if (expires == -1) {
      claimArray[3] = null;
    }
    else {
      claimArray[3] = String.valueOf(expires);
    }

    JWT token;
    if (SUPPORTED_SIG_ALGS.contains(algorithm)) {
      token = new JWTToken(algorithm, claimArray, audiences);
      char[] passphrase;
      try {
        passphrase = getSigningKeyPassphrase(signingKeystorePassphrase);
      } catch (AliasServiceException e) {
        throw new TokenServiceException(e);
      }
      try {
        RSAPrivateKey key = (RSAPrivateKey) ks.getSigningKey(signingKeystoreName,
            getSigningKeyAlias(signingKeystoreAlias), passphrase);
        // allowWeakKey to not break existing 1024 bit certificates
        JWSSigner signer = new RSASSASigner(key, true);
        token.sign(signer);
      } catch (KeystoreServiceException e) {
        throw new TokenServiceException(e);
      }
    }
    else {
      throw new TokenServiceException("Cannot issue token - Unsupported algorithm");
    }

    return token;
  }

  private char[] getSigningKeyPassphrase(char[] signingKeyPassphrase) throws AliasServiceException {
    if(signingKeyPassphrase != null) {
      return signingKeyPassphrase;
    }

    return as.getSigningKeyPassphrase();
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
  public boolean verifyToken(JWT token)
      throws TokenServiceException {
    return verifyToken(token, null);
  }

  @Override
  public boolean verifyToken(JWT token, RSAPublicKey publicKey)
      throws TokenServiceException {
    boolean rc;
    PublicKey key;
    try {
      if (publicKey == null) {
        key = ks.getSigningKeystore().getCertificate(getSigningKeyAlias()).getPublicKey();
      }
      else {
        key = publicKey;
      }
      JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) key);
      // TODO: interrogate the token for issuer claim in order to determine the public key to use for verification
      // consider jwk for specifying the key too
      rc = token.verify(verifier);
    } catch (KeyStoreException | KeystoreServiceException e) {
      throw new TokenServiceException("Cannot verify token.", e);
    }
    return rc;
  }

  @Override
  public boolean verifyToken(JWT token, String jwksurl, String algorithm) throws TokenServiceException {
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
    if (as == null || ks == null) {
      throw new ServiceLifecycleException("Alias or Keystore service is not set");
    }
    this.config = config;
  }

  @Override
  public void start() throws ServiceLifecycleException {
    // Ensure that the default signing keystore is available
    KeyStore keystore;
    try {
      keystore = ks.getSigningKeystore();
      if (keystore == null) {
        throw new ServiceLifecycleException(RESOURCES.signingKeystoreNotAvailable(config.getSigningKeystorePath()));
      }
    } catch (KeystoreServiceException e) {
      throw new ServiceLifecycleException(RESOURCES.signingKeystoreNotAvailable(config.getSigningKeystorePath()), e);
    }

    // Ensure that the password for the signing key is available
    char[] passphrase;
    try {
      passphrase = as.getSigningKeyPassphrase();
      if (passphrase == null) {
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
    } catch (KeyStoreException e) {
      throw new ServiceLifecycleException(RESOURCES.publicSigningKeyNotFound(signingKeyAlias), e);
    }

    // Ensure that the private signing keys is available
    try {
      Key key = keystore.getKey(signingKeyAlias, passphrase);
      if (key == null) {
        throw new ServiceLifecycleException(RESOURCES.privateSigningKeyNotFound(signingKeyAlias));
      }
      else if (! (key instanceof  RSAPrivateKey)) {
        throw new ServiceLifecycleException(RESOURCES.privateSigningKeyWrongType(signingKeyAlias));
      }
    } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
      throw new ServiceLifecycleException(RESOURCES.privateSigningKeyNotFound(signingKeyAlias), e);
    }
  }

  @Override
  public void stop() throws ServiceLifecycleException {
  }
}
