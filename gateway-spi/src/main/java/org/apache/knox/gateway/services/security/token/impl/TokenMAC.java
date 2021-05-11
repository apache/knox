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
package org.apache.knox.gateway.services.security.token.impl;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.knox.gateway.services.ServiceLifecycleException;

public class TokenMAC {
  public static final String KNOX_TOKEN_HASH_KEY_ALIAS_NAME = "knox.token.hash.key";

  private final Mac mac;
  private final Lock hashLock = new ReentrantLock(true);

  public TokenMAC(String algorithm, char[] knoxTokenHashKey) throws ServiceLifecycleException {
    try {
      if (knoxTokenHashKey != null) {
        final SecretKey key = new SecretKeySpec(new String(knoxTokenHashKey).getBytes(StandardCharsets.UTF_8), algorithm);
        mac = Mac.getInstance(algorithm);
        mac.init(key);
      } else {
        throw new ServiceLifecycleException("Missing " + KNOX_TOKEN_HASH_KEY_ALIAS_NAME + " alias from Gateway's credential store");
      }
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new ServiceLifecycleException("Error while initiating Knox Token MAC: " + e, e);
    }
  }

  public String hash(String tokenId, long issueTime, String userName, String toBeHashed) {
    hashLock.lock();
    try {
      mac.update(getSalt(tokenId, issueTime, userName));
      return new String(mac.doFinal(toBeHashed.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    } finally {
      hashLock.unlock();
    }
  }

  private byte[] getSalt(String tokenId, long issueTime, String userName) {
    return (tokenId + issueTime + userName).getBytes(StandardCharsets.UTF_8);
  }

}
