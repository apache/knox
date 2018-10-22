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

import java.security.KeyStoreException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;

import javax.security.auth.Subject;

import org.apache.knox.gateway.config.GatewayConfig;
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

import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;

public class DefaultTokenAuthorityService implements JWTokenAuthority, Service {

  private static final String SIGNING_KEY_PASSPHRASE = "signing.key.passphrase";
  private static final Set<String> SUPPORTED_SIG_ALGS = new HashSet<>();
  private AliasService as = null;
  private KeystoreService ks = null;
  String signingKeyAlias = null;

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

  /* (non-Javadoc)
   * @see org.apache.knox.gateway.provider.federation.jwt.JWTokenAuthority#issueToken(javax.security.auth.Subject, java.lang.String)
   */
  @Override
  public JWT issueToken(Subject subject, String algorithm) throws TokenServiceException {
    Principal p = (Principal) subject.getPrincipals().toArray()[0];
    return issueToken(p, algorithm);
  }

  /* (non-Javadoc)
   * @see org.apache.knox.gateway.provider.federation.jwt.JWTokenAuthority#issueToken(java.security.Principal, java.lang.String)
   */
  @Override
  public JWT issueToken(Principal p, String algorithm) throws TokenServiceException {
    return issueToken(p, null, algorithm);
  }

  /* (non-Javadoc)
   * @see org.apache.knox.gateway.provider.federation.jwt.JWTokenAuthority#issueToken(java.security.Principal, java.lang.String, long expires)
   */
  @Override
  public JWT issueToken(Principal p, String algorithm, long expires) throws TokenServiceException {
    return issueToken(p, (String)null, algorithm, expires);
  }

  public JWT issueToken(Principal p, String audience, String algorithm)
      throws TokenServiceException {
    return issueToken(p, audience, algorithm, -1);
  }

  /* (non-Javadoc)
   * @see org.apache.knox.gateway.provider.federation.jwt.JWTokenAuthority#issueToken(java.security.Principal, java.lang.String, java.lang.String)
   */
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

    JWT token = null;
    if (SUPPORTED_SIG_ALGS.contains(algorithm)) {
      token = new JWTToken(algorithm, claimArray, audiences);
      RSAPrivateKey key;
      char[] passphrase = null;
      try {
        passphrase = getSigningKeyPassphrase();
      } catch (AliasServiceException e) {
        throw new TokenServiceException(e);
      }
      try {
        key = (RSAPrivateKey) ks.getSigningKey(getSigningKeyAlias(),
            passphrase);
        JWSSigner signer = new RSASSASigner(key);
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

  private char[] getSigningKeyPassphrase() throws AliasServiceException {
    char[] phrase = as.getPasswordFromAliasForGateway(SIGNING_KEY_PASSPHRASE);
    if (phrase == null) {
      phrase = as.getGatewayIdentityPassphrase();
    }
    return phrase;
  }

  private String getSigningKeyAlias() {
    if (signingKeyAlias == null) {
      return "gateway-identity";
    }
    return signingKeyAlias;
  }

  @Override
  public boolean verifyToken(JWT token)
      throws TokenServiceException {
    return verifyToken(token, null);
  }

  @Override
  public boolean verifyToken(JWT token, RSAPublicKey publicKey)
      throws TokenServiceException {
    boolean rc = false;
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
    } catch (KeyStoreException e) {
      throw new TokenServiceException("Cannot verify token.", e);
    } catch (KeystoreServiceException e) {
      throw new TokenServiceException("Cannot verify token.", e);
    }
    return rc;
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options)
      throws ServiceLifecycleException {
    if (as == null || ks == null) {
      throw new ServiceLifecycleException("Alias or Keystore service is not set");
    }
    signingKeyAlias = config.getSigningKeyAlias();

    @SuppressWarnings("unused")
    RSAPrivateKey key;
    char[] passphrase = null;
    try {
      passphrase = as.getPasswordFromAliasForGateway(SIGNING_KEY_PASSPHRASE);
      if (passphrase != null) {
        key = (RSAPrivateKey) ks.getSigningKey(getSigningKeyAlias(),
            passphrase);
        if (key == null) {
          throw new ServiceLifecycleException("Provisioned passphrase cannot be used to acquire signing key.");
        }
      }
    } catch (AliasServiceException e) {
      throw new ServiceLifecycleException("Provisioned signing key passphrase cannot be acquired.", e);
    } catch (KeystoreServiceException e) {
      throw new ServiceLifecycleException("Provisioned signing key passphrase cannot be acquired.", e);
    }
  }

  @Override
  public void start() throws ServiceLifecycleException {
  }

  @Override
  public void stop() throws ServiceLifecycleException {
  }
}
