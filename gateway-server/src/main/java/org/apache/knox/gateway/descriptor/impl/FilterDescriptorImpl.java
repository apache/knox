/*
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
package org.apache.knox.gateway.descriptor.impl;

import org.apache.knox.gateway.descriptor.FilterDescriptor;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;

import javax.servlet.Filter;
import java.util.ArrayList;
import java.util.List;

public class FilterDescriptorImpl implements FilterDescriptor {

  private ResourceDescriptor parent;
  private List<FilterParamDescriptor> params;
  private String name;
  private String role;
  private String impl;

  FilterDescriptorImpl( ResourceDescriptor parent ) {
    this.parent = parent;
    this.params = new ArrayList<>();
  }

  @Override
  public ResourceDescriptor up() {
    return parent;
  }

  @Override
  public FilterDescriptor name( String name ) {
    this.name = name;
    return this;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public FilterDescriptor role( String role ) {
    this.role = role;
    return this;
  }

  @Override
  public String role() {
    return role;
  }

  @Override
  public FilterDescriptor impl( String impl ) {
    this.impl = impl;
    return this;
  }

  @Override
  public FilterDescriptor impl( Class<? extends Filter> type ) {
    this.impl = type.getName();
    return this;
  }

  @Override
  public String impl() {
    return impl;
  }

  @Override
  public List<FilterParamDescriptor> params() {
    return params;
  }

  @Override
  public FilterParamDescriptor param() {
    FilterParamDescriptor param = createParam();
    param( param );
    return param;
  }

  @Override
  public FilterParamDescriptor createParam() {
    return new FilterParamDescriptorImpl( this );
  }

  @Override
  public FilterDescriptor param( FilterParamDescriptor param ) {
    param.up( this );
    params.add( param );
    return this;
  }

  @Override
  public FilterDescriptor params( List<FilterParamDescriptor> params ) {
    if( params != null ) {
      for( FilterParamDescriptor param : params ) {
        param.up( this );
      }
      this.params.addAll( params );
    }
    return this;
  }

}
