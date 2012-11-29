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
import org.apache.hadoop.gateway.descriptor.ClusterResourceDescriptor;

import java.util.ArrayList;
import java.util.List;

public class ClusterDescriptorImpl implements ClusterDescriptor {

  private List<ClusterResourceDescriptor> resources;

  public ClusterDescriptorImpl() {
    resources = new ArrayList<ClusterResourceDescriptor>();
  }

  @Override
  public List<ClusterResourceDescriptor> resources() {
    return resources;
  }

  @Override
  public ClusterResourceDescriptor addResource() {
    ClusterResourceDescriptor resource = createResource();
    addResource( resource );
    return resource;
  }

  @Override
  public ClusterResourceDescriptor createResource() {
    return new ClusterResourceImpl( this );
  }

  @Override
  public void addResource( ClusterResourceDescriptor resource ) {
    resources.add( resource );
  }

}
