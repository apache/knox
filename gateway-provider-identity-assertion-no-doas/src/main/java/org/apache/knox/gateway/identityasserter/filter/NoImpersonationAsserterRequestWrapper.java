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
package org.apache.knox.gateway.identityasserter.filter;

import org.apache.knox.gateway.identityasserter.common.filter.IdentityAsserterHttpServletRequestWrapper;
import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NoImpersonationAsserterRequestWrapper extends
    IdentityAsserterHttpServletRequestWrapper {

  public NoImpersonationAsserterRequestWrapper(
      HttpServletRequest request, String principal) {
    super(request, principal);
  }

  /**
   * Skip adding doAs as query param
   * @return
   */
  @Override
  public String getQueryString() {
    String q = null;
    Map<String, List<String>> params;
    try {
      params = getParams();
      if (params == null) {
        params = new LinkedHashMap<>();
      }

      List<String> principalParamNames = getImpersonationParamNames();
      params = scrubOfExistingPrincipalParams(params, principalParamNames);

      String encoding = getCharacterEncoding();
      if (encoding == null) {
        encoding = Charset.defaultCharset().name();
      }
      q = urlEncode(params, encoding);
    } catch (UnsupportedEncodingException e) {
      log.unableToGetParamsFromQueryString(e);
    }

    return q;
  }
}
