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
package org.apache.knox.gateway.filter.rewrite.impl.json;

import com.jayway.jsonassert.JsonAssert;
import org.apache.commons.io.IOUtils;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterApplyDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterBufferDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterContentDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterDetectDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptorFactory;
import org.apache.knox.test.TestUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class JsonFilterReaderTest {

  @Test
  public void testValueNumberWithBuffering() throws Exception {
    String input = "{ \"apps\" : {\"app\":[{\"id\":\"one\", \"progress\":100.0, \"startedTime\":1399975176760}]} }";

    UrlRewriteRulesDescriptor rulesConfig = UrlRewriteRulesDescriptorFactory.create();
    UrlRewriteFilterDescriptor filterConfig = rulesConfig.addFilter( "filter-1" );
    UrlRewriteFilterContentDescriptor contentConfig = filterConfig.addContent( "text/json" );
    UrlRewriteFilterBufferDescriptor bufferConfig = contentConfig.addBuffer( "$.apps.app[*]" );
    UrlRewriteFilterApplyDescriptor applyConfig = bufferConfig.addApply( "$.id", "test-rule" );
    assertNotNull(applyConfig);

    JsonFilterReader filter = new JsonFilterReader( new StringReader( input ), contentConfig );
    String output = IOUtils.toString( filter );
    assertThat( output, containsString( "\"startedTime\":1399975176760}" ) );
  }

  @Test
  public void testString() throws IOException {
    String inputJson = "\"abc\"";
    StringReader inputReader = new StringReader( inputJson );
    JsonFilterReader filterReader = new TestJsonFilterReader( inputReader, null );
    String outputJson = new String( IOUtils.toCharArray( filterReader ) );
    JsonAssert.with( outputJson ).assertThat( "$", is( "abc" ) );
  }

  @Test
  public void testNumber() throws IOException {
    int num = ThreadLocalRandom.current().nextInt();
    String inputJson = String.valueOf(num);
    StringReader inputReader = new StringReader( inputJson );
    JsonFilterReader filterReader = new TestJsonFilterReader( inputReader, null );
    String outputJson = new String( IOUtils.toCharArray( filterReader ) );
    JsonAssert.with( outputJson ).assertThat( "$", is( num ) );
  }

  @Test
  public void testBoolean() throws IOException {
    List<Boolean> booleans = Arrays.asList(true, false);
    for(boolean bool : booleans) {
      String inputJson = String.valueOf(bool);
      StringReader inputReader = new StringReader(inputJson);
      JsonFilterReader filterReader = new TestJsonFilterReader(inputReader, null);
      String outputJson = new String(IOUtils.toCharArray(filterReader));
      JsonAssert.with(outputJson).assertThat("$", is(bool));
    }
  }

  @Test
  public void testNull() throws IOException {
    String inputJson = "null";
    StringReader inputReader = new StringReader( inputJson );
    JsonFilterReader filterReader = new TestJsonFilterReader( inputReader, null );
    String outputJson = new String( IOUtils.toCharArray( filterReader ) );
    assertThat(inputJson, is(outputJson));
  }

  @Test
  public void testSimple() throws IOException {
    String inputJson = "{ \"test-name\" : \"test-value\" }";
    StringReader inputReader = new StringReader( inputJson );
    JsonFilterReader filterReader = new TestJsonFilterReader( inputReader, null );
    String outputJson = new String( IOUtils.toCharArray( filterReader ) );

    JsonAssert.with( outputJson ).assertThat( "name<test-name>", is( "value:null<test-value>" ) );
  }

  @Test
  public void testRootArray() throws Exception {
    String inputJson = "[\"test-value-1\",\"test-value-2\",\"test-value-3\"]";
    StringReader inputReader = new StringReader( inputJson );
    JsonFilterReader filterReader = new TestJsonFilterReader( inputReader, null );
    String outputJson = new String( IOUtils.toCharArray( filterReader ) );
    JsonAssert.with( outputJson ).assertThat( "$.[0]", is( "value:null<test-value-1>" ) );
    JsonAssert.with( outputJson ).assertThat( "$.[1]", is( "value:null<test-value-2>" ) );
    JsonAssert.with( outputJson ).assertThat( "$.[2]", is( "value:null<test-value-3>" ) );

    inputJson = "[777,42]";
    inputReader = new StringReader( inputJson );
    filterReader = new TestJsonFilterReader( inputReader, null );
    outputJson = new String( IOUtils.toCharArray( filterReader ) );
    JsonAssert.with( outputJson ).assertThat( "$.[0]", is( 777 ) );
    JsonAssert.with( outputJson ).assertThat( "$.[1]", is( 42 ) );
  }

  @Test
  public void testEmptyObject() throws IOException {
    String inputJson = "{}";
    StringReader inputReader = new StringReader( inputJson );
    JsonFilterReader filterReader = new TestJsonFilterReader( inputReader, null );
    String outputJson = new String( IOUtils.toCharArray( filterReader ) );

    assertThat( outputJson, is( "{}" ) );
  }

  @Test
  public void testEmptyArray() throws IOException {
    String inputJson = "[]";
    StringReader inputReader = new StringReader( inputJson );
    JsonFilterReader filterReader = new TestJsonFilterReader( inputReader, null );
    String outputJson = new String( IOUtils.toCharArray( filterReader ) );

    assertThat( outputJson, is( "[]" ) );
  }

  @Test
  public void testUnscopedStreaming() throws IOException {
    InputStream stream = TestUtils.getResourceStream( this.getClass(), "simple-values.json" );
    String input = IOUtils.toString( stream, StandardCharsets.UTF_8 );

    UrlRewriteRulesDescriptor rulesConfig = UrlRewriteRulesDescriptorFactory.create();
    UrlRewriteFilterDescriptor filterConfig = rulesConfig.addFilter( "filter=1" );
    UrlRewriteFilterContentDescriptor contentConfig = filterConfig.addContent( "text/json" );
    UrlRewriteFilterApplyDescriptor applyConfig = contentConfig.addApply( "$['test-str']", "test-rule" );
    assertNotNull(applyConfig);

    JsonFilterReader filter = new TestJsonFilterReader( new StringReader( input ), contentConfig );
    String output = IOUtils.toString( filter );

    JsonAssert.with( output ).assertThat( "name<test-str>", is( "value:null<text>" ) );
  }

  @Test
  public void testNamesWithDots() throws IOException {
    InputStream stream = TestUtils.getResourceStream( this.getClass(), "dotted-field-name.json" );
    String input = IOUtils.toString( stream, StandardCharsets.UTF_8 );

    UrlRewriteRulesDescriptor rulesConfig = UrlRewriteRulesDescriptorFactory.create();
    UrlRewriteFilterDescriptor filterConfig = rulesConfig.addFilter( "test-filter" );
    UrlRewriteFilterContentDescriptor contentConfig = filterConfig.addContent( "application/json" );
    //NOTE: The field names are rewritten first so the values rules need to match the rewritten name.
    contentConfig.addApply( "$.name<testField>", "test-rule" );
    contentConfig.addApply( "$.name<test_field>", "test-rule" );
    contentConfig.addApply( "$.name<test-field>", "test-rule" );
    contentConfig.addApply( "$['name<test.field>']", "test-rule" );

    JsonFilterReader filter = new TestJsonFilterReader( new StringReader( input ), contentConfig );
    String output = IOUtils.toString( filter );

    JsonAssert.with( output ).assertThat( "$['name<testField>']", is( "value:test-rule<testField value>" ) );
    JsonAssert.with( output ).assertThat( "$['name<test_field>']", is( "value:test-rule<test_field value>" ) );
    JsonAssert.with( output ).assertThat( "$['name<test-field>']", is( "value:test-rule<test-field value>" ) );
    JsonAssert.with( output ).assertThat( "$['name<test.field>']", is( "value:test-rule<test.field value>" ) );
  }

//  @Test
//  public void testJsonPathObject() throws IOException {
//    InputStream stream = TestUtils.getResourceStream( this.getClass(), "complex.json" );
//    String input = IOUtils.toString( stream, StandardCharsets.UTF_8 );
//
//    Object o;
//    o = JsonPath.read( "$", input, JsonNode.class );
//    assertThat( o, instanceOf( ObjectNode.class ) );
//    assertThat( o.toString(), startsWith( "{" ) );
//    o = JsonPath.read( "$['test-str']", input, JsonNode.class );
//    o = JsonPath.read( "$['test-obj-multi']", input, JsonNode.class );
//    o = JsonPath.read( "$['val']", (JsonNode)o, JsonNode.class );
//    JsonPath p = JsonPath.compile( "$['test-obj-multi']['val']" );
//    o = JsonPath.read( "$['test-obj-multi']['val']", input, JsonNode.class );
//    JsonNode pp = ((JsonNode)o).findParent("val");
//
//  }
//
//  @Test
//  public void testJsonPathArray() throws IOException {
//    InputStream stream = TestUtils.getResourceStream( this.getClass(), "array.json" );
//    String input = IOUtils.toString( stream, StandardCharsets.UTF_8 );
//
//    Object o;
//    o = JsonPath.read( "$", input, JsonNode.class );
//    o = JsonPath.read( "$[0]", input, JsonNode.class );
//    o = JsonPath.read( "$[*]", input, JsonNode.class );
//    o = JsonPath.read( "$['obj1-fld1']", (JsonNode)o, JsonNode.class );
//    o = JsonPath.read( "$[0]['obj1-fld1']", input, JsonNode.class );
//  }

  @Test
  public void testBuffered() throws IOException {
    InputStream stream = TestUtils.getResourceStream( this.getClass(), "simple-values.json" );
    String input = IOUtils.toString( stream, StandardCharsets.UTF_8 );

    UrlRewriteRulesDescriptor rulesConfig = UrlRewriteRulesDescriptorFactory.create();
    UrlRewriteFilterDescriptor filterConfig = rulesConfig.addFilter( "filter-1" );
    UrlRewriteFilterContentDescriptor contentConfig = filterConfig.addContent( "text/json" );
    UrlRewriteFilterBufferDescriptor bufferConfig = contentConfig.addBuffer( "$" );
    UrlRewriteFilterApplyDescriptor applyConfig = bufferConfig.addApply( "$['name<test-str>']", "test-rule" );
    assertNotNull(applyConfig);

    JsonFilterReader filter = new TestJsonFilterReader( new StringReader( input ), contentConfig );
    String output = IOUtils.toString( filter );

    JsonAssert.with( output ).assertThat( "name<test-str>", is( "value:test-rule<text>" ) );
  }

  @Test
  public void testBufferedDetectApply() throws IOException {
    InputStream stream = TestUtils.getResourceStream( this.getClass(), "properties.json" );
    String input = IOUtils.toString( stream, StandardCharsets.UTF_8 );

    UrlRewriteRulesDescriptor rulesConfig = UrlRewriteRulesDescriptorFactory.create();
    UrlRewriteFilterDescriptor filterConfig = rulesConfig.addFilter( "filter-1" );
    UrlRewriteFilterContentDescriptor contentConfig = filterConfig.addContent( "text/json" );
    UrlRewriteFilterBufferDescriptor bufferConfig = contentConfig.addBuffer( "$.name<properties>.*.name<property>" );
    UrlRewriteFilterDetectDescriptor detectConfig = bufferConfig.addDetect( "$.name<property-name>", "test-name-2" );
    UrlRewriteFilterApplyDescriptor applyConfig = detectConfig.addApply( "$.name<property-value>", "test-rule-2" );
    assertNotNull(applyConfig);

    JsonFilterReader filter = new TestJsonFilterReader( new StringReader( input ), contentConfig );
    String output = IOUtils.toString( filter );

    JsonAssert.with( output ).assertThat( "name<properties>[0].name<property>.name<property-name>", is( "test-name-1" ) );
    JsonAssert.with( output ).assertThat( "name<properties>[0].name<property>.name<property-value>", is( "test-value-1" ) );
    JsonAssert.with( output ).assertThat( "name<properties>[1].name<property>.name<property-name>", is( "test-name-2" ) );
    JsonAssert.with( output ).assertThat( "name<properties>[1].name<property>.name<property-value>", is( "value:test-rule-2<test-value-2>" ) );
    JsonAssert.with( output ).assertThat( "name<properties>[2].name<property>.name<property-name>", is( "test-name-3" ) );
    JsonAssert.with( output ).assertThat( "name<properties>[2].name<property>.name<property-value>", is( "test-value-3" ) );
  }

  @Test
  public void testBufferedApply() throws IOException {
    InputStream stream = TestUtils.getResourceStream( this.getClass(), "properties.json" );
    String input = IOUtils.toString( stream, StandardCharsets.UTF_8 );

    UrlRewriteRulesDescriptor rulesConfig = UrlRewriteRulesDescriptorFactory.create();
    UrlRewriteFilterDescriptor filterConfig = rulesConfig.addFilter( "filter-1" );
    UrlRewriteFilterContentDescriptor contentConfig = filterConfig.addContent( "text/json" );
    UrlRewriteFilterBufferDescriptor bufferConfig = contentConfig.addBuffer( "$.name<properties>.*.name<property>" );
    UrlRewriteFilterApplyDescriptor applyConfig = bufferConfig.addApply( "$.name<property-value>", "test-rule" );
    assertNotNull(applyConfig);

    JsonFilterReader filter = new TestJsonFilterReader( new StringReader( input ), contentConfig );
    String output = IOUtils.toString( filter );

    JsonAssert.with( output ).assertThat( "name<properties>[0].name<property>.name<property-name>", is( "test-name-1" ) );
    JsonAssert.with( output ).assertThat( "name<properties>[0].name<property>.name<property-value>", is( "value:test-rule<test-value-1>" ) );
    JsonAssert.with( output ).assertThat( "name<properties>[1].name<property>.name<property-name>", is( "test-name-2" ) );
    JsonAssert.with( output ).assertThat( "name<properties>[1].name<property>.name<property-value>", is( "value:test-rule<test-value-2>" ) );
    JsonAssert.with( output ).assertThat( "name<properties>[2].name<property>.name<property-name>", is( "test-name-3" ) );
    JsonAssert.with( output ).assertThat( "name<properties>[2].name<property>.name<property-value>", is( "value:test-rule<test-value-3>" ) );
  }

  @Test
  public void testBufferedMultiApply() throws IOException {
    InputStream stream = TestUtils.getResourceStream( this.getClass(), "properties.json" );
    String input = IOUtils.toString( stream, StandardCharsets.UTF_8 );

    UrlRewriteRulesDescriptor rulesConfig = UrlRewriteRulesDescriptorFactory.create();
    UrlRewriteFilterDescriptor filterConfig = rulesConfig.addFilter( "filter-1" );
    UrlRewriteFilterContentDescriptor contentConfig = filterConfig.addContent( "text/json" );
    UrlRewriteFilterBufferDescriptor bufferConfig = contentConfig.addBuffer( "$.name<properties>" );
    UrlRewriteFilterApplyDescriptor applyConfig = bufferConfig.addApply( "$.*.name<property>.name<property-value>", "test-rule" );
    assertNotNull(applyConfig);

    JsonFilterReader filter = new TestJsonFilterReader( new StringReader( input ), contentConfig );
    String output = IOUtils.toString( filter );

    JsonAssert.with( output ).assertThat( "name<properties>[0].name<property>.name<property-name>", is( "test-name-1" ) );
    JsonAssert.with( output ).assertThat( "name<properties>[0].name<property>.name<property-value>", is( "value:test-rule<test-value-1>" ) );
    JsonAssert.with( output ).assertThat( "name<properties>[1].name<property>.name<property-name>", is( "test-name-2" ) );
    JsonAssert.with( output ).assertThat( "name<properties>[1].name<property>.name<property-value>", is( "value:test-rule<test-value-2>" ) );
    JsonAssert.with( output ).assertThat( "name<properties>[2].name<property>.name<property-name>", is( "test-name-3" ) );
    JsonAssert.with( output ).assertThat( "name<properties>[2].name<property>.name<property-value>", is( "value:test-rule<test-value-3>" ) );
  }

  @Test
  public void testInvalidConfigShouldThrowException() throws Exception {
    String input = "{\"test-name\":\"test-value\"}";

    UrlRewriteRulesDescriptor rulesConfig = UrlRewriteRulesDescriptorFactory.create();
    UrlRewriteFilterDescriptor filterConfig = rulesConfig.addFilter( "filter-1" );
    UrlRewriteFilterContentDescriptor contentConfig = filterConfig.addContent( "*/json" );
    contentConfig.addApply( "/root/@url", "test-rule" );

    try {
      JsonFilterReader filter = new TestJsonFilterReader( new StringReader( input ), contentConfig );
      IOUtils.toString( filter );
      fail( "Should have thrown an IllegalArgumentException." );
    } catch ( IOException e ) {
      fail( "Should have thrown an IllegalArgumentException." );
    } catch ( IllegalArgumentException e ) {
      assertThat( e.getMessage(), containsString( "/root/@url" ) );
    }
  }

  @Test
  public void testEscapeCharactersBugKnox616() throws Exception {
    String input, output;
    JsonFilterReader filter;

    input = "{ \"test-name\" : \"\\\"\" }";
    filter = new NoopJsonFilterReader( new StringReader( input ), null );
    output = IOUtils.toString( filter );
    assertThat( output, is( "{\"test-name\":\"\\\"\"}" ) );

    input = "{\"test-name\":\"\\b\"}";
    filter = new NoopJsonFilterReader( new StringReader( input ), null );
    output = IOUtils.toString( filter );
    assertThat( output, is( "{\"test-name\":\"\\b\"}" ) );
  }
}
