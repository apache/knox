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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.knox.gateway.util.JsonUtils;

public class TokenMetadata {
  private static final String JSON_ELEMENT_USER_NAME = "userName";
  private static final String JSON_ELEMENT_COMMENT = "comment";
  private static final String EMPTY_COMMENT = "";

  private final String userName;
  private final String comment;

  public TokenMetadata(String userName) {
    this(userName, EMPTY_COMMENT);
  }

  public TokenMetadata(String userName, String comment) {
    this.userName = userName;
    this.comment = comment;
  }

  public String getUserName() {
    return userName;
  }

  public String getComment() {
    return comment;
  }

  public String toJSON() {
    final Map<String, String> metadataMap = new HashMap<>();
    metadataMap.put(JSON_ELEMENT_USER_NAME, getUserName());
    metadataMap.put(JSON_ELEMENT_COMMENT, getComment() == null ? EMPTY_COMMENT : getComment());
    return JsonUtils.renderAsJsonString(metadataMap);
  }

  public static TokenMetadata fromJSON(String json) {
    final Map<String, String> metadataMap = JsonUtils.getMapFromJsonString(json);
    if (metadataMap != null) {
      return new TokenMetadata(metadataMap.get(JSON_ELEMENT_USER_NAME), metadataMap.get(JSON_ELEMENT_COMMENT));
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
