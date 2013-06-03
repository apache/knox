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
package org.apache.hadoop.gateway.provider.federation.jwt;

import java.security.Principal;

import javax.security.auth.Subject;

import org.apache.hadoop.gateway.services.security.AliasService;
import org.apache.hadoop.gateway.services.security.CryptoService;

public class JWTAuthority {
  private CryptoService crypto = null;
  
  public JWTAuthority(CryptoService crypto) {
    this.crypto = crypto;
  }
  
  public JWTToken issueToken(Subject subject, String algorithm) {
    Principal p = (Principal) subject.getPrincipals().toArray()[0];
    return issueToken(p, algorithm);
  }
  
  public JWTToken issueToken(Principal p, String algorithm) {
    return issueToken(p, null, algorithm);
  }
  
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
  
  public boolean verifyToken(JWTToken token) {
    boolean rc = false;
    
    // TODO: interrogate the token for issuer claim in order to determine the public key to use for verification
    // consider jwk for specifying the key too
    rc = crypto.verify("SHA256withRSA", "gateway-identity", token.getPayloadToSign(), token.getSignaturePayload());
    return rc;
  }

}
