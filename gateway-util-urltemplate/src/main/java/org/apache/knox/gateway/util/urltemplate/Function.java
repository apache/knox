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
package org.apache.knox.gateway.util.urltemplate;

import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

class Function {

  private enum State { UNKNOWN, FUNCTION, VARIABLE, LITERAL }

  private String funcName = null;
  private String paramName = null;
  private List<String> paramValue = null;

  static List<String> evaluate( String statement, Resolver resolver, Evaluator evaluator ) {
    Function function = new Function( statement );
    List<String> results = function.evaluate( resolver, evaluator );
    return results;
  }

  Function( String statement ) {
    if( statement != null ) {
      StringTokenizer parser = new StringTokenizer( statement, "$()[]", true );
      State state = State.UNKNOWN;
      String token;
      while( parser.hasMoreTokens() ) {
        token = parser.nextToken().trim();
        if( !token.isEmpty() ) {
          switch( state ) {
            case UNKNOWN:
              if( "$".equals( token ) ) {
                state = State.FUNCTION;
              } else if( "(".equals( token ) ) {
                state = State.VARIABLE;
              } else if( "[".equals( token ) ) {
                state = State.LITERAL;
              } else {
                paramName = token;
                return;
              }
              break;
            case FUNCTION:
              if( "$".equals( token ) || ")".equals( token ) || "]".equals( token ) ) {
                // Ignore it.
              } else if( "(".equals( token ) ) {
                state = State.VARIABLE;
              } else if( "[".equals( token ) ) {
                state = State.LITERAL;
              } else {
                funcName = token;
                state = State.UNKNOWN;
              }
              break;
            case VARIABLE:
              if( "$".equals( token ) || "(".equals( token ) || ")".equals( token ) || "[".equals( token ) || "]".equals( token ) ) {
                // Ignore it.
              } else {
                paramName = token;
                return;
              }
            case LITERAL:
              if( "$".equals( token ) || "(".equals( token ) || ")".equals( token ) || "[".equals( token ) || "]".equals( token ) ) {
                // Ignore it.
              } else {
                paramValue = Arrays.asList(token.split(","));
              }
              return;
          }
        }
      }
    }
  }

  String getFunctionName() {
    return funcName;
  }

  String getParameterName() {
    return paramName;
  }

  List<String> getParameterValue() {
    return paramValue;
  }

  List<String> evaluate( Resolver resolver, Evaluator evaluator ) {
    List<String> values = paramValue;
    if( paramName != null && resolver != null ) {
      values = resolver.resolve( paramName );
    }
    if( funcName != null && evaluator != null ) {
      values = evaluator.evaluate( funcName, values );
    }
    return values;
  }

}
