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

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;

/**
 * Discovers {@link RequestAudienceValidator} implementations via
 * {@link ServiceLoader} and resolves the one named by a filter's
 * {@link #REQUEST_AUDIENCE_VALIDATOR_PARAM} init parameter, modeled on
 * PreAuthService's ServiceLoader-based discovery.
 */
public class RequestAudienceValidatorService {
  public static final String REQUEST_AUDIENCE_VALIDATOR_PARAM = "request.audience.validator";
  private final Map<String, RequestAudienceValidator> validatorMap = new ConcurrentHashMap<>();
  private boolean initialized;

  private synchronized void ensureInitialized() {
    if (!initialized) {
      final ServiceLoader<RequestAudienceValidator> loader = ServiceLoader.load(RequestAudienceValidator.class);
      for (RequestAudienceValidator validator : loader) {
        validatorMap.put(validator.getName(), validator);
      }
      initialized = true;
    }
  }

  public Optional<RequestAudienceValidator> getValidator(final FilterConfig filterConfig) throws ServletException {
    ensureInitialized();
    final String name = filterConfig.getInitParameter(REQUEST_AUDIENCE_VALIDATOR_PARAM);
    if (StringUtils.isBlank(name)) {
      return Optional.empty();
    }
    final RequestAudienceValidator validator = validatorMap.get(name);
    if (validator == null) {
      throw new ServletException(String.format(Locale.ROOT,
          "Unable to find request audience validator with name '%s'", name));
    }
    return Optional.of(validator);
  }

  // VisibleForTesting
  public Map<String, RequestAudienceValidator> getValidatorMap() {
    ensureInitialized();
    return Collections.unmodifiableMap(validatorMap);
  }
}
