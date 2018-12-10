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
package org.apache.knox.gateway.filter.rewrite.impl.javascript;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterContentDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterPathDescriptor;
import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteFilterReader;
import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.regex.Pattern;

public abstract class JavaScriptFilterReader extends Reader implements UrlRewriteFilterReader {

  private static final UrlRewriteFilterPathDescriptor.Compiler<Pattern> REGEX_COMPILER = new RegexCompiler();

  private BufferedReader reader;
  private int offset;
  private StringWriter writer;
  private StringBuffer buffer;
  private UrlRewriteFilterContentDescriptor config;

  protected JavaScriptFilterReader( Reader reader, UrlRewriteFilterContentDescriptor config ) throws IOException {
    this.reader = new BufferedReader( reader );
    this.config = config;
    writer = new StringWriter();
    buffer = writer.getBuffer();
    offset = 0;
  }

  @Override
  public abstract String filterValueString( String name, String value, String rule );

  @Override
  public int read( char[] destBuffer, int destOffset, int destCount ) throws IOException {
    int count = 0;
    int available = buffer.length() - offset;
    String cbuff;
    if( available == 0 ) {
      cbuff = reader.readLine();
      if( cbuff != null ) {
        count = cbuff.length();
        writer.write( UrlRewriteUtil.filterJavaScript( cbuff, config, this, REGEX_COMPILER ) );
        writer.write( '\n' );
        available = buffer.length() - offset;
      } else {
        count = -1;
      }
    }

    if( available > 0 ) {
      count = Math.min( destCount, available );
      buffer.getChars( offset, offset + count, destBuffer, destOffset );
      offset += count;
      if( offset == buffer.length() ) {
        offset = 0;
        buffer.setLength( 0 );
      }
    }

    return count;
  }

  @Override
  public void close() throws IOException {
    reader.close();
    writer.close();
  }
}
