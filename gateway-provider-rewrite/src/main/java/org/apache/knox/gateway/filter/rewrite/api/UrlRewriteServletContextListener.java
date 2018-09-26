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
package org.apache.knox.gateway.filter.rewrite.api;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.knox.gateway.filter.rewrite.i18n.UrlRewriteMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;

public class UrlRewriteServletContextListener implements ServletContextListener {

  public static final String PROCESSOR_ATTRIBUTE_NAME = UrlRewriteProcessor.class.getName();
  public static final String DESCRIPTOR_LOCATION_INIT_PARAM_NAME = "rewriteDescriptorLocation";
  public static final String DESCRIPTOR_DEFAULT_FILE_NAME = "rewrite.xml";
  public static final String DESCRIPTOR_DEFAULT_LOCATION = "/WEB-INF/" + DESCRIPTOR_DEFAULT_FILE_NAME;
  private static final UrlRewriteMessages LOG = MessagesFactory.get( UrlRewriteMessages.class );

  @Override
  public void contextInitialized( ServletContextEvent event ) {
    UrlRewriteRulesDescriptor descriptor = null;
    try {
      URL url = locateDescriptor( event.getServletContext() );
      descriptor = loadDescriptor( url );
    } catch( IOException e ) {
      throw new IllegalStateException( e );
    }
    ServletContext context = event.getServletContext();
    UrlRewriteEnvironment environment = new UrlRewriteServletEnvironment( context );
    UrlRewriteProcessor processor = new UrlRewriteProcessor();
    processor.initialize( environment, descriptor );
    event.getServletContext().setAttribute( PROCESSOR_ATTRIBUTE_NAME, processor );
  }

  @Override
  public void contextDestroyed( ServletContextEvent event ) {
    UrlRewriteProcessor processor =
        (UrlRewriteProcessor)event.getServletContext().getAttribute( PROCESSOR_ATTRIBUTE_NAME );
    event.getServletContext().removeAttribute( PROCESSOR_ATTRIBUTE_NAME );
    if( processor != null ) {
      processor.destroy();
    }
  }

  public static UrlRewriter getUrlRewriter( ServletContext context ) {
    return ((UrlRewriteProcessor)context.getAttribute( PROCESSOR_ATTRIBUTE_NAME ));
  }

  private static URL locateDescriptor( ServletContext context ) throws IOException {
    String param = context.getInitParameter( DESCRIPTOR_LOCATION_INIT_PARAM_NAME );
    if( param == null ) {
      param = DESCRIPTOR_DEFAULT_LOCATION;
    }
    URL url;
    try {
      url = context.getResource( param );
    } catch( MalformedURLException e ) {
      // Ignore it and try using the value directly as a URL.
      url = null;
    }
    if( url == null ) {
      url = new URL( param );
    }
    if( url == null ) {
      throw new FileNotFoundException( param );
    }
    return url;
  }

  private static UrlRewriteRulesDescriptor loadDescriptor( URL url ) throws IOException {
    InputStream stream = url.openStream();
    Reader reader = new InputStreamReader( stream, "UTF-8" );
    UrlRewriteRulesDescriptor descriptor = UrlRewriteRulesDescriptorFactory.load( "xml", reader );
    try {
      reader.close();
    } catch( IOException closeException ) {
      LOG.failedToLoadRewriteRulesDescriptor( closeException );
    }
    return descriptor;
  }

}
