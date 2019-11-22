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
package org.apache.knox.gateway.filter.rewrite.impl.xml;

import org.apache.commons.digester3.Digester;
import org.apache.commons.digester3.Rule;
import org.apache.commons.digester3.SetPropertiesRule;
import org.apache.commons.digester3.binder.AbstractRulesModule;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterContentDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterGroupDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterPathDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFlowDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFunctionDescriptorFactory;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRuleDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteStepDescriptorFactory;
import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteFilterApplyDescriptorImpl;
import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteFilterBufferDescriptorImpl;
import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteFilterDetectDescriptorImpl;
import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteFilterScopeDescriptorImpl;
import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteRulesDescriptorImpl;
import org.xml.sax.Attributes;

public class XmlRewriteRulesDigester extends AbstractRulesModule implements XmlRewriteRulesTags {

  @Override
  protected void configure() {
    forPattern( ROOT ).addRule( new RulesFactory() );
    forPattern( ROOT ).addRule( new SetPropertiesRule() );

    for( String name : UrlRewriteFunctionDescriptorFactory.getNames() ) {
      forPattern( ROOT + "/" + FUNCTIONS + "/" + name ).addRule( new FunctionFactory() );
      forPattern( ROOT + "/" + FUNCTIONS + "/" + name ).addRule( new SetPropertiesRule() );
    }

    forPattern( ROOT + "/" + RULE ).addRule( new RuleFactory() );
    forPattern( ROOT + "/" + RULE ).addRule( new SetPropertiesRule() );
    for( String type : UrlRewriteStepDescriptorFactory.getTypes() ) {
      forPattern( "*/" + type ).addRule( new StepFactory() );
      forPattern( "*/" + type ).addRule( new SetPropertiesRule() );
    }

    forPattern( ROOT + "/" + FILTER ).addRule( new FilterFactory() );
    forPattern( ROOT + "/" + FILTER ).addRule( new SetPropertiesRule() );
    forPattern( ROOT + "/" + FILTER + "/" + CONTENT ).addRule( new FilterContentFactory() );
    forPattern( ROOT + "/" + FILTER + "/" + CONTENT ).addRule( new SetPropertiesRule() );

    forPattern( ROOT + "/" + FILTER + "/" + CONTENT + "/*/" + APPLY ).addRule( new FilterApplyFactory() );
    forPattern( ROOT + "/" + FILTER + "/" + CONTENT + "/*/" + APPLY ).addRule( new SetPropertiesRule() );

    forPattern( ROOT + "/" + FILTER + "/" + CONTENT + "/" + SCOPE ).addRule( new FilterScopeFactory() );
    forPattern( ROOT + "/" + FILTER + "/" + CONTENT + "/" + SCOPE ).addRule( new SetPropertiesRule() );

    forPattern( ROOT + "/" + FILTER + "/" + CONTENT + "/" + BUFFER ).addRule( new FilterBufferFactory() );
    forPattern( ROOT + "/" + FILTER + "/" + CONTENT + "/" + BUFFER ).addRule( new SetPropertiesRule() );

    forPattern( ROOT + "/" + FILTER + "/" + CONTENT + "/" + BUFFER + "/" + DETECT ).addRule( new FilterDetectFactory() );
    forPattern( ROOT + "/" + FILTER + "/" + CONTENT + "/" + BUFFER + "/" + DETECT ).addRule( new SetPropertiesRule() );

//    forPattern( "*/" + MATCH ).addRule( new MatchFactory() );
//    forPattern( "*/" + MATCH ).addRule( new SetPropertiesRule() );
//    forPattern( "*/" + CHECK ).addRule( new CheckFactory() );
//    forPattern( "*/" + CHECK ).addRule( new SetPropertiesRule() );
//    forPattern( "*/" + CONTROL ).addRule( new ControlFactory() );
//    forPattern( "*/" + CONTROL ).addRule( new SetPropertiesRule() );
//    forPattern( "*/" + ACTION ).addRule( new ActionFactory() );
//    forPattern( "*/" + ACTION ).addRule( new SetPropertiesRule() );
  }

  private static class RulesFactory extends FactoryRule {
    @Override
    public Object create( String namespace, String name, Attributes attributes ) {
      return new UrlRewriteRulesDescriptorImpl();
    }
  }

  private static class RuleFactory extends Rule {
    @Override
    public void begin( String namespace, String name, Attributes attributes ) throws Exception {
      Digester digester = getDigester();
      UrlRewriteRulesDescriptor rules = digester.peek();
      UrlRewriteRuleDescriptor rule = rules.newRule();
      getDigester().push( rule );
    }

    @Override
    public void end( String namespace, String name ) throws Exception {
      Digester digester = getDigester();
      UrlRewriteRuleDescriptor rule = digester.pop();
      UrlRewriteRulesDescriptor rules = digester.peek();
      rules.addRule( rule );
    }
  }

  private static class StepFactory extends FactoryRule {
    @Override
    public Object create( String namespace, String name, Attributes attributes ) {
      UrlRewriteFlowDescriptor flow = getDigester().peek();
      return flow.addStep( name );
    }
  }

  private static class FunctionFactory extends FactoryRule {
    @Override
    public Object create( String namespace, String name, Attributes attributes ) {
      UrlRewriteRulesDescriptor rules = getDigester().peek();
      return rules.addFunction( name );
    }
  }

  private static class FilterFactory extends FactoryRule {
    @Override
    public Object create( String namespace, String name, Attributes attributes ) {
      UrlRewriteRulesDescriptor parent = getDigester().peek();
      return parent.addFilter( attributes.getValue( "name" ) );
    }
  }

  private static class FilterContentFactory extends FactoryRule {
    @Override
    public Object create( String namespace, String name, Attributes attributes ) {
      UrlRewriteFilterDescriptor parent = getDigester().peek();
      UrlRewriteFilterContentDescriptor descriptor = parent.addContent( attributes.getValue( "type" ) );
      if (attributes.getValue( "asType" ) != null) {
        descriptor = descriptor.asType(attributes.getValue( "asType" ));
      }
      return descriptor;
    }
  }

  private static class FilterApplyFactory extends FactoryRule {
    @Override
    public Object create( String namespace, String name, Attributes attributes ) {
      UrlRewriteFilterGroupDescriptor parent = getDigester().peek();
      UrlRewriteFilterPathDescriptor child = new UrlRewriteFilterApplyDescriptorImpl();
      child.path( attributes.getValue( "path" ) );
      parent.addSelector( child );
      return child;
    }
  }

  private static class FilterScopeFactory extends FactoryRule {
    @Override
    public Object create( String namespace, String name, Attributes attributes ) {
      UrlRewriteFilterGroupDescriptor parent = getDigester().peek();
      UrlRewriteFilterPathDescriptor child = new UrlRewriteFilterScopeDescriptorImpl();
      child.path( attributes.getValue( "path" ) );
      parent.addSelector( child );
      return child;
    }
  }

  private static class FilterBufferFactory extends FactoryRule {
    @Override
    public Object create( String namespace, String name, Attributes attributes ) {
      UrlRewriteFilterGroupDescriptor parent = getDigester().peek();
      UrlRewriteFilterPathDescriptor child = new UrlRewriteFilterBufferDescriptorImpl();
      child.path( attributes.getValue( "path" ) );
      parent.addSelector( child );
      return child;
    }
  }

  private static class FilterDetectFactory extends FactoryRule {
    @Override
    public Object create( String namespace, String name, Attributes attributes ) {
      UrlRewriteFilterGroupDescriptor parent = getDigester().peek();
      UrlRewriteFilterPathDescriptor child = new UrlRewriteFilterDetectDescriptorImpl();
      child.path( attributes.getValue( "path" ) );
      parent.addSelector( child );
      return child;
    }
  }

//  private static class MatchFactory extends FactoryRule {
//    @Override
//    public Object create( String namespace, String name, Attributes attributes ) {
//      UrlRewriteRuleDescriptor rule = getDigester().peek();
//      return rule.addMatch();
//    }
//  }
//
//  private static class CheckFactory extends FactoryRule {
//    @Override
//    public Object create( String namespace, String name, Attributes attributes ) {
//      UrlRewriteRuleDescriptor rule = getDigester().peek();
//      return rule.addCheck();
//    }
//  }
//
//  private static class ActionFactory extends FactoryRule {
//    @Override
//    public Object create( String namespace, String name, Attributes attributes ) {
//      UrlRewriteRuleDescriptor rule = getDigester().peek();
//      return rule.addAction();
//    }
//  }
//
//  private static class ControlFactory extends FactoryRule {
//    @Override
//    public Object create( String namespace, String name, Attributes attributes ) {
//      UrlRewriteRuleDescriptor rule = getDigester().peek();
//      return rule.addControl();
//    }
//  }

  private abstract static class FactoryRule extends Rule {
    protected abstract Object create( String namespace, String name, Attributes attributes );

    @Override
    public void begin( String namespace, String name, Attributes attributes ) throws Exception {
      getDigester().push( create( namespace, name, attributes ) );
    }

    @Override
    public void end( String namespace, String name ) throws Exception {
      getDigester().pop();
    }
  }
}
