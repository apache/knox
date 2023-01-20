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

import org.apache.knox.gateway.servlet.SynchronousServletOutputStreamAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.ServletOutputStream;
import java.io.IOException;
import java.util.Locale;

class TraceOutput extends SynchronousServletOutputStreamAdapter {
  private static final Logger log = LogManager.getLogger( TraceHandler.HTTP_RESPONSE_LOGGER );
  private static final Logger bodyLog = LogManager.getLogger( TraceHandler.HTTP_RESPONSE_BODY_LOGGER );

  private ServletOutputStream delegate;

  private static final int BUFFER_LIMIT = 1024;
  private StringBuilder buffer = new StringBuilder( BUFFER_LIMIT );

  TraceOutput( ServletOutputStream delegate ) {
    this.delegate = delegate;
  }

  @Override
  public synchronized void write( int b ) throws IOException {
    if( b >= 0 ) {
      buffer.append( (char)b );
      if( buffer.length() == BUFFER_LIMIT ) {
        traceBody();
      }
    }
    delegate.write( b );
  }

  @Override
  public void flush() throws IOException {
    traceBody();
    delegate.flush();
  }

  @Override
  public void close() throws IOException {
    traceBody();
    delegate.close();
  }

  private synchronized void traceBody() {
    if( buffer.length() > 0 ) {
      String body = buffer.toString();
      buffer.setLength( 0 );
      StringBuilder sb = new StringBuilder();
      TraceUtil.appendCorrelationContext( sb );
      sb.append( String.format(Locale.ROOT, "|ResponseBody[%d]%n\t%s", body.length(), body ) );
      if( bodyLog.isTraceEnabled() ) {
        log.trace( sb.toString() );
      }
    }
  }

}
