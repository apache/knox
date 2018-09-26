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
package org.apache.knox.gateway.filter.rewrite.api;

import java.util.List;

/**
 * <pre>
 * {@code
 * <rules><rule></rule></rules>
 * }
 * </pre>
 */
public interface UrlRewriteRulesDescriptor {

  void addRules( UrlRewriteRulesDescriptor rules );

  List<UrlRewriteFunctionDescriptor> getFunctions();

  <T extends UrlRewriteFunctionDescriptor<?>> T getFunction( String name );

  <T extends UrlRewriteFunctionDescriptor<?>> T addFunction( String name );


  List<UrlRewriteRuleDescriptor> getRules();

  UrlRewriteRuleDescriptor getRule( String name );

  UrlRewriteRuleDescriptor newRule();

  UrlRewriteRuleDescriptor addRule( String name );

  void addRule( UrlRewriteRuleDescriptor rule );


  List<UrlRewriteFilterDescriptor> getFilters();

  UrlRewriteFilterDescriptor getFilter( String name );

  UrlRewriteFilterDescriptor newFilter();

  UrlRewriteFilterDescriptor addFilter( String name );

  void addFilter( UrlRewriteFilterDescriptor filter );

}
