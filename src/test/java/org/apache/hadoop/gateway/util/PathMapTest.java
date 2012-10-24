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
package org.apache.hadoop.gateway.util;

import org.apache.hadoop.test.FastTests;
import org.apache.hadoop.test.UnitTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 *
 */
@Category( { UnitTests.class, FastTests.class })
public class PathMapTest {

  @Test
  public void testRegex() {
    String str;

    str = "(*.jsp)";
    str = str.replaceAll( "\\.", "\\\\." );
    assertThat( str, equalTo( "(*\\.jsp)" ) );

    str = "(*..jsp)";
    str = str.replaceAll( "\\.", "\\\\." );
    assertThat( str, equalTo( "(*\\.\\.jsp)" ) );

  }

  @Test
  public void testStar() {
    PathMap<String> map = new PathMap<String>();
    map.put( "/webhdfs/*", "/webhdfs/*" );
    assertThat( map.get( "/webhdfs/*" ), equalTo( "/webhdfs/*" ) );
    assertThat( map.pick( "/webhdfs/file" ), equalTo( "/webhdfs/*" ) );
    assertThat( map.pick( "/webhdfs/path/" ), equalTo( "/webhdfs/*" ) );
    assertThat( map.pick( "/webhdfs/path/file" ), nullValue() );
    assertThat( map.pick( "/webhdfs/path/path/" ), nullValue() );
  }

  @Test
  public void testGlob() {
    PathMap<String> map = new PathMap<String>();
    map.put( "/webhdfs/**", "/webhdfs/**" );
    assertThat( map.get( "/webhdfs/**" ), equalTo( "/webhdfs/**" ) );
    assertThat( map.pick( "/webhdfs/file" ), equalTo( "/webhdfs/**" ) );
    assertThat( map.pick( "/webhdfs/path/" ), equalTo( "/webhdfs/**" ) );
    assertThat( map.pick( "/webhdfs/path/file" ), equalTo( "/webhdfs/**" ) );
    assertThat( map.pick( "/webhdfs/path/path/" ), equalTo( "/webhdfs/**" ) );

    //assertThat( map.pick( "/webhdfs" ), equalTo( "/webhdfs/**" ) );
    //assertThat( map.pick( "/webhdfs/" ), equalTo( "/webhdfs/**" ) );
  }

  @Test
  public void testMatrixParam() {
    PathMap<String> map = new PathMap<String>();
    map.put( "/webhdfs/**", "/webhdfs/**" );
    map.put( "/webhdfs/browseDirectory.jsp;dn=*", "/webhdfs/browseDirectory.jsp;dn=*" );
    assertThat(
        map.get( "/webhdfs/browseDirectory.jsp;dn=*" ),
        equalTo( "/webhdfs/browseDirectory.jsp;dn=*" ) );
    assertThat(
        map.pick( "/webhdfs/browseDirectory.jsp;dn=X" ),
        equalTo( "/webhdfs/browseDirectory.jsp;dn=*" ) );
  }

  @Test
  public void testTwoGlobsAtDifferentDepths() {
    PathMap<String> map = new PathMap<String>();
    map.put( "/webhdfs/**", "/webhdfs/**" );
    map.put( "/webhdfs/v1/**", "/webhdfs/v1/**" );
    assertThat( map.get( "/webhdfs/**" ), equalTo( "/webhdfs/**" ) );
    assertThat( map.get( "/webhdfs/v1/**" ), equalTo( "/webhdfs/v1/**" ) );
    assertThat( map.pick( "/webhdfs/file" ), equalTo( "/webhdfs/**" ) );
    assertThat( map.pick( "/webhdfs/v1/file" ), equalTo( "/webhdfs/v1/**" ) );

    // Reverse the put order.
    map = new PathMap<String>();
    map.put( "/webhdfs/v1/**", "/webhdfs/v1/**" );
    map.put( "/webhdfs/**", "/webhdfs/**" );
    assertThat( map.get( "/webhdfs/**" ), equalTo( "/webhdfs/**" ) );
    assertThat( map.get( "/webhdfs/v1/**" ), equalTo( "/webhdfs/v1/**" ) );
    assertThat( map.pick( "/webhdfs/file" ), equalTo( "/webhdfs/**" ) );
    assertThat( map.pick( "/webhdfs/v1/file" ), equalTo( "/webhdfs/v1/**" ) );
  }

  @Test
  public void testGlobsVsStarsAtSameDepth() {
    PathMap<String> map = new PathMap<String>();
    map.put( "/webhdfs/*", "/webhdfs/*" );
    map.put( "/webhdfs/**", "/webhdfs/**" );
    assertThat( map.get( "/webhdfs/*" ), equalTo( "/webhdfs/*" ) );
    assertThat( map.get( "/webhdfs/**" ), equalTo( "/webhdfs/**" ) );
    assertThat( map.pick( "/webhdfs/file" ), equalTo( "/webhdfs/*" ) );
    assertThat( map.pick( "/webhdfs/path/file" ), equalTo( "/webhdfs/**" ) );

    // Reverse the put order.
    map = new PathMap<String>();
    map.put( "/webhdfs/**", "/webhdfs/**" );
    map.put( "/webhdfs/*", "/webhdfs/*" );
    assertThat( map.get( "/webhdfs/*" ), equalTo( "/webhdfs/*" ) );
    assertThat( map.get( "/webhdfs/**" ), equalTo( "/webhdfs/**" ) );
    assertThat( map.pick( "/webhdfs/path/file" ), equalTo( "/webhdfs/**" ) );
    assertThat( map.pick( "/webhdfs/file" ), equalTo( "/webhdfs/*" ) );
  }

  @Test
  public void testVariousPatterns() {
    PathMap<String> map = new PathMap<String>();
    map.put( "/webhdfs", "/webhdfs" );
    map.put( "/webhdfs/dfshealth.jsp", "/webhdfs/dfshealth.jsp" );
    map.put( "/webhdfs/*.jsp", "/webhdfs/*.jsp" );
    map.put( "/webhdfs/other.jsp", "/webhdfs/other.jsp" );
    map.put( "/webhdfs/*", "/webhdfs/*" );
    map.put( "/webhdfs/**", "/webhdfs/**" );
    map.put( "/webhdfs/v1/**", "/webhdfs/v1/**" );
    map.put( "/webhdfs/**/middle/*.xml", "/webhdfs/**/middle/*.xml" );

    assertThat( map.pick( "/webhdfs" ), equalTo( "/webhdfs" ) );
    assertThat( map.pick( "/webhdfs/dfshealth.jsp" ), equalTo( "/webhdfs/dfshealth.jsp" ) );
    assertThat( map.pick( "/webhdfs/v1" ), equalTo( "/webhdfs/*" ) );
    assertThat( map.pick( "/webhdfs/some.jsp" ), equalTo( "/webhdfs/*.jsp" ) );
    assertThat( map.pick( "/webhdfs/other.jsp" ), equalTo( "/webhdfs/other.jsp" ) );
    assertThat( map.pick( "/webhdfs/path/some.jsp" ), equalTo( "/webhdfs/**" ) );
    assertThat( map.pick( "/webhdfs/path/middle/some.jsp" ), equalTo( "/webhdfs/**" ) );
    assertThat( map.pick( "/webhdfs/path/middle/some.xml" ), equalTo( "/webhdfs/**/middle/*.xml" ) );

    assertThat( map.pick( "/webhdfs/path/to/file" ), equalTo( "/webhdfs/**" ) );
    assertThat( map.pick( "/webhdfs/v1/path/to/file" ), equalTo( "/webhdfs/v1/**" ) );

    assertThat( map.get( "/webhdfs/*.jsp" ), equalTo( "/webhdfs/*.jsp" ) );
    assertThat( map.get( "/webhdfs/v1/**" ), equalTo( "/webhdfs/v1/**" ) );
    assertThat( map.get( "/webhdfs/**" ), equalTo( "/webhdfs/**" ) );
  }

  private void add( PathMap<String> map, String pattern ) {

  }

  public void testPatternThoughts() {
    PathMap<String> map = new PathMap<String>();

    add( map, "path" );
    add( map, "/path" );
    add( map, "//path" );
    add( map, "path/" );
    add( map, "path//" );
    add( map, "/path/" );
    add( map, "/path//" );
    add( map, "//path/" );
    add( map, "//path//" );

    add( map, "pathA/pathB" );
    add( map, "/path/pathB" );
    add( map, "//pathA/pathB" );
    add( map, "pathA/pathB/" );
    add( map, "pathA/pathB//" );
    add( map, "/pathA/pathB/" );
    add( map, "/patA/pathB//" );
    add( map, "//pathA/pathB/" );
    add( map, "//pathA/pathB//" );

    add( map, "{pathA}/pathB" );
    add( map, "/{path}/pathB" );
    add( map, "//{pathA}/pathB" );
    add( map, "{pathA}/pathB/" );
    add( map, "{pathA}/pathB//" );
    add( map, "/{pathA}/pathB/" );
    add( map, "/{pathA}/pathB//" );
    add( map, "//{pathA}/pathB/" );
    add( map, "//{pathA}/pathB//" );

    add( map, "pathA/{pathB}" );
    add( map, "/path/{pathB}" );
    add( map, "//pathA/{pathB}" );
    add( map, "pathA/{pathB}/" );
    add( map, "pathA/{pathB}//" );
    add( map, "/pathA/{pathB}/" );
    add( map, "/patA/{pathB}//" );
    add( map, "//pathA/{pathB}/" );
    add( map, "//pathA/{pathB}//" );

    add( map, "/{**}?{**}" ); // Should match everything.
    add( map, "/" ); // Should only match /.
    add( map, "{/**/}" );
    add( map, "/?{**}" ); // Should only match / with query params.
    add( map, "/pathA/{**}" );
    add( map, "/pathA/{**}?{**}" );
    add( map, "/pathA/{**}/pathB" );
    add( map, "/pathA/{**}/pathB?{**}" );
    add( map, "/pathA/{**}?paramA={valueA}" );
    add( map, "/pathA/{**}?paramA={valueA}&{**}" );
    add( map, "/pathA/{**}?{**}&paramA={valueA}" );
    add( map, "/pathA/{**}?{**}&paramA={valueA}" );
    add( map, "/pathA/{**}?paramA={valueA}&paramB={valueB}" );
    add( map, "/pathA/{**}?paramA={valueA}&paramB={valueB}&{**}" );

    // Path params with *
    // Query params with *.

    // Path segments with *.
    // Query names with *.
    // Query values with *
  }

  public void testTemplateThoughts() {
    String t;
    t = "/?"; // Should turn into an empty string no matter the input.
    t = "//??"; // Should turn into /? no matter the input.
    t = "/{pathA}?{paramB}";
    t = "//{pathA}??{paramB}";
  }

}
