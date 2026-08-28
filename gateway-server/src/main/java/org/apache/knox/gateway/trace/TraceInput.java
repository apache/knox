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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Accumulates and logs the request body for tracing.
 *
 * <p>Only the first {@value #BUFFER_LIMIT} bytes are captured so tracing a large
 * upload should not blow up memory, once that cap is hit (or the last chunk
 * arrives) the accumulated prefix is flushed to the log.
 */
class TraceInput {
  private static final Logger log = LogManager.getLogger( TraceHandler.HTTP_REQUEST_LOGGER );

  private static final int BUFFER_LIMIT = 1024;
  private final StringBuilder buffer = new StringBuilder( BUFFER_LIMIT );

  /**
   * Appends up to the remaining {@value #BUFFER_LIMIT}-byte budget from this
   * chunk view, then flushes when the budget is exhausted or this is the last
   * chunk. {@code synchronized} because chunks may be read from different
   * threads over the life of a request.
   *
   * @param view a slice of the chunk's bytes (safe to reposition here), or null
   * @param last whether this is the final chunk of the body
   */
  public synchronized void extractContent(ByteBuffer view, boolean last) {
    if (view != null && view.hasRemaining() && buffer.length() < BUFFER_LIMIT) {
      // Never decode more than what is left of the capture budget.
      int cap = BUFFER_LIMIT - buffer.length();
      ByteBuffer limited = view.duplicate();
      if (limited.remaining() > cap) {
        limited.limit(limited.position() + cap);
      }
      buffer.append(StandardCharsets.UTF_8.decode(limited));
    }
    if (buffer.length() >= BUFFER_LIMIT || last) {
      traceBody();
    }
  }

  private void traceBody() {
    if (!buffer.isEmpty()) {
      String body = buffer.toString();
      buffer.setLength(0);
      StringBuilder sb = new StringBuilder();
      TraceUtil.appendCorrelationContext(sb);
      sb.append(String.format(Locale.ROOT, "|RequestBody[%d]%n\t%s", body.length(), body));
      log.trace(sb.toString());
    }
  }
}
