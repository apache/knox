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

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterPathDescriptor;

public class UrlRewriteFilterSelectorDescriptorBase<T> implements UrlRewriteFilterPathDescriptor<T> {

  private String path;
  private Object compiledPath;

  @Override
  public String path() {
    return path;
  }

  @Override
  public T path( String path ) {
    this.path = path;
    return (T)this;
  }

  public void setPath( String path ) {
    this.path = path;
  }

  public String getPath()  {
    return path;
  }

  @Override
  public <C> C compiledPath() {
    return (C)compiledPath;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T compiledPath( Object compiledPath ) {
    this.compiledPath = compiledPath;
    return (T)this;
  }

  @Override
  public <C> C compiledPath( Compiler<C> compiler ) {
    compiledPath = compiler.compile( path, (C)compiledPath );
    return (C)compiledPath;
  }

}
