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
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.component.AbstractLifeCycle;

import java.util.concurrent.TimeUnit;

/**
 * Emits a one-line access log entry (remote addr, method, URI, request/response
 * sizes, status, duration) for every completed exchange, gated on the
 * {@code org.apache.knox.gateway.access} logger being at TRACE.
 *
 * <p><b>Jetty 9.4 &rarr; Jetty 12 migration.</b> {@link RequestLog#log} was
 * reshaped by the upgrade: the old signature took Jetty's servlet-flavoured
 * {@code org.eclipse.jetty.server.Request}/{@code Response} and exposed getters
 * like {@code getRemoteAddr()}, {@code getRequestURI()}, {@code getContentRead()}
 * and {@code getTimeStamp()} directly on those objects. In Jetty 12 the
 * {@code Request}/{@code Response} passed here are the core (non-servlet)
 * abstractions, and most of those accessors moved to <em>static</em> helpers on
 * the {@code Request}/{@code Response} types. Hence:
 * <ul>
 *   <li>remote address &rarr; {@link Request#getRemoteAddr(Request)} (static);</li>
 *   <li>request line &rarr; {@link Request#getHttpURI()} instead of
 *       {@code getRequestURI()}/{@code getQueryString()};</li>
 *   <li>request body size &rarr; {@link Request#getLength()};</li>
 *   <li>bytes written &rarr; {@link Response#getContentBytesWritten(Response)}
 *       (static) instead of the old {@code Response.getContentCount()};</li>
 *   <li>elapsed time &rarr; computed from {@link Request#getBeginNanoTime()}
 *       against {@code System.nanoTime()} rather than reading a wall-clock
 *       {@code getTimeStamp()}. Using the monotonic nano clock avoids
 *       wall-clock skew and matches how Jetty 12 itself times requests.</li>
 * </ul>
 * The emitted format is unchanged; only how each field is sourced changed.
 */
public class AccessHandler extends AbstractLifeCycle implements RequestLog {
  private static final Logger log = LogManager.getLogger( "org.apache.knox.gateway.access" );

  @Override
  public void log( Request request, Response response ) {
    if( log.isTraceEnabled() ) {
      StringBuilder sb = new StringBuilder();
      TraceUtil.appendCorrelationContext(sb);
      // Jetty 12: duration is derived from the request's monotonic start
      // (getBeginNanoTime) rather than a wall-clock timestamp getter.
      long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - request.getBeginNanoTime());
      sb.append('|')
      .append(Request.getRemoteAddr(request))
      .append('|')
      .append(request.getMethod())
      .append('|')
      .append(request.getHttpURI().toString())
      .append('|')
      .append(request.getLength())
      .append('|')
      .append(response.getStatus())
      .append('|')
      .append(Response.getContentBytesWritten(response))
      .append('|')
      .append(durationMillis);
      log.trace(sb);
    }
  }
}
