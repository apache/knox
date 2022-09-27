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
package org.apache.knox.gateway.shell.knox.token;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.knox.gateway.shell.KnoxSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Token {
  private static final String KNOX_TOKEN_INCLUDE_GROUPS = "knox.token.include.groups";
  static String SERVICE_PATH = "/knoxtoken/api/v1/token";

  public static Get.Request get(final KnoxSession session) {
    return new Get.Request(session);
  }

  public static Get.Request get(final KnoxSession session, final String doAsUser) {
    return new Get.Request(session, doAsUser, Collections.emptyList());
  }

  public static Get.Request get(final KnoxSession session, final String doAsUser, boolean includeGroupsInToken) {
    List<NameValuePair> queryParamss = new ArrayList<>();
    if (includeGroupsInToken) {
      queryParamss.add(new BasicNameValuePair(KNOX_TOKEN_INCLUDE_GROUPS, "true"));
    }
    return new Get.Request(session, doAsUser, queryParamss);
  }

  public static Renew.Request renew(final KnoxSession session, final String token) {
    return new Renew.Request(session, token);
  }

  public static Renew.Request renew(final KnoxSession session, final String token, final String doAsUser) {
    return new Renew.Request(session, token, doAsUser);
  }

  public static Revoke.Request revoke(final KnoxSession session, final String token) {
    return new Revoke.Request(session, token);
  }

  public static Revoke.Request revoke(final KnoxSession session, final String token, final String doAsUser) {
    return new Revoke.Request(session, token, doAsUser);
  }

}
