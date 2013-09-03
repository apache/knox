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
package org.apache.hadoop.gateway.util.urltemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class Expander {

  private static Params EMPTY_PARAMS = new EmptyParams();

  public static URI expand( Template template, Params params ) throws URISyntaxException {
    return new Expander().expandToUri( template, params );
  }

  public URI expandToUri( Template template, Params params ) throws URISyntaxException {
    return new URI( expandToString( template, params ) );
  }

  public Template expandToTemplate( Template template, Params params ) throws URISyntaxException {
    //TODO: This could be much more efficient if it didn't create and then parse a string.
    return Parser.parse( expandToString( template, params ) );
  }

  public String expandToString( Template template, Params params ) {
    StringBuilder builder = new StringBuilder();
    if( params == null ) {
      params = EMPTY_PARAMS;
    }
    Set<String> names = new HashSet<String>( params.getNames() );
    expandScheme( template, names, params, builder );
    expandAuthority( template, names, params, builder );
    expandPath( template, names, params, builder );
    expandQuery( template, names, params, builder );
    expandFragment( template, names, params, builder );
    return builder.toString();
  }

  private static void expandScheme( Template template, Set<String> names, Params params, StringBuilder builder ) {
    Segment segment = template.getScheme();
    if( segment != null ) {
      expandSingleValue( template.getScheme(), names, params, builder );
      builder.append( ":" );
    }
  }

  private static void expandAuthority( Template template, Set<String> names, Params params, StringBuilder builder ) {
    if( template.hasAuthority() ) {
      builder.append( "//" );
      Segment username = template.getUsername();
      Segment password = template.getPassword();
      Segment host = template.getHost();
      Segment port = template.getPort();
      expandSingleValue( username, names, params, builder );
      if( password != null ) {
        builder.append( ":" );
        expandSingleValue( password, names, params, builder );
      }
      if( username != null || password != null ) {
        builder.append( "@" );
      }
      if( host != null ) {
        expandSingleValue( host, names, params, builder );
      }
      if( port != null ) {
        builder.append( ":" );
        expandSingleValue( port, names, params, builder );
      }
    }
  }

  private static void expandPath( Template template, Set<String> names, Params params, StringBuilder builder ) {
    if( template.isAbsolute() ) {
      builder.append( "/" );
    }
    List<Path> path = template.getPath();
    for( int i=0, n=path.size(); i<n; i++ ) {
      if( i > 0 ) {
        builder.append( "/" );
      }
      Path segment = path.get( i );
      String name = segment.getParamName();
      names.remove( name );
      Segment.Value value = segment.getFirstValue();
      switch( value.getType() ) {
        case( Segment.STATIC ):
          String pattern = value.getPattern();
          builder.append( pattern );
          break;
        case( Segment.DEFAULT ):
        case( Segment.STAR ):
        case( Segment.GLOB ):
        case( Segment.REGEX ):
          List<String> values = params.resolve( name );
          expandPathValues( segment, values, builder );
          break;
      }
    }
    if( template.isDirectory() && path.size() > 0 ) {
      builder.append( "/" );
    }
  }

  //TODO: This needs to handle multiple values but only to the limit of the segment.
  private static void expandPathValues( Path segment, List<String> values, StringBuilder builder ) {
    if( values != null && values.size() > 0 ) {
      int type = segment.getFirstValue().getType();
      if( type == Segment.GLOB || type == Segment.DEFAULT ) {
        for( int i=0, n=values.size(); i<n; i++ ) {
          if( i > 0 ) {
            builder.append( "/" );
          }
          builder.append( values.get( i ) );
        }
      } else {
        builder.append( values.get( 0 ) );
      }
    } else {
      builder.append( segment.getFirstValue().getPattern() );
    }
  }

  private static void expandQuery( Template template, Set<String> names, Params params, StringBuilder builder ) {
    AtomicInteger index = new AtomicInteger( 0 );
    expandExplicitQuery( template, names, params, builder, index );
    expandExtraQuery( template, names, params, builder, index );
    //Kevin: I took this out because it causes '?' to be added to expanded templates when there are not query params.
//    if( template.hasQuery() && index.get() == 0 ) {
//      builder.append( '?' );
//    }
  }

  private static void expandExplicitQuery( Template template, Set<String> names, Params params, StringBuilder builder, AtomicInteger index ) {
    Collection<Query> query = template.getQuery().values();
    if( !query.isEmpty() ) {
      Iterator<Query> iterator = query.iterator();
      while( iterator.hasNext() ) {
        int i = index.incrementAndGet();
        if( i == 1 ) {
          builder.append( "?" );
        } else {
          builder.append( "&" );
        }
        Query segment = iterator.next();
        String queryName = segment.getQueryName();
        String funcName = segment.getParamName();
        String paramName = extractParamNameFromFunction( funcName );
        names.remove( paramName );
        for( Segment.Value value: segment.getValues() ) {
          switch( value.getType() ) {
            case( Segment.STATIC ):
              String pattern = value.getPattern();
              builder.append( queryName );
              if( pattern != null ) {
                builder.append( "=" );
                builder.append( pattern );
              }
              break;
            case( Segment.DEFAULT ):
            case( Segment.GLOB ):
            case( Segment.STAR ):
            case( Segment.REGEX ):
              List<String> values = params.resolve( funcName );
              expandQueryValues( segment, queryName, values, builder );
              break;
            default:
          }
        }
      }
    }
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
              builder.append( "?" );
            } else {
              builder.append( "&" );
            }
            builder.append( name );
            builder.append( "=" );
            builder.append( value );
          }
        }
      }
    }
  }

  private static void expandQueryValues( Query segment, String queryName, List<String> values, StringBuilder builder ) {
    if( values == null || values.size() == 0 ) {
      builder.append( queryName );
    } else {
      int type = segment.getFirstValue().getType();
      if( type == Segment.GLOB || type == Segment.DEFAULT ) {
        for( int i=0, n=values.size(); i<n; i++ ) {
          if( i > 0 ) {
            builder.append( "&" );
          }
          builder.append( queryName );
          builder.append( "=" );
          builder.append( values.get( i ) );
        }
      } else {
        builder.append( queryName );
        builder.append( "=" );
        builder.append( values.get( 0 ) );
      }
    }
  }

  private static void expandFragment( Template template, Set<String> names, Params params, StringBuilder builder ) {
    if( template.hasFragment() ) {
      builder.append( "#" );
    }
    expandSingleValue( template.getFragment(), names, params, builder );
  }

  private static void expandSingleValue( Segment segment, Set<String> names, Params params, StringBuilder builder ) {
    if( segment != null ) {
      String name = segment.getParamName();
      names.remove( name );
      Segment.Value value = segment.getFirstValue();
      switch( value.getType() ) {
        case( Segment.STATIC ):
          String pattern = value.getPattern();
          builder.append( pattern );
          break;
        case( Segment.DEFAULT ):
        case( Segment.STAR ):
        case( Segment.GLOB ):
        case( Segment.REGEX ):
          List<String> values = params.resolve( name );
          if( values != null && !values.isEmpty() ) {
            builder.append( values.get( 0 ) );
          } else {
            builder.append( segment.getFirstValue().getPattern() );
          }
          break;
      }
    }
  }

  private static String extractParamNameFromFunction( String function ) {
    String param = function;
    if( param != null && param.startsWith( "$" ) ) {
      int stop = param.lastIndexOf( ')' );
      if( stop > 1 ) {
        int start = param.indexOf( '(' );
        if( start > -1 ) {
          param = param.substring( start+1, stop );
        }
      }
    }
    return param;
  }

  private static class EmptyParams implements Params {
    @Override
    public Set<String> getNames() {
      return Collections.EMPTY_SET;
    }

    @Override
    public List<String> resolve( String name ) {
      return Collections.EMPTY_LIST;
    }
  }

}
