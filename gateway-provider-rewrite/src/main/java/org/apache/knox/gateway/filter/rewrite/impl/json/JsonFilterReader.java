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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterApplyDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterBufferDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterContentDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterDetectDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterGroupDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterPathDescriptor;
import org.apache.knox.gateway.filter.rewrite.i18n.UrlRewriteMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.util.JsonPath;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.List;
import java.util.Stack;
import java.util.regex.Pattern;

class JsonFilterReader extends Reader {

  private static final UrlRewriteMessages LOG = MessagesFactory.get( UrlRewriteMessages.class );

  private static final UrlRewriteFilterPathDescriptor.Compiler<JsonPath.Expression> JPATH_COMPILER = new JsonPathCompiler();
  private static final UrlRewriteFilterPathDescriptor.Compiler<Pattern> REGEX_COMPILER = new RegexCompiler();

  private JsonFactory factory;
  private JsonParser parser;
  private JsonGenerator generator;
  private ObjectMapper mapper;

  private Reader reader;
  private int offset;
  private StringWriter writer;
  private StringBuffer buffer;
  private Stack<Level> stack;
  private Level bufferingLevel;
  private UrlRewriteFilterBufferDescriptor bufferingConfig;
  private UrlRewriteFilterGroupDescriptor config;

  JsonFilterReader( Reader reader, UrlRewriteFilterContentDescriptor config ) throws IOException {
    this.reader = reader;
    factory = new JsonFactory();
    mapper = new ObjectMapper();
    parser = factory.createParser( reader );
    writer = new StringWriter();
    buffer = writer.getBuffer();
    offset = 0;
    generator = factory.createGenerator( writer );
    stack = new Stack<>();
    bufferingLevel = null;
    bufferingConfig = null;
    this.config = config;
  }

  @Override
  public int read( char[] destBuffer, int destOffset, int destCount ) throws IOException {
    int count = 0;
    int available = buffer.length() - offset;

    if( available == 0 ) {
      JsonToken token = parser.nextToken();
      if( token == null ) {
        count = -1;
      } else {
        processCurrentToken();
        available = buffer.length() - offset;
      }
    }

    if( available > 0 ) {
      count = Math.min( destCount, available );
      buffer.getChars( offset, offset+count, destBuffer, destOffset );
      offset += count;
      if( offset == buffer.length() ) {
        offset = 0;
        buffer.setLength( 0 );
      }
    }

    return count;
  }

  private void processCurrentToken() throws IOException {
    switch( parser.getCurrentToken() ) {
      case START_OBJECT:
        processStartObject();
        break;
      case END_OBJECT:
        processEndObject();
        break;
      case START_ARRAY:
        processStartArray();
        break;
      case END_ARRAY:
        processEndArray();
        break;
      case FIELD_NAME:
        processFieldName(); // Could be the name of an object, array or value.
        break;
      case VALUE_STRING:
        processValueString();
        break;
      case VALUE_NUMBER_INT:
      case VALUE_NUMBER_FLOAT:
        processValueNumber();
        break;
      case VALUE_TRUE:
      case VALUE_FALSE:
        processValueBoolean();
        break;
      case VALUE_NULL:
        processValueNull();
        break;
      case NOT_AVAILABLE:
        // Ignore it.
        break;
    }
    generator.flush();
  }

  private Level pushLevel( String field, JsonNode node, JsonNode scopeNode, UrlRewriteFilterGroupDescriptor scopeConfig ) {
    if( !stack.isEmpty() ) {
      Level top = stack.peek();
      if( scopeNode == null ) {
        scopeNode = top.scopeNode;
        scopeConfig = top.scopeConfig;
      }
    }
    Level level = new Level( field, node, scopeNode, scopeConfig );
    stack.push( level );
    return level;
  }

  private void processStartObject() throws IOException {
    JsonNode node;
    Level child;
    Level parent;
    if( stack.isEmpty() ) {
      node = mapper.createObjectNode();
      child = pushLevel( null, node, node, config );
    } else {
      child = stack.peek();
      if( child.node == null ) {
        child.node = mapper.createObjectNode();
        parent = stack.get( stack.size()-2 );
        switch( parent.node.asToken() ) {
          case START_ARRAY:
            ((ArrayNode)parent.node ).add( child.node );
            break;
          case START_OBJECT:
            ((ObjectNode)parent.node ).set( child.field, child.node );
            break;
          default:
            throw new IllegalStateException();
        }
      } else if( child.isArray() ) {
        parent = child;
        node = mapper.createObjectNode();
        child = pushLevel( null, node, null, null );
        ((ArrayNode)parent.node ).add( child.node );
      } else {
        throw new IllegalStateException();
      }
    }
    if( bufferingLevel == null && !startBuffering( child ) ) {
      generator.writeStartObject();
    }
  }

  private void processEndObject() throws IOException {
    Level child;
    Level parent;
    child = stack.pop();
    if( child.equals(bufferingLevel) ) {
      filterBufferedNode( child );
      mapper.writeTree( generator, child.node );
      bufferingLevel = null;
      bufferingConfig = null;
    } else if( bufferingLevel == null ) {
      generator.writeEndObject();
      if( !stack.isEmpty() ) {
        parent = stack.peek();
        switch( parent.node.asToken() ) {
          case START_ARRAY:
            ((ArrayNode)parent.node ).removeAll();
            break;
          case START_OBJECT:
            ((ObjectNode)parent.node ).removeAll();
            break;
          default:
            throw new IllegalStateException();
        }
      }
    }
  }

  private void processStartArray() throws IOException {
    JsonNode node;
    Level child;
    Level parent;
    if( stack.isEmpty() ) {
      node = mapper.createArrayNode();
      child = pushLevel( null, node, node, config );
    } else {
      child = stack.peek();
      if( child.node == null ) {
        child.node = mapper.createArrayNode();
        parent = stack.get( stack.size() - 2 );
        switch( parent.node.asToken() ) {
          case START_ARRAY:
            ((ArrayNode)parent.node ).add( child.node );
            break;
          case START_OBJECT:
            ((ObjectNode)parent.node ).set( child.field, child.node );
            break;
          default:
            throw new IllegalStateException();
        }
      } else if( child.isArray() ) {
        parent = child;
        child = pushLevel( null, mapper.createArrayNode(), null, null );
        ((ArrayNode)parent.node ).add( child.node );
      } else {
        throw new IllegalStateException();
      }
    }
    if( bufferingLevel == null && !startBuffering( child ) ) {
      generator.writeStartArray();
    }
  }

  private void processEndArray() throws IOException {
    Level child;
    Level parent;
    child = stack.pop();
    if( child.equals(bufferingLevel) ) {
      filterBufferedNode( child );
      mapper.writeTree( generator, child.node );
      bufferingLevel = null;
      bufferingConfig = null;
    } else if( bufferingLevel == null ) {
      generator.writeEndArray();
      if( !stack.isEmpty() ) {
        parent = stack.peek();
        switch( parent.node.asToken() ) {
          case START_ARRAY:
            ((ArrayNode)parent.node ).removeAll();
            break;
          case START_OBJECT:
            ((ObjectNode)parent.node ).removeAll();
            break;
          default:
            throw new IllegalStateException();
        }
      }
    }
  }

  private void processFieldName() throws IOException {
    Level child = pushLevel( parser.getCurrentName(), null, null, null );
    try {
      child.field = filterFieldName( child.field );
    } catch( Exception e ) {
      LOG.failedToFilterFieldName( child.field, e );
      // Write original name.
    }
    if( bufferingLevel == null ) {
      generator.writeFieldName( child.field );
    }
  }

  private void processValueString() throws IOException {
    Level child;
    Level parent;
    String value = null;
    if(stack.isEmpty()) {
      generator.writeString( parser.getText() );
      return;
    }
    parent = stack.peek();
    if( parent.isArray() ) {
      ArrayNode array = (ArrayNode)parent.node;
      array.add( parser.getText() );
      if( bufferingLevel == null ) {
        value = filterStreamValue( parent );
        array.set( array.size()-1, new TextNode( value ) );
      } else {
        array.removeAll();
      }
    } else {
      child = stack.pop();
      parent = stack.peek();
      ((ObjectNode)parent.node ).put( child.field, parser.getText() );
      if( bufferingLevel == null ) {
        child.node = parent.node; // Populate the JsonNode of the child for filtering.
        value = filterStreamValue( child );
      }
    }
    if( bufferingLevel == null ) {
      if( parent.node.isArray() ) {
        ((ArrayNode)parent.node).removeAll();
      } else {
        ((ObjectNode)parent.node).removeAll();
      }
      generator.writeString( value );
    }
  }

  private void processValueNumber() throws IOException {
    Level child;
    Level parent;
    if(stack.isEmpty()) {
      processedUnbufferedValueNumber();
      return;
    }
    parent = stack.peek();
    if( parent.isArray() ) {
      if( bufferingLevel != null ) {
        ArrayNode array = (ArrayNode)parent.node;
        processBufferedArrayValueNumber( array );
      }
    } else {
      child = stack.pop();
      if( bufferingLevel != null ) {
        parent = stack.peek();
        ObjectNode object = (ObjectNode)parent.node;
        processBufferedFieldValueNumber( child, object );
      }
    }
    if( bufferingLevel == null ) {
      processedUnbufferedValueNumber();
    }
  }

  private void processedUnbufferedValueNumber() throws IOException {
    switch( parser.getNumberType() ) {
      case INT:
        generator.writeNumber( parser.getIntValue() );
        break;
      case LONG:
        generator.writeNumber( parser.getLongValue() );
        break;
      case BIG_INTEGER:
        generator.writeNumber( parser.getBigIntegerValue() );
        break;
      case FLOAT:
        generator.writeNumber( parser.getFloatValue() );
        break;
      case DOUBLE:
        generator.writeNumber( parser.getDoubleValue() );
        break;
      case BIG_DECIMAL:
        generator.writeNumber( parser.getDecimalValue() );
        break;
    }
  }

  private void processBufferedFieldValueNumber( Level child, ObjectNode object ) throws IOException {
    //object.put( child.field, parser.getDecimalValue() );
    switch( parser.getNumberType() ) {
      case INT:
        object.put( child.field, parser.getIntValue() );
        break;
      case LONG:
        object.put( child.field, parser.getLongValue() );
        break;
      case BIG_INTEGER:
        object.put( child.field, parser.getDecimalValue() );
        break;
      case FLOAT:
        object.put( child.field, parser.getFloatValue() );
        break;
      case DOUBLE:
        object.put( child.field, parser.getDoubleValue() );
        break;
      case BIG_DECIMAL:
        object.put( child.field, parser.getDecimalValue() );
        break;
    }
  }

  private void processBufferedArrayValueNumber( ArrayNode array ) throws IOException {
    //array.add( parser.getDecimalValue() );
    switch( parser.getNumberType() ) {
      case INT:
        array.add( parser.getIntValue() );
        break;
      case LONG:
        array.add( parser.getLongValue() );
        break;
      case BIG_INTEGER:
        array.add( parser.getDecimalValue() );
        break;
      case FLOAT:
        array.add( parser.getFloatValue() );
        break;
      case DOUBLE:
        array.add( parser.getDoubleValue() );
        break;
      case BIG_DECIMAL:
        array.add( parser.getDecimalValue() );
        break;
    }
  }

  private void processValueBoolean() throws IOException {
    Level child;
    Level parent;
    if(stack.isEmpty()) {
      generator.writeBoolean(parser.getBooleanValue());
      return;
    }
    parent = stack.peek();
    if( parent.isArray() ) {
      ((ArrayNode)parent.node ).add( parser.getBooleanValue() );
      //dump();
      if( bufferingLevel == null ) {
        ((ArrayNode)parent.node ).removeAll();
      }
    } else {
      child = stack.pop();
      parent = stack.peek();
      ((ObjectNode)parent.node ).put( child.field, parser.getBooleanValue() );
      //dump();
      if( bufferingLevel == null ) {
        ((ObjectNode)parent.node ).remove( child.field );
      }
    }
    if( bufferingLevel == null ) {
      generator.writeBoolean( parser.getBooleanValue() );
    }
  }

  private void processValueNull() throws IOException {
    Level child;
    if(stack.isEmpty()) {
      generator.writeNull();
      return;
    }
    Level parent = stack.peek();
    if( parent.isArray() ) {
      ((ArrayNode)parent.node ).addNull();
      //dump();
      if( bufferingLevel == null ) {
        ((ArrayNode)parent.node ).removeAll();
      }
    } else {
      child = stack.pop();
      parent = stack.peek();
      ((ObjectNode)parent.node ).putNull( child.field );
      //dump();
      if( bufferingLevel == null ) {
        ((ObjectNode)parent.node ).remove( child.field );
      }
    }
    if( bufferingLevel == null ) {
      generator.writeNull();
    }
  }

  protected boolean startBuffering( Level node ) {
    boolean buffered = false;
    UrlRewriteFilterGroupDescriptor scope = node.scopeConfig;
    if( scope != null ) {
      for( UrlRewriteFilterPathDescriptor selector : scope.getSelectors() ) {
        JsonPath.Expression path = (JsonPath.Expression)selector.compiledPath( JPATH_COMPILER );
        List<JsonPath.Match> matches = path.evaluate( node.scopeNode );
        if( matches != null && !matches.isEmpty() ) {
          if( selector instanceof UrlRewriteFilterBufferDescriptor ) {
            bufferingLevel = node;
            bufferingConfig = (UrlRewriteFilterBufferDescriptor)selector;
            buffered = true;
          }
          break;
        }
      }
    }
    return buffered;
  }

  protected String filterStreamValue( Level node ) {
    String value;
    if( node.isArray() ) {
      value = node.node.get( 0 ).asText();
    } else {
      value = node.node.get( node.field ).asText();
    }
    String rule = null;
    UrlRewriteFilterGroupDescriptor scope = node.scopeConfig;
    //TODO: Scan the top level apply rules for the first match.
    if( scope != null ) {
      for( UrlRewriteFilterPathDescriptor selector : scope.getSelectors() ) {
        JsonPath.Expression path = (JsonPath.Expression)selector.compiledPath( JPATH_COMPILER );
        List<JsonPath.Match> matches = path.evaluate( node.scopeNode );
        if( matches != null && !matches.isEmpty() ) {
          JsonPath.Match match = matches.get( 0 );
          if( match.getNode().isTextual() && selector instanceof UrlRewriteFilterApplyDescriptor ) {
            UrlRewriteFilterApplyDescriptor apply = (UrlRewriteFilterApplyDescriptor)selector;
            rule = apply.rule();
            break;
          }
        }
      }
    }
    try {
      value = filterValueString( node.field, value, rule );
      if( node.isArray() ) {
        ((ArrayNode)node.node).set( 0, new TextNode( value ) );
      } else {
        ((ObjectNode)node.node).put( node.field, value );
      }
    } catch( Exception e ) {
      LOG.failedToFilterValue( value, rule, e );
    }
    return value;
  }

  private void filterBufferedNode( Level node ) {
    for( UrlRewriteFilterPathDescriptor selector : bufferingConfig.getSelectors() ) {
      JsonPath.Expression path = (JsonPath.Expression)selector.compiledPath( JPATH_COMPILER );
      List<JsonPath.Match> matches = path.evaluate( node.node );
      for( JsonPath.Match match : matches ) {
        if( selector instanceof UrlRewriteFilterApplyDescriptor ) {
          if( match.getNode().isTextual() ) {
            filterBufferedValue( match, (UrlRewriteFilterApplyDescriptor)selector );
          }
        } else if( selector instanceof UrlRewriteFilterDetectDescriptor ) {
          UrlRewriteFilterDetectDescriptor detectConfig = (UrlRewriteFilterDetectDescriptor)selector;
          JsonPath.Expression detectPath = (JsonPath.Expression)detectConfig.compiledPath( JPATH_COMPILER );
          List<JsonPath.Match> detectMatches = detectPath.evaluate( node.node );
          for( JsonPath.Match detectMatch : detectMatches ) {
            if( detectMatch.getNode().isTextual() ) {
              String detectValue = detectMatch.getNode().asText();
              Pattern detectPattern = detectConfig.compiledValue( REGEX_COMPILER );
              if( detectPattern.matcher( detectValue ).matches() ) {
                filterBufferedValues( node, detectConfig.getSelectors() );
              }
            }
          }
        }
      }
    }
  }

  private void filterBufferedValues( Level node, List<UrlRewriteFilterPathDescriptor> selectors ) {
    for( UrlRewriteFilterPathDescriptor selector : selectors ) {
      JsonPath.Expression path = (JsonPath.Expression)selector.compiledPath( JPATH_COMPILER );
      List<JsonPath.Match> matches = path.evaluate( node.node );
      for( JsonPath.Match match : matches ) {
        if( match.getNode().isTextual() && selector instanceof UrlRewriteFilterApplyDescriptor ) {
          filterBufferedValue( match, (UrlRewriteFilterApplyDescriptor)selector );
        }
      }
    }
  }

  private void filterBufferedValue( JsonPath.Match match, UrlRewriteFilterApplyDescriptor apply ) {
    String field = match.getField();
    String value = match.getNode().asText();
    try {
      value = filterValueString( field, value, apply.rule() );
      ((ObjectNode)match.getParent().getNode()).put( field, value );
    } catch( Exception e ) {
      LOG.failedToFilterValue( value, apply.rule(), e );
    }
  }

  protected String filterFieldName( String field ) {
    return field;
  }

  protected String filterValueString( String name, String value, String rule ) {
    return value;
  }

  @Override
  public void close() throws IOException {
    generator.close();
    writer.close();
    parser.close();
    reader.close();
  }

  private static class Level {
    String field;
    JsonNode node;
    JsonNode scopeNode;
    UrlRewriteFilterGroupDescriptor scopeConfig;
    Level( String field, JsonNode node, JsonNode scopeNode, UrlRewriteFilterGroupDescriptor scopeConfig ) {
      this.field = field;
      this.node = node;
      this.scopeNode = scopeNode;
      this.scopeConfig = scopeConfig;
    }
    public boolean isArray() {
      return node != null && node.isArray();
    }
  }

  private static class JsonPathCompiler implements UrlRewriteFilterPathDescriptor.Compiler<JsonPath.Expression> {
    @Override
    public JsonPath.Expression compile( String expression, JsonPath.Expression compiled ) {
      return JsonPath.compile( expression );
    }
  }

  private static class RegexCompiler implements UrlRewriteFilterPathDescriptor.Compiler<Pattern> {
    @Override
    public Pattern compile( String expression, Pattern compiled ) {
      if( compiled != null ) {
        return compiled;
      } else {
        return Pattern.compile( expression );
      }
    }
  }
}

