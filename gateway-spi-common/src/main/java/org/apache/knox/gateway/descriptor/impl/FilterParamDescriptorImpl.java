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

public class FilterParamDescriptorImpl implements FilterParamDescriptor {

  private FilterDescriptor parent;
  private String name;
  private String value;

  FilterParamDescriptorImpl() {
    this.parent = null;
  }

  FilterParamDescriptorImpl( FilterDescriptor parent ) {
    this.parent = parent;
  }

  @Override
  public void up( FilterDescriptor parent ) {
    this.parent = parent;
  }

  @Override
  public FilterDescriptor up() {
    return parent;
  }

  @Override
  public FilterParamDescriptor name( String name ) {
    this.name = name;

    // If there is already a param identified by the new name, remove it, such that it is REPLACED with this new param
    if (parent != null) {
      for (FilterParamDescriptor param : parent.params()) {
        if (name.equals(param.name()) && (param != this)) {
          parent.params().remove(param);
          break;
        }
      }
    }

    return this;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public FilterParamDescriptor value( String value ) {
    this.value = value;
    return this;
  }

  @Override
  public String value() {
    return value;
  }

}
