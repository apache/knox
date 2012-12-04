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
import org.apache.hadoop.gateway.descriptor.ClusterFilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ClusterResourceDescriptor;
import org.apache.hadoop.gateway.descriptor.ClusterResourceParamDescriptor;

import java.util.ArrayList;
import java.util.List;

public class ClusterResourceDescriptorImpl implements ClusterResourceDescriptor {

  private ClusterDescriptor parent;
  private String source;
  private String target;
  private List<ClusterResourceParamDescriptor> params;
  private List<ClusterFilterDescriptor> filters;

  ClusterResourceDescriptorImpl( ClusterDescriptor parent ) {
    this.parent = parent;
    this.params = new ArrayList<ClusterResourceParamDescriptor>();
    this.filters = new ArrayList<ClusterFilterDescriptor>();
  }

  @Override
  public ClusterDescriptor up() {
    return parent;
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
    return new ClusterFilterDescriptorImpl( this );
  }

  @Override
  public void addFilter( ClusterFilterDescriptor filter ) {
    filters.add( filter );
  }

  @Override
  public void addFilters( List<ClusterFilterDescriptor> filters ) {
    this.filters.addAll( filters );
  }

  @Override
  public ClusterFilterParamDescriptor createFilterParam() {
    return new ClusterFilterParamDescriptorImpl();
  }

  @Override
  public List<ClusterResourceParamDescriptor> params() {
    return params;
  }

  @Override
  public ClusterResourceParamDescriptor addParam() {
    ClusterResourceParamDescriptor param = createParam();
    addParam( param );
    return param;
  }

  @Override
  public ClusterResourceParamDescriptor createParam() {
    return new ClusterResourceParamDescriptorImpl( this );
  }

  @Override
  public void addParam( ClusterResourceParamDescriptor param ) {
    param.up( this );
    params.add( param );
  }

  @Override
  public void addParams( List<ClusterResourceParamDescriptor> params ) {
    if( params != null ) {
      for( ClusterResourceParamDescriptor param : params ) {
        param.up( this );
      }
      this.params.addAll( params );
    }
  }

}
