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
package org.apache.hadoop.gateway.preauth.filter;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import java.util.Set;
import java.util.Collections;
import java.util.ServiceLoader;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class manages few utility methods used across different classes of pre-auth module
 * @since 0.12
 */
public class PreAuthService {

  public static final String VALIDATION_METHOD_PARAM = "preauth.validation.method";
  private static ConcurrentHashMap<String, PreAuthValidator> validatorMap;

  static {
    initializeValidators();
  }


  private static void initializeValidators() {
    ServiceLoader<PreAuthValidator> servLoader = ServiceLoader.load(PreAuthValidator.class);
    validatorMap = new ConcurrentHashMap<>();
    for (Iterator<PreAuthValidator> iterator = servLoader.iterator(); iterator.hasNext(); ) {
      PreAuthValidator validator = iterator.next();
      validatorMap.put(validator.getName(), validator);
    }
  }

  @VisibleForTesting
  public static Map<String, PreAuthValidator> getValidatorMap() {
    return Collections.unmodifiableMap(validatorMap);
  }

  /**
   * This method returns appropriate pre-auth Validator as defined in config
   *
   * @since 0.12
   * @param filterConfig
   * @return PreAuthValidator
   * @throws ServletException
   */
  public static PreAuthValidator getValidator(FilterConfig filterConfig) throws ServletException {
    String validationMethod = filterConfig.getInitParameter(VALIDATION_METHOD_PARAM);
    if (Strings.isNullOrEmpty(validationMethod)) {
      validationMethod = DefaultValidator.DEFAULT_VALIDATION_METHOD_VALUE;
    }
    if (validatorMap.containsKey(validationMethod)) {
      return validatorMap.get(validationMethod);
    } else {
      throw new ServletException(String.format("Unable to find validator with name '%s'", validationMethod));
    }
  }

}
