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

import org.apache.hadoop.gateway.filter.rewrite.i18n.UrlRewriteMessages;
import org.apache.hadoop.gateway.filter.rewrite.impl.UrlRewriteContextImpl;
import org.apache.hadoop.gateway.filter.rewrite.impl.UrlRewriteFunctionProcessorFactory;
import org.apache.hadoop.gateway.filter.rewrite.impl.UrlRewriteStepProcessorHolder;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteStepStatus;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.util.urltemplate.Matcher;
import org.apache.hadoop.gateway.util.urltemplate.Resolver;
import org.apache.hadoop.gateway.util.urltemplate.Template;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriter.Direction.IN;
import static org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriter.Direction.OUT;

public class UrlRewriteProcessor implements UrlRewriter {

  private static final UrlRewriteMessages LOG = MessagesFactory.get( UrlRewriteMessages.class );

  UrlRewriteEnvironment environment;
  UrlRewriteRulesDescriptor descriptor;
  Map<String,UrlRewriteStepProcessorHolder> rules = new HashMap<String,UrlRewriteStepProcessorHolder>();
  Matcher<UrlRewriteStepProcessorHolder> inbound = new Matcher<UrlRewriteStepProcessorHolder>();
  Matcher<UrlRewriteStepProcessorHolder> outbound = new Matcher<UrlRewriteStepProcessorHolder>();
  Map<String,UrlRewriteFunctionProcessor> functions = new HashMap<String,UrlRewriteFunctionProcessor>();

  public UrlRewriteProcessor() {
  }

  // Convert the descriptor into processors.
  public void initialize( UrlRewriteEnvironment environment, UrlRewriteRulesDescriptor descriptor ) {
    this.environment = environment;
    this.descriptor = descriptor;
    initializeFunctions( descriptor );
    initializeRules( descriptor );
  }

  public UrlRewriteRulesDescriptor getConfig() {
    return descriptor;
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
        // Ignore it and it won't be available as a function.
        LOG.failedToInitializeRewriteFunctions( e );
      }
    }
  }

  private void initializeRules( UrlRewriteRulesDescriptor descriptor ) {
    for( UrlRewriteRuleDescriptor ruleDescriptor : descriptor.getRules() ) {
      try {
        UrlRewriteStepProcessorHolder ruleProcessor = new UrlRewriteStepProcessorHolder();
        ruleProcessor.initialize( environment, ruleDescriptor );
        if( !rules.containsKey( ruleDescriptor.name() ) ) {
          rules.put( ruleDescriptor.name(), ruleProcessor );
        }
        Template template = ruleDescriptor.template();
        if( template != null ) {
          EnumSet<Direction> directions = ruleDescriptor.directions();
          if( directions == null || directions.isEmpty() ) {
            inbound.add( template, ruleProcessor );
            outbound.add( template, ruleProcessor );
          } else if( directions.contains( IN ) ) {
            inbound.add( template, ruleProcessor );
          } else if ( directions.contains( OUT ) ) {
            outbound.add( template, ruleProcessor );
          }
        }
      } catch( Exception e ) {
        LOG.failedToInitializeRewriteRules( e );
      }
    }
  }

  public void destroy() {
    for( UrlRewriteStepProcessorHolder rule : rules.values() ) {
      try {
        rule.destroy();
      } catch ( Exception e ) {
        LOG.failedToDestroyRewriteRuleProcessor( e );
      }
    }
    for( UrlRewriteFunctionProcessor function : functions.values() ) {
      try {
        function.destroy();
      } catch( Exception e ) {
        LOG.failedToDestroyRewriteFunctionProcessor( e );
      }
    }
  }

  @Override
  public Template rewrite( Resolver resolver, Template inputUri, Direction direction, String ruleName ) {
    Template outputUri = inputUri;
    UrlRewriteStepProcessorHolder stepHolder = null;
    if( ruleName == null || "*".equals( ruleName ) ) {
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
        stepHolder = match.getValue();
      }
    } else if( !ruleName.isEmpty() ) {
      stepHolder = rules.get( ruleName );
    }
    if( stepHolder != null ) {
      UrlRewriteContext context = new UrlRewriteContextImpl( environment, resolver, functions, direction, inputUri );
      try {
        UrlRewriteStepStatus stepStatus = stepHolder.process( context );
        if( UrlRewriteStepStatus.SUCCESS == stepStatus ) {
          outputUri = context.getCurrentUrl();
        } else {
          outputUri = null;
        }
      } catch( Exception e ) {
        LOG.failedToRewriteUrl( e );
        outputUri = null;
      }
    }
//    System.out.println( "REWRITE: " + inputUri );
//    System.out.println( "       : " + outputUri );
    return outputUri;
  }

}
