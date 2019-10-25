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
package org.apache.knox.gateway.filter.rewrite.spi;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFlowDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteStepDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteStepDescriptorFactory;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteStepFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public abstract class UrlRewriteFlowDescriptorBase<T> extends UrlRewriteStepDescriptorBase<T> implements
    UrlRewriteFlowDescriptor<T> {

  private UrlRewriteStepFlow flow;
  private List<UrlRewriteStepDescriptor> steps = new ArrayList<>();

  public UrlRewriteFlowDescriptorBase( String type ) {
    super( type );
  }

  @Override
  public UrlRewriteStepFlow flow() {
    return flow;
  }

  @SuppressWarnings( "unchecked" )
  @Override
  public <F extends UrlRewriteFlowDescriptor<?>> F flow( String flow ) {
    setFlow( flow );
    return (F)this;
  }

  @SuppressWarnings( "unchecked" )
  @Override
  public <F extends UrlRewriteFlowDescriptor<?>> F flow( UrlRewriteStepFlow flow ) {
    setFlow( flow );
    return (F)this;
  }

  public void setFlow( UrlRewriteStepFlow flow ) {
    this.flow = flow;
  }

  public void setFlow( String flow ) {
    flow = flow.trim().toUpperCase(Locale.ROOT);
    this.flow = Enum.valueOf( UrlRewriteStepFlow.class, flow );
  }

  public String getFlow() {
    String str = null;
    if( flow != null ) {
      str = flow.toString();
    }
    return str;
  }

  @Override
  public List<UrlRewriteStepDescriptor> steps() {
    return steps;
  }

//  @Override
//  public UrlRewriteMatchDescriptor addMatch() {
//    UrlRewriteMatchDescriptor step = new UrlRewriteMatchDescriptorExt();
//    steps.add( step );
//    return step;
//  }
//
//  @Override
//  public UrlRewriteCheckDescriptor addCheck() {
//    UrlRewriteCheckDescriptor step = new UrlRewriteCheckDescriptorExt();
//    steps.add( step );
//    return step;
//  }
//
//  @Override
//  public UrlRewriteControlDescriptor addControl() {
//    UrlRewriteControlDescriptor step = new UrlRewriteControlDescriptorExt();
//    steps.add( step );
//    return step;
//  }
//
//  @Override
//  public UrlRewriteActionDescriptor addAction() {
//    UrlRewriteActionDescriptor step = new UrlRewriteActionDescriptorBase();
//    steps.add( step );
//    return step;
//  }

  @SuppressWarnings( "unchecked" )
  @Override
  public <T extends UrlRewriteStepDescriptor<?>> T addStep( String type ) {
    T step = UrlRewriteStepDescriptorFactory.create( type );
    steps.add( step );
    return step;
  }

}
