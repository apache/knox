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
package org.apache.knox.gateway.launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Config {

  static final String DEFAULT_NAME = "";
  static final String COMMENT_PREFIX = "#";
  static final String SECTION_PREFIX = "[";
  static final String SECTION_SUFFIX = "]";
  static final Pattern SECTION_PATTERN = Pattern.compile( "^\\[(.*?)\\]?$" );
  static final Pattern PAIR_PATTERN = Pattern.compile( "^(.*?)=(.*)$" );

  Map<String,Map<String,String>> sections;

  public Config() {
    sections = new LinkedHashMap<>();
  }

  public void load( Reader reader ) throws IOException {
    if( reader != null ) {
      BufferedReader input = new BufferedReader( reader );
      String profile = null;
      String line = input.readLine();
      while( line != null ) {
        line = line.trim();
        if(line.isEmpty()) {
          // Ignore blank lines.
        } else if( line.startsWith( COMMENT_PREFIX ) ) {
          // Ignore comments.
        } else if( line.startsWith( SECTION_PREFIX ) ) {
          // Switch sections.
          Matcher matcher = SECTION_PATTERN.matcher( line );
          if( matcher.matches() ) {
            profile = matcher.group( 1 );
          }
        } else {
          // Consume line.
          Matcher matcher = PAIR_PATTERN.matcher( line );
          if( matcher.matches() ) {
            if( matcher.groupCount() > 0 ) {
              String name = matcher.group( 1 );
              String value = null;
              if( matcher.groupCount() > 1 ) {
                value = matcher.group( 2 );
              }
              set( profile, name, value );
            }
          }
        }
        line = input.readLine();
      }
    }
  }

  public void save( Writer writer ) {
    boolean empty = true;
    if( writer != null ) {
      PrintWriter output = new PrintWriter( writer );
      Map<String,String> map = sections.get( DEFAULT_NAME );
      if( map != null ) {
        empty &= (map.isEmpty());
        saveMap( output, map );
      }
      for( Map.Entry<String,Map<String,String>> entry : sections.entrySet() ) {
        if( !empty ) {
          output.println();
        }
        String section = entry.getKey();
        if( section != null && !section.equals( DEFAULT_NAME ) ) {
          map = sections.get( section );
          output.print( SECTION_PREFIX );
          output.print( section );
          output.println( SECTION_SUFFIX );
          empty = false;
          saveMap( output, map );
        }
      }
    }
  }

  void saveMap( PrintWriter output, Map<String,String> map ) {
    for( Map.Entry<String,String> entry : map.entrySet() ) {
      output.print( entry.getKey() );
      output.print( "=" );
      String value = entry.getValue();
      if( value != null ) {
        output.print( entry.getValue() );
      }
    }
  }

  public String get( String section, String name ) {
    section = fixName( section );
    name = fixName( name );
    String value = null;
    Map<String,String> map = sections.get( section );
    if( map != null && map.containsKey( name ) ) {
      value = map.get( name );
    } else {
      map = sections.get( DEFAULT_NAME );
      if( map != null ) {
        value = map.get( name );
      }
    }
    return value;
  }

  public void set( String section, String name, String value ) {
    section = fixName( section );
    name = fixName( name );
    Map<String, String> map = sections.computeIfAbsent(section, k -> new LinkedHashMap<>());
    map.put( name, value );
  }

  private static String fixName( String name ) {
    return( name == null ? DEFAULT_NAME : name.trim() );
  }

}
