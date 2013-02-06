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
package org.apache.hadoop.gateway.filter.rewrite.impl;

import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteStepDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteStepFlow;
import org.apache.hadoop.gateway.filter.rewrite.ext.UrlRewriteFlowDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteStepProcessor;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteStepStatus;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class UrlRewriteStepProcessorHolder implements UrlRewriteStepProcessor {

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
  public void initialize( UrlRewriteStepDescriptor descriptor ) throws Exception {
    this.descriptor = descriptor;
    this.isCondition = descriptor instanceof UrlRewriteFlowDescriptor;
    if( descriptor instanceof UrlRewriteFlowDescriptor ) {
      this.childProcessors = new ArrayList<UrlRewriteStepProcessorHolder>();
      UrlRewriteFlowDescriptor flowDescriptor = (UrlRewriteFlowDescriptor)descriptor;
      Iterator<UrlRewriteStepDescriptor<?>> stepDescriptors = flowDescriptor.steps().iterator();
      while( stepDescriptors.hasNext() ) {
        UrlRewriteStepDescriptor stepDescriptor = stepDescriptors.next();
        UrlRewriteStepProcessorHolder stepProcessor = new UrlRewriteStepProcessorHolder();
        stepProcessor.initialize( stepDescriptor );
        childProcessors.add( stepProcessor );
      }
    }
    this.processor = UrlRewriteStepProcessorFactory.create( descriptor );
    initializeUnchecked( this.processor, descriptor );
  }

  @SuppressWarnings("unchecked")
  private static void initializeUnchecked( UrlRewriteStepProcessor processor, UrlRewriteStepDescriptor descriptor )
      throws Exception {
    processor.initialize( descriptor );
  }

  @Override
  public UrlRewriteStepStatus process( UrlRewriteContext context ) throws Exception {
    UrlRewriteStepStatus status = UrlRewriteStepStatus.SUCCESS;
    // If initialization failed then fail processing
    if( processor != null ) {
      status = processor.process( context );
      if( UrlRewriteStepStatus.SUCCESS == status && descriptor instanceof UrlRewriteFlowDescriptor ) {
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
        //TODO: Log stack properly.
        e.printStackTrace();
      }
    }
  }

}
