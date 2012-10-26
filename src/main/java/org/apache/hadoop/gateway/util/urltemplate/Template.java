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

  private SchemeSegment scheme;
  private boolean hasScheme;
  private UsernameSegment username;
  private PasswordSegment password;
  private HostSegment host;
  private PortSegment port;
  private boolean hasAuthority;
  private List<PathSegment> path;
  private boolean isAbsolute;
  private boolean isDirectory;
  private Map<String,QuerySegment> query;
  private boolean hasQuery;
  private FragmentSegment fragment;
  private boolean hasFragment;

  Template(
      SchemeSegment scheme,
      boolean hasScheme,
      UsernameSegment username,
      PasswordSegment password,
      HostSegment host,
      PortSegment port,
      boolean hasAuthority,
      List<PathSegment> path,
      boolean isAbsolute,
      boolean isDirectory,
      LinkedHashMap<String,QuerySegment> query,
      boolean hasQuery,
      FragmentSegment fragment,
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

  public SchemeSegment getScheme() {
    return scheme;
  }

  public boolean hasScheme() {
    return hasScheme;
  }

  public UsernameSegment getUsername() {
    return username;
  }

  public PasswordSegment getPassword() {
    return password;
  }

  public HostSegment getHost() {
    return host;
  }

  public PortSegment getPort() {
    return port;
  }

  public boolean hasAuthority() {
    return hasAuthority;
  }

  public List<PathSegment> getPath() {
    return path;
  }

  public boolean isAbsolute() {
    return isAbsolute;
  }

  public boolean isDirectory() {
    return isDirectory;
  }

  public Map<String,QuerySegment> getQuery() {
    return query;
  }

  public boolean hasQuery() {
    return hasQuery;
  }

  public FragmentSegment getFragment() {
    return fragment;
  }

  public boolean hasFragment() {
    return hasFragment;
  }

}
