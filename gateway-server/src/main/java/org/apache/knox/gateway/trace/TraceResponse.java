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
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Set;

/**
 * Traces the outbound side of an exchange, the status line + response headers
 * once, and (optionally, filtered by status code) the response body as it is
 * written.
 *
 * This previously (Jetty 9) extended
 * {@code HttpServletResponseWrapper} and traced the body through a
 * {@code ServletOutputStream}/{@code PrintWriter} decorator. Jetty 12 writes
 * responses through the core sink {@link Response#write(boolean, ByteBuffer,
 * Callback)} instead of servlet streams, so:
 * <ul>
 *   <li>the class now extends {@link Response.Wrapper} and overrides
 *       {@code write(...)} to observe each buffer being flushed;</li>
 *   <li>status/headers come from {@link #getStatus()} and {@link HttpFields}
 *       ({@link #getHeaders()}).</li>
 * </ul>
 */
class TraceResponse extends Response.Wrapper {
  private static final Logger log = LogManager.getLogger( TraceHandler.HTTP_RESPONSE_LOGGER );
  private static final Logger headLog = LogManager.getLogger( TraceHandler.HTTP_RESPONSE_HEADER_LOGGER );
  private static final Logger bodyLog = LogManager.getLogger( TraceHandler.HTTP_RESPONSE_BODY_LOGGER );

  private final boolean tracing;
  private final boolean tracingHeaders;
  private final boolean tracingBody;
  private final TraceOutput output;
  private final Set<Integer> filter;
  private boolean traced;

  TraceResponse(Request request, Response wrapped, Set<Integer> filter ) {
    super(request, wrapped);
    this.tracing = log.isTraceEnabled();
    this.tracingHeaders = headLog.isTraceEnabled();
    this.tracingBody = bodyLog.isTraceEnabled();
    this.filter = filter;
    this.output = new TraceOutput();
  }

  @Override
  public void write(boolean last, ByteBuffer content, Callback callback) {
    // First write commits the response, so this is where status/headers are
    // logged for the common (body-bearing) case; ensureTraced() dedupes against
    // the callback-driven path used by body-less responses.
    if (tracing) {
      ensureTraced();
    }
    // Body tracing is optionally restricted to a set of status codes (e.g. only
    // log error-response bodies). An empty/null filter means "all statuses".
    if (tracingBody && (filter == null || filter.isEmpty() || filter.contains(getStatus()))) {
      // slice() so decoding for the log leaves the caller's buffer position
      // untouched for the real write below.
      ByteBuffer view = (content != null) ? content.slice() : null;
      output.extractContent(view, last);
    }
    super.write(last, content, callback);
  }

  /**
   * Logs the status line + headers exactly once. Invoked either from the first
   * {@link #write} or from {@code TraceHandler}'s wrapped callback (for
   * responses that never call {@code write}). The {@code traced} guard ensures
   * whichever fires second does nothing.
   */
  void ensureTraced() {
    if (tracing && !traced) {
      traced = true;
      traceResponseDetails();
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
    if (tracingHeaders) {
      HttpFields responseHeaders = getHeaders();
      if (responseHeaders != null) {
        for (HttpField header: responseHeaders) {
          sb.append( String.format(Locale.ROOT, "%n\tHeader[%s]=%s", header.getName(), header.getValue()));
        }
      }
    }
  }

}


