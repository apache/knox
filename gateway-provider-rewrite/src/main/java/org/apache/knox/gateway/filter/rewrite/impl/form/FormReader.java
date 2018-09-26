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

import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class FormReader {

  private static final String DEFFAULT_FORM_ENCODING = "UTF-8";

  private static final int DEFAULT_BUFFER_SIZE = 1024;

  private Reader reader;
  private FormPair current;
  private StringBuilder buffer;
  private int sepIndex;

  public FormReader( Reader reader ) {
    this.reader = reader;
    this.current = null;
    this.buffer = new StringBuilder( DEFAULT_BUFFER_SIZE );
    this.sepIndex = -1;
  }

  public FormPair getNextPair() throws IOException {
    while( true ) {
      int c = reader.read();
      switch( c ) {
        case '=':
          sepIndex = buffer.length();
          break;
        case '&':
          // Ignore adjacent &s.
          if( buffer.length() == 0 ) {
            sepIndex = -1;
            continue;
          } else {
            return createCurrentPair();
          }
        case -1:
          // Ignore adjacent &s.
          if( buffer.length() == 0 ) {
            sepIndex = -1;
            return null;
          } else {
            return createCurrentPair();
          }
        default:
          buffer.append( (char)c );
          break;
      }
    }
  }

  private FormPair createCurrentPair() throws UnsupportedEncodingException {
    String name;
    String value;
    if( sepIndex >= 0 ) {
      name = buffer.substring( 0, sepIndex );
      value = buffer.substring( sepIndex );
    } else {
      name = buffer.toString();
      value = "";
    }
    name = URLDecoder.decode( name, DEFFAULT_FORM_ENCODING );
    value = URLDecoder.decode( value, DEFFAULT_FORM_ENCODING );
    FormPair pair = new FormPair( name, value );
    current = pair;
    buffer.setLength( 0 );
    sepIndex = -1;
    return pair;
  }

  public FormPair getCurrentPair() {
    return current;
  }

}
