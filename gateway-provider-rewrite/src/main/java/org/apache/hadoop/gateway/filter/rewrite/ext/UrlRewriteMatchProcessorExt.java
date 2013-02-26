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
package org.apache.hadoop.gateway.filter.rewrite.ext;

import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteStepProcessor;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteStepStatus;
import org.apache.hadoop.gateway.util.urltemplate.Matcher;

public class UrlRewriteMatchProcessorExt implements UrlRewriteStepProcessor<UrlRewriteMatchDescriptor> {

  //private UrlRewriteMatchDescriptor descriptor;
  private Matcher<Void> matcher;

  @Override
  public String getType() {
    return "match";
  }

  @Override
  public void initialize( UrlRewriteEnvironment environment, UrlRewriteMatchDescriptor descriptor ) throws Exception {
    //this.descriptor = descriptor;
    this.matcher = new Matcher<Void>( descriptor.template(), null );
  }

  @Override
  public UrlRewriteStepStatus process( UrlRewriteContext context ) throws Exception {
    UrlRewriteStepStatus status = UrlRewriteStepStatus.FAILURE;
    Matcher.Match match = matcher.match( context.getCurrentUrl() );
    if( match != null ) {
      context.addParameters( match.getParams() );
      status = UrlRewriteStepStatus.SUCCESS;
    }
    return status;
  }

  @Override
  public void destroy() {
    //descriptor = null;
    matcher = null;
  }

}
