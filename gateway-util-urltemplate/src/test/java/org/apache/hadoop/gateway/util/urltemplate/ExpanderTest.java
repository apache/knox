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
package org.apache.hadoop.gateway.util.urltemplate;

import org.apache.hadoop.test.category.FastTests;
import org.apache.hadoop.test.category.UnitTests;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.net.URI;
import java.net.URISyntaxException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@Category( { UnitTests.class, FastTests.class } )
public class ExpanderTest {

  @Test
  public void testCompleteUrl() throws URISyntaxException {
    String text;
    Template template;
    MockParams params;
    URI expanded;

    text = "foo://username:password@example.com:8042/over/there/index.dtb?type=animal&name=narwhal#nose";
    template = Parser.parse( text );
    params = new MockParams();
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( text ) ) ;

    text = "{scheme}://{username}:{password}@{host}:{port}/{path=**}?query={queryParam}#{fragment}";
    template = Parser.parse( text );
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

    template = Parser.parse( "" );
    params = new MockParams();
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "" ) ) ;

    template = Parser.parse( "/" );
    params = new MockParams();
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "/" ) ) ;

    template = Parser.parse( "{path-name}" );
    params = new MockParams();
    params.addValue( "path-name", "path-value" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "path-value" ) ) ;

    template = Parser.parse( "/{path-name}" );
    params = new MockParams();
    params.addValue( "path-name", "path-value" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "/path-value" ) ) ;

    template = Parser.parse( "{path-name}/" );
    params = new MockParams();
    params.addValue( "path-name", "path-value" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "path-value/" ) ) ;

    template = Parser.parse( "/{path-name}/" );
    params = new MockParams();
    params.addValue( "path-name", "path-value" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "/path-value/" ) ) ;

    template = Parser.parse( "path-name" );
    params = new MockParams();
    params.addValue( "path-name", "other-path-value" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "path-name" ) ) ;

    template = Parser.parse( "/path-name" );
    params = new MockParams();
    params.addValue( "path-name", "other-path-value" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "/path-name" ) ) ;

    template = Parser.parse( "path-name/" );
    params = new MockParams();
    params.addValue( "path-name", "other-path-value" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "path-name/" ) ) ;

    template = Parser.parse( "/path-name/" );
    params = new MockParams();
    params.addValue( "path-name", "other-path-value" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "/path-name/" ) ) ;

    template = Parser.parse( "?" );
    params = new MockParams();
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "" ) ) ;

    template = Parser.parse( "?query-name={queryParam-name}" );
    params = new MockParams();
    params.addValue( "queryParam-name", "queryParam-value" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "?query-name=queryParam-value" ) ) ;

    template = Parser.parse( "?query-name-1={queryParam-name-1}&query-name-2={queryParam-name-2}" );
    params = new MockParams();
    params.addValue( "queryParam-name-1", "queryParam-value-1" );
    params.addValue( "queryParam-name-2", "queryParam-value-2" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "?query-name-1=queryParam-value-1&query-name-2=queryParam-value-2" ) ) ;

    template = Parser.parse( "?query-name=queryParam-value" );
    params = new MockParams();
    params.addValue( "queryParam-name", "other-queryParam-value" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "?query-name=queryParam-value" ) ) ;

    template = Parser.parse( "?query-name-1=queryParam-value-1&query-name-2=queryParam-value-2" );
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

    template = Parser.parse( "//host" );
    params = new MockParams();
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "//host" ) ) ;

    template = Parser.parse( "//:port" );
    params = new MockParams();
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "//:port" ) ) ;

    template = Parser.parse( "//username@" );
    params = new MockParams();
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "//username@" ) ) ;

    template = Parser.parse( "//:password@" );
    params = new MockParams();
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "//:password@" ) ) ;
  }

  @Test
  public void testPathExpansion() throws URISyntaxException {
    Template template;
    MockParams params;
    URI expanded;

    template = Parser.parse( "/a/b/c" );
    params = new MockParams();
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "/a/b/c" ) ) ;

    template = Parser.parse( "/top/{middle}/bottom" );
    params = new MockParams();
    params.addValue( "middle", "A" );
    params.addValue( "middle", "B" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "/top/A/B/bottom" ) ) ;

    template = Parser.parse( "/top/{middle=*}/bottom" );
    params = new MockParams();
    params.addValue( "middle", "A" );
    params.addValue( "middle", "B" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "/top/A/bottom" ) ) ;

    template = Parser.parse( "/top/{middle=**}/bottom" );
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

    template = Parser.parse( "?query" );
    params = new MockParams();
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "?query" ) ) ;

    template = Parser.parse( "?query={queryParam}" );
    params = new MockParams();
    params.addValue( "queryParam", "A" );
    params.addValue( "queryParam", "B" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "?query=A&query=B" ) ) ;

    template = Parser.parse( "?query={queryParam=*}" );
    params = new MockParams();
    params.addValue( "queryParam", "A" );
    params.addValue( "queryParam", "B" );
    expanded = Expander.expand( template, params, null );
    assertThat( expanded.toString(), equalTo( "?query=A" ) ) ;

    template = Parser.parse( "?query={queryParam=**}" );
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
    template = Parser.parse( text );
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

    template = Parser.parse( "{scheme}://host/{path=**}?{query=**}" );
    expandedUri = Expander.expand( template, params, null );
    assertThat( expandedUri.toString(), equalTo( "schemeA://host/pathA/pathB?query=queryA&query=queryB" ) );

    template = Parser.parse( "{scheme}://host/{path=**}?{host}&{query=**}&{**}" );
    expandedUri = Expander.expand( template, params, null );
    assertThat(
        expandedUri.toString(),
        equalTo( "schemeA://host/pathA/pathB?host=hostA&query=queryA&query=queryB&extra=extraA" ) );

    template = Parser.parse( "{scheme}://host/{path=**}?server={host}&{query=**}&{**}" );
    expandedUri = Expander.expand( template, params, null );
    assertThat(
        expandedUri.toString(),
        equalTo( "schemeA://host/pathA/pathB?server=hostA&query=queryA&query=queryB&extra=extraA" ) );

    // In this case "server-host" is treated as a param name and not found in the params so it
    // is copied.  I'm not really sure what the correct behavior should be.  My initial thinking
    // is that if something within {} isn't resolve to a param it should be dropped from the output.
    template = Parser.parse( "{scheme}://host/{path=**}?{server=host}&{query=**}&{**}" );
    expandedUri = Expander.expand( template, params, null );
    assertThat(
        expandedUri.toString(),
        equalTo( "schemeA://host/pathA/pathB?server=host&query=queryA&query=queryB&host=hostA&extra=extraA" ) );
  }

}
