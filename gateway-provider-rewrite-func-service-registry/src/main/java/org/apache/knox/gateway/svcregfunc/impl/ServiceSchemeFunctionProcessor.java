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
package org.apache.knox.gateway.svcregfunc.impl;

import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.apache.knox.gateway.svcregfunc.api.ServiceSchemeFunctionDescriptor;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Scheme;
import org.apache.knox.gateway.util.urltemplate.Template;

import java.util.ArrayList;
import java.util.List;

public class ServiceSchemeFunctionProcessor
    extends ServiceRegistryFunctionProcessorBase<ServiceSchemeFunctionDescriptor>
    implements UrlRewriteFunctionProcessor<ServiceSchemeFunctionDescriptor> {

  @Override
  public String name() {
    return ServiceSchemeFunctionDescriptor.FUNCTION_NAME;
  }

  @Override
  public List<String> resolve( UrlRewriteContext context, List<String> parameters ) throws Exception {
    List<String> results = null;
    if( parameters != null ) {
      results = new ArrayList<>( parameters.size() );
      for( String parameter : parameters ) {
        String url = lookupServiceUrl( parameter );
        if( url != null && !url.equals( parameter ) ) {
          Template template = Parser.parseLiteral( url );
          Scheme scheme = template.getScheme();
          if( scheme != null ) {
            parameter = scheme.getFirstValue().getPattern();
          }
        }
        results.add( parameter );
      }
    }
    return results;
  }

}

