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

import org.junit.Test;

import java.net.URI;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

public class ExtractorTest {

  @Test
  public void testEdgeCaseExtraction() throws Exception {
    Extractor extractor = new Extractor();
    Parser parser = new Parser();
    Template template;
    Params params;
    URI uri;

    template = parser.parse( "" );
    uri = new URI("");
    params = extractor.extract( template, uri );
    assertThat( params, notNullValue() );
    assertThat( params.getNames().size(), equalTo( 0 ) );
  }

    @Test
  public void testPathExtraction() throws Exception {
    Extractor extractor = new Extractor();
    Parser parser = new Parser();
    Template template;
    Params params;
    URI uri;

    template = parser.parse( "{path-param}" );
    uri = new URI("path-value");
    params = extractor.extract( template, uri );
    assertThat( params, notNullValue() );
    assertThat( params.getNames().size(), equalTo( 1 ) );
    assertThat( params.getNames(), hasItem( "path-param" ) );
    assertThat( params.getValues( "path-param" ).size(), equalTo( 1 ) );
    assertThat( params.getValues( "path-param" ), hasItem( "path-value" ) );

    template = parser.parse( "/some-path/{path-param}" );
    uri = new URI("/some-path/path-value");
    params = extractor.extract( template, uri );
    assertThat( params, notNullValue() );
    assertThat( params.getNames().size(), equalTo( 1 ) );
    assertThat( params.getNames(), hasItem( "path-param" ) );
    assertThat( params.getValues( "path-param" ).size(), equalTo( 1 ) );
    assertThat( params.getValues( "path-param" ), hasItem( "path-value" ) );

    template = parser.parse( "/some-path/{path-param}/some-other-path" );
    uri = new URI("/some-path/path-value/some-other-path");
    params = extractor.extract( template, uri );
    assertThat( params, notNullValue() );
    assertThat( params.getNames().size(), equalTo( 1 ) );
    assertThat( params.getNames(), hasItem( "path-param" ) );
    assertThat( params.getValues( "path-param" ).size(), equalTo( 1 ) );
    assertThat( params.getValues( "path-param" ), hasItem( "path-value" ) );
  }

  @Test
  public void testQueryExtraction() throws Exception {
    Extractor extractor = new Extractor();
    Parser parser = new Parser();
    Template template;
    Params params;
    URI uri;

    template = parser.parse( "?query-param={param-name}" );
    uri = new URI("?query-param=param-value");
    params = extractor.extract( template, uri );
    assertThat( params, notNullValue() );
    assertThat( params.getNames().size(), equalTo( 1 ) );
    assertThat( params.getNames(), hasItem( "param-name" ) );
    assertThat( params.getValues( "param-name" ).size(), equalTo( 1 ) );
    assertThat( params.getValues( "param-name" ), hasItem( "param-value" ) );

    template = parser.parse( "?query-param={param-name}" );
    uri = new URI("?query-param=param-value");
    params = extractor.extract( template, uri );
    assertThat( params, notNullValue() );
    assertThat( params.getNames().size(), equalTo( 1 ) );
    assertThat( params.getNames(), hasItem( "param-name" ) );
    assertThat( params.getValues( "param-name" ).size(), equalTo( 1 ) );
    assertThat( params.getValues( "param-name" ), hasItem( "param-value" ) );
  }

}
