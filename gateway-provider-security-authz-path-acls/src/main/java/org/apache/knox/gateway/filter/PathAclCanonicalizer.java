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
package org.apache.knox.gateway.filter;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Pattern;

final class PathAclCanonicalizer {
  private static final String SCHEME_SEPARATOR = "://";
  private static final char REPLACEMENT_CHARACTER = '\uFFFD';
  private static final Pattern DOT_SEGMENT_WITH_PARAMETER = Pattern.compile("(^|/)\\.{1,2};");

  private PathAclCanonicalizer() {
  }

  static String canonicalize(final String url) {
    if (url == null) {
      return null;
    }
    final int schemeEnd = url.indexOf(SCHEME_SEPARATOR);
    final int pathStart = schemeEnd < 0 ? 0 : url.indexOf('/', schemeEnd + SCHEME_SEPARATOR.length());
    if (pathStart < 0) {
      return url;
    }
    try {
      final String path = url.substring(pathStart).replaceAll(";[^/]*", "").replace("+", "%2B");
      final String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8.name()).replace('\\', '/');
      if (decoded.indexOf(REPLACEMENT_CHARACTER) >= 0 || DOT_SEGMENT_WITH_PARAMETER.matcher(decoded).find()) {
        return null;
      }
      final String normalized = new URI(null, null, decoded, null).normalize().getRawPath();
      if (Arrays.asList(normalized.split("/")).contains("..")) {
        return null;
      }
      return url.substring(0, pathStart) + normalized;
    } catch (IllegalArgumentException | URISyntaxException | UnsupportedEncodingException e) {
      return null;
    }
  }
}
