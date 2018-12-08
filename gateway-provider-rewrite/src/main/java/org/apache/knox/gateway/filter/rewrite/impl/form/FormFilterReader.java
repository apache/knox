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
package org.apache.knox.gateway.filter.rewrite.impl.form;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterContentDescriptor;
import org.apache.knox.gateway.filter.rewrite.i18n.UrlRewriteMessages;
import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteUtil;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;

public class FormFilterReader extends Reader {

  private static final UrlRewriteMessages LOG = MessagesFactory.get( UrlRewriteMessages.class );

  private int offset;
  private StringWriter writer;
  private StringBuffer buffer;
  private Reader reader;
  private FormReader parser;
  private FormWriter generator;
  private UrlRewriteFilterContentDescriptor config;

  public FormFilterReader( Reader reader, UrlRewriteFilterContentDescriptor config ) throws IOException {
    this.reader = reader;
    this.config = config;
    parser = new FormReader( reader );
    writer = new StringWriter();
    buffer = writer.getBuffer();
    offset = 0;
    generator = new FormWriter( writer );
  }

  @Override
  public int read( char[] destBuffer, int destOffset, int destCount ) throws IOException {
    int count = 0;
    int available = buffer.length() - offset;

    if( available == 0 ) {
      FormPair pair = parser.getNextPair();
      if( pair == null ) {
        count = -1;
      } else {
        processPair();
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

  private void processPair() throws IOException {
    FormPair pair = parser.getCurrentPair();
    String name = pair.getName();
    String value;
    String rule = UrlRewriteUtil.pickFirstRuleWithEqualsIgnoreCasePathMatch( config, name );
    try {
      value = filterValue( name, pair.getValue(), rule );
      pair.setValue( value );
    } catch( Exception e ) {
      LOG.failedToFilterValue( pair.getValue(), rule, e );
      // Write original value.
    }
    generator.writePair( pair );
  }

  protected String filterValue( String name, String value, String rule ) {
    return value;
  }

  @Override
  public void close() throws IOException {
    writer.close();
    reader.close();
  }

}

