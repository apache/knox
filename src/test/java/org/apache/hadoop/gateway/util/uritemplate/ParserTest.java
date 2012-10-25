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
package org.apache.hadoop.gateway.util.uritemplate;

import org.junit.Ignore;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class ParserTest {

  private void assertBasics(
      Template template,
      boolean isAbsolute,
      boolean isDirectory,
      boolean hasQuery,
      int pathSegmentsSize,
      int querySegmentsSize ) {
    assertThat( template.isAbsolute(), equalTo( isAbsolute ) );
    assertThat( template.isDirectory(), equalTo( isDirectory ) );
    assertThat( template.hasQuery(), equalTo( hasQuery ) );
    assertThat( template.getPath().size(), equalTo( pathSegmentsSize ) );
    assertThat( template.getQuery().size(), equalTo( querySegmentsSize ) );
  }

  public void assertPath(
      Template template,
      int index,
      String paramName,
      String valuePattern ) {
    PathSegment segment = template.getPath().get( index );
    assertThat( segment.getParamName(), equalTo( paramName ));
    assertThat( segment.getValuePattern(), equalTo( valuePattern ));
  }

  public void assertPath(
      Template template,
      int index,
      String paramName,
      String valuePattern,
      int type,
      int minRequired,
      int maxAllowed ) {
    PathSegment segment = template.getPath().get( index );
    assertThat( "Param name wrong.", segment.getParamName(), equalTo( paramName ));
    assertThat( "Value pattern wrong.", segment.getValuePattern(), equalTo( valuePattern ));
    assertThat( "Segment type wrong.", segment.getType(), equalTo( type ) );
    assertThat( "Segment min required wrong.", segment.getMinRequired(), equalTo( minRequired ) );
    assertThat( "Segment max allowed wrong.", segment.getMaxAllowed(), equalTo( maxAllowed ) );
  }

  public void assertQuery(
      Template template,
      String queryName,
      String paramName,
      String valuePattern ) {
    QuerySegment segment = template.getQuery().get( queryName );
    assertThat( "Query name wrong.", segment.getQueryName(), equalTo( queryName ));
    assertThat( "Param name wrong.", segment.getParamName(), equalTo( paramName ));
    assertThat( "value pattern wrong.", segment.getValuePattern(), equalTo( valuePattern ));
  }

  public void assertQuery(
      Template template,
      String queryName,
      String paramName,
      String valuePattern,
      int type,
      int minRequired,
      int maxAllowed ) {
    QuerySegment segment = template.getQuery().get( queryName );
    assertThat( "Query name wrong.", segment.getQueryName(), equalTo( queryName ));
    assertThat( "Param name wrong.", segment.getParamName(), equalTo( paramName ));
    assertThat( "value pattern wrong.", segment.getValuePattern(), equalTo( valuePattern ));
    assertThat( "Segment type wrong.", segment.getType(), equalTo( type ) );
    assertThat( "Segment min required wrong.", segment.getMinRequired(), equalTo( minRequired ) );
    assertThat( "Segment max allowed wrong.", segment.getMaxAllowed(), equalTo( maxAllowed ) );
  }

  @Ignore( "TODO" )
  @Test
  public void testInvalidPatterns() {
    //TODO: ? in wrong spot.
    //TODO: & in wrong spots.
  }

  @Ignore( "TODO" )
  @Test
  public void testRegexPatterns() {
  }

  @Test
  public void testTemplates() {
    String text;
    Template template;

    text = "{path}";
    template = Parser.parse( text );
    assertBasics( template, false, false, false, 1, 0 );
    assertPath( template, 0, "path", "*" );

    text = "{pathA}/{pathB}";
    template = Parser.parse( text );
    assertBasics( template, false, false, false, 2, 0 );
    assertPath( template, 0, "pathA", "*" );
    assertPath( template, 1, "pathB", "*" );

    text = "?paramA={valueA}";
    template = Parser.parse( text );
    assertBasics( template, false, false, true, 0, 1 );
    assertQuery( template, "paramA", "valueA", "*" );

    text = "?paramA={valueA}&paramB={valueB}";
    template = Parser.parse( text );
    assertBasics( template, false, false, true, 0, 2 );
    assertQuery( template, "paramA", "valueA", "*" );
    assertQuery( template, "paramB", "valueB", "*" );

    text = "{pathA}?paramA={valueA}";
    template = Parser.parse( text );
    assertBasics( template, false, false, true, 1, 1 );
    assertPath( template, 0, "pathA", "*" );
    assertQuery( template, "paramA", "valueA", "*" );
  }

  @Test
  public void testStaticPatterns() throws Exception {
    String text;
    Template template;

    text = "";
    template = Parser.parse( text );
    assertBasics( template, false, false, false, 0, 0 );

    text = "/";
    template = Parser.parse( text );
    assertBasics( template, true, true, false, 0, 0 );

    text = "//";
    template = Parser.parse( text );
    assertBasics( template, true, true, false, 0, 0 );

    text = "?";
    template = Parser.parse( text );
    assertBasics( template, false, false, true, 0, 0 );

    text = "??";
    template = Parser.parse( text );
    assertBasics( template, false, false, true, 0, 0 );

    text = "path";
    template = Parser.parse( text );
    assertBasics( template, false, false, false, 1, 0 );
    assertPath( template, 0, "", "path" );

    text = "/path";
    template = Parser.parse( text );
    assertBasics( template, true, false, false, 1, 0 );
    assertPath( template, 0, "", "path" );

    text = "//path";
    template = Parser.parse( text );
    assertBasics( template, true, false, false, 1, 0 );
    assertPath( template, 0, "", "path" );

    text = "path/";
    template = Parser.parse( text );
    assertBasics( template, false, true, false, 1, 0 );
    assertPath( template, 0, "", "path" );

    text = "path//";
    template = Parser.parse( text );
    assertBasics( template, false, true, false, 1, 0 );
    assertPath( template, 0, "", "path" );

    text = "/path/";
    template = Parser.parse( text );
    assertBasics( template, true, true, false, 1, 0 );
    assertPath( template, 0, "", "path" );

    text = "//path//";
    template = Parser.parse( text );
    assertBasics( template, true, true, false, 1, 0 );
    assertPath( template, 0, "", "path" );

    text = "pathA/pathB";
    template = Parser.parse( text );
    assertBasics( template, false, false, false, 2, 0 );
    assertPath( template, 0, "", "pathA" );
    assertPath( template, 1, "", "pathB" );

    text = "pathA//pathB";
    template = Parser.parse( text );
    assertBasics( template, false, false, false, 2, 0 );
    assertPath( template, 0, "", "pathA" );
    assertPath( template, 1, "", "pathB" );

    text = "/pathA/pathB";
    template = Parser.parse( text );
    assertBasics( template, true, false, false, 2, 0 );
    assertPath( template, 0, "", "pathA" );
    assertPath( template, 1, "", "pathB" );

    text = "/pathA//pathB";
    template = Parser.parse( text );
    assertBasics( template, true, false, false, 2, 0 );
    assertPath( template, 0, "", "pathA" );
    assertPath( template, 1, "", "pathB" );

    text = "pathA/pathB/";
    template = Parser.parse( text );
    assertBasics( template, false, true, false, 2, 0 );
    assertPath( template, 0, "", "pathA" );
    assertPath( template, 1, "", "pathB" );

    text = "pathA//pathB/";
    template = Parser.parse( text );
    assertBasics( template, false, true, false, 2, 0 );
    assertPath( template, 0, "", "pathA" );
    assertPath( template, 1, "", "pathB" );

    text = "/pathA/pathB/";
    template = Parser.parse( text );
    assertBasics( template, true, true, false, 2, 0 );
    assertPath( template, 0, "", "pathA" );
    assertPath( template, 1, "", "pathB" );

    text = "/pathA//pathB/";
    template = Parser.parse( text );
    assertBasics( template, true, true, false, 2, 0 );
    assertPath( template, 0, "", "pathA" );
    assertPath( template, 1, "", "pathB" );

    text = "/?";
    template = Parser.parse( text );
    assertBasics( template, true, true, true, 0, 0 );

    text = "//??";
    template = Parser.parse( text );
    assertBasics( template, true, true, true, 0, 0 );

    text = "?name=value";
    template = Parser.parse( text );
    assertBasics( template, false, false, true, 0, 1 );
    assertQuery( template, "name", "", "value" );

    text = "??name=value";
    template = Parser.parse( text );
    assertBasics( template, false, false, true, 0, 1 );
    assertQuery( template, "name", "", "value" );

    text = "?name1=value1&name2=value2";
    template = Parser.parse( text );
    assertBasics( template, false, false, true, 0, 2 );
    assertQuery( template, "name1", "", "value1" );
    assertQuery( template, "name2", "", "value2" );

    text = "?name1=value1&&name2=value2";
    template = Parser.parse( text );
    assertBasics( template, false, false, true, 0, 2 );
    assertQuery( template, "name1", "", "value1" );
    assertQuery( template, "name2", "", "value2" );

    text = "/?name=value";
    template = Parser.parse( text );
    assertBasics( template, true, true, true, 0, 1 );
    assertQuery( template, "name", "", "value" );

    text = "/?name1=value1&name2=value2";
    template = Parser.parse( text );
    assertBasics( template, true, true, true, 0, 2 );
    assertQuery( template, "name1", "", "value1" );
    assertQuery( template, "name2", "", "value2" );
  }

  @Test
  public void testParameterizedPathTemplatesWithWildcardAndRegex() {
    String text;
    Template template;

    text = "{path}";
    template = Parser.parse( text );
    assertBasics( template, false, false, false, 1, 0 );
    assertPath( template, 0, "path", "*", Segment.WILDCARD, 1, 1 );

    text = "{path=static}";
    template = Parser.parse( text );
    assertBasics( template, false, false, false, 1, 0 );
    assertPath( template, 0, "path", "static", Segment.STATIC, 1, 1 );

    text = "{path=*}";
    template = Parser.parse( text );
    assertBasics( template, false, false, false, 1, 0 );
    assertPath( template, 0, "path", "*", Segment.WILDCARD, 1, 1 );

    text = "{path=**}";
    template = Parser.parse( text );
    assertBasics( template, false, false, false, 1, 0 );
    assertPath( template, 0, "path", "**", Segment.WILDCARD, 0, Integer.MAX_VALUE );

    text = "{path=wild*card}";
    template = Parser.parse( text );
    assertBasics( template, false, false, false, 1, 0 );
    assertPath( template, 0, "path", "wild*card", Segment.REGEX, 1, 1 );
  }

  @Test
  public void testParameterizedQueryTemplatesWithWildcardAndRegex() {
    String text;
    Template template;

    text = "?query={param}";
    template = Parser.parse( text );
    assertBasics( template, false, false, true, 0, 1 );
    assertQuery( template, "query", "param", "*", Segment.WILDCARD, 1, 1 );

    text = "?query={param=static}";
    template = Parser.parse( text );
    assertBasics( template, false, false, true, 0, 1 );
    assertQuery( template, "query", "param", "static", Segment.STATIC, 1, 1 );

    text = "?query={param=*}";
    template = Parser.parse( text );
    assertBasics( template, false, false, true, 0, 1 );
    assertQuery( template, "query", "param", "*", Segment.WILDCARD, 1, 1 );

    text = "?query={param=**}";
    template = Parser.parse( text );
    assertBasics( template, false, false, true, 0, 1 );
    assertQuery( template, "query", "param", "**", Segment.WILDCARD, 0, Integer.MAX_VALUE );

    text = "?query={param=wild*card}";
    template = Parser.parse( text );
    assertBasics( template, false, false, true, 0, 1 );
    assertQuery( template, "query", "param", "wild*card", Segment.REGEX, 1, 1 );
  }

  @Test
  public void testGlobPattern() {
    String text;
    Template template;

    text = "**";
    template = Parser.parse( text );
    assertBasics( template, false, false, false, 1, 0 );

    text = "/**";
    template = Parser.parse( text );
    assertBasics( template, true, false, false, 1, 0 );

    text = "**/";
    template = Parser.parse( text );
    assertBasics( template, false, true, false, 1, 0 );

    text = "/**/";
    template = Parser.parse( text );
    assertBasics( template, true, true, false, 1, 0 );

    text = "/**/path";
    template = Parser.parse( text );
    assertBasics( template, true, false, false, 2, 0 );
  }

}
