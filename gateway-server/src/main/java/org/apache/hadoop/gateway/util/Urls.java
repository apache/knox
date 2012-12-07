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

import sun.org.mozilla.javascript.internal.regexp.RegExpImpl;

import java.text.MessageFormat;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class Urls {

  public static String ensureLeadingSlash( String path ) {
    if( path == null ) {
      return "/";
    } else if( path.startsWith( "/" ) ) {
      return path;
    } else {
      return "/" + path;
    }
  }

  private static String stripLeadingSlash( String s ) {
    s = s.trim();
    while( s.startsWith( "/" ) ) {
      s = s.substring(1);
    }
    return s;
  }

  public static String concatUrl( String prefix, String suffix ) {
    if( suffix == null ) {
      return prefix;
    } else if ( prefix.endsWith( "/" ) && suffix.startsWith( "/" ) ) {
      return prefix + stripLeadingSlash( suffix );
    } else if( !prefix.endsWith( "/" ) && !suffix.startsWith( "/" ) ) {
      return prefix + "/" + suffix;
    } else {
      return prefix + suffix;
    }
  }

}
