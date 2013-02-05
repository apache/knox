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
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( request, response );

    UrlRewriteProcessor processor = new UrlRewriteProcessor();
    UrlRewriteRulesDescriptor config = UrlRewriteRulesDescriptorFactory.load(
        "xml", getTestResourceReader( "rewrite.xml", "UTF-8" ) );
    processor.initialize( config );

    Template inputUrl = Parser.parse( "test-scheme://test-host:1/test-input-path" );
    Template outputUrl = processor.rewrite( inputUrl, UrlRewriter.Direction.IN );

    assertThat( "Expect rewrite to produce a new URL",
        outputUrl, notNullValue() );
    assertThat(
        "Expect rewrite to contain the correct path.",
        outputUrl.toString(), is( "test-scheme://test-host:1/test-output-path" ) );
    processor.destroy();
  }

}
