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
package org.apache.hadoop.gateway.util.urltemplate;

import org.apache.hadoop.test.category.FastTests;
import org.apache.hadoop.test.category.UnitTests;
import org.easymock.EasyMock;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@Category( { UnitTests.class, FastTests.class } )
public class RewriterTest {

  @Ignore( "Not implemented yet." )
  @Test
  public void testParamIndirectionRewrite() throws Exception {
    URI inputUri, outputUri;
    Template inputTemplate, outputTemplate;
    MockParams resolver = new MockParams();
    resolver.addValue( "some-known-host", "some-other-host" );

    // This is how it works now.
    // This is the URI like we would get from say a Location HTTP header.
    inputUri = new URI( "http://some-host:80" );
    // This will be used to extract the three values from input URI: scheme='http', host='some-known-host', port='80'
    inputTemplate = Parser.parse( "{scheme}://{host}:{port}" );
    // The template to build a new URI.  The match those in the input template.
    outputTemplate = Parser.parse( "{scheme}://{host}:{port}" );
    // Copies the values extracted from the input URI via the inputTemplate and inserts them into the outputTemplate.
    // The resolver isn't used in this case.
    outputUri = Rewriter.rewrite( inputUri, inputTemplate, outputTemplate, resolver );
    assertThat( outputUri.toString(), equalTo( "http://some-host:80" ) );

    // Need a syntax for the URL rewriter to tell it to take the value extracted from the inputUri
    // and lookup it up via the resolver to populate the output template.  So from the input template
    // the values of 'some-host' is extracted for the 'host' parameter.  The '$' in the output template below
    // would tell the rewriter to look the value 'some-host' up in the resolver and place that in the
    // output URI.
    // I want to discuss the '$' syntax hoping you have a better suggestion.
    inputUri = new URI( "http://some-known-host:80" );
    inputTemplate = Parser.parse( "{scheme}://{host}:{port}" );
    outputTemplate = Parser.parse( "{scheme}://{$host}:{port}" );
    outputUri = Rewriter.rewrite( inputUri, inputTemplate, outputTemplate, resolver );
    assertThat( outputUri.toString(), equalTo( "http://some-other-host:80" ) );

    // What should happen if the param value cannot be resolved to something else?
    // Right now it uses the empty string.
    inputUri = new URI( "http://some-unknown-host:80" );
    inputTemplate = Parser.parse( "{scheme}://{host}:{port}" );
    outputTemplate = Parser.parse( "{scheme}://{$host}:{port}" );
    outputUri = Rewriter.rewrite( inputUri, inputTemplate, outputTemplate, resolver );
    assertThat( outputUri.toString(), equalTo( "http://:80" ) );

    // Should there be another syntax that uses the original value if it cannot resolve the extraced value?
    // Should this be the default and only behavior?
    // See the '?' in the output template below.
    inputUri = new URI( "http://some-unknown-host:80" );
    inputTemplate = Parser.parse( "{scheme}://{host}:{port}" );
    outputTemplate = Parser.parse( "{scheme}://{?host}:{port}" );
    outputUri = Rewriter.rewrite( inputUri, inputTemplate, outputTemplate, resolver );
    assertThat( outputUri.toString(), equalTo( "http://some-unknown-host:80" ) );
  }

  @Test
  public void testBasicRewrite() throws Exception {
    URI inputUri, outputUri;
    Template inputTemplate, outputTemplate;
    MockParams resolver = new MockParams();

    inputUri = new URI( "path-1/path-2" );
    inputTemplate = Parser.parse( "{path-1-name}/{path-2-name}" );
    outputTemplate = Parser.parse( "{path-2-name}/{path-1-name}" );
    outputUri = Rewriter.rewrite( inputUri, inputTemplate, outputTemplate, resolver );
    assertThat( outputUri.toString(), equalTo( "path-2/path-1" ) );

    inputUri = new URI( "path-1/path-2/path-3/path-4" );
    inputTemplate = Parser.parse( "path-1/{path=**}/path-4" ); // Need the ** to allow the expansion to include all path values.
    outputTemplate = Parser.parse( "new-path-1/{path=**}/new-path-4" );
    outputUri = Rewriter.rewrite( inputUri, inputTemplate, outputTemplate, resolver );
    assertThat( outputUri.toString(), equalTo( "new-path-1/path-2/path-3/new-path-4" ) );

    inputUri = new URI( "some-path?query-name=some-queryParam-value" );
    inputTemplate = Parser.parse( "{path-name}?query-name={queryParam-value}" );
    outputTemplate = Parser.parse( "{queryParam-value}/{path-name}" );
    outputUri = Rewriter.rewrite( inputUri, inputTemplate, outputTemplate, resolver );
    assertThat( outputUri.toString(), equalTo( "some-queryParam-value/some-path" ) );
  }

  @Test
  public void testQueryExtraRewrite() throws Exception {
    URI inputUri, outputUri;
    Template inputTemplate, outputTemplate;
    MockParams params = new MockParams();

    inputUri = new URI( "path?query=value" );
    inputTemplate = Parser.parse( "path?{**}" );
    outputTemplate = Parser.parse( "path?{**}" );

    outputUri = Rewriter.rewrite( inputUri, inputTemplate, outputTemplate, params );
    assertThat( outputUri.toString(), equalTo( "path?query=value" ) );

    inputUri = new URI( "path?query=value" );
    inputTemplate = Parser.parse( "path?{*}" );
    outputTemplate = Parser.parse( "path?{*}" );
    outputUri = Rewriter.rewrite( inputUri, inputTemplate, outputTemplate, params );
    assertThat( outputUri.toString(), equalTo( "path?query=value" ) );
  }

  @Test
  public void testRewriteUrlWithHttpServletRequestAndFilterConfig() throws Exception {
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getParameter( "expect-queryParam-name" ) ).andReturn( "expect-queryParam-value" ).anyTimes();
    EasyMock.expect( request.getParameterValues( "expect-queryParam-name" ) ).andReturn( new String[]{"expect-queryParam-value"} ).anyTimes();
    EasyMock.replay( request );

    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect( config.getInitParameter( "filter-queryParam-name" ) ).andReturn( "filter-queryParam-value" ).anyTimes();
    EasyMock.replay( config );

    Template sourcePattern, targetPattern;
    URI actualInput, actualOutput, expectOutput;

    actualInput = new URI( "http://some-host:0/some-path" );
//    sourcePattern = Parser.parse( "**" );
    sourcePattern = Parser.parse( "*://*:*/**" );
    targetPattern = Parser.parse( "should-not-change" );
    actualOutput = Rewriter.rewrite( actualInput, sourcePattern, targetPattern, new TestResolver( config, request ) );
    expectOutput = new URI( "should-not-change" );
    assertThat( actualOutput, equalTo( expectOutput ) );

    actualInput = new URI( "http://some-host:0/some-path" );
    sourcePattern = Parser.parse( "*://*:*/{0=**}" );
    targetPattern = Parser.parse( "{0}" );
    actualOutput = Rewriter.rewrite( actualInput, sourcePattern, targetPattern, new TestResolver( config, request ) );
    expectOutput = new URI( "some-path" );
    assertThat( actualOutput, equalTo( expectOutput ) );

    actualInput = new URI( "http://some-host:0/pathA/pathB/pathC" );
    sourcePattern = Parser.parse( "*://*:*/pathA/{1=*}/{2=*}" );
    targetPattern = Parser.parse( "http://some-other-host/{2}/{1}" );
    actualOutput = Rewriter.rewrite( actualInput, sourcePattern, targetPattern, new TestResolver( config, request ) );
    expectOutput = new URI( "http://some-other-host/pathC/pathB" );
    assertThat( actualOutput, equalTo( expectOutput ) );

    actualInput = new URI( "http://some-host:0/some-path" );
    sourcePattern = Parser.parse( "*://*:*/**" );
    targetPattern = Parser.parse( "{filter-queryParam-name}" );
    actualOutput = Rewriter.rewrite( actualInput, sourcePattern, targetPattern, new TestResolver( config, request ) );
    expectOutput = new URI( "filter-queryParam-value" );
    assertThat( actualOutput, equalTo( expectOutput ) );

    actualInput = new URI( "http://some-host:0/some-path" );
    sourcePattern = Parser.parse( "*://*:*/**" );
    targetPattern = Parser.parse( "{expect-queryParam-name}" );
    actualOutput = Rewriter.rewrite( actualInput, sourcePattern, targetPattern, new TestResolver( config, request ) );
    expectOutput = new URI( "expect-queryParam-value" );
    assertThat( actualOutput, equalTo( expectOutput ) );

    actualInput = new URI( "http://some-host:0/some-path" );
    sourcePattern = Parser.parse( "*://*:*/**" );
    targetPattern = Parser.parse( "http://some-other-host/{filter-queryParam-name}/{expect-queryParam-name}" );
    actualOutput = Rewriter.rewrite( actualInput, sourcePattern, targetPattern, new TestResolver( config, request ) );
    expectOutput = new URI( "http://some-other-host/filter-queryParam-value/expect-queryParam-value" );
    assertThat( actualOutput, equalTo( expectOutput ) );

    actualInput = new URI( "http://some-host:0/pathA/pathB/pathC" );
    sourcePattern = Parser.parse( "*://*:*/pathA/{1=*}/{2=*}" );
    targetPattern = Parser.parse( "http://some-other-host/{2}/{1}/{filter-queryParam-name}/{expect-queryParam-name}" );
    actualOutput = Rewriter.rewrite( actualInput, sourcePattern, targetPattern, new TestResolver( config, request ) );
    expectOutput = new URI( "http://some-other-host/pathC/pathB/filter-queryParam-value/expect-queryParam-value" );
    assertThat( actualOutput, equalTo( expectOutput ) );

    actualInput = new URI( "/namenode/api/v1/test" );
    sourcePattern = Parser.parse( "/namenode/api/v1/{0=**}" );
    targetPattern = Parser.parse( "http://{filter-queryParam-name}/webhdfs/v1/{0}" );
    actualOutput = Rewriter.rewrite( actualInput, sourcePattern, targetPattern, new TestResolver( config, request ) );
    expectOutput = new URI( "http://filter-queryParam-value/webhdfs/v1/test" );
    assertThat( actualOutput, equalTo( expectOutput ) );

    actualInput = new URI( "/namenode/api/v1/test" );
    sourcePattern = Parser.parse( "/namenode/api/v1/{0=**}" );
    targetPattern = Parser.parse( "http://{filter-queryParam-name}/webhdfs/v1/{0}" );
    actualOutput = Rewriter.rewrite( actualInput, sourcePattern, targetPattern, new TestResolver( config, request ) );
    expectOutput = new URI( "http://filter-queryParam-value/webhdfs/v1/test" );
    assertThat( actualOutput, equalTo( expectOutput ) );

    actualInput = new URI( "http://vm.home:50075/webhdfs/v1/test/file?op=CREATE&user.name=hdfs&overwrite=false" );
    expectOutput = new URI( "http://filter-queryParam-value/gatewaycluster/namenode/api/v1/test/file?op=CREATE&user.name=hdfs&overwrite=false" );
    sourcePattern = Parser.parse( "*://*:*/webhdfs/v1/{path=**}?op={op=*}&user.name={username=*}&overwrite={overwrite=*}" );
    targetPattern = Parser.parse( "http://{filter-queryParam-name}/gatewaycluster/namenode/api/v1/{path=**}?op={op}&user.name={username}&overwrite={overwrite}" );
    actualOutput = Rewriter.rewrite( actualInput, sourcePattern, targetPattern, new TestResolver( config, request ) );
    assertThat( actualOutput, equalTo( expectOutput ) );

    actualInput = new URI( "/datanode/api/v1/test?user.name=hdfs&op=CREATE&overwrite=false&host=vm.home&port=50075" );
    expectOutput = new URI( "http://vm.home:50075/webhdfs/v1/test?op=CREATE&user.name=hdfs&overwrite=false" );
    sourcePattern = Parser.parse( "/datanode/api/v1/{path=**}?{host}&{port}&{**}" );
    targetPattern = Parser.parse( "http://{host}:{port}/webhdfs/v1/{path=**}?{**}" );
    actualOutput = Rewriter.rewrite( actualInput, sourcePattern, targetPattern, new TestResolver( config, request ) );
    // Note: Had to change the order of the expected query params to match.
    // This is probably dependent upon iterator ordering and therefore might be a test issue.
    // Unfortunately URI.equals() doesn't ignore query queryParam order.
    // TODO: Enhance Template.equals() to ignore query queryParam order and use that here.
    assertThat( actualOutput, equalTo( expectOutput ) );

    // *://**/webhdfs/v1/{path=**}?**={**}
    // http://{org.apache.org.apache.hadoop.gateway.address}/gatewaycluster/namenode/api/v1/{path}?**={**}
    // 1) Should not add query if none in source.
    // 2) Should only add unmatch query parameters
    // Consider chaning = within {} to : and wrapping query fully within {} (e.g. {query=pattern:alias}
    // *://**/webhdfs/v1/{path:**}?{**=**} // Means 0..n query names allowed/expanded.  Each can have 0..n values.
    // *://**/webhdfs/v1/{path:**}?{*=**} // Means one and only one query name matched.  Only last unused queryParam is expanded.
    // *://**/webhdfs/v1/{path:**}?{**=*} // Means 0..n query names required.  Only last value is matched/expanded.
    // *://**/webhdfs/v1/{path:**}?{*=*} // Means one and only one query name required.  Only last value is matched/expanded.
    // /top/{**:mid}/bot
    // ?{query=pattern:alias}
    // ?{query} => ?{query=*:query}
    // ?{query=pattern} => ?{query=pattern:query}

  }

  @Test
  public void testRewriteNameNodeLocationResponseHeader() throws URISyntaxException {
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.replay( request );

    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect( config.getInitParameter( "gateway.url" ) ).andReturn( "http://gw:8888/gateway/cluster" ).anyTimes();
    EasyMock.replay( config );

    Template sourcePattern, targetPattern;
    URI actualInput, actualOutput, expectOutput;

    sourcePattern = Parser.parse( "*://{host}:{port}/webhdfs/v1/{path=**}?{**}" );
    targetPattern = Parser.parse( "{gateway.url}/datanode/api/v1/{path=**}?{host}&{port}&{**}" );

    actualInput = new URI( "http://vm.local:50075/webhdfs/v1/tmp/GatewayWebHdfsFuncTest/dirA700/fileA700?op=CREATE&user.name=hdfs&overwrite=false&permission=700" );
    expectOutput = new URI( "http://gw:8888/gateway/cluster/datanode/api/v1/tmp/GatewayWebHdfsFuncTest/dirA700/fileA700?host=vm.local&port=50075&op=CREATE&user.name=hdfs&overwrite=false&permission=700" );
    actualOutput = Rewriter.rewrite( actualInput, sourcePattern, targetPattern, new TestResolver( config, request ) );
    assertThat( actualOutput, equalTo( expectOutput ) );
  }

  @Test
  public void testRewriteDataNodeLocationResponseHeader() throws URISyntaxException {
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.replay( request );

    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect( config.getInitParameter( "gateway.url" ) ).andReturn( "http://gw:8888/gateway/cluster" ).anyTimes();
    EasyMock.replay( config );

    Template sourcePattern, targetPattern;
    URI actualInput, actualOutput, expectOutput;

    sourcePattern = Parser.parse( "/datanode/api/v1/{path=**}?{host}&{port}&{**}" );
    targetPattern = Parser.parse( "http://{host}:{port}/webhdfs/v1/{path=**}?{**}" );

    actualInput = new URI( "/datanode/api/v1/tmp/GatewayWebHdfsFuncTest/dirA700/fileA700?host=vm.local&port=50075&op=CREATE&user.name=hdfs&overwrite=false&permission=700" );
    expectOutput = new URI( "http://vm.local:50075/webhdfs/v1/tmp/GatewayWebHdfsFuncTest/dirA700/fileA700?op=CREATE&user.name=hdfs&overwrite=false&permission=700" );
    actualOutput = Rewriter.rewrite( actualInput, sourcePattern, targetPattern, new TestResolver( config, request ) );
    assertThat( actualOutput, equalTo( expectOutput ) );
  }

  @Test
  public void testRewriteExcludesQueryDelimWhenInputHasNoQueryParams() throws Exception {
    Template inputTemplate, outputTemplate;
    URI actualInput, actualOutput, expectOutput;

    inputTemplate = Parser.parse( "{scheme}://{host}:*/{path=**}?{**}" );
    outputTemplate = Parser.parse( "{scheme}://{host}:777/test-output/{path=**}?{**}" );

    actualInput = new URI( "http://host:42/pathA/pathB" );
    expectOutput = new URI( "http://host:777/test-output/pathA/pathB" );

    actualOutput = Rewriter.rewrite( actualInput, inputTemplate, outputTemplate, null );

    assertThat( actualOutput, is( expectOutput ) );
  }
  
  private class TestResolver implements Params {

    private FilterConfig config;
    private HttpServletRequest request;

    private TestResolver( FilterConfig config, HttpServletRequest request ) {
      this.config = config;
      this.request = request;
    }

    @Override
    public Set<String> getNames() {
      return Collections.emptySet();
    }

    // Picks the values from either the expect or the config in that order.
    @SuppressWarnings( "unchecked" )
    public List<String> resolve( String name ) {
      List<String> values = null;

      if( request !=  null ) {
        String[] array = request.getParameterValues( name );
        if( array != null ) {
          values = (List<String>)Arrays.asList( array );
          return values;
        }
      }

      if( config != null ) {
        String value = config.getInitParameter( name );
        if( value != null ) {
          values = new ArrayList<String>( 1 );
          values.add( value );
          return values;
        }
      }

      return values;
    }
  }

}
