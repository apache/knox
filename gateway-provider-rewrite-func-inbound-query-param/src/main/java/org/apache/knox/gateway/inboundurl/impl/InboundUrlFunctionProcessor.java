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
package org.apache.knox.gateway.inboundurl.impl;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.apache.knox.gateway.inboundurl.api.InboundUrlFunctionDescriptor;
import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteResponse;

import java.util.Collections;
import java.util.List;

public class InboundUrlFunctionProcessor
    implements UrlRewriteFunctionProcessor<InboundUrlFunctionDescriptor> {

  @Override
  public String name() {
    return InboundUrlFunctionDescriptor.FUNCTION_NAME;
  }

  @Override
  public void initialize( UrlRewriteEnvironment environment, InboundUrlFunctionDescriptor descriptor ) throws Exception {
  }

  @Override
  public void destroy() throws Exception {
  }

  @Override
  public List<String> resolve( UrlRewriteContext context, List<String> parameters ) throws Exception {
      if( parameters == null || parameters.size() == 0 ) {
        return Collections.emptyList();
      } else {
        switch( context.getDirection() ) {
          case IN:
            return Collections.emptyList();
          case OUT:
            return context.getParameters().resolve( UrlRewriteResponse.INBOUND_QUERY_PARAM_PREFIX + parameters.get( 0 ));
          default:
            return Collections.emptyList();
        }
      }
  }
}
