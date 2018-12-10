/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.filter.rewrite.ext;

import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteRuleProcessorHolder;
import org.apache.knox.gateway.util.urltemplate.Matcher;
import org.apache.knox.gateway.util.urltemplate.Template;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple extension to the matcher that takes into account scopes for rules along with the templates themselves.
 * This matcher maintains a list of matchers and delegates to an appropriate matcher based on scope information for the
 * associated rules.
 */
public class ScopedMatcher extends Matcher<UrlRewriteRuleProcessorHolder> {

  public static final String GLOBAL_SCOPE = "GLOBAL";

  private Map<String, Matcher<UrlRewriteRuleProcessorHolder>> matchers;

  public ScopedMatcher() {
    super();
    matchers = new HashMap<>();
  }

  @Override
  public UrlRewriteRuleProcessorHolder get(Template template) {
    return super.get(template);
  }

  @Override
  public void add(Template template, UrlRewriteRuleProcessorHolder value) {
    Matcher<UrlRewriteRuleProcessorHolder> matcher = getMatcher(value);
    matcher.add( template, value );
  }

  @Override
  public Match match(Template input) {
    return match(input, null);
  }

  public Match match(Template input, String scope) {
    List<Match> matches = new ArrayList<>();
    for (Matcher<UrlRewriteRuleProcessorHolder> matcher : matchers.values()) {
      Match match = matcher.match(input);
      if (match != null) {
        matches.add(match);
      }
    }
    if (matches.isEmpty()) {
      return null;
    }
    if (matches.size() == 1) {
      return getMatch(matches, scope);
    }
    return findBestMatch(matches, scope);
  }

  private Match findBestMatch(List<Match> matches, String scope) {
    if (scope != null) {
      //when multiple matches are found, find the first one that matches in scope
      for ( Match match : matches ) {
        String matchedScope = match.getValue().getScope();
        if ( matchedScope != null && matchedScope.equals(scope) ) {
          return match;
        }
      }
    }
    //since no scope match was found return the first global scopeed match
    for ( Match match : matches ) {
      String matchedScope = match.getValue().getScope();
      if ( matchedScope != null && matchedScope.equals(GLOBAL_SCOPE) ) {
        return match;
      }
    }
    //return the first match from the list
    return getMatch(matches, scope);
  }

  private Match getMatch(List<Match> matches, String scope) {
    Match match = matches.get(0);
    String matchedScope = match.getValue().getScope();
    if (matchedScope != null && scope != null && !matchedScope.equals(scope) && !matchedScope.equals(GLOBAL_SCOPE)) {
      return null;
    }
    return match;
  }

  /**
   * Returns a matcher for a given template and processor holder. This method takes into account different scopes in
   * addition to template values. If a matcher exists for a template but the scope is different, a new matcher is
   * created and returned.
   * @param holder the rule holder that goes along with the template.
   * @return a matcher
   */
  private Matcher<UrlRewriteRuleProcessorHolder> getMatcher(UrlRewriteRuleProcessorHolder holder) {
    String scope = holder.getScope();
    if (!matchers.containsKey(scope)) {
      matchers.put(scope, new Matcher<>());
    }

    return matchers.get(scope);
  }
}
