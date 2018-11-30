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

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriter;
import org.apache.knox.gateway.filter.rewrite.i18n.UrlRewriteMessages;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.util.urltemplate.Evaluator;
import org.apache.knox.gateway.util.urltemplate.Params;
import org.apache.knox.gateway.util.urltemplate.Resolver;
import org.apache.knox.gateway.util.urltemplate.Template;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UrlRewriteContextImpl implements UrlRewriteContext {

  private static final UrlRewriteMessages LOG = MessagesFactory.get( UrlRewriteMessages.class );

  private UrlRewriteEnvironment environment;
  private Resolver resolver;
  private Evaluator evaluator;
  private Map<String,UrlRewriteFunctionProcessor> functions;
  private ContextParameters params;
  private UrlRewriter.Direction direction;
  private Template originalUrl;
  private Template currentUrl;

  public UrlRewriteContextImpl(
      UrlRewriteEnvironment environment,
      Resolver resolver,
      Map<String,UrlRewriteFunctionProcessor> functions,
      UrlRewriter.Direction direction,
      Template url ) {
    this.environment = environment;
    this.resolver = resolver;
    this.functions = functions;
    this.params = new ContextParameters();
    this.evaluator = new ContextEvaluator();
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

  @Override
  public Evaluator getEvaluator() {
    return evaluator;
  }

  private class ContextParameters implements Params {
    Map<String,List<String>> map = new LinkedHashMap<>();

    @Override
    public Set<String> getNames() {
      return map.keySet();
    }

    @Override
    public List<String> resolve( String name ) {
      List<String> values = map.get( name ); // Try to find the name in the context map.
      if( values == null ) {
        try {
          values = resolver.resolve( name );
          if( values == null ) {
            values = environment.resolve( name ); // Try to find the name in the environment.
          }
        } catch( Exception e ) {
          LOG.failedToFindValuesByParameter( name, e );
          // Ignore it and return null.
        }
      }
      return values;
    }

    public void add( Params params ) {
      for( String name : params.getNames() ) {
        map.put( name, params.resolve( name ) );
      }
    }

  }

  private class ContextEvaluator implements Evaluator {

    @Override
    public List<String> evaluate( String function, List<String> parameters ) {
      List<String> results = null;
      UrlRewriteFunctionProcessor processor = functions.get( function );
      if( processor != null ) {
        try {
          results = processor.resolve( UrlRewriteContextImpl.this, parameters );
        } catch( Exception e ) {
          LOG.failedToInvokeRewriteFunction( function, e );
          results = null;
        }
      }
      return results;
    }
  }

}
