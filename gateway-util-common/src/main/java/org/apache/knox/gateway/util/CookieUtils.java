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

package org.apache.knox.gateway.util;

import org.apache.knox.gateway.i18n.GatewayUtilCommonMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

public class CookieUtils {
  private static final GatewayUtilCommonMessages LOGGER = MessagesFactory.get(GatewayUtilCommonMessages.class);

  private CookieUtils() {
  }

  public static List<Cookie> getCookiesForName(HttpServletRequest request, String name) {
    List<Cookie> cookiesByName = new ArrayList<>();
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for(Cookie cookie : cookies){
        if(name.equals(cookie.getName())){
          cookiesByName.add(cookie);
        }
      }
    }
    if (cookiesByName.isEmpty()) {
      LOGGER.cookieNotFound(name);
    }
    return cookiesByName;
  }
}
