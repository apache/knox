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

import java.security.Principal;
import java.util.Map;

import javax.security.auth.Subject;

import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.services.Service;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.hadoop.gateway.services.security.CryptoService;
import org.apache.hadoop.gateway.services.security.token.JWTokenAuthority;
import org.apache.hadoop.gateway.services.security.token.impl.JWTToken;

public class DefaultTokenAuthorityService implements JWTokenAuthority, Service {
  
  private CryptoService crypto = null;

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.provider.federation.jwt.JWTokenAuthority#issueToken(javax.security.auth.Subject, java.lang.String)
   */
  @Override
  public JWTToken issueToken(Subject subject, String algorithm) {
    Principal p = (Principal) subject.getPrincipals().toArray()[0];
    return issueToken(p, algorithm);
  }
  
  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.provider.federation.jwt.JWTokenAuthority#issueToken(java.security.Principal, java.lang.String)
   */
  @Override
  public JWTToken issueToken(Principal p, String algorithm) {
    return issueToken(p, null, algorithm);
  }
  
  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.provider.federation.jwt.JWTokenAuthority#issueToken(java.security.Principal, java.lang.String, java.lang.String)
   */
  @Override
  public JWTToken issueToken(Principal p, String audience, String algorithm) {
    String[] claimArray = new String[4];
    claimArray[0] = "HSSO";
    claimArray[1] = p.getName();
    if (audience == null) {
      audience = "HSSO";
    }
    claimArray[2] = audience;
    // TODO: make the validity period configurable
    claimArray[3] = Long.toString( ( System.currentTimeMillis()/1000 ) + 300);

    JWTToken token = null;
    if ("RS256".equals(algorithm)) {
      token = new JWTToken("RS256", claimArray);
      signToken(token);
    }
    else {
      // log inappropriate alg
    }
    
    return token;
  }

  private void signToken(JWTToken token) {
    byte[] signature = null;
    signature = crypto.sign("SHA256withRSA","gateway-identity",token.getPayloadToSign());
    token.setSignaturePayload(signature);
  }

  @Override
  public boolean verifyToken(JWTToken token) {
    boolean rc = false;
    
    // TODO: interrogate the token for issuer claim in order to determine the public key to use for verification
    // consider jwk for specifying the key too
    rc = crypto.verify("SHA256withRSA", "gateway-identity", token.getPayloadToSign(), token.getSignaturePayload());
    return rc;
  }

  public void setCryptoService(CryptoService crypto) {
    this.crypto = crypto;
  }
  
  @Override
  public void init(GatewayConfig config, Map<String, String> options)
      throws ServiceLifecycleException {
    if (crypto == null) {
      throw new ServiceLifecycleException("Crypto service is not set");
    }
  }

  @Override
  public void start() throws ServiceLifecycleException {
  }

  @Override
  public void stop() throws ServiceLifecycleException {
  }

}
