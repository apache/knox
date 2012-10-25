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

import java.util.regex.Pattern;

public class Segment {

  static final String WILDCARD_PATTERN = "*";
  static final String GLOB_PATTERN = "**";

  public static final int STATIC = 0;
  public static final int WILDCARD = 1;
  public static final int REGEX = 2;

  private int type;
  private String paramName;
  private String valuePattern;
  private Pattern valueRegex;
  private int minRequired;
  private int maxAllowed;
  //private boolean greedy;

  @Override
  public int hashCode() {
    return valuePattern.hashCode() + paramName.hashCode();
  }

  @Override
  @SuppressWarnings( "unchecked" )
  public boolean equals( Object obj ) {
    boolean equal = false;
    if( obj instanceof Segment ) {
      Segment that = (Segment)obj;
      equal = ( ( this.type == that.type ) &&
                ( this.paramName.equals( that.paramName ) ) &&
                ( this.valuePattern.equals( that.valuePattern ) ) &&
                ( this.minRequired == that.minRequired ) &&
                ( this.maxAllowed == that.maxAllowed ) ) ;//&&
                //( this.greedy == that.greedy ) );
    }
    return equal;
  }

  public Segment( String paramName, String valuePattern ) { //}, int minRequired, int maxAllowed, boolean greedy ) {
    this.paramName = paramName;
    this.valuePattern = valuePattern;
    //this.greedy = greedy;
    if( WILDCARD_PATTERN.equals( valuePattern ) ) {
      this.type = WILDCARD;
      this.minRequired = 1;
      this.maxAllowed = 1;
      this.valueRegex = null;
    } else if( GLOB_PATTERN.equals( valuePattern ) ) {
      type = WILDCARD;
      this.minRequired = 0;
      this.maxAllowed = Integer.MAX_VALUE;
      this.valueRegex = null;
    } else if ( valuePattern.contains( WILDCARD_PATTERN ) ) {
      this.type = REGEX;
      this.minRequired = 1;
      this.maxAllowed = 1;
      this.valueRegex = compileRegex( valuePattern );
    } else {
      this.type = STATIC;
      this.minRequired = 1;
      this.maxAllowed = 1;
      this.valueRegex = null;
    }
  }

  public int getType() {
    return type;
  }

  public String getParamName() {
    return paramName;
  }

  public String getValuePattern() {
    return valuePattern;
  }

  public Pattern getValueRegex() {
    return valueRegex;
  }

  public int getMinRequired() {
    return minRequired;
  }

  public int getMaxAllowed() {
    return maxAllowed;
  }

//  public boolean getGreedy() {
//    return greedy;
//  }

  // Creates a pattern for a simplified filesystem style wildcard '*' syntax.
  private static Pattern compileRegex( String segment ) {
    // Turn '*' into '/' to keep it safe.
    // Chose '/' because that can't exist in a segment.
    segment = segment.replaceAll( "\\*", "/" );
    // Turn '.' into '\.'.
    segment = segment.replaceAll( "\\.", "\\\\." );
    // Turn '/' back into '.*'.
    segment = segment.replaceAll( "/", "\\.\\*" );
    if( !segment.startsWith( "^" ) ) {
      segment = "^" + segment;
    }
    if( !segment.endsWith( "$" ) ) {
      segment = segment + "$";
    }
    return Pattern.compile( segment );
  }

  public boolean matches( Segment that ) {
    boolean matches;
    switch( this.getType() ) {
      case( STATIC ):
        matches = matchThisStatic( that );
        break;
      case( WILDCARD ):
        matches = matchThisWildcard( that );
        break;
      case( REGEX ):
        matches = matchThisRegex( that );
        break;
      default:
        matches = false;
    }
    return matches;
  }

  private boolean matchThisStatic( Segment that ) {
    boolean matches;
    switch( that.getType() ) {
      case( STATIC ):
        matches = this.getValuePattern().equals( that.getValuePattern() );
        break;
      case( WILDCARD ):
        matches = true;
        break;
      case( REGEX ):
        matches = that.getValueRegex().matcher( this.getValuePattern() ).matches();
        break;
      default:
        matches = false;
    }
    return matches;
  }

  private boolean matchThisWildcard( Segment that ) {
    boolean matches;
    switch( that.getType() ) {
      case( STATIC ):
        matches = true;
        break;
      case( WILDCARD ):
        matches = true;
        break;
      case( REGEX ):
        matches = true;
        break;
      default:
        matches = false;
    }
    return matches;
  }

  private boolean matchThisRegex( Segment that ) {
    boolean matches;
    switch( that.getType() ) {
      case( STATIC ):
        matches = this.getValueRegex().matcher( that.getValuePattern() ).matches();
        break;
      case( WILDCARD ):
        matches = true;
        break;
      case( REGEX ):
        matches = false;
        break;
      default:
        matches = false;
    }
    return matches;
  }

}
