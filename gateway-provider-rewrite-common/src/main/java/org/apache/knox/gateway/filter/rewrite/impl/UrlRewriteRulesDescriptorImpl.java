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

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFunctionDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFunctionDescriptorFactory;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRuleDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UrlRewriteRulesDescriptorImpl implements UrlRewriteRulesDescriptor {

  private Map<String,UrlRewriteFunctionDescriptor> funcMap = new HashMap<>();
  private List<UrlRewriteFunctionDescriptor> funcList = new ArrayList<>();
  private List<UrlRewriteRuleDescriptor> ruleList = new ArrayList<>();
  private Map<String,UrlRewriteRuleDescriptor> ruleMap = new HashMap<>();
  private List<UrlRewriteFilterDescriptor> filterList = new ArrayList<>();
  private Map<String,UrlRewriteFilterDescriptor> filterMap = new HashMap<>();

  @Override
  public void addRules( UrlRewriteRulesDescriptor rules ) {
    for( UrlRewriteRuleDescriptor rule : rules.getRules() ) {
      addRule( rule );
    }
    for( UrlRewriteFilterDescriptor filter : rules.getFilters() ) {
      addFilter( filter  );
    }
  }

  @Override
  public UrlRewriteRuleDescriptor getRule( String name ) {
    return ruleMap.get( name );
  }

  @Override
  public List<UrlRewriteRuleDescriptor> getRules() {
    return ruleList;
  }

  @Override
  public UrlRewriteRuleDescriptor addRule( String name ) {
    UrlRewriteRuleDescriptor rule = newRule();
    rule.name( name );
    addRule( rule );
    return rule;
  }

  @Override
  public UrlRewriteRuleDescriptor newRule() {
    return new UrlRewriteRuleDescriptorImpl();
  }

  @Override
  public void addRule( UrlRewriteRuleDescriptor rule ) {
    ruleList.add( rule );
    String name = rule.name();
    if( name != null && !name.isEmpty()) {
      ruleMap.put( rule.name(), rule );
    }
  }

  @Override
  public List<UrlRewriteFunctionDescriptor> getFunctions() {
    return funcList;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends UrlRewriteFunctionDescriptor<?>> T getFunction( String name ) {
    return (T)funcMap.get( name );
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends UrlRewriteFunctionDescriptor<?>> T addFunction( String name ) {
    T descriptor = newFunction( name );
    addFunction( descriptor );
    return descriptor;
  }

  @SuppressWarnings("unchecked")
  protected <T extends UrlRewriteFunctionDescriptor<?>> T newFunction( String name ) {
    return UrlRewriteFunctionDescriptorFactory.create( name );
  }

  protected void addFunction( UrlRewriteFunctionDescriptor descriptor ) {
    funcList.add( descriptor );
    funcMap.put( descriptor.name(), descriptor );
  }


  @Override
  public List<UrlRewriteFilterDescriptor> getFilters() {
    return filterList;
  }

  @Override
  public UrlRewriteFilterDescriptor getFilter( String name ) {
    return filterMap.get( name );
  }

  @Override
  public UrlRewriteFilterDescriptor newFilter() {
    return new UrlRewriteFilterDescriptorImpl();
  }

  @Override
  public UrlRewriteFilterDescriptor addFilter( String name ) {
    UrlRewriteFilterDescriptor filter = newFilter();
    filter.name( name );
    addFilter( filter );
    return filter;
  }

  @Override
  public void addFilter( UrlRewriteFilterDescriptor descriptor ) {
    filterList.add( descriptor );
    filterMap.put( descriptor.name(), descriptor );
  }

}
