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
package org.apache.hadoop.gateway.filter;

import org.apache.hadoop.gateway.util.UrlRewriter;

import javax.servlet.FilterConfig;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;

/**
 *
 */
public class UrlRewriteResponse extends HttpServletResponseWrapper {

  private static final String CONTENT_LENGTH_HEADER_NAME = "Content-Length";
  private FilterConfig config;
  private UrlRewriter rewriter;
  private HtmlUrlRewritingOutputStream output;

  public UrlRewriteResponse( FilterConfig config, UrlRewriter rewriter, HttpServletResponse response ) throws IOException {
    super( response );
    this.config = config;
    this.rewriter = rewriter;
    this.output = null;
  }

  protected boolean allowHeader( String name ) {
    return !CONTENT_LENGTH_HEADER_NAME.equalsIgnoreCase( name );
  }

  // Ignore the Content-Length from the pivot response since the response body may be rewritten.
  @Override
  public void setHeader( String name, String value ) {
    if( allowHeader( name) ) {
      super.setHeader( name, value );
    }
  }

  // Ignore the Content-Length from the pivot response since the response body may be rewritten.
  @Override
  public void addHeader( String name, String value ) {
    if( allowHeader( name) ) {
      super.addHeader( name, value );
    }
  }

  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    HttpServletResponse response = (HttpServletResponse)getResponse();
    String contentType = response.getContentType();
    if( contentType != null ) {
      String[] contentTypeParts = contentType.split( ";" );
      String mediaType = contentTypeParts[ 0 ];
      if( "text/html".equals( mediaType ) ) {
        if( output == null ) {
          output = new HtmlUrlRewritingOutputStream(
              config, rewriter, response.getOutputStream(), response.getCharacterEncoding() );
        }
        return output;
      } else {
        return getResponse().getOutputStream();
      }
    } else {
      return getResponse().getOutputStream();
    }
  }

}
