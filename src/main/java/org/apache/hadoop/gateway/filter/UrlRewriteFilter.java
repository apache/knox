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

import org.apache.hadoop.gateway.util.UrlRewriter;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 *
 */
public class UrlRewriteFilter extends AbstractGatewayFilter {

  //TODO: Rewrite headers that contain URLs.
  //TODO: Rewrite cookies that contain URLs.
  private UrlRewriter rewriter;

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    super.init( filterConfig );
    rewriter = new UrlRewriter();
    addRule( filterConfig.getInitParameter( "rewrite" ) );
    String rewrite;
    int i = 0;
    do {
      rewrite = filterConfig.getInitParameter( "rewrite." + i++ );
      addRule( rewrite );
    } while( rewrite != null || i == 1 );
  }

  private void addRule( String rewrite ) {
    if( rewrite != null ) {
      String[] patternFormatPair = rewrite.split( " ", 2 );
      if( patternFormatPair.length == 2 ) {
        rewriter.addRule( patternFormatPair[ 0 ], patternFormatPair[ 1 ] );
      }
    }
  }

  @Override
  protected void doFilter( HttpServletRequest request, HttpServletResponse response, FilterChain chain ) throws IOException, ServletException {
    chain.doFilter( request, new UrlRewriteResponse( getConfig(), rewriter, response ) );
  }

}
