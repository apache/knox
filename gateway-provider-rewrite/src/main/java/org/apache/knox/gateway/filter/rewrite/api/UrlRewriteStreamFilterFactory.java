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
package org.apache.knox.gateway.filter.rewrite.api;

import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteStreamFilter;
import org.apache.knox.gateway.util.MimeTypes;
import org.apache.knox.gateway.util.urltemplate.Resolver;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

public abstract class UrlRewriteStreamFilterFactory {

  private static final Map<String,Map<String,UrlRewriteStreamFilter>> MAP = loadFactories();

  private UrlRewriteStreamFilterFactory() {
  }

  @Deprecated
  public static InputStream create(
      MimeType type,
      String name,
      InputStream stream,
      UrlRewriter rewriter,
      Resolver resolver,
      UrlRewriter.Direction direction,
      UrlRewriteFilterContentDescriptor config )
          throws IOException {
    UrlRewriteStreamFilter filter = create(type, name);
    String charset = MimeTypes.getCharset( type, StandardCharsets.UTF_8.name() );
    final InputStream filteredStream;
    if( filter != null ) {
      filteredStream = filter.filter( stream, charset, rewriter, resolver, direction, config );
    } else {
      filteredStream = stream;
    }
    return filteredStream;
  }

  public static UrlRewriteStreamFilter create(MimeType type, String name) {
    Map<String,UrlRewriteStreamFilter> nameMap = getNameMap( type );
    return getFilter( nameMap, name );
  }

  private static Map<String,Map<String,UrlRewriteStreamFilter>> loadFactories() {
    Map<String,Map<String,UrlRewriteStreamFilter>> typeMap = new HashMap<>();
    ServiceLoader<UrlRewriteStreamFilter> filters = ServiceLoader.load( UrlRewriteStreamFilter.class );
    for( UrlRewriteStreamFilter filter : filters ) {
      String[] types = filter.getTypes();
      for( String type: types ) {
        Map<String, UrlRewriteStreamFilter> nameMap = typeMap.computeIfAbsent(type, k -> new LinkedHashMap<>());
        for( String name: filter.getNames() ) {
          nameMap.put( name, filter );
        }
      }
    }
    return typeMap;
  }

  private static Map<String,UrlRewriteStreamFilter> getNameMap( MimeType type ) {
    if( type == null ) {
      type = new MimeType();
    }
    Map<String,UrlRewriteStreamFilter> nameMap = MAP.get( type.getBaseType() );
    try {
      if( nameMap == null ) {
        type.setPrimaryType( "*" );
        nameMap = MAP.get( type.getBaseType() );
        if( nameMap == null ) {
          type.setSubType( "*" );
          nameMap = MAP.get( type.getBaseType() );
          if( nameMap == null ) {
            nameMap = MAP.get( null );
          }
        }
      }
    } catch( MimeTypeParseException e ) {
      throw new IllegalArgumentException( type.toString(), e );
    }
    return nameMap;
  }

  private static UrlRewriteStreamFilter getFilter( Map<String,UrlRewriteStreamFilter> map, String name ) {
    UrlRewriteStreamFilter filter = null;
    if( map != null ) {
      if( name == null && !map.isEmpty() ) {
        filter = map.values().iterator().next();
      } else {
        filter = map.get( name );
      }
    }
    return filter;
  }

}
