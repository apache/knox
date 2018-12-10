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
package org.apache.knox.gateway.util.urltemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

//TODO: There are usability issues when trying to rewrite only the path portion of a fully qualified URL.
// See RewriterTeset.testRewriteUrlWithHttpServletRequestAndFilterConfig
// For example...
// sourceInput = new URI( "http://some-host:0/some-path" );
// sourcePattern = Parser.parse( "*://*:*/**" );
// targetPattern = Parser.parse( "should-not-change" );
// actualOutput = UrlRewriter.rewrite( sourceInput, sourcePattern, targetPattern, new TestResolver( config, expect ) );
// expectedOutput = new URI( "should-not-change" );
// assertThat( actualOutput, equalTo( expectedOutput ) );
// This was possible before with just the source pattern being "**".
// At a minimum, the authority portion should support a "**" glob meaning "//*:*@*:*"
public class Rewriter {

  private Matcher<Template> rules;

  public static URI rewrite( URI inputUri, Template inputTemplate, Template outputTemplate, Resolver resolver, Evaluator evaluator )
      throws URISyntaxException {
    Rewriter rewriter = new Rewriter();
    rewriter.addRule( inputTemplate, outputTemplate );
    Template inputUriTemplate = Parser.parseLiteral( inputUri.toString() );
    return rewriter.rewrite( inputUriTemplate, resolver, evaluator );
  }

  public Rewriter() {
    rules = new Matcher<>();
  }

  public void addRule( Template inputTemplate, Template outputTemplate ) {
    rules.add( inputTemplate, outputTemplate );
  }

  public URI rewrite( Template input, Resolver resolver, Evaluator evaluator )
      throws URISyntaxException {
    URI outputUri = null;
    Matcher<Template>.Match match = rules.match( input );
    Params params;
    if( match != null ) {
      if( resolver == null ) {
        params = match.getParams();
      } else {
        params = new RewriteParams( match.getParams(), resolver );
      }
      outputUri = Expander.expand( match.getValue(), params, evaluator );
    }
    return outputUri;
  }

  private static class RewriteParams implements Params {
    private Params params;
    private Resolver[] resolvers;

    RewriteParams( Params params, Resolver... resolvers ) {
      this.params = params;
      this.resolvers = resolvers;
    }

    @Override
    public Set<String> getNames() {
      return params.getNames();
    }

    @Override
    public List<String> resolve( String name ) {
      List<String> values = params.resolve( name );
      if( values == null && resolvers != null ) {
        for( Resolver resolver: resolvers ) {
          values = resolver.resolve( name );
          if( values != null ) {
            break;
          }
        }
      }
      return values;
    }

  }

}
