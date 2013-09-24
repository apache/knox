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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

abstract class Segment {

  static final String ANONYMOUS_PARAM = "";
  static final String DEFAULT_PATTERN = "";
  static final String STAR_PATTERN = "*";
  static final String GLOB_PATTERN = "**";

  // Note: The order of these is important.  The numbers must be from most specific to least.
  public static final int STATIC = 1;
  public static final int REGEX = 2;
  public static final int STAR = 3;
  public static final int DEFAULT = 4;
  public static final int GLOB = 5;
  public static final int UNKNOWN = 6;

  private String paramName; // ?queryName={paramName=value}
  private Map<String,Value> values;

  public Segment( String paramName, String valuePattern ) {
    this.paramName = paramName;
    this.values = new LinkedHashMap<String,Value>();
    this.values.put( valuePattern, new Value( valuePattern ) );
  }

  protected Segment( Segment that ) {
    this.paramName = that.paramName;
    this.values = new LinkedHashMap<String,Value>();
    for( Value thatValue : that.getValues() ) {
      Value thisValue = new Value( thatValue );
      this.values.put( thisValue.getPattern(), thisValue );
    }
  }

  @Override
  public int hashCode() {
    return paramName.hashCode();
  }

  @Override
  @SuppressWarnings( "unchecked" )
  public boolean equals( Object obj ) {
    boolean equal = false;
    if( obj instanceof Segment ) {
      Segment that = (Segment)obj;
      equal = ( this.paramName.equals( that.paramName ) && this.values.size() == that.values.size() );
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
    return paramName;
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

  void addValue( String valuePattern ) {
    Value value = new Value( valuePattern );
    values.put( valuePattern, value );
  }

  public class Value {
    private int type;
    private String pattern;
    private Pattern regex;

    private Value( String pattern ) {
      this.pattern = pattern;
      this.regex = null;
      if( DEFAULT_PATTERN.equals( pattern ) ) {
        this.type = DEFAULT;
      } else if( STAR_PATTERN.equals( pattern ) ) {
        this.type = STAR;
      } else if( GLOB_PATTERN.equals( pattern ) ) {
        type = GLOB;
      } else if ( pattern != null && pattern.contains( STAR_PATTERN ) ) {
        this.type = REGEX;
        this.regex = compileRegex( pattern );
      } else {
        this.type = STATIC;
      }
    }

    private Value( Value that ) {
      this.type = that.type;
      this.pattern = that.pattern;
      this.regex = that.regex;
    }

    public int getType() {
      return type;
    }

    public String getPattern() {
      return pattern;
    }

    public Pattern getRegex() {
      return regex;
    }

    public boolean matches( Value that ) {
      boolean matches = getClass().isInstance( that );
      if( matches ) {
        switch( this.getType() ) {
          case( STATIC ):
            matches = this.pattern.equals( that.pattern );
            //matches = matchThisStatic( that ); // See: MatcherTest.testWildcardCharacterInInputTemplate
            break;
          case( DEFAULT ):
          case( STAR ):
          case( GLOB ):
            matches = true;
            //matches = matchThisWildcard( that ); // See: MatcherTest.testWildcardCharacterInInputTemplate
            break;
          case( REGEX ):
            matches = this.regex.matcher( that.pattern ).matches();
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
//          matches =  this.pattern.equals( that.pattern );
//          break;
//      }
//      return matches;
//    }

  }

  // Creates a pattern for a simplified filesystem style wildcard '*' syntax.
  private static Pattern compileRegex( String segment ) {
    // Turn '*' into '/' to keep it safe.
    // Chose '/' because that can't exist in a segment.
    segment = segment.replaceAll( "\\*", "/" );
    // Turn '.' into '\.'.
    segment = segment.replaceAll( "\\.", "\\\\." );
    // Turn '/' back into '.*'.
    segment = segment.replaceAll( "/", "\\.\\*" );
    return Pattern.compile( segment );
  }

}
