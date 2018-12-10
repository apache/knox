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
package org.apache.knox.gateway.trace;

import org.apache.knox.gateway.audit.api.CorrelationContext;
import org.apache.knox.gateway.audit.api.CorrelationService;
import org.apache.knox.gateway.audit.api.CorrelationServiceFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

public class TraceUtil {

  private static CorrelationService cs = CorrelationServiceFactory.getCorrelationService();

  static void appendCorrelationContext(final StringBuilder sb ) {
    CorrelationContext cc = cs.getContext();
    if( cc == null ) {
      sb.append( "||" );
    } else {
      append( sb, cc.getRootRequestId() );
      sb.append('|');
      append( sb, cc.getParentRequestId() );
      sb.append('|');
      append( sb, cc.getRequestId() );
    }
  }

  private static void append( final StringBuilder sb, final String s ) {
    if( s != null ) {
      sb.append( s );
    }
  }

  static Set<Integer> parseIntegerSet(String str ) {
    Set<Integer> set = new HashSet<>();
    if( str != null && !str.trim().isEmpty() ) {
      StringTokenizer parser = new StringTokenizer( str.trim(), ",", false );
      while( parser.hasMoreTokens() ) {
        addParsedIntegerToSet( set, parser.nextToken() );
      }
    }
    return set;
  }

  private static void addParsedIntegerToSet( Set<Integer> set, String str ) {
    if( str != null && !str.trim().isEmpty() ) {
      try {
        set.add( Integer.valueOf( str.trim() ) );
      } catch( NumberFormatException e ) {
        // Ignore it.
      }
    }
  }

}
