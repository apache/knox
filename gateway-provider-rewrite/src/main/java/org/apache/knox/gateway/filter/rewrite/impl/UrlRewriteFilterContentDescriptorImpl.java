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
package org.apache.knox.gateway.filter.rewrite.impl;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterBufferDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterContentDescriptor;

public class UrlRewriteFilterContentDescriptorImpl
    extends UrlRewriteFilterGroupDescriptorBase
    implements UrlRewriteFilterContentDescriptor {

  private String type;

  private String asType;

  public UrlRewriteFilterContentDescriptorImpl() {
  }

  @Override
  public String type() {
    return this.type;
  }

  @Override
  public String asType() {
    return asType;
  }

  @Override
  public UrlRewriteFilterContentDescriptor type( String type ) {
    this.type = type;
    return this;
  }

  @Override
  public UrlRewriteFilterContentDescriptor asType( String type ) {
    asType = type;
    return this;
  }

  public void setType(String type ) {
    type( type );
  }

  public String getType() {
    return type;
  }

  public String getAsType() {
    return asType;
  }

  public void setAsType(String asType) {
    this.asType = asType;
  }

  @Override
  public UrlRewriteFilterBufferDescriptor addBuffer( String path ) {
    UrlRewriteFilterBufferDescriptor descriptor = new UrlRewriteFilterBufferDescriptorImpl();
    descriptor.path( path );
    addSelector( descriptor );
    return descriptor;
  }

}
