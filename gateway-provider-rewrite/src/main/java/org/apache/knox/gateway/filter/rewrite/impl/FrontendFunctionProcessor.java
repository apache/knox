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

import org.apache.knox.gateway.filter.rewrite.api.FrontendFunctionDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.i18n.UrlRewriteResources;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteResolver;
import org.apache.knox.gateway.i18n.resources.ResourcesFactory;
import org.apache.knox.gateway.services.GatewayServices;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FrontendFunctionProcessor implements UrlRewriteFunctionProcessor<FrontendFunctionDescriptor> {

  private static UrlRewriteResources RES = ResourcesFactory.get( UrlRewriteResources.class );

  private Map<String,UrlRewriteResolver> resolvers;

  @Override
  public String name() {
    return FrontendFunctionDescriptor.FUNCTION_NAME;
  }

  @Override
  public void initialize( UrlRewriteEnvironment environment, FrontendFunctionDescriptor descriptor ) throws Exception {
    if( environment == null ) {
      throw new IllegalArgumentException( "environment==null" );
    }
    URI frontend = environment.getAttribute( FrontendFunctionDescriptor.FRONTEND_URI_ATTRIBUTE );
    resolvers = new HashMap<>();
    if( frontend == null ) {
      resolvers.put( "url", new ParamResolver( "gateway.url" ) );
      resolvers.put( "addr", new ParamResolver( "gateway.addr" ) );
      resolvers.put( "scheme", new ParamResolver( "gateway.scheme" ) );
      resolvers.put( "host", new ParamResolver( "gateway.host" ) );
      resolvers.put( "port", new ParamResolver( "gateway.port" ) );
      resolvers.put( "path", new ParamResolver( "gateway.path" ) );
    } else {
      resolvers.put( "url", new FixedResolver( frontend.toString() ) );
      resolvers.put( "addr", new FixedResolver( frontend.getHost() + ":" + frontend.getPort() ) );
      resolvers.put( "scheme", new FixedResolver( frontend.getScheme() ) );
      resolvers.put( "host", new FixedResolver( frontend.getHost() ) );
      resolvers.put( "port", new FixedResolver( Integer.toString( frontend.getPort() ) ) );
      resolvers.put( "path", new FixedResolver( frontend.getPath() ) );
    }
    resolvers.put( "topology", new FixedResolver( (String)environment.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE) ) );
    resolvers.put( "gateway.name", new FixedResolver( (String)environment.getAttribute(GatewayServices.GATEWAY_NAME) ) );
    resolvers.put( "address", resolvers.get( "addr" ) );
  }

  @Override
  public void destroy() throws Exception {
    resolvers.clear();
  }

  @Override
  public List<String> resolve( UrlRewriteContext context, List<String> parameters ) throws Exception {
    String parameter = "url";
    if( parameters != null && !parameters.isEmpty() ) {
      String first = parameters.get( 0 );
      if( first != null ) {
        parameter = first;
      }
    }
    parameter = parameter.trim().toLowerCase(Locale.ROOT);
    UrlRewriteResolver resolver = resolvers.get( parameter );
    if( resolver == null ) {
      throw new IllegalArgumentException( RES.invalidFrontendFunctionParameter( parameter ) );
    }
    return resolver.resolve( context, parameters );
  }

  private static class ParamResolver implements UrlRewriteResolver {

    private String paramName;

    ParamResolver( String paramName ) {
      this.paramName = paramName;
    }

    @Override
    public List<String> resolve( UrlRewriteContext context, List<String> parameter ) throws Exception {
      return context.getParameters().resolve( paramName );
    }
  }

  private static class FixedResolver implements UrlRewriteResolver {

    private List<String> fixedValues;

    FixedResolver( String... fixedValues ) {
      this.fixedValues = Arrays.asList( fixedValues );
    }

    @Override
    public List<String> resolve( UrlRewriteContext context, List<String> parameter ) throws Exception {
      return fixedValues;
    }
  }
}
