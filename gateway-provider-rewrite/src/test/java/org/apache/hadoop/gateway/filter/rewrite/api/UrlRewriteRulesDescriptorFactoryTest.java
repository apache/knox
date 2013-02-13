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

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class UrlRewriteRulesDescriptorFactoryTest {

  @Test
  public void testCreate() throws Exception {
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
    InputStream stream = url.openStream();
    return stream;
  }

  private static Reader getTestResourceReader( String name, String charset ) throws IOException {
    return new InputStreamReader( getTestResourceStream( name ), charset );
  }

  @Test
  public void testLoadMissingFile() throws IOException {
    try {
      UrlRewriteRulesDescriptorFactory.load( "xml", getTestResourceReader( "missing.xml", "UTF-8" ) );
      fail( "Should have thrown a FileNotFoundException." );
    } catch ( FileNotFoundException e ) {
      assertThat( e.getMessage(), containsString( "missing.xml" ) );
    }
  }

  @Test
  public void testLoadEmptyFile() throws IOException {
    Logger logger = org.apache.log4j.LogManager.getLogger( "org.apache.commons.digester3.Digester" );
    Level level = logger.getLevel();
    try {
      logger.setLevel( org.apache.log4j.Level.OFF );
      UrlRewriteRulesDescriptorFactory.load( "xml", getTestResourceReader( "empty.xml", "UTF-8" ) );
      fail( "Should have thrown an IOException." );
    } catch ( IOException e ) {
      // Expected.
    } catch ( Throwable t ) {
      fail( "Should have thrown an IOException." );
    } finally {
      logger.setLevel( level );
    }
  }

  @Test
  public void testLoadInvalidFile() throws IOException {
    Logger logger = org.apache.log4j.LogManager.getLogger( "org.apache.commons.digester3.Digester" );
    Level level = logger.getLevel();
    try {
      logger.setLevel( org.apache.log4j.Level.OFF );
      UrlRewriteRulesDescriptorFactory.load( "xml", getTestResourceReader( "invalid.xml", "UTF-8" ) );
      fail( "Should have thrown an IOException." );
    } catch ( IOException e ) {
      // Expected.
    } catch ( Throwable t ) {
      fail( "Should have thrown an IOException." );
    } finally {
      logger.setLevel( level );
    }
  }

  @Test
  public void testLoadNoopFile() throws IOException {
    UrlRewriteRulesDescriptor config =
        UrlRewriteRulesDescriptorFactory.load( "xml", getTestResourceReader( "noop.xml", "UTF-8" ) );
    assertThat( "Rules should be an empty list.", config.getRules().isEmpty(), Matchers.is( true ) );
  }

  @Test
  public void testLoadSimpleFile() throws IOException {
    UrlRewriteRulesDescriptor config =
        UrlRewriteRulesDescriptorFactory.load( "xml", getTestResourceReader( "simple.xml", "UTF-8" ) );
  }

}
