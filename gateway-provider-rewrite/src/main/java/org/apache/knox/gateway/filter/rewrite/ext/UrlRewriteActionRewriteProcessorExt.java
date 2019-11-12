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
package org.apache.knox.gateway.filter.rewrite.ext;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteStepProcessor;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteStepStatus;
import org.apache.knox.gateway.util.urltemplate.Expander;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Template;

public class UrlRewriteActionRewriteProcessorExt
    implements UrlRewriteStepProcessor<UrlRewriteActionRewriteDescriptorExt> {

  private Template template;

  @Override
  public String getType() {
    return "rewrite";
  }

  @Override
  public void initialize( UrlRewriteEnvironment environment, UrlRewriteActionRewriteDescriptorExt descriptor ) throws Exception {
    if ( descriptor.parameter() != null ) {
      this.template = Parser.parseTemplate( descriptor.parameter() );
    } else {
      this.template = Parser.parseTemplate( "" );
    }
  }

  @Override
  public UrlRewriteStepStatus process( UrlRewriteContext context ) throws Exception {
    Template rewritten = Expander.expandToTemplate( template, context.getParameters(), context.getEvaluator() );
    context.setCurrentUrl( rewritten );
    return UrlRewriteStepStatus.SUCCESS;
  }

  @Override
  public void destroy() {
  }

}
