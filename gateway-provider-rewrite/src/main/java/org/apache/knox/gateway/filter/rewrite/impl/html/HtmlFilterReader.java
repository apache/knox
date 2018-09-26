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
package org.apache.knox.gateway.filter.rewrite.impl.html;

import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterContentDescriptor;

import java.io.IOException;
import java.io.Reader;

//map.put( "meta", buildTagPattern( ".*url\\s*=\\s*['\"]?(.*?)[;\\s'\"\\/>].*" ) );
//map.put( "link", buildTagPattern( ".*href\\s*=\\s*['\"]?(.*?)['\"\\>].*" ) );
//map.put( "a", buildTagPattern( ".*href\\s*=\\s*['\"]?(.*?)['\"\\>].*" ) );
//map.put( "th", buildTagPattern( ".*window.document.location\\s*=\\s*['\"]?(.*?)['\"\\>].*" ) );
//    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\"0;url=dfshealth.jsp\"/>", "meta" );
//String markup = "<link href=\"/static/org.apache.hadoop.css\" rel=\"stylesheet\" type=\"text/css\" >";
//String markup = "<a href=\"dfsnodelist.jsp?whatNodes=DECOMMISSIONING\">";
//String markup = "th class=headerASC onClick=\"window.document.location='/dfsnodelist.jsp?whatNodes=LIVE&sorter/field=name&sorter/order=DSC'\" title=\"sort on this column\">";

public abstract class HtmlFilterReader extends HtmlFilterReaderBase {

  public HtmlFilterReader( Reader reader ) throws IOException, ParserConfigurationException {
    super( reader );
  }

  public HtmlFilterReader( Reader reader, UrlRewriteFilterContentDescriptor config ) throws IOException, ParserConfigurationException {
    super( reader, config );
  }

  protected abstract String filterAttribute( String tagName, String attributeName, String attributeValue, String ruleName );

  protected abstract String filterText( String tagName, String text, String ruleName );

  @Override
  protected final String filterAttribute( QName elementName, QName attributeName, String attributeValue, String ruleName ) {
    return filterAttribute( elementName.getLocalPart(), attributeName.getLocalPart(), attributeValue, ruleName );
  }

  @Override
  protected final String filterText( QName elementName, String text, String ruleName ) {
    return filterText( elementName.getLocalPart(), text, ruleName );
  }

}
