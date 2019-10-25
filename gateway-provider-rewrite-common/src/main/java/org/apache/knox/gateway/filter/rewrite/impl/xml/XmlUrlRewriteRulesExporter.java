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
package org.apache.knox.gateway.filter.rewrite.impl.xml;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterBufferDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterContentDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterGroupDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterPathDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterScopeDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFlowDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFunctionDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRuleDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteStepDescriptor;
import org.apache.knox.gateway.filter.rewrite.i18n.UrlRewriteMessages;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteRulesExporter;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterApplyDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterDetectDescriptor;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.util.XmlUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
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
  public Object store(UrlRewriteRulesDescriptor descriptor, Writer writer) throws IOException {
    return store(descriptor, writer, false);
  }

  public Object store( UrlRewriteRulesDescriptor descriptor, Writer writer, boolean omitXmlHeader ) throws IOException {
    try {
      Document document = XmlUtils.createDocument();

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

      if( !descriptor.getRules().isEmpty() ) {
        for( UrlRewriteRuleDescriptor rule : descriptor.getRules() ) {
          Element ruleElement = createRule( document, rule );
          root.appendChild( ruleElement );
        }
      }

      if( !descriptor.getFilters().isEmpty() ) {
        for( UrlRewriteFilterDescriptor filter : descriptor.getFilters() ) {
          Element filterElement = createFilter( document, filter );
          root.appendChild( filterElement );
        }
      }

      XmlUtils.writeXml( document, writer, omitXmlHeader);

      return document;

    } catch( ParserConfigurationException | TransformerException e ) {
      throw new IOException( e );
    } catch( InvocationTargetException | IllegalAccessException | IntrospectionException | NoSuchMethodException e ) {
      LOG.failedToWriteRulesDescriptor( e );
      return null;
    }
  }

  private Element createFilter( Document document, UrlRewriteFilterDescriptor parent )
      throws IntrospectionException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Element parentElement = createElement( document, FILTER, parent );
    for( UrlRewriteFilterContentDescriptor child: parent.getContents() ) {
      Element childElement = createFilterContent( document, child );
      parentElement.appendChild( childElement );
    }
    return parentElement;
  }

  private Element createFilterContent( Document document, UrlRewriteFilterContentDescriptor parent )
      throws IntrospectionException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Element parentElement = createElement( document, CONTENT, parent );
    for( UrlRewriteFilterPathDescriptor child: parent.getSelectors() ) {
      Element childElement = createFilterSelector( document, child );
      parentElement.appendChild( childElement );
    }
    return parentElement;
  }

  private Element createFilterSelector( Document document, UrlRewriteFilterPathDescriptor parent )
      throws IntrospectionException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Element parentElement = createElement( document, toTagName( parent ), parent );
    if( parent instanceof UrlRewriteFilterGroupDescriptor) {
      for( UrlRewriteFilterPathDescriptor child: ((UrlRewriteFilterGroupDescriptor)parent).getSelectors() ) {
        Element childElement = createFilterSelector( document, child );
        parentElement.appendChild( childElement );
      }
    }
    return parentElement;
  }

  private Element createRule( Document document, UrlRewriteRuleDescriptor rule )
      throws IntrospectionException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Element ruleElement = createElement( document, RULE, rule );
    for( UrlRewriteStepDescriptor step: rule.steps() ) {
      Element childElement = createStep( document, step );
      ruleElement.appendChild( childElement );
    }
    return ruleElement;
  }

  private Element createStep( Document document, UrlRewriteStepDescriptor step )
      throws IntrospectionException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Element parentElement = createElement( document, step.type(), step );
    if( step instanceof UrlRewriteFlowDescriptor) {
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

  private static String toTagName( final UrlRewriteFilterPathDescriptor descriptor ) {
    if( descriptor instanceof UrlRewriteFilterApplyDescriptor) {
      return APPLY;
    } else if( descriptor instanceof UrlRewriteFilterDetectDescriptor) {
      return DETECT;
    } else if( descriptor instanceof UrlRewriteFilterBufferDescriptor) {
      return BUFFER;
    } else if( descriptor instanceof UrlRewriteFilterScopeDescriptor) {
      return SCOPE;
    } else {
      throw new IllegalArgumentException();
    }
  }

}
