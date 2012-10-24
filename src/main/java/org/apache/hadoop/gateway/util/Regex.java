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

public class Regex {

  private static Object[] EMPTY_OBJECT_ARRAY = new Object[ 0 ];

  public static Object[] toGroupArray( Matcher matcher ) {
    Object[] array;
    if( matcher.matches() ) {
      array = new String[ matcher.groupCount() ];
      for( int i = 0, n = matcher.groupCount(); i < n; i++ ) {
        array[ i ] = matcher.group( i + 1 );
      }
    } else {
      array = EMPTY_OBJECT_ARRAY;
    }
    return array;
  }

}
