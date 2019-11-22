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
package org.apache.knox.gateway.impala;

import org.apache.http.auth.AuthenticationException;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.auth.BasicScheme;

public class ImpalaDispatchUtils {

  private static final String PASSWORD_PLACEHOLDER = "*";

  public static void addCredentialsToRequest(HttpUriRequest request) {
    String principal = SubjectUtils.getCurrentEffectivePrincipalName();
    if ( principal != null ) {
      UsernamePasswordCredentials credentials =
          new UsernamePasswordCredentials(principal, PASSWORD_PLACEHOLDER);
      try {
        request.addHeader(new BasicScheme().authenticate(credentials, request, null));
      } catch (AuthenticationException e) {
        // impossible
      }
    }
  }
}
