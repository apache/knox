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

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MimeTypes {

  private static final String CHARSET_PARAMETER_NAME = "charset";

  private static final String UTF8_CHARSET = "UTF-8";

  private static Map<String,String> DEFAULT_CHARSETS;
  static {
    DEFAULT_CHARSETS = new HashMap<>();
    DEFAULT_CHARSETS.put( "text/xml", UTF8_CHARSET );
    DEFAULT_CHARSETS.put( "text/json", UTF8_CHARSET );
    DEFAULT_CHARSETS.put( "application/xml", UTF8_CHARSET );
    DEFAULT_CHARSETS.put( "application/json", UTF8_CHARSET );
  }

  public static MimeType create( final String base, final String encoding ) {
    MimeType type = null;
    if( base != null ) {
      try {
        type = new MimeType( base );
        if( encoding != null && getCharset( type, null ) == null ) {
          setCharset( type, encoding );
        }
      } catch( MimeTypeParseException e ) {
        throw new IllegalArgumentException( base, e );
      }
    }
    return type;
  }

  public static String getCharset( final MimeType type, String defaultCharset ) {
    String charset = null;
    if( type != null ) {
      charset = type.getParameter( CHARSET_PARAMETER_NAME );
    }
    if( charset == null ) {
      charset = defaultCharset;
    }
    return charset;
  }

  public static void setCharset( final MimeType type, final String charset ) {
    type.setParameter( CHARSET_PARAMETER_NAME, charset );
  }

  public static String getDefaultCharsetForMimeType( final String mimeType ) {
    String charset = null;
    if( mimeType != null ) {
      charset = DEFAULT_CHARSETS.get( mimeType.trim().toLowerCase(Locale.ROOT) );
    }
    return charset;
  }

}
