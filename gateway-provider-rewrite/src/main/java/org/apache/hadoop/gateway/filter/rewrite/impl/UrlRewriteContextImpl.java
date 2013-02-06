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
package org.apache.hadoop.gateway.filter.rewrite.impl;

import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriter;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.hadoop.gateway.util.urltemplate.Params;
import org.apache.hadoop.gateway.util.urltemplate.Resolver;
import org.apache.hadoop.gateway.util.urltemplate.Template;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UrlRewriteContextImpl implements UrlRewriteContext {

  private Resolver resolver;
  private ContextParameters params;
  private UrlRewriter.Direction direction;
  private Template originalUrl;
  private Template currentUrl;

  public UrlRewriteContextImpl(
      Resolver resolver,
      UrlRewriter.Direction direction,
      Template url ) {
    this.resolver = resolver;
    this.params = new ContextParameters();
    this.direction = direction;
    this.originalUrl = url;
    this.currentUrl = url;
  }

  @Override
  public UrlRewriter.Direction getDirection() {
    return direction;
  }

  @Override
  public Template getOriginalUrl() {
    return originalUrl;
  }

  @Override
  public Template getCurrentUrl() {
    return currentUrl;
  }

  @Override
  public void setCurrentUrl( Template url ) {
    currentUrl = url;
  }

  @Override
  public void addParameters( Params parameters ) {
    params.add( parameters );
  }

  @Override
  public Params getParameters() {
    return params;
  }

  private class ContextParameters implements Params {

    Map<String,List<String>> map = new HashMap<String,List<String>>();

    @Override
    public Set<String> getNames() {
      return map.keySet();
    }

    @Override
    public List<String> resolve( String name ) {
      List<String> values = map.get( name );
      if( values == null ) {
        values = resolver.resolve( name );
      }
      return values;
    }

    public void add( Params params ) {
      for( String name : params.getNames() ) {
        map.put( name, params.resolve( name ) );
      }
    }

  }

}
