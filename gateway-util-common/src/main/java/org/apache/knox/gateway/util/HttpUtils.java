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

import static java.util.Arrays.asList;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class HttpUtils {
  private static final List<Class<? extends IOException>> connectionErrors = asList(UnknownHostException.class, NoRouteToHostException.class,
          SocketException.class);

  public static Map<String, List<String>> splitQuery(String queryString)
      throws UnsupportedEncodingException {
    final Map<String, List<String>> queryPairs = new LinkedHashMap<>();
    if (queryString == null || queryString.trim().isEmpty()) {
      return queryPairs;
    }
    final String[] pairs = queryString.split("&");
    for (String pair : pairs) {
      final int idx = pair.indexOf('=');
      final String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name()) : pair;
      if (!queryPairs.containsKey(key)) {
        queryPairs.put(key, new ArrayList<>());
      }
      final String value = idx > 0 && pair.length() > idx
          ? URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name()) : null;
      queryPairs.get(key).add(value);
    }
    return queryPairs;
  }

  public static Map<String,String[]> parseQueryString( String queryString ) {
    Map<String,String[]> map = new LinkedHashMap<>();
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

  private static String urlDecodeUtf8(String s ) {
    if( s != null ) {
      try {
        s = URLDecoder.decode( s, StandardCharsets.UTF_8.name() );
      } catch( UnsupportedEncodingException e ) {
        // Ignore it.
      }
    }
    return s;
  }

  static void addQueryStringParam(final Map<String,String[]> map, String name, String value ) {
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

  /**
   * Determine if the specified exception represents an error condition that is related to a connection error.
   *
   * @return true, if the exception represents an connection error; otherwise, false.
   */
  public static boolean isConnectionError(Throwable exception) {
    boolean isFailoverException = false;
    if (exception != null) {
      for (Class<? extends Exception> exceptionType : connectionErrors) {
        if (exceptionType.isAssignableFrom(exception.getClass())) {
          isFailoverException = true;
          break;
        }
      }
    }
    return isFailoverException;
  }
}
