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

import java.security.Principal;
import java.util.Set;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.knox.gateway.security.GroupPrincipal;

public class HeaderPreAuthFederationFilter extends AbstractPreAuthFederationFilter {
  static final String CUSTOM_HEADER_PARAM = "preauth.custom.header";
  static final String CUSTOM_GROUP_HEADER_PARAM = "preauth.custom.group.header";
  String headerName = "SM_USER";
  String groupHeaderName = null;
  
  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    super.init(filterConfig);
    String customHeader = filterConfig.getInitParameter(CUSTOM_HEADER_PARAM);
    if (customHeader != null) {
      headerName = customHeader;
    }
    String customGroupHeader = filterConfig.getInitParameter(CUSTOM_GROUP_HEADER_PARAM);
    if (customGroupHeader != null) {
      groupHeaderName = customGroupHeader;
    }
  }

  /**
   * @param httpRequest
   */
  @Override
  protected String getPrimaryPrincipal(HttpServletRequest httpRequest) {
    return httpRequest.getHeader(headerName);
  }

  /**
   * @param principals
   */
  @Override
  protected void addGroupPrincipals(HttpServletRequest request, Set<Principal> principals) {
    if (groupHeaderName != null) {
      String headers = request.getHeader(groupHeaderName);
      if (headers != null) {
        String[] groups = headers.split(",");
        for (int i = 0; i < groups.length; i++) {
          principals.add(new GroupPrincipal(groups[i]));
        }
      }
    }
  }
}
