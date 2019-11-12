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

import java.net.URISyntaxException;
import java.util.Iterator;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@Category( { UnitTests.class, FastTests.class } )
public class ParserTest {

  private void assertBasics(
      Template template,
      boolean isAbsolute,
      boolean isDirectory,
      boolean hasQuery,
      int pathSegmentsSize,
      int querySegmentsSize ) {
    assertThat( "Incorrect isAbsolute value.", template.isAbsolute(), is( isAbsolute ) );
    assertThat( "Incorrect isDirectory value.", template.isDirectory(), is( isDirectory ) );
    assertThat( "Incorrect hasQuery value.", template.hasQuery(), is( hasQuery ) );
    assertThat( "Incorrect path size.", template.getPath().size(), is( pathSegmentsSize ) );
    assertThat( "Incorrect query size.", template.getQuery().size(), is( querySegmentsSize ) );
  }

  public void assertPath(
      Template template,
      int index,
      String paramName,
      String valuePattern ) {
    Path segment = template.getPath().get( index );
    assertThat( "Incorrect template queryParam name.", segment.getParamName(), is( paramName ) );
    assertThat( "Incorrect template value pattern.", segment.getFirstValue().getToken().getEffectivePattern(), is( valuePattern ) );
  }

  public void assertPath(
      Template template,
      int index,
      String paramName,
      String valuePattern,
      int type,
      int minRequired,
      int maxAllowed ) {
    Path segment = template.getPath().get( index );
    assertThat( "Param name wrong.", segment.getParamName(), is( paramName ) );
    assertThat( "Value pattern wrong.", segment.getFirstValue().getEffectivePattern(), is( valuePattern ) );
    assertThat( "Segment type wrong.", segment.getFirstValue().getType(), is( type ) );
//    assertThat( "Segment min required wrong.", segment.getMinRequired(), is( minRequired ) );
//    assertThat( "Segment max allowed wrong.", segment.getMaxAllowed(), is( maxAllowed ) );
  }

  public void assertQuery(
      Template template,
      String queryName,
      String paramName,
      String valuePattern ) {
    Query segment = template.getQuery().get( queryName );
    assertThat( "Query name wrong.", segment.getQueryName(), is( queryName ));
    assertThat( "Param name wrong.", segment.getParamName(), is( paramName ));
    assertThat( "value pattern wrong.", segment.getFirstValue().getToken().getEffectivePattern(), is( valuePattern ) );
  }

  public void assertQuery(
      Template template,
      String queryName,
      String paramName,
      String valuePattern,
      int type,
      int minRequired,
      int maxAllowed ) {
    Query segment = template.getQuery().get( queryName );
    assertThat( "Query name wrong.", segment.getQueryName(), is( queryName ));
    assertThat( "Param name wrong.", segment.getParamName(), is( paramName ));
    assertThat( "value pattern wrong.", segment.getFirstValue().getEffectivePattern(), is( valuePattern ));
    assertThat( "Segment type wrong.", segment.getFirstValue().getType(), is( type ) );
//    assertThat( "Segment min required wrong.", segment.getMinRequired(), is( minRequired ) );
//    assertThat( "Segment max allowed wrong.", segment.getMaxAllowed(), is( maxAllowed ) );
  }

  @Test
  public void testCompleteUrl() throws URISyntaxException {
    String text;
    Template template;

    text = "foo://username:password@example.com:8042/over/there/index.dtb?type=animal&name=narwhal#nose";
    template = Parser.parseTemplate( text );
    assertBasics( template, true, false, true, 3, 2 );
    assertThat( template.toString(), is( text ) );
  }

//  @Test
//  public void testInvalidPatterns() {
//    //TODO: ? in wrong spot.
//    //TODO: & in wrong spots.
//  }

//  @Ignore( "TODO" )
//  @Test
//  public void testRegexPatterns() {
//  }

  @Test
  public void testTemplates() throws URISyntaxException {
    String text;
    Template template;

    text = "{path}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 1, 0 );
    assertPath( template, 0, "path", "**" );
    assertThat( template.toString(), is( text ) );

    text = "{pathA}/{pathB}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 2, 0 );
    assertPath( template, 0, "pathA", "**" );
    assertPath( template, 1, "pathB", "**" );
    assertThat( template.toString(), is( text ) );

    text = "?paramA={valueA}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 1 );
    assertQuery( template, "paramA", "valueA", "**" );
    assertThat( template.toString(), is( text ) );

    text = "?paramA={valueA}&paramB={valueB}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 2 );
    assertQuery( template, "paramA", "valueA", "**" );
    assertQuery( template, "paramB", "valueB", "**" );
    assertThat( template.toString(), is( text ) );

    text = "?paramA={valueA}?paramB={valueB}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 2 );
    assertQuery( template, "paramA", "valueA", "**" );
    assertQuery( template, "paramB", "valueB", "**" );
    //assertThat( template.toString(), is( text ) );

    text = "{pathA}?paramA={valueA}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 1, 1 );
    assertPath( template, 0, "pathA", "**" );
    assertQuery( template, "paramA", "valueA", "**" );
    assertThat( template.toString(), is( text ) );
  }

  @Test
  public void testStaticPatterns() throws Exception {
    String text;
    Template template;

    text = "";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 0, 0 );
    assertThat( template.toString(), is( text ) );

    text = "/";
    template = Parser.parseTemplate( text );
    assertBasics( template, true, true, false, 0, 0 );
    assertThat( template.toString(), is( text ) );

    text = "?";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 0 );
    assertThat( template.toString(), is( text ) );

    text = "#";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 0, 0 );
    assertThat( template.hasFragment(), is( true ) );
    assertThat( template.getFragment(), nullValue() );
    assertThat( template.toString(), is( text ) );

    text = "path";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 1, 0 );
    assertPath( template, 0, "", "path" );
    assertThat( template.toString(), is( text ) );

    text = "/path";
    template = Parser.parseTemplate( text );
    assertBasics( template, true, false, false, 1, 0 );
    assertPath( template, 0, "", "path" );
    assertThat( template.toString(), is( text ) );

//    text = "//path";
//    template = Parser.parseTemplate( text );
//    assertBasics( template, true, false, false, 1, 0 );
//    assertPath( template, 0, "", "path" );

    text = "path/";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, true, false, 1, 0 );
    assertPath( template, 0, "", "path" );
    assertThat( template.toString(), is( text ) );

    text = "path//";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, true, false, 1, 0 );
    assertPath( template, 0, "", "path" );
    //IMPROVE assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );

    text = "/path/";
    template = Parser.parseTemplate( text );
    assertBasics( template, true, true, false, 1, 0 );
    assertPath( template, 0, "", "path" );
    assertThat( template.toString(), is( text ) );

//    text = "//path//";
//    template = Parser.parseTemplate( text );
//    assertBasics( template, true, true, false, 1, 0 );
//    assertPath( template, 0, "", "path" );

    text = "pathA/pathB";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 2, 0 );
    assertPath( template, 0, "", "pathA" );
    assertPath( template, 1, "", "pathB" );
    assertThat( template.toString(), is( text ) );

    text = "pathA//pathB";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 2, 0 );
    assertPath( template, 0, "", "pathA" );
    assertPath( template, 1, "", "pathB" );
    //IMPROVE assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );

    text = "/pathA/pathB";
    template = Parser.parseTemplate( text );
    assertBasics( template, true, false, false, 2, 0 );
    assertPath( template, 0, "", "pathA" );
    assertPath( template, 1, "", "pathB" );
    assertThat( template.toString(), is( text ) );

    text = "/pathA//pathB";
    template = Parser.parseTemplate( text );
    assertBasics( template, true, false, false, 2, 0 );
    assertPath( template, 0, "", "pathA" );
    assertPath( template, 1, "", "pathB" );
    //IMPROVE assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );

    text = "pathA/pathB/";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, true, false, 2, 0 );
    assertPath( template, 0, "", "pathA" );
    assertPath( template, 1, "", "pathB" );
    assertThat( template.toString(), is( text ) );

    text = "pathA//pathB/";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, true, false, 2, 0 );
    assertPath( template, 0, "", "pathA" );
    assertPath( template, 1, "", "pathB" );
    //IMPROVE assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );

    text = "/pathA/pathB/";
    template = Parser.parseTemplate( text );
    assertBasics( template, true, true, false, 2, 0 );
    assertPath( template, 0, "", "pathA" );
    assertPath( template, 1, "", "pathB" );
    assertThat( template.toString(), is( text ) );

    text = "/pathA//pathB/";
    template = Parser.parseTemplate( text );
    assertBasics( template, true, true, false, 2, 0 );
    assertPath( template, 0, "", "pathA" );
    assertPath( template, 1, "", "pathB" );
    //IMPROVE assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );

    text = "/?";
    template = Parser.parseTemplate( text );
    assertBasics( template, true, true, true, 0, 0 );
    assertThat( template.toString(), is( text ) );

//    text = "//??";
//    template = Parser.parseTemplate( text );
//    assertBasics( template, true, true, true, 0, 0 );

    text = "?name=value";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 1 );
    assertQuery( template, "name", "", "value" );
    assertThat( template.toString(), is( text ) );

    text = "?name1=value1&name2=value2";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 2 );
    assertQuery( template, "name1", "", "value1" );
    assertQuery( template, "name2", "", "value2" );
    assertThat( template.toString(), is( text ) );

    text = "?name1=value1&&name2=value2";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 2 );
    assertQuery( template, "name1", "", "value1" );
    assertQuery( template, "name2", "", "value2" );
    //IMPROVE assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );

    text = "/?name=value";
    template = Parser.parseTemplate( text );
    assertBasics( template, true, true, true, 0, 1 );
    assertQuery( template, "name", "", "value" );
    assertThat( template.toString(), is( text ) );

    text = "/?name1=value1&name2=value2";
    template = Parser.parseTemplate( text );
    assertBasics( template, true, true, true, 0, 2 );
    assertQuery( template, "name1", "", "value1" );
    assertQuery( template, "name2", "", "value2" );
    assertThat( template.toString(), is( text ) );
  }

  /*
   *  KNOX-1055
   *  In some cases & could be encoded as &amp;
   */
  @Test
  public void testEncodedChar() throws URISyntaxException {
    String text;
    Template template;

    text = "stage?id=007&amp;attempt=0";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 1, 2 );
    assertQuery( template, "id", "", "007" );
    assertQuery( template, "attempt", "", "0" );
  }

  @Test
  public void testParameterizedPathTemplatesWithWildcardAndRegex() throws URISyntaxException {
    String text;
    Template template;

    text = "{path}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 1, 0 );
    assertPath( template, 0, "path", "**", Segment.GLOB, 1, 1 );
    assertThat( template.toString(), is( text ) );

    text = "{path=static}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 1, 0 );
    assertPath( template, 0, "path", "static", Segment.STATIC, 1, 1 );
    assertThat( template.toString(), is( text ) );

    text = "{path=*}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 1, 0 );
    assertPath( template, 0, "path", "*", Segment.STAR, 1, 1 );
    assertThat( template.toString(), is( text ) );

    text = "{path=**}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 1, 0 );
    assertPath( template, 0, "path", "**", Segment.GLOB, 0, Integer.MAX_VALUE );
    assertThat( template.toString(), is( text ) );

    text = "{path=wild*card}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 1, 0 );
    assertPath( template, 0, "path", "wild*card", Segment.REGEX, 1, 1 );
    assertThat( template.toString(), is( text ) );
  }

  @Test
  public void testParameterizedQueryTemplatesWithWildcardAndRegex() throws URISyntaxException {
    String text;
    Template template;

    text = "?query={queryParam}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 1 );
    assertQuery( template, "query", "queryParam", "**", Segment.GLOB, 1, 1 );
    assertThat( template.toString(), is( text ) );

    text = "?query={queryParam=static}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 1 );
    assertQuery( template, "query", "queryParam", "static", Segment.STATIC, 1, 1 );
    assertThat( template.toString(), is( text ) );

    text = "?query={queryParam=*}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 1 );
    assertQuery( template, "query", "queryParam", "*", Segment.STAR, 1, 1 );
    assertThat( template.toString(), is( text ) );

    text = "?query={queryParam=**}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 1 );
    assertQuery( template, "query", "queryParam", "**", Segment.GLOB, 0, Integer.MAX_VALUE );
    assertThat( template.toString(), is( text ) );

    text = "?query={queryParam=wild*card}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 1 );
    assertQuery( template, "query", "queryParam", "wild*card", Segment.REGEX, 1, 1 );
    assertThat( template.toString(), is( text ) );
  }

  @Test
  public void testGlobPattern() throws URISyntaxException {
    String text;
    Template template;

    text = "**";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 1, 0 );
    assertThat( template.toString(), is( text ) );

    text = "/**";
    template = Parser.parseTemplate( text );
    assertBasics( template, true, false, false, 1, 0 );
    assertThat( template.toString(), is( text ) );

    text = "**/";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, true, false, 1, 0 );
    assertThat( template.toString(), is( text ) );

    text = "/**/";
    template = Parser.parseTemplate( text );
    assertBasics( template, true, true, false, 1, 0 );
    assertThat( template.toString(), is( text ) );

    text = "/**/path";
    template = Parser.parseTemplate( text );
    assertBasics( template, true, false, false, 2, 0 );
    assertThat( template.toString(), is( text ) );
  }

//  @Ignore( "TODO" )
//  @Test
//  public void testPatternsWithSchemeAndAuthority() throws URISyntaxException {
//    String text;
//    Template template;
//
//    text = "http:";
//    template = Parser.parse( text );
//
//    text = "http:/path";
//    template = Parser.parse( text );
//
//    text = "http://host";
//    template = Parser.parse( text );
//
//    text = "http://host/";
//    template = Parser.parse( text );
//
//    text = "http://host:80";
//    template = Parser.parse( text );
//
//    text = "http://host:80/";
//    template = Parser.parse( text );
//
//
//    text = "{scheme}:";
//    template = Parser.parse( text );
//
//    text = "{scheme}:/{path}";
//    template = Parser.parse( text );
//
//    text = "{scheme}://{host}";
//    template = Parser.parse( text );
//
//    text = "{scheme}://{host}/";
//    template = Parser.parse( text );
//
//    text = "{scheme}://{host}:{port}";
//    template = Parser.parse( text );
//
//    text = "{scheme}://{host}:{port}/";
//    template = Parser.parse( text );
//
//
//    text = "{scheme=http}:/{path=index.html}";
//    template = Parser.parse( text );
//
//    text = "{scheme=http}://{host=*.com}";
//    template = Parser.parse( text );
//
//    text = "{scheme=https}://{host=*.edu}/";
//    template = Parser.parse( text );
//
//    text = "{scheme=rmi}://{host=*}:{port=80}";
//    template = Parser.parse( text );
//
//    text = "{scheme=ftp}://{host=localhost*}:{port=*80}/";
//    template = Parser.parse( text );
//  }

  @Test
  public void testAuthority() throws URISyntaxException {
    String text;
    Template template;
    String image;

    text = "//";
    template = Parser.parseTemplate( text );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template.getUsername(), nullValue() );
    assertThat( template.getPassword(), nullValue() );
    assertThat( template.getHost(), nullValue() );
    assertThat( template.getPort(), nullValue() );
    assertThat( template.toString(), is( text ) );

    text = "//:@:";
    template = Parser.parseTemplate( text );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template.getUsername(), nullValue() );
    assertThat( template.getPassword(), nullValue() );
    assertThat( template.getHost(), nullValue() );
    assertThat( template.getPort(), nullValue() );
    //IMPROVE assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );

    text = "//host";
    template = Parser.parseTemplate( text );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template.getUsername(), nullValue() );
    assertThat( template.getPassword(), nullValue() );
    assertThat( template.getHost().getFirstValue().getOriginalPattern(), is( "host" ) );
    assertThat( template.getHost().getFirstValue().getEffectivePattern(), is( "host" ) );
    assertThat( template.getPort(), nullValue() );
    assertThat( template.toString(), is( text ) );

    text = "//@host";
    template = Parser.parseTemplate( text );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template.getUsername(), nullValue() );
    assertThat( template.getPassword(), nullValue() );
    assertThat( template.getHost().getFirstValue().getOriginalPattern(), is( "host" ) );
    assertThat( template.getHost().getFirstValue().getEffectivePattern(), is( "host" ) );
    assertThat( template.getPort(), nullValue() );
    //IMPROVE assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );

    text = "//@:80";
    template = Parser.parseTemplate( text );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template.getUsername(), nullValue() );
    assertThat( template.getPassword(), nullValue() );
    assertThat( template.getHost(), nullValue() );
    assertThat( template.getPort().getFirstValue().getOriginalPattern(), is( "80" ) );
    assertThat( template.getPort().getFirstValue().getEffectivePattern(), is( "80" ) );
    //IMPROVE assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );

    text = "//username@";
    template = Parser.parseTemplate( text );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template.getUsername().getFirstValue().getOriginalPattern(), is( "username" ) );
    assertThat( template.getUsername().getFirstValue().getEffectivePattern(), is( "username" ) );
    assertThat( template.getPassword(), nullValue() );
    assertThat( template.getHost(), nullValue() );
    assertThat( template.getPort(), nullValue() );
    assertThat( template.toString(), is( text ) );

    text = "//:password@";
    template = Parser.parseTemplate( text );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template.getUsername(), nullValue() );
    assertThat( template.getPassword().getFirstValue().getOriginalPattern(), is( "password" ) );
    assertThat( template.getPassword().getFirstValue().getEffectivePattern(), is( "password" ) );
    assertThat( template.getHost(), nullValue() );
    assertThat( template.getPort(), nullValue() );
    assertThat( template.toString(), is( text ) );

    text = "//{host}:{port}";
    template = Parser.parseTemplate( text );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template.getUsername(), nullValue() );
    assertThat( template.getPassword(), nullValue() );
    assertThat( template.getHost().getParamName(), is( "host" ) );
    assertThat( template.getHost().getFirstValue().getOriginalPattern(), nullValue() );
    assertThat( template.getHost().getFirstValue().getEffectivePattern(), is( "*" ) );
    assertThat( template.getPort().getParamName(), is( "port" ) );
    assertThat( template.getPort().getFirstValue().getOriginalPattern(), nullValue() );
    assertThat( template.getPort().getFirstValue().getEffectivePattern(), is( "*" ) );
    image = template.toString();
    assertThat( image, is( "//{host}:{port}" ) );
    assertThat( template.toString(), is( text ) );

    text = "{host}:{port}";
    template = Parser.parseTemplate( text );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template.getUsername(), nullValue() );
    assertThat( template.getPassword(), nullValue() );
    assertThat( template.getHost().getParamName(), is( "host" ) );
    assertThat( template.getHost().getFirstValue().getOriginalPattern(), nullValue() );
    assertThat( template.getHost().getFirstValue().getEffectivePattern(), is( "*" ) );
    assertThat( template.getPort().getParamName(), is( "port" ) );
    assertThat( template.getPort().getFirstValue().getOriginalPattern(), nullValue() );
    assertThat( template.getPort().getFirstValue().getEffectivePattern(), is( "*" ) );
    image = template.toString();
    assertThat( image, is( "{host}:{port}" ) );
    assertThat( template.toString(), is( text ) );
  }

  @Test
  public void testQuery() throws URISyntaxException {
    String text;
    Template template;
    Query query;
    Iterator<Segment.Value> values;
    Segment.Value value;

    text = "?queryName";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 1 );
    query = template.getQuery().get( "queryName" );
    assertThat( query, notNullValue() );
    assertThat( query.getQueryName(), is( "queryName" ) );
    assertThat( query.getParamName(), is( "" ) );
    assertThat( query.getFirstValue().getEffectivePattern(), nullValue() ); //is( "*" ) );
    assertThat( template.toString(), is( text ) );

    text = "?query=value1&query=value2";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 1 );
    query = template.getQuery().get( "query" );
    assertThat( query, notNullValue() );
    assertThat( query.getQueryName(), is( "query" ) );
    assertThat( query.getParamName(), is( "" ) );
    values = query.getValues().iterator();
    value = values.next();
    assertThat( value.getOriginalPattern(), is( "value1" ) );
    assertThat( value.getEffectivePattern(), is( "value1" ) );
    value = values.next();
    assertThat( value.getOriginalPattern(), is( "value2" ) );
    assertThat( value.getEffectivePattern(), is( "value2" ) );
    assertThat( values.hasNext(), is( false ) );
    assertThat( template.toString(), is( text ) );
  }

  @Test
  public void testFragment() throws URISyntaxException {
    String text;
    Template template;

    text = "#fragment";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 0, 0 );
    assertThat( template.hasFragment(), is( true ) );
    assertThat( template.getFragment().getFirstValue().getEffectivePattern(), is( "fragment" ) );
    assertThat( template.toString(), is( text ) );
  }

  @Test
  public void testEdgeCases() throws URISyntaxException {
    String text;
    Template template;

    text = "//";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 0, 0 );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template.toString(), is( text ) );

    text = "??";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 0 );
    //IMPROVE assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );

    text = "##";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 0, 0 );
    assertThat( template.hasFragment(), is( true ) );
    assertThat( template.getFragment().getFirstValue().getEffectivePattern(), is( "#" ) );
    assertThat( template.toString(), is( text ) );

    text = "??name=value";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 1 );
    assertQuery( template, "name", "", "value" );
    //IMPROVE assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );

    text = "//?";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 0 );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template.getUsername(), nullValue() );
    assertThat( template.getPassword(), nullValue() );
    assertThat( template.getHost(), nullValue() );
    assertThat( template.getPort(), nullValue() );
    assertThat( template.toString(), is( text ) );

    text = "//#";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 0, 0 );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template.getUsername(), nullValue() );
    assertThat( template.getPassword(), nullValue() );
    assertThat( template.getHost(), nullValue() );
    assertThat( template.getPort(), nullValue() );
    assertThat( template.toString(), is( text ) );

    text = ":";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 1, 0 );
    assertThat( template.hasScheme(), is( false ) );
    assertThat( template.getScheme(), nullValue() );
    assertThat( template.hasAuthority(), is( false ) );
    assertThat( template.getHost(), nullValue() );
    assertThat( template.getPort(), nullValue() );
    assertThat( template.getPath().get( 0 ).getFirstValue().getOriginalPattern(), is( ":" ) );
    assertThat( template.getPath().get( 0 ).getFirstValue().getEffectivePattern(), is( ":" ) );
    assertThat( template.toString(), is( ":" ) );
    assertThat( template.toString(), is( text ) );

    text = ":?";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 1, 0 );
    assertThat( template.hasScheme(), is( false ) );
    assertThat( template.getScheme(), nullValue() );
    assertThat( template.getPath().get( 0 ).getFirstValue().getOriginalPattern(), is( ":" ) );
    assertThat( template.getPath().get( 0 ).getFirstValue().getEffectivePattern(), is( ":" ) );
    assertThat( template.hasQuery(), is( true ) );
    assertThat( template.toString(), is( text ) );

    text = ":#";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 1, 0 );
    assertThat( template.hasScheme(), is( false ) );
    assertThat( template.getScheme(), nullValue() );
    assertThat( template.getPath().get( 0 ).getFirstValue().getOriginalPattern(), is( ":" ) );
    assertThat( template.getPath().get( 0 ).getFirstValue().getEffectivePattern(), is( ":" ) );
    assertThat( template.hasFragment(), is( true ) );
    assertThat( template.getFragment(), nullValue() );
    assertThat( template.toString(), is( text ) );

    text = "http:?";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 0 );
    assertThat( template.hasScheme(), is( true ) );
    assertThat( template.getScheme().getFirstValue().getOriginalPattern(), is( "http" ) );
    assertThat( template.getScheme().getFirstValue().getEffectivePattern(), is( "http" ) );
    assertThat( template.hasQuery(), is( true ) );
    assertThat( template.toString(), is( text ) );

    text = "http:#";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 0, 0 );
    assertThat( template.hasScheme(), is( true ) );
    assertThat( template.getScheme().getFirstValue().getOriginalPattern(), is( "http" ) );
    assertThat( template.getScheme().getFirstValue().getEffectivePattern(), is( "http" ) );
    assertThat( template.hasFragment(), is( true ) );
    assertThat( template.getFragment(), nullValue() );
    assertThat( template.toString(), is( text ) );

    text = "scheme:path?";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 1, 0 );
    assertThat( template.toString(), is( text ) );

    text = "scheme:path#";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 1, 0 );
    assertThat( template.hasFragment(), is( true ) );
    assertThat( template.getFragment(), nullValue() );
    assertThat( template.toString(), is( text ) );

    text = "//host/";
    template = Parser.parseTemplate( text );
    assertBasics( template, true, true, false, 0, 0 );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template.getHost().getFirstValue().getOriginalPattern(), is( "host" ) );
    assertThat( template.getHost().getFirstValue().getEffectivePattern(), is( "host" ) );
    assertThat( template.toString(), is( text ) );

    text = "//host?";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 0 );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template.getHost().getFirstValue().getOriginalPattern(), is( "host" ) );
    assertThat( template.getHost().getFirstValue().getEffectivePattern(), is( "host" ) );
    assertThat( template.toString(), is( text ) );

    text = "//host#";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 0, 0 );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template.hasFragment(), is( true ) );
    assertThat( template.getFragment(), nullValue() );
    assertThat( template.getHost().getFirstValue().getOriginalPattern(), is( "host" ) );
    assertThat( template.getHost().getFirstValue().getEffectivePattern(), is( "host" ) );
    assertThat( template.toString(), is( text ) );

    text = "///";
    template = Parser.parseTemplate( text );
    assertBasics( template, true, true, false, 0, 0 );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template.toString(), is( text ) );

    text = "//:";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 0, 0 );
    assertThat( template.hasAuthority(), is( true ) );
    //IMPROVE assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );

    text = "//?";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 0 );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template.toString(), is( text ) );

    text = "//#";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 0, 0 );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template.hasFragment(), is( true ) );
    assertThat( template.getFragment(), nullValue() );
    assertThat( template.toString(), is( text ) );

    text = "//:/";
    template = Parser.parseTemplate( text );
    assertBasics( template, true, true, false, 0, 0 );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template.getHost(), nullValue() );
    //IMPROVE assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );

    text = "//:?";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 0 );
    assertThat( template.getHost(), nullValue() );
    //IMPROVE assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );

    text = "//:#";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, false, 0, 0 );
    assertThat( template.hasFragment(), is( true ) );
    assertThat( template.getHost(), nullValue() );
    //IMPROVE assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );

    text = "///#";
    template = Parser.parseTemplate( text );
    assertBasics( template, true, true, false, 0, 0 );
    assertThat( template.hasFragment(), is( true ) );
    assertThat( template.getHost(), nullValue() );
    assertThat( template.getFragment(), nullValue() );
    assertThat( template.toString(), is( text ) );

    text = "///path#";
    template = Parser.parseTemplate( text );
    assertBasics( template, true, false, false, 1, 0 );
    assertThat( template.hasFragment(), is( true ) );
    assertThat( template.getHost(), nullValue() );
    assertThat( template.getFragment(), nullValue() );
    assertThat( template.toString(), is( text ) );

    text = "///?";
    template = Parser.parseTemplate( text );
    assertBasics( template, true, true, true, 0, 0 );
    assertThat( template.getHost(), nullValue() );
    assertThat( template.getFragment(), nullValue() );
    assertThat( template.toString(), is( text ) );

    text = "///path?";
    template = Parser.parseTemplate( text );
    assertBasics( template, true, false, true, 1, 0 );
    assertThat( template.getHost(), nullValue() );
    assertThat( template.getFragment(), nullValue() );
    assertThat( template.toString(), is( text ) );
  }

  @Test
  public void testQueryRemainder() throws URISyntaxException {
    String text;
    Template template;
    Query query;

    text = "?*";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 0 );
    query = template.getExtra();
    assertThat( query, notNullValue() );
    assertThat( query.getQueryName(), is( "*" ) );
    assertThat( query.getParamName(), is( "" ) );
    assertThat( query.getFirstValue().getOriginalPattern(), nullValue() );
    assertThat( query.getFirstValue().getEffectivePattern(), nullValue() ); //is( "*" ) );
    assertThat( template.toString(), is( text ) );

    text = "?**";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 0 );
    query = template.getExtra();
    assertThat( query, notNullValue() );
    assertThat( query.getQueryName(), is( "**" ) );
    assertThat( query.getParamName(), is( "" ) );
    assertThat( query.getFirstValue().getOriginalPattern(), nullValue() );
    assertThat( query.getFirstValue().getEffectivePattern(), nullValue() ); //is( "*" ) );
    assertThat( template.toString(), is( text ) );

    text = "?{*}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 0 );
    query = template.getExtra();
    assertThat( query, notNullValue() );
    assertThat( query.getQueryName(), is( "*" ) );
    assertThat( query.getParamName(), is( "*" ) );
    assertThat( query.getFirstValue().getOriginalPattern(), nullValue() );
    assertThat( query.getFirstValue().getEffectivePattern(), is( "**" ) );
    assertThat( template.toString(), is( text ) );

    text = "?{**}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 0 );
    query = template.getExtra();
    assertThat( query, notNullValue() );
    assertThat( query.getQueryName(), is( "**" ) );
    assertThat( query.getParamName(), is( "**" ) );
    assertThat( query.getFirstValue().getOriginalPattern(), nullValue() );
    assertThat( query.getFirstValue().getEffectivePattern(), is( "**" ) );
    assertThat( template.toString(), is( text ) );

    text = "?*={*}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 0 );
    query = template.getExtra();
    assertThat( query, notNullValue() );
    assertThat( query.getQueryName(), is( "*" ) );
    assertThat( query.getParamName(), is( "*" ) );
    assertThat( query.getFirstValue().getOriginalPattern(), nullValue() );
    assertThat( query.getFirstValue().getEffectivePattern(), is( "**" ) );
    //IMPROVE    assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );

    text = "?**={**}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 0 );
    query = template.getExtra();
    assertThat( query, notNullValue() );
    assertThat( query.getQueryName(), is( "**" ) );
    assertThat( query.getParamName(), is( "**" ) );
    assertThat( query.getFirstValue().getOriginalPattern(), nullValue() );
    assertThat( query.getFirstValue().getEffectivePattern(), is( "**" ) );
    //IMPROVE    assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );

    text = "?**={**=**}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 0 );
    query = template.getExtra();
    assertThat( query, notNullValue() );
    assertThat( query.getQueryName(), is( "**" ) );
    assertThat( query.getParamName(), is( "**" ) );
    assertThat( query.getFirstValue().getOriginalPattern(), is( "**" ) );
    assertThat( query.getFirstValue().getEffectivePattern(), is( "**" ) );
    //IMPROVE assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );
  }

  @Test
  public void testSimplifiedQuerySyntax() throws URISyntaxException {
    String text;
    Template template;
    Query query;

    text = "?{queryParam}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 1 );
    query = template.getQuery().get( "queryParam" );
    assertThat( query, notNullValue() );
    assertThat( query.getQueryName(), is( "queryParam" ) );
    assertThat( query.getParamName(), is( "queryParam" ) );
    assertThat( query.getFirstValue().getOriginalPattern(), nullValue() );
    assertThat( query.getFirstValue().getEffectivePattern(), is( "**" ) );
    //IMPROVE  assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );

    text = "?{queryParam=value}";
    template = Parser.parseTemplate( text );
    assertBasics( template, false, false, true, 0, 1 );
    query = template.getQuery().get( "queryParam" );
    assertThat( query, notNullValue() );
    assertThat( query.getQueryName(), is( "queryParam" ) );
    assertThat( query.getParamName(), is( "queryParam" ) );
    assertThat( query.getFirstValue().getOriginalPattern(), is( "value" ) );
    assertThat( query.getFirstValue().getEffectivePattern(), is( "value" ) );
    //IMPROVE assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );
  }

  @Test
  public void testAllWildcardUseCases() throws URISyntaxException {
    String text;
    Template template;

    text = "*://*:*/**?**";
    template = Parser.parseTemplate( text );
    assertThat( template, notNullValue() );
    assertThat( template.toString(), is( text ) );

    text = "*://*:*/**/path?{**}";
    template = Parser.parseTemplate( text );
    assertThat( template, notNullValue() );
    assertThat( template.toString(), is( text ) );

    text = "*://*:*/**/webhdfs/v1/?{**}";
    template = Parser.parseTemplate( text );
    assertThat( template, notNullValue() );
    assertThat( template.toString(), is( text ) );
  }

  @Test
  public void testQueryNameWithoutValue() throws URISyntaxException {
    String text;
    Template template;
    String string;

    text = "*://*:*/**?X";
    template = Parser.parseTemplate( text );
    assertThat( template.hasScheme(), is( true ) );
    assertThat( template.getScheme().getParamName(), is( "" ) );
    assertThat( template.getScheme().getFirstValue().getOriginalPattern(), is( "*" ) );
    assertThat( template.getScheme().getFirstValue().getEffectivePattern(), is( "*" ) );
    assertThat( template.getHost().getParamName(), is( "" ) );
    assertThat( template.getHost().getFirstValue().getOriginalPattern(), is( "*" ) );
    assertThat( template.getHost().getFirstValue().getEffectivePattern(), is( "*" ) );
    assertThat( template.getPort().getParamName(), is( "" ) );
    assertThat( template.getPort().getFirstValue().getOriginalPattern(), is( "*" ) );
    assertThat( template.getPort().getFirstValue().getEffectivePattern(), is( "*" ) );
    assertThat( template.getPath().size(), is( 1 ) );
    assertThat( template.getPath().get( 0 ).getParamName(), is( "" ) );
    assertThat( template.getPath().get( 0 ).getFirstValue().getOriginalPattern(), is( "**" ) );
    assertThat( template.getPath().get( 0 ).getFirstValue().getEffectivePattern(), is( "**" ) );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template, notNullValue() );
    assertThat( template.getQuery().get( "X" ), notNullValue() );
    string = Expander.expandToString( template, null, null );
    assertThat( string, is( text ) );
    assertThat( template.toString(), is( text ) );

    text = "*://*:*/**?X=";
    template = Parser.parseTemplate( text );
    assertThat( template, notNullValue() );
    assertThat( template.getQuery().get( "X" ), notNullValue() );
    string = Expander.expandToString( template, null, null );
    assertThat( string, is( "*://*:*/**?X" ) );
    //IMPROVE assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );

    text = "http://localhost:62142/gateway/cluster/webhdfs/data/v1/tmp/GatewayWebHdfsFuncTest/testBasicHdfsUseCase/dir/file?aG9zdD1sb2NhbGhvc3QmcG9ydD02MjEzOSZvcD1DUkVBVEUmdXNlci5uYW1lPWhkZnM";
    template = Parser.parseTemplate( text );
    assertThat( template, notNullValue() );
    assertThat( template.getQuery().get( "aG9zdD1sb2NhbGhvc3QmcG9ydD02MjEzOSZvcD1DUkVBVEUmdXNlci5uYW1lPWhkZnM" ), notNullValue() );
    string = Expander.expandToString( template, null, null );
    assertThat( string, is( "http://localhost:62142/gateway/cluster/webhdfs/data/v1/tmp/GatewayWebHdfsFuncTest/testBasicHdfsUseCase/dir/file?aG9zdD1sb2NhbGhvc3QmcG9ydD02MjEzOSZvcD1DUkVBVEUmdXNlci5uYW1lPWhkZnM" ) );
    assertThat( template.toString(), is( text ) );

    text = "http://localhost:62142/gateway/cluster/webhdfs/data/v1/tmp/GatewayWebHdfsFuncTest/testBasicHdfsUseCase/dir/file?aG9zdD1sb2NhbGhvc3QmcG9ydD02MjEzOSZvcD1DUkVBVEUmdXNlci5uYW1lPWhkZnM=";
    template = Parser.parseTemplate( text );
    assertThat( template, notNullValue() );
    assertThat( template.getQuery().get( "aG9zdD1sb2NhbGhvc3QmcG9ydD02MjEzOSZvcD1DUkVBVEUmdXNlci5uYW1lPWhkZnM" ), notNullValue() );
    string = Expander.expandToString( template, null, null );
    assertThat( string, is( "http://localhost:62142/gateway/cluster/webhdfs/data/v1/tmp/GatewayWebHdfsFuncTest/testBasicHdfsUseCase/dir/file?aG9zdD1sb2NhbGhvc3QmcG9ydD02MjEzOSZvcD1DUkVBVEUmdXNlci5uYW1lPWhkZnM" ) );
    //IMPROVE assertThat( template.toString(), is( text ) );
    assertThat( template.getPattern(), is( text ) );
  }

  @Test
  public void testTemplateWithOnlyAuthority() throws Exception {
    String text;
    Template template;

    text = "test-host:42";
    template = Parser.parseTemplate( text );
    assertThat( template.hasScheme(), is( false ) );
    assertThat( template.getHost().getFirstValue().getOriginalPattern(), is( "test-host" ) );
    assertThat( template.getHost().getFirstValue().getEffectivePattern(), is( "test-host" ) );
    assertThat( template.getPort().getFirstValue().getOriginalPattern(), is( "42" ) );
    assertThat( template.getPort().getFirstValue().getEffectivePattern(), is( "42" ) );
    assertThat( template.toString(), is( text ) );

    text = "{test-host}:{test-port}";
    template = Parser.parseTemplate( text );
    assertThat( template.hasScheme(), is( false ) );
    assertThat( template.getHost().getParamName(), is( "test-host" ) );
    assertThat( template.getHost().getFirstValue().getToken().getOriginalPattern(), nullValue() );
    assertThat( template.getHost().getFirstValue().getToken().getEffectivePattern(), is( "*" ) );
    assertThat( template.getPort().getParamName(), is( "test-port" ) );
    assertThat( template.getHost().getFirstValue().getToken().getOriginalPattern(), nullValue() );
    assertThat( template.getPort().getFirstValue().getToken().getEffectivePattern(), is( "*" ) );
    assertThat( template.toString(), is( text ) );
  }

  @Test
  public void testTemplateWithoutAuthority() throws Exception {
    String text;
    Template template;

    text = "test-scheme:/test-path";
    template = Parser.parseTemplate( text );
    assertThat( template.hasScheme(), is( true ) );
    assertThat( template.getScheme().getFirstValue().getOriginalPattern(), is( "test-scheme" ) );
    assertThat( template.getScheme().getFirstValue().getEffectivePattern(), is( "test-scheme" ) );
    assertThat( template.hasAuthority(), is( false ) );
    assertThat( template.getPath().size(), is( 1 ) );
    assertThat( template.getPath().get( 0 ).getFirstValue().getOriginalPattern(), is( "test-path" ) );
    assertThat( template.getPath().get( 0 ).getFirstValue().getEffectivePattern(), is( "test-path" ) );
    assertThat( template.hasQuery(), is( false ) );
    assertThat( template.toString(), is( text ) );

    text = "test-scheme:///test-path";
    template = Parser.parseTemplate( text );
    assertThat( template.hasScheme(), is( true ) );
    assertThat( template.getScheme().getFirstValue().getOriginalPattern(), is( "test-scheme" ) );
    assertThat( template.getScheme().getFirstValue().getEffectivePattern(), is( "test-scheme" ) );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template.getUsername(), nullValue() );
    assertThat( template.getPassword(), nullValue() );
    assertThat( template.getHost(), nullValue() );
    assertThat( template.getPort(), nullValue() );
    assertThat( template.getPath().size(), is( 1 ) );
    assertThat( template.getPath().get( 0 ).getFirstValue().getEffectivePattern(), is( "test-path" ) );
    assertThat( template.hasQuery(), is( false ) );
    assertThat( template.toString(), is( text ) );

    text = "{test-scheme}:/{test-path}";
    template = Parser.parseTemplate( text );
    assertThat( template.hasScheme(), is( true ) );
    assertThat( template.getScheme().getParamName(), is( "test-scheme" ) );
    assertThat( template.getScheme().getFirstValue().getOriginalPattern(), nullValue() );
    assertThat( template.getScheme().getFirstValue().getEffectivePattern(), is( "*" ) );
    assertThat( template.hasAuthority(), is( false ) );
    assertThat( template.getPath().size(), is( 1 ) );
    assertThat( template.getPath().get( 0 ).getParamName(), is( "test-path" ) );
    assertThat( template.getPath().get( 0 ).getFirstValue().getOriginalPattern(), nullValue() );
    assertThat( template.getPath().get( 0 ).getFirstValue().getEffectivePattern(), is( "**" ) );
    assertThat( template.hasQuery(), is( false ) );
    assertThat( template.toString(), is( text ) );

    text = "{test-scheme}:///{test-path}";
    template = Parser.parseTemplate( text );
    assertThat( template.hasScheme(), is( true ) );
    assertThat( template.getScheme().getParamName(), is( "test-scheme" ) );
    assertThat( template.getScheme().getFirstValue().getOriginalPattern(), nullValue() );
    assertThat( template.getScheme().getFirstValue().getEffectivePattern(), is( "*" ) );
    assertThat( template.hasAuthority(), is( true ) );
    assertThat( template.getUsername(), nullValue() );
    assertThat( template.getPassword(), nullValue() );
    assertThat( template.getHost(), nullValue() );
    assertThat( template.getPort(), nullValue() );
    assertThat( template.getPath().size(), is( 1 ) );
    assertThat( template.getPath().get( 0 ).getParamName(), is( "test-path" ) );
    assertThat( template.getPath().get( 0 ).getFirstValue().getOriginalPattern(), nullValue() );
    assertThat( template.getPath().get( 0 ).getFirstValue().getEffectivePattern(), is( "**" ) );
    assertThat( template.hasQuery(), is( false ) );
    assertThat( template.toString(), is( text ) );
  }

  @Test
  public void testAuthorityWildcards() throws Exception {
    String text;
    Template template;

    text = "*://*:*/";
    template = Parser.parseTemplate( text );
    assertThat( template.getHost().getFirstValue().getOriginalPattern(), is( "*" ) );
    assertThat( template.getHost().getFirstValue().getEffectivePattern(), is( "*" ) );
    assertThat( template.getPort().getFirstValue().getOriginalPattern(), is( "*" ) );
    assertThat( template.getPort().getFirstValue().getEffectivePattern(), is( "*" ) );
    assertThat( template.toString(), is( text ) );

    text = "*://**/";
    template = Parser.parseTemplate( text );
    assertThat( template.getHost().getFirstValue().getOriginalPattern(), is( "**" ) );
    assertThat( template.getHost().getFirstValue().getEffectivePattern(), is( "*" ) );
    assertThat( template.getPort(), nullValue() );
    assertThat( template.toString(), is( text ) );

    text = "*://*/";
    template = Parser.parseTemplate( text );
    assertThat( template.getHost().getFirstValue().getOriginalPattern(), is( "*" ) );
    assertThat( template.getHost().getFirstValue().getEffectivePattern(), is( "*" ) );
    assertThat( template.getPort(), nullValue() );
    assertThat( template.toString(), is( text ) );

    text = "*://**:**/";
    template = Parser.parseTemplate( text );
    assertThat( template.getHost().getFirstValue().getOriginalPattern(), is( "**" ) );
    assertThat( template.getHost().getFirstValue().getEffectivePattern(), is( "*" ) );
    assertThat( template.getPort().getFirstValue().getOriginalPattern(), is( "**" ) );
    assertThat( template.getPort().getFirstValue().getEffectivePattern(), is( "*" ) );
    assertThat( template.toString(), is( text ) );
  }

  @Test
  public void testParseTemplateToken() {
    Builder builder;
    String input;
    Token output;

    builder = new Builder( "" );

    input = "{";
    output = Parser.parseTemplateToken( builder, input, "~" );
    assertThat( output.getParameterName(), is( "" ) );
    assertThat( output.getOriginalPattern(), is( "{" ) );
    assertThat( output.getEffectivePattern(), is( "{" ) );

    input = "}";
    output = Parser.parseTemplateToken( builder, input, "~" );
    assertThat( output.getParameterName(), is( "" ) );
    assertThat( output.getOriginalPattern(), is( "}" ) );
    assertThat( output.getEffectivePattern(), is( "}" ) );

    input = "{X";
    output = Parser.parseTemplateToken( builder, input, "~" );
    assertThat( output.getParameterName(), is( "" ) );
    assertThat( output.getOriginalPattern(), is( "{X" ) );
    assertThat( output.getEffectivePattern(), is( "{X" ) );

    input = "X}";
    output = Parser.parseTemplateToken( builder, input, "~" );
    assertThat( output.getParameterName(), is( "" ) );
    assertThat( output.getOriginalPattern(), is( "X}" ) );
    assertThat( output.getEffectivePattern(), is( "X}" ) );

    input = "X";
    output = Parser.parseTemplateToken( builder, input, "~" );
    assertThat( output.getParameterName(), is( "" ) );
    assertThat( output.getOriginalPattern(), is( "X" ) );
    assertThat( output.getEffectivePattern(), is( "X" ) );

    input = "$";
    output = Parser.parseTemplateToken( builder, input, "~" );
    assertThat( output.getParameterName(), is( "" ) );
    assertThat( output.getOriginalPattern(), is( "$" ) );
    assertThat( output.getEffectivePattern(), is( "$" ) );

    input = "";
    output = Parser.parseTemplateToken( builder, input, Segment.GLOB_PATTERN );
    assertThat( output.getParameterName(), is( "" ) );
    assertThat( output.getOriginalPattern(), is( "" ) );
    assertThat( output.getEffectivePattern(), is( "" ) );
  }

  @Test
  public void testBugKnox599() throws Exception {
    Template template;
    Template input;
    Matcher<String> matcher;

    matcher = new Matcher<>();
    template = Parser.parseTemplate( "*://*:*/**/webhdfs/v1/{path=**}?{**}" );
    matcher.add( template, "test-value" );

    input = Parser.parseTemplate( "http://kminder-os-u14-23-knoxha-150922-1352-2.novalocal:1022/gateway/sandbox/webhdfs/v1/user/hrt_qa/knox-ha/knox_webhdfs_client_dir/test_file?op=CREATE&delegation=XXX&namenoderpcaddress=nameservice&createflag=&createparent=true&overwrite=true" );

    assertThat( input.getQuery().get( "createflag" ).getFirstValue().getPattern(), is( "" ) );

    input = Parser.parseTemplate( "http://kminder-os-u14-23-knoxha-150922-1352-2.novalocal:1022/gateway/sandbox/webhdfs/v1/user/hrt_qa/knox-ha/knox_webhdfs_client_dir/test_file?op=CREATE&delegation=XXX&namenoderpcaddress=nameservice&createflag&createparent=true&overwrite=true" );

    assertThat( input.getQuery().get( "createflag" ).getFirstValue().getPattern(), nullValue() );
  }

  @Test
  public void testParserLiteralsWithReservedCharactersBugKnox394() throws Exception {
    Template template;
    String image;

    template = Parser.parseLiteral( "{}" );
    image = template.toString();
    assertThat( image, is( "{}" ) );

    template = Parser.parseLiteral( "{app.path}/child/path" );
    image = template.toString();
    assertThat( image, is( "{app.path}/child/path" ) );

    template = Parser.parseLiteral( "${app.path}/child/path" );
    image = template.toString();
    assertThat( image, is( "${app.path}/child/path" ) );

  }

}
