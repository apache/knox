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

import net.htmlparser.jericho.Attribute;
import net.htmlparser.jericho.Attributes;
import net.htmlparser.jericho.EndTag;
import net.htmlparser.jericho.Segment;
import net.htmlparser.jericho.StartTag;
import net.htmlparser.jericho.StreamedSource;
import net.htmlparser.jericho.Tag;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterApplyDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterContentDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterPathDescriptor;
import org.apache.knox.gateway.filter.rewrite.i18n.UrlRewriteMessages;
import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteFilterReader;
import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteUtil;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class HtmlFilterReaderBase extends Reader implements
    UrlRewriteFilterReader {

  private static final String SCRIPTTAG = "script";
  private static final UrlRewriteFilterPathDescriptor.Compiler<Pattern> REGEX_COMPILER = new RegexCompiler();

  private static final UrlRewriteMessages LOG = MessagesFactory.get( UrlRewriteMessages.class );

  private Deque<Level> stack;
  private Reader reader;
  private StreamedSource parser;
  private Iterator<Segment> iterator;
  private int lastSegEnd;
  private int offset;
  private StringWriter writer;
  private StringBuffer buffer;
  private UrlRewriteFilterContentDescriptor config;

  protected HtmlFilterReaderBase( Reader reader ) throws IOException {
    this.reader = reader;
    stack = new ConcurrentLinkedDeque<>();
    parser = new StreamedSource( reader );
    iterator = parser.iterator();
    writer = new StringWriter();
    buffer = writer.getBuffer();
    offset = 0;
  }

  protected HtmlFilterReaderBase( Reader reader, UrlRewriteFilterContentDescriptor config ) throws IOException, ParserConfigurationException {
    this(reader);
    this.config = config;
  }

  protected abstract String filterAttribute( QName elementName, QName attributeName, String attributeValue, String ruleName );

  protected abstract String filterText( QName elementName, String text, String ruleName );

  @Override
  public int read( char[] destBuffer, int destOffset, int destCount ) throws IOException {
    int count = 0;
    int available = buffer.length() - offset;

    if( available == 0 ) {
      if( iterator.hasNext() ) {
        iterator.next();
        processCurrentSegment();
        available = buffer.length() - offset;
      } else {
        count = -1;
      }
    }

    if( available > 0 ) {
      count = Math.min( destCount, available );
      buffer.getChars( offset, offset + count, destBuffer, destOffset );
      offset += count;
      if( offset == buffer.length() ) {
        offset = 0;
        buffer.setLength( 0 );
      }
    }

    return count;
  }

  private void processCurrentSegment() {
    Segment segment = parser.getCurrentSegment();
    // If this tag is inside the previous tag (e.g. a server tag) then
    // ignore it as it was already output along with the previous tag.
    if( segment.getEnd() <= lastSegEnd ) {
      return;
    }
    lastSegEnd = segment.getEnd();
    if( segment instanceof Tag ) {
      if( segment instanceof StartTag ) {
        processStartTag( (StartTag)segment );
      } else if ( segment instanceof EndTag ) {
        processEndTag( (EndTag)segment );
      } else {
        writer.write( segment.toString() );
      }
    } else {
      processText( segment );
    }
  }

  private void processEndTag( EndTag tag ) {
    while( !stack.isEmpty() ) {
      Level popped = stack.pop();
      if( popped.getTag().getName().equalsIgnoreCase( tag.getName() ) ) {
        break;
      }
    }
    writer.write( tag.toString() );
  }

  private void processStartTag( StartTag tag ) {
    if( "<".equals( tag.getTagType().getStartDelimiter() ) ) {
      stack.push( new Level( tag ) );
      writer.write( "<" );
      writer.write( tag.getNameSegment().toString() );
      Attributes attributes = tag.getAttributes();
      if( !attributes.isEmpty() ) {
        for( Attribute attribute : attributes ) {
          processAttribute( attribute );
        }
      }
      if( tag.toString().trim().endsWith( "/>" ) || tag.isEmptyElementTag() ) {
        stack.pop();
        writer.write( "/>" );
      } else {
        writer.write( ">" );
      }
    } else {
      writer.write( tag.toString() );
    }
  }

  private void processAttribute( Attribute attribute ) {
    writer.write( " " );
    writer.write( attribute.getName() );
    if(attribute.hasValue()) {
      /*
       * non decoded value, return the raw value of the attribute as it appears
       * in the source document, without decoding, see KNOX-791.
       */
      String inputValue = attribute.getValueSegment().toString();
      String outputValue = inputValue;
      try {
        Level tag = stack.peek();
        String name = getRuleName(inputValue);
        outputValue = filterAttribute( tag.getQName(), tag.getQName( attribute.getName() ), inputValue, name );
        if( outputValue == null ) {
          outputValue = inputValue;
        }
      } catch ( Exception e ) {
        LOG.failedToFilterAttribute( attribute.getName(), e );
      }
      writer.write( "=" );
      writer.write( attribute.getQuoteChar() );
      writer.write( outputValue );
      writer.write( attribute.getQuoteChar() );
    }
  }

  private String getRuleName(String inputValue) {
    if( config != null && !config.getSelectors().isEmpty() ) {
      for( UrlRewriteFilterPathDescriptor<?> selector : config.getSelectors() ) {
        if ( selector instanceof UrlRewriteFilterApplyDescriptor) {
          UrlRewriteFilterApplyDescriptor apply = (UrlRewriteFilterApplyDescriptor)selector;
          Matcher matcher = apply.compiledPath( REGEX_COMPILER ).matcher( inputValue );
            if (matcher.matches()) {
              return apply.rule();
            }
          }
        }
      }
      return null;
    }

  private void processText( Segment segment ) {
    String inputValue = segment.toString();
    String outputValue = inputValue;
    try {
      if (!stack.isEmpty()) {
        String tagName = stack.peek().getTag().getName();
        if (SCRIPTTAG.equals(tagName) && config != null && !config.getSelectors().isEmpty() ) {
          // embedded javascript content
          outputValue = UrlRewriteUtil.filterJavaScript( inputValue, config, this, REGEX_COMPILER );
        } else {
          outputValue = filterText( stack.peek().getQName(), inputValue, getRuleName(inputValue) );
        }
      }
      if( outputValue == null ) {
        outputValue = inputValue;
      }
    } catch ( Exception e ) {
      LOG.failedToFilterValue( inputValue, null, e );
    }
    writer.write( outputValue );
  }

  @Override
  public void close() throws IOException {
    parser.close();
    reader.close();
    writer.close();
    stack.clear();
  }

  private static class Level {
    private StartTag tag;
    private QName name;
    private Map<String,String> namespaces;

    Level( StartTag tag ) {
      this.tag = tag;
      this.name = null;
      this.namespaces = null;
    }

    private StartTag getTag() {
      return tag;
    }

    private QName getQName() {
      if( name == null ) {
        name = getQName( tag.getName() );
      }
      return name;
    }

    private String getNamespace( String prefix ) {
      return getNamespaces().get( prefix );
    }

    private QName getQName(String name) {
      final int colon = name == null ? -1 : name.indexOf(':');
      final String prefix = getPrefix(name, colon);
      final String local = getLocal(name, colon);
      final String namespace = getNamespace(prefix);
      return new QName(namespace, local, prefix);
    }

    private String getPrefix(String name, final int colon) {
      return name != null && colon > 0 ? name.substring(0, colon) : "";
    }

    private String getLocal(String name, final int colon) {
      if (name != null && colon > 0) {
        return colon + 1 < name.length() ? name.substring(colon + 1) : "";
      } else {
        return name;
      }
    }

    private Map<String,String> getNamespaces() {
      if( namespaces == null ) {
        namespaces = new HashMap<>();
        parseNamespaces();
      }
      return namespaces;
    }

    private void parseNamespaces() {
      if (tag.getAttributes() != null) {
        String prefix, attributeName;
        for (Attribute attribute : tag.getAttributes()) {
          attributeName = attribute.getName() == null ? "" : attribute.getName();
          if (attributeName.toLowerCase(Locale.ROOT).startsWith("xmlns")) {
            int colon = attributeName.indexOf(':', 5);
            prefix = colon <= 0 ? "" : attributeName.substring(colon);
            namespaces.put(prefix, attribute.getValue());
          }
        }
      }
    }

  }

}
