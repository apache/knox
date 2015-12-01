/**
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
package org.apache.hadoop.gateway.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegExUtils {
  /**
   * Checks for a match of a given string against
   * a whitelist of semi-colon separated regex patterns.
   * @param whitelist - semi-colon separated patterns
   * @param tomatch - the string to match against list
   * @return true for a match otherwise false
   */
  public static boolean checkWhitelist(String whitelist, String tomatch) {
    String[] patterns = whitelist.split(";");
    for (String patternString : patterns) {
      Pattern pattern = Pattern.compile(patternString);
      Matcher matcher = pattern.matcher(tomatch);
      if (matcher.matches()) {
        return true;
      }
    }
    return false;
  }

}
