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

import org.apache.knox.test.category.FastTests;
import org.apache.knox.test.category.UnitTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@Category( { UnitTests.class, FastTests.class } )
public class ExpanderTest {

  @Test
  public void testHostAndPortOnlyExpansionBugKnox381() throws Exception {
    String text = "{host}:{port}";
    Template template = Parser.parseTemplate( text );
    MockParams params = new MockParams();
    params.addValue( "host", "test-host" );
    params.addValue( "port", "777" );
    URI expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "test-host:777" ) ) ;
  }

  @Test
  public void testCompleteUrl() throws URISyntaxException {
    String text;
    Template template;
    MockParams params;
    URI expanded;

    text = "foo://username:password@example.com:8042/over/there/index.dtb?type=animal&name=narwhal#nose";
    template = Parser.parseLiteral( text );
    params = new MockParams();
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( text ) ) ;

    text = "{scheme}://{username}:{password}@{host}:{port}/{path=**}?query={queryParam}#{fragment}";
    template = Parser.parseTemplate( text );
    params = new MockParams();
    params.addValue( "scheme", "http" );
    params.addValue( "username", "horton" );
    params.addValue( "password", "hadoop" );
    params.addValue( "host", "hortonworks.com" );
    params.addValue( "port", "8888" );
    params.addValue( "path", "top" );
    params.addValue( "path", "mid" );
    params.addValue( "path", "bot" );
    params.addValue( "path", "file" );
    params.addValue( "queryParam", "new-value" );
    params.addValue( "fragment", "fragment" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "http://horton:hadoop@hortonworks.com:8888/top/mid/bot/file?query=new-value#fragment" ) ) ;
  }

  @Test
  public void testBasicExpansion() throws Exception {
    Template template;
    MockParams params;
    URI expanded;

    template = Parser.parseTemplate( "" );
    params = new MockParams();
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "" ) ) ;

    template = Parser.parseTemplate( "/" );
    params = new MockParams();
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "/" ) ) ;

    template = Parser.parseTemplate( "{path-name}" );
    params = new MockParams();
    params.addValue( "path-name", "path-value" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "path-value" ) ) ;

    template = Parser.parseTemplate( "/{path-name}" );
    params = new MockParams();
    params.addValue( "path-name", "path-value" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "/path-value" ) ) ;

    template = Parser.parseTemplate( "{path-name}/" );
    params = new MockParams();
    params.addValue( "path-name", "path-value" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "path-value/" ) ) ;

    template = Parser.parseTemplate( "/{path-name}/" );
    params = new MockParams();
    params.addValue( "path-name", "path-value" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "/path-value/" ) ) ;

    template = Parser.parseTemplate( "path-name" );
    params = new MockParams();
    params.addValue( "path-name", "other-path-value" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "path-name" ) ) ;

    template = Parser.parseTemplate( "/path-name" );
    params = new MockParams();
    params.addValue( "path-name", "other-path-value" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "/path-name" ) ) ;

    template = Parser.parseTemplate( "path-name/" );
    params = new MockParams();
    params.addValue( "path-name", "other-path-value" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "path-name/" ) ) ;

    template = Parser.parseTemplate( "/path-name/" );
    params = new MockParams();
    params.addValue( "path-name", "other-path-value" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "/path-name/" ) ) ;

    template = Parser.parseTemplate( "?" );
    params = new MockParams();
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "" ) ) ;

    template = Parser.parseTemplate( "?query-name={queryParam-name}" );
    params = new MockParams();
    params.addValue( "queryParam-name", "queryParam-value" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "?query-name=queryParam-value" ) ) ;

    template = Parser.parseTemplate( "?query-name-1={queryParam-name-1}&query-name-2={queryParam-name-2}" );
    params = new MockParams();
    params.addValue( "queryParam-name-1", "queryParam-value-1" );
    params.addValue( "queryParam-name-2", "queryParam-value-2" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "?query-name-1=queryParam-value-1&query-name-2=queryParam-value-2" ) ) ;

    template = Parser.parseTemplate( "?query-name=queryParam-value" );
    params = new MockParams();
    params.addValue( "queryParam-name", "other-queryParam-value" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "?query-name=queryParam-value" ) ) ;

    template = Parser.parseTemplate( "?query-name-1=queryParam-value-1&query-name-2=queryParam-value-2" );
    params = new MockParams();
    params.addValue( "queryParam-name-1", "other-queryParam-value-1" );
    params.addValue( "queryParam-name-2", "other-queryParam-value-2" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "?query-name-1=queryParam-value-1&query-name-2=queryParam-value-2" ) ) ;
  }

// Can't create a URI with just a scheme: new URI( "http:" )
//  @Test
//  public void testSchemeExpansion() throws URISyntaxException {
//    Template template;
//    Params params;
//    URI expanded;
//
//    template = Parser.parse( "scheme:" );
//    params = new Params();
//    expanded = Expander.expand( template, params );
//    assertThat( expanded.toString(), equalTo( "scheme:" ) ) ;
//  }

  @Test
  public void testAuthorityExpansion() throws URISyntaxException {
    Template template;
    MockParams params;
    URI expanded;

    template = Parser.parseTemplate( "//host" );
    params = new MockParams();
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "//host" ) ) ;

    template = Parser.parseTemplate( "//:port" );
    params = new MockParams();
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "//:port" ) ) ;

    template = Parser.parseTemplate( "//username@" );
    params = new MockParams();
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "//username@" ) ) ;

    template = Parser.parseTemplate( "//:password@" );
    params = new MockParams();
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "//:password@" ) ) ;
  }

  @Test
  public void testPathExpansion() throws URISyntaxException {
    Template template;
    MockParams params;
    URI expanded;

    template = Parser.parseTemplate( "/a/b/c" );
    params = new MockParams();
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "/a/b/c" ) ) ;

    template = Parser.parseTemplate( "/top/{middle}/bottom" );
    params = new MockParams();
    params.addValue( "middle", "A" );
    params.addValue( "middle", "B" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "/top/A/B/bottom" ) ) ;

    template = Parser.parseTemplate( "/top/{middle=*}/bottom" );
    params = new MockParams();
    params.addValue( "middle", "A" );
    params.addValue( "middle", "B" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "/top/A/bottom" ) ) ;

    template = Parser.parseTemplate( "/top/{middle=**}/bottom" );
    params = new MockParams();
    params.addValue( "middle", "A" );
    params.addValue( "middle", "B" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "/top/A/B/bottom" ) ) ;
  }

  @Test
  public void testQueryExpansion() throws URISyntaxException {
    Template template;
    MockParams params;
    URI expanded;

    template = Parser.parseTemplate( "?query" );
    params = new MockParams();
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "?query" ) ) ;

    template = Parser.parseTemplate( "?query={queryParam}" );
    params = new MockParams();
    params.addValue( "queryParam", "A" );
    params.addValue( "queryParam", "B" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "?query=A&query=B" ) ) ;

    template = Parser.parseTemplate( "?query={queryParam=*}" );
    params = new MockParams();
    params.addValue( "queryParam", "A" );
    params.addValue( "queryParam", "B" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "?query=A" ) ) ;

    template = Parser.parseTemplate( "?query={queryParam=**}" );
    params = new MockParams();
    params.addValue( "queryParam", "A" );
    params.addValue( "queryParam", "B" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "?query=A&query=B" ) ) ;

  }

  @Test
  public void testExtraParamHandling() throws Exception {
    String text;
    Template template;
    MockParams params;
    URI expandedUri;
    Template expandedTemplate;
    String expandedString;

    params = new MockParams();
    params.addValue(  "scheme", "schemeA"  );
    params.addValue( "host", "hostA" );
    params.addValue( "query", "queryA" );
    params.addValue( "query", "queryB" );
    params.addValue( "path", "pathA" );
    params.addValue( "path", "pathB" );
    params.addValue( "extra", "extraA" );

    text = "{scheme}://host/{path=*]?{query=*}";
    template = Parser.parseTemplate( text );
    expandedTemplate = Expander.expandToTemplate( template, params, null );
    assertThat( expandedTemplate.toString(), equalTo( "schemeA://host/{path=*]?query=queryA" ) );
    expandedString = Expander.expandToString( template, params, null );
    assertThat( expandedString, equalTo( "schemeA://host/{path=*]?query=queryA" ) );
    try {
      expandedUri = Expander.expand( template, params, null );
      fail( "Should have thrown exception" );
    } catch( URISyntaxException e ) {
      // Expected.
    }

    template = Parser.parseTemplate( "{scheme}://host/{path=**}?{query=**}" );
    expandedUri = Expander.expand( template, params, null );
    assertThat( expandedUri.toString(), equalTo( "schemeA://host/pathA/pathB?query=queryA&query=queryB" ) );

    template = Parser.parseTemplate( "{scheme}://host/{path=**}?{host}&{query=**}&{**}" );
    expandedUri = Expander.expand( template, params, null );
    assertThat(
        expandedUri.toString(),
        equalTo( "schemeA://host/pathA/pathB?host=hostA&query=queryA&query=queryB&extra=extraA" ) );

    template = Parser.parseTemplate( "{scheme}://host/{path=**}?server={host}&{query=**}&{**}" );
    expandedUri = Expander.expand( template, params, null );
    assertThat(
        expandedUri.toString(),
        equalTo( "schemeA://host/pathA/pathB?server=hostA&query=queryA&query=queryB&extra=extraA" ) );

    // In this case "server-host" is treated as a param name and not found in the params so it
    // is copied.  I'm not really sure what the correct behavior should be.  My initial thinking
    // is that if something within {} isn't resolve to a param it should be dropped from the output.
    template = Parser.parseTemplate( "{scheme}://host/{path=**}?{server=host}&{query=**}&{**}" );
    expandedUri = Expander.expand( template, params, null );
    expandedString = expandedUri.toString();
    assertThat( expandedString, containsString( "schemeA://host/pathA/pathB?" ) );
    assertThat( expandedString, containsString( "server=host" ) );
    assertThat( expandedString, containsString( "query=queryA" ) );
    assertThat( expandedString, containsString( "query=queryB" ) );
    assertThat( expandedString, containsString( "host=hostA" ) );
    assertThat( expandedString, containsString( "extra=extraA" ) );
    assertThat( expandedString, containsString( "&" ) );
  }


  @Test
  public void testBugKnox599() throws Exception {
    String text;
    Template template;
    MockParams params;
    URI expanded;

    text = "{scheme}://{host}:{port}/{path=**}?{**}";
    template = Parser.parseTemplate( text );
    params = new MockParams();
    params.addValue( "scheme", "http" );
    params.addValue( "host", "hortonworks.com" );
    params.addValue( "port", "8888" );
    params.addValue( "path", "top" );
    params.addValue( "path", "mid" );
    params.addValue( "path", "bot" );
    params.addValue( "path", "file" );
    params.addValue( "name", "value" );
    params.addValue( "flag", "" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "http://hortonworks.com:8888/top/mid/bot/file?flag=&name=value" ) ) ;

    text = "{scheme}://{host}:{port}/{path=**}?{**}";
    template = Parser.parseTemplate( text );
    params = new MockParams();
    params.addValue( "scheme", "http" );
    params.addValue( "host", "hortonworks.com" );
    params.addValue( "port", "8888" );
    params.addValue( "path", "top" );
    params.addValue( "path", "mid" );
    params.addValue( "path", "bot" );
    params.addValue( "path", "file" );
    params.addValue( "name", "value" );
    params.addValue( "flag", null );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "http://hortonworks.com:8888/top/mid/bot/file?flag&name=value" ) ) ;

    text = "{scheme}://{host}:{port}/{path=**}?{name=*}&{**}";
    template = Parser.parseTemplate( text );
    params = new MockParams();
    params.addValue( "scheme", "http" );
    params.addValue( "host", "hortonworks.com" );
    params.addValue( "port", "8888" );
    params.addValue( "path", "top" );
    params.addValue( "path", "mid" );
    params.addValue( "path", "bot" );
    params.addValue( "path", "file" );
    params.addValue( "name", null );
    params.addValue( "flag", "" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "http://hortonworks.com:8888/top/mid/bot/file?name&flag=" ) ) ;
  }

  @Test
  public void testValuelessQueryParamParsingAndExpansionBugKnox599Knox447() throws Exception {
    URI inputUri, outputUri;
    Matcher<Void> matcher;
    Matcher<Void>.Match match;
    Template input, pattern, template;
    Evaluator evaluator;

    inputUri = new URI( "https://knoxHost:8443/gateway/knoxTopo/templeton/v1/?version/hive" );

    input = Parser.parseLiteral( inputUri.toString() );
    pattern = Parser.parseTemplate( "*://*:*/**/templeton/v1/?{**}" );
    template = Parser.parseTemplate( "{$serviceUrl[WEBHCAT]}/v1/?{**}" );

    matcher = new Matcher<>();
    matcher.add( pattern, null );
    match = matcher.match( input );

    evaluator = new Evaluator() {
      @Override
      public List<String> evaluate( String function, List<String> parameters ) {
        return Arrays.asList( "https://webhcatTestHost.com:50111/templeton" );
      }
    };

    outputUri = Expander.expand( template, match.getParams(), evaluator );
    assertThat(
        outputUri.toString(),
        equalToIgnoringCase( "https://webhcatTestHost.com:50111/templeton/v1/?version%2Fhive" ) );

  }

  @Test
  public void testRedirectHeaderRewriteKnoxBug614() throws Exception {
    URI inputUri, outputUri;
    Matcher<Void> matcher;
    Matcher<Void>.Match match;
    Template input, pattern, template;
    Evaluator evaluator;

    inputUri = new URI("https://internal-host:9443/context/?user.name=admin#/login");

    input = Parser.parseLiteral( inputUri.toString() );
    pattern = Parser.parseTemplate( "*://*:*/{contextRoot}/?{**}#{fragment}" );
    template = Parser.parseTemplate( "{$gateway.url}/foo/{contextRoot}/?{**}#{fragment}" );

    matcher = new Matcher<>();
    matcher.add( pattern, null );
    match = matcher.match( input );

    evaluator = new Evaluator() {
      @Override
      public List<String> evaluate( String function, List<String> parameters ) {
        return Arrays.asList( "https://gateway-host:9443/gateway/default" );
      }
    };

    outputUri = Expander.expand( template, match.getParams(), evaluator );
    assertNotNull(outputUri.toString());
    assertThat(
        outputUri.toString(),
        is( "https://gateway-host:9443/gateway/default/foo/context/?user.name=admin#/login" ) );
  }

  @Test
  public void testLiteralsAndRegexInTemplates() throws Exception {
    String output;
    Matcher<Void> matcher;
    Matcher<Void>.Match match;
    Template input, template, rewrite;
    Evaluator evaluator;

    evaluator = new Evaluator() {
      @Override
      public List<String> evaluate( String function, List<String> parameters ) {
        return Arrays.asList( "https://gateway-host:9443/gateway/default" );
      }
    };

    // Check to make sure that you can use constants within the {}
    template = Parser.parseTemplate( "{root=ROOT}/{path=**}" );
    rewrite = Parser.parseTemplate( "{root}/{path}" );
    matcher = new Matcher<>();
    matcher.add( template, null );
    input = Parser.parseLiteral( "ROOT/child/path" );
    match = matcher.match( input );
    assertThat( match, notNullValue() );
    output = Expander.expandToString( rewrite, match.getParams(), evaluator );
    assertThat( output, is( "ROOT/child/path" ) );

    // Check to see what happens when you use the special { character within the {}.
    template = Parser.parseTemplate( "{root={}/{path=**}" );
    rewrite = Parser.parseTemplate( "{root}/{path}" );
    matcher = new Matcher<>();
    matcher.add( template, null );
    input = Parser.parseLiteral( "{/child/path" );
    match = matcher.match( input );
    assertThat( match, notNullValue() );
    output = Expander.expandToString( rewrite, match.getParams(), evaluator );
    assertThat( output, is( "{/child/path" ) );

    // Check to see what happens when you use the special } character within the {}.
    template = Parser.parseTemplate( "{root=}}/{path=**}" );
    rewrite = Parser.parseTemplate( "{root}/{path}" );
    matcher = new Matcher<>();
    matcher.add( template, null );
    input = Parser.parseLiteral( "}/child/path" );
    match = matcher.match( input );
    assertThat( match, notNullValue() );
    output = Expander.expandToString( rewrite, match.getParams(), evaluator );
    assertThat( output, is( "}/child/path" ) );

    // Check to see what happens when you use the special } character within the {}.
    template = Parser.parseTemplate( "{root={}}/{path=**}" );
    rewrite = Parser.parseTemplate( "{root}/{path}" );
    matcher = new Matcher<>();
    matcher.add( template, null );
    input = Parser.parseLiteral( "{}/child/path" );
    match = matcher.match( input );
    assertThat( match, notNullValue() );
    output = Expander.expandToString( rewrite, match.getParams(), evaluator );
    assertThat( output, is( "{}/child/path" ) );

    template = Parser.parseTemplate( "{var=${*}}/{path=**}" );
    rewrite = Parser.parseTemplate( "{var}/{path}" );

    matcher = new Matcher<>();
    matcher.add( template, null );

    input = Parser.parseLiteral( "${app.dir}/child/path" );
    match = matcher.match( input );
    assertThat( match, notNullValue() );

    output = Expander.expandToString( rewrite, match.getParams(), evaluator );
    assertThat( output, is( "${app.dir}/child/path" ) );
  }

}
