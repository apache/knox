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
package org.apache.hadoop.gateway.util;

import org.apache.hadoop.gateway.GatewayResources;
import org.apache.hadoop.gateway.i18n.resources.ResourcesFactory;

import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlRewriter {

  private static GatewayResources res = ResourcesFactory.get( GatewayResources.class );

  private Map<Pattern,String> rules = new LinkedHashMap<Pattern,String>();

  public void addRule( String pattern, String format ) {
    rules.put( compileUrlRegex( pattern ), format );
  }

  public String rewriteUrl( String url ) {
    return rewriteUrl( url, rules );
  }

  public static Pattern compileUrlRegex( String segment ) {
    segment = segment.replaceAll( "\\?", "\\\\?" ); // Turn '(' into '\('.
    segment = segment.replaceAll( "\\(", "\\\\(" ); // Turn '(' into '\('.
    segment = segment.replaceAll( "\\)", "\\\\)" ); // Turn '(' into '\)'.
    segment = segment.replaceAll( "\\.", "\\\\." ); // Turn '.' into '\.'.
    segment = segment.replaceAll( "\\*\\*", "|" ); // Temporarily turn '**' into '^' because that isn't allowed in a URL.
    segment = segment.replaceAll( "\\*", "(.*)" ); // Turn '*' into '.*?'.
    segment = segment.replaceAll( "\\|", "(.*)" ); // Turn '^' back into '.*?' to represent multiple directories.
    return Pattern.compile( segment );
  }

  public static String rewriteUrl( String url, Map<Pattern,String> rules ) {
    for( Map.Entry<Pattern,String> entry : rules.entrySet() ) {
      Pattern pattern = entry.getKey();
      Matcher matcher = pattern.matcher( url );
      if( matcher.matches() ) {
        String format = entry.getValue();
        return MessageFormat.format( format, Regex.toGroupArray( matcher ) );
      }
    }
    return url;
  }

  private static Pattern NAMED_PARAM_PATTERN = Pattern.compile( "\\{([^0-9\\*].*?)\\}" );

  public static String rewriteUrl(
      String sourceInput,
      String sourceTemplate,
      String targetTemplate,
      HttpServletRequest request,
      FilterConfig config ) {
    String targetOutput = targetTemplate;
    Matcher matcher = NAMED_PARAM_PATTERN.matcher( targetOutput );
    // While there are non-numeric parameters in the target template
    while( matcher.find() ) {
      String name = matcher.group(1);
      // Attempt to resolve the parameter from the request, if resolved then continue.
      String value = request.getParameter( name );
      if( value != null ) {
        targetOutput = matcher.replaceFirst( value );
        matcher = NAMED_PARAM_PATTERN.matcher( targetOutput );
        continue;
      }
      // Attempt to resolve the patameter from the config if resolved then continue.
      value = config.getInitParameter( name );
      if( value != null ) {
        targetOutput = matcher.replaceFirst( value );
        matcher = NAMED_PARAM_PATTERN.matcher( targetOutput );
        continue;
      }
      // Fail because there is an unresolvable non-numberic pattern in the target.
      throw new IllegalArgumentException( res.unableToResolveTemplateParam( name, targetTemplate ) );
    }
    // Now attempt to resolve all of the numeric params
    Pattern sourcePattern = compileUrlRegex( sourceTemplate );
    matcher = sourcePattern.matcher( sourceInput );
    if( matcher.matches() ) {
      targetOutput = MessageFormat.format( targetOutput, Regex.toGroupArray( matcher ) );
    }
    return targetOutput;
  }

}
