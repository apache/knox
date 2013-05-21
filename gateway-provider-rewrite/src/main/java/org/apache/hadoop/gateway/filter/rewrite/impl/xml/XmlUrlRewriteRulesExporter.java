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
package org.apache.hadoop.gateway.filter.rewrite.impl.xml;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteFlowDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteFunctionDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRuleDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteStepDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.i18n.UrlRewriteMessages;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteRulesExporter;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;

public class XmlUrlRewriteRulesExporter implements UrlRewriteRulesExporter, XmlRewriteRulesTags {

  private static final UrlRewriteMessages LOG = MessagesFactory.get( UrlRewriteMessages.class );
  
  @Override
  public String getFormat() {
    return "xml";
  }

  @Override
  public void store( UrlRewriteRulesDescriptor descriptor, Writer writer ) throws IOException {
    try {
      DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = builderFactory.newDocumentBuilder();
      Document document = builder.newDocument();
      document.setXmlStandalone( true );

      Element root = document.createElement( ROOT );
      document.appendChild( root );

      if( !descriptor.getFunctions().isEmpty() ) {
        Element functionsElement = document.createElement( FUNCTIONS );
        root.appendChild( functionsElement );
        for( UrlRewriteFunctionDescriptor function : descriptor.getFunctions() ) {
          Element functionElement = createElement( document, function.name(), function );
          functionsElement.appendChild( functionElement );
        }
      }

      for( UrlRewriteRuleDescriptor rule : descriptor.getRules() ) {
        Element ruleElement = createRule( document, rule );
        root.appendChild( ruleElement );
      }

      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      transformerFactory.setAttribute( "indent-number", 2 );
      Transformer transformer = transformerFactory.newTransformer();
      //transformer.setOutputProperty( OutputKeys.OMIT_XML_DECLARATION, "yes" );
      transformer.setOutputProperty( OutputKeys.STANDALONE, "yes" );
      transformer.setOutputProperty( OutputKeys.INDENT, "yes" );

      StreamResult result = new StreamResult( writer );
      DOMSource source = new DOMSource(document);
      transformer.transform( source, result );

    } catch( ParserConfigurationException e ) {
      throw new IOException( e );
    } catch( TransformerException e ) {
      throw new IOException( e );
    } catch( InvocationTargetException e ) {
      LOG.failedToWriteRulesDescriptor( e );
    } catch( NoSuchMethodException e ) {
      LOG.failedToWriteRulesDescriptor( e );
    } catch( IntrospectionException e ) {
      LOG.failedToWriteRulesDescriptor( e );
    } catch( IllegalAccessException e ) {
      LOG.failedToWriteRulesDescriptor( e );
    }
  }

  private Element createRule( Document document, UrlRewriteRuleDescriptor rule )
      throws IntrospectionException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Element ruleElement = createElement( document, "rule", rule );
    for( UrlRewriteStepDescriptor step: rule.steps() ) {
      Element childElement = createStep( document, step );
      ruleElement.appendChild( childElement );
    }
    return ruleElement;
  }

  private Element createStep( Document document, UrlRewriteStepDescriptor step )
      throws IntrospectionException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Element parentElement = createElement( document, step.type(), step );
    if( step instanceof UrlRewriteFlowDescriptor ) {
      UrlRewriteFlowDescriptor flow = (UrlRewriteFlowDescriptor)step;
      for( Object child: flow.steps() ) {
        UrlRewriteStepDescriptor childStep = (UrlRewriteStepDescriptor)child;
        Element childElement = createStep( document, childStep );
        parentElement.appendChild( childElement );
      }

    }
    return parentElement;
  }

  private Element createElement( Document document, String name, Object bean )
      throws IntrospectionException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Element element = document.createElement( name );
    BeanInfo beanInfo = Introspector.getBeanInfo( bean.getClass(), Object.class );
    for( PropertyDescriptor propInfo: beanInfo.getPropertyDescriptors() ) {
      String propName = propInfo.getName();
      if( propInfo.getReadMethod() != null && String.class.isAssignableFrom( propInfo.getPropertyType() ) ) {
        String propValue = BeanUtils.getProperty( bean, propName );
        if( propValue != null && !propValue.isEmpty() ) {
          // Doing it the hard way to avoid having the &'s in the query string escaped at &amp;
          Attr attr = document.createAttribute( propName );
          attr.setValue( propValue );
          element.setAttributeNode( attr );
          //element.setAttribute( propName, propValue );
        }
      }
    }
    return element;
  }

}
