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
package org.apache.hadoop.gateway.provider.authn.jwt.filter;

import java.security.Principal;

import javax.security.auth.Subject;

import org.apache.hadoop.gateway.services.security.CryptoService;

public class JWTAuthority {
  private CryptoService crypto = null;
  
  public JWTAuthority(CryptoService crypto) {
    this.crypto = crypto;
  }
  
  public JWTToken issueToken(Subject subject) {
    Principal p = (Principal) subject.getPrincipals().toArray()[0];
    String[] claimArray = new String[4];
    claimArray[0] = "gateway";
    claimArray[1] = p.getName();
    // TODO: what do we need here and how do we determine what it should be?
    claimArray[2] = "https://login.hadoop.example.org";
    // TODO: make the validity period configurable
    claimArray[3] = Long.toString( ( System.currentTimeMillis()/1000 ) + 300);

    JWTToken token = new JWTToken("RS256", claimArray);
    signToken(token);
    
    return token;
  }
  
  private void signToken(JWTToken token) {
    byte[] signature = null;
    signature = crypto.sign("SHA256withRSA","gateway-identity",token.getPayloadToSign());
    token.setSignaturePayload(signature);
  }
  
  public boolean verifyToken(JWTToken token) {
    boolean rc = false;
    rc = crypto.verify("SHA256withRSA", "gateway-identity", token.getPayloadToSign(), token.getSignaturePayload());
    return rc;
  }
}
