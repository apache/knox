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
package org.apache.hadoop.gateway.util.urltemplate;

import java.net.URISyntaxException;
import java.util.StringTokenizer;

//NOTE: Instances Not thread safe but reusable.  Static parse method is thread safe.
//NOTE: Does not handle scheme, authority or fragment parts yet.
//NOTE: Basically ignores matrix parameters at this point.
public class Parser {

  public static final char TEMPLATE_OPEN_MARKUP = '{';
  public static final char TEMPLATE_CLOSE_MARKUP = '}';
  public static final char NAME_PATTERN_SEPARATOR = '=';

  private static final int STATE_UNKNOWN = 0;
  private static final int STATE_SCHEME = 1;
  private static final int STATE_AUTHORITY = 2;
  private static final int STATE_PATH = 3;
  private static final int STATE_QUERY = 4;
  private static final int STATE_FRAGMENT = 5;

  private int state;
  private String template; // Kept this for debugging.
  private Builder builder;
  private StringTokenizer parser;
  private String prevToken;
  private String currToken;

  public static Template parse( String template ) throws URISyntaxException {
    return new Parser().parseTemplate( template );
  }

  public Template parseTemplate( String template ) throws URISyntaxException {
    this.template = template;
    state = STATE_UNKNOWN;
    parser = new StringTokenizer( template, ":/?", true ); // Note that the delims are returned.
    builder = new Builder();
    builder.setHasScheme( false );
    builder.setHasAuthority( false );
    builder.setIsAbsolute( false ); // Assume relative until found otherwise.
    builder.setIsDirectory( false ); // Assume a file path until found otherwise.
    builder.setHasQuery( false ); // Assume no ? until found otherwise.
    builder.setHasFragment( false );
    prevToken = null;
    currToken = null;
    while( more() ) {
      switch( state ) {
        case( STATE_UNKNOWN ):
          parseUnknown();
          break;
        case( STATE_SCHEME ):
          parseScheme();
          break;
        case( STATE_AUTHORITY ):
          parseAuthority();
          break;
        case( STATE_PATH ):
          parsePath();
          break;
        case( STATE_QUERY ):
          parseQuery();
          break;
        case( STATE_FRAGMENT ):
          parseFragment();
          break;
      }
    }
    parseFinish();
    return builder.build();
  }

  private void next( String delim ) {
    prevToken = currToken;
    currToken = parser.nextToken( delim );
  }

  private boolean more() {
    return parser.hasMoreTokens();
  }

  private void parseUnknown() throws URISyntaxException {
    next( ":/?#" );
    if( "/".equals( currToken ) ) {
      if( "/".equals( prevToken ) ) {
        state = STATE_AUTHORITY;
        builder.setHasAuthority( true );
      } else if( prevToken != null ) {
        state = STATE_PATH;
        consumePathSegmentToken( prevToken ); // Assume anything unknown before the '/' is a relative path.
      } else {
        // Could be the start an absolute path or the start of the authority so get the next token.
      }
    } else if ( "?".equals( currToken ) ) {
      state = STATE_QUERY;
      builder.setHasQuery( true );
      if( "/".equals( prevToken ) ) {
        builder.setIsAbsolute( true );
        builder.setIsDirectory( true );
      } else {
        consumePathSegmentToken( prevToken ); // Assume anything unknown before the '?' is a relative path.
      }
    } else if ( ":".equals( currToken ) ) {
      state = STATE_SCHEME;
      builder.setHasScheme( true );
      consumeSchemeToken( prevToken );
    } else if( "#".equals( currToken ) ) {
      state = STATE_FRAGMENT;
      builder.setHasFragment( true );
      consumePathSegmentToken( prevToken );
    } else if( "/".equals( prevToken ) ) {
      state = STATE_PATH;
      builder.setIsAbsolute( true );
      consumePathSegmentToken( currToken );
    } else {
      // Don't know what it is yet so get the next token.
    }
  }

  private void parseScheme() throws URISyntaxException {
    next( "/?#" );
    if( "/".equals( currToken ) ) {
      if( "/".equals( prevToken ) ) {
        state = STATE_AUTHORITY;
        builder.setHasAuthority( true );
      } else {
        // Could be the start an absolute path or the start of the authority so get the next token.
      }
    } else if ( "?".equals( currToken ) ) {
      state = STATE_QUERY;
      builder.setHasQuery( true );
      if( !":".equals( prevToken ) ) {
        consumePathSegmentToken( prevToken ); // Assume anything unknown before the '?' is a relative path.
      }
    } else if ( "#".equals( currToken ) ) {
      state = STATE_FRAGMENT;
      builder.setHasFragment( true );
      if( !":".equals( prevToken ) ) {
        consumePathSegmentToken( prevToken ); // Assume anything unknown before the '?' is a relative path.
      }
    } else {
      // Don't know what it is yet so get the next token.
    }
  }

  private void parseAuthority() throws URISyntaxException {
    next( "/?#" );
    if( "/".equals( currToken ) ) {
      state = STATE_PATH;
      builder.setIsAbsolute( true );
      if( !"/".equals( prevToken ) && !":".equals( prevToken ) ) {
        consumeAuthorityToken( prevToken );
      }
    } else if( "?".equals( currToken ) ) {
      state = STATE_QUERY;
      builder.setHasQuery( true );
      if( !"/".equals( prevToken ) && !":".equals( prevToken ) ) {
        consumeAuthorityToken( prevToken );
      }
    } else if( "#".equals( currToken ) ) {
      state = STATE_FRAGMENT;
      builder.setHasFragment( true );
      if( !"/".equals( prevToken ) && !":".equals( prevToken ) ) {
        consumeAuthorityToken( prevToken );
      }
    } else {
      // Advance past what must be the the authority token if any.
      // It will be consumed when the terminators are found.
    }
  }

  private void parsePath() {
    next( "/?#" );
    if( "/".equals( currToken ) ) {
      // Ingore the double /
    } else if( "?".equals( currToken ) ) {
      state = STATE_QUERY;
      builder.setHasQuery( true );
      if( "/".equals( prevToken ) ) {
        builder.setIsDirectory( true );
      }
    } else if( "#".equals( currToken ) ) {
      state = STATE_FRAGMENT;
      builder.setHasFragment( true );
      if( "/".equals( prevToken ) ) {
        builder.setIsDirectory( true );
      }
    } else {
      consumePathSegmentToken( currToken );
    }
  }

  private void parseQuery() {
    next( "?&#" );
    if( "&".equals( currToken ) || "?".equals( currToken ) ) {
      // Ignore the double & and ?
    } else if ( "#".equals( currToken ) ) {
        state = STATE_FRAGMENT;
        builder.setHasFragment( true );
    } else {
      consumeQuerySegmentToken( currToken );
    }
  }

  private void parseFragment() {
    next( "#" );
    if( "#".equals( currToken ) ) {
      // Ignore the double #
    } else {
      consumeFragmentToken( currToken );
    }
  }

  private void parseFinish() {
    switch( state ) {
      case( STATE_UNKNOWN ):
        if( "/".equals( currToken ) ) {
          builder.setIsAbsolute( true );
          builder.setIsDirectory( true );
        } else {
          consumePathSegmentToken( currToken );
        }
        break;
      case( STATE_AUTHORITY ):
        if( !"/".equals( currToken ) ) {
          consumeAuthorityToken( currToken );
        }
        break;
      case( STATE_PATH ):
        if( "/".equals( currToken ) ) {
          builder.setIsDirectory( true );
        }
        break;
      case( STATE_QUERY ):
        if( !"?".equals( currToken ) ) {
          consumeQuerySegmentToken( currToken );
        }
        break;
      case( STATE_FRAGMENT ):
        if( !"#".equals( currToken ) ) {
          consumeFragmentToken( currToken );
        }
        break;
    }
  }

  private void consumeSchemeToken( String token ) {
    if( token != null ) {
      String[] pair = parseTemplateToken( token );
      builder.setScheme( pair[0], pair[1] );
    }
  }

  private void consumeAuthorityToken( String token ) {
    if( token != null ) {
      String[] usernamePassword=null, hostPort=null, paramPattern=null;
      String[] userAddr = split( token, '@' );
      if( userAddr.length == 1 ) {
        hostPort = split( userAddr[ 0 ], ':' );
      } else {
        usernamePassword = split( userAddr[ 0 ], ':' );
        hostPort = split( userAddr[ 1 ], ':' );
      }
      if( usernamePassword != null ) {
        if( usernamePassword[ 0 ].length() > 0 ) {
          paramPattern = parseTemplateToken( usernamePassword[ 0 ] );
          builder.setUsername( paramPattern[ 0 ], paramPattern[ 1 ] );
        }
        if( usernamePassword.length > 1 && usernamePassword[ 1 ].length() > 0 ) {
          paramPattern = parseTemplateToken( usernamePassword[ 1 ] );
          builder.setPassword( paramPattern[ 0 ], paramPattern[ 1 ] );
        }
      }
      if( hostPort[ 0 ].length() > 0 ) {
        paramPattern = parseTemplateToken( hostPort[ 0 ] );
        builder.setHost( paramPattern[ 0 ], paramPattern[1] );
      }
      if( hostPort.length > 1 && hostPort[ 1 ].length() > 0 ) {
        paramPattern = parseTemplateToken( hostPort[ 1 ] );
        builder.setPort( paramPattern[ 0 ], paramPattern[ 1 ] );
      }
    }
  }

  private void consumePathSegmentToken( String token ) {
    if( token != null ) {
      String[] pair = parseTemplateToken( token );
      builder.addPath( pair[ 0 ], pair[ 1 ] );
    }
  }

  private void consumeQuerySegmentToken( String token ) {
    if( token != null && token.length() > 0 ) {
      // Shorthand format {queryParam} == queryParam={queryParam=*}
      if( TEMPLATE_OPEN_MARKUP == token.charAt( 0 ) ) {
        String[] paramPattern = parseTemplateToken( token );
        if( paramPattern.length == 1 ) {
          String paramName = paramPattern[ 0 ];
          builder.addQuery( paramName, paramName, Segment.STAR_PATTERN );
        } else {
          String paramName = paramPattern[ 0 ];
          builder.addQuery( paramName, paramName, paramPattern[ 1 ] );
        }
      } else {
        String nameValue[] = split( token, '=' );
        if( nameValue.length == 1 ) {
          String queryName = nameValue[ 0 ];
          builder.addQuery( queryName, Segment.ANONYMOUS_PARAM, "*" );
        } else {
          String queryName = nameValue[ 0 ];
          String[] paramPattern = parseTemplateToken( nameValue[ 1 ] );
          builder.addQuery( queryName, paramPattern[ 0 ], paramPattern[ 1 ] );
        }
      }
    }
  }

  private void consumeFragmentToken( String token ) {
    if( token != null ) {
      String[] pair = parseTemplateToken( token );
      builder.setFragment( pair[0], pair[1] );
    }
  }

  private static String[] parseTemplateToken( String t ) {
    String[] a;
    int l = t.length();
    int b = ( t.charAt( 0 ) == TEMPLATE_OPEN_MARKUP ? 1 : 0 );
    int e = ( t.charAt( l-1 ) == TEMPLATE_CLOSE_MARKUP ? l-1 : l );
    int i = t.indexOf( NAME_PATTERN_SEPARATOR, b );
    // If this is a parameter template (ie {...}
    if( b > 0 ) {
      if( i < 0 ) {
        a = new String[]{ t.substring( b, e ), Segment.STAR_PATTERN };
      } else {
        a = new String[]{ t.substring( b, i ), t.substring( i+1, e ) };
      }
    // Otherwise this is an anonymous template
    } else {
      a = new String[]{ Segment.ANONYMOUS_PARAM, t.substring( b, e ) };
    }
    return a;
  }

  private static String[] split( String s, char d ) {
    String[] a;
    int i = s.indexOf( d );
    if( i < 0 ) {
      a = new String[]{ s };
    } else {
      a = new String[]{ s.substring( 0, i ), s.substring( i + 1 ) };
    }
    return a;
  }

}
