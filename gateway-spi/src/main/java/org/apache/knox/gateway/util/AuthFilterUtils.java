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

import org.apache.commons.lang3.StringUtils;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.StringTokenizer;

public class AuthFilterUtils {
  public static final String DEFAULT_AUTH_UNAUTHENTICATED_PATHS_PARAM = "/knoxtoken/api/v1/jwks.json";

  /**
   * A helper method that checks whether request contains
   * unauthenticated path
   * @param request
   * @return
   */
  public static boolean doesRequestContainUnauthPath(
      final Set<String> unAuthenticatedPaths, final ServletRequest request) {
    /* make sure the path matches EXACTLY to prevent auth bypass */
    return unAuthenticatedPaths.contains(((HttpServletRequest) request).getPathInfo());
  }

  /**
   * A helper method that parses a string and adds to the
   * provided unauthenticated set.
   * @param unAuthenticatedPaths
   * @param list
   */
  public static void parseStringThenAdd(final Set<String> unAuthenticatedPaths, final String list) {
    final StringTokenizer tokenizer = new StringTokenizer(list, ";,");
    while (tokenizer.hasMoreTokens()) {
      unAuthenticatedPaths.add(tokenizer.nextToken());
    }
  }

  /**
   * A method that parses a string (delimiters = ;,) and adds them to the
   * provided un-authenticated path set.
   * @param unAuthenticatedPaths
   * @param list
   * @param defaultList
   */
  public static void addUnauthPaths(final Set<String> unAuthenticatedPaths, final String list, final String defaultList) {
    /* add default unauthenticated paths list */
    parseStringThenAdd(unAuthenticatedPaths, defaultList);
    /* add provided unauthenticated paths list if specified */
    if (!StringUtils.isBlank(list)) {
      AuthFilterUtils.parseStringThenAdd(unAuthenticatedPaths, list);
    }
  }
}
