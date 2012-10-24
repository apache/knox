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
package org.apache.hadoop.gateway.util.uritemplate;

import java.util.*;

public class Template {

  //private SchemeSegment scheme;
  //private HostSegment host;
  //private PortSegment port;
  private boolean isAbsolute;
  private boolean isDirectory;
  private boolean hasQuery;
  private List<PathSegment> path;
  private Map<String,QuerySegment> query;

  Template(
      List<PathSegment> path,
      boolean isAbsolute,
      boolean isDirectory,
      LinkedHashMap<String,QuerySegment> query,
      boolean hasQuery ) {
    this.path = Collections.unmodifiableList( path );
    this.isAbsolute = isAbsolute;
    this.isDirectory = isDirectory;
    this.query = Collections.unmodifiableMap( query );
    this.hasQuery = hasQuery;
  }

  public List<PathSegment> getPath() {
    return path;
  }

  public Map<String,QuerySegment> getQuery() {
    return query;
  }

  public boolean isAbsolute() {
    return isAbsolute;
  }

  public boolean isDirectory() {
    return isDirectory;
  }

  public boolean hasQuery() {
    return hasQuery;
  }

}
