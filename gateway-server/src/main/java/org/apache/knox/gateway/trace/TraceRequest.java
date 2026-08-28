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

/**
 * Traces the inbound side of an exchange: the request line + headers once at
 * construction, and the request body as it is consumed downstream.
 * Previously (Jetty 9) this wrapped the
 * Servlet API. Jetty 12's core layer replaced the servlet
 * request/stream model entirely, so:
 * <ul>
 *   <li>the class now extends {@link Request.Wrapper} (the core request
 *       decorator) instead of the servlet wrapper;</li>
 *   <li>the body is no longer an {@code InputStream}. It arrives as a sequence
 *       of {@link Content.Chunk}s pulled via {@link Request#read()}, so we
 *       override {@code read()} and hand each chunk's bytes to {@link TraceInput}
 *       for logging;</li>
 *   <li>headers are read from {@link HttpFields} ({@link #getHeaders()}) and the
 *       request target from {@link org.eclipse.jetty.http.HttpURI}
 *       ({@link #getHttpURI()}) rather than the servlet
 *       {@code getRequestURI()}/{@code getHeaderNames()} accessors.</li>
 * </ul>
 * The three {@code tracing*} flags are cached once in the constructor so the
 * hot {@code read()} path does no per-chunk logger lookups when tracing is off.
 */
class TraceRequest extends Request.Wrapper {
  private static final Logger log = LogManager.getLogger( TraceHandler.HTTP_REQUEST_LOGGER );
  private static final Logger headLog = LogManager.getLogger( TraceHandler.HTTP_REQUEST_HEADER_LOGGER );
  private static final Logger bodyLog = LogManager.getLogger( TraceHandler.HTTP_REQUEST_BODY_LOGGER );

  private final boolean tracing;
  private final boolean tracingHeaders;
  private final boolean tracingBody;
  private final TraceInput delegate = new TraceInput();

  TraceRequest(Request request) {
    super(request);
    this.tracing = log.isTraceEnabled();
    this.tracingHeaders = headLog.isTraceEnabled();
    this.tracingBody = bodyLog.isTraceEnabled();
    if (tracing) {
      traceRequestDetails();
    }
  }

  @Override
  public Content.Chunk read() {
    // Jetty 12 delivers the request body as Content.Chunks pulled here rather
    // than through a ServletInputStream. Observe each chunk on its way to the
    // real reader, then return it untouched so downstream consumption is
    // unaffected.
    Content.Chunk chunk = super.read();
    if (chunk != null && tracingBody) {
      // Failure chunks (error/abort sentinels) carry no readable body; pass
      // them straight through without attempting to decode them.
      if (Content.Chunk.isFailure(chunk)) {
        return chunk;
      }
      ByteBuffer data = chunk.getByteBuffer();
      // slice() gives the tracer an independent view (its own position/limit)
      // so decoding the bytes for the log does not disturb the buffer the real
      // reader is about to consume.
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
    if (tracingHeaders) {
      HttpFields requestHeaders = getHeaders();
      if (requestHeaders != null) {
        for (HttpField header: requestHeaders) {
          sb.append( String.format(Locale.ROOT, "%n\tHeader[%s]=%s", header.getName(), header.getValue()));
        }
      }
    }
  }
}
