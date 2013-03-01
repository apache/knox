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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//NOTE: Instances Not thread safe but reusable.  Static parse method is thread safe.
//NOTE: Ignores matrix parameters at this point.
public class Parser {

  /*
      ^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?
       12            3  4          5       6  7        8 9

   The numbers in the second line above are only to assist readability;
   they indicate the reference points for each subexpression (i.e., each
   paired parenthesis).  We refer to the value matched for subexpression
   <n> as $<n>.  For example, matching the above expression to

      http://www.ics.uci.edu/pub/ietf/uri/#Related

   results in the following subexpression matches:

      $1 = http:
      $2 = http
      $3 = //www.ics.uci.edu
      $4 = www.ics.uci.edu
      $5 = /pub/ietf/uri/
      $6 = <undefined>
      $7 = <undefined>
      $8 = #Related
      $9 = Related

   where <undefined> indicates that the component is not present, as is
   the case for the query component in the above example.  Therefore, we
   can determine the value of the five components as

      scheme    = $2
      authority = $4
      path      = $5
      query     = $7
      fragment  = $9
   */

  public static final char TEMPLATE_OPEN_MARKUP = '{';
  public static final char TEMPLATE_CLOSE_MARKUP = '}';
  public static final char NAME_PATTERN_SEPARATOR = '=';

  private static final int MATCH_GROUP_SCHEME = 1;
  private static final int MATCH_GROUP_SCHEME_NAKED = 2;
  private static final int MATCH_GROUP_AUTHORITY = 3;
  private static final int MATCH_GROUP_AUTHORITY_NAKED = 4;
  private static final int MATCH_GROUP_PATH = 5;
  private static final int MATCH_GROUP_QUERY = 6;
  private static final int MATCH_GROUP_QUERY_NAKED = 7;
  private static final int MATCH_GROUP_FRAGMENT = 8;
  private static final int MATCH_GROUP_FRAGMENT_NAKED = 9;

  private static Pattern PATTERN = Pattern.compile( "^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?" );

  private String template; // Kept this for debugging.
  private Builder builder;

  public static Template parse( String template ) throws URISyntaxException {
    return new Parser().parseTemplate( template );
  }

  public Template parseTemplate( String template ) throws URISyntaxException {
    this.template = template;
    builder = new Builder();
    builder.setHasScheme( false );
    builder.setHasAuthority( false ); // Assume no until found otherwise.  If true, will cause // in output URL.
    builder.setIsAbsolute( false ); // Assume relative until found otherwise.  If true, will cause leading / in output URL.
    builder.setIsDirectory( false ); // Assume a file path until found otherwise.  If true, will cause trailing / in output URL.
    builder.setHasQuery( false ); // Assume no ? until found otherwise.  If true, will cause ? in output URL.
    builder.setHasFragment( false ); // Assume no # until found otherwise.  If true, will cause # in output URL.
    Matcher match = PATTERN.matcher( template );
    if( match.matches() ) {
      consumeSchemeMatch( match );
      consumeAuthorityMatch( match );
      consumePathMatch( match );
      consumeQueryMatch( match );
      consumeFragmentMatch( match );
      fixNakedAuthority();
    } else {
      throw new IllegalArgumentException( template );
    }
    return builder.build();
  }

  private void fixNakedAuthority() {
    if( builder.getHashScheme() &&
        !builder.getHashAuthority() &&
        !builder.getIsAbsolute() &&
        !builder.getIsDirectory() &&
        ( builder.getPath().size() == 1 ) &&
        !builder.getHasQuery() &&
        !builder.getHasFragment() ) {
      Scheme scheme = builder.getScheme();
      builder.setHasScheme( false );
      builder.setHost( scheme.getParamName(), makePatternSingular( scheme.getFirstValue().getPattern() ) );
      Path path = builder.getPath().remove( 0 );
      builder.setPort( path.getParamName(), makePatternSingular( path.getFirstValue().getPattern() ) );
    }
  }

  private String makePatternSingular( String pattern ) {
    if( Segment.GLOB_PATTERN.equals( pattern ) ) {
      pattern = Segment.STAR_PATTERN;
    }
    return pattern;
  }

  private void consumeSchemeMatch( Matcher match ) {
    if( match.group( MATCH_GROUP_SCHEME ) != null ) {
      builder.setHasScheme( true );
      consumeSchemeToken( match.group( MATCH_GROUP_SCHEME_NAKED ) );
    }
  }

  private void consumeSchemeToken( String token ) {
    if( token != null ) {
      String[] pair = parseTemplateToken( token, Segment.STAR_PATTERN );
      builder.setScheme( pair[0], pair[1] );
    }
  }

  private void consumeAuthorityMatch( Matcher match ) {
    if( match.group( MATCH_GROUP_AUTHORITY ) != null ) {
      builder.setHasAuthority( true );
      consumeAuthorityToken( match.group( MATCH_GROUP_AUTHORITY_NAKED ) );
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
          paramPattern = parseTemplateToken( usernamePassword[ 0 ], Segment.STAR_PATTERN );
          builder.setUsername( paramPattern[ 0 ], paramPattern[ 1 ] );
        }
        if( usernamePassword.length > 1 && usernamePassword[ 1 ].length() > 0 ) {
          paramPattern = parseTemplateToken( usernamePassword[ 1 ], Segment.STAR_PATTERN );
          builder.setPassword( paramPattern[ 0 ], paramPattern[ 1 ] );
        }
      }
      if( hostPort[ 0 ].length() > 0 ) {
        paramPattern = parseTemplateToken( hostPort[ 0 ], Segment.STAR_PATTERN );
        builder.setHost( paramPattern[ 0 ], paramPattern[1] );
      }
      if( hostPort.length > 1 && hostPort[ 1 ].length() > 0 ) {
        paramPattern = parseTemplateToken( hostPort[ 1 ], Segment.STAR_PATTERN );
        builder.setPort( paramPattern[ 0 ], paramPattern[ 1 ] );
      }
    }
  }

  private void consumePathMatch( Matcher match ) {
    String path = match.group( MATCH_GROUP_PATH );
    if( path != null ) {
      builder.setIsAbsolute( path.startsWith( "/" ) );
      builder.setIsDirectory( path.endsWith( "/" ) );
      consumePathToken( path );
    }
  }

  private void consumePathToken( String token ) {
    if( token != null ) {
      StringTokenizer tokenizer = new StringTokenizer( token, "/" );
      while( tokenizer.hasMoreTokens() ) {
        consumePathSegment( tokenizer.nextToken() );
      }
    }
  }

  private void consumePathSegment( String token ) {
    if( token != null ) {
      String[] pair = parseTemplateToken( token, Segment.GLOB_PATTERN );
      builder.addPath( pair[ 0 ], pair[ 1 ] );
    }
  }

  private void consumeQueryMatch( Matcher match ) {
    if( match.group( MATCH_GROUP_QUERY ) != null ) {
      builder.setHasQuery( true );
      consumeQueryToken( match.group( MATCH_GROUP_QUERY_NAKED ) );
    }
  }

  private void consumeQueryToken( String token ) {
    if( token != null ) {
      StringTokenizer tokenizer = new StringTokenizer( token, "?&" );
      while( tokenizer.hasMoreTokens() ) {
        consumeQuerySegment( tokenizer.nextToken() );
      }
    }
  }

  private void consumeQuerySegment( String token ) {
    if( token != null && token.length() > 0 ) {
      // Shorthand format {queryParam} == queryParam={queryParam=*}
      if( TEMPLATE_OPEN_MARKUP == token.charAt( 0 ) ) {
        String[] paramPattern = parseTemplateToken( token, Segment.GLOB_PATTERN );
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
          builder.addQuery( queryName, Segment.ANONYMOUS_PARAM, null );
        } else {
          String queryName = nameValue[ 0 ];
          String[] paramPattern = parseTemplateToken( nameValue[ 1 ], Segment.GLOB_PATTERN );
          builder.addQuery( queryName, paramPattern[ 0 ], paramPattern[ 1 ] );
        }
      }
    }
  }

  private void consumeFragmentMatch( Matcher match ) {
    if( match.group( MATCH_GROUP_FRAGMENT ) != null ) {
      builder.setHasFragment( true );
      consumeFragmentToken( match.group( MATCH_GROUP_FRAGMENT_NAKED ) );
    }
  }

  private void consumeFragmentToken( String token ) {
    if( token != null && token.length() > 0 ) {
      String[] pair = parseTemplateToken( token, Segment.STAR_PATTERN );
      builder.setFragment( pair[0], pair[1] );
    }
  }

  private static String[] parseTemplateToken( String t, String defaultPattern ) {
    String[] a;
    int l = t.length();
    if( l > 0 ) {
      int b = ( t.charAt( 0 ) == TEMPLATE_OPEN_MARKUP ? 1 : 0 );
      int e = ( t.charAt( l-1 ) == TEMPLATE_CLOSE_MARKUP ? l-1 : l );
      int i = t.indexOf( NAME_PATTERN_SEPARATOR, b );
      // If this is a parameter template (ie {...}
      if( b > 0 ) {
        if( i < 0 ) {
          String n = t.substring( b, e );
          String p;
          if( Segment.GLOB_PATTERN.equals( n ) ) {
            p = Segment.GLOB_PATTERN;
          } else {
            p = defaultPattern; //Segment.STAR_PATTERN;
          }
          a = new String[]{ n, p };
        } else {
          a = new String[]{ t.substring( b, i ), t.substring( i+1, e ) };
        }
      // Otherwise this is an anonymous template
      } else {
        a = new String[]{ Segment.ANONYMOUS_PARAM, t.substring( b, e ) };
      }
    } else {
      a = new String[]{ Segment.ANONYMOUS_PARAM, null };
    }
    return a;
  }

  // Using this because String.split is very inefficient.
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
