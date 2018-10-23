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

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

abstract class Segment {

  static final String ANONYMOUS_PARAM = "";
  static final String DEFAULT_PATTERN = "";
  static final String STAR_PATTERN = "*";
  static final String GLOB_PATTERN = "**";

  static final char DOT = '.';
  static final char ESC = '\\';

  // Note: The order of these is important.  The numbers must be from most specific to least.
  public static final int STATIC = 1;
  public static final int REGEX = 2;
  public static final int STAR = 3;
  public static final int DEFAULT = 4;
  public static final int GLOB = 5;
  public static final int UNKNOWN = 6;

//  private String paramName; // ?queryName={paramName=value}
  private Token token;
  private Map<String,Value> values;

//  protected Segment( String paramName, String valuePattern ) {
//    this.paramName = paramName;
//    this.values = new LinkedHashMap<>();
//    this.values.put( valuePattern, new Value( valuePattern ) );
//  }

  protected Segment( Token token ) {
    this.token = token;
    this.values = new LinkedHashMap<>();
    this.values.put( token.effectivePattern, new Value( token ) );
  }

//  protected Segment( Segment that ) {
//    this.paramName = that.paramName;
//    this.values = new LinkedHashMap<>();
//    for( Value thatValue : that.getValues() ) {
//      Value thisValue = new Value( thatValue );
//      this.values.put( thisValue.getPattern(), thisValue );
//    }
//  }

  protected Token getToken() {
    return token;
  }

  @Override
  public int hashCode() {
    return token.parameterName.hashCode();
  }

  @Override
  @SuppressWarnings( "unchecked" )
  public boolean equals( Object obj ) {
    boolean equal = false;
    if( obj instanceof Segment ) {
      Segment that = (Segment)obj;
      equal = ( this.token.parameterName.equals( that.token.parameterName ) && this.values.size() == that.values.size() );
      if( equal ) {
        for( String pattern: this.values.keySet() ) {
          equal = that.values.containsKey( pattern );
          if( !equal ) {
            break;
          }
        }
      }
    }
    return equal;
  }

  public String getParamName() {
    return token.parameterName;
  }

  public Collection<Value> getValues() {
    return values.values();
  }

  public Value getFirstValue() {
    Value first = null;
    if( !values.isEmpty() ) {
      first = values.values().iterator().next();
    }
    return first;
  }

  public boolean matches( Segment that ) {
    if( getClass().isInstance( that ) ) {
      for( Value thisValue: this.values.values() ) {
        for( Value thatValue: that.values.values() ) {
          if( thisValue.matches( thatValue ) ) {
            return true;
          }
        }
      }
    }
    return false;
  }

  void addValue( Token token ) {
    Value value = new Value( token );
    values.put( token.effectivePattern, value );
  }

//  void addValue( String valuePattern ) {
//    Value value = new Value( valuePattern );
//    values.put( valuePattern, value );
//  }

  public String toString() {
    StringBuilder s = new StringBuilder();
    s.append( getParamName() );
    Collection<Value> values = getValues();
    if( values == null ) {
      s.append( "null" );
    } else if( values.isEmpty() ) {
      s.append( "empty" );
    } else {
      s.append( "[" );
      Iterator i = values.iterator();
      while( i.hasNext() ) {
        s.append( i.next() );
        if( i.hasNext() ) {
          s.append( "," );
        }
      }
      s.append( "]" );
    }
    return s.toString();
  }

  public class Value {
    private int type;
    private Token token;
    private Pattern regex;

    private Value( Token token ) {
      this.token = token;
      this.regex = null;
      String effectivePattern = token.effectivePattern;
      if( token.isLiteral() ) {
        this.type = STATIC;
      } else if( DEFAULT_PATTERN.equals( effectivePattern ) ) {
        this.type = DEFAULT;
      } else if( STAR_PATTERN.equals( effectivePattern ) ) {
        this.type = STAR;
      } else if( GLOB_PATTERN.equals( effectivePattern ) ) {
        type = GLOB;
      } else if ( effectivePattern != null && effectivePattern.contains( STAR_PATTERN ) ) {
        this.type = REGEX;
        this.regex = compileRegex( effectivePattern );
      } else {
        this.type = STATIC;
      }
    }

//    private Value( String pattern ) {
//      this.pattern = pattern;
//      this.regex = null;
//      if( DEFAULT_PATTERN.equals( pattern ) ) {
//        this.type = DEFAULT;
//      } else if( STAR_PATTERN.equals( pattern ) ) {
//        this.type = STAR;
//      } else if( GLOB_PATTERN.equals( pattern ) ) {
//        type = GLOB;
//      } else if ( pattern != null && pattern.contains( STAR_PATTERN ) ) {
//        this.type = REGEX;
//        this.regex = compileRegex( pattern );
//      } else {
//        this.type = STATIC;
//      }
//    }

    private Value( Value that ) {
      this.type = that.type;
      this.token = that.token;
      this.regex = that.regex;
    }

    Token getToken() {
      return token;
    }

    public int getType() {
      return type;
    }

    public String getPattern() {
      return token.originalPattern;
    }

    String getOriginalPattern() {
      return token.originalPattern;
    }

    String getEffectivePattern() {
      return token.effectivePattern;
    }

    public Pattern getRegex() {
      return regex;
    }

    public String toString() {
      return token.effectivePattern;
    }

    public boolean matches( Value that ) {
      boolean matches = getClass().isInstance( that );
      if( matches ) {
        switch( this.getType() ) {
          case( STATIC ):
            matches = this.token.originalPattern.equals( that.token.originalPattern );
            //matches = matchThisStatic( that ); // See: MatcherTest.testWildcardCharacterInInputTemplate
            break;
          case( DEFAULT ):
          case( STAR ):
          case( GLOB ):
            matches = true;
            //matches = matchThisWildcard( that ); // See: MatcherTest.testWildcardCharacterInInputTemplate
            break;
          case( REGEX ):
            matches = this.regex.matcher( that.token.effectivePattern ).matches();
            //matches = matchThisRegex( that ); // See: MatcherTest.testWildcardCharacterInInputTemplate
            break;
          default:
            matches = false;
        }
      }
      return matches;
    }

// See: MatcherTest.testWildcardCharacterInInputTemplate
//    private boolean matchThisStatic( Value that ) {
//      boolean matches = false;
//      switch( that.getType() ) {
//        case( STATIC ):
//          matches = this.pattern.equals( that.pattern );
//          break;
//        case( DEFAULT ):
//        case( STAR ):
//        case( GLOB ):
//          matches = true;
//          break;
//        case( REGEX ):
//          matches = that.regex.matcher( this.pattern ).matches();
//          break;
//      }
//      return matches;
//    }

// See: MatcherTest.testWildcardCharacterInInputTemplate
//    private boolean matchThisWildcard( Value that ) {
//      boolean matches = false;
//      switch( that.getType() ) {
//        case( STATIC ):
//          matches = true;
//          break;
//        case( DEFAULT ):
//        case( STAR ):
//        case( GLOB ):
//          matches = true;
//          break;
//        case( REGEX ):
//          matches = true;
//          break;
//      }
//      return matches;
//    }

// See: MatcherTest.testWildcardCharacterInInputTemplate
//    private boolean matchThisRegex( Value that ) {
//      boolean matches = false;
//      switch( that.getType() ) {
//        case( STATIC ):
//          matches = this.regex.matcher( that.pattern ).matches();
//          break;
//        case( DEFAULT ):
//        case( STAR ):
//        case( GLOB ):
//          matches = true;
//          break;
//        case( REGEX ):
//          matches = this.pattern.equals( that.pattern );
//          break;
//      }
//      return matches;
//    }

  }

  // Escape .\${ and turn * into .*
  static final String createRegex( final String segment ) {
    StringBuilder regex = new StringBuilder( segment );
    int len = regex.length();
    for( int i=len-1; i>=0; i-- ) {
      char c = regex.charAt( i );
      switch( c ) {
        case '*':
          regex.insert( i, DOT );
          break;
        case '\\':
        case '.':
        case '{':
        case '}':
        case '$':
          regex.insert( i, ESC );
          break;
        default:
          // noop
      }
    }
    return regex.toString();
  }

  // Creates a pattern for a simplified filesystem style wildcard '*' syntax.
  static final Pattern compileRegex( String segment ) {
//    // Turn '*' into '/' to keep it safe.
//    // Chose '/' because that can't exist in a segment.
//    segment = segment.replaceAll( "\\*", "/" );
//    // Turn '.' into '\.'.
//    segment = segment.replaceAll( "\\.", "\\\\." );
//    // Turn '$' into '\$'.
//    segment = escapeSpecialRegExChar( segment, '$' );
//    segment = escapeSpecialRegExChar( segment, '{' );
//    // Turn '/' back into '.*'.
//    segment = segment.replaceAll( "/", "\\.\\*" );
    segment = createRegex( segment );
    return Pattern.compile( segment );
  }

//  private static String escapeSpecialRegExChar( String input, char c ) {
//    int i = input.indexOf( c );
//    if( i >= 0 ) {
//      int inputLength = input.length();
//      StringBuilder output = new StringBuilder( inputLength + 1 );
//      output.append( input, 0, i );
//      output.append( '\\' );
//      if( i < inputLength ) {
//        output.append( input, i, inputLength );
//      }
//      input = output.toString();
//    }
//    return input;
//  }

  // Escape
}
