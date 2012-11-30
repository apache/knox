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

import org.apache.hadoop.gateway.descriptor.ClusterFilterDescriptor;
import org.apache.hadoop.gateway.descriptor.ClusterFilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ClusterResourceDescriptor;

import java.util.ArrayList;
import java.util.List;

public class ClusterFilterImpl implements ClusterFilterDescriptor {

  private ClusterResourceDescriptor resource;
  private String role;
  private String impl;
  private List<ClusterFilterParamDescriptor> params;

  ClusterFilterImpl( ClusterResourceDescriptor resource ) {
    this.resource = resource;
    this.params = new ArrayList<ClusterFilterParamDescriptor>();
  }

  @Override
  public ClusterResourceDescriptor up() {
    return resource;
  }

  @Override
  public ClusterFilterDescriptor role( String role ) {
    this.role = role;
    return this;
  }

  @Override
  public String role() {
    return role;
  }

  @Override
  public ClusterFilterDescriptor impl( String impl ) {
    this.impl = impl;
    return this;
  }

  @Override
  public String impl() {
    return impl;
  }

  @Override
  public List<ClusterFilterParamDescriptor> params() {
    return params;
  }

  @Override
  public ClusterFilterParamDescriptor addParam() {
    ClusterFilterParamDescriptor param = createParam();
    addParam( param );
    return param;
  }

  @Override
  public ClusterFilterParamDescriptor createParam() {
    return new ClusterFilterParamImpl( this );
  }

  @Override
  public void addParam( ClusterFilterParamDescriptor param ) {
    params.add( param );
  }

}
