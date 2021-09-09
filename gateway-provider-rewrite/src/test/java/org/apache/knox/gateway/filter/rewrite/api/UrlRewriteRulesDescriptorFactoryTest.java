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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.xmlmatchers.transform.XmlConverters;

import javax.xml.transform.Source;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.xmlmatchers.XmlMatchers.hasXPath;

public class UrlRewriteRulesDescriptorFactoryTest {

  @Test
  public void testCreate() {
    UrlRewriteRulesDescriptor descriptor = UrlRewriteRulesDescriptorFactory.create();
    assertThat( descriptor, notNullValue() );
    assertThat( descriptor.getRules(), notNullValue() );
    assertThat( descriptor.getRules().isEmpty(), is( true ) );

    UrlRewriteRuleDescriptor rule = descriptor.newRule();
    assertThat( rule, notNullValue() );
    assertThat( descriptor.getRules().isEmpty(), is( true ) );
    rule.name( "first" );
    descriptor.addRule( rule );
    assertThat( descriptor.getRules().size(), is( 1 ) );
    assertThat( descriptor.getRule( "first" ), sameInstance( rule ) );

    rule = descriptor.addRule( "second" );
    assertNotNull(rule);
    assertThat( descriptor.getRules().size(), is( 2 ) );
  }

  private static URL getTestResourceUrl( String name ) throws FileNotFoundException {
    name = UrlRewriteRulesDescriptorFactoryTest.class.getName().replaceAll( "\\.", "/" ) + "/" + name;
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

  private static Reader getTestResourceReader( String name) throws IOException {
    return new InputStreamReader( getTestResourceStream( name ), StandardCharsets.UTF_8 );
  }

  @Test
  public void testLoadMissingFile() throws IOException {
    try {
      UrlRewriteRulesDescriptorFactory.load( "xml", getTestResourceReader( "missing.xml" ) );
      fail( "Should have thrown a FileNotFoundException." );
    } catch ( FileNotFoundException e ) {
      assertThat( e.getMessage(), containsString( "missing.xml" ) );
    }
  }

  @Test
  public void testLoadEmptyFile() {
    Logger logger = (Logger)LogManager.getLogger( "org.apache.commons.digester3.Digester" );
    Level level = logger.getLevel();
    try {
      logger.setLevel( Level.OFF );
      UrlRewriteRulesDescriptorFactory.load( "xml", getTestResourceReader( "empty.xml" ) );
      fail( "Should have thrown an IOException." );
    } catch ( IOException e ) {
      // Expected.
      assertEquals("org.xml.sax.SAXParseException; lineNumber: 16; " +
                       "columnNumber: 4; Premature end of file.",
          e.getMessage());
    } catch ( Throwable t ) {
      fail( "Should have thrown an IOException." );
    } finally {
      logger.setLevel( level );
    }
  }

  @Test
  public void testLoadInvalidFile() {
    Logger logger = (Logger)LogManager.getLogger( "org.apache.commons.digester3.Digester" );
    Level level = logger.getLevel();
    try {
      logger.setLevel( Level.OFF );
      UrlRewriteRulesDescriptorFactory.load( "xml", getTestResourceReader( "invalid.xml" ) );
      fail( "Should have thrown an IOException." );
    } catch ( IOException e ) {
      // Expected.
      assertEquals("org.xml.sax.SAXParseException; lineNumber: 18; columnNumber: 2; " +
                       "The markup in the document preceding the root element must be well-formed.",
          e.getMessage());
    } catch ( Throwable t ) {
      fail( "Should have thrown an IOException." );
    } finally {
      logger.setLevel( level );
    }
  }

  @Test
  public void testLoadNoopFile() throws IOException {
    UrlRewriteRulesDescriptor config =
        UrlRewriteRulesDescriptorFactory.load( "xml", getTestResourceReader( "noop.xml" ) );
    assertThat( "Rules should be an empty list.", config.getRules().isEmpty(), Matchers.is( true ) );
  }

  @Test
  public void testLoadSimpleFile() throws IOException {
    UrlRewriteRulesDescriptor config =
        UrlRewriteRulesDescriptorFactory.load( "xml", getTestResourceReader( "simple.xml" ) );
    assertThat( "Failed to load simple config file.", config, notNullValue() );
  }

  @Test
  public void testLoadSimpleFilterFile() throws IOException {
    UrlRewriteRulesDescriptor config =
        UrlRewriteRulesDescriptorFactory.load( "xml", getTestResourceReader( "filter-simple.xml" ) );
    List<UrlRewriteFilterDescriptor> filters = config.getFilters();
    assertThat( filters.size(), is( 1 ) );
    UrlRewriteFilterDescriptor filter = config.getFilter( "test-filter-1" );
    assertThat( filter, notNullValue() );
    assertThat( config.getFilters().get(0), sameInstance( filter ) );
  }

  @Test
  public void testLoadStoreCompleteFilterFile() throws IOException {
    UrlRewriteRulesDescriptor config =
        UrlRewriteRulesDescriptorFactory.load( "xml", getTestResourceReader( "filter-complete.xml" ) );

    List<UrlRewriteFilterDescriptor> filters = config.getFilters();
    assertThat( filters.size(), is( 1 ) );

    UrlRewriteFilterDescriptor filter = config.getFilter( "test-filter-name-1" );
    assertThat( filter, notNullValue() );
    assertThat( config.getFilters().get(0), sameInstance( filter ) );
    assertThat( filter.name(), is( "test-filter-name-1" ) );

    UrlRewriteFilterContentDescriptor content = filter.getContent( "test-content-type-1/test-content-subtype-1" );
    assertThat( content, notNullValue() );
    assertThat( content.type(), is( "test-content-type-1/test-content-subtype-1" ) );

    List<UrlRewriteFilterPathDescriptor> selectors = content.getSelectors();
    assertThat( selectors, notNullValue() );
    assertThat( selectors.size(), is( 3 ) );

    UrlRewriteFilterApplyDescriptor apply = (UrlRewriteFilterApplyDescriptor)selectors.get( 0 );
    assertThat( apply, notNullValue() );
    assertThat( apply.path(), is( "test-apply-path-1" ) );
    assertThat( apply.rule(), is( "test-apply-rule-1" ) );
    assertThat( apply.compiledPath(), nullValue() );

    UrlRewriteFilterScopeDescriptor scope = (UrlRewriteFilterScopeDescriptor)selectors.get( 1 );
    assertThat( scope, notNullValue() );
    assertThat( scope.path(), is( "test-scope-path-1" ) );
    assertThat( scope.compiledPath(), nullValue() );
    List<UrlRewriteFilterPathDescriptor> scopeSelectors = scope.getSelectors();
    assertThat( scopeSelectors, notNullValue() );
    assertThat( scopeSelectors.size(), is( 1 ) );
    UrlRewriteFilterApplyDescriptor scopeApply = (UrlRewriteFilterApplyDescriptor)scopeSelectors.get( 0 );
    assertThat( scopeApply, notNullValue() );
    assertThat( scopeApply.path(), is( "test-apply-path-2" ) );
    assertThat( scopeApply.compiledPath(), nullValue() );
    assertThat( scopeApply.rule(), is( "test-apply-rule-2" ) );

    UrlRewriteFilterBufferDescriptor buffer = (UrlRewriteFilterBufferDescriptor)selectors.get( 2 );
    assertThat( buffer, notNullValue() );
    assertThat( buffer.path(), is( "test-buffer-path-1" ) );
    assertThat( buffer.compiledPath(), nullValue() );
    List<UrlRewriteFilterPathDescriptor> bufferSelectors = buffer.getSelectors();
    assertThat( bufferSelectors, notNullValue() );
    assertThat( bufferSelectors.size(), is( 2 ) );
    UrlRewriteFilterApplyDescriptor bufferApply = (UrlRewriteFilterApplyDescriptor)bufferSelectors.get( 0 );
    assertThat( bufferApply, notNullValue() );
    assertThat( bufferApply.path(), is( "test-apply-path-3" ) );
    assertThat( bufferApply.compiledPath(), nullValue() );
    assertThat( bufferApply.rule(), is( "test-apply-rule-3" ) );
    UrlRewriteFilterDetectDescriptor bufferDetect = (UrlRewriteFilterDetectDescriptor)bufferSelectors.get( 1 );
    assertThat( bufferDetect, notNullValue() );
    assertThat( bufferDetect.value(), is( "test-detect-value-1" ) );
    assertThat( bufferDetect.compiledValue(), nullValue() );
    List<UrlRewriteFilterPathDescriptor> detectSelectors = bufferDetect.getSelectors();
    assertThat( detectSelectors, notNullValue() );
    assertThat( detectSelectors.size(), is( 1 ) );
    UrlRewriteFilterApplyDescriptor detectApply = (UrlRewriteFilterApplyDescriptor)detectSelectors.get( 0 );
    assertThat( detectApply, notNullValue() );
    assertThat( detectApply.path(), is( "test-apply-path-4" ) );
    assertThat( detectApply.compiledPath(), nullValue() );
    assertThat( detectApply.rule(), is( "test-apply-rule-4" ) );

    StringWriter writer = new StringWriter();
    UrlRewriteRulesDescriptorFactory.store( config, "xml", writer );
    Source xml = XmlConverters.the( writer.toString() );

    assertThat( xml, notNullValue() );
    assertThat( xml, hasXPath( "/" ) );
    assertThat( xml, hasXPath( "/rules" ) );
    assertThat( xml, hasXPath( "/rules/filter" ) );
    assertThat( xml, hasXPath( "/rules/filter/@name", equalTo( "test-filter-name-1" ) ) );
    assertThat( xml, hasXPath( "/rules/filter/content" ) );
    assertThat( xml, hasXPath( "/rules/filter/content/@type", equalTo( "test-content-type-1/test-content-subtype-1" ) ) );
    assertThat( xml, hasXPath( "/rules/filter/content/apply" ) );
    assertThat( xml, hasXPath( "/rules/filter/content/apply/@path", equalTo( "test-apply-path-1" ) ) );
    assertThat( xml, hasXPath( "/rules/filter/content/apply/@rule", equalTo( "test-apply-rule-1" ) ) );
    assertThat( xml, hasXPath( "/rules/filter/content/scope" ) );
    assertThat( xml, hasXPath( "/rules/filter/content/scope/@path", equalTo( "test-scope-path-1" ) ) );
    assertThat( xml, hasXPath( "/rules/filter/content/scope/apply" ) );
    assertThat( xml, hasXPath( "/rules/filter/content/scope/apply/@path", equalTo( "test-apply-path-2" ) ) );
    assertThat( xml, hasXPath( "/rules/filter/content/scope/apply/@rule", equalTo( "test-apply-rule-2" ) ) );
    assertThat( xml, hasXPath( "/rules/filter/content/buffer" ) );
    assertThat( xml, hasXPath( "/rules/filter/content/buffer/@path", equalTo( "test-buffer-path-1" ) ) );
    assertThat( xml, hasXPath( "/rules/filter/content/buffer/apply" ) );
    assertThat( xml, hasXPath( "/rules/filter/content/buffer/apply/@path", equalTo( "test-apply-path-3" ) ) );
    assertThat( xml, hasXPath( "/rules/filter/content/buffer/apply/@rule", equalTo( "test-apply-rule-3" ) ) );
    assertThat( xml, hasXPath( "/rules/filter/content/buffer/detect" ) );
    assertThat( xml, hasXPath( "/rules/filter/content/buffer/detect/@path", equalTo( "test-detect-path-1" ) ) );
    assertThat( xml, hasXPath( "/rules/filter/content/buffer/detect/@value", equalTo( "test-detect-value-1" ) ) );
    assertThat( xml, hasXPath( "/rules/filter/content/buffer/detect/apply" ) );
    assertThat( xml, hasXPath( "/rules/filter/content/buffer/detect/apply/@path", equalTo( "test-apply-path-4" ) ) );
    assertThat( xml, hasXPath( "/rules/filter/content/buffer/detect/apply/@rule", equalTo( "test-apply-rule-4" ) ) );
  }
}
