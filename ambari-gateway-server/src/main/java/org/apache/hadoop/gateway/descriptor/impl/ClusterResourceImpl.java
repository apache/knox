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
package org.apache.hadoop.gateway.descriptor.impl;

import org.apache.hadoop.gateway.descriptor.ClusterDescriptor;
import org.apache.hadoop.gateway.descriptor.ClusterFilterDescriptor;
import org.apache.hadoop.gateway.descriptor.ClusterResourceDescriptor;

import java.util.ArrayList;
import java.util.List;

public class ClusterResourceImpl implements ClusterResourceDescriptor {

  private ClusterDescriptor descriptor;
  private String source;
  private String target;
  private List<ClusterFilterDescriptor> filters;

  ClusterResourceImpl( ClusterDescriptor descriptor ) {
    this.descriptor = descriptor;
    this.filters = new ArrayList<ClusterFilterDescriptor>();
  }

  @Override
  public ClusterDescriptor up() {
    return descriptor;
  }

  public ClusterResourceDescriptor source( String source ) {
    this.source = source;
    return this;
  }

  public String source() {
    return source;
  }

  public ClusterResourceDescriptor target( String target ) {
    this.target = target;
    return this;
  }

  public String target() {
    return target;
  }

  @Override
  public List<ClusterFilterDescriptor> filters() {
    return filters;
  }

  @Override
  public ClusterFilterDescriptor addFilter() {
    ClusterFilterDescriptor filter = createFilter();
    addFilter( filter );
    return filter;
  }

  @Override
  public ClusterFilterDescriptor createFilter() {
    return new ClusterFilterImpl( this );
  }

  @Override
  public void addFilter( ClusterFilterDescriptor filter ) {
    filters.add( filter );
  }

}
