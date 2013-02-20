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
import org.apache.hadoop.gateway.filter.rewrite.impl.UrlRewriteFunctionProcessorFactory;
import org.apache.hadoop.gateway.filter.rewrite.impl.UrlRewriteFunctionResolver;
import org.apache.hadoop.gateway.filter.rewrite.impl.UrlRewriteStepProcessorHolder;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteStepStatus;
import org.apache.hadoop.gateway.util.urltemplate.Matcher;
import org.apache.hadoop.gateway.util.urltemplate.Resolver;
import org.apache.hadoop.gateway.util.urltemplate.Template;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriter.Direction.IN;
import static org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriter.Direction.OUT;

public class UrlRewriteProcessor implements UrlRewriter {

  UrlRewriteEnvironment environment;
  List<UrlRewriteStepProcessorHolder> rules = new ArrayList<UrlRewriteStepProcessorHolder>();
  Matcher<UrlRewriteStepProcessorHolder> inbound = new Matcher<UrlRewriteStepProcessorHolder>();
  Matcher<UrlRewriteStepProcessorHolder> outbound = new Matcher<UrlRewriteStepProcessorHolder>();
  Map<String,UrlRewriteFunctionProcessor> functions = new HashMap<String,UrlRewriteFunctionProcessor>();

  public UrlRewriteProcessor() {
  }

  // Convert the descriptor into processors.
  public void initialize( UrlRewriteEnvironment environment, UrlRewriteRulesDescriptor descriptor ) {
    this.environment = environment;
    initializeFunctions( descriptor );
    initializeRules( descriptor );
  }

  @SuppressWarnings("unchecked")
  private void initializeFunctions( UrlRewriteRulesDescriptor rules ) {
    for( String name : UrlRewriteFunctionDescriptorFactory.getNames() ) {
      try {
        UrlRewriteFunctionDescriptor descriptor = rules.getFunction( name );
        UrlRewriteFunctionProcessor processor = UrlRewriteFunctionProcessorFactory.create( name, descriptor );
        processor.initialize( environment, descriptor );
        functions.put( name, processor );
      } catch( Exception e ) {
        //TODO: Proper i18n stack trace logging.
        e.printStackTrace();
        // Ignore it and it won't be available as a function.
      }
    }
  }

  private void initializeRules( UrlRewriteRulesDescriptor descriptor ) {
    for( UrlRewriteRuleDescriptor ruleDescriptor : descriptor.getRules() ) {
      try {
        UrlRewriteStepProcessorHolder ruleProcessor = new UrlRewriteStepProcessorHolder();
        ruleProcessor.initialize( environment, ruleDescriptor );
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
        //TODO: Log i18n stack trace properly.
        e.printStackTrace();
        // Ignore it.
      }
    }
    for( UrlRewriteFunctionProcessor function : functions.values() ) {
      try {
        function.destroy();
      } catch( Exception e ) {
        //TODO: Log i18n stack trace properly.
        e.printStackTrace();
        // Ignore it.
      }
    }
  }

  @Override
  public Template rewrite(
      Resolver resolver, Template inputUri, Direction direction ) {
    Template outputUri = inputUri;
    Matcher<UrlRewriteStepProcessorHolder>.Match match = null;
    switch( direction ) {
      case IN:
        match = inbound.match( outputUri );
        break;
      case OUT:
        match = outbound.match( outputUri );
        break;
    }
    if( match != null ) {
      UrlRewriteFunctionResolver function = new UrlRewriteFunctionResolver( functions, resolver );
      UrlRewriteContext context = new UrlRewriteContextImpl( environment, function, direction, uri );
      try {
        UrlRewriteStepProcessorHolder stepHolder = match.getValue();
        UrlRewriteStepStatus stepStatus = stepHolder.process( context );
        if( UrlRewriteStepStatus.SUCCESS == stepStatus ) {
          outputUri = context.getCurrentUrl();
        } else {
          outputUri = null;
        }
      } catch( Exception e ) {
        //TODO: I18N Log stack trace properly.
        e.printStackTrace();
        outputUri = null;
      }
    }
    return outputUri;
  }

}
