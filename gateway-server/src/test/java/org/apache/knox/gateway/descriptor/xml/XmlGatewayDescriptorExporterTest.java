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
package org.apache.knox.gateway.descriptor.xml;

import org.apache.knox.gateway.descriptor.GatewayDescriptor;
import org.apache.knox.gateway.descriptor.GatewayDescriptorFactory;
import org.apache.knox.gateway.util.XmlUtils;
import org.apache.knox.test.Console;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class XmlGatewayDescriptorExporterTest {

  @Test
  public void testFormat() {
    XmlGatewayDescriptorExporter exporter = new XmlGatewayDescriptorExporter();
    assertThat( exporter.getFormat(), is( "xml" ) );
  }

  @Test
  public void testXmlGatewayDescriptorStore() throws IOException, SAXException, ParserConfigurationException {
    GatewayDescriptor descriptor = GatewayDescriptorFactory.create()
        .addResource()
        .pattern( "resource1-source" )
        //.target( "resource1-target" )
        .addFilter()
        .role( "resource1-filter1-role" )
        .impl( "resource1-filter1-impl" )
        .param()
        .name( "resource1-filter1-param1-name" )
        .value( "resource1-filter1-param1-value" ).up()
        .param()
        .name( "resource1-filter1-param2-name" )
        .value( "resource1-filter1-param2-value" ).up().up()
        .addFilter()
        .name( "resource1-filter2-name" )
        .role( "resource1-filter2-role" )
        .impl( "resource1-filter2-impl" ).up().up();

    CharArrayWriter writer = new CharArrayWriter();
    GatewayDescriptorFactory.store( descriptor, "xml", writer );

    String xml = writer.toString();

    InputSource source = new InputSource( new StringReader( xml ) );
    Document doc = XmlUtils.readXml( source );

    assertThat( doc, hasXPath( "/gateway/resource[1]/pattern", is( "resource1-source" ) ) );
    //assertThat( doc, hasXPath( "/gateway/resource[1]/target", is( "resource1-target" ) ) );
    assertThat( doc, hasXPath( "/gateway/resource[1]/filter[1]/name", is( "" ) ) );
    assertThat( doc, hasXPath( "/gateway/resource[1]/filter[1]/role", is( "resource1-filter1-role" ) ) );
    assertThat( doc, hasXPath( "/gateway/resource[1]/filter[1]/class", is( "resource1-filter1-impl" ) ) );
    assertThat( doc, hasXPath( "/gateway/resource[1]/filter[1]/param[1]/name", is( "resource1-filter1-param1-name" ) ) );
    assertThat( doc, hasXPath( "/gateway/resource[1]/filter[1]/param[1]/value", is( "resource1-filter1-param1-value" ) ) );
    assertThat( doc, hasXPath( "/gateway/resource[1]/filter[1]/param[2]/name", is( "resource1-filter1-param2-name" ) ) );
    assertThat( doc, hasXPath( "/gateway/resource[1]/filter[1]/param[2]/value", is( "resource1-filter1-param2-value" ) ) );
    assertThat( doc, hasXPath( "/gateway/resource[1]/filter[2]/name", is( "resource1-filter2-name" ) ) );
    assertThat( doc, hasXPath( "/gateway/resource[1]/filter[2]/role", is( "resource1-filter2-role" ) ) );
    assertThat( doc, hasXPath( "/gateway/resource[1]/filter[2]/class", is( "resource1-filter2-impl" ) ) );
  }

  @Test
  public void testXmlGatewayDescriptorStoreMissingValue() throws IOException, SAXException, ParserConfigurationException {

    GatewayDescriptor descriptor = GatewayDescriptorFactory.create()
        .addResource().addFilter().param().up().up().up();

    CharArrayWriter writer = new CharArrayWriter();
    GatewayDescriptorFactory.store( descriptor, "xml", writer );
    String xml = writer.toString();
    //System.out.println( xml );

    InputSource source = new InputSource( new StringReader( xml ) );
    Document doc = XmlUtils.readXml( source );

    assertThat( doc, hasXPath( "/gateway/resource/filter/param" ) );
  }

  @Test
  public void testXmlGatewayDescriptorStoreFailure() throws IOException {
    GatewayDescriptor descriptor = GatewayDescriptorFactory.create()
        .addResource().addFilter().param().up().up().up();

    Console console = new Console();
    try {
      console.capture();
      GatewayDescriptorFactory.store( descriptor, "xml", new BrokenWriter() );
      fail( "Expected IOException" );
    } catch( IOException e ) {
      assertThat( e.getMessage(), containsString( "BROKEN" ) );
    } finally {
      console.release();
    }

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
