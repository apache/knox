/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.aws.utils;

import java.util.Date;
import java.util.Optional;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import org.apache.knox.gateway.aws.model.AwsSamlCredentials;

/**
 * Utility methods for {@link Cookie} management.
 */
public class CookieUtils {

  /**
   * Gets the cookie value with {@code name} from the {@code request}
   *
   * @param request the HTTP request object
   * @param name the name of cookie to search for
   * @return cookie value if found, else {@link Optional#EMPTY}
   */
  public static Optional<String> getCookieValue(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    Optional<String> value = Optional.empty();
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        if (cookie.getName().equals(name)) {
          value = Optional.of(cookie.getValue());
        }
      }
    }
    return value;
  }

  /**
   * Creates a {@link Cookie} with the specified settings.
   *
   * @param name the name of the cookie
   * @param value the value of the cookie
   * @param domain the domain of the cookie
   * @param maxAge the max age for the cookie
   * @param isHttp determines if cookie is for http only use
   * @param isSecure determines if cookie is sent over secure channels
   * @return a new {@code Cookie} with the settings specified
   */
  public static Cookie createCookie(String name, String value,
      String domain,
      String path, int maxAge, boolean isHttp, boolean isSecure) {
    Cookie c = new Cookie(name, value);
    c.setDomain(domain);
    c.setMaxAge(maxAge);
    c.setHttpOnly(isHttp);
    c.setSecure(isSecure);
    c.setPath(path);
    return c;
  }

  /**
   * Returns the max age of Cookie that matches the expiration of AWS credentials in the cookie value.
   *
   * @param credentials  the credentials obtained from AWS using SAML Response
   * @return the age in seconds of cookie
   */
  public static int getCookieAgeMatchingAwsCredentials(AwsSamlCredentials credentials) {
    long diffInMs = Math.abs(credentials.getExpiration() - new Date().getTime());
    return diffInMs > 0 ? (int) (diffInMs / 1000) : 0;
  }
}
