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

import org.apache.knox.gateway.util.IpAddressValidator;

/**
 *
 */
public class IPValidator implements PreAuthValidator {
  public static final String IP_ADDRESSES_PARAM = "preauth.ip.addresses";
  public static final String IP_VALIDATION_METHOD_VALUE = "preauth.ip.validation";

  public IPValidator() {
  }

  /**
   * @param httpRequest
   * @param filterConfig
   * @return true if validated, otherwise false
   * @throws PreAuthValidationException
   */
  @Override
  public boolean validate(HttpServletRequest httpRequest, FilterConfig filterConfig)
      throws PreAuthValidationException {
    String ipParam = filterConfig.getInitParameter(IP_ADDRESSES_PARAM);
    IpAddressValidator ipv = new IpAddressValidator(ipParam);
    return ipv.validateIpAddress(httpRequest.getRemoteAddr());
  }

  /**
   * Return unique validator name
   *
   * @return name of validator
   */
  @Override
  public String getName() {
    return IP_VALIDATION_METHOD_VALUE;
  }
}