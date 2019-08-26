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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.knox.gateway.util.urltemplate.Expander;
import org.apache.knox.gateway.util.urltemplate.Matcher;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Template;
import org.easymock.EasyMock;
import org.junit.Test;

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
    return url.openStream();
  }

  private static Reader getTestResourceReader( String name ) throws IOException {
    return new InputStreamReader( getTestResourceStream( name ), StandardCharsets.UTF_8 );
  }

  @Test
  public void testBasicPathRewrite() throws IOException, URISyntaxException {
    UrlRewriteEnvironment environment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( environment, request, response );

    UrlRewriteProcessor processor = new UrlRewriteProcessor();
    UrlRewriteRulesDescriptor config = UrlRewriteRulesDescriptorFactory.load(
        "xml", getTestResourceReader( "rewrite.xml" ) );
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
        "xml", getTestResourceReader( "rewrite-with-same-rules.xml" ) );
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
  public void testIdenticalRewriteOutputRulesWithScopes() throws IOException, URISyntaxException {
    UrlRewriteEnvironment environment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    ArrayList<String> roles = new ArrayList<>();
    roles.add("service-1");
    EasyMock.expect(environment.resolve("service.role")).andReturn(roles).anyTimes();
    EasyMock.replay( environment, request, response );

    UrlRewriteProcessor processor = new UrlRewriteProcessor();
    UrlRewriteRulesDescriptor config = UrlRewriteRulesDescriptorFactory.load(
        "xml", getTestResourceReader( "rewrite-with-same-rules-different-scope.xml" ) );
    processor.initialize( environment, config );

    Template inputUrl = Parser.parseLiteral( "scheme://input-mock-host:42/test-input-path" );
    Template outputUrl = processor.rewrite( environment, inputUrl, UrlRewriter.Direction.OUT, null );

    assertThat( "Expect rewrite to produce a new URL",
        outputUrl, notNullValue() );
    assertThat(
        "Expect rewrite to contain the correct path.",
        outputUrl.toString(), is( "output-mock-scheme-2://output-mock-host-2:42/test-input-path" ) );

    inputUrl = Parser.parseLiteral( "mock-scheme://input-mock-host:42/no-query" );
    outputUrl = processor.rewrite( environment, inputUrl, UrlRewriter.Direction.OUT, null );

    roles.remove(0);
    roles.add("service-2");

    assertThat(
        "Expect rewrite to contain the correct path.",
        outputUrl.toString(), is( "mock-scheme://output-mock-host-5:42/no-query" ) );

    outputUrl = processor.rewrite( environment, inputUrl, UrlRewriter.Direction.OUT, "service-2/test-rule-4" );

    //no scope information should pick the first one
    assertThat(
        "Expect rewrite to contain the correct path.",
        outputUrl.toString(), is( "mock-scheme://output-mock-host-4:42/no-query" ) );

    outputUrl = processor.rewrite( null, inputUrl, UrlRewriter.Direction.OUT, "service-2/test-rule-4" );

    assertThat(
        "Expect rewrite to contain the correct path.",
        outputUrl.toString(), is( "mock-scheme://output-mock-host-4:42/no-query" ) );

    //Test the IN direction
    inputUrl = Parser.parseLiteral( "scheme://input-mock-host:42/test-input-path" );
    outputUrl = processor.rewrite( environment, inputUrl, UrlRewriter.Direction.IN, null );

    assertThat( "Expect rewrite to produce a new URL",
        outputUrl, notNullValue() );
    assertThat(
        "Expect rewrite to contain the correct path.",
        outputUrl.toString(), is( "input-mock-scheme-2://input-mock-host-2:42/test-input-path" ) );

    //Test the scenario where input could match two different rules
    inputUrl = Parser.parseLiteral( "/foo/bar" );

    roles.remove(0);
    roles.add("service-1");
    outputUrl = processor.rewrite( environment, inputUrl, UrlRewriter.Direction.OUT, null);

    assertThat(
            "Expect rewrite to contain the correct path.",
            outputUrl.toString(), is( "/foo/service-1" ) );

    roles.remove(0);
    roles.add("service-2");

    outputUrl = processor.rewrite( environment, inputUrl, UrlRewriter.Direction.OUT, null);

    assertThat(
            "Expect rewrite to contain the correct path.",
            outputUrl.toString(), is( "/foo/service-2" ) );

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
        "xml", getTestResourceReader( "rewrite-with-same-rules.xml" ) );
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
        "xml", getTestResourceReader( "rewrite.xml" ) );
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
        "xml", getTestResourceReader( "rewrite.xml" ) );
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

  /*
   * Tests the rewrite pattern used for re-writing Solr urls passed through Knox.
   */
  @Test
  public void testSolrRewrite() throws Exception {
    URI inputUri, outputUri;
    Matcher<Void> matcher;
    Matcher<Void>.Match match;
    Template input, pattern, template;

    inputUri = new URI(
        "https://hortonworks.sandbox.hdp.24.test:8443/gateway/sandbox/solr/TestCollection/select?q=*.*&wt=json&indent=true");

    input = Parser.parseLiteral(inputUri.toString());
    pattern = Parser.parseTemplate("*://*:*/**/solr/{collection=**}/{query=**}?{**}");
    template = Parser.parseTemplate("http://sandbox.hortonworks.com/solr/{collection=**}/{query=**}?{**}");

    matcher = new Matcher<>();
    matcher.add(pattern, null);
    match = matcher.match(input);

    outputUri = Expander.expand(template, match.getParams(), null);

    final String reWrittenScheme = outputUri.getScheme();
    assertEquals("http", reWrittenScheme);

    final String reWrittenHost = outputUri.getHost();
    assertEquals("sandbox.hortonworks.com", reWrittenHost);

    final String reWrittenPath = outputUri.getPath();
    assertEquals("/solr/TestCollection/select", reWrittenPath);

    // Whole thing is (non-deterministicly ordered around the &s):
    // "q=*.*&wt=json&indent=true"
    final String reWrittenQuery = outputUri.getQuery();

    // Check individual parameters are present, and have the right value.
    final Map<String, String> reWrittenParams = mapUrlParameters(reWrittenQuery);
    assertTrue(reWrittenParams.containsKey("q"));
    assertEquals("*.*", reWrittenParams.get("q"));
    assertTrue(reWrittenParams.containsKey("wt"));
    assertEquals("json", reWrittenParams.get("wt"));
    assertEquals("true", reWrittenParams.get("indent"));
  }


  @Test
  public void testSolrRewriteDefaultPort() throws Exception {
    URI inputUri, outputUri;
    Matcher<Void> matcher;
    Matcher<Void>.Match match;
    Template input, pattern, template;

    inputUri = new URI(
                "https://hortonworks.sandbox.hdp.24.test/gateway/sandbox/solr/TestCollection/select?q=*.*&wt=json&indent=true");

    input = Parser.parseLiteral(inputUri.toString());
    pattern = Parser.parseTemplate("*://*:*/**/solr/{collection=**}/{query=**}?{**}");
    template = Parser.parseTemplate("http://sandbox.hortonworks.com/solr/{collection=**}/{query=**}?{**}");

    matcher = new Matcher<>();
    matcher.add(pattern, null);
    match = matcher.match(input);

    outputUri = Expander.expand(template, match.getParams(), null);

    final String reWrittenScheme = outputUri.getScheme();
    assertEquals("http", reWrittenScheme);

    final String reWrittenHost = outputUri.getHost();
    assertEquals("sandbox.hortonworks.com", reWrittenHost);

    final String reWrittenPath = outputUri.getPath();
    assertEquals("/solr/TestCollection/select", reWrittenPath);

    // Whole thing is (non-deterministicly ordered around the &s):
    // "q=*.*&wt=json&indent=true"
    final String reWrittenQuery = outputUri.getQuery();

    // Check individual parameters are present, and have the right value.
    final Map<String, String> reWrittenParams = mapUrlParameters(reWrittenQuery);
    assertTrue(reWrittenParams.containsKey("q"));
    assertEquals("*.*", reWrittenParams.get("q"));
    assertTrue(reWrittenParams.containsKey("wt"));
    assertEquals("json", reWrittenParams.get("wt"));
    assertEquals("true", reWrittenParams.get("indent"));
  }

  @Test
  public void testNoMatchOutput() throws IOException, URISyntaxException {
    UrlRewriteEnvironment environment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( environment, request, response );

    UrlRewriteProcessor processor = new UrlRewriteProcessor();
    UrlRewriteRulesDescriptor config = UrlRewriteRulesDescriptorFactory.load(
        "xml", getTestResourceReader( "rewrite-no-match.xml" ) );
    processor.initialize( environment, config );

    Template inputUrl = Parser.parseLiteral( "HTTP" );
    Template outputUrl = processor.rewrite( null, inputUrl, UrlRewriter.Direction.IN, "YARNUIV2/yarnuiv2/outbound/timeline" );

    assertThat( "Expect rewrite to not change the value",
        outputUrl, nullValue() );
    processor.destroy();
  }

  /**
   * Turn a string containing URL parameters, e.g.
   *
   * <pre>
   * a=b&c=d&e=f
   * </pre>
   *
   * into a map such as
   * <table>
   * <tr>
   * <th>Key</th>
   * <th>Value</th>
   * </tr>
   * <tr>
   * <td>a</td>
   * <td>b</td>
   * </tr>
   * <tr>
   * <td>c</td>
   * <td>d</td>
   * </tr>
   * </table>
   *
   * @param urlParameters the URL parameter string. Expected to contain something of the form
   *        "a=b&c=d" etc (i.e. Key=Value separated by &).
   * @return a map, with the key-values pairs representing the URL parameters.
   */
  private Map<String, String> mapUrlParameters(String urlParameters) {
    final Map<String, String> map = new HashMap<>();
    for (String pair : urlParameters.split("&")) {
      String[] kv = pair.split("=");
      map.put(kv[0], kv[1]);
    }
    return map;
  }
}
