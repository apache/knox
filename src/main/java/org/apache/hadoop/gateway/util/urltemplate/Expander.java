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
import java.util.Iterator;
import java.util.List;

public class Expander {

  public static URI expand( Template template, Resolver resolver ) throws URISyntaxException {
    return new Expander().expandTemplate( template, resolver );
  }

  public URI expandTemplate( Template template, Resolver resolver ) throws URISyntaxException {
    StringBuilder builder = new StringBuilder();
    expandScheme( template, resolver, builder );
    expandAuthority( template, resolver, builder );
    expandPath( template, resolver, builder );
    expandQuery( template, resolver, builder );
    expandFragment( template, resolver, builder );
    return new URI( builder.toString() );
  }

  private static void expandScheme( Template template, Resolver resolver, StringBuilder builder ) {
    Segment segment = template.getScheme();
    if( segment != null ) {
      expandSingleValue( template.getScheme(), resolver, builder );
      builder.append( ":" );
    }
  }

  private static void expandAuthority( Template template, Resolver resolver, StringBuilder builder ) {
    if( template.hasAuthority() ) {
      builder.append( "//" );
      Segment username = template.getUsername();
      Segment password = template.getPassword();
      Segment host = template.getHost();
      Segment port = template.getPort();
      expandSingleValue( username, resolver, builder );
      if( password != null ) {
        builder.append( ":" );
        expandSingleValue( password, resolver, builder );
      }
      if( username != null || password != null ) {
        builder.append( "@" );
      }
      if( host != null ) {
        expandSingleValue( host, resolver, builder );
      }
      if( port != null ) {
        builder.append( ":" );
        expandSingleValue( port, resolver, builder );
      }
    }
  }

  private static void expandPath( Template template, Resolver resolver, StringBuilder builder ) {
    if( template.isAbsolute() ) {
      builder.append( "/" );
    }
    List<Path> path = template.getPath();
    for( int i=0, n=path.size(); i<n; i++ ) {
      if( i > 0 ) {
        builder.append( "/" );
      }
      Path segment = path.get( i );
      switch( segment.getType() ) {
        case( Segment.STATIC ):
          String value = segment.getValuePattern();
          builder.append( value );
          break;
        case( Segment.WILDCARD ):
        case( Segment.REGEX ):
          String name = segment.getParamName();
          List<String> values = resolver.getValues( name );
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
    if( values != null ) {
      for( int i=0, n=Math.min( values.size(), segment.getMaxAllowed() ); i<n; i++ ) {
        if( i > 0 ) {
          builder.append( "/" );
        }
        builder.append( values.get( i ) );
      }
    }
  }

  private static void expandQuery( Template template, Resolver resolver, StringBuilder builder ) {
    Collection<Query> query = template.getQuery().values();
    if( query.isEmpty() ) {
      if( template.hasQuery() ) {
        builder.append( "?" );
      }
    } else {
      boolean first = true;
      Iterator<Query> iterator = query.iterator();
      while( iterator.hasNext() ) {
        if( first ) {
          builder.append( "?" );
          first = false;
        } else {
          builder.append( "&" );
        }
        Query segment = iterator.next();
        String queryName = segment.getQueryName();
        switch( segment.getType() ) {
          case( Segment.STATIC ):
            String value = segment.getValuePattern();
            builder.append( queryName );
            builder.append( "=" );
            builder.append( value );
            break;
          case( Segment.WILDCARD ):
          case( Segment.REGEX ):
            String paramName = segment.getParamName();
            List<String> values = resolver.getValues( paramName );
            expandQueryValues( segment, queryName, values, builder );
            break;
          default:
        }
      }
    }
  }

  private static void expandQueryValues( Query segment, String queryName, List<String> values, StringBuilder builder ) {
    if( values == null ) {
      builder.append( queryName );
    } else {
      for( int i=0, n=Math.min( values.size(), segment.getMaxAllowed() ); i<n; i++ ) {
        if( i > 0 ) {
          builder.append( "&" );
        }
        builder.append( queryName );
        builder.append( "=" );
        builder.append( values.get( i ) );
      }
    }
  }

  private static void expandFragment( Template template, Resolver resolver, StringBuilder builder ) {
    if( template.hasFragment() ) {
      builder.append( "#" );
    }
    expandSingleValue( template.getFragment(), resolver, builder );
  }

  private static void expandSingleValue( Segment segment, Resolver resolver, StringBuilder builder ) {
    if( segment != null ) {
      switch( segment.getType() ) {
        case( Segment.STATIC ):
          String value = segment.getValuePattern();
          builder.append( value );
          break;
        case( Segment.WILDCARD ):
        case( Segment.REGEX ):
          String name = segment.getParamName();
          List<String> values = resolver.getValues( name );
          if( values != null && !values.isEmpty() ) {
            builder.append( values.get( 0 ) );
          }
          break;
      }
    }
  }

}
