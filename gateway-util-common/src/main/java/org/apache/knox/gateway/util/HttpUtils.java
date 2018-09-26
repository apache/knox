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
package org.apache.knox.gateway.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class HttpUtils {

  public static Map<String, List<String>> splitQuery(String queryString)
      throws UnsupportedEncodingException {
    final Map<String, List<String>> queryPairs = new HashMap<String, List<String>>();
    if (queryString == null || queryString.trim().isEmpty()) {
      return queryPairs;
    }
    final String[] pairs = queryString.split("&");
    for (String pair : pairs) {
      final int idx = pair.indexOf("=");
      final String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
      if (!queryPairs.containsKey(key)) {
        queryPairs.put(key, new ArrayList<String>());
      }
      final String value = idx > 0 && pair.length() > idx + 1 
          ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8") : "";
      queryPairs.get(key).add(value);
    }
    return queryPairs;
  }

  public static Map<String,String[]> parseQueryString( String queryString ) {
    Map<String,String[]> map = new HashMap<>();
    if( queryString != null && !queryString.isEmpty() ) {
      StringTokenizer parser = new StringTokenizer( queryString, "&?;=", true );
      String name = null;
      String value = null;
      while( parser.hasMoreTokens() ) {
        String token = parser.nextToken();
        String ttoken = token.trim();
        if( ttoken.length() == 1 ) {
          char c = ttoken.charAt( 0 );
          switch( c ) {
            case '&':
            case '?':
            case ';':
              addQueryStringParam( map, name, value );
              name = null;
              value = null;
              continue;
            case '=':
              if( name == null ) {
                name = "";
                value = "";
              } else if( name.isEmpty() ) {
                addQueryStringParam( map, name, value );
                name = "";
                value = "";
              } else {
                value = "";
              }
              continue;
          }
        }
        if( name == null ) {
          name = token;
        } else {
          value = token;
        }
      } // while
      if( name != null ) {
        addQueryStringParam( map, name, value );
      }
    }
    return map;
  }

  private static final String urlDecodeUtf8( String s ) {
    if( s != null ) {
      try {
        s = URLDecoder.decode( s, "UTF-8" );
      } catch( UnsupportedEncodingException e ) {
        // Ignore it.
      }
    }
    return s;
  }

  static final void addQueryStringParam( final Map<String,String[]> map, String name, String value ) {
    name = urlDecodeUtf8( name );
    value = urlDecodeUtf8( value );
    String[] values = map.get( name );
    if( values == null ) {
      values = new String[]{ value };
    } else {
      values = Arrays.copyOf( values, values.length + 1 );
      values[ values.length-1 ] = value;
    }
    map.put( name, values );
  }

}
