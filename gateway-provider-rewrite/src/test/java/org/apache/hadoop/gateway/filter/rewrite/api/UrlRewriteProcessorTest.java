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
package org.apache.hadoop.gateway.filter.rewrite.api;

import org.apache.hadoop.gateway.util.urltemplate.Parser;
import org.apache.hadoop.gateway.util.urltemplate.Template;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class UrlRewriteProcessorTest {

  private static URL getTestResourceUrl( String name ) throws FileNotFoundException {
    name = UrlRewriteProcessorTest.class.getName().replaceAll( "\\.", "/" ) + "/" + name;
    URL url = ClassLoader.getSystemResource( name );
    if( url == null ) {
      throw new FileNotFoundException( name );
    }
    return url;
  }

  private static InputStream getTestResourceStream( String name ) throws IOException {
    URL url = getTestResourceUrl( name );
    InputStream stream = url.openStream();
    return stream;
  }

  private static Reader getTestResourceReader( String name, String charset ) throws IOException {
    return new InputStreamReader( getTestResourceStream( name ), charset );
  }

  @Test
  public void testBasicPathRewrite() throws IOException, URISyntaxException {
    UrlRewriteEnvironment environment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( environment, request, response );

    UrlRewriteProcessor processor = new UrlRewriteProcessor();
    UrlRewriteRulesDescriptor config = UrlRewriteRulesDescriptorFactory.load(
        "xml", getTestResourceReader( "rewrite.xml", "UTF-8" ) );
    processor.initialize( environment, config );

    Template inputUrl = Parser.parseLiteral( "test-scheme://test-host:1/test-input-path" );
    Template outputUrl = processor.rewrite( null, inputUrl, UrlRewriter.Direction.IN, null );

    assertThat( "Expect rewrite to produce a new URL",
        outputUrl, notNullValue() );
    assertThat(
        "Expect rewrite to contain the correct path.",
        outputUrl.toString(), is( "test-scheme://test-host:1/test-output-path" ) );
    processor.destroy();
  }

  @Test
  public void testMultipleIdenticalRewriteOutputRules() throws IOException, URISyntaxException {
    UrlRewriteEnvironment environment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( environment, request, response );

    UrlRewriteProcessor processor = new UrlRewriteProcessor();
    UrlRewriteRulesDescriptor config = UrlRewriteRulesDescriptorFactory.load(
        "xml", getTestResourceReader( "rewrite-with-same-rules.xml", "UTF-8" ) );
    processor.initialize( environment, config );

    Template inputUrl = Parser.parseLiteral( "scheme://input-mock-host:42/test-input-path" );
    Template outputUrl = processor.rewrite( null, inputUrl, UrlRewriter.Direction.OUT, null );

    assertThat( "Expect rewrite to produce a new URL",
        outputUrl, notNullValue() );
    // Should always pick the first one.
    assertThat(
        "Expect rewrite to contain the correct path.",
        outputUrl.toString(), is( "output-mock-scheme-1://output-mock-host-1:42/test-input-path" ) );

    inputUrl = Parser.parseLiteral( "mock-scheme://input-mock-host:42/no-query" );
    outputUrl = processor.rewrite( null, inputUrl, UrlRewriter.Direction.OUT, null );

    assertThat(
        "Expect rewrite to contain the correct path.",
        outputUrl.toString(), is( "mock-scheme://output-mock-host-3:42/no-query" ) );

    outputUrl = processor.rewrite( null, inputUrl, UrlRewriter.Direction.OUT, "test-rule-4" );

    assertThat(
        "Expect rewrite to contain the correct path.",
        outputUrl.toString(), is( "mock-scheme://output-mock-host-4:42/no-query" ) );

    processor.destroy();
  }

  @Test
  public void testRewriteViaRuleNameWithAmbiguousRules() throws IOException, URISyntaxException {
    UrlRewriteEnvironment environment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( environment, request, response );

    UrlRewriteProcessor processor = new UrlRewriteProcessor();
    UrlRewriteRulesDescriptor config = UrlRewriteRulesDescriptorFactory.load(
        "xml", getTestResourceReader( "rewrite-with-same-rules.xml", "UTF-8" ) );
    processor.initialize( environment, config );

    Template inputUrl = Parser.parseLiteral( "input-mock-scheme-1://input-mock-host-1:42/test-input-path" );
    Template outputUrl = processor.rewrite( null, inputUrl, UrlRewriter.Direction.OUT, "test-rule-2" );

    assertThat( "Expect rewrite to produce a new URL",
        outputUrl, notNullValue() );
    assertThat(
        "Expect rewrite to contain the correct path.",
        outputUrl.toString(), is( "output-mock-scheme-2://output-mock-host-2:42/test-input-path" ) );

    outputUrl = processor.rewrite( null, inputUrl, UrlRewriter.Direction.OUT, "test-rule-1" );

    assertThat( "Expect rewrite to produce a new URL",
        outputUrl, notNullValue() );
    assertThat(
        "Expect rewrite to contain the correct path.",
        outputUrl.toString(), is( "output-mock-scheme-1://output-mock-host-1:42/test-input-path" ) );

    processor.destroy();
  }

  @Test
  public void testRewriteViaRuleWithComplexFlow() throws Exception {
    UrlRewriteEnvironment environment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( environment, request, response );

    UrlRewriteProcessor processor = new UrlRewriteProcessor();
    UrlRewriteRulesDescriptor config = UrlRewriteRulesDescriptorFactory.load(
        "xml", getTestResourceReader( "rewrite.xml", "UTF-8" ) );
    processor.initialize( environment, config );

    Template inputUrl;
    Template outputUrl;

    inputUrl = Parser.parseLiteral( "test-scheme://test-host:777/test-path" );
    outputUrl = processor.rewrite( null, inputUrl, UrlRewriter.Direction.IN, "test-rule-with-complex-flow" );
    assertThat(
        "Expect rewrite to contain the correct path.",
        outputUrl.toString(), is( "test-scheme-output://test-host-output:42/test-path-output/test-path" ) );

    inputUrl = Parser.parseLiteral( "test-scheme://test-host:42/~/test-path" );
    outputUrl = processor.rewrite( null, inputUrl, UrlRewriter.Direction.IN, "test-rule-with-complex-flow" );
    assertThat(
        "Expect rewrite to contain the correct path.",
        outputUrl.toString(), is( "test-scheme-output://test-host-output:777/test-path-output/test-home/test-path" ) );

    processor.destroy();
  }

  @Test
  public void testRewriteViaRuleWithWildcardTemplateAndOptionalQuery() throws Exception {
    UrlRewriteEnvironment environment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( environment, request, response );

    UrlRewriteProcessor processor = new UrlRewriteProcessor();
    UrlRewriteRulesDescriptor config = UrlRewriteRulesDescriptorFactory.load(
        "xml", getTestResourceReader( "rewrite.xml", "UTF-8" ) );
    processor.initialize( environment, config );

    Template inputUrl;
    Template outputUrl;

    inputUrl = Parser.parseLiteral( "test-scheme-input://test-host-input:42/test-path-input-one/test-path-input-two?test-query-name=test-query-value" );
    outputUrl = processor.rewrite( null, inputUrl, UrlRewriter.Direction.OUT, "test-rule-2" );

    assertThat(
        "Expect rewrite to contain the correct path and query.",
        outputUrl.toString(), is( "test-scheme-output://test-host-output:777/test-path-output/test-path-input-one/test-path-input-two?test-query-name=test-query-value" ) );

    inputUrl = Parser.parseLiteral( "test-scheme-input://test-host-input:42/test-path-input-one/test-path-input-two" );
    outputUrl = processor.rewrite( null, inputUrl, UrlRewriter.Direction.OUT, "test-rule-2" );

    assertThat(
        "Expect rewrite to contain the correct path.",
        outputUrl.toString(), is( "test-scheme-output://test-host-output:777/test-path-output/test-path-input-one/test-path-input-two" ) );

    processor.destroy();
  }

}
