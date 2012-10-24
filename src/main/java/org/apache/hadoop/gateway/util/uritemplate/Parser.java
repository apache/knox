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
package org.apache.hadoop.gateway.util.uritemplate;

import java.util.StringTokenizer;

//NOTE: Not thread safe but reusable.
public class Parser {

  private Builder builder;
  private StringTokenizer parser;
  private String prevToken;
  private String currToken;

  public void next( String delim ) {
    prevToken = currToken;
    currToken = parser.nextToken( delim );
  }

  public boolean more() {
    return parser.hasMoreTokens();
  }

  public Template parse( String text ) {
    parser = new StringTokenizer( text, "/?", true ); // Note that the delims are returned.
    builder = new Builder();
    builder.setIsAbsolute( false ); // Assume relative until found otherwise.
    builder.setIsDirectory( false ); // Assume a file path until found otherwise.
    prevToken = null;
    currToken = null;
    boolean firstPath = true;
    boolean inPath = true;
    while( more() ) {
      if( inPath ) {
        next( "/?" );
        if( "/".equals( currToken ) ) {
          if( firstPath ) {
            builder.setIsAbsolute( true );
          }
        } else if ( "?".equals( currToken ) ) {
          if( "/".equals( prevToken ) ) {
            builder.setIsDirectory( true );
          }
          builder.setHasQuery( true );
          inPath = false;
        } else {
          consumePathSegmentToken();
        }
        firstPath = false;
      } else {
        next( "&?" );
        if( !"&".equals( currToken ) && !"?".equals( currToken ) ) {
          consumeQuerySegmentToken();
        }
      }
    }
    if( "/".equals( currToken ) ) {
      builder.setIsDirectory( true );
    }
    return builder.build();
  }

  private void consumePathSegmentToken() {
    if( currToken.startsWith( "{" ) ) {
      consumePathTemplateToken();
    } else {
      consumePathPatternToken();
    }
  }

  private void consumePathPatternToken() {
    builder.addPathSegment( "", currToken );
  }

  private void consumePathTemplateToken() {
    String template = currToken;
    if( template.startsWith( "{" ) ) {
      template = template.substring( 1 );
    }
    if( template.endsWith( "}" ) ) {
      template = template.substring( 0, template.length()-1 );
    }
    builder.addPathSegment( template, "*" );
  }

  private void consumeQuerySegmentToken() {
    String nameValue[] = currToken.split( "=", 2 );
    if( nameValue.length == 2 ) {
      String name = nameValue[ 0 ];
      String value = nameValue[ 1 ];
      if( value.startsWith( "{" ) ) {
        consumeQueryTemplateToken( name, value );
      } else {
        consumeQueryPatternToken( name, value );
      }
    }
  }

  private void consumeQueryPatternToken( String queryName, String valuePattern ) {
    builder.addQuerySegment( queryName, "", valuePattern );
  }

  private void consumeQueryTemplateToken( String queryName, String paramName ) {
    if( paramName.startsWith( "{" ) ) {
      paramName = paramName.substring( 1 );
    }
    if( paramName.endsWith( "}" ) ) {
      paramName = paramName.substring( 0, paramName.length()-1 );
    }
    builder.addQuerySegment( queryName, paramName, "*" );
  }

}
