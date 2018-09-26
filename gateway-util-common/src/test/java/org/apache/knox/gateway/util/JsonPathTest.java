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
package org.apache.knox.gateway.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class JsonPathTest {

  @Test
  public void testCompileInvalidExpressions() {
    String[] paths = {
        null,
        "",
        "$.",
        "$[",
        "$[]",
        ".",
        "[",
        "$.[one]",
        "$.['one']",
        "(",
        ")",
        "@",
        "$?",
        "$(",
        "$)",
        "$@",
        "$?"
    };

    for( String path : paths ) {
      try {
        JsonPath.compile( path );
        fail( "Expected IllegalArgumentException" );
      } catch( IllegalArgumentException e ) {
      }
    }

  }

  @Test
  public void testCompileUnsupportedExpressions() {
    String[] paths = {
        "$..book[-1:]",
        "$..book[,1]",
        "$..book[0,1]",
        "$..book[:2]",
        "$..book[0:7:2]",
        "$..book[(@.length-1)]",
        "$..book[?(@.isbn)]",
        "$..book[?(@.price<10)]"
    };

    for( String path : paths ) {
      try {
        JsonPath.compile( path );
        fail( "Expected IllegalArgumentException for " + path );
      } catch( IllegalArgumentException e ) {
        // Expected.
      }
    }

  }

  @Test
  public void testCompileValidExpressions() {

    JsonPath.Expression expression;
    JsonPath.Segment[] segments;

    expression = JsonPath.compile( "$" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 1 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[0].getField(), nullValue() );
    assertThat( segments[0].getIndex(), is( -1 ) );

    expression = JsonPath.compile( "$.one" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 2 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[0].getField(), nullValue() );
    assertThat( segments[1].getField(), is( "one" ) );

    expression = JsonPath.compile( "$.'one'" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 2 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[0].getField(), nullValue() );
    assertThat( segments[1].getField(), is( "one" ) );

    expression = JsonPath.compile( "$[one]" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 2 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[0].getField(), nullValue() );
    assertThat( segments[1].getField(), is( "one" ) );

    expression = JsonPath.compile( "$['one']" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 2 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[0].getField(), nullValue() );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[1].getField(), is( "one" ) );
    assertThat( segments[1].getIndex(), is( -1 ) );

    expression = JsonPath.compile( "$[7]" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 2 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[0].getField(), nullValue() );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.INDEX ) );
    assertThat( segments[1].getField(), nullValue() );
    assertThat( segments[1].getIndex(), is( 7 ) ) ;

    expression = JsonPath.compile( "$.7" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 2 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[0].getField(), nullValue() );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.INDEX ) );
    assertThat( segments[1].getField(), nullValue() );
    assertThat( segments[1].getIndex(), is( 7 ) ) ;

    expression = JsonPath.compile( "$['7']" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 2 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[0].getField(), nullValue() );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[1].getField(), is( "7" ) );
    assertThat( segments[1].getIndex(), is( -1 ) ) ;

    expression = JsonPath.compile( "$.'7'" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 2 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[0].getField(), nullValue() );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[1].getField(), is( "7" ) );
    assertThat( segments[1].getIndex(), is( -1 ) ) ;

    expression = JsonPath.compile( "$.*.field" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 3 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.WILD ) );
    assertThat( segments[1].getField(), nullValue() );
    assertThat( segments[2].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[2].getField(), is( "field" ) );

    expression = JsonPath.compile( "$.**.field" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 3 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.GLOB ) );
    assertThat( segments[1].getField(), is( nullValue() ) );
    assertThat( segments[2].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[2].getField(), is( "field" ) );

    expression = JsonPath.compile( "$[*][field]" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 3 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.WILD ) );
    assertThat( segments[1].getField(), nullValue() );
    assertThat( segments[2].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[2].getField(), is( "field" ) );

    expression = JsonPath.compile( "$[**]['field']" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 3 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.GLOB ) );
    assertThat( segments[1].getField(), nullValue() );
    assertThat( segments[2].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[2].getField(), is( "field" ) );

    expression = JsonPath.compile( "$..field" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 3 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.GLOB ) );
    assertThat( segments[1].getField(), nullValue() );
    assertThat( segments[2].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[2].getField(), is( "field" ) );

    expression = JsonPath.compile( "$[one].two" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 3 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[1].getField(), is( "one" ) );
    assertThat( segments[2].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[2].getField(), is( "two" ) );

    expression = JsonPath.compile( "$.one[two]" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 3 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[1].getField(), is( "one") );
    assertThat( segments[2].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[2].getField(), is( "two" ) );

    expression = JsonPath.compile( "$..*" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 3 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[0].getField(), nullValue() );
    assertThat( segments[0].getIndex(), is( -1 ) );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.GLOB ) );
    assertThat( segments[1].getField(), nullValue() );
    assertThat( segments[1].getIndex(), is( -1 ) );
    assertThat( segments[2].getType(), is( JsonPath.Segment.Type.WILD ) );
    assertThat( segments[2].getField(), nullValue() );
    assertThat( segments[2].getIndex(), is( -1 ) );
  }

  @Test
  public void testCompileQuotedExpressions() {
    JsonPath.Expression expression;
    JsonPath.Segment[] segments;

    expression = JsonPath.compile( "$.fieldName" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 2 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[1].getField(), is( "fieldName" ) );

    expression = JsonPath.compile( "$[fieldName]" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 2 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[1].getField(), is( "fieldName" ) );

    expression = JsonPath.compile( "$['fieldName']" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 2 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[1].getField(), is( "fieldName" ) );

    expression = JsonPath.compile( "$.field_name" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 2 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[1].getField(), is( "field_name" ) );

    expression = JsonPath.compile( "$['field_name']" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 2 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[1].getField(), is( "field_name" ) );

    expression = JsonPath.compile( "$[field_name]" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 2 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[1].getField(), is( "field_name" ) );

    expression = JsonPath.compile( "$['field-name']" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 2 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[1].getField(), is( "field-name" ) );

    expression = JsonPath.compile( "$[field-name]" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 2 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[1].getField(), is( "field-name" ) );

    expression = JsonPath.compile( "$.field-name" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 2 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[1].getField(), is( "field-name" ) );

    expression = JsonPath.compile( "$['field.name']" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 2 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[1].getField(), is( "field.name" ) );

    expression = JsonPath.compile( "$[field.name]" );
    assertThat( expression, notNullValue() );
    segments = expression.getSegments();
    assertThat( segments, notNullValue() );
    assertThat( segments.length, is( 2 ) );
    assertThat( segments[0].getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( segments[1].getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( segments[1].getField(), is( "field.name" ) );

  }

  @Test
  public void testEvaluateObjects() throws IOException {
    String json;
    JsonPath.Segment seg;
    List<JsonPath.Match> matches;
    JsonPath.Match match;
    JsonPath.Match parent;
    JsonNode root;
    JsonNode node;
    JsonPath.Expression expression;

    JsonFactory factory = new JsonFactory();
    ObjectMapper mapper = new ObjectMapper( factory );


    json = "{ \"field\" : \"value\" }";
    root = mapper.readTree( json );
    assertThat( root, notNullValue() );

    expression = JsonPath.compile( "$.field" );
    matches = expression.evaluate( root );
    assertThat( matches, notNullValue() );
    assertThat( matches.size(), is( 1 ) );
    match = matches.get( 0 );
    assertThat( matches,  notNullValue() );
    seg = matches.get( 0 ).getSegment();
    assertThat( seg, notNullValue() );
    assertThat( seg.getType(), is( JsonPath.Segment.Type.FIELD ) );
    assertThat( seg.getField(), is( "field" ) );
    node = match.getNode();
    assertThat( node, notNullValue() );
    assertThat( node.getNodeType(), is( JsonNodeType.STRING ) );
    assertThat( node.asText(), is( "value" ) );
    parent = match.getParent();
    assertThat( parent, notNullValue() );
    assertThat( parent.getNode(), sameInstance( root ) );
    assertThat( parent.getParent(), nullValue() );
    assertThat( parent.getSegment().getType(), is( JsonPath.Segment.Type.ROOT ) );


    json = "{ \"outer\" : { \"inner\" : \"value\"}  }";
    root = mapper.readTree( json );
    assertThat( root, notNullValue() );

    expression = JsonPath.compile( "$.outer.inner" );
    matches = expression.evaluate( root );
    match = matches.get( 0 );
    assertThat( match, notNullValue() );
    assertThat( match.getField(), is( "inner" ) );
    seg = match.getSegment();
    assertThat( seg, notNullValue() );
    assertThat( seg.getField(), is( "inner" ) );
    assertThat( seg.getType(), is( JsonPath.Segment.Type.FIELD ) );

    node = match.getNode();
    assertThat( node, notNullValue() );
    assertThat( node.asText(), is( "value" ) );

    parent = match.getParent();
    assertThat( parent, notNullValue() );
    assertThat( parent.getField(), is( "outer") );
    assertThat( parent.getNode().getNodeType(), is( JsonNodeType.OBJECT ) );

    parent = parent.getParent();
    assertThat( parent.getSegment().getType(), is( JsonPath.Segment.Type.ROOT ) );
    assertThat( parent.getNode().getNodeType(), is( JsonNodeType.OBJECT ) );


    json = "{ \"outer\" : { \"inner\" : \"value\"}  }";
    root = mapper.readTree( json );
    assertThat( root, notNullValue() );

    expression = JsonPath.compile( "$.*.inner" );
    matches = expression.evaluate( root );
    assertThat( matches.size(), is( 1 ) ) ;
    match = matches.get( 0 );
    assertThat( match.getField(), is( "inner" ) );
    assertThat( match.getNode().asText(), is( "value" ) );
  }

  @Test
  public void testEvaluateArrays() throws IOException {
    String json;
    JsonPath.Segment seg;
    List<JsonPath.Match> matches;
    JsonPath.Match match;
    JsonPath.Match parent;
    JsonNode root;
    JsonNode node;
    JsonPath.Expression expression;

    JsonFactory factory = new JsonFactory();
    ObjectMapper mapper = new ObjectMapper( factory );

    json = "[ \"outer-array\", { \"nested-field\" : \"nested-object\" }, [ \"nested-array\" ] ]";
    root = mapper.readTree( json );
    assertThat( root, notNullValue() );

    expression = JsonPath.compile( "$[0]" );
    matches = expression.evaluate( root );
    assertThat( matches.size(), is( 1 ) ) ;
    match = matches.get( 0 );
    assertThat( match.getIndex(), is( 0 ) );
    assertThat( match.getSegment().getIndex(), is( 0 ) );
    assertThat( match.getNode().asText(), is( "outer-array" ) );

    expression = JsonPath.compile( "$[1][nested-field]" );
    matches = expression.evaluate( root );
    assertThat( matches.size(), is( 1 ) ) ;
    match = matches.get( 0 );
    assertThat( match.getField(), is( "nested-field" ) );
    assertThat( match.getNode().asText(), is( "nested-object" ) );

    expression = JsonPath.compile( "$[*][nested-field]" );
    matches = expression.evaluate( root );
    assertThat( matches.size(), is( 1 ) ) ;
    match = matches.get( 0 );
    assertThat( match.getField(), is( "nested-field" ) );
    assertThat( match.getNode().asText(), is( "nested-object" ) );

    json = "{ \"array\" : [ { \"name\" : \"value-1\" }, { \"name\" : \"value-2 \"}, { \"name\" : \"value-3\" } ] }";
    root = mapper.readTree( json );
    expression = JsonPath.compile( "$.array.*.name" );
    matches = expression.evaluate( root );
    assertThat( matches.size(), is( 3 ) ) ;
    expression = JsonPath.compile( "$.array[*].name" );
    matches = expression.evaluate( root );
    assertThat( matches.size(), is( 3 ) ) ;
    expression = JsonPath.compile( "$.array[*][name]" );
    matches = expression.evaluate( root );
    assertThat( matches.size(), is( 3 ) ) ;

    expression = JsonPath.compile( "$.array" );
    matches = expression.evaluate( root );
    match = matches.get( 0 );
    expression = JsonPath.compile( "$.*.name" );
    matches = expression.evaluate( match.getNode() );
    Assert.assertThat( matches.size(), is( 3 ) );
  }

  @Test
  public void testGlobMatching() throws IOException {
    String json;
    JsonPath.Segment seg;
    List<JsonPath.Match> matches;
    JsonPath.Match parent;
    JsonNode root;
    JsonNode node;
    JsonPath.Expression expression;
    Set<String> matchValues;

    JsonFactory factory = new JsonFactory();
    ObjectMapper mapper = new ObjectMapper( factory );

    json = "{ \"field\" : \"value\" }";
    root = mapper.readTree( json );
    assertThat( root, notNullValue() );

    expression = JsonPath.compile( "$..field" );
    matches = expression.evaluate( root );
    matchValues = new HashSet<>();
    assertThat( matches.size(), is( 1 ) );
    for( JsonPath.Match match : matches ) {
      matchValues.add( match.getNode().asText() );
    }
    assertThat( matchValues, hasItem( "value" ) );

    json = "{ \"field-1\" : { \"field-1-1\" : { \"field-1-1-1\" : \"value-1-1-1\", \"field\" : \"value-A\" }, \"field\" : \"value-B\"}, \"field-2\" : { \"field-2-1\" : { \"field-2-1-1\" : \"value-2-1-1\", \"field\" : \"value-C\" }, \"field\" : \"value-D\" }, \"field\" : \"value-E\" }";
    root = mapper.readTree( json );
    assertThat( root, notNullValue() );

    expression = JsonPath.compile( "$..field" );
    matches = expression.evaluate( root );
    assertThat( matches.size(), is( 5 ) );
    matchValues = new HashSet<>();
    for( JsonPath.Match match : matches ) {
      matchValues.add( match.getNode().asText() );
    }
    assertThat( matchValues, hasItem( "value-A" ) );
    assertThat( matchValues, hasItem( "value-B" ) );
    assertThat( matchValues, hasItem( "value-C" ) );
    assertThat( matchValues, hasItem( "value-D" ) );
    assertThat( matchValues, hasItem( "value-E" ) );

  }

}