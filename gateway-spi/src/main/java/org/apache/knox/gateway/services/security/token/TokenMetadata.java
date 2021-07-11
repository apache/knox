/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.services.security.token;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.knox.gateway.util.JsonUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class TokenMetadata {
  public static final String USER_NAME = "userName";
  public static final String COMMENT = "comment";
  public static final String ENABLED = "enabled";
  public static final String PASSCODE = "passcode";

  private final Map<String, String> metadataMap = new HashMap<>();

  public TokenMetadata(String userName) {
    this(userName, null);
  }

  public TokenMetadata(String userName, String comment) {
    this(userName, comment, true);
  }

  public TokenMetadata(String userName, String comment, boolean enabled) {
    saveMetadata(USER_NAME, userName);
    saveMetadata(COMMENT, comment);
    setEnabled(enabled);
  }

  private void saveMetadata(String key, String value) {
    if (StringUtils.isNotBlank(value)) {
      this.metadataMap.put(key, value);
    }
  }

  public TokenMetadata(Map<String, String> metadataMap) {
    this.metadataMap.clear();
    this.metadataMap.putAll(metadataMap);
  }

  @JsonIgnore
  public Map<String, String> getMetadataMap() {
    return new HashMap<String, String>(this.metadataMap);
  }

  public String getUserName() {
    return metadataMap.get(USER_NAME);
  }

  public String getComment() {
    return metadataMap.get(COMMENT);
  }

  public void setEnabled(boolean enabled) {
    saveMetadata(ENABLED, String.valueOf(enabled));
  }

  public boolean isEnabled() {
    return Boolean.parseBoolean(metadataMap.get(ENABLED));
  }

  public void setPasscode(String passcode) {
    saveMetadata(PASSCODE, passcode);
  }

  @JsonIgnore
  public String getPasscode() {
    return metadataMap.get(PASSCODE);
  }

  public String toJSON() {
    return JsonUtils.renderAsJsonString(metadataMap);
  }

  public static TokenMetadata fromJSON(String json) {
    final Map<String, String> metadataMap = JsonUtils.getMapFromJsonString(json);
    if (metadataMap != null) {
      return new TokenMetadata(metadataMap);
    }
    throw new IllegalArgumentException("Invalid metadata JSON: " + json);
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }

  @Override
  public boolean equals(Object obj) {
    return EqualsBuilder.reflectionEquals(this, obj);
  }

  @Override
  public int hashCode() {
    return HashCodeBuilder.reflectionHashCode(this);
  }
}
