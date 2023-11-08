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
package org.apache.knox.gateway.identityasserter.regex.filter;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexTemplate {

  private static Pattern directPattern = Pattern.compile( "\\{(\\[?\\d+?\\]?)\\}" );
  private static Pattern indirectPattern = Pattern.compile( "\\[(\\d+?)\\]" );

  Pattern inputPattern;
  String outputTemplate;
  Map<String,String> lookupTable;
  boolean useOriginalOnLookupFailure;

  public RegexTemplate( String regex, String template ) {
    this( regex, template, null, false );
  }

  public RegexTemplate( String regex, String template, Map<String,String> map, boolean useOriginalOnLookupFailure ) {
    this.inputPattern = Pattern.compile( regex );
    this.outputTemplate = template;
    this.lookupTable = map;
    this.useOriginalOnLookupFailure = useOriginalOnLookupFailure;
  }

  public String apply( String input ) {
    String output = outputTemplate;
    Matcher inputMatcher = inputPattern.matcher( input );
    if( inputMatcher.find() ) {
      output = expandTemplate( inputMatcher, output );
    }
    return output;
  }

  private String expandTemplate( Matcher inputMatcher, String output ) {
    Matcher directMatcher = directPattern.matcher( output );
    while( directMatcher.find() ) {
      String lookupKey = null;
      String lookupValue = null;
      String lookupStr = directMatcher.group( 1 );
      Matcher indirectMatcher = indirectPattern.matcher( lookupStr );
      if( indirectMatcher.find() ) {
        lookupStr = indirectMatcher.group( 1 );
        int lookupIndex = Integer.parseInt( lookupStr );
        if( lookupTable != null ) {
          lookupKey = inputMatcher.group( lookupIndex );
          lookupValue = lookupTable.get( lookupKey );
        }
      } else {
        int lookupIndex = Integer.parseInt( lookupStr );
        lookupValue = inputMatcher.group( lookupIndex );
      }
      String replaceWith = this.useOriginalOnLookupFailure ? lookupKey : "" ;
      output = directMatcher.replaceFirst( lookupValue == null ? replaceWith : lookupValue );
      directMatcher = directPattern.matcher( output );
    }
    return output;
  }

}
