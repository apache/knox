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
package org.apache.knox.gateway.preauth.filter;

import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;

/**
 * @since 0.12
 * This class implements the default Validator where really no validation is performed.
 * TODO: log the fact that there is no verification going on to validate
 * +  who is asserting the identity with the a header. Without some validation
 * +  we are assuming the network security is the primary protection method.
 */
public class DefaultValidator implements PreAuthValidator {
  public static final String DEFAULT_VALIDATION_METHOD_VALUE = "preauth.default.validation";

  public DefaultValidator() {
  }

  @Override
  public boolean validate(HttpServletRequest httpRequest, FilterConfig filterConfig) throws PreAuthValidationException {
    return true;
  }

  /**
   * Return unique validator name
   *
   * @return name of validator
   */
  @Override
  public String getName() {
    return DEFAULT_VALIDATION_METHOD_VALUE;
  }
}
