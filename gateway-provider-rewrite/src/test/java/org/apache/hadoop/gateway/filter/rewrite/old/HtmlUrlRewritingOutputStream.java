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

import org.apache.hadoop.gateway.util.urltemplate.Parser;
import org.apache.hadoop.gateway.util.urltemplate.Rewriter;

import javax.servlet.FilterConfig;
import javax.servlet.ServletOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
//TODO: This class needs to handle character encoding properly.
public class HtmlUrlRewritingOutputStream extends ServletOutputStream {

  static Pattern absoluteUrlPattern = Pattern.compile( "^/.*$" );

  static Pattern tagNamePattern = Pattern.compile( "<\\s*([^>\\s/!]*).*", Pattern.CASE_INSENSITIVE | Pattern.CANON_EQ | Pattern.DOTALL );

  static Map<String,Pattern> tagUrlPatterns = buildTagPatternMap();

  private FilterConfig config;
  private Rewriter rewriter;
  private ServletOutputStream stream;
  private String encoding;
  private ByteArrayOutputStream buffer;
  private boolean inTag;

  public HtmlUrlRewritingOutputStream( FilterConfig config, Rewriter rewriter, ServletOutputStream stream, String encoding ) {
    this.config = config;
    this.rewriter = rewriter;
    this.stream = stream;
    this.encoding = encoding;
    this.buffer = new ByteArrayOutputStream( 1024 );
    this.inTag = false;
  }

  private void flushTag() throws IOException {
    byte[] bytes = buffer.toByteArray();
    String tag = new String( bytes, encoding );
    try {
      tag = rewriteTag( tag );
    } catch( URISyntaxException e ) {
      throw new IOException( e );
    }
    stream.write( tag.getBytes( encoding ) );
    inTag = false;
    buffer.reset();
  }

  private boolean openTag( byte b ) {
    if( !inTag && b == '<' ) {
      inTag = true;
      return true;
    }
    return false;
  }

  private boolean closeTag( int b ) {
    if( inTag && b == '>' ) {
      inTag = false;
      return true;
    }
    return false;
  }

  @Override
  public void write( int b ) throws IOException {
    if( openTag( (byte)b ) ) {
      buffer.write( b );
    } else if( closeTag( b ) ) {
      buffer.write( b );
      flushTag();
    } else if( inTag ) {
      buffer.write( b );
    } else {
      stream.write( b );
    }
  }

  @Override
  public void flush() throws IOException {
    if( inTag ) {
      flushTag();
    }
    stream.flush();
  }

  @Override
  public void close() throws IOException {
    flush();
    stream.close();
  }

  // There are three things that can happen here.
  // 1. A physical URL that starts with a scheme (e.g. http:)
  // 2. An absolute URL that starts with a slash (/path)
  // 3. A relative URL that doesn't start with either.
  String rewriteUrl( String oldUrl ) throws URISyntaxException {
    //String newUrl = rewriter.rewriteUrl( oldUrl );
    //TODO: Need to have a param resolver for stream based URL rewriting.  Needs to be common for all Url rewriting.
    URI newUri = rewriter.rewrite( Parser.parse( oldUrl ), null );
    //System.out.println( "Rewrite: " + oldUrl + " to " + newUrl );
//    String newUrl = oldUrl;
//    // If this is an absolute URL, then
//    if( absoluteUrlPattern.matcher( oldUrl ).matches() ) {
//      //TODO: Should really rewriteUri absolute urls that target the proxied service too.
//      newUrl = Urls.concatUrl( config.getInitParameter("prefix"), oldUrl );
//    } else {
//      //TODO: Need access to the proxy path.
//      //TODO: Don't think I need to do this.
//      // Prefix the original pattern with the proxy path.
//      //newUrl = Urls.concatUrl( config.getInitParameter("prefix"), oldUrl );
//    }
    return newUri.toString();
  }

  String rewriteTag( String oldTag ) throws URISyntaxException {
    //System.out.println( "Extracted markup: " + oldTag );
    String newTag = oldTag;
    String tagName = extractTag( oldTag );
    Pattern pattern = tagUrlPatterns.get( tagName.toLowerCase() );
    if( pattern != null ) {
      Matcher matcher = pattern.matcher( oldTag );
      if( matcher.matches() ) {
        String oldUrl = matcher.group( 1 );
        String newUrl = rewriteUrl( oldUrl );
        int urlStart = matcher.start( 1 );
        int urlEnd = matcher.end( 1 );
        int newUrlLen = urlStart + newUrl.length() + oldTag.length()-urlEnd;
        StringBuilder newTagBuilder = new StringBuilder( newUrlLen );
        newTagBuilder.append( oldTag.substring( 0, urlStart ) ).append( newUrl ).append( oldTag.substring( urlEnd ) );
        newTag = newTagBuilder.toString();
      }
    }
    return newTag;
  }

  static String extractTag( String markup ) {
    String tag = null;
    Matcher matcher = tagNamePattern.matcher( markup );
    if( matcher.matches() ) {
      tag = matcher.group( 1 );
    }
    //System.out.println( "Extracted tag: " + tag );
    return tag;
  }

  static Pattern buildTagPattern( String pattern ) {
    return Pattern.compile( pattern, Pattern.CASE_INSENSITIVE | Pattern.CANON_EQ | Pattern.DOTALL );
  }

  //TODO: This is starting to fall apart.  This either needs to come from configuration or not depend upon the tag name.
  // Possibly both config driven and attribute based.
  // The basic issue is that I can imagine window.document.location being in a number of tags.
  static Map<String,Pattern> buildTagPatternMap() {
    Map<String,Pattern> map = new HashMap<String,Pattern>();
    map.put( "meta", buildTagPattern( ".*url\\s*=\\s*['\"]?(.*?)[;\\s'\"\\/>].*" ) );
    map.put( "link", buildTagPattern( ".*href\\s*=\\s*['\"]?(.*?)['\"\\>].*" ) );
    map.put( "a", buildTagPattern( ".*href\\s*=\\s*['\"]?(.*?)['\"\\>].*" ) );
    map.put( "th", buildTagPattern( ".*window.document.location\\s*=\\s*['\"]?(.*?)['\"\\>].*" ) );
    return map;
  }

}
