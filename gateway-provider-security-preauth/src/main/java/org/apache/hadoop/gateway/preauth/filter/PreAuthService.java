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
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
   * @return List<PreAuthValidator>
   * @throws ServletException
   */
  public static List<PreAuthValidator> getValidators(FilterConfig filterConfig) throws ServletException {
    String validationMethods = filterConfig.getInitParameter(VALIDATION_METHOD_PARAM);
    List<PreAuthValidator> vList = new ArrayList<>();
    if (Strings.isNullOrEmpty(validationMethods)) {
      validationMethods = DefaultValidator.DEFAULT_VALIDATION_METHOD_VALUE;
    }
    Set<String> vMethodSet = new LinkedHashSet<>();
    Collections.addAll(vMethodSet, validationMethods.trim().split("\\s*,\\s*"));
    for (String vName : vMethodSet) {
      if (validatorMap.containsKey(vName)) {
        vList.add(validatorMap.get(vName));
      } else {
        throw new ServletException(String.format("Unable to find validator with name '%s'", validationMethods));
      }
    }
    return vList;
  }

  public static boolean validate(HttpServletRequest httpRequest, FilterConfig filterConfig, List<PreAuthValidator>
      validators) {
    try {
      for (PreAuthValidator validator : validators) {
        //Any one validator fails, it will fail the request. loginal AND behavior
        if (!validator.validate(httpRequest, filterConfig)) {
          return false;
        }
      }
    } catch (PreAuthValidationException e) {
      // TODO log exception
      return false;
    }
    return true;
  }

}
