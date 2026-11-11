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
package org.apache.knox.gateway.filter.rewrite.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterApplyDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterApplyMatch;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterContentDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterPathDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;

import jakarta.activation.MimeType;

public class UrlRewriteUtil {

  public static String pickFirstRuleWithEqualsIgnoreCasePathMatch( UrlRewriteFilterContentDescriptor config, String name ) {
    String rule = "*";
    if( config != null && !config.getSelectors().isEmpty() && name != null ) {
      rule = "";
      for( UrlRewriteFilterPathDescriptor selector : config.getSelectors() ) {
        if( name.equalsIgnoreCase( selector.path() ) ) {
          if( selector instanceof UrlRewriteFilterApplyDescriptor) {
            rule = ((UrlRewriteFilterApplyDescriptor)selector).rule();
          }
          break;
        }
      }
    }
    return rule;
  }

  public static UrlRewriteFilterContentDescriptor getRewriteFilterConfig(
      UrlRewriteRulesDescriptor config, String filterName, MimeType mimeType ) {
    UrlRewriteFilterContentDescriptor filterContentConfig = null;
    if( config != null ) {
      UrlRewriteFilterDescriptor filterConfig = config.getFilter( filterName );
      if( filterConfig != null ) {
        filterContentConfig = filterConfig.getContent( mimeType );
      }
    }
    return filterContentConfig;
  }

  public static String filterJavaScript( String inputValue, UrlRewriteFilterContentDescriptor config,
      UrlRewriteFilterReader filterReader, UrlRewriteFilterPathDescriptor.Compiler<Pattern> regexCompiler ) {
    return filterMatches( inputValue, config, filterReader, regexCompiler, false );
  }

  /**
   * Rewrites every match of the applies that are configured with match="partial", the rest of the input is left
   * untouched. Applies with match="whole" have to match a whole value, they are not used here.
   */
  public static String filterPartialMatches( String inputValue, UrlRewriteFilterContentDescriptor config,
      UrlRewriteFilterReader filterReader, UrlRewriteFilterPathDescriptor.Compiler<Pattern> regexCompiler ) {
    return filterMatches( inputValue, config, filterReader, regexCompiler, true );
  }

  public static boolean hasPartialMatchApply( UrlRewriteFilterContentDescriptor config ) {
    if( config != null ) {
      for( UrlRewriteFilterPathDescriptor selector : config.getSelectors() ) {
        if( selector instanceof UrlRewriteFilterApplyDescriptor && ((UrlRewriteFilterApplyDescriptor)selector).match() == UrlRewriteFilterApplyMatch.PARTIAL ) {
          return true;
        }
      }
    }
    return false;
  }

  /*
   * The applies are processed one after the other and every apply searches the output of the previous one, so an
   * apply can match something that was produced by the rewrite of an earlier apply.
   */
  private static String filterMatches( String inputValue, UrlRewriteFilterContentDescriptor config,
      UrlRewriteFilterReader filterReader, UrlRewriteFilterPathDescriptor.Compiler<Pattern> regexCompiler,
      boolean partialOnly ) {
    StringBuilder tbuff = new StringBuilder();
    StringBuffer sbuff = new StringBuffer();
    sbuff.append( inputValue );
    if( config != null && !config.getSelectors().isEmpty() ) {
      for( UrlRewriteFilterPathDescriptor selector : config.getSelectors() ) {
        if ( selector instanceof UrlRewriteFilterApplyDescriptor ) {
          UrlRewriteFilterApplyDescriptor apply = (UrlRewriteFilterApplyDescriptor)selector;
          if( partialOnly && apply.match() != UrlRewriteFilterApplyMatch.PARTIAL ) {
            continue;
          }
          Matcher matcher = apply.compiledPath( regexCompiler ).matcher( sbuff );
          int index = 0;
          while ( matcher.find() ) {
            int start = matcher.start();
            int end = matcher.end();
            if ( start != -1 && end != -1 ) {
              tbuff.append( sbuff, index, start );
              String value = matcher.group();
              value = filterReader.filterValueString( null, value, apply.rule() );
              tbuff.append(value);
              index = end;
            }
          }
          tbuff.append( sbuff, index, sbuff.length() );
          sbuff.setLength( 0 );
          sbuff.append( tbuff );
          tbuff.setLength( 0 );
        }
      }
    }
    return sbuff.toString();
  }

}
