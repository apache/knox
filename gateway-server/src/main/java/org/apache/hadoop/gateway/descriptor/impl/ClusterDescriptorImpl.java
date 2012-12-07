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
import org.apache.hadoop.gateway.descriptor.ClusterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;

import java.util.ArrayList;
import java.util.List;

public class ClusterDescriptorImpl implements ClusterDescriptor {

  private List<ClusterParamDescriptor> params;
  private List<ResourceDescriptor> resources;

  public ClusterDescriptorImpl() {
    this.params = new ArrayList<ClusterParamDescriptor>();
    this.resources = new ArrayList<ResourceDescriptor>();
  }

  @Override
  public List<ResourceDescriptor> resources() {
    return resources;
  }

  @Override
  public ResourceDescriptor addResource() {
    ResourceDescriptor resource = createResource();
    addResource( resource );
    return resource;
  }

  @Override
  public ResourceDescriptor createResource() {
    return new ResourceDescriptorImpl( this );
  }

  @Override
  public void addResource( ResourceDescriptor resource ) {
    resources.add( resource );
  }

  @Override
  public List<ClusterParamDescriptor> params() {
    return params;
  }

  @Override
  public ClusterParamDescriptor addParam() {
    ClusterParamDescriptor param = createParam();
    addParam( param );
    return param;
  }

  @Override
  public ClusterParamDescriptor createParam() {
    return new ClusterParamDescriptorImpl( this );
  }

  @Override
  public void addParam( ClusterParamDescriptor param ) {
    param.up( this );
    params.add( param );
  }

  @Override
  public void addParams( List<ClusterParamDescriptor> params ) {
    if( params != null ) {
      for( ClusterParamDescriptor param : params ) {
        param.up( this );
      }
      this.params.addAll( params );
    }
  }

}
