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

public class StringUtils {
  private StringUtils() {
  }

  /**
   * @param str a String
   * @param prefix a prefix
   * @return true if {@code str} starts with {@code prefix}, ignoring case sensitivity
   */
  public static boolean startsWithIgnoreCase(String str, String prefix) {
    return str.regionMatches(true, 0, prefix, 0, prefix.length());
  }

  /**
   * @param str a String
   * @param suffix a suffix
   * @return true if {@code str} ends with {@code suffix}, ignoring case sensitivity
   */
  public static boolean endsWithIgnoreCase(String str, String suffix) {
    int suffixLength = suffix.length();
    return str.regionMatches(true, str.length() - suffixLength, suffix, 0, suffixLength);
  }
}
