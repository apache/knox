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

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;

class TraceResponse extends HttpServletResponseWrapper {
  private static final Logger log = LogManager.getLogger( TraceHandler.HTTP_RESPONSE_LOGGER );
  private static final Logger headLog = LogManager.getLogger( TraceHandler.HTTP_RESPONSE_HEADER_LOGGER );

  private ServletOutputStream output;
  private Set<Integer> filter;

  TraceResponse( HttpServletResponse response, Set<Integer> filter ) {
    super( response );
    this.filter = filter;
  }

  @Override
  public synchronized ServletOutputStream getOutputStream() throws IOException {
    if( log.isTraceEnabled() ) {
      traceResponseDetails();
      if( output == null && ( filter == null || filter.isEmpty() || filter.contains( getStatus() ) ) ) {
        output = new TraceOutput( super.getOutputStream() );
      }
      return output;
    } else {
      return super.getOutputStream();
    }
  }

  private void traceResponseDetails() {
    StringBuilder sb = new StringBuilder();
    TraceUtil.appendCorrelationContext( sb );
    sb.append( "|Response=" )
        .append( getStatus() );
    appendHeaders( sb );
    log.trace( sb.toString() );
  }

  private void appendHeaders( StringBuilder sb ) {
    if( headLog.isTraceEnabled() ) {
      Collection<String> names = getHeaderNames();
      for( String name : names ) {
        for( String value : getHeaders( name ) ) {
          sb.append( String.format(Locale.ROOT, "%n\tHeader[%s]=%s", name, value ) );
        }
      }
    }
  }
}


