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
import org.apache.hadoop.gateway.svcregfunc.api.ServiceUrlFunctionDescriptor;

public class ServiceUrlFunctionProcessor
    extends ServiceRegistryFunctionProcessorBase<ServiceUrlFunctionDescriptor>
    implements UrlRewriteFunctionProcessor<ServiceUrlFunctionDescriptor> {

  @Override
  public String name() {
    return ServiceUrlFunctionDescriptor.FUNCTION_NAME;
  }

  public String resolve( UrlRewriteContext context, String parameter ) throws Exception {
    return super.resolve( context, parameter );
  }

}

