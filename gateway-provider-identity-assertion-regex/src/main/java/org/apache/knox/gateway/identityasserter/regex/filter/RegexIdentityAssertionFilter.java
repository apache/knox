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
package org.apache.knox.gateway.identityasserter.regex.filter;

import javax.security.auth.Subject;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;

import org.apache.knox.gateway.identityasserter.common.filter.CommonIdentityAssertionFilter;
import org.apache.knox.gateway.security.principal.PrincipalMappingException;

import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

public class RegexIdentityAssertionFilter extends
    CommonIdentityAssertionFilter {

  private String input = null;
  private String output = null;
  private Map<String,String> dict;
  RegexTemplate template;
  
  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    super.init(filterConfig);
    try {
      input = filterConfig.getInitParameter( "input" );
      if( input == null ) {
        input = "";
      }
      output = filterConfig.getInitParameter( "output" );
      if( output == null ) {
        output = "";
      }
      dict = loadDictionary( filterConfig.getInitParameter( "lookup" ) );
      boolean useOriginalOnLookupFailure = Boolean.parseBoolean(filterConfig.getInitParameter("use.original.on.lookup.failure"));
      template = new RegexTemplate( input, output, dict, useOriginalOnLookupFailure);
    } catch ( PrincipalMappingException e ) {
      throw new ServletException( e );
    }
  }

  public String[] mapGroupPrincipals(String mappedPrincipalName, Subject subject) {
    // Returning null will allow existing Subject group principals to remain the same
    return null;
  }

  public String mapUserPrincipal(String principalName) {
    return template.apply( principalName );
  }

  private Map<String, String> loadDictionary( String config ) throws PrincipalMappingException {
    Map<String,String> dict = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    if( config != null && !config.isEmpty() ) {
      try {
        StringTokenizer t = new StringTokenizer( config, ";" );
        while( t.hasMoreTokens() ) {
          String nvp = t.nextToken();
          String[] a = nvp.split( "=" );
          dict.put( a[0].trim(), a[1].trim() );
        }
        return dict;
      } catch( Exception e ) {
        dict.clear();
        throw new PrincipalMappingException(
            "Unable to load lookup dictionary from provided configuration: " + config +
                ".  No principal mapping will be provided.", e );
      }
    }
    return dict;
  }

}
