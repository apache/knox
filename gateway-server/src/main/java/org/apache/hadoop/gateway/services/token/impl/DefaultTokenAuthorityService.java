/**
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
package org.apache.hadoop.gateway.services.token.impl;

import java.security.KeyStoreException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;

import javax.security.auth.Subject;

import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.services.Service;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.hadoop.gateway.services.security.AliasService;
import org.apache.hadoop.gateway.services.security.AliasServiceException;
import org.apache.hadoop.gateway.services.security.KeystoreService;
import org.apache.hadoop.gateway.services.security.KeystoreServiceException;
import org.apache.hadoop.gateway.services.security.token.JWTokenAuthority;
import org.apache.hadoop.gateway.services.security.token.TokenServiceException;
import org.apache.hadoop.gateway.services.security.token.impl.JWTToken;

import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;

public class DefaultTokenAuthorityService implements JWTokenAuthority, Service {
  
  private AliasService as = null;
  private KeystoreService ks = null;

  public void setKeystoreService(KeystoreService ks) {
    this.ks = ks;
  }

  public void setAliasService(AliasService as) {
    this.as = as;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.provider.federation.jwt.JWTokenAuthority#issueToken(javax.security.auth.Subject, java.lang.String)
   */
  @Override
  public JWTToken issueToken(Subject subject, String algorithm) throws TokenServiceException {
    Principal p = (Principal) subject.getPrincipals().toArray()[0];
    return issueToken(p, algorithm);
  }
  
  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.provider.federation.jwt.JWTokenAuthority#issueToken(java.security.Principal, java.lang.String)
   */
  @Override
  public JWTToken issueToken(Principal p, String algorithm) throws TokenServiceException {
    return issueToken(p, null, algorithm);
  }
  
  public JWTToken issueToken(Principal p, String audience, String algorithm)
      throws TokenServiceException {
    return issueToken(p, audience, algorithm, -1);
  }
  
  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.provider.federation.jwt.JWTokenAuthority#issueToken(java.security.Principal, java.lang.String, java.lang.String)
   */
  @Override
  public JWTToken issueToken(Principal p, String audience, String algorithm, long expires)
      throws TokenServiceException {
    String[] claimArray = new String[4];
    claimArray[0] = "HSSO";
    claimArray[1] = p.getName();
    if (audience == null) {
      audience = "HSSO";
    }
    claimArray[2] = audience;
    // TODO: make the validity period configurable
    if (expires == -1) {
      claimArray[3] = Long.toString( ( System.currentTimeMillis() ) + 30000);
    }
    else {
      claimArray[3] = String.valueOf(expires);
    }

    JWTToken token = null;
    if ("RS256".equals(algorithm)) {
      token = new JWTToken("RS256", claimArray);
      RSAPrivateKey key;
      char[] passphrase = null;
      try {
        passphrase = as.getGatewayIdentityPassphrase();
      } catch (AliasServiceException e) {
        throw new TokenServiceException(e);
      }
      try {
        key = (RSAPrivateKey) ks.getKeyForGateway("gateway-identity", 
            passphrase);
        JWSSigner signer = new RSASSASigner(key);
        token.sign(signer);
      } catch (KeystoreServiceException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    else {
      // log inappropriate alg
    }
    
    return token;
  }

  @Override
  public boolean verifyToken(JWTToken token)
      throws TokenServiceException {
    boolean rc = false;
    PublicKey key;
    try {
      key = ks.getKeystoreForGateway().getCertificate("gateway-identity").getPublicKey();
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
  }

  @Override
  public void start() throws ServiceLifecycleException {
  }

  @Override
  public void stop() throws ServiceLifecycleException {
  }

}
