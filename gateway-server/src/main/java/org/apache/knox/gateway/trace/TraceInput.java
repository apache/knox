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

import org.apache.knox.gateway.servlet.SynchronousServletInputStreamAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.ServletInputStream;
import java.io.IOException;
import java.util.Locale;

class TraceInput extends SynchronousServletInputStreamAdapter {
  private static final Logger log = LogManager.getLogger( TraceHandler.HTTP_REQUEST_LOGGER );
  private static final Logger bodyLog = LogManager.getLogger( TraceHandler.HTTP_REQUEST_BODY_LOGGER );

  private ServletInputStream delegate;

  private static final int BUFFER_LIMIT = 1024;
  private StringBuilder buffer = new StringBuilder( BUFFER_LIMIT );

  TraceInput( ServletInputStream delegate ) {
    this.delegate = delegate;
  }

  @Override
  public int read() throws IOException {
    int b = delegate.read();
    if( b >= 0 ) {
      buffer.append( (char)b );
      if( buffer.length() == BUFFER_LIMIT || delegate.available() == 0 ) {
        traceBody();
      }
    }
    return b;
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
      sb.append( String.format(Locale.ROOT, "|RequestBody[%d]%n\t%s", body.length(), body ) );
      if( bodyLog.isTraceEnabled() ) {
        log.trace( sb.toString() );
      }
    }
  }
}
