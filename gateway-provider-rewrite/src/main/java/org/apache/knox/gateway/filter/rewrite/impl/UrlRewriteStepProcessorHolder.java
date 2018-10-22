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

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFlowDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteStepDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteStepFlow;
import org.apache.knox.gateway.filter.rewrite.i18n.UrlRewriteMessages;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteStepProcessor;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteStepStatus;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class UrlRewriteStepProcessorHolder implements UrlRewriteStepProcessor {

  private static final UrlRewriteMessages LOG = MessagesFactory.get( UrlRewriteMessages.class );

  private boolean isCondition;
  private UrlRewriteStepDescriptor descriptor;
  private UrlRewriteStepProcessor processor;
  private List<UrlRewriteStepProcessorHolder> childProcessors;

  @Override
  public String getType() {
    return "system";
  }

  public boolean isCondition() {
    return isCondition;
  }

  public boolean isAction() {
    return !isCondition;
  }

  @Override
  @SuppressWarnings( "unchecked" )
  public void initialize( UrlRewriteEnvironment environment, UrlRewriteStepDescriptor descriptor ) throws Exception {
    UrlRewriteStepProcessor processor = UrlRewriteStepProcessorFactory.create( descriptor );
    processor.initialize( environment, descriptor );
    initialize( environment, descriptor, processor );
  }

  // For unit testing.
  @SuppressWarnings("unchecked")
  void initialize( UrlRewriteEnvironment environment, UrlRewriteStepDescriptor descriptor, UrlRewriteStepProcessor processor ) throws Exception {
    this.descriptor = descriptor;
    this.processor = processor;
    this.isCondition = descriptor instanceof UrlRewriteFlowDescriptor;
    this.childProcessors = new ArrayList<>();
    if( isCondition ) {
      UrlRewriteFlowDescriptor flowDescriptor = (UrlRewriteFlowDescriptor)descriptor;
      List<UrlRewriteStepDescriptor> stepList = flowDescriptor.steps();
      if( stepList != null && !stepList.isEmpty() ) {
        Iterator<UrlRewriteStepDescriptor> stepIterator = stepList.iterator();
        while( stepIterator.hasNext() ) {
          UrlRewriteStepDescriptor stepDescriptor = stepIterator.next();
          UrlRewriteStepProcessorHolder stepProcessor = new UrlRewriteStepProcessorHolder();
          stepProcessor.initialize( environment, stepDescriptor );
          childProcessors.add( stepProcessor );
        }
      }
    }
  }

  // For unit testing.
  UrlRewriteStepDescriptor getDescriptor() {
    return descriptor;
  }

  // For unit testing.
  UrlRewriteStepProcessor getProcessor() {
    return processor;
  }

  @Override
  public UrlRewriteStepStatus process( UrlRewriteContext context ) throws Exception {
    UrlRewriteStepStatus status = UrlRewriteStepStatus.SUCCESS;
    // If initialization failed then fail processing
    if( processor != null ) {
      status = processor.process( context );
      if( UrlRewriteStepStatus.SUCCESS == status &&
          descriptor instanceof UrlRewriteFlowDescriptor &&
          !childProcessors.isEmpty() ) {
        UrlRewriteFlowDescriptor flowDescriptor = (UrlRewriteFlowDescriptor)descriptor;
        UrlRewriteStepFlow flow = flowDescriptor.flow();
        if( flow == null ) {
          flow = UrlRewriteStepFlow.AND;
        }
        switch( flow ) {
          case ALL:
            return processAllFlow( context );
          case AND:
            return processAndFlow( context );
          case OR:
            return processOrFlow( context );
        }
      }
    }
    return status;
  }

  private UrlRewriteStepStatus processAllFlow( UrlRewriteContext context ) throws Exception {
    UrlRewriteStepProcessorState state = new UrlRewriteStepProcessorState( childProcessors.iterator() );
    UrlRewriteStepStatus stepStatus = UrlRewriteStepStatus.SUCCESS;
    UrlRewriteStepProcessorHolder step;
    while( state.hasNext() ) {
      while( state.hasNextCondition() ) {
        step = state.nextCondition( stepStatus );
        stepStatus = step.process( context );
        if( stepStatus == UrlRewriteStepStatus.FINISHED ) {
          return stepStatus;
        }
      }
      stepStatus = processActions( context, state );
      if( stepStatus == UrlRewriteStepStatus.FINISHED ) {
        return stepStatus;
      }
    }
    return UrlRewriteStepStatus.SUCCESS;
  }

  // All conditions proceeding a set of one or more actions must succeed for the actions to be executed.
  private UrlRewriteStepStatus processAndFlow( UrlRewriteContext context ) throws Exception {
    UrlRewriteStepProcessorState state = new UrlRewriteStepProcessorState( childProcessors.iterator() );
    UrlRewriteStepStatus stepStatus = UrlRewriteStepStatus.SUCCESS;
    UrlRewriteStepProcessorHolder step;
    while( state.hasNext() ) {
      while( state.hasNextCondition() ) {
        step = state.nextCondition( stepStatus );
        stepStatus = step.process( context );
        if( !( stepStatus == UrlRewriteStepStatus.SUCCESS ) ) {
          return stepStatus;
        }
      }
      stepStatus = processActions( context, state );
      if( !( stepStatus == UrlRewriteStepStatus.SUCCESS ) ) {
        return stepStatus;
      }
    }
    return UrlRewriteStepStatus.SUCCESS;
  }

  // At least one condition proceeding a set of one or more actions must succedd for the actions to be executed.
  private UrlRewriteStepStatus processOrFlow( UrlRewriteContext context ) throws Exception {
    UrlRewriteStepProcessorState state = new UrlRewriteStepProcessorState( childProcessors.iterator() );
    UrlRewriteStepStatus status = UrlRewriteStepStatus.SUCCESS;
    UrlRewriteStepProcessorHolder step;
    while( state.hasNext() ) {
      UrlRewriteStepStatus flowStatus = UrlRewriteStepStatus.FAILURE;
      while( state.hasNextCondition() ) {
        step = state.nextCondition( status );
        if( flowStatus == UrlRewriteStepStatus.FAILURE ) {
          status = step.process( context );
          switch( status ) {
            case SUCCESS:
              flowStatus = UrlRewriteStepStatus.SUCCESS;
              continue;
            case FINISHED:
              return status;
          }
        }
      }
      status = processActions( context, state );
      if( status != UrlRewriteStepStatus.SUCCESS ) {
        return status;
      }
    }
    return UrlRewriteStepStatus.SUCCESS;
  }

  private UrlRewriteStepStatus processActions( UrlRewriteContext context, UrlRewriteStepProcessorState state )
      throws Exception {
    UrlRewriteStepStatus flowStatus = UrlRewriteStepStatus.SUCCESS;
    while( state.hasNextAction() ) {
      if( flowStatus == UrlRewriteStepStatus.SUCCESS ) {
        UrlRewriteStepStatus stepStatus = UrlRewriteStepStatus.SUCCESS;
        UrlRewriteStepProcessorHolder step = state.nextAction( stepStatus );
        stepStatus = step.process( context );
        switch( stepStatus ) {
          case FAILURE:
            flowStatus = UrlRewriteStepStatus.FAILURE;
            continue;
          case FINISHED:
            return stepStatus;
        }
      }
    }
    return flowStatus;
  }

  @Override
  public void destroy() throws Exception {
    destroy( processor );
    if( descriptor instanceof UrlRewriteFlowDescriptor ) {
      for( UrlRewriteStepProcessorHolder childProcessor : childProcessors ) {
        destroy( childProcessor );
      }
    }
  }

  public void destroy( UrlRewriteStepProcessor processor ) {
    if( processor != null ) {
      try {
        processor.destroy();
      } catch( Exception e ) {
        // Maybe it makes sense to throw exception
        LOG.failedToDestroyRewriteStepProcessor( e );
      }
    }
  }

}
