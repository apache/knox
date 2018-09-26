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

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRuleDescriptor;
import org.apache.knox.gateway.filter.rewrite.ext.ScopedMatcher;

import java.util.List;

public class UrlRewriteRuleProcessorHolder extends UrlRewriteStepProcessorHolder {

  private String ruleName;

  private String scope;

  public void initialize( UrlRewriteEnvironment environment, UrlRewriteRuleDescriptor descriptor ) throws Exception {
    super.initialize( environment, descriptor );
    ruleName = descriptor.name();
    //if a scope is set in the rewrite file, use that
    if (descriptor.scope() != null) {
      scope = descriptor.scope();
    } else {
      //by convention the name of the rules start with ROLENAME/servicename/direction
      //use the first part of the name to determine the scope, therefore setting the scope of a rule to
      //be local to that service
      int slashIndex = ruleName.indexOf('/');
      if (slashIndex > 0) {
        scope = ruleName.substring( 0, slashIndex );
      }
      //check config to see if the is an override configuration for a given service to have all its rules set to global
      GatewayConfig gatewayConfig = environment.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
      if (gatewayConfig != null) {
        List<String> globalRulesServices = gatewayConfig.getGlobalRulesServices();
        if ( globalRulesServices.contains(scope) ) {
          scope = ScopedMatcher.GLOBAL_SCOPE;
        }
      }
    }
  }

  public String getRuleName() {
    return ruleName;
  }

  public String getScope() {
    return scope;
  }
}
