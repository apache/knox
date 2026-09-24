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

import java.util.List;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.knox.gateway.services.security.token.impl.JWT;

/**
 * Pluggable, externally-provided audience validation check for a JWT token.
 *
 * <p>A single instance is shared across all concurrent requests hitting the
 * filter that holds it (mirroring how PreAuthValidator instances are shared
 * via PreAuthService's validatorMap). Implementations must therefore not
 * mutate any state that is not itself already safe for concurrent access,
 * and must not assume init() and validate() run on the same thread.
 */
@FunctionalInterface
public interface RequestAudienceValidator {

  AudienceValidationResult validate(HttpServletRequest request, JWT token, List<String> configuredAudiences);

  default void init(FilterConfig filterConfig) throws Exception {
    // No-op: an implementation with nothing to set up (e.g. a purely
    // static/stateless check) does not need to override this.
  }

  default void destroy() {
    // No-op: an implementation with nothing to release does not need to
    // override this. Called unconditionally at filter shutdown on
    // whichever implementation the filter is currently holding, so this
    // must be safe to call even when init() was never called on this
    // instance.
  }

  /**
   * @return an identifier for this validator, used as the lookup key in
   *     {@code request.audience.validator}. Defaults to the implementing class's fully-qualified
   *     name, which is collision-resistant without requiring every implementation to override
   *     this; implementations registered under a shorter configured name should override it.
   */
  default String getName() {
    return getClass().getName();
  }
}
