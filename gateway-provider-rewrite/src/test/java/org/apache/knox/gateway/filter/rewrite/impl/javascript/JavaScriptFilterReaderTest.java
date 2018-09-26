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
package org.apache.knox.gateway.filter.rewrite.impl.javascript;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterContentDescriptor;
import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteFilterContentDescriptorImpl;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class JavaScriptFilterReaderTest {
  public static class NoopJsFilterReader extends JavaScriptFilterReader {
    public NoopJsFilterReader( Reader reader, UrlRewriteFilterContentDescriptor config ) throws IOException {
      super( reader, config );
    }

    @Override
    public String filterValueString( String name, String value, String ruleName ) {
      return value;
    }
  }

  public static class MatchRuleJsFilterReader extends JavaScriptFilterReader {
    private Map<String, Map<String,String>> rules;
    public MatchRuleJsFilterReader( Reader reader, Map<String, Map<String,String>> rules, UrlRewriteFilterContentDescriptor config ) throws IOException {
      super( reader, config );
      this.rules = rules;
    }

    @Override
    public String filterValueString( String name, String value, String ruleName ) {
      Map<String, String> rule = rules.get( ruleName );
      if ( rule == null ) {
        return value;
      }
      for ( Map.Entry<String, String> entry : rule.entrySet() ) {
        if ( Pattern.compile( entry.getKey() ).matcher( value ).matches() ) {
          return entry.getValue();
        }
      }
      return value;
    }
  }

  @Test
  public void testSimple() throws IOException {
    String inputJs = "function load_page() {}\n";
    StringReader inputReader = new StringReader( inputJs );
    UrlRewriteFilterContentDescriptor config = new UrlRewriteFilterContentDescriptorImpl();
    JavaScriptFilterReader filterReader = new NoopJsFilterReader( inputReader, config );
    String outputJs = new String( IOUtils.toCharArray( filterReader ) );
    assertThat( outputJs, is ( inputJs ) );
  }

  @Test
  public void testSimpleMultipleLines() throws IOException {
    String inputJs =
        "var url = '/webhdfs/v1' + abs_path + '?op=GET_BLOCK_LOCATIONS';\n" +
        "$.ajax({\"url\": url, \"crossDomain\": true}).done(function(data) {}).error(network_error_handler(url));\n";
    StringReader inputReader = new StringReader( inputJs );
    UrlRewriteFilterContentDescriptor config = new UrlRewriteFilterContentDescriptorImpl();
    config.addApply( "/webhdfs/v1", "test-rule" );
    JavaScriptFilterReader filterReader = new NoopJsFilterReader( inputReader, config );
    String outputJs = new String( IOUtils.toCharArray( filterReader ) );
    assertThat( outputJs, is ( inputJs ) );
  }

  @Test
  public void testMatchedJsContent() throws IOException {
    Map<String, Map<String, String>> rules = new HashMap<>();
    Map<String, String> map = new HashMap<>();
    map.put( "(https?://[^/':,]+:[\\d]+)?/cluster/app", "https://knoxhost:8443/cluster/app" );
    map.put( "/webhdfs/v1", "https://knoxhost:8443/webhdfs/v1" );
    rules.put( "test-rule", map );
    String inputJs =
        "var url = '/webhdfs/v1' + abs_path + '?op=GET_BLOCK_LOCATIONS';\n" +
        "$.ajax({\"url\": url, \"crossDomain\": true}).done(function(data) {\n" +
        "  var url = http://testhost:8088/cluster/app/application_1436831599487_0001;\n" +
        "}).error(network_error_handler(url));\n";
    StringReader inputReader = new StringReader( inputJs );
    UrlRewriteFilterContentDescriptor config = new UrlRewriteFilterContentDescriptorImpl();
    config.addApply( "(https?://[^/':,]+:[\\d]+)?/cluster/app", "test-rule" );
    config.addApply( "/webhdfs/v1", "test-rule" );
    JavaScriptFilterReader filterReader = new MatchRuleJsFilterReader( inputReader, rules, config );
    String outputJs = new String( IOUtils.toCharArray( filterReader ) );
    String expectedOutputJs =
        "var url = 'https://knoxhost:8443/webhdfs/v1' + abs_path + '?op=GET_BLOCK_LOCATIONS';\n" +
        "$.ajax({\"url\": url, \"crossDomain\": true}).done(function(data) {\n" +
        "  var url = https://knoxhost:8443/cluster/app/application_1436831599487_0001;\n" +
        "}).error(network_error_handler(url));\n";
    assertThat( outputJs, is ( expectedOutputJs ) );
  }
}
