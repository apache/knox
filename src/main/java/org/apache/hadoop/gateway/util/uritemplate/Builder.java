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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class Builder {

  private boolean isAbsolute;
  private boolean isDirectory;
  private boolean hasQuery;
  private List<PathSegment> pathSegments;
  private LinkedHashMap<String,QuerySegment> querySegments;

  public Builder() {
    this.isAbsolute = false;
    this.isDirectory = false;
    this.hasQuery = false;
    this.pathSegments = new ArrayList<PathSegment>();
    this.querySegments = new LinkedHashMap<String,QuerySegment>();
  }

  public Template build() {
    return new Template( pathSegments, isAbsolute, isDirectory, querySegments, hasQuery );
  }

  public Builder setIsAbsolute( boolean isAbsolute ) {
    this.isAbsolute = isAbsolute;
    return this;
  }
  
  public Builder setIsDirectory( boolean isDirectory ) {
    this.isDirectory = isDirectory;
    return this;
  }

  public Builder setHasQuery( boolean hasQuery ) {
    this.hasQuery = hasQuery;
    return this;
  }

  public Builder addPathSegment( String paramName, String valuePattern ) {
    PathSegment segment = new PathSegment( paramName, valuePattern, 1, 1, false );
    pathSegments.add( segment );
    return this;
  }

  public Builder addQuerySegment( String queryName, String paramName, String valuePattern ) {
    QuerySegment segment = new QuerySegment( queryName, paramName, valuePattern, 1, 1, false );
    querySegments.put( queryName, segment );
    return this;
  }

}
