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
package org.apache.hadoop.gateway.filter.rewrite.impl;

import org.apache.hadoop.gateway.filter.rewrite.i18n.UrlRewriteMessages;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteResolver;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.util.urltemplate.Resolver;

import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class UrlRewriteFunctionResolver implements UrlRewriteResolver {

  private static final UrlRewriteMessages LOG = MessagesFactory.get( UrlRewriteMessages.class );

  private Map<String,UrlRewriteFunctionProcessor> functions;
  private Resolver delegate;
  private enum TokenType { FUNCTION, DIRECT_PARAMETER, INDIRECT_PARAMETER}

  public UrlRewriteFunctionResolver( Map<String,UrlRewriteFunctionProcessor> functions, Resolver delegate ) {
    this.functions = functions;
    this.delegate = delegate;
  }

  @Override
  public String resolve( UrlRewriteContext context, String parameter ) throws Exception {
//    System.out.println( "RESOLVE: " + parameter );
    String value = null;
    String function = null;
    if( parameter != null && parameter.startsWith( "$" ) ) {
      StringTokenizer parser = new StringTokenizer( parameter, "$()[]", true );
      parser.nextToken(); // Consume the $
      TokenType tokenType = TokenType.FUNCTION;
      while( parser.hasMoreTokens() ) {
        String token = parser.nextToken();
        //Note: Currently () implies a variable parameter and [] a constant parameter.
        if( "(".equals( token ) ) {
          tokenType = TokenType.INDIRECT_PARAMETER;
        } else if( "[".equals( token ) ) {
          tokenType = TokenType.DIRECT_PARAMETER;
        } else if ( ")".equals( token ) ) {
          break;
        } else if( "]".equals( token ) ) {
          break;
        } else if( tokenType.equals( TokenType.FUNCTION ) ) {
          function = token;
        } else {
          parameter = token;
        }
      }
      if( tokenType.equals( TokenType.INDIRECT_PARAMETER ) ) {
        value = getFirstValue( context.getParameters().resolve( parameter ) );
        if( value != null ) {
          parameter = value;
        } else {
          parameter = invokeDelegate( parameter );
        }
      }
      value = invokeFunction( context, function, parameter );
    } else {
      value = getFirstValue( delegate.resolve( parameter ) );
    }
    return value;
  }

  private String invokeFunction( UrlRewriteContext context, String function, String parameter ) {
    String value = parameter;
    if( function != null ) {
      UrlRewriteResolver resolver = functions.get( function );
      if( resolver != null ) {
        try {
          value = resolver.resolve( context, parameter );
        } catch( Exception e ) {
          LOG.failedToInvokeRewriteFunction( function, e );
          // Ignore it and use the original parameter values.
        }
      }
    }
    return value;
  }

  private String invokeDelegate( String parameter ) {
    List<String> values = delegate.resolve( parameter );
    String value = getFirstValue( values );
    return value;
  }

  private static String getFirstValue( List<String> values ) {
    String value = null;
    if( values != null && !values.isEmpty() ) {
      value = values.get( 0 );
    }
    return value;
  }

}
