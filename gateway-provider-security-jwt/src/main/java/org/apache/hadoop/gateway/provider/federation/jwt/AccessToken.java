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

import java.io.UnsupportedEncodingException;

import org.apache.commons.codec.binary.Base64;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.services.security.CryptoService;
import org.apache.hadoop.gateway.services.security.EncryptionResult;

public class AccessToken {
  private static final String ENCRYPT_ACCESS_TOKENS = "encrypt_access_tokens";
  private static final String GATEWAY = "__gateway";
  private static final JWTProviderMessages LOG = MessagesFactory.get( JWTProviderMessages.class );
  
  private CryptoService crypto = null;
  private String tokenStr = null;
  private String principalName;
  private long expires;
  
  public AccessToken(CryptoService crypto, String principalName, long expires) {
    this.crypto = crypto;
    this.principalName = principalName;
    this.expires = expires;
  }
  
  public String toString() {
    if (tokenStr != null) {
      return tokenStr;
    }
    String claims = principalName + "::" + expires;
    EncryptionResult result;
    try {
      result = crypto.encryptForCluster(GATEWAY, ENCRYPT_ACCESS_TOKENS, claims.getBytes("UTF-8"));
      tokenStr = Base64.encodeBase64URLSafeString(result.iv) + "+" + 
          Base64.encodeBase64URLSafeString(result.salt) + "+" + 
          Base64.encodeBase64URLSafeString(result.cipher);
    } catch (UnsupportedEncodingException e) {
      LOG.unsupportedEncoding( e );
    }
    return tokenStr;
  }
  
  public static AccessToken parseToken(CryptoService crypto, String wireToken) {
    AccessToken token = null;
    String[] parts = wireToken.split("\\+");
    byte[] bytes = crypto.decryptForCluster(GATEWAY, ENCRYPT_ACCESS_TOKENS, Base64.decodeBase64(parts[2]), Base64.decodeBase64(parts[0]), Base64.decodeBase64(parts[1]));
    
    try {
      String claims = new String(bytes, "UTF-8");
      String[] claimz = claims.split("\\::");
      token = new AccessToken(crypto, claimz[0], Long.parseLong(claimz[1]));
      token.setTokenStr(wireToken);
    } catch (UnsupportedEncodingException e) {
      LOG.unsupportedEncoding( e );
    }
    return token;
  }
  
  private void setTokenStr(String wireToken) {
    this.tokenStr = wireToken;
  }

  public String getPrincipalName() {
    return principalName;
  }
  
  public long getExpires() {
    return expires;
  }
}
