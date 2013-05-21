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
package org.apache.hadoop.gateway.filter.rewrite.impl.json;

import org.apache.hadoop.gateway.filter.rewrite.i18n.UrlRewriteMessages;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;

public class JsonFilterReader extends Reader {

  private static final UrlRewriteMessages LOG = MessagesFactory.get( UrlRewriteMessages.class );
  
  private int offset;
  private JsonParser parser;
  private JsonGenerator generator;
  private StringWriter writer;
  private StringBuffer buffer;
  private Reader reader;

  public JsonFilterReader( Reader reader ) throws IOException {
    this.reader = reader;
    JsonFactory parserFactory = new JsonFactory();
    parser = parserFactory.createJsonParser( reader );
    writer = new StringWriter();
    buffer = writer.getBuffer();
    offset = 0;
    generator = parserFactory.createJsonGenerator( writer );
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
        generator.writeStartObject();
        break;
      case END_OBJECT:
        generator.writeEndObject();
        break;
      case START_ARRAY:
        generator.writeStartArray();
        break;
      case END_ARRAY:
        generator.writeEndArray();
        break;
      case FIELD_NAME:
        processFieldName();
        break;
      case VALUE_STRING:
        processValueString();
        break;
      case VALUE_NUMBER_INT:
      case VALUE_NUMBER_FLOAT:
        processNumber();
        break;
      case VALUE_TRUE:
        generator.writeBoolean( true );
        break;
      case VALUE_FALSE:
        generator.writeBoolean( false );
        break;
      case VALUE_NULL:
        generator.writeNull();
        break;
      case NOT_AVAILABLE:
        // Ignore it.
        break;
    }
    generator.flush();
  }

  private void processNumber() throws IOException {
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

  private void processFieldName() throws IOException {
    String name = parser.getCurrentName();
    try {
      name = filterFieldName( name );
    } catch( Exception e ) {
      LOG.failedToFilterFieldName( name, e );
      // Write original name.
    }
    generator.writeFieldName( name );
  }

  private void processValueString() throws IOException {
    String name = parser.getCurrentName();
    String value = parser.getText();
    try {
      value = filterValueString( name, value );
    } catch( Exception e ) {
      LOG.failedToFilterValue( value, e );
      // Write original value.
    }
    generator.writeString( value );
  }

  protected String filterFieldName( String name ) {
    return name;
  }

  protected String filterValueString( String name, String value ) {
    return value;
  }

  @Override
  public void close() throws IOException {
    generator.close();
    writer.close();
    parser.close();
    reader.close();
  }

}

