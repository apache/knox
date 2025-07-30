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
package org.apache.knox.gateway.services.security.token;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class KnoxToken implements Comparable<KnoxToken> {
  private static final Comparator<KnoxToken> COMPARATOR = Comparator
          .comparingLong((KnoxToken kt) -> kt.issueTime)
          .thenComparing(kt -> kt.tokenId);

  public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
  // SimpleDateFormat is not thread safe must use as a ThreadLocal
  private static final ThreadLocal<DateFormat> KNOX_TOKEN_TS_FORMAT = ThreadLocal
      .withInitial(() -> new SimpleDateFormat(DATE_FORMAT, Locale.getDefault()));

  private final String tokenId;
  private final long issueTime;
  private final long expiration;
  private final long maxLifetime;
  private TokenMetadata metadata;

  public KnoxToken(String tokenId, long issueTime, long expiration, long maxLifetimeDuration) {
    this(tokenId, issueTime, expiration, maxLifetimeDuration, new TokenMetadata(Collections.emptyMap()));
  }

  public KnoxToken(String tokenId, long issueTime, long expiration, long maxLifetime, TokenMetadata metadata) {
    this.tokenId = tokenId;
    this.issueTime = issueTime;
    this.expiration = expiration;
    this.maxLifetime = maxLifetime;
    this.metadata = metadata;
  }

  public String getTokenId() {
    return tokenId;
  }

  public String getIssueTime() {
    return KNOX_TOKEN_TS_FORMAT.get().format(new Date(issueTime));
  }

  public long getIssueTimeLong() {
    return issueTime;
  }

  public String getExpiration() {
    return expiration < 0 ? "Never" : KNOX_TOKEN_TS_FORMAT.get().format(new Date(expiration));
  }

  public long getExpirationLong() {
    return expiration;
  }

  public String getMaxLifetime() {
    return maxLifetime < 0 ? "Unbounded" : KNOX_TOKEN_TS_FORMAT.get().format(new Date(maxLifetime));
  }

  public long getMaxLifetimeLong() {
    return maxLifetime;
  }

  public TokenMetadata getMetadata() {
    return metadata;
  }

  public void setMetadata(TokenMetadata metadata) {
    this.metadata = metadata;
  }

  public String getMetadataValue(String key) {
    return this.metadata == null ? null : metadata.getMetadata(key);
  }

  public boolean hasMetadata(String key) {
    return getMetadataValue(key) != null;
  }

  @Override
  public int compareTo(KnoxToken other) {
    return COMPARATOR.compare(this, other);
  }

  public void addMetadata(String name, String value) {
    metadata.add(name, value);
  }
}
