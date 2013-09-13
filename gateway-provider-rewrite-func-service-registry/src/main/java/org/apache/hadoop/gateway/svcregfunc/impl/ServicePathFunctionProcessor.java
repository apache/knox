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
package org.apache.hadoop.gateway.svcregfunc.impl;

import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.apache.hadoop.gateway.svcregfunc.api.ServicePathFunctionDescriptor;
import org.apache.hadoop.gateway.util.urltemplate.Parser;
import org.apache.hadoop.gateway.util.urltemplate.Path;
import org.apache.hadoop.gateway.util.urltemplate.Template;

import java.util.List;

public class ServicePathFunctionProcessor
    extends ServiceRegistryFunctionProcessorBase<ServicePathFunctionDescriptor>
    implements UrlRewriteFunctionProcessor<ServicePathFunctionDescriptor> {

  @Override
  public String name() {
    return ServicePathFunctionDescriptor.FUNCTION_NAME;
  }

  public String resolve( UrlRewriteContext context, String parameter ) throws Exception {
    String value = parameter;
    String url = super.resolve( context, parameter );
    if( url != null && !url.equals(  parameter ) ) {
      Template template = Parser.parse( url );
      List<Path> path = template.getPath();
      if( path != null ) {
        value = toString( path );
      } else {
        value = parameter;
      }
    }
    return value;
  }

  private String toString( List<Path> paths ) {
    StringBuilder s = new StringBuilder();
    for( Path path: paths ) {
      s.append( '/' );
      s.append( path.getFirstValue().getPattern() );
    }
    if( s.length() == 0 ) {
      s.append( '/' );
    }
    return s.toString();
  }

}

