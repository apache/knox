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

import org.apache.knox.gateway.util.Tokens;

public class UnknownTokenException extends Exception {

  private final String tokenId;
  private final String tokenIdDisplay;

  /**
   *
   * @param tokenId The token unique identifier
   */
  public UnknownTokenException(final String tokenId) {
    this.tokenId = tokenId;
    this.tokenIdDisplay = Tokens.getTokenIDDisplayText(tokenId);
  }

  public String getTokenId() {
    return tokenId;
  }

  @Override
  public String getMessage() {
    return "Unknown token: " + tokenIdDisplay;
  }

}
