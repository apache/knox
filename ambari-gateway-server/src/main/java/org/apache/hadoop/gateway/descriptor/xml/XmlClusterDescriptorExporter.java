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

import org.apache.hadoop.gateway.descriptor.ClusterDescriptor;
import org.apache.hadoop.gateway.descriptor.ClusterFilterDescriptor;
import org.apache.hadoop.gateway.descriptor.ClusterFilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ClusterResourceDescriptor;
import org.apache.hadoop.gateway.descriptor.spi.ClusterDescriptorExporter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.Writer;

public class XmlClusterDescriptorExporter implements ClusterDescriptorExporter, XmlClusterDescriptorTags {

  @Override
  public String getFormat() {
    return "xml";
  }

  @Override
  public void store( ClusterDescriptor descriptor, Writer writer ) throws IOException {
    try {
      DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = builderFactory.newDocumentBuilder();
      Document dom = builder.newDocument();

      Element root = dom.createElement( CLUSTER );
      dom.appendChild( root );

      for( ClusterResourceDescriptor resource : descriptor.resources() ) {
        root.appendChild( createResource( dom, resource ) );
      }

      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      Transformer transformer = transformerFactory.newTransformer();
      transformer.setOutputProperty( OutputKeys.OMIT_XML_DECLARATION, "yes" );
      transformer.setOutputProperty( OutputKeys.INDENT, "yes" );

      StreamResult result = new StreamResult( writer );
      DOMSource source = new DOMSource(dom);
      transformer.transform( source, result );
    } catch( Exception e ) {
      throw new IOException( e );
    }
  }

  private static Element createResource( Document dom, ClusterResourceDescriptor resource ) {
    Element element = dom.createElement( RESOURCE );

    Element source = dom.createElement( RESOURCE_SOURCE );
    source.appendChild( dom.createTextNode( resource.source() ) );
    element.appendChild( source );

    Element target = dom.createElement( RESOURCE_TARGET );
    source.appendChild( dom.createTextNode( resource.target() ) );
    element.appendChild( target );

    for( ClusterFilterDescriptor filter : resource.filters() ) {
      element.appendChild( createFilter( dom, filter ) );
    }

    return element;
  }

  private static Element createFilter( Document dom, ClusterFilterDescriptor filter ) {
    Element element = dom.createElement( FILTER );

    Element source = dom.createElement( FILTER_ROLE );
    source.appendChild( dom.createTextNode( filter.role() ) );
    element.appendChild( source );

    Element target = dom.createElement( FILTER_IMPL );
    source.appendChild( dom.createTextNode( filter.impl() ) );
    element.appendChild( target );

    for( ClusterFilterParamDescriptor param : filter.params() ) {
      element.appendChild( createFilterParam( dom, param ) );
    }

    return element;
  }

  private static Element createFilterParam( Document dom, ClusterFilterParamDescriptor param ) {
    Element element = dom.createElement( FILTER_PARAM );

    Element name = dom.createElement( FILTER_PARAM_NAME );
    name.appendChild( dom.createTextNode( param.name() ) );
    element.appendChild( name );

    Element value = dom.createElement( FILTER_PARAM_VALUE );
    value.appendChild( dom.createTextNode( param.value() ) );
    element.appendChild( value );

    return element;
  }

}
