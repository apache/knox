/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.util;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class URLUtils {

  /**
   * A method that decode url and concat query params, return result as URI.
   *
   * @param encoded encoded url
   * @param queryStr query params
   * @return decoded URI
   */
  public static URI getDecodeUri(String encoded, String queryStr) {
    String decoded;
    try {
      decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8.name());
    } catch (final Exception e) {
      /* fall back in case of exception */
      decoded = encoded;
    }

    final StringBuilder str = new StringBuilder(decoded);
    if (queryStr != null) {
      str.append('?');
      str.append(queryStr);
    }
    return URI.create(str.toString());
  }
}
