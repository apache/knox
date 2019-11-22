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

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterDetectDescriptor;

public class UrlRewriteFilterDetectDescriptorImpl
    extends UrlRewriteFilterGroupDescriptorBase
    implements UrlRewriteFilterDetectDescriptor {

  private String value;
  private Object compiledValue;

  @Override
  public String value() {
    return value;
  }

  @Override
  public UrlRewriteFilterDetectDescriptor value( String value ) {
    this.value = value;
    return this;
  }

  public void setValue( String value ) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  @Override
  public <C> C compiledValue() {
    return (C)compiledValue;
  }

  @Override
  public UrlRewriteFilterDetectDescriptor compiledValue( String compiledValue ) {
    this.compiledValue = compiledValue;
    return this;
  }

  @Override
  public <C> C compiledValue( Compiler<C> compiler ) {
    compiledValue = compiler.compile( value, (C)compiledValue );
    return (C)compiledValue;
  }

}
