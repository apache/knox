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

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Request;

import java.nio.ByteBuffer;
import java.util.Locale;

class TraceRequest extends Request.Wrapper {
  private static final Logger log = LogManager.getLogger( TraceHandler.HTTP_REQUEST_LOGGER );
  private static final Logger headLog = LogManager.getLogger( TraceHandler.HTTP_REQUEST_HEADER_LOGGER );

  private TraceInput delegate;

  TraceRequest(Request request) {
    super(request);
    if (log.isTraceEnabled()) {
      delegate = new TraceInput();
      traceRequestDetails();
    }
  }

  @Override
  public void demand(Runnable demandCallback) {
    super.demand(demandCallback);
  }

  @Override
  public Content.Chunk read() {
    Content.Chunk chunk = super.read();
    if (chunk != null && log.isTraceEnabled()) {
      if (Content.Chunk.isFailure(chunk)) {
        // Log that the request failed if you want
        return chunk;
      }
      ByteBuffer data = chunk.getByteBuffer();
      // Use slice() so the tracer doesn't interfere with the data
      delegate.extractContent(data != null ? data.slice() : null, chunk.isLast());
    }
    return chunk;
  }

  private void traceRequestDetails() {
    StringBuilder sb = new StringBuilder();
    TraceUtil.appendCorrelationContext( sb );
    sb.append("|Request=")
    .append(getMethod())
    .append(' ')
    .append(getHttpURI().getPath());
    String qs = getHttpURI().getQuery();
    if( qs != null ) {
      sb.append('?').append(qs);
    }
    appendHeaders(sb);
    log.trace(sb.toString());
  }

  private void appendHeaders(StringBuilder sb) {
    if (headLog.isTraceEnabled()) {
      HttpFields requestHeaders = getHeaders();
      if (requestHeaders != null) {
        for (HttpField header: requestHeaders) {
          sb.append( String.format(Locale.ROOT, "%n\tHeader[%s]=%s", header.getName(), header.getValue()));
        }
      }
    }
  }
}