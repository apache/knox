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

import java.io.IOException;
import java.io.Writer;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.knox.gateway.descriptor.FilterDescriptor;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.GatewayDescriptor;
import org.apache.knox.gateway.descriptor.GatewayDescriptorExporter;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class XmlGatewayDescriptorExporter implements GatewayDescriptorExporter, XmlGatewayDescriptorTags {

  @Override
  public String getFormat() {
    return "xml";
  }

  @Override
  public void store( GatewayDescriptor descriptor, Writer writer ) throws IOException {
    try {
      Document document = XmlUtils.createDocument();

      Element gateway = document.createElement( GATEWAY );
      document.appendChild( gateway );

      for( ResourceDescriptor resource : descriptor.resources() ) {
        gateway.appendChild( createResource( document, resource ) );
      }

      XmlUtils.writeXml( document, writer );

    } catch( ParserConfigurationException | TransformerException e ) {
      throw new IOException( e );
    }
  }

  private static Element createResource( Document dom, ResourceDescriptor resource ) {
    Element element = dom.createElement( RESOURCE );

    String role = resource.role();
    if( role != null ) {
      addTextElement( dom, element, RESOURCE_ROLE, role );
    }
    addTextElement( dom, element, RESOURCE_PATTERN, resource.pattern() );
    //addTextElement( dom, element, RESOURCE_TARGET, resource.target() );

    for( FilterDescriptor filter : resource.filters() ) {
      element.appendChild( createFilter( dom, filter ) );
    }

    return element;
  }

  private static Element createFilter( Document dom, FilterDescriptor filter ) {
    Element element = dom.createElement( FILTER );

    String role = filter.role();
    if( role != null ) {
      addTextElement( dom, element, FILTER_ROLE, filter.role() );
    }
    String name = filter.name();
    if( name != null ) {
      addTextElement( dom, element, FILTER_NAME, filter.name() );
    }
    addTextElement( dom, element, FILTER_IMPL, filter.impl() );

    for( FilterParamDescriptor param : filter.params() ) {
      element.appendChild( createFilterParam( dom, param ) );
    }

    return element;
  }

  private static Element createFilterParam( Document dom, FilterParamDescriptor param ) {
    Element element = dom.createElement( FILTER_PARAM );
    addTextElement( dom, element, FILTER_PARAM_NAME, param.name() );
    addTextElement( dom, element, FILTER_PARAM_VALUE, param.value() );
    return element;
  }

  private static void addTextElement( Document doc, Element parent, String tag, String text ) {
    if( text != null ) {
      Element element = doc.createElement( tag );
      element.appendChild( doc.createTextNode( text ) );
      parent.appendChild( element );
    }
  }

}
