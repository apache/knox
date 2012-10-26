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

import java.net.URI;
import java.net.URISyntaxException;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

public class ExtractorTest {

  @Test
  public void testFullUrlExtraction() throws URISyntaxException {
    String text;
    Template template;
    Params params;
    URI uri;

    text = "{scheme}://{username}:{password}@{host}:{port}/{root}/{path}/{file}?queryA={paramA}&queryB={paramB}#{fragment}";
    template = Parser.parse( text );
    uri = new URI( "http://horton:hadoop@hortonworks.com:80/top/middle/end?queryA=valueA&queryB=valueB#section" );
    params = Extractor.extract( template, uri );

    assertThat( params.getNames(), hasItem( "scheme" ) );
    assertThat( params.getValues( "scheme" ), hasItem( "http" ) );
    assertThat( params.getNames(), hasItem( "username" ) );
    assertThat( params.getValues( "username" ), hasItem( "horton" ) );
    assertThat( params.getNames(), hasItem( "password" ) );
    assertThat( params.getValues( "password" ), hasItem( "hadoop" ) );
    assertThat( params.getNames(), hasItem( "host" ) );
    assertThat( params.getValues( "host" ), hasItem( "hortonworks.com" ) );
    assertThat( params.getNames(), hasItem( "port" ) );
    assertThat( params.getValues( "port" ), hasItem( "80" ) );
    assertThat( params.getNames(), hasItem( "root" ) );
    assertThat( params.getValues( "root" ), hasItem( "top" ) );
    assertThat( params.getNames(), hasItem( "path" ) );
    assertThat( params.getValues( "path" ), hasItem( "middle" ) );
    assertThat( params.getNames(), hasItem( "file" ) );
    assertThat( params.getValues( "file" ), hasItem( "end" ) );
    assertThat( params.getNames(), hasItem( "paramA" ) );
    assertThat( params.getValues( "paramA" ), hasItem( "valueA" ) );
    assertThat( params.getNames(), hasItem( "paramB" ) );
    assertThat( params.getValues( "paramB" ), hasItem( "valueB" ) );
    assertThat( params.getNames(), hasItem( "fragment" ) );
    assertThat( params.getValues( "fragment" ), hasItem( "section" ) );
    assertThat( params.getNames().size(), equalTo( 11 ) );
  }

  @Test
  public void testPathExtraction() throws Exception {
    Template template;
    Params params;
    URI uri;

    template = Parser.parse( "{path-param}" );
    uri = new URI("path-value");
    params = Extractor.extract( template, uri );
    assertThat( params, notNullValue() );
    assertThat( params.getNames().size(), equalTo( 1 ) );
    assertThat( params.getNames(), hasItem( "path-param" ) );
    assertThat( params.getValues( "path-param" ).size(), equalTo( 1 ) );
    assertThat( params.getValues( "path-param" ), hasItem( "path-value" ) );

    template = Parser.parse( "/some-path/{path-param}" );
    uri = new URI("/some-path/path-value");
    params = Extractor.extract( template, uri );
    assertThat( params, notNullValue() );
    assertThat( params.getNames().size(), equalTo( 1 ) );
    assertThat( params.getNames(), hasItem( "path-param" ) );
    assertThat( params.getValues( "path-param" ).size(), equalTo( 1 ) );
    assertThat( params.getValues( "path-param" ), hasItem( "path-value" ) );

    template = Parser.parse( "/some-path/{path-param}/some-other-path" );
    uri = new URI("/some-path/path-value/some-other-path");
    params = Extractor.extract( template, uri );
    assertThat( params, notNullValue() );
    assertThat( params.getNames().size(), equalTo( 1 ) );
    assertThat( params.getNames(), hasItem( "path-param" ) );
    assertThat( params.getValues( "path-param" ).size(), equalTo( 1 ) );
    assertThat( params.getValues( "path-param" ), hasItem( "path-value" ) );
  }

  @Test
  public void testQueryExtraction() throws Exception {
    Template template;
    Params params;
    URI uri;

    template = Parser.parse( "?query-param={param-name}" );
    uri = new URI("?query-param=param-value");
    params = Extractor.extract( template, uri );
    assertThat( params, notNullValue() );
    assertThat( params.getNames().size(), equalTo( 1 ) );
    assertThat( params.getNames(), hasItem( "param-name" ) );
    assertThat( params.getValues( "param-name" ).size(), equalTo( 1 ) );
    assertThat( params.getValues( "param-name" ), hasItem( "param-value" ) );

    template = Parser.parse( "?query-param={param-name}" );
    uri = new URI("?query-param=param-value");
    params = Extractor.extract( template, uri );
    assertThat( params, notNullValue() );
    assertThat( params.getNames().size(), equalTo( 1 ) );
    assertThat( params.getNames(), hasItem( "param-name" ) );
    assertThat( params.getValues( "param-name" ).size(), equalTo( 1 ) );
    assertThat( params.getValues( "param-name" ), hasItem( "param-value" ) );
  }

  @Test
  public void testEdgeCaseExtraction() throws Exception {
    Template template;
    Params params;
    URI uri;

    template = Parser.parse( "" );
    uri = new URI("");
    params = Extractor.extract( template, uri );
    assertThat( params, notNullValue() );
    assertThat( params.getNames().size(), equalTo( 0 ) );
  }

}
