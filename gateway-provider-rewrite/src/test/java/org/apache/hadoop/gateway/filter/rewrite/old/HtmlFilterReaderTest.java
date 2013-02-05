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
package org.apache.hadoop.gateway.filter.rewrite.old;

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.gateway.filter.rewrite.impl.html.HtmlFilterReader;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.xmlmatchers.XmlMatchers;
import org.xmlmatchers.transform.XmlConverters;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

public class HtmlFilterReaderTest {

  public static class NoopHtmlFilterReader extends HtmlFilterReader {
    public NoopHtmlFilterReader( Reader reader ) throws IOException {
      super( reader );
    }

    @Override
    protected String filterAttribute( String tagName, String attributeName, String attributeValue ) {
      return attributeValue;
    }

    @Override
    protected String filterText( String tagName, String text ) {
      return text;
    }
  }

  @Test
  public void testSimple() throws IOException {
    String inputHtml = "<html><head></head><body></body></html>";
    StringReader inputReader = new StringReader( inputHtml );
    HtmlFilterReader filterReader = new NoopHtmlFilterReader( inputReader );
    String outputHtml = new String( IOUtils.toCharArray( filterReader ) );

    MatcherAssert.assertThat( XmlConverters.the( outputHtml ), XmlMatchers.hasXPath( "/html" ) );
    MatcherAssert.assertThat( XmlConverters.the( outputHtml ), XmlMatchers.hasXPath( "/html/head" ) );
    MatcherAssert.assertThat( XmlConverters.the( outputHtml ), XmlMatchers.hasXPath( "/html/body" ) );
  }

  @Test
  public void testSimpleNs() throws IOException {
    String inputHtml = "<html><head></head><body></body></html>";
    StringReader inputReader = new StringReader( inputHtml );
    HtmlFilterReader filterReader = new NoopHtmlFilterReader( inputReader );
    String outputHtml = new String( IOUtils.toCharArray( filterReader ) );

    MatcherAssert.assertThat( XmlConverters.the( outputHtml ), XmlMatchers.hasXPath( "/html" ) );
    MatcherAssert.assertThat( XmlConverters.the( outputHtml ), XmlMatchers.hasXPath( "/html/head" ) );
    MatcherAssert.assertThat( XmlConverters.the( outputHtml ), XmlMatchers.hasXPath( "/html/body" ) );
  }

}
