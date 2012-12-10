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
package org.apache.hadoop.gateway.descriptor.xml;

import org.apache.hadoop.gateway.descriptor.GatewayDescriptor;
import org.apache.hadoop.gateway.descriptor.GatewayDescriptorFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.internal.matchers.StringContains.containsString;

public class XmlGatewayDescriptorExporterTest {

  @Test
  public void testFormat() {
    XmlGatewayDescriptorExporter exporter = new XmlGatewayDescriptorExporter();
    assertThat( exporter.getFormat(), is( "xml" ) );
  }

  @Test
  public void testXmlClusterDescriptorStore() throws IOException, SAXException, ParserConfigurationException {
    GatewayDescriptor descriptor = GatewayDescriptorFactory.create()
        .addResource()
        .source( "resource1-source" )
        .target( "resource1-target" )
        .addFilter()
        .role( "resource1-filter1-role" )
        .impl( "resource1-filter1-impl" )
        .addParam()
        .name( "resource1-filter1-param1-name" )
        .value( "resource1-filter1-param1-value" ).up()
        .addParam()
        .name( "resource1-filter1-param2-name" )
        .value( "resource1-filter1-param2-value" ).up().up()
        .addFilter()
        .role( "resource1-filter2-role" )
        .impl( "resource1-filter2-impl" ).up().up();

    CharArrayWriter writer = new CharArrayWriter();
    GatewayDescriptorFactory.store( descriptor, "xml", writer );

    String xml = writer.toString();

    Document doc = parse( xml );

    assertThat( doc, hasXPath( "/gateway/resource[1]/source", equalTo( "resource1-source" ) ) );
    assertThat( doc, hasXPath( "/gateway/resource[1]/target", equalTo( "resource1-target" ) ) );
    assertThat( doc, hasXPath( "/gateway/resource[1]/filter[1]/role", equalTo( "resource1-filter1-role" ) ) );
    assertThat( doc, hasXPath( "/gateway/resource[1]/filter[1]/class", equalTo( "resource1-filter1-impl" ) ) );
    assertThat( doc, hasXPath( "/gateway/resource[1]/filter[1]/param[1]/name", equalTo( "resource1-filter1-param1-name" ) ) );
    assertThat( doc, hasXPath( "/gateway/resource[1]/filter[1]/param[1]/value", equalTo( "resource1-filter1-param1-value" ) ) );
    assertThat( doc, hasXPath( "/gateway/resource[1]/filter[1]/param[2]/name", equalTo( "resource1-filter1-param2-name" ) ) );
    assertThat( doc, hasXPath( "/gateway/resource[1]/filter[1]/param[2]/value", equalTo( "resource1-filter1-param2-value" ) ) );
    assertThat( doc, hasXPath( "/gateway/resource[1]/filter[2]/role", equalTo( "resource1-filter2-role" ) ) );
    assertThat( doc, hasXPath( "/gateway/resource[1]/filter[2]/class", equalTo( "resource1-filter2-impl" ) ) );
  }

  @Test
  public void testXmlClusterDescriptorStoreMissingValue() throws IOException, SAXException, ParserConfigurationException {

    GatewayDescriptor descriptor = GatewayDescriptorFactory.create()
        .addResource().addFilter().addParam().up().up().up();

    CharArrayWriter writer = new CharArrayWriter();
    GatewayDescriptorFactory.store( descriptor, "xml", writer );
    String xml = writer.toString();
    //System.out.println( xml );

    Document doc = parse( xml );

    assertThat( doc, hasXPath( "/gateway/resource/filter/param" ) );
  }

  @Test
  public void testXmlClusterDescriptorStoreFailure() throws IOException {
    GatewayDescriptor descriptor = GatewayDescriptorFactory.create()
        .addResource().addFilter().addParam().up().up().up();

    try {
      GatewayDescriptorFactory.store( descriptor, "xml", new BrokenWriter() );
      fail( "Expected IOException" );
    } catch( IOException e ) {
      assertThat( e.getMessage(), containsString( "BROKEN" ) );
    }
  }

  private Document parse( String xml ) throws IOException, SAXException, ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    InputSource source = new InputSource( new StringReader( xml ) );
    return builder.parse( source );
  }

  private static class BrokenWriter extends Writer {

    @Override
    public void write( char[] cbuf, int off, int len ) throws IOException {
      throw new IOException( "BROKEN" );
    }

    @Override
    public void flush() throws IOException {
      throw new IOException( "BROKEN" );
    }

    @Override
    public void close() throws IOException {
      throw new IOException( "BROKEN" );
    }

  }

}
