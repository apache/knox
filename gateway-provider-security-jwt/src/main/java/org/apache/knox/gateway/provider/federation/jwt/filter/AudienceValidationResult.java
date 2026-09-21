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
package org.apache.knox.gateway.provider.federation.jwt.filter;

/**
 * Outcome of a {@link RequestAudienceValidator} check: whether the request's
 * token satisfies the audience requirement, and an optional message.
 */
public final class AudienceValidationResult {
  private final boolean isValid;
  private final String message;

  public AudienceValidationResult(final boolean isValid, final String message) {
    this.isValid = isValid;
    this.message = message;
  }

  public static AudienceValidationResult of(final boolean isValid) {
    return new AudienceValidationResult(isValid, null);
  }

  public boolean isValid() {
    return isValid;
  }

  public String message() {
    return message;
  }
}
