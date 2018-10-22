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
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteStepProcessor;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteStepStatus;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class UrlRewriteStepProcessorStateTest {

  @Test
  public void testEmpty() {
    List<UrlRewriteStepProcessorHolder> steps = new ArrayList<>();
    UrlRewriteStepProcessorState state = new UrlRewriteStepProcessorState( steps.iterator() );

    MatcherAssert
        .assertThat( state.status(), CoreMatchers.is( UrlRewriteStepStatus.SUCCESS ) );
    assertThat( state.hasNext(), is( false ) );
    assertThat( state.hasNextAction(), is( false ) );
    assertThat( state.hasNextCondition(), is( false ) );
    assertThat( state.nextAction( UrlRewriteStepStatus.SUCCESS ), nullValue() );
    assertThat( state.nextCondition( UrlRewriteStepStatus.SUCCESS ), nullValue() );
  }

  @Test
  public void testNextAction() throws Exception {
    UrlRewriteStepProcessorHolder holder;
    List<UrlRewriteStepProcessorHolder> steps = new ArrayList<>();
    holder = new UrlRewriteStepProcessorHolder();
    holder.initialize( new FakeEnvironment(), new FakeActionDescriptor( "one" ), new FakeActionProcessor( "one" ) );
    steps.add( holder );
    holder = new UrlRewriteStepProcessorHolder();
    holder.initialize( new FakeEnvironment(), new FakeActionDescriptor( "two" ), new FakeActionProcessor( "two" ) );
    steps.add( holder );
    UrlRewriteStepProcessorState state = new UrlRewriteStepProcessorState( steps.iterator() );
    assertThat( state.hasNext(), is( true ) );
    assertThat( state.hasNextAction(), is( true ) );
    assertThat( state.hasNextCondition(), is( false ) );
    assertThat( state.nextCondition( UrlRewriteStepStatus.SUCCESS ), nullValue() );

    holder = state.nextAction( UrlRewriteStepStatus.SUCCESS );
    assertThat( ((FakeActionDescriptor)holder.getDescriptor()).name, is( "one" ) );

    assertThat( state.hasNext(), is( true ) );
    assertThat( state.hasNextAction(), is( true ) );
    assertThat( state.hasNextCondition(), is( false ) );
    assertThat( state.nextCondition( UrlRewriteStepStatus.SUCCESS ), nullValue() );

    holder = state.nextAction( UrlRewriteStepStatus.SUCCESS );
    assertThat( ((FakeActionDescriptor)holder.getDescriptor()).name, is( "two" ) );

    assertThat( state.hasNext(), is( false ) );
    assertThat( state.hasNextAction(), is( false ) );
    assertThat( state.hasNextCondition(), is( false ) );
    assertThat( state.nextAction( UrlRewriteStepStatus.SUCCESS ), nullValue() );
    assertThat( state.nextCondition( UrlRewriteStepStatus.SUCCESS ), nullValue() );
  }

  @Test
  public void testNextCondition() throws Exception {
    UrlRewriteStepProcessorHolder holder;
    List<UrlRewriteStepProcessorHolder> steps = new ArrayList<>();
    holder = new UrlRewriteStepProcessorHolder();
    holder.initialize( new FakeEnvironment(), new FakeConditionDescriptor( "one" ), new FakeConditionProcessor( "one" ) );
    steps.add( holder );
    holder = new UrlRewriteStepProcessorHolder();
    holder.initialize( new FakeEnvironment(), new FakeConditionDescriptor( "two" ), new FakeConditionProcessor( "two" ) );
    steps.add( holder );
    UrlRewriteStepProcessorState state = new UrlRewriteStepProcessorState( steps.iterator() );
    assertThat( state.hasNext(), is( true ) );
    assertThat( state.hasNextAction(), is( false ) );
    assertThat( state.hasNextCondition(), is( true ) );
    assertThat( state.nextAction( UrlRewriteStepStatus.SUCCESS ), nullValue() );

    holder = state.nextCondition( UrlRewriteStepStatus.SUCCESS );
    assertThat( ((FakeConditionDescriptor)holder.getDescriptor()).name, is( "one" ) );

    assertThat( state.hasNext(), is( true ) );
    assertThat( state.hasNextAction(), is( false ) );
    assertThat( state.hasNextCondition(), is( true ) );
    assertThat( state.nextAction( UrlRewriteStepStatus.SUCCESS ), nullValue() );

    holder = state.nextCondition( UrlRewriteStepStatus.SUCCESS );
    assertThat( ((FakeConditionDescriptor)holder.getDescriptor()).name, is( "two" ) );

    assertThat( state.hasNext(), is( false ) );
    assertThat( state.hasNextAction(), is( false ) );
    assertThat( state.hasNextCondition(), is( false ) );
    assertThat( state.nextAction( UrlRewriteStepStatus.SUCCESS ), nullValue() );
    assertThat( state.nextCondition( UrlRewriteStepStatus.SUCCESS ), nullValue() );
  }

  public class FakeActionDescriptor implements UrlRewriteStepDescriptor<FakeActionDescriptor> {
    public String name;

    public FakeActionDescriptor( String name ) {
      this.name = name;
    }

    @Override
    public String type() {
      return "fake-action";
    }

    @Override
    public FakeActionDescriptor type( String type ) {
      return null;
    }

  }

  public class FakeActionProcessor implements
      UrlRewriteStepProcessor<UrlRewriteStepDescriptor<FakeActionDescriptor>> {
    public String name;

    public FakeActionProcessor( String name ) {
      this.name = name;
    }

    @Override
    public String getType() {
      return "fake-action";
    }

    @Override
    public void initialize( UrlRewriteEnvironment environment, UrlRewriteStepDescriptor<FakeActionDescriptor> descriptor ) throws Exception {
    }

    @Override
    public UrlRewriteStepStatus process( UrlRewriteContext context ) throws Exception {
      return null;
    }

    @Override
    public void destroy() throws Exception {
    }
  }

  public class FakeConditionDescriptor implements UrlRewriteFlowDescriptor<FakeConditionDescriptor> {
    public String name;

    public FakeConditionDescriptor( String name ) {
      this.name = name;
    }

    @Override
    public UrlRewriteStepFlow flow() {
      return null;
    }

    @Override
    public FakeConditionDescriptor flow( String flow ) {
      return null;
    }

    @Override
    public FakeConditionDescriptor flow( UrlRewriteStepFlow flow ) {
      return null;
    }

    @Override
    public List<UrlRewriteStepDescriptor> steps() {
      return null;
    }

    @Override
    public <T extends UrlRewriteStepDescriptor<?>> T addStep( String type ) {
      return null;
    }

    @Override
    public String type() {
      return "fake-condition";
    }

    @Override
    public FakeConditionDescriptor type( String type ) {
      return null;
    }
  }

  public class FakeConditionProcessor implements UrlRewriteStepProcessor<FakeConditionDescriptor> {
    public String name;

    public FakeConditionProcessor( String name ) {
      this.name = name;
    }

    @Override
    public String getType() {
      return "fake-condition";
    }

    @Override
    public void initialize( UrlRewriteEnvironment environment, FakeConditionDescriptor descriptor ) throws Exception {
    }

    @Override
    public UrlRewriteStepStatus process( UrlRewriteContext context ) throws Exception {
      return null;
    }

    @Override
    public void destroy() throws Exception {
    }
  }

  private class FakeEnvironment implements UrlRewriteEnvironment {
    @Override
    public URL getResource( String name ) throws IOException {
      return null;
    }

    @Override
    public <T> T getAttribute( String name ) {
      return null;
    }

    @Override
    public List<String> resolve( String name ) {
      return null;
    }
  }
}
