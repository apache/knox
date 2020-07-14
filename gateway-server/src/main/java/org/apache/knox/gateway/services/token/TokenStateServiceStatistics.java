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
package org.apache.knox.gateway.services.token;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javax.management.StandardMBean;

import org.apache.knox.gateway.services.security.token.TokenStateServiceStatisticsMBean;

public class TokenStateServiceStatistics extends StandardMBean implements TokenStateServiceStatisticsMBean {

  public enum KeystoreInteraction {
    SAVE_ALIAS("saveAlias"), REMOVE_ALIAS("removeAlias"), GET_PASSWORD("getPassword"), GET_ALIAS("getAlias");

    private final String key;

    KeystoreInteraction(String key) {
      this.key = key;
    }

    public String getKey() {
      return key;
    }
  };

  private final AtomicInteger numberOfTokensAdded = new AtomicInteger(0);
  private final AtomicInteger numberOfTokensRenewed = new AtomicInteger(0);
  private final Map<String, AtomicInteger> keystoreInteractions = new ConcurrentHashMap<>();
  private final AtomicLong gatewayCredentialsFileSize = new AtomicLong(0L);

  public TokenStateServiceStatistics() {
    super(TokenStateServiceStatisticsMBean.class, false);
  }

  public void addToken() {
    this.numberOfTokensAdded.incrementAndGet();
  }

  public void renewToken() {
    this.numberOfTokensRenewed.incrementAndGet();
  }

  public void interactKeystore(KeystoreInteraction keystoreInteraction) {
    this.keystoreInteractions.putIfAbsent(keystoreInteraction.getKey(), new AtomicInteger(0));
    this.keystoreInteractions.get(keystoreInteraction.getKey()).incrementAndGet();
  }

  public void setGatewayCredentialsFileSize(long gatewayCredentialsFileSize) {
    this.gatewayCredentialsFileSize.set(gatewayCredentialsFileSize);
  }

  @Override
  public int getNumberOfTokensAdded() {
    return this.numberOfTokensAdded.get();
  }

  @Override
  public int getNumberOfTokensRenewed() {
    return this.numberOfTokensRenewed.get();
  }

  @Override
  public Map<String, Integer> getKeystoreInteractions() {
    return this.keystoreInteractions.entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue().get()));
  }

  @Override
  public long getGatewayCredentialsFileSize() {
    return this.gatewayCredentialsFileSize.get();
  }
}
