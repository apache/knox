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

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Template {

  private String original;
  private Scheme scheme;
  private boolean hasScheme;
  private Username username;
  private Password password;
  private Host host;
  private Port port;
  private boolean hasAuthority;
  private boolean isAuthorityOnly;
  private List<Path> path;
  private boolean isAbsolute;
  private boolean isDirectory;
  private Map<String,Query> query;
  private Query extra;
  private boolean hasQuery;
  private Fragment fragment;
  private boolean hasFragment;
  private Integer hash;

  Template(
      String original,
      Scheme scheme,
      boolean hasScheme,
      Username username,
      Password password,
      Host host,
      Port port,
      boolean hasAuthority,
      boolean isAuthorityOnly,
      List<Path> path,
      boolean isAbsolute,
      boolean isDirectory,
      Map<String,Query> query,
      Query extra,
      boolean hasQuery,
      Fragment fragment,
      boolean hasFragment ) {
    this.original = original;
    this.scheme = scheme;
    this.hasScheme = hasScheme;
    this.username = username;
    this.password = password;
    this.host = host;
    this.port = port;
    this.hasAuthority = hasAuthority;
    this.isAuthorityOnly = isAuthorityOnly;
    this.path = Collections.unmodifiableList( path );
    this.isAbsolute = isAbsolute;
    this.isDirectory = isDirectory;
    this.query = Collections.unmodifiableMap( query );
    this.extra = extra;
    this.hasQuery = hasQuery;
    this.fragment = fragment;
    this.hasFragment = hasFragment;
    this.hash = null;
  }

  public String getPattern() {
    return original != null ? original : toString();
  }

  public Scheme getScheme() {
    return scheme;
  }

  public boolean hasScheme() {
    return hasScheme;
  }

  public Username getUsername() {
    return username;
  }

  public Password getPassword() {
    return password;
  }

  public Host getHost() {
    return host;
  }

  public Port getPort() {
    return port;
  }

  public boolean hasAuthority() {
    return hasAuthority;
  }

  public boolean isAuthorityOnly() {
    return isAuthorityOnly;
  }

  public List<Path> getPath() {
    return path;
  }

  public boolean isAbsolute() {
    return isAbsolute;
  }

  public boolean isDirectory() {
    return isDirectory;
  }

  public Map<String,Query> getQuery() {
    return query;
  }

  public Query getExtra() {
    return extra;
  }

  public boolean hasQuery() {
    return hasQuery;
  }

  public Fragment getFragment() {
    return fragment;
  }

  public boolean hasFragment() {
    return hasFragment;
  }

  private void buildScheme( StringBuilder b ) {
    if( hasScheme ) {
      if( scheme != null ) {
        buildSegmentValue( b, scheme, scheme.getFirstValue() );
      }
      b.append( ':' );
    }
  }

  private void buildAuthority( StringBuilder b ) {
    if( hasAuthority ) {
      if( !isAuthorityOnly ) {
        b.append( "//" );
      }
      if( username != null || password != null ) {
        if( username != null ) {
          buildSegmentValue( b, username, username.getFirstValue() );
        }
        if( password != null ) {
          b.append( ':' );
          buildSegmentValue( b, password, password.getFirstValue() );
        }
        b.append( '@' );
      }
      if( host != null ) {
        buildSegmentValue( b, host, host.getFirstValue() );
      }
      if( port != null ) {
        b.append( ':' );
        buildSegmentValue( b, port, port.getFirstValue() );
      }
    }
  }

  private void buildSegmentValue( StringBuilder b, Segment s, Segment.Value v ) {
    String paramName = s.getParamName();
    if( paramName != null && !paramName.isEmpty()) {
      b.append( '{' );
      b.append( s.getParamName() );
      String actualPattern = v.getToken().originalPattern;
      if( ( actualPattern != null ) && ( v.getType() != Segment.DEFAULT ) )  {
        b.append( '=' );
        b.append( v.getOriginalPattern() );
      }
      b.append( '}' );
    } else {
      b.append( s.getFirstValue().getOriginalPattern() );
    }
  }

  private void buildPath( StringBuilder b ) {
    if( isAbsolute ) {
      b.append( '/' );
    }
    boolean first = true;
    for( Path segment: path ) {
      if( first ) {
        first = false;
      } else {
        b.append( '/' );
      }
      String paramName = segment.getParamName();
      Segment.Value firstValue = segment.getFirstValue();
      if( paramName != null && !paramName.isEmpty()) {
        b.append( '{' );
        b.append( segment.getParamName() );
        String pattern = firstValue.getOriginalPattern();
        if( pattern != null && !pattern.isEmpty() ) {
          b.append( '=' );
          b.append( firstValue );
        }
        b.append( '}' );
      } else {
        b.append( firstValue.getOriginalPattern() );
      }
    }
    if( isDirectory && ( !isAbsolute || !path.isEmpty() ) ) {
      b.append( '/' );
    }
  }

  private void buildQuery( StringBuilder b ) {
    if( hasQuery ) {
      int count = 0;
      for( Query segment: query.values() ) {
//        String paramName = segment.getParamName();
        for( Segment.Value value: segment.getValues() ) {
          count++;
          if( count == 1 ) {
            b.append( '?' );
          } else {
            b.append( '&' );
          }
          buildQuerySegment( b, segment, value );
//          String valuePattern = value.getPattern();
//          if( paramName != null && paramName.length() > 0 ) {
//            b.append( segment.getQueryName() );
//            b.append( "={" );
//            b.append( segment.getParamName() );
//            if( valuePattern != null ) {
//              b.append( '=' );
//              b.append( valuePattern );
//            }
//            b.append( '}' );
//          } else {
//            b.append( segment.getQueryName() );
//            if( valuePattern != null ) {
//              b.append( "=" );
//              b.append( valuePattern );
//            }
//          }
        }
      }
      if( extra != null ) {
        count++;
        if( count == 1 ) {
          b.append( '?' );
        } else {
          b.append( '&' );
        }
        buildQuerySegment( b, extra, extra.getFirstValue() );
      }
      if( count == 0 ) {
        b.append( '?' );
      }
    }
  }

  private void buildQuerySegment( StringBuilder b, Query segment, Segment.Value value ) {
    String paramName = segment.getParamName();
    String queryName = segment.getQueryName();
    String valuePattern = value.getOriginalPattern();
    if( paramName != null && !paramName.isEmpty()) {
      if( !Segment.GLOB_PATTERN.equals( queryName ) && !Segment.STAR_PATTERN.equals( queryName ) ) {
        b.append( segment.getQueryName() );
        b.append( '=' );
      }
      b.append( '{' );
      b.append( segment.getParamName() );
      if( valuePattern != null ) {
        b.append( '=' );
        b.append( valuePattern );
      }
      b.append( '}' );
    } else {
      b.append( queryName );
      if( valuePattern != null ) {
        b.append( '=' );
        b.append( valuePattern );
      }
    }
  }

  private void buildFragment( StringBuilder b ) {
    if( hasFragment ) {
      b.append( '#' );
      if( fragment != null ) {
        b.append( fragment.getFirstValue().getOriginalPattern() );
      }
    }
  }

  @Override
  public String toString() {
    String s;
    StringBuilder b = new StringBuilder();
    buildScheme( b );
    buildAuthority( b );
    buildPath( b );
    buildQuery( b );
    buildFragment( b );
    s = b.toString();
    return s;
  }

  @Override
  public int hashCode() {
    Integer hc = hash;
    if( hc == null ) {
      hc = toString().hashCode();
      hash = hc;
    }
    return hc;
  }

  @Override
  public boolean equals(Object object ) {
    boolean equals = false;
    if(object instanceof Template) {
      String thisStr = toString();
      String thatStr = object.toString();
      equals = thisStr.equals( thatStr );
    }
    return equals;

  }
}
