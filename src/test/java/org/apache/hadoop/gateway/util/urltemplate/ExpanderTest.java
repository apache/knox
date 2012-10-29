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

import org.junit.Ignore;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class ExpanderTest {

  @Ignore( "TODO" )
  @Test
  public void testUrlEncoding() {
  }

  @Test
  public void testCompleteUrl() throws URISyntaxException {
    String text;
    Template template;
    Params params;
    URI expanded;

    text = "foo://username:password@example.com:8042/over/there/index.dtb?type=animal&name=narwhal#nose";
    template = Parser.parse( text );
    params = new Params();
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( text ) ) ;

    text = "{scheme}://{username}:{password}@{host}:{port}/{path=**}?query={param}#{fragment}";
    template = Parser.parse( text );
    params = new Params();
    params.addValue( "scheme", "http" );
    params.addValue( "username", "horton" );
    params.addValue( "password", "hadoop" );
    params.addValue( "host", "hortonworks.com" );
    params.addValue( "port", "8888" );
    params.addValue( "path", "top" );
    params.addValue( "path", "mid" );
    params.addValue( "path", "bot" );
    params.addValue( "path", "file" );
    params.addValue( "param", "new-value" );
    params.addValue( "fragment", "fragment" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "http://horton:hadoop@hortonworks.com:8888/top/mid/bot/file?query=new-value#fragment" ) ) ;
  }

  @Test
  public void testBasicExpansion() throws Exception {
    Template template;
    Params params;
    URI expanded;

    template = Parser.parse( "" );
    params = new Params();
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "" ) ) ;

    template = Parser.parse( "/" );
    params = new Params();
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "/" ) ) ;

    template = Parser.parse( "{path-name}" );
    params = new Params();
    params.addValue( "path-name", "path-value" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "path-value" ) ) ;

    template = Parser.parse( "/{path-name}" );
    params = new Params();
    params.addValue( "path-name", "path-value" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "/path-value" ) ) ;

    template = Parser.parse( "{path-name}/" );
    params = new Params();
    params.addValue( "path-name", "path-value" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "path-value/" ) ) ;

    template = Parser.parse( "/{path-name}/" );
    params = new Params();
    params.addValue( "path-name", "path-value" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "/path-value/" ) ) ;

    template = Parser.parse( "path-name" );
    params = new Params();
    params.addValue( "path-name", "other-path-value" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "path-name" ) ) ;

    template = Parser.parse( "/path-name" );
    params = new Params();
    params.addValue( "path-name", "other-path-value" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "/path-name" ) ) ;

    template = Parser.parse( "path-name/" );
    params = new Params();
    params.addValue( "path-name", "other-path-value" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "path-name/" ) ) ;

    template = Parser.parse( "/path-name/" );
    params = new Params();
    params.addValue( "path-name", "other-path-value" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "/path-name/" ) ) ;

    template = Parser.parse( "?" );
    params = new Params();
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "?" ) ) ;

    template = Parser.parse( "?query-name={param-name}" );
    params = new Params();
    params.addValue( "param-name", "param-value" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "?query-name=param-value" ) ) ;

    template = Parser.parse( "?query-name-1={param-name-1}&query-name-2={param-name-2}" );
    params = new Params();
    params.addValue( "param-name-1", "param-value-1" );
    params.addValue( "param-name-2", "param-value-2" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "?query-name-1=param-value-1&query-name-2=param-value-2" ) ) ;

    template = Parser.parse( "?query-name=param-value" );
    params = new Params();
    params.addValue( "param-name", "other-param-value" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "?query-name=param-value" ) ) ;

    template = Parser.parse( "?query-name-1=param-value-1&query-name-2=param-value-2" );
    params = new Params();
    params.addValue( "param-name-1", "other-param-value-1" );
    params.addValue( "param-name-2", "other-param-value-2" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "?query-name-1=param-value-1&query-name-2=param-value-2" ) ) ;
  }

  @Test
  public void testAuthorityExpansion() throws URISyntaxException {
    Template template;
    Params params;
    URI expanded;

    template = Parser.parse( "//host" );
    params = new Params();
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "//host" ) ) ;

    template = Parser.parse( "//:port" );
    params = new Params();
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "//:port" ) ) ;

    template = Parser.parse( "//username@" );
    params = new Params();
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "//username@" ) ) ;

    template = Parser.parse( "//:password@" );
    params = new Params();
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "//:password@" ) ) ;
  }

  @Test
  public void testPathExpansion() throws URISyntaxException {
    Template template;
    Params params;
    URI expanded;

    template = Parser.parse( "/a/b/c" );
    params = new Params();
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "/a/b/c" ) ) ;

    template = Parser.parse( "/top/{middle}/bottom" );
    params = new Params();
    params.addValue( "middle", "A" );
    params.addValue( "middle", "B" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "/top/A/bottom" ) ) ;

    template = Parser.parse( "/top/{middle=*}/bottom" );
    params = new Params();
    params.addValue( "middle", "A" );
    params.addValue( "middle", "B" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "/top/A/bottom" ) ) ;

    template = Parser.parse( "/top/{middle=**}/bottom" );
    params = new Params();
    params.addValue( "middle", "A" );
    params.addValue( "middle", "B" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "/top/A/B/bottom" ) ) ;
  }

  @Test
  public void testQueryExpansion() throws URISyntaxException {
    Template template;
    Params params;
    URI expanded;

    template = Parser.parse( "?query" );
    params = new Params();
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "?query" ) ) ;

    template = Parser.parse( "?query={param}" );
    params = new Params();
    params.addValue( "param", "A" );
    params.addValue( "param", "B" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "?query=A" ) ) ;

    template = Parser.parse( "?query={param=*}" );
    params = new Params();
    params.addValue( "param", "A" );
    params.addValue( "param", "B" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "?query=A" ) ) ;

    template = Parser.parse( "?query={param=**}" );
    params = new Params();
    params.addValue( "param", "A" );
    params.addValue( "param", "B" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "?query=A&query=B" ) ) ;

  }

}
