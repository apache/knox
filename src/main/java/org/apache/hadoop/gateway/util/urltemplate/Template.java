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

import java.util.*;

public class Template {

  private Scheme scheme;
  private boolean hasScheme;
  private UsernameSegment username;
  private Password password;
  private Host host;
  private Port port;
  private boolean hasAuthority;
  private List<Path> path;
  private boolean isAbsolute;
  private boolean isDirectory;
  private Map<String,Query> query;
  private boolean hasQuery;
  private Fragment fragment;
  private boolean hasFragment;

  Template(
      Scheme scheme,
      boolean hasScheme,
      UsernameSegment username,
      Password password,
      Host host,
      Port port,
      boolean hasAuthority,
      List<Path> path,
      boolean isAbsolute,
      boolean isDirectory,
      LinkedHashMap<String,Query> query,
      boolean hasQuery,
      Fragment fragment,
      boolean hasFragment ) {
    this.scheme = scheme;
    this.hasScheme = hasScheme;
    this.username = username;
    this.password = password;
    this.host = host;
    this.port = port;
    this.hasAuthority = hasAuthority;
    this.path = Collections.unmodifiableList( path );
    this.isAbsolute = isAbsolute;
    this.isDirectory = isDirectory;
    this.query = Collections.unmodifiableMap( query );
    this.hasQuery = hasQuery;
    this.fragment = fragment;
    this.hasFragment = hasFragment;
  }

  public Scheme getScheme() {
    return scheme;
  }

  public boolean hasScheme() {
    return hasScheme;
  }

  public UsernameSegment getUsername() {
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

  public boolean hasQuery() {
    return hasQuery;
  }

  public Fragment getFragment() {
    return fragment;
  }

  public boolean hasFragment() {
    return hasFragment;
  }

}
