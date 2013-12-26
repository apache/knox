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

import javax.servlet.http.HttpServletRequest;

import org.apache.hadoop.gateway.util.IpAddressValidator;

/**
 *
 */
class IPValidator implements PreAuthValidator {
  private IpAddressValidator ipv = null;
  
  /**
   * @param initParameter
   */
  public IPValidator(String ipParam) {
    ipv = new IpAddressValidator(ipParam);
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.preauth.filter.PreAuthValidator#validate(java.lang.String, java.lang.String)
   */
  @Override
  public boolean validate(HttpServletRequest request)
      throws PreAuthValidationException {
    
    return ipv.validateIpAddress(request.getRemoteAddr());
  }
}