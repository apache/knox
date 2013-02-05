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
package org.apache.hadoop.gateway.filter.rewrite.api;

import org.apache.hadoop.gateway.filter.rewrite.impl.UrlRewriteContextImpl;
import org.apache.hadoop.gateway.filter.rewrite.impl.UrlRewriteStepProcessorHolder;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteStepStatus;
import org.apache.hadoop.gateway.util.urltemplate.Matcher;
import org.apache.hadoop.gateway.util.urltemplate.Template;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriter.Direction.IN;
import static org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriter.Direction.OUT;

public class UrlRewriteProcessor implements UrlRewriter {

  List<UrlRewriteStepProcessorHolder> rules = new ArrayList<UrlRewriteStepProcessorHolder>();
  Matcher<UrlRewriteStepProcessorHolder> inbound = new Matcher<UrlRewriteStepProcessorHolder>();
  Matcher<UrlRewriteStepProcessorHolder> outbound = new Matcher<UrlRewriteStepProcessorHolder>();

  public UrlRewriteProcessor() {
  }

  // Convert the descriptor into processors.
  public void initialize( UrlRewriteRulesDescriptor descriptor ) {
    for( UrlRewriteRuleDescriptor ruleDescriptor : descriptor.rules() ) {
      try {
        UrlRewriteStepProcessorHolder ruleProcessor = new UrlRewriteStepProcessorHolder();
        ruleProcessor.initialize( ruleDescriptor );
        Template template = ruleDescriptor.template();
        rules.add( ruleProcessor );
        EnumSet<Direction> directions = ruleDescriptor.directions();
        if( directions == null || directions.isEmpty() ) {
          inbound.add( template, ruleProcessor );
          outbound.add( template, ruleProcessor );
        } else if( directions.contains( IN ) ) {
          inbound.add( template, ruleProcessor );
        } else if ( directions.contains( OUT ) ) {
          outbound.add( template, ruleProcessor );
        }
      } catch( Exception e ) {
        //TODO: Log stack trace properly.
        e.printStackTrace();
      }
    }
  }

  public void destroy() {
    for( UrlRewriteStepProcessorHolder rule : rules ) {
      try {
        rule.destroy();
      } catch ( Exception e ) {
        //TODO: Log stack trace properly.
        e.printStackTrace();
      }
    }
  }

  @Override
  public Template rewrite(
      Template uri, Direction direction ) {
    Matcher<UrlRewriteStepProcessorHolder>.Match match = null;
    switch( direction ) {
      case IN:
        match = inbound.match( uri );
        break;
      case OUT:
        match = outbound.match( uri );
        break;
    }
    if( match != null ) {
      UrlRewriteContext context = new UrlRewriteContextImpl( direction, uri );
      try {
        UrlRewriteStepProcessorHolder stepHolder = match.getValue();
        UrlRewriteStepStatus stepStatus = stepHolder.process( context );
        if( UrlRewriteStepStatus.SUCCESS == stepStatus ) {
          uri = context.getCurrentUrl();
        } else {
          uri = null;
        }
      } catch( Exception e ) {
        //TODO: I18N Log stack trace properly.
        e.printStackTrace();
        uri = null;
      }
    }
    return uri;
  }

}
