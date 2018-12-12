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

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRuleDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptorFactory;
import org.apache.knox.gateway.filter.rewrite.ext.UrlRewriteActionDescriptor;
import org.apache.knox.gateway.filter.rewrite.ext.UrlRewriteActionRewriteDescriptorExt;
import org.apache.knox.gateway.filter.rewrite.ext.UrlRewriteCheckDescriptor;
import org.apache.knox.gateway.filter.rewrite.ext.UrlRewriteControlDescriptor;
import org.apache.knox.gateway.filter.rewrite.ext.UrlRewriteMatchDescriptor;
import org.junit.Assert;
import org.junit.Test;
import org.xmlmatchers.XmlMatchers;
import org.xmlmatchers.transform.XmlConverters;

import javax.xml.transform.Source;
import java.io.IOException;
import java.io.StringWriter;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class XmlUrlRewriteRulesExporterTest {

  @Test
  public void testEmptyRules() throws IOException {
    UrlRewriteRulesDescriptor rules = UrlRewriteRulesDescriptorFactory.create();
    StringWriter writer = new StringWriter();
    UrlRewriteRulesDescriptorFactory.store( rules, "xml", writer );

    String xml = writer.toString();
    assertThat( XmlConverters.the( xml ), XmlMatchers.hasXPath( "/rules" ) );
  }

  @Test
  public void testSingleNamedRule() throws IOException {
    UrlRewriteRulesDescriptor rules = UrlRewriteRulesDescriptorFactory.create();
    rules.addRule( "first" ).scope( "test-scope" );

    StringWriter writer = new StringWriter();
    UrlRewriteRulesDescriptorFactory.store( rules, "xml", writer );

    String str = writer.toString();
    Source xml = XmlConverters.the( str );
    assertThat( xml, XmlMatchers.hasXPath( "/rules" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule" ) );
    assertThat( xml, XmlMatchers.hasXPath( "count(/rules/rule)", is( "1" ) ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/@name", is( "first" ) ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/@scope", is( "test-scope" ) ) );
  }

  @Test
  public void testMatchStep() throws Exception {
    UrlRewriteRulesDescriptor rules = UrlRewriteRulesDescriptorFactory.create();
    UrlRewriteRuleDescriptor rule = rules.addRule( "test-rule" ).pattern("test-pattern-rule");
    UrlRewriteMatchDescriptor match = rule.addStep( "match" );
    match.operation("test-operation").pattern( "test-pattern-step" ).flow( "all" );

    StringWriter writer = new StringWriter();
    UrlRewriteRulesDescriptorFactory.store( rules, "xml", writer );

    String str = writer.toString();
    Source xml = XmlConverters.the( str );
    assertThat( xml, XmlMatchers.hasXPath( "/rules" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule" ) );
    assertThat( xml, XmlMatchers.hasXPath( "count(/rules/rule)", is( "1" ) ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/@name", is( "test-rule" ) ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/@pattern", is( "test-pattern-rule" ) ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/match" ) );
    assertThat( xml, XmlMatchers.hasXPath( "count(/rules/rule/match)", is( "1" ) ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/match/@flow", is( "ALL" ) ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/match/@oper", is( "test-operation" ) ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/match/@pattern", is( "test-pattern-step" ) ) );
  }

  @Test
  public void testControlStep() throws Exception {
    UrlRewriteRulesDescriptor rules = UrlRewriteRulesDescriptorFactory.create();
    UrlRewriteRuleDescriptor rule = rules.addRule( "test-rule" );
    UrlRewriteControlDescriptor control = rule.addStep( "control" );
    control.flow( "or" );

    StringWriter writer = new StringWriter();
    UrlRewriteRulesDescriptorFactory.store( rules, "xml", writer );

    String str = writer.toString();
    Source xml = XmlConverters.the( str );
    assertThat( xml, XmlMatchers.hasXPath( "/rules" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule" ) );
    assertThat( xml, XmlMatchers.hasXPath( "count(/rules/rule)", is( "1" ) ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/@name", is( "test-rule" ) ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/control" ) );
    assertThat( xml, XmlMatchers.hasXPath( "count(/rules/rule/control)", is( "1" ) ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/control/@flow", is( "OR" ) ) );
  }

  @Test
  public void testCheckStep() throws Exception {
    UrlRewriteRulesDescriptor rules = UrlRewriteRulesDescriptorFactory.create();
    UrlRewriteRuleDescriptor rule = rules.addRule( "test-rule" );
    UrlRewriteCheckDescriptor step = rule.addStep( "check" );
    step.operation("test-operation").input("test-input").value("test-value").flow( "all" );

    StringWriter writer = new StringWriter();
    UrlRewriteRulesDescriptorFactory.store( rules, "xml", writer );

    String str = writer.toString();
    Source xml = XmlConverters.the( str );
    assertThat( xml, XmlMatchers.hasXPath( "/rules" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule" ) );
    assertThat( xml, XmlMatchers.hasXPath( "count(/rules/rule)", is( "1" ) ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/@name", is( "test-rule" ) ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/check" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/check/@oper", is("test-operation") ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/check/@input", is("test-input") ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/check/@value", is("test-value") ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/check/@flow", is("ALL") ) );
  }

  @Test
  public void testRewriteStep() throws Exception {
    UrlRewriteRulesDescriptor rules = UrlRewriteRulesDescriptorFactory.create();
    UrlRewriteRuleDescriptor rule = rules.addRule( "test-rule" );
    UrlRewriteActionRewriteDescriptorExt step = rule.addStep( "rewrite" );
    step.operation("test-operation").parameter( "test-param" );

    StringWriter writer = new StringWriter();
    UrlRewriteRulesDescriptorFactory.store( rules, "xml", writer );

    String str = writer.toString();
    Source xml = XmlConverters.the( str );
    assertThat( xml, XmlMatchers.hasXPath( "/rules" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule" ) );
    assertThat( xml, XmlMatchers.hasXPath( "count(/rules/rule)", is( "1" ) ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/@name", is( "test-rule" ) ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/rewrite" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/rewrite/@oper", is("test-operation") ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/rewrite/@template", is("test-param") ) );
  }

  @Test
  public void testNestedStep() throws Exception {
    UrlRewriteRulesDescriptor rules = UrlRewriteRulesDescriptorFactory.create();
    UrlRewriteRuleDescriptor rule = rules.addRule( "test-rule" );

    UrlRewriteMatchDescriptor match = rule.addStep( "match" );
    UrlRewriteMatchDescriptor matchMatch = match.addStep( "match" );
    UrlRewriteCheckDescriptor matchCheck = match.addStep( "check" );
    UrlRewriteControlDescriptor matchControl = match.addStep( "control" );
    UrlRewriteActionDescriptor matchRewrite = match.addStep( "rewrite" );
    Assert.assertNotNull(matchMatch);
    Assert.assertNotNull(matchCheck);
    Assert.assertNotNull(matchControl);
    Assert.assertNotNull(matchRewrite);

    UrlRewriteCheckDescriptor check = rule.addStep( "check" );
    UrlRewriteMatchDescriptor checkMatch = check.addStep( "match" );
    UrlRewriteCheckDescriptor checkCheck = check.addStep( "check" );
    UrlRewriteControlDescriptor checkControl = check.addStep( "control" );
    UrlRewriteActionDescriptor checkRewrite = check.addStep( "rewrite" );
    Assert.assertNotNull(checkMatch);
    Assert.assertNotNull(checkCheck);
    Assert.assertNotNull(checkControl);
    Assert.assertNotNull(checkRewrite);

    UrlRewriteControlDescriptor control = rule.addStep( "control" );
    UrlRewriteMatchDescriptor controlMatch = control.addStep( "match" );
    UrlRewriteCheckDescriptor controlCheck = control.addStep( "check" );
    UrlRewriteControlDescriptor controlControl = control.addStep( "control" );
    UrlRewriteActionDescriptor controlRewrite = control.addStep( "rewrite" );
    Assert.assertNotNull(controlMatch);
    Assert.assertNotNull(controlCheck);
    Assert.assertNotNull(controlControl);
    Assert.assertNotNull(controlRewrite);

    UrlRewriteActionDescriptor rewrite = rule.addStep( "rewrite" );
    Assert.assertNotNull(rewrite);

    StringWriter writer = new StringWriter();
    UrlRewriteRulesDescriptorFactory.store( rules, "xml", writer );

    String str = writer.toString();
    Source xml = XmlConverters.the( str );
    assertThat( xml, XmlMatchers.hasXPath( "/rules" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule" ) );
    assertThat( xml, XmlMatchers.hasXPath( "count(/rules/rule)", is( "1" ) ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/@name", is( "test-rule" ) ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/match" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/match/match" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/match/check" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/match/control" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/match/rewrite" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/check" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/check/match" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/check/check" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/check/control" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/check/rewrite" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/control" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/control/match" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/control/check" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/control/control" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/control/rewrite" ) );
    assertThat( xml, XmlMatchers.hasXPath( "/rules/rule/rewrite" ) );
  }
}
