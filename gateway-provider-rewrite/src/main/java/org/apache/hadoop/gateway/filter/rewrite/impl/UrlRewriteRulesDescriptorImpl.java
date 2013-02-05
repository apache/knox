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

import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRuleDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UrlRewriteRulesDescriptorImpl implements UrlRewriteRulesDescriptor {

  private List<UrlRewriteRuleDescriptor> ruleList = new ArrayList<UrlRewriteRuleDescriptor>();
  private Map<String,UrlRewriteRuleDescriptor> ruleMap = new HashMap<String,UrlRewriteRuleDescriptor>();

  @Override
  public UrlRewriteRuleDescriptor rule( String name ) {
    return ruleMap.get( name );
  }

  @Override
  public List<UrlRewriteRuleDescriptor> rules() {
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
    if( name != null && name.length() > 0 ) {
      ruleMap.put( rule.name(), rule );
    }
  }

}
