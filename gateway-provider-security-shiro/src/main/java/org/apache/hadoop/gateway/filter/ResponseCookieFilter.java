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

package org.apache.hadoop.gateway.filter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class ResponseCookieFilter extends AbstractGatewayFilter {

  @Override
  protected void doFilter( HttpServletRequest request, HttpServletResponse response, FilterChain chain ) throws IOException, ServletException {
    ResponseWrapper responseWrapper = new ResponseWrapper( response );
    chain.doFilter( request, responseWrapper );
  }

  // inner class wraps response to prevent adding of not allowed headers
  private class ResponseWrapper extends HttpServletResponseWrapper {

    public ResponseWrapper( HttpServletResponse response ) {
      super( response );
    }

    public void addCookie( Cookie cookie ) {
      if( cookie != null && isAllowedHeaderValue( cookie.getValue() ) ) {
        super.addCookie( cookie );
      }
    }

    public void setHeader( String name, String value ) {
      if( isAllowedHeaderValue( value ) ) {
        super.setHeader( name, value );
      }
    }

    public void addHeader( String name, String value ) {
      if( isAllowedHeaderValue( value ) ) {
        super.addHeader( name, value );
      }
    }

    private boolean isAllowedHeaderValue( String value ) {
      if( value != null ) {
        for( String v : restrictedCookieValues ) {
          if( value.contains( v ) ) {
            return false;
          }
        }
      }
      return true;
    }
  }

  private final static List<String> restrictedCookieValues = new ArrayList<String>(
      Arrays.asList( "rememberMe" )
  );

}
