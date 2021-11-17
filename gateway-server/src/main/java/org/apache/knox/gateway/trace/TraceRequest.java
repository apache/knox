/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.trace;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Locale;

class TraceRequest extends HttpServletRequestWrapper {
  private static final Logger log = LogManager.getLogger( TraceHandler.HTTP_REQUEST_LOGGER );
  private static final Logger headLog = LogManager.getLogger( TraceHandler.HTTP_REQUEST_HEADER_LOGGER );

  private ServletInputStream input;

  TraceRequest( HttpServletRequest request ) {
    super( request );
    if( log.isTraceEnabled() ) {
      traceRequestDetails();
    }
  }

  @Override
  public synchronized ServletInputStream getInputStream() throws IOException {
    if( log.isTraceEnabled() ) {
      if( input == null ) {
        input = new TraceInput( super.getInputStream() );
      }
      return input;
    } else {
      return super.getInputStream();
    }
  }

  private void traceRequestDetails() {
    StringBuilder sb = new StringBuilder();
    TraceUtil.appendCorrelationContext( sb );
    sb.append("|Request=")
        .append(getMethod())
        .append(' ')
        .append(getRequestURI());
    String qs = getQueryString();
    if( qs != null ) {
      sb.append('?').append(qs);
    }
    appendHeaders(sb);
    log.trace(sb.toString());
  }

  private void appendHeaders( StringBuilder sb ) {
    if( headLog.isTraceEnabled() ) {
      Enumeration<String> names = getHeaderNames();
      while( names.hasMoreElements() ) {
        String name = names.nextElement();
        Enumeration<String> values = getHeaders( name );
        while( values.hasMoreElements() ) {
          String value = values.nextElement();
          sb.append( String.format(Locale.ROOT, "%n\tHeader[%s]=%s", name, value ) );
        }
      }
    }
  }
}