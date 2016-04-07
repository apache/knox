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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.LinkedHashMap;
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
  private String image;
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
      LinkedHashMap<String,Query> query,
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
    this.image = null;
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

  private void buildScheme( StringBuilder b, boolean encode ) {
    if( hasScheme ) {
      if( scheme != null ) {
        buildSegmentValue( b, scheme, scheme.getFirstValue(), encode );
      }
      b.append( ':' );
    }
  }

  private void buildAuthority( StringBuilder b, boolean encode ) {
    if( hasAuthority ) {
      if( !isAuthorityOnly ) {
        b.append( "//" );
      }
      if( username != null || password != null ) {
        if( username != null ) {
          buildSegmentValue( b, username, username.getFirstValue(), encode );
        }
        if( password != null ) {
          b.append( ':' );
          buildSegmentValue( b, password, password.getFirstValue(), encode );
        }
        b.append( "@" );
      }
      if( host != null ) {
        buildSegmentValue( b, host, host.getFirstValue(), encode );
      }
      if( port != null ) {
        b.append( ':' );
        buildSegmentValue( b, port, port.getFirstValue(), encode );
      }
    }
  }

  private void buildSegmentValue( StringBuilder b, Segment s, Segment.Value v, boolean encode ) {
    String paramName = s.getParamName();
    if( paramName != null && paramName.length() > 0 ) {
      b.append( "{" );
      if ( encode ) {
        b.append( encodeValue( s.getParamName() ));
      } else {
        b.append( s.getParamName() );
      }
      String actualPattern = v.getToken().originalPattern;
      if( ( actualPattern != null ) && ( v.getType() != Segment.DEFAULT ) )  {
        b.append( '=' );
        if ( encode ) {
          b.append( encodeValue( v.getOriginalPattern() ));
        } else {
          b.append( v.getOriginalPattern() );
        }
      }
      b.append( '}' );
    } else {
      if ( encode ) {
        b.append( encodeValue( s.getFirstValue().getOriginalPattern() ));
      } else {
        b.append( s.getFirstValue().getOriginalPattern() );
      }
    }
  }

  private void buildPath( StringBuilder b, boolean encode ) {
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
      if( paramName != null && paramName.length() > 0 ) {
        b.append( "{" );
        if ( encode ) {
          b.append( encodeValue(segment.getParamName()) );
        } else {
          b.append( segment.getParamName() );
        }
        String pattern = firstValue.getOriginalPattern();
        if( pattern != null && !pattern.isEmpty() ) {
          b.append( '=' );
          b.append( firstValue );
        }
        b.append( '}' );
      } else {
        if ( encode ) {
          b.append( encodeValue(firstValue.getOriginalPattern()) );
        } else {
          b.append( firstValue.getOriginalPattern() );
        }
      }
    }
    if( isDirectory && ( !isAbsolute || path.size() > 0 ) ) {
      b.append( '/' );
    }
  }

  private void buildQuery( StringBuilder b , boolean encode ) {
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
          buildQuerySegment( b, segment, value, encode );
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
        buildQuerySegment( b, extra, extra.getFirstValue(), encode );
      }
      if( count == 0 ) {
        b.append( '?' );
      }
    }
  }

  private void buildQuerySegment( StringBuilder b, Query segment, Segment.Value value, boolean encode ) {
    String paramName = segment.getParamName();
    String queryName = segment.getQueryName();
    String valuePattern = value.getOriginalPattern();
    if (encode) {
      queryName = encodeValue(queryName);
      valuePattern = encodeValue(valuePattern);
    }
    if( paramName != null && paramName.length() > 0 ) {
      if( !Segment.GLOB_PATTERN.equals( queryName ) && !Segment.STAR_PATTERN.equals( queryName ) ) {
        b.append( segment.getQueryName() );
        b.append( "=" );
      }
      b.append( "{" );
      if (encode) {
        b.append( encodeValue(segment.getParamName()) );
      } else {
        b.append( segment.getParamName() );
      }
      if( valuePattern != null ) {
        b.append( '=' );
        b.append( valuePattern );
      }
      b.append( '}' );
    } else {
      b.append( queryName );
      if( valuePattern != null ) {
        b.append( "=" );
        b.append( valuePattern );
      }
    }
  }

  private void buildFragment( StringBuilder b, boolean encode ) {
    if( hasFragment ) {
      b.append( '#' );
      if( fragment != null ) {
        String value = fragment.getFirstValue().getOriginalPattern();
        if (encode) {
          value = encodeValue( value );
        }
        b.append( value );
      }
    }
  }

  private String encodeValue(String value) {
    if ( value != null ) {
      try {
        return URLEncoder.encode( value, "UTF-8" );
      } catch ( UnsupportedEncodingException e ) {
        //log
      }
    }
    return value;
  }

  public String toString() {
    return toString( false );
  }

  private String toString( boolean encoded ) {
    String s = image;
    if( s == null ) {
      StringBuilder b = new StringBuilder();
      buildScheme( b, encoded );
      buildAuthority( b, encoded );
      buildPath( b, encoded );
      buildQuery( b, encoded );
      buildFragment( b , encoded);
      s = b.toString();
      //image = s;
    }
    return s;
  }

 public String toEncodedString() {
    return toString(true);
  }

  public int hashCode() {
    Integer hc = hash;
    if( hc == null ) {
      hc = toString().hashCode();
      hash = hc;
    }
    return hc.intValue();
  }

  public boolean equals( Object object ) {
    boolean equals = false;
    if( object != null && object instanceof Template ) {
      String thisStr = toString();
      String thatStr = object.toString();
      equals = thisStr.equals( thatStr );
    }
    return equals;

  }
}
