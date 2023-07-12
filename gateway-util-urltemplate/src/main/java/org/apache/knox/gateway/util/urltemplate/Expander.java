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
package org.apache.knox.gateway.util.urltemplate;

import static org.apache.knox.gateway.util.urltemplate.Parser.TEMPLATE_CLOSE_MARKUP;
import static org.apache.knox.gateway.util.urltemplate.Parser.TEMPLATE_OPEN_MARKUP;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class Expander {

  private static Params EMPTY_PARAMS = new EmptyParams();

  public static URI expand( Template template, Params params, Evaluator evaluator ) throws URISyntaxException {
    return Expander.expandToUri( template, params, evaluator );
  }

  public static URI expandToUri( Template template, Params params, Evaluator evaluator ) throws URISyntaxException {
    return new URI( expandToString( template, params, evaluator ) );
  }

  public static Template expandToTemplate( Template template, Params params, Evaluator evaluator ) throws URISyntaxException {
    //TODO: This could be much more efficient if it didn't create and then parse a string.
    return Parser.parseLiteral( expandToString( template, params, evaluator ) );
  }

  public static String expandToString( Template template, Params params, Evaluator evaluator ) {
    StringBuilder builder = new StringBuilder();
    if( params == null ) {
      params = EMPTY_PARAMS;
    }
    Set<String> names = new LinkedHashSet<>( params.getNames() );
    expandScheme( template, names, params, evaluator, builder );
    expandAuthority( template, names, params, evaluator, builder );
    expandPath( template, names, params, evaluator, builder );
    if( template.hasFragment() ) {
      StringBuilder fragment = new StringBuilder();
      expandFragment( template, names, params, evaluator, fragment );
      expandQuery( template, names, params, evaluator, builder );
      builder.append( fragment );
    } else {
      expandQuery( template, names, params, evaluator, builder );
    }
    return builder.toString();
  }

  private static void expandScheme( Template template, Set<String> names, Params params, Evaluator evaluator, StringBuilder builder ) {
    Segment segment = template.getScheme();
    if( segment != null ) {
      expandSingleValue( template.getScheme(), names, params, evaluator, builder );
      builder.append(':');
    }
  }

  private static void expandAuthority( Template template, Set<String> names, Params params, Evaluator evaluator, StringBuilder builder ) {
    if( template.hasAuthority() ) {
      if( !template.isAuthorityOnly() ) {
        builder.append( "//" );
      }
      Segment username = template.getUsername();
      Segment password = template.getPassword();
      Segment host = template.getHost();
      Segment port = template.getPort();
      expandSingleValue( username, names, params, evaluator, builder );
      if( password != null ) {
        builder.append(':');
        expandSingleValue( password, names, params, evaluator, builder );
      }
      if( username != null || password != null ) {
        builder.append('@');
      }
      if( host != null ) {
        expandSingleValue( host, names, params, evaluator, builder );
      }
      if( port != null ) {
        builder.append(':');
        expandSingleValue( port, names, params, evaluator, builder );
      }
    }
  }

  private static void expandPath( Template template, Set<String> names, Params params, Evaluator evaluator, StringBuilder builder ) {
    if( template.isAbsolute() ) {
      builder.append('/');
    }
    List<Path> path = template.getPath();
    for( int i=0, n=path.size(); i<n; i++ ) {
      if( i > 0 ) {
        builder.append('/');
      }
      Path segment = path.get( i );
      String name = segment.getParamName();
      Function function = new Function( name );
      names.remove( function.getParameterName() );
      Segment.Value value = segment.getFirstValue();
      switch( value.getType() ) {
        case( Segment.STATIC ):
          String pattern = value.getOriginalPattern();
          builder.append( pattern );
          break;
        case( Segment.DEFAULT ):
        case( Segment.STAR ):
        case( Segment.GLOB ):
        case( Segment.REGEX ):
          List<String> values = function.evaluate( params, evaluator );
          expandPathValues( segment, values, builder );
          break;
      }
    }
    if( template.isDirectory() && !path.isEmpty() ) {
      builder.append('/');
    }
  }

  //TODO: This needs to handle multiple values but only to the limit of the segment.
  private static void expandPathValues( Path segment, List<String> values, StringBuilder builder ) {
    if( values != null && !values.isEmpty() ) {
      int type = segment.getFirstValue().getType();
      if( type == Segment.GLOB || type == Segment.DEFAULT ) {
        for( int i=0, n=values.size(); i<n; i++ ) {
          if( i > 0 ) {
            builder.append( '/' );
          }
          builder.append( values.get( i ) );
        }
      } else {
        builder.append( values.get( 0 ) );
      }
    } else {
      builder.append( segment.getFirstValue().getOriginalPattern() );
    }
  }

  private static void expandQuery( Template template, Set<String> names, Params params, Evaluator evaluator, StringBuilder builder ) {
    AtomicInteger index = new AtomicInteger( 0 );
    expandExplicitQuery( template, names, params, evaluator, builder, index );
    expandExtraQuery( template, names, params, builder, index );
    //Kevin: I took this out because it causes '?' to be added to expanded templates when there are not query params.
//    if( template.hasQuery() && index.get() == 0 ) {
//      builder.append( '?' );
//    }
  }

  private static void expandExplicitQuery( Template template, Set<String> names, Params params, Evaluator evaluator, StringBuilder builder, AtomicInteger index ) {
    Collection<Query> query = template.getQuery().values();
    if( !query.isEmpty() ) {
      for (Query query1 : query) {
        int i = index.incrementAndGet();
        if (i == 1) {
          builder.append('?');
        } else {
          builder.append('&');
        }
        String queryName = query1.getQueryName();
        String paramName = query1.getParamName();
        Function function = new Function(paramName);
        names.remove(function.getParameterName());
        for (Segment.Value value : query1.getValues()) {
          switch (value.getType()) {
          case (Segment.STATIC):
            builder.append(queryName);
            String pattern = value.getOriginalPattern();
            if (pattern != null) {
              builder.append('=');
              builder.append(unescape(pattern));
            }
            break;
          case (Segment.DEFAULT):
          case (Segment.GLOB):
          case (Segment.STAR):
          case (Segment.REGEX):
            List<String> values = function.evaluate(params, evaluator);
            expandQueryValues(query1, queryName, values, builder);
            break;
          default:
          }
        }
      }
    }
  }

  private static String unescape(String pattern) {
    if (pattern == null) {
      return null;
    }
    return pattern
            .replace("\\" + TEMPLATE_OPEN_MARKUP, String.valueOf(TEMPLATE_OPEN_MARKUP))
            .replace("\\" + TEMPLATE_CLOSE_MARKUP, String.valueOf(TEMPLATE_CLOSE_MARKUP));
  }

  private static void expandExtraQuery( Template template, Set<String> names, Params params, StringBuilder builder, AtomicInteger index ) {
    Query extra = template.getExtra();
    if( extra != null ) {
      // Need to copy to an array because we are going to modify the set while iterating.
      String[] array = new String[ names.size() ];
      names.toArray( array );
      for( String name: array ) {
        names.remove( name );
        List<String> values = params.resolve( name );
        if( values != null ) {
          for( String value: values ) {
            int i = index.incrementAndGet();
            if( i == 1 ) {
              builder.append('?');
            } else {
              builder.append('&');
            }
            appendQueryPart(name, builder);
            if( value != null ) {
              builder.append('=');
              appendQueryPart(value, builder);
            }
          }
        }
      }
    }
  }

  private static void expandQueryValues( Query segment, String queryName, List<String> values, StringBuilder builder ) {
    String value;
    if( values == null || values.isEmpty()) {
      builder.append( queryName );
    } else {
      int type = segment.getFirstValue().getType();
      if( type == Segment.GLOB || type == Segment.DEFAULT ) {
        for( int i=0, n=values.size(); i<n; i++ ) {
          if( i > 0 ) {
            builder.append('&');
          }
          appendQueryPart(queryName, builder);
          value = values.get( i );
          if( value != null ) {
            builder.append('=');
            appendQueryPart(value, builder);
          }
        }
      } else {
        appendQueryPart(queryName, builder);
        value = values.get( 0 );
        if( value != null ) {
          builder.append('=');
          appendQueryPart(value, builder);
        }
      }
    }
  }

  private static void appendQueryPart(String part, StringBuilder builder) {
    try {
      builder.append(URLEncoder.encode(part, StandardCharsets.UTF_8.name()));
    } catch ( UnsupportedEncodingException e ) {
      builder.append(part);
    }
  }

  private static void expandFragment( Template template, Set<String> names, Params params, Evaluator evaluator, StringBuilder builder ) {
    if( template.hasFragment() ) {
      builder.append('#');
    }
    expandSingleValue( template.getFragment(), names, params, evaluator, builder );
  }

  private static void expandSingleValue( Segment segment, Set<String> names, Params params, Evaluator evaluator, StringBuilder builder ) {
    if( segment != null ) {
      String paramName = segment.getParamName();
      Function function = new Function( paramName );
      names.remove( function.getParameterName() );
      Segment.Value value = segment.getFirstValue();
      String str;
      switch( value.getType() ) {
        case Segment.DEFAULT:
        case Segment.STAR:
        case Segment.GLOB:
        case Segment.REGEX:
          List<String> values = function.evaluate( params, evaluator );
          if( values != null && !values.isEmpty() ) {
            str = values.get( 0 );
          } else if( function.getFunctionName() != null ) {
            str = paramName;
          } else {
            str = value.getOriginalPattern();
          }
          break;
        default:
          str = value.getOriginalPattern();
          break;
      }
      builder.append( str );
    }
  }

  private static class EmptyParams implements Params {
    @Override
    public Set<String> getNames() {
      return Collections.emptySet();
    }

    @Override
    public List<String> resolve( String name ) {
      return Collections.emptyList();
    }

  }

}
