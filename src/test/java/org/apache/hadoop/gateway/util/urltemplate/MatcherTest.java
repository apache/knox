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


import org.junit.Test;

import java.net.URISyntaxException;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

public class MatcherTest {

  @Test
  public void testMatch() throws Exception {
    Matcher<String> matcher;
    String pattern, input;
    Template patternTemplate, inputTemplate;
    Matcher.Match<String> match;

    matcher = new Matcher<String>();
    pattern = "path";
    patternTemplate = Parser.parse( pattern );
    matcher.add( patternTemplate, pattern );
    input = "path";
    inputTemplate = Parser.parse( input );
    match = matcher.match( inputTemplate );
    assertThat( match.getTemplate(), sameInstance( patternTemplate ) );
    assertThat( match.getValue(), equalTo( pattern ) );

    matcher = new Matcher<String>();
    pattern = "/path";
    patternTemplate = Parser.parse( pattern );
    matcher.add( patternTemplate, pattern );
    input = "/path";
    inputTemplate = Parser.parse( input );
    match = matcher.match( inputTemplate );
    assertThat( match.getTemplate(), sameInstance( patternTemplate ) );
    assertThat( match.getValue(), equalTo( pattern ) );

    matcher = new Matcher<String>();
    pattern = "path/path";
    patternTemplate = Parser.parse( pattern );
    matcher.add( patternTemplate, pattern );
    input = "path/path";
    inputTemplate = Parser.parse( input );
    match = matcher.match( inputTemplate );
    assertThat( match.getTemplate(), sameInstance( patternTemplate ) );
    assertThat( match.getValue(), equalTo( pattern ) );

    matcher = new Matcher<String>();
    pattern = "*/path";
    patternTemplate = Parser.parse( pattern );
    matcher.add( patternTemplate, pattern );
    input = "pathA/path";
    inputTemplate = Parser.parse( input );
    match = matcher.match( inputTemplate );
    assertThat( match.getTemplate(), sameInstance( patternTemplate ) );
    assertThat( match.getValue(), equalTo( pattern ) );

    matcher = new Matcher<String>();
    pattern = "**/path";
    patternTemplate = Parser.parse( pattern );
    matcher.add( patternTemplate, pattern );
    input = "pathA/pathB/path";
    inputTemplate = Parser.parse( input );
    match = matcher.match( inputTemplate );
    assertThat( match.getTemplate(), sameInstance( patternTemplate ) );
    assertThat( match.getValue(), equalTo( pattern ) );

    matcher = new Matcher<String>();
    pattern = "/";
    patternTemplate = Parser.parse( pattern );
    matcher.add( patternTemplate, pattern );
    input = "/";
    inputTemplate = Parser.parse( input );
    match = matcher.match( inputTemplate );
    assertThat( match.getTemplate(), sameInstance( patternTemplate ) );
    assertThat( match.getValue(), equalTo( pattern ) );

    matcher = new Matcher<String>();
    pattern = "";
    patternTemplate = Parser.parse( pattern );
    matcher.add( patternTemplate, pattern );
    input = "";
    inputTemplate = Parser.parse( input );
    match = matcher.match( inputTemplate );
    assertThat( match.getTemplate(), sameInstance( patternTemplate ) );
    assertThat( match.getValue(), equalTo( pattern ) );
  }

  @Test
  public void testVariousPatterns() throws URISyntaxException {
    Matcher<String> matcher = new Matcher<String>();
    matcher.add( Parser.parse( "/webhdfs" ), "/webhdfs" );
    matcher.add( Parser.parse( "/webhdfs/dfshealth.jsp" ), "/webhdfs/dfshealth.jsp" );
    matcher.add( Parser.parse( "/webhdfs/*.jsp" ), "/webhdfs/*.jsp" );
    matcher.add( Parser.parse( "/webhdfs/other.jsp" ), "/webhdfs/other.jsp" );
    matcher.add( Parser.parse( "/webhdfs/*" ), "/webhdfs/*" );
    matcher.add( Parser.parse( "/webhdfs/**" ), "/webhdfs/**" );
    matcher.add( Parser.parse( "/webhdfs/v1/**" ), "/webhdfs/v1/**" );
    matcher.add( Parser.parse( "/webhdfs/**/middle/*.xml" ), "/webhdfs/**/middle/*.xml" );

    assertValidMatch( matcher, "/webhdfs", "/webhdfs" );
    assertValidMatch( matcher, "/webhdfs/dfshealth.jsp", "/webhdfs/dfshealth.jsp" );
    assertValidMatch( matcher, "/webhdfs/v1", "/webhdfs/*" );
    assertValidMatch( matcher, "/webhdfs/some.jsp", "/webhdfs/*.jsp" );
    assertValidMatch( matcher, "/webhdfs/other.jsp", "/webhdfs/other.jsp" );
    assertValidMatch( matcher, "/webhdfs/path/some.jsp", "/webhdfs/**" );
    assertValidMatch( matcher, "/webhdfs/path/middle/some.jsp", "/webhdfs/**" );
    assertValidMatch( matcher, "/webhdfs/path/middle/some.xml", "/webhdfs/**/middle/*.xml" );
    assertValidMatch( matcher, "/webhdfs/path/to/file", "/webhdfs/**" );
    assertValidMatch( matcher, "/webhdfs/v1/path/to/file", "/webhdfs/v1/**" );
  }

  @Test
  public void testStar() throws URISyntaxException {
    Matcher<String> matcher = new Matcher<String>();
    matcher.add( Parser.parse( "/webhdfs/*" ), "/webhdfs/*" );
    assertValidMatch( matcher, "/webhdfs/*", "/webhdfs/*" );
    assertValidMatch( matcher, "/webhdfs/file", "/webhdfs/*" );
    assertValidMatch( matcher, "/webhdfs/path/", "/webhdfs/*" );
    assertValidMatch( matcher, "/webhdfs/path/file", null );
    assertValidMatch( matcher, "/webhdfs/path/path/", null );
  }

  @Test
  public void testGlob() throws URISyntaxException {
    Matcher<String> matcher = new Matcher<String>();
    matcher.add( Parser.parse( "/webhdfs/**" ), "/webhdfs/**" );
    assertValidMatch( matcher, "/webhdfs/file", "/webhdfs/**" );
    assertValidMatch( matcher, "/webhdfs/path/", "/webhdfs/**" );
    assertValidMatch( matcher, "/webhdfs/path/file", "/webhdfs/**" );
    assertValidMatch( matcher, "/webhdfs/path/path/", "/webhdfs/**" );
  }

  @Test
  public void testMatrixParam() throws URISyntaxException {
    Matcher<String> matcher = new Matcher<String>();
    matcher.add( Parser.parse( "/webhdfs/**" ), "/webhdfs/**" );
    matcher.add( Parser.parse( "/webhdfs/browseDirectory.jsp;dn=*" ), "/webhdfs/browseDirectory.jsp;dn=*" );
    assertValidMatch( matcher, "/webhdfs/browseDirectory.jsp;dn=X", "/webhdfs/browseDirectory.jsp;dn=*" );
  }

  @Test
  public void testTwoGlobsAtDifferentDepths() throws URISyntaxException {
    Matcher<String> matcher = new Matcher<String>();
    matcher.add( Parser.parse( "/webhdfs/**" ), "/webhdfs/**" );
    matcher.add( Parser.parse( "/webhdfs/v1/**" ), "/webhdfs/v1/**" );
    assertValidMatch( matcher, "/webhdfs/file", "/webhdfs/**" );
    assertValidMatch( matcher, "/webhdfs/v1/file", "/webhdfs/v1/**" );

    // Reverse the put order.
    matcher = new Matcher<String>();
    matcher.add( Parser.parse( "/webhdfs/v1/**" ), "/webhdfs/v1/**" );
    matcher.add( Parser.parse( "/webhdfs/**" ), "/webhdfs/**" );
    assertValidMatch( matcher, "/webhdfs/file", "/webhdfs/**" );
    assertValidMatch( matcher, "/webhdfs/v1/file", "/webhdfs/v1/**" );
  }

  @Test
  public void testGlobsVsStarsAtSameDepth() throws URISyntaxException {
    Matcher<String> matcher = new Matcher<String>();
    matcher.add( Parser.parse( "/webhdfs/*" ), "/webhdfs/*" );
    matcher.add( Parser.parse( "/webhdfs/**" ), "/webhdfs/**" );
    assertValidMatch( matcher, "/webhdfs/file", "/webhdfs/*" );
    assertValidMatch( matcher, "/webhdfs/path/file", "/webhdfs/**" );

    // Reverse the put order.
    matcher = new Matcher<String>();
    matcher.add( Parser.parse( "/webhdfs/**" ), "/webhdfs/**" );
    matcher.add( Parser.parse( "/webhdfs/*" ), "/webhdfs/*" );
    assertValidMatch( matcher, "/webhdfs/path/file", "/webhdfs/**" );
    assertValidMatch( matcher, "/webhdfs/file", "/webhdfs/*" );
  }

  @Test
  public void testMatchingPatternsWithinPathSegments() throws URISyntaxException {
    Matcher<String> matcher = new Matcher<String>();
    matcher.add( Parser.parse( "/path/{file}" ), "default" );
    assertValidMatch( matcher, "/path/file-name", "default" );

    matcher = new Matcher<String>();
    matcher.add( Parser.parse( "/path/{file=*}" ), "*" );
    assertValidMatch( matcher, "/path/some-name", "*" );

    matcher = new Matcher<String>();
    matcher.add( Parser.parse( "/path/{more=**}" ), "**" );
    assertValidMatch( matcher, "/path/some-path/some-name", "**" );

    matcher = new Matcher<String>();
    matcher.add( Parser.parse( "/path/{regex=prefix*suffix}" ), "regex" );
    assertValidMatch( matcher, "/path/prefix-middle-suffix", "regex" );
    assertValidMatch( matcher, "/path/not-prefix-middle-suffix", null );
  }

  @Test
  public void testMatchingPatternsWithinQuerySegments() throws URISyntaxException {
    Matcher<String> matcher = new Matcher<String>();
    matcher.add( Parser.parse( "?query={param}" ), "default" );
    assertValidMatch( matcher, "?query=value", "default" );

    matcher = new Matcher<String>();
    matcher.add( Parser.parse( "?query={param=*}" ), "*" );
    assertValidMatch( matcher, "?query=some-value", "*" );

    matcher = new Matcher<String>();
    matcher.add( Parser.parse( "?query={param=**}" ), "**" );
    assertValidMatch( matcher, "?query=some-value", "**" );

    matcher = new Matcher<String>();
    matcher.add( Parser.parse( "?query={param=prefix*suffix}" ), "regex" );
    assertValidMatch( matcher, "?query=prefix-middle-suffix", "regex" );
    assertValidMatch( matcher, "?query=not-prefix-middle-suffix", null );
  }

  @Test
  public void testMatchingForTemplatesThatVaryOnlyByQueryParams() throws URISyntaxException {
    Matcher<String> matcher = new Matcher<String>();
    addTemplate( matcher, "?one={param}" );
    addTemplate( matcher, "?two={param}" );

    assertValidMatch( matcher, "?one=value", "?one={param}" );
    assertValidMatch( matcher, "?two=value", "?two={param}" );
    assertValidMatch( matcher, "?three=value", null );
    assertValidMatch( matcher, "?", null );
  }

  private void addTemplate( Matcher<String> matcher, String template ) throws URISyntaxException {
    matcher.add( Parser.parse( template ), template );
  }

  private void assertValidMatch( Matcher<String> matcher, String uri, String template ) throws URISyntaxException {
    if( template == null ) {
      assertThat( matcher.match( Parser.parse( uri ) ), nullValue() );
    } else {
      Template uriTemplate = Parser.parse( uri );
      Matcher.Match<String> match = matcher.match( uriTemplate );
      assertThat( "Expected to find a match.", match, notNullValue() );
      assertThat( match.getValue(), equalTo( template ) );
    }
  }

}
