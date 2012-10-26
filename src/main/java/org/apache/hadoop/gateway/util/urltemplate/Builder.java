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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class Builder {

  private boolean hasScheme;
  private Scheme scheme;
  private boolean hasAuthority;
  private UsernameSegment username;
  private Password password;
  private Host host;
  private Port port;
  private boolean isAbsolute;
  private boolean isDirectory;
  private List<Path> pathSegments;
  private boolean hasQuery;
  private LinkedHashMap<String,Query> querySegments;
  private boolean hasFragment;
  private Fragment fragment;

  public Builder() {
    this.isAbsolute = false;
    this.isDirectory = false;
    this.hasQuery = false;
    this.pathSegments = new ArrayList<Path>();
    this.querySegments = new LinkedHashMap<String,Query>();
  }

  public Template build() {
    return new Template(
        scheme, hasScheme,
        username, password, host, port, hasAuthority,
        pathSegments, isAbsolute, isDirectory,
        querySegments, hasQuery,
        fragment, hasFragment );
  }

  public void setHasScheme( boolean hasScheme ) {
    this.hasScheme = hasScheme;
  }

  public void setScheme( String paramName, String valuePattern ) {
    this.scheme = new Scheme( paramName, valuePattern );
    setHasScheme( true );
  }

  public void setHasAuthority( boolean hasAuthority ) {
    this.hasAuthority = hasAuthority;
  }

  public void setUsername( String paramName, String valuePattern ) {
    setHasAuthority( true );
    this.username = new UsernameSegment( paramName, valuePattern );
  }

  public void setPassword( String paramName, String valuePattern ) {
    setHasAuthority( true );
    this.password = new Password( paramName, valuePattern );
  }

  public void setHost( String paramName, String valuePattern ) {
    setHasAuthority( true );
    this.host = new Host( paramName, valuePattern );
  }

  public void setPort( String paramName, String valuePattern ) {
    setHasAuthority( true );
    this.port = new Port( paramName, valuePattern );
  }

  public Builder setIsAbsolute( boolean isAbsolute ) {
    this.isAbsolute = isAbsolute;
    return this;
  }
  
  public Builder setIsDirectory( boolean isDirectory ) {
    this.isDirectory = isDirectory;
    return this;
  }

  public Builder addPathSegment( String paramName, String valuePattern ) {
    Path segment = new Path( paramName, valuePattern );
    pathSegments.add( segment );
    return this;
  }

  public Builder setHasQuery( boolean hasQuery ) {
    this.hasQuery = hasQuery;
    return this;
  }

  public Builder addQuerySegment( String queryName, String paramName, String valuePattern ) {
    Query segment = new Query( queryName, paramName, valuePattern );
    querySegments.put( queryName, segment );
    return this;
  }

  public void setHasFragment( boolean hasFragment ) {
    this.hasFragment = hasFragment;
  }

  public void setFragment( String paramName, String valuePattern ) {
    setHasFragment( true );
    this.fragment = new Fragment( paramName, valuePattern );
  }

}
