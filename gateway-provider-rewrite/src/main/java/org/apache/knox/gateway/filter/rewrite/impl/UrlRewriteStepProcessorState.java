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

import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteStepStatus;

import java.util.Iterator;

class UrlRewriteStepProcessorState {

  private UrlRewriteStepStatus status;
  private UrlRewriteStepProcessorHolder next;
  private Iterator<UrlRewriteStepProcessorHolder> steps;

  UrlRewriteStepProcessorState( Iterator<UrlRewriteStepProcessorHolder> steps ) {
    this.status = UrlRewriteStepStatus.SUCCESS;
    this.next = null;
    this.steps = steps;
  }

  private UrlRewriteStepProcessorHolder peek() {
    if( next == null && steps.hasNext() ) {
      next = steps.next();
      return next;
    } else if ( next != null ) {
      return next;
    } else {
      return null;
    }
  }

  public boolean hasNextCondition() {
    UrlRewriteStepProcessorHolder curr = peek();
    return curr != null && curr.isCondition();
  }

  public boolean hasNextAction() {
    UrlRewriteStepProcessorHolder curr = peek();
    return curr != null && curr.isAction();
  }

  private UrlRewriteStepProcessorHolder take( UrlRewriteStepStatus lastStatus ) {
    UrlRewriteStepProcessorHolder step = peek();
    status = lastStatus;
    next = null;
    return step;
  }

  public UrlRewriteStepProcessorHolder nextCondition( UrlRewriteStepStatus lastStatus ){
    if( hasNextCondition() ) {
      return take( lastStatus );
    } else {
      return null;
    }
  }

  public UrlRewriteStepProcessorHolder nextAction( UrlRewriteStepStatus lastStatus ){
    if( hasNextAction() ) {
      return take( lastStatus );
    } else {
      return null;
    }
  }

  public UrlRewriteStepStatus status(){
    return status;
  }

  public boolean hasNext() {
      return next != null || steps.hasNext();
  }

}
